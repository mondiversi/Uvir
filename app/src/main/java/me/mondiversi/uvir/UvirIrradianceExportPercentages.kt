package me.mondiversi.uvir

import android.content.Context

/** Raw record ratios, independent of display exclusions, units and rounded readings. */
internal data class UvirExportSpectralPercentages(val total: Double?, val group: Double?)

internal fun SensorSample.exportSpectralPercentages(
    value: Double,
    group: SensorGroup
): UvirExportSpectralPercentages {
    val totals = spectralTotals()
    fun percent(denominator: Double, unavailable: Boolean): Double? =
        if (unavailable || !value.isFinite() || value < 0.0 ||
            !denominator.isFinite() || denominator <= 0.0) null
        else (value / denominator).coerceIn(0.0, 1.0) * 100.0
    return UvirExportSpectralPercentages(
        total = percent(totals.ultraviolet + totals.visible + totals.infrared,
            totals.outOfRange || group == SensorGroup.BIOLOGICAL),
        group = percent(totals.total(group), isOutOfRange(group) || group == SensorGroup.BIOLOGICAL)
    )
}

internal fun readableIrradianceExportTable(
    context: Context,
    sample: SensorSample,
    group: SensorGroup,
    totalLabel: String,
    components: List<Pair<String, Double>>,
    numericFormat: UvirNumericFormat,
    irradianceUnit: UvirIrradianceUnit,
    irradianceDecimals: Int?
): String {
    fun percent(value: Double?): String = value?.let {
        formatUvirExportPercentage(it, numericFormat, irradianceDecimals) + "%"
    } ?: "—"
    val rows = (listOf(totalLabel to sample.spectralTotals().total(group)) + components).map { (name, value) ->
        val percentages = sample.exportSpectralPercentages(value, group)
        listOf(name,
            if (sample.isOutOfRange(group)) context.getString(R.string.out_of_range_short)
            else formatUvirIrradianceExportNumber(value, numericFormat, irradianceUnit, irradianceDecimals) + " " + irradianceUnit.symbol,
            percent(percentages.total), percent(percentages.group))
    }
    val headings = listOf("", context.getString(R.string.irradiance_view),
        context.getString(R.string.export_percentages_total), context.getString(R.string.export_percentages_group))
    val table = listOf(headings) + rows
    val widths = headings.indices.map { column -> table.maxOf { it[column].length } }
    return table.joinToString("\n", postfix = "\n") { row ->
        row.mapIndexed { column, value ->
            if (column == 0) value.padEnd(widths[column]) else value.padStart(widths[column])
        }.joinToString("  ")
    }
}
