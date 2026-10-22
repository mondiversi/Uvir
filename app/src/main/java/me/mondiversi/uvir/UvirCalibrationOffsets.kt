package me.mondiversi.uvir

import org.json.JSONArray
import org.json.JSONObject

internal const val MAX_CALIBRATION_OFFSET_W_M2 = 10_000f

/** Version 2: one offset per displayed color, after channel pairing, before factors.
 * Version 1 (15 physical channels) remains losslessly readable for older settings.
 * Canonical W/m², never display units or ADC counts at one particular gain. */
data class CalibrationOffsets(val values: List<Float> = List(11) { 0f }) {
    init {
        require(values.size in listOf(11, 15) && values.all { it.isFinite() && it in 0f..MAX_CALIBRATION_OFFSET_W_M2 })
    }

    val isLegacyPhysical: Boolean get() = values.size == 15
    val version: Int get() = if (isLegacyPhysical) 1 else 2
    val commandMarker: String get() = if (isLegacyPhysical) "OFFSETS" else "COLOR_OFFSETS"

    fun withChannel(index: Int, value: Float): CalibrationOffsets =
        copy(values = values.toMutableList().apply { set(index, value) })

    /** Only an explicit user edit converts a legacy configuration; opening never writes. */
    fun forColorEditor(): CalibrationOffsets = if (!isLegacyPhysical) this else CalibrationOffsets(
        listOf(listOf(0), listOf(1), listOf(2), listOf(3, 4), listOf(5, 6), listOf(7),
            listOf(8, 9), listOf(10), listOf(11), listOf(12, 13), listOf(14))
            .map { indices -> indices.map { values[it].toDouble() }.average().toFloat() })

    fun toJson(): JSONObject = JSONObject().put("version", version).put("unit", UVIR_CANONICAL_IRRADIANCE_UNIT)
        .put("values", JSONArray(values.map(Float::toDouble)))

    companion object {
        fun fromJsonOrNull(json: JSONObject?): CalibrationOffsets? = runCatching {
            if (json == null) return null
            val scale = irradianceUnitScaleToWm2(json.getString("unit"))
            val count = when (json.getInt("version")) { 1 -> 15; 2 -> 11; else -> return null }
            val values = json.getJSONArray("values")
            if (values.length() != count) return null
            CalibrationOffsets(List(values.length()) { (values.getDouble(it) * scale).toFloat() })
        }.getOrNull()

        fun fromStoredOrNull(value: String?): CalibrationOffsets? = runCatching {
            fromJsonOrNull(value?.let(::JSONObject))
        }.getOrNull()
    }
}

internal fun firmwareSupportsCalibrationOffsets(version: String, offsets: CalibrationOffsets? = null): Boolean =
    compareFirmwareVersions(version, if (offsets?.isLegacyPhysical == true) "0.5.107" else "0.5.108") >= 0

internal val calibrationOffsetChannelNames = listOf(
    "UVC", "UVB", "UVA", "F1/F2", "FZ/F3", "F4", "F5/FY", "FXL", "F6", "F7/F8", "NIR")
private val legacyCalibrationOffsetChannelNames = listOf(
    "UVC", "UVB", "UVA", "F1", "F2", "FZ", "F3", "F4", "F5", "FY", "FXL", "F6", "F7", "F8", "NIR")
internal val calibrationOffsetReferences = opticalWindowChannelReferences

/** Locale-independent editable text; DecimalField supplies the user's decimal separator. */
internal fun calibrationOffsetInput(value: Float, unit: UvirIrradianceUnit): String =
    java.math.BigDecimal(value.toString())
        .multiply(java.math.BigDecimal.valueOf(unit.fromCanonicalWm2(1.0)))
        .stripTrailingZeros().toPlainString()

internal fun calibrationOffsetFromInput(text: String, unit: UvirIrradianceUnit): Float? =
    text.replace(',', '.').toDoubleOrNull()?.let(unit::toCanonicalWm2)?.toFloat()
        ?.takeIf { it.isFinite() && it in 0f..MAX_CALIBRATION_OFFSET_W_M2 }

internal fun calibrationOffsetsSummary(
    value: CalibrationOffsets?, unit: UvirIrradianceUnit, format: UvirNumericFormat
): String = value?.values?.mapIndexed { index, offset ->
    "${(if (value.isLegacyPhysical) legacyCalibrationOffsetChannelNames else calibrationOffsetChannelNames)[index]} ${formatUvirIrradianceNumber(offset.toDouble(), 6, format, unit, grouping = false)}"
}?.joinToString(" · ")?.let { "$it ${unit.symbol}" } ?: "—"
