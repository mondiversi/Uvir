package me.mondiversi.uvir

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs

internal data class AlertSessionValueEvent(
    val entry: ThresholdAlertLogEntry,
    val violations: List<ThresholdAlertViolation>
)

internal fun alertSessionValueEvents(
    entries: List<ThresholdAlertLogEntry>,
    biologicalEffects: Boolean
): List<AlertSessionValueEvent> =
    entries
        .sortedBy { it.timestamp }
        .mapNotNull { entry ->
            val violations =
                parseThresholdAlertLogDetails(entry.details)
                    .filter { violation ->
                        violation.rule.metric.isBiologicalEffect() ==
                            biologicalEffects
                    }
            if (violations.isEmpty()) {
                null
            } else {
                AlertSessionValueEvent(
                    entry = entry,
                    violations = violations
                )
            }
        }

@Composable
internal fun AlertSessionValuesCard(
    events: List<AlertSessionValueEvent>,
    biologicalEffects: Boolean,
    expanded: Boolean,
    onToggle: () -> Unit,
    cardColor: Color,
    primaryText: Color,
    secondaryText: Color
) {
    UvirCollapsibleChartCard(
        iconViewMode = if (biologicalEffects) ViewMode.BIOLOGICAL_EFFECTS else ViewMode.IRRADIANCE,
        title =
            stringResource(
                if (biologicalEffects) {
                    R.string.biological_effects_group_name
                } else {
                    R.string.irradiance_view
                }
            ),
        subtitle =
            stringResource(
                if (biologicalEffects) {
                    R.string.session_chart_unit_biological
                } else {
                    R.string.session_chart_unit
                }
            ),
        expanded = expanded,
        onToggle = onToggle,
        cardColor = cardColor,
        primaryText = primaryText,
        secondaryText = secondaryText
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            events.forEachIndexed { eventIndex, event ->
                val sequence = event.entry.sessionSequence ?: eventIndex + 1

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "#$sequence",
                        modifier = Modifier.weight(1f),
                        color = primaryText,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = formatDateTime(event.entry.timestamp),
                        color = secondaryText,
                        fontSize = 11.sp,
                        maxLines = 1
                    )
                }

                event.violations
                    .sortedBy { it.rule.metric.ordinal }
                    .forEach { violation ->
                        AlertViolationDataRow(
                            violation = violation,
                            primaryText = primaryText,
                            secondaryText = secondaryText
                        )
                    }

                if (eventIndex < events.lastIndex) {
                    HorizontalDivider(
                        color = secondaryText.copy(alpha = 0.16f)
                    )
                }
            }
        }
    }
}

@Composable
internal fun AlertViolationDataCard(
    biologicalEffects: Boolean,
    violations: List<ThresholdAlertViolation>,
    expanded: Boolean,
    onToggle: () -> Unit,
    cardColor: Color,
    primaryText: Color,
    secondaryText: Color
) {
    UvirCollapsibleChartCard(
        iconViewMode = if (biologicalEffects) ViewMode.BIOLOGICAL_EFFECTS else ViewMode.IRRADIANCE,
        title =
            stringResource(
                if (biologicalEffects) {
                    R.string.biological_effects_group_name
                } else {
                    R.string.irradiance_view
                }
            ),
        subtitle =
            stringResource(
                if (biologicalEffects) {
                    R.string.session_chart_unit_biological
                } else {
                    R.string.session_chart_unit
                }
            ),
        expanded = expanded,
        onToggle = onToggle,
        cardColor = cardColor,
        primaryText = primaryText,
        secondaryText = secondaryText
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            violations
                .sortedBy { it.rule.metric.ordinal }
                .forEach { violation ->
                    AlertViolationDataRow(
                        violation = violation,
                        primaryText = primaryText,
                        secondaryText = secondaryText
                    )
                }
        }
    }
}

@Composable
private fun AlertViolationDataRow(
    violation: ThresholdAlertViolation,
    primaryText: Color,
    secondaryText: Color
) {
    val metricLabel =
        stringResource(
            thresholdAlertMetricLabelResource(
                violation.rule.metric
            )
        )
    val symbol =
        if (violation.rule.direction == ThresholdAlertDirection.ABOVE) {
            "≥"
        } else {
            "≤"
        }
    val numericFormat = LocalUvirNumericFormat.current
    val recordedValue =
        formatUvirNumber(violation.value, 3, numericFormat)
    val thresholdValue =
        "$symbol " +
            formatUvirNumber(
                violation.rule.threshold.toDouble(),
                3,
                numericFormat
            )
    val delta =
        alertThresholdDeltaPercent(
            value = violation.value,
            threshold = violation.rule.threshold.toDouble()
        )
    val deltaText =
        delta?.let { value ->
            val sign =
                when {
                    value > 0.0 -> "+"
                    value < 0.0 -> "−"
                    else -> ""
                }
            "$sign${formatUvirNumber(abs(value), 1, numericFormat)}%"
        } ?: "—"

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = metricLabel,
            modifier = Modifier.weight(1f),
            color =
                uvirSessionValueLabelColor(
                    base =
                        thresholdAlertMetricDisplayColor(
                            violation.rule.metric
                        ),
                    darkMode = isSystemInDarkTheme()
                ),
            fontSize = 12.sp,
            lineHeight = 14.sp,
            fontWeight = FontWeight.SemiBold
        )
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = recordedValue,
                color = primaryText,
                fontSize = 12.sp,
                textAlign = TextAlign.End,
                maxLines = 1,
                softWrap = false
            )
            Text(
                text = thresholdValue,
                color = secondaryText,
                fontSize = 10.sp,
                textAlign = TextAlign.End,
                maxLines = 1,
                softWrap = false
            )
        }
        Text(
            text = deltaText,
            modifier = Modifier.width(72.dp),
            color = primaryText,
            fontSize = 12.sp,
            textAlign = TextAlign.End,
            maxLines = 1,
            softWrap = false
        )
    }
}

internal fun ThresholdAlertMetric.alertSessionGroup(): SessionChartGroup =
    when (this) {
        ThresholdAlertMetric.UV_TOTAL,
        ThresholdAlertMetric.UVC,
        ThresholdAlertMetric.UVB,
        ThresholdAlertMetric.UVA -> SessionChartGroup.UV

        ThresholdAlertMetric.HEV,
        ThresholdAlertMetric.HEB,
        ThresholdAlertMetric.VISIBLE_TOTAL,
        ThresholdAlertMetric.VIOLET,
        ThresholdAlertMetric.BLUE,
        ThresholdAlertMetric.GREEN,
        ThresholdAlertMetric.YELLOW,
        ThresholdAlertMetric.ORANGE,
        ThresholdAlertMetric.RED -> SessionChartGroup.VISIBLE

        ThresholdAlertMetric.NIR_TOTAL,
        ThresholdAlertMetric.FAR_RED,
        ThresholdAlertMetric.NIR -> SessionChartGroup.FAR_RED_NIR

        ThresholdAlertMetric.BIO_DNA_UV,
        ThresholdAlertMetric.BIO_UVA_PHOTOAGING,
        ThresholdAlertMetric.BIO_HEV_OXIDATIVE -> SessionChartGroup.BIOLOGICAL
    }
