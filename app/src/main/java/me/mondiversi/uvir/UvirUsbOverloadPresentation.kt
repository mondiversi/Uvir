package me.mondiversi.uvir

import me.mondiversi.uvir.usb.*

/** Display-only partial measurements. Never passed to publish()/recording.
 * An unavailable number is represented by its group's OL flag, not a measured zero. */
internal class UsbOverloadPresentation {
    private val uvWindow = PhoneSamplingWindow()
    private val visibleWindow = PhoneSamplingWindow()

    fun add(frame: PhoneOpticalFrame, calibration: PhoneCalibration, samples: Int, trim: Boolean,
        freshnessMs: Long, startedAtMs: Long, recoveringFlags: Int = 0): SensorSample? {
        val uvRows = if (frame.uv != null) {
            uvWindow.add(frame.uv.wm2 + DoubleArray(8), frame.revision, samples,
                frame.capturedAtMs, freshnessMs, startedAtMs)
        } else { uvWindow.expire(frame.capturedAtMs); null }
        val visibleRows = if (frame.visible != null) {
            visibleWindow.add(DoubleArray(3) + frame.visible.wm2, frame.revision, samples,
                frame.capturedAtMs, freshnessMs, startedAtMs)
        } else { visibleWindow.expire(frame.capturedAtMs); null }
        if (frame.complete != null && recoveringFlags == 0) return null
        if (frame.uv == null && frame.uvError?.saturated != true ||
            frame.visible == null && frame.visibleError?.saturated != true) return null
        val flags = (if (frame.uv == null) UVIR_QUALITY_UV_OUT_OF_RANGE else 0) or
            (if (frame.visible == null) UVIR_QUALITY_VISIBLE_NIR_OUT_OF_RANGE else 0) or
            (if (frame.complete != null) recoveringFlags else 0)
        if (flags and UVIR_QUALITY_UV_OUT_OF_RANGE == 0 && uvRows == null ||
            flags and UVIR_QUALITY_VISIBLE_NIR_OUT_OF_RANGE == 0 && visibleRows == null) return null
        val uv = uvRows?.let { processPhoneMeasurement(it, calibration, trim).channelValues().take(3) }
            ?: List(3) { 0.0 }
        val visible = visibleRows?.let { processPhoneMeasurement(it, calibration, trim).channelValues().drop(3) }
            ?: List(8) { 0.0 }
        return (uv + visible).toDoubleArray().toSensorSample().copy(qualityFlags = flags,
            opticalWindow = OpticalWindowSettings(true, calibration.window.map(Double::toFloat), calibration.windowMaster.toFloat()))
    }
}
