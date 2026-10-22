package me.mondiversi.uvir

import org.json.JSONArray
import org.json.JSONObject

/** Factors follow the wire order; values have already been corrected by the firmware. */
data class OpticalWindowSettings(
    val enabled: Boolean = false,
    val factors: List<Float> = List(11) { 1f },
    // Null is the lossless legacy format: its independent master is implicitly one.
    val masterFactor: Float? = null
) {
    init {
        require(factors.size == 11 && factors.all { it.isFinite() && it in 1f..10f })
        require(masterFactor == null || (masterFactor.isFinite() && masterFactor in 1f..10f))
        require(masterFactor == null || masterFactor == normalizeOpticalMasterFactor(masterFactor))
    }

    val masterMultiplier: Float get() = masterFactor ?: 1f
    val effectiveFactors: List<Float> get() = factors.map { it * masterMultiplier }

    fun withMasterFactor(factor: Float): OpticalWindowSettings =
        copy(masterFactor = normalizeOpticalMasterFactor(factor))

    fun resetMultipliers(): OpticalWindowSettings = copy(factors = List(11) { 1f },
        masterFactor = masterFactor?.let { 1f })

    fun withCommonFactor(factor: Float): OpticalWindowSettings = copy(factors = List(11) { factor })

    fun withChannelFactor(index: Int, factor: Float): OpticalWindowSettings =
        copy(factors = factors.toMutableList().apply { set(index, factor) })

    val commonFactorOrNull: Float? get() = factors.first().takeIf { factor -> factors.all { it == factor } }

    fun toJson(): JSONObject = JSONObject().put("version", if (masterFactor == null) 1 else 2).put("enabled", enabled)
        .put("factors", JSONArray(factors.map { it.toDouble() }))
        .apply { masterFactor?.let { put("master", it.toDouble()) } }

    companion object {
        fun fromJsonOrNull(json: JSONObject?): OpticalWindowSettings? = runCatching {
            if (json == null || json.getInt("version") !in 1..2) return null
            val factors = json.getJSONArray("factors")
            OpticalWindowSettings(json.getBoolean("enabled"),
                List(factors.length()) { factors.getDouble(it).toFloat() },
                if (json.getInt("version") == 2) normalizeOpticalMasterFactor(json.getDouble("master").toFloat()) else null)
        }.getOrNull()

        fun fromStoredOrNull(text: String?): OpticalWindowSettings? =
            runCatching { fromJsonOrNull(text?.takeIf { it.isNotBlank() }?.let(::JSONObject)) }.getOrNull()
    }
}

internal fun firmwareSupportsOpticalWindow(version: String): Boolean =
    compareFirmwareVersions(version, "0.5.106") >= 0

internal const val OPTICAL_WINDOW_MASTER_MINIMUM_FIRMWARE = "0.5.111"

internal fun firmwareSupportsOpticalWindowMaster(version: String): Boolean =
    compareFirmwareVersions(version, OPTICAL_WINDOW_MASTER_MINIMUM_FIRMWARE) >= 0

/** Matches the firmware's versioned, six-decimal master encoding. */
internal fun normalizeOpticalMasterFactor(value: Float): Float {
    require(value.isFinite() && value in 1f..10f)
    return (kotlin.math.floor(value.toDouble() * 1_000_000.0 + 0.5) / 1_000_000.0).toFloat()
}

/** A rejected calibration command is not a lost/authentication-failed link. */
internal fun isCalibrationCommandRejection(code: String): Boolean = code in setOf(
    "calibration_busy", "calibration_invalid", "calibration_save_failed"
)

internal val UvirSensorRuntimeInfo.calibrationChangeBlocked: Boolean
    get() = calibrationLocked ?: (operationActive == true)

/** Legacy OFF factors were ignored: opening the editor must not activate them. */
internal fun OpticalWindowSettings.forWindowEditor(): OpticalWindowSettings =
    if (enabled) this else OpticalWindowSettings(enabled = true)

