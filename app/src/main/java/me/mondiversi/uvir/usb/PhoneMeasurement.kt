package me.mondiversi.uvir.usb

import me.mondiversi.uvir.*

internal data class PhoneCalibration(
    val factors: List<Double> = List(11) { 1.0 },
    val offsets: List<Double> = List(11) { 0.0 },
    val windowMaster: Double = 1.0,
    val window: List<Double> = List(11) { 1.0 }
) {
    init {
        require(factors.size == 11 && factors.all { it.isFinite() && it in .1..10.0 })
        require(offsets.size == 11 && offsets.all { it.isFinite() && it in 0.0..10000.0 })
        require(windowMaster.isFinite() && windowMaster in 1.0..10.0)
        require(window.size == 11 && window.all { it.isFinite() && it in 1.0..10.0 })
    }
}

/** Subtract channel offsets BEFORE gain/window, average signed residuals BEFORE clamping to zero.
 * Values and storage are W/m². Display inclusion/rounding never enters the measurement pipeline.
 */
internal fun processPhoneMeasurement(frames: List<DoubleArray>, calibration: PhoneCalibration,
    discardExtremes: Boolean = false): SensorSample {
    require(frames.isNotEmpty() && frames.all { it.size == 11 && it.all(Double::isFinite) })
    val values = DoubleArray(11) { channel ->
        val raw = frames.map { it[channel] }.sorted()
        val usable = if (discardExtremes && raw.size >= 3) raw.subList(1, raw.lastIndex) else raw
        maxOf(0.0, usable.average() - calibration.offsets[channel]) * calibration.factors[channel] *
            calibration.windowMaster * calibration.window[channel]
    }
    return values.toSensorSample().copy(opticalWindow = OpticalWindowSettings(true,
        calibration.window.map(Double::toFloat), calibration.windowMaster.toFloat()))
}

internal fun DoubleArray.toSensorSample(): SensorSample {
    require(size == 11)
    return SensorSample(this[0], this[1], this[2], this[3], this[4], this[5], this[6], this[7], this[8], this[9], this[10])
}
internal fun SensorSample.channelValues(): DoubleArray =
    doubleArrayOf(uvc, uvb, uva, violetto, blu, verde, giallo, arancione, rosso, f8, nir)

/** No hardware serial means a fresh, connection-scoped identity: never reuse another unit's calibration. */
internal fun usbHardwareIdentity(serial: String?, connectionId: String): String {
    val valid = serial?.trim()?.takeIf { it.matches(Regex("[A-Za-z0-9_-]{4,64}")) && it.any { c -> c != '0' } }
    return if (valid != null) "FT232H:$valid" else "FT232H-TEMP:$connectionId"
}

/** A monotonic interval scheduler. An overdue tick yields ONE new sample, never a catch-up burst. */
internal class PhoneAcquisitionClock(private val intervalMs: Long, now: Long) {
    init { require(intervalMs > 0) }
    private var next = now
    fun due(now: Long): Boolean {
        if (now < next) return false
        next = now + intervalMs
        return true
    }
}
