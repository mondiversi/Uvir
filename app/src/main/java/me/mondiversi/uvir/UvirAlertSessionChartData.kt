package me.mondiversi.uvir

import androidx.compose.ui.graphics.Color

internal data class AlertSessionChartPoint(
    val timestamp: Long,
    val value: Double,
    val threshold: Double
)

internal data class AlertSessionChartSeries(
    val metric: ThresholdAlertMetric,
    val direction: ThresholdAlertDirection,
    val color: Color,
    val points: List<AlertSessionChartPoint>
)

internal fun AlertSessionChartSeries.usesPercentageScale(): Boolean =
    points.isNotEmpty() && points.all { it.threshold > 0.0 }

internal fun AlertSessionChartPoint.chartValue(
    percentageScale: Boolean
): Double =
    if (percentageScale) {
        value / threshold * 100.0
    } else {
        value
    }

internal fun AlertSessionChartPoint.chartThreshold(
    percentageScale: Boolean
): Double =
    if (percentageScale) 100.0 else threshold

internal fun alertSessionChartSeries(
    entries: List<ThresholdAlertLogEntry>
): List<AlertSessionChartSeries> {
    val pointsByMetric =
        linkedMapOf<ThresholdAlertMetric, MutableList<Pair<ThresholdAlertDirection, AlertSessionChartPoint>>>()

    entries.sortedBy { it.timestamp }.forEach { entry ->
        parseThresholdAlertLogDetails(entry.details).forEach { violation ->
            val point =
                AlertSessionChartPoint(
                    timestamp = entry.timestamp,
                    value = violation.value,
                    threshold = violation.rule.threshold.toDouble()
                )
            pointsByMetric
                .getOrPut(violation.rule.metric) { mutableListOf() }
                .add(violation.rule.direction to point)
        }
    }

    return pointsByMetric.mapNotNull { (metric, values) ->
        val valid =
            values.filter { (_, point) ->
                point.value.isFinite() &&
                    point.threshold.isFinite() &&
                    point.value >= 0.0 &&
                    point.threshold >= 0.0
            }
        valid.firstOrNull()?.let { (direction, _) ->
            AlertSessionChartSeries(
                metric = metric,
                direction = direction,
                color = thresholdAlertMetricDisplayColor(metric),
                points = valid.map { it.second }
            )
        }
    }
}
