package me.mondiversi.uvir

import me.mondiversi.uvir.usb.*
import org.junit.Assert.*
import org.junit.Test

class UvirUsbOverloadPresentationTest {
    private val saturated = InvalidSensorReading("saturated", retryable = true, saturated = true)
    private fun frame(at: Long, uv: Double? = 2.0, visible: Double? = 3.0,
        uvError: InvalidSensorReading? = if (uv == null) saturated else null,
        visibleError: InvalidSensorReading? = if (visible == null) saturated else null, revision: Long = 1) =
        PhoneOpticalFrame(1, revision, at, uv?.let { value -> RawChannels(DoubleArray(3) { value }, 64.0) },
            visible?.let { value -> RawChannels(DoubleArray(8) { value }, 32.0) }, uvError, visibleError)

    @Test fun saturatedVisibleKeepsCalibratedAveragedUvAndNeverBecomesARecordablePair() {
        val display = UsbOverloadPresentation()
        val calibration = PhoneCalibration(factors = List(11) { 2.0 }, offsets = List(11) { 1.0 },
            windowMaster = 3.0, window = List(11) { 4.0 })
        assertNull(display.add(frame(100, uv = 2.0, visible = null), calibration, 3, false, 500, 0))
        assertNull(display.add(frame(200, uv = 3.0, visible = null), calibration, 3, false, 500, 100))
        val raw = frame(300, uv = 4.0, visible = null)
        val sample = display.add(raw, calibration, 3, false, 500, 200)!!
        assertNull(raw.complete)
        assertEquals(UVIR_QUALITY_VISIBLE_NIR_OUT_OF_RANGE, sample.qualityFlags)
        assertEquals(48.0, sample.uva, 0.0)
        assertFalse(sample.isOutOfRange(SensorGroup.UV))
        assertTrue(sample.isOutOfRange(SensorGroup.VISIBLE))
        assertTrue(sample.isOutOfRange(SensorGroup.NIR))
        val state = UsbPhoneState(status = UsbPhoneStatus.READY, sample = SensorSample(), overloadSample = sample,
            overloadAtMs = 300, automaticSession = 7, alertSession = 8)
        assertTrue(state.liveReady); assertFalse(state.ready)
        assertSame(sample, state.liveSample)
        assertNull(UvirLiveSpectrumDisplay().groupShare(SensorGroup.UV, sample, 2, UvirIrradianceUnit.W_M2))
    }

    @Test fun eitherOrBothOverloadedModulesRemainExplicitWhileOtherFailuresDoNotBecomeOl() {
        for ((uv, vis, flags) in listOf(Triple(null, 3.0, UVIR_QUALITY_UV_OUT_OF_RANGE),
            Triple(null, null, UVIR_QUALITY_UV_OUT_OF_RANGE or UVIR_QUALITY_VISIBLE_NIR_OUT_OF_RANGE))) {
            val sample = UsbOverloadPresentation().add(frame(100, uv, vis), PhoneCalibration(), 1, false, 500, 0)!!
            assertEquals(flags, sample.qualityFlags)
            if (vis != null) assertEquals(vis, sample.rosso, 0.0)
        }
        val timeout = frame(100, visible = null, visibleError = InvalidSensorReading("not ready", true))
        assertNull(UsbOverloadPresentation().add(timeout, PhoneCalibration(), 1, false, 500, 0))
        assertNull(UsbOverloadPresentation().add(frame(100), PhoneCalibration(), 1, false, 500, 0))
    }

    @Test fun saturatedDisplayExpiresWithoutNewFramesAndCannotExtendAStalledCaptureForever() {
        val sample = UsbOverloadPresentation().add(frame(100, visible = null), PhoneCalibration(), 1, false, 500, 0)!!
        val state = UsbPhoneState(status = UsbPhoneStatus.READY, overloadSample = sample, overloadAtMs = 100,
            freshnessMs = 500, error = "saturated", automaticSession = 7, alertSession = 8)
        assertTrue(state.expireOpticalReading(600).liveReady)
        val expired = state.expireOpticalReading(601)
        assertFalse(expired.liveReady); assertNull(expired.overloadSample)
        assertEquals(7L, expired.automaticSession); assertEquals(8L, expired.alertSession)
        assertFalse(state.copy(status = UsbPhoneStatus.DETACHED).liveReady)
        val pending = state.beginOpticalCapture(200)
        assertTrue(pending.expireOpticalReading(3000).liveReady)
        assertFalse(pending.expireOpticalReading(10201).liveReady)
        assertEquals(0L, expired.beginOpticalCapture(602).captureDeadlineMs)
    }

    @Test fun recoveryUpdatesValidGroupDuringFullWindowRefillWithoutFalselyRefreshingCachedNumbers() {
        val display = UsbOverloadPresentation()
        repeat(3) { display.add(frame(it * 100L, visible = null), PhoneCalibration(), 3, false, 500, it * 100L) }
        val flags = UVIR_QUALITY_VISIBLE_NIR_OUT_OF_RANGE
        val recovering = display.add(frame(400, uv = 8.0), PhoneCalibration(), 3, false, 500, 300, flags)!!
        assertEquals(flags, recovering.qualityFlags)
        assertEquals(4.0, recovering.uva, 0.0)
        assertTrue(UsbPhoneState(status = UsbPhoneStatus.READY, overloadSample = recovering).liveReady)
        assertFalse(UsbPhoneState(status = UsbPhoneStatus.READY, overloadSample = recovering).ready)
        // The normal full-pair window/publish path, rather than this UI path, ends recovery.
        assertNull(display.add(frame(500), PhoneCalibration(), 3, false, 500, 400))
    }

    @Test fun configurationAndStaleGapsRefillTheIndependentGroupWindow() {
        val display = UsbOverloadPresentation()
        repeat(3) { display.add(frame(it * 100L, visible = null), PhoneCalibration(), 3, false, 500, it * 100L) }
        assertNull(display.add(frame(1000, visible = null), PhoneCalibration(), 3, false, 500, 900))
        assertNull(display.add(frame(1100, visible = null, revision = 2), PhoneCalibration(), 3, false, 500, 1000))
        assertNull(display.add(frame(1200, visible = null, revision = 2), PhoneCalibration(), 3, false, 500, 1100))
        assertNotNull(display.add(frame(1300, visible = null, revision = 2), PhoneCalibration(), 3, false, 500, 1200))
    }
}
