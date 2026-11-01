package me.mondiversi.uvir.usb

import java.io.IOException
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class PhoneOpticalSamplingTest {
    @Test fun rollingWindowPublishesEveryNewValidSampleAfterItsInitialFill() {
        val window = PhoneSamplingWindow()
        assertNull(window.add(DoubleArray(11) { 1.0 }, 1, 3, 100, 500))
        assertNull(window.add(DoubleArray(11) { 2.0 }, 1, 3, 200, 500))
        assertEquals(2.0, processPhoneMeasurement(window.add(DoubleArray(11) { 3.0 }, 1, 3, 300, 500)!!,
            PhoneCalibration()).uva, 0.0)
        // One failed conversion contributed nothing, but did not erase the valid window.
        val next = window.add(DoubleArray(11) { 4.0 }, 1, 3, 600, 500)!!
        assertEquals(listOf(2.0, 3.0, 4.0), next.map { it[0] })
    }

    @Test fun staleGapsAndConfigurationChangesMustRefillRatherThanReuseOldValues() {
        val window = PhoneSamplingWindow()
        repeat(3) { window.add(DoubleArray(11) { 99.0 }, 1, 3, it * 100L, 500) }
        assertNull(window.add(DoubleArray(11) { 1.0 }, 1, 3, 1000, 500))
        assertNull(window.add(DoubleArray(11) { 1.0 }, 1, 3, 1100, 500))
        assertNull(window.add(DoubleArray(11) { 1.0 }, 2, 3, 1200, 500))
        assertNull(window.add(DoubleArray(11) { 1.0 }, 2, 3, 1300, 500))
        assertEquals(listOf(1.0,1.0,1.0), window.add(DoubleArray(11) { 1.0 }, 2, 3, 1400, 500)!!.map { it[0] })
    }

    @Test fun freshnessAccountsForSlowUsbAndConfiguredSpacingButHasAFiniteBound() {
        assertEquals(500L, opticalFreshnessMs(100, 80))
        assertEquals(3000L, opticalFreshnessMs(100, 1000))
        assertEquals(180000L, opticalFreshnessMs(60000, 1000))
        assertEquals(30000L, opticalFreshnessMs(0, 90000))
    }

    @Test fun aSlowRecoveryCannotRetroactivelyMakeExpiredSamplesFresh() {
        val window = PhoneSamplingWindow()
        window.add(DoubleArray(11) { 99.0 }, 1, 2, 0, 500)
        assertNotNull(window.add(DoubleArray(11) { 99.0 }, 1, 2, 100, 500))
        assertNull(window.add(DoubleArray(11) { 1.0 }, 1, 2, 2000, 6000))
        assertEquals(listOf(1.0, 2.0), window.add(DoubleArray(11) { 2.0 }, 1, 2, 2100, 500)!!.map { it[0] })
    }

    @Test fun aCaptureStartedOnTimeCanAdaptGainWithoutDestroyingTheValidWindow() {
        val window = PhoneSamplingWindow()
        repeat(5) { window.add(DoubleArray(11) { 1.0 }, 1, 5, it * 200L, 699) }
        // Recorded lamp transition: a complete, valid conversion took 3247 ms.
        val frames = window.add(DoubleArray(11) { 10.0 }, 1, 5, 4048, 9741, captureStartedAtMs = 801)!!
        assertEquals(listOf(1.0, 1.0, 1.0, 1.0, 10.0), frames.map { it[0] })
    }

    @Test fun retriesCannotResurrectAWindowAfterAFailedOrExcessivelyLongCapture() {
        val window = PhoneSamplingWindow()
        repeat(3) { window.add(DoubleArray(11) { 9.0 }, 1, 3, it * 100L, 500) }
        window.expire(500) // Short failed sample still leaves valid rows.
        assertNotNull(window.add(DoubleArray(11) { 1.0 }, 1, 3, 750, 500, 501))
        window.expire(1500) // A genuinely failed gap expires the window.
        assertNull(window.add(DoubleArray(11) { 2.0 }, 1, 3, 1600, 500, 1501))
        assertNull(window.add(DoubleArray(11) { 2.0 }, 1, 3, 1700, 500, 1601))
        assertNotNull(window.add(DoubleArray(11) { 2.0 }, 1, 3, 1800, 500, 1701))
        assertNull(window.add(DoubleArray(11) { 3.0 }, 1, 3, 11802, 30000, 1801))
    }

    @Test fun transientOpticalFailuresRetryButTransportAndCancellationNeverDo() = runBlocking {
        var attempts = 0
        assertEquals(7, retryOpticalSample { if (++attempts < 3) throw InvalidSensorReading("adapt", true) else 7 })
        assertEquals(3, attempts)
        for (failure in listOf(IOException("transport"), InvalidSensorReading("fatal"), CancellationException())) {
            attempts = 0
            try { retryOpticalSample<Int> { attempts++; throw failure }; fail("Must propagate") }
            catch (error: Exception) { assertSame(failure, error); assertEquals(1, attempts) }
        }
        attempts = 0
        try { retryOpticalSample<Int> { attempts++; throw InvalidSensorReading("saturated", true) }; fail("Bounded retries") }
        catch (error: InvalidSensorReading) { assertEquals(3, attempts) }
    }

    @Test fun uvIntegrationOverlapsVisibleAndFailedUvDoesNotRecaptureGoodVisible() = runBlocking {
        val bus = HeadBus(uvFailures = 2)
        val frame = readPhoneOpticalFrame(As7331(bus) {}, As7343(bus) {}, 3, 7, true, true) { 100 }
        assertNotNull(frame.complete)
        assertEquals(1, bus.visibleReads)
        assertTrue(bus.order.indexOf("uv-start") < bus.order.indexOf("visible-read"))
        assertTrue(bus.order.indexOf("visible-read") < bus.order.indexOf("uv-result"))
    }

    @Test fun oneFailedGroupRemainsMissingWhileTheOtherStillContributesReferences() = runBlocking {
        val frame = readPhoneOpticalFrame(As7331(HeadBus(uvFailures = 100)) {},
            As7343(HeadBus()) {}, 3, 7, true, true) { 100 }
        assertNull(frame.complete)
        assertTrue(frame.referenceValues().take(3).all { it.isNaN() })
        assertTrue(frame.referenceValues().drop(3).all { it.isFinite() })
    }

    @Test fun minimumOrLockedGainOverloadDoesNotRepeatIdenticalConversionsAndStillRecovers() = runBlocking {
        val bus = HeadBus(visibleSaturated = true)
        val uv = As7331(bus) {}
        val visible = As7343(bus) {}
        // Auto-range reaches minimum, then reports saturation without throwing away UV.
        val first = readPhoneOpticalFrame(uv, visible, 1, 1, false, false) { 100 }
        assertNull(first.complete); assertNotNull(first.uv)
        assertTrue(first.visibleError!!.rangeExhausted)
        assertEquals(0, visible.gainIndex)
        val before = bus.visibleReads
        val next = readPhoneOpticalFrame(uv, visible, 1, 1, false, false) { 200 }
        assertTrue(next.visibleError!!.saturated)
        assertEquals("Only one conversion while already at minimum gain", before + 1, bus.visibleReads)
        bus.visibleSaturated = false
        assertNotNull(readPhoneOpticalFrame(uv, visible, 1, 1, false, false) { 300 }.complete)
        bus.visibleSaturated = true
        val lockedAt = bus.visibleReads
        assertNull(readPhoneOpticalFrame(uv, visible, 1, 1, true, true) { 400 }.complete)
        assertEquals("Locked references cannot adapt gain", lockedAt + 1, bus.visibleReads)
    }

    private class HeadBus(var uvFailures: Int = 0, var visibleSaturated: Boolean = false) : I2cBus {
        var gain = 9
        var visibleReads = 0
        val order = mutableListOf<String>()
        override fun transfer(address: Int, write: ByteArray, readCount: Int): ByteArray {
            val register = write[0].toInt() and 255
            if (readCount == 0) {
                if (address == 0x74 && register == 0 && write[1].toInt() and 255 == 0x83) order.add("uv-start")
                if (address == 0x39 && register == 0xc6) gain = write[1].toInt() and 15
                return byteArrayOf()
            }
            return when {
                address == 0x74 && register == 0 -> {
                    order.add("uv-result")
                    byteArrayOf(3, if (uvFailures-- > 0) 0 else 8)
                }
                address == 0x74 && register == 2 -> byteArrayOf(100,0,100,0,100,0)
                address == 0x39 && register == 0x90 -> byteArrayOf(0x40)
                address == 0x39 && register == 0x94 -> {
                    order.add("visible-read"); visibleReads++
                    byteArrayOf((gain or if (visibleSaturated) 0x80 else 0).toByte()) +
                        ByteArray(36) { if (it % 2 == 0) 100 else 0 }
                }
                else -> ByteArray(readCount)
            }
        }
        override fun close() {}
    }
}
