package me.mondiversi.uvir

import org.json.JSONArray
import org.json.JSONObject

/** Absolute per-color calibration gains. Null in a profile means use its legacy module gains. */
data class CalibrationChannelFactors(val values: List<Float> = List(11) { 1f }) {
    init {
        require(values.size == 11 && values.all {
            it.isFinite() && it in MIN_SENSOR_CALIBRATION_FACTOR..MAX_SENSOR_CALIBRATION_FACTOR
        })
    }

    fun withChannel(index: Int, factor: Float): CalibrationChannelFactors =
        copy(values = values.toMutableList().apply { set(index, factor) })

    fun toJson(): JSONObject = JSONObject().put("version", 1)
        .put("values", JSONArray(values.map(Float::toDouble)))

    companion object {
        fun fromModuleFactors(visible: Float, uv: Float): CalibrationChannelFactors =
            CalibrationChannelFactors(List(11) { if (it < 3) uv else visible })

        fun fromJsonOrNull(json: JSONObject?): CalibrationChannelFactors? = runCatching {
            if (json == null || json.getInt("version") != 1) return null
            val values = json.getJSONArray("values")
            CalibrationChannelFactors(List(values.length()) { values.getDouble(it).toFloat() })
        }.getOrNull()

        fun fromStoredOrNull(value: String?): CalibrationChannelFactors? = runCatching {
            fromJsonOrNull(value?.let(::JSONObject))
        }.getOrNull()
    }
}

internal fun firmwareSupportsCalibrationChannels(version: String): Boolean =
    compareFirmwareVersions(version, "0.5.109") >= 0

internal fun calibrationChannelFactorsSummary(value: CalibrationChannelFactors?, format: UvirNumericFormat): String =
    value?.values?.mapIndexed { index, factor ->
        "${calibrationOffsetChannelNames[index]} ×${formatUvirNumber(factor.toDouble(), 3, format, grouping = false, minimumDigits = 1)}"
    }?.joinToString(" · ") ?: "—"