/** Exact channel references for selecting window transmission factors. */
internal val opticalWindowChannelReferences = listOf(
    UvirSensorBands.UVC, UvirSensorBands.UVB, UvirSensorBands.UVA,
    UvirSensorBands.VIOLET_CHANNELS, UvirSensorBands.BLUE_CHANNELS, UvirSensorBands.GREEN_CHANNEL,
    UvirSensorBands.GREEN_YELLOW_CHANNELS, UvirSensorBands.ORANGE_CHANNEL, UvirSensorBands.RED_CHANNEL,
    UvirSensorBands.FAR_RED_CHANNELS, UvirSensorBands.NIR_CHANNEL
)

internal fun parseSensorCalibrationCommand(command: String): SensorCalibrationSettings? = runCatching {
    val tokens = command.trim().split(Regex("\\s+"))
    require(tokens[0] == "CALIBRATION_CONFIG" && tokens.size >= 3)
    val visible = tokens[1].toFloat()
    val uv = tokens[2].toFloat()
    require(visible.isFinite() && visible in .1f..10f && uv.isFinite() && uv in .1f..10f)
    var window = if (tokens.size == 3) OpticalWindowSettings() else {
        require(tokens[3] in listOf("ON", "OFF"))
        OpticalWindowSettings(tokens[3] == "ON", tokens.subList(4, 15).map(String::toFloat))
    }
    var cursor = if (tokens.size == 3) 3 else 15
    val offsets = if (tokens.getOrNull(cursor) in listOf("COLOR_OFFSETS", "OFFSETS")) {
        val count = if (tokens[cursor++] == "COLOR_OFFSETS") 11 else 15
        CalibrationOffsets(tokens.subList(cursor, cursor + count).map { legacyIrradianceToWm2(it.toDouble()).toFloat() }).also { cursor += count }
    } else null
    val factors = if (tokens.getOrNull(cursor) == "CHANNEL_FACTORS") {
        cursor++
        CalibrationChannelFactors(tokens.subList(cursor, cursor + 11).map(String::toFloat)).also { cursor += 11 }
    } else null
    if (tokens.getOrNull(cursor) == "WINDOW_MASTER") {
        cursor++
        window = window.withMasterFactor(tokens[cursor++].toFloat())
    }
    require(cursor == tokens.size)
    SensorCalibrationSettings(visible, uv, window, offsets, factors)
}.getOrNull()

/** Never let a legacy firmware silently accept and ignore an extended calibration. */
internal fun sensorCalibrationCommandsSupported(commands: List<String>, version: String): Boolean =
    commands.filter { it.startsWith("CALIBRATION_CONFIG ") }.all { command ->
        val settings = parseSensorCalibrationCommand(command)
        settings != null && (settings.opticalWindow == OpticalWindowSettings() ||
            firmwareSupportsOpticalWindow(version)) &&
            (settings.opticalWindow.masterFactor == null || firmwareSupportsOpticalWindowMaster(version)) &&
            (settings.offsets == null || firmwareSupportsCalibrationOffsets(version, settings.offsets)) &&
            (settings.channelFactors == null || firmwareSupportsCalibrationChannels(version))
    }

internal fun Iterable<SensorSample>.commonOpticalWindow(): OpticalWindowSettings? =
    map { it.opticalWindow }.distinct().singleOrNull()

internal fun JSONObject.opticalWindowOrNull(): OpticalWindowSettings? =
    OpticalWindowSettings.fromJsonOrNull(optJSONObject("optical_window"))

internal fun opticalWindowSummary(
    value: OpticalWindowSettings?,
    numericFormat: UvirNumericFormat = UvirNumericFormat.SYSTEM
): String = when {
    value == null -> "—"
    !value.enabled -> "×1"
    else -> listOf("UVC", "UVB", "UVA", "F1/F2", "FZ/F3", "F4", "F5/FY", "FXL", "F6", "F7/F8", "NIR")
        .zip(value.effectiveFactors).joinToString(" · ") { (name, factor) ->
            "$name ×${formatUvirNumber(factor.toDouble(), 3, numericFormat, grouping = false, minimumDigits = 1)}"
        }
}
