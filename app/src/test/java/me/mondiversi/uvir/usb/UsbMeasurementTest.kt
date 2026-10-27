package me.mondiversi.uvir.usb

import me.mondiversi.uvir.*
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class UsbMeasurementTest {
    @Test fun correctionOrderAndSiStorage() {
        val sample = processPhoneMeasurement(listOf(DoubleArray(11) { 10.0 }),
            PhoneCalibration(List(11) { 2.0 }, List(11) { 3.0 }, 2.0, List(11) { 3.0 }))
        assertEquals(84.0, sample.uva, 1e-10) // (10-3)*2*2*3, never (10*gain-offset).
        assertEquals(8400.0, UvirIrradianceUnit.UW_CM2.fromCanonicalWm2(sample.uva), 1e-8)
    }
    @Test fun signedAverageIsClampedOnce() {
        val result = processPhoneMeasurement(listOf(DoubleArray(11) { 0.0 }, DoubleArray(11) { 2.0 }),
            PhoneCalibration(offsets = List(11) { 1.0 }))
        assertEquals(0.0, result.uvc, 0.0) // Clamping each frame first would bias this to +0.5.
    }
    @Test fun negativeValuesAreNeverSaved() {
        assertEquals(0.0, processPhoneMeasurement(listOf(DoubleArray(11) { .01 }),
            PhoneCalibration(offsets = List(11) { .02 })).nir, 0.0)
    }
    @Test fun trimIsPerChannel() {
        val frames = listOf(DoubleArray(11) { 0.0 }, DoubleArray(11) { 2.0 }, DoubleArray(11) { 200.0 })
        assertEquals(2.0, processPhoneMeasurement(frames, PhoneCalibration(), true).uva, 0.0)
        assertEquals(202.0 / 3, processPhoneMeasurement(frames, PhoneCalibration(), false).uva, 1e-10)
    }
    @Test fun twoSamplesCannotBeTrimmedToAnEmptySet() {
        assertEquals(2.0, processPhoneMeasurement(listOf(DoubleArray(11) { 1.0 }, DoubleArray(11) { 3.0 }),
            PhoneCalibration(), true).nir, 0.0)
    }
    @Test(expected = IllegalArgumentException::class) fun nanNeverBecomesAZeroMeasurement() {
        processPhoneMeasurement(listOf(DoubleArray(11) { Double.NaN }), PhoneCalibration())
    }
    @Test(expected = IllegalArgumentException::class) fun incompleteFrameCannotBeRecorded() {
        processPhoneMeasurement(listOf(DoubleArray(3)), PhoneCalibration())
    }
    @Test fun channelOrderingMatchesExistingArchives() {
        val values = DoubleArray(11) { it + .5 }
        assertArrayEquals(values, values.toSensorSample().channelValues(), 0.0)
    }
    @Test fun temporaryIdentityNeverCarriesCalibrationBetweenAttachments() {
        assertNotEquals(usbHardwareIdentity(null, "a"), usbHardwareIdentity(null, "b"))
        assertTrue(usbHardwareIdentity("00000000", "a").startsWith("FT232H-TEMP:"))
        assertTrue(usbHardwareIdentity("bad serial /", "a").startsWith("FT232H-TEMP:"))
    }
    @Test fun usableSerialIsStable() {
        assertEquals("FT232H:FT12345", usbHardwareIdentity("FT12345", "connection1"))
        assertEquals(usbHardwareIdentity("FT12345", "1"), usbHardwareIdentity("FT12345", "2"))
    }
    @Test fun overdueIntervalsDoNotInventHistoricalRecords() {
        val clock = PhoneAcquisitionClock(1000, 100)
        assertFalse(clock.due(99)); assertTrue(clock.due(100)); assertFalse(clock.due(101))
        assertTrue(clock.due(10000)); assertFalse(clock.due(10000)); assertFalse(clock.due(10999)); assertTrue(clock.due(11000))
    }
    @Test fun ftdiHeadersAreStrippedFromEveryPacket() {
        val data = byteArrayOf(1, 0x60, 10, 11, 1, 0x60, 12, 13, 1, 0x60)
        assertArrayEquals(byteArrayOf(10, 11, 12, 13), stripFtdiStatus(data, data.size, 4))
    }
    @Test(expected = InvalidSensorReading::class) fun truncatedUsbPacketIsRejected() {
        stripFtdiStatus(byteArrayOf(1), 1, 512)
    }
    @Test(expected = InvalidSensorReading::class) fun lineErrorsAreNotOpticalCounts() {
        stripFtdiStatus(byteArrayOf(1, 0x62, 99), 3, 512)
    }
    @Test fun uvGainSelectionIsBoundedAndReducesOverload() {
        assertEquals(4, nextUvGain(64, 65535, true))
        assertEquals(1, nextUvGain(1, 65535, true))
        assertEquals(16, nextUvGain(64, 60000, false))
        assertEquals(2048, nextUvGain(64, 0, false))
        assertEquals(64, nextUvGain(64, 20000, false))
    }
    @Test fun visibleNormalizationUsesActualMeasuredGain() {
        val reference = intArrayOf(5749, 1756, 2169, 770, 3141, 1574, 3747, 4776, 3336, 5435, 864, 10581)
        assertArrayEquals(DoubleArray(8) { .155 }, visibleWm2(reference, 11), 1e-12)
        val at64 = visibleWm2(reference, 7)
        assertEquals(.155 * 16.9, at64[0], 1e-12)
    }
    @Test fun mpsseUsesOpenDrainAnd100KhzThreePhaseClock() {
        val io = FakeIo(listOf(byteArrayOf(0xfa.toByte(), 0xaa.toByte()), byteArrayOf(5)))
        Ft232hMpsse(io).initialize()
        assertTrue(io.writes.any { command -> command.toList().windowed(3).any { it == listOf(0x9e.toByte(), 3.toByte(), 0.toByte()) } })
        assertTrue(io.writes.any { command -> command.toList().windowed(3).any { it == listOf(0x86.toByte(), 199.toByte(), 0.toByte()) } })
    }
    @Test fun noAcknowledgeStopsAndReleasesTheBus() {
        val io = FakeIo(listOf(byteArrayOf(1)))
        try { Ft232hMpsse(io).transfer(0x74, byteArrayOf(2), 6); fail("Expected NACK") }
        catch (expected: I2cNack) { assertEquals(0x74, expected.address) }
        assertArrayEquals(byteArrayOf(0x80.toByte(), 0, 3, 0x80.toByte(), 1, 3, 0x80.toByte(), 3, 3), io.writes.last())
    }
    @Test fun repeatedStartReadsBytesAndNacksTheLastOne() {
        val io = FakeIo(listOf(byteArrayOf(0), byteArrayOf(0), byteArrayOf(0), byteArrayOf(0x12), byteArrayOf(0x34)))
        assertArrayEquals(byteArrayOf(0x12, 0x34), Ft232hMpsse(io).transfer(0x74, byteArrayOf(2), 2))
        val acknowledgements = io.writes.filter { it.firstOrNull() == 0x13.toByte() }
        assertEquals(0, acknowledgements[0][2].toInt())
        assertEquals(255, acknowledgements[1][2].toInt() and 255)
    }
    @Test fun uvRegistersAndChannelUnits() {
        val bus = RegisterBus()
        bus.reads[2 to 1] = byteArrayOf(0x21)
        bus.reads[0 to 2] = byteArrayOf(3, 8) // NDATA, no overflow.
        bus.reads[2 to 6] = byteArrayOf(100, 0, 50, 0, 25, 0) // UVA, UVB, UVC.
        val sensor = As7331(bus) {}
        sensor.initialize()
        val sample = sensor.capture(lockGain = true)
        assertEquals(25 * 169984.0 / (64 * 64.0 * 1024 * 100), sample.wm2[0], 1e-12)
        assertEquals(100 * 348160.0 / (64 * 64.0 * 1024 * 100), sample.wm2[2], 1e-12)
        assertTrue(bus.writes.any { it.contentEquals(byteArrayOf(6, 0x56)) })
    }
    @Test(expected = InvalidSensorReading::class) fun uvWithoutNewDataCannotReusePreviousResult() {
        val bus = RegisterBus(); bus.reads[0 to 2] = byteArrayOf(3, 0)
        As7331(bus) {}.capture()
    }
    @Test(expected = InvalidSensorReading::class) fun overflowCannotBecomeALowUvValue() {
        val bus = RegisterBus(); bus.reads[0 to 2] = byteArrayOf(3, 0x68)
        bus.reads[2 to 6] = ByteArray(6)
        As7331(bus) {}.capture(lockGain = true)
    }
    @Test fun visibleOverloadKeepsReducingGainAcrossInvalidReadingsWithoutReconnecting() {
        val bus = RegisterBus()
        bus.reads[0xc6 to 1] = byteArrayOf(0)
        bus.reads[0x90 to 1] = byteArrayOf(0x50) // Ready + digital saturation.
        bus.reads[0x95 to 36] = ByteArray(36) { if (it % 2 == 0) 0x10 else 0x27 } // 10,000 counts.
        val sensor = As7343(bus) {}
        try { sensor.capture(); fail("Must reject saturated data") }
        catch (expected: InvalidSensorReading) { assertTrue(expected.retryable) }
        assertEquals(6, sensor.gainIndex) // 256 -> 128 -> 64 -> 32; next capture retains 32.
        bus.reads[0x90 to 1] = byteArrayOf(0x40)
        bus.reads[0x95 to 36] = ByteArray(36) { if (it % 2 == 0) 0xe8.toByte() else 3 } // 1,000 counts.
        assertEquals(32.0, sensor.capture().gain, 0.0)
    }

    private class FakeIo(reads: List<ByteArray>) : MpsseIo {
        val writes = mutableListOf<ByteArray>()
        private val incoming = ArrayDeque(reads)
        override fun write(bytes: ByteArray) { writes.add(bytes) }
        override fun readExactly(count: Int): ByteArray = incoming.removeFirst().also { assertEquals(count, it.size) }
        override fun close() {}
    }
    private class RegisterBus : I2cBus {
        val reads = mutableMapOf<Pair<Int, Int>, ByteArray>()
        val writes = mutableListOf<ByteArray>()
        override fun transfer(address: Int, write: ByteArray, readCount: Int): ByteArray {
            if (readCount == 0) { writes.add(write); return byteArrayOf() }
            return reads[(write[0].toInt() and 255) to readCount] ?: throw IOException("Missing fixture")
        }
        override fun close() {}
    }
}
