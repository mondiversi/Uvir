package me.mondiversi.uvir

import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.pow

private const val ALERT_CHART_LOG_FLOOR_PERCENT = 1.0
private val ALERT_CHART_MIN_LOG_EXTENT = log10(1.25)

internal fun alertThresholdCenteredLogExtent(
    percentages: Iterable<Double>
): Double =
    percentages
        .filter { it.isFinite() }
        .maxOfOrNull { percentage ->
            abs(
                log10(
                    percentage.coerceAtLeast(ALERT_CHART_LOG_FLOOR_PERCENT) /
                        100.0
                )
            )
        }
        ?.coerceAtLeast(ALERT_CHART_MIN_LOG_EXTENT)
        ?: ALERT_CHART_MIN_LOG_EXTENT

internal fun alertThresholdCenteredLogFraction(
    percentage: Double,
    extent: Double
): Float {
    val safeExtent = extent.coerceAtLeast(ALERT_CHART_MIN_LOG_EXTENT)
    val transformed =
        log10(
            percentage.coerceAtLeast(ALERT_CHART_LOG_FLOOR_PERCENT) /
                100.0
        )
    return ((transformed + safeExtent) / (safeExtent * 2.0))
        .toFloat()
        .coerceIn(0f, 1f)
}

internal fun alertThresholdCenteredLogTicks(
    extent: Double
): List<Double> {
    val safeExtent = extent.coerceAtLeast(ALERT_CHART_MIN_LOG_EXTENT)
    return (4 downTo 0).map { step ->
        val fraction = step / 4.0
        100.0 * 10.0.pow((fraction * 2.0 - 1.0) * safeExtent)
    }
}

internal fun alertThresholdAxisFractionDigits(value: Double): Int =
    when {
        value >= 100.0 -> 0
        value >= 10.0 -> 1
        else -> 2
    }

internal data class AlertChartBar(
    val metric: ThresholdAlertMetric,
    val direction: ThresholdAlertDirection,
    val shortLabel: String,
    val color: Color,
    val value: Double,
    val threshold: Double,
    val thresholdPercent: Double
)

internal val AlertChartBar.thresholdDeltaPercent: Double
    get() = thresholdPercent - 100.0

internal fun alertThresholdDeltaPercent(
    value: Double,
    threshold: Double
): Double? =
    if (
        value.isFinite() &&
        threshold.isFinite() &&
        threshold > 0.0
    ) {
        (value / threshold - 1.0) * 100.0
    } else {
        null
    }

internal fun alertChartBars(
    entry: ThresholdAlertLogEntry
): List<AlertChartBar> =
    parseThresholdAlertLogDetails(entry.details)
        .filter { violation ->
            violation.value.isFinite() &&
                violation.rule.threshold.isFinite() &&
                violation.value >= 0.0 &&
                violation.rule.threshold >= 0f
        }
        .map { violation ->
            val threshold = violation.rule.threshold.toDouble()
            val relativePercent =
                when {
                    threshold > 0.0 ->
                        violation.value / threshold * 100.0
                    violation.value == 0.0 -> 100.0
                    else -> 200.0
                }

            AlertChartBar(
                metric = violation.rule.metric,
                direction = violation.rule.direction,
                shortLabel =
                    when (violation.rule.metric) {
                        ThresholdAlertMetric.UV_TOTAL -> "UV"
                        ThresholdAlertMetric.UVC -> "UVC"
                        ThresholdAlertMetric.UVB -> "UVB"
                        ThresholdAlertMetric.UVA -> "UVA"
                        ThresholdAlertMetric.HEV -> "HEV"
                        ThresholdAlertMetric.HEB -> "HEB"
                        ThresholdAlertMetric.VISIBLE_TOTAL -> "VIS"
                        ThresholdAlertMetric.VIOLET -> "V"
                        ThresholdAlertMetric.BLUE -> "B"
                        ThresholdAlertMetric.GREEN -> "G"
                        ThresholdAlertMetric.YELLOW -> "Y"
                        ThresholdAlertMetric.ORANGE -> "O"
                        ThresholdAlertMetric.RED -> "R"
                        ThresholdAlertMetric.NIR_TOTAL -> "IR"
                        ThresholdAlertMetric.FAR_RED -> "FR"
                        ThresholdAlertMetric.NIR -> "NIR"
                        ThresholdAlertMetric.BIO_DNA_UV -> "DNA"
                        ThresholdAlertMetric.BIO_UVA_PHOTOAGING -> "UVA"
                        ThresholdAlertMetric.BIO_HEV_OXIDATIVE -> "HEV"
                    },
                color =
                    thresholdAlertMetricDisplayColor(
                        violation.rule.metric
                    ),
                value = violation.value,
                threshold = threshold,
                thresholdPercent = relativePercent.coerceAtLeast(0.0)
            )
        }
