package me.mondiversi.uvir

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import java.math.BigDecimal

internal const val UVIR_CHART_HEADROOM = 1.1

/** No minimum of one in the display unit: unit conversions must preserve chart proportions. */
internal fun uvirChartMaximum(values: Iterable<Double>, zeroMaximum: Double = 1.0): Double {
    var peak = 0.0
    for (value in values) if (value.isFinite() && value > peak) peak = value
    if (peak == 0.0) return zeroMaximum.takeIf { it.isFinite() && it > 0.0 } ?: 1.0
    return (peak * UVIR_CHART_HEADROOM).takeIf { it.isFinite() } ?: peak
}

internal fun uvirChartAxisFractionDigits(maximum: Double, preferred: Int): Int {
    if (!maximum.isFinite() || maximum <= 0.0) return preferred.coerceIn(0, 6)
    val required = (ceil(-log10(maximum / 4.0)) + 1.0).toInt().coerceIn(0, 6)
    return maxOf(preferred.coerceIn(0, 2), required)
}

/** Compact scientific notation for extreme scales, with the selected decimal convention. */
internal fun formatUvirChartAxisValue(
    value: Double,
    maximum: Double,
    preferredFractionDigits: Int,
    numericFormat: UvirNumericFormat
): String {
    if (value == 0.0) return formatUvirNumber(0.0, 0, numericFormat, grouping = false)
    if (value.isFinite() && (abs(maximum) < 0.001 || abs(maximum) >= 1_000_000.0)) {
        val exponent = floor(log10(abs(value))).toInt()
        val mantissa = BigDecimal.valueOf(value).scaleByPowerOfTen(-exponent).toDouble()
        return formatUvirNumber(mantissa, 2, numericFormat, grouping = false) +
            "e$exponent"
    }
    return formatUvirNumber(value, uvirChartAxisFractionDigits(maximum, preferredFractionDigits),
        numericFormat, grouping = false)
}
