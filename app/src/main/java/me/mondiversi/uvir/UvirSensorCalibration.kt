package me.mondiversi.uvir

internal const val DEFAULT_SENSOR_CALIBRATION_FACTOR = 1f
internal const val MIN_SENSOR_CALIBRATION_FACTOR = 0.1f
internal const val MAX_SENSOR_CALIBRATION_FACTOR = 10f
private val SENSOR_CALIBRATION_MINIMUM_FIRMWARE = listOf(0, 5, 11)

data class SensorCalibrationSettings(
    val visibleFactor: Float = DEFAULT_SENSOR_CALIBRATION_FACTOR,
    val uvFactor: Float = DEFAULT_SENSOR_CALIBRATION_FACTOR,
    val opticalWindow: OpticalWindowSettings = OpticalWindowSettings(),
    val offsets: CalibrationOffsets? = null,
    val channelFactors: CalibrationChannelFactors? = null
)

internal fun sensorCalibrationCommand(settings: SensorCalibrationSettings): String =
    "CALIBRATION_CONFIG " +
        "${settings.visibleFactor.coerceIn(MIN_SENSOR_CALIBRATION_FACTOR, MAX_SENSOR_CALIBRATION_FACTOR)} " +
        settings.uvFactor.coerceIn(MIN_SENSOR_CALIBRATION_FACTOR, MAX_SENSOR_CALIBRATION_FACTOR) +
        (if (settings.opticalWindow == OpticalWindowSettings() && settings.offsets == null && settings.channelFactors == null) "" else
            " ${if (settings.opticalWindow.enabled) "ON" else "OFF"} " +
                settings.opticalWindow.factors.joinToString(" ")) +
        (settings.offsets?.let { " ${it.commandMarker} " + it.values.joinToString(" ", transform = ::irradianceLegacyCommandNumber) } ?: "") +
        (settings.channelFactors?.let { " CHANNEL_FACTORS " + it.values.joinToString(" ") } ?: "") +
        (settings.opticalWindow.masterFactor?.let { " WINDOW_MASTER " + normalizeOpticalMasterFactor(it) } ?: "")

internal fun firmwareSupportsSensorCalibration(version: String): Boolean {
    val parts = version.substringBefore('-').split('.').mapNotNull(String::toIntOrNull)
    if (parts.size < 3) return false
    return (0..2).map { parts.getOrElse(it) { 0 } }.zip(SENSOR_CALIBRATION_MINIMUM_FIRMWARE)
        .firstOrNull { (actual, required) -> actual != required }
        ?.let { (actual, required) -> actual > required } ?: true
}
