package me.mondiversi.uvir

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
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
    secondaryText: Color,
    onOpenAlert: ((ThresholdAlertLogEntry) -> Unit)? = null
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
            ).withUvirIrradianceUnit(LocalUvirIrradianceUnit.current),
        expanded = expanded,
        onToggle = onToggle,
        cardColor = cardColor,
        primaryText = primaryText,
        secondaryText = secondaryText
    ) {
        Column(
            modifier = Modifier.padding(vertical = 15.dp),
            verticalArrangement = Arrangement.spacedBy(15.dp)
        ) {
            events.forEachIndexed { eventIndex, event ->
                val sequence = event.entry.sessionSequence ?: eventIndex + 1
                val elapsedText =
                    sessionRecordElapsedText(
                        currentTimestamp = event.entry.timestamp,
                        previousTimestamp =
                            events.getOrNull(eventIndex - 1)?.entry?.timestamp
                    )

                UvirSessionRecordHeader("alert_${biologicalEffects}_${event.entry.id}", primaryText, secondaryText,
                    onOpenAlert?.let { open -> { open(event.entry) } }) {
                    Text(
                        text = "#$sequence",
                        modifier = Modifier.weight(1f),
                        color = primaryText,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(0.dp)
                    ) {
                        Text(
                            text = formatDateTime(
                                event.entry.timestamp,
                                LocalUvirDateFormat.current,
                                LocalUvirTimeFormat.current
                            ),
                            color = secondaryText,
                            fontSize = 11.sp,
                            lineHeight = 12.sp,
                            maxLines = 1
                        )
                        elapsedText?.let { elapsed ->
                            Text(
                                text = elapsed,
                                color = secondaryText,
                                fontSize = 10.sp,
                                lineHeight = 10.sp,
                                maxLines = 1
                            )
                        }
                    }
                }

                event.violations
                    .sortedBy { it.rule.metric.ordinal }
                    .forEach { violation ->
                        Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                            AlertViolationDataRow(
                                violation = violation,
                                qualityFlags = event.entry.qualityFlags,
                                primaryText = primaryText,
                                secondaryText = secondaryText,
                                key = "alert_${event.entry.id}_${violation.rule.metric.name}"
                            )
                        }
                    }

                if (eventIndex < events.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
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
    qualityFlags: Int = 0,
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
            ).withUvirIrradianceUnit(LocalUvirIrradianceUnit.current),
        expanded = expanded,
        onToggle = onToggle,
        cardColor = cardColor,
        primaryText = primaryText,
        secondaryText = secondaryText
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 15.dp),
            verticalArrangement = Arrangement.spacedBy(15.dp)
        ) {
            violations
                .sortedBy { it.rule.metric.ordinal }
                .forEach { violation ->
                    AlertViolationDataRow(
                        violation = violation,
                        qualityFlags = qualityFlags,
                        primaryText = primaryText,
                        secondaryText = secondaryText,
                        key = "alert_single_${violation.rule.metric.name}"
                    )
                }
        }
    }
}

@Composable
private fun AlertViolationDataRow(
    violation: ThresholdAlertViolation,
    qualityFlags: Int,
    primaryText: Color,
    secondaryText: Color,
    key: String
) {
    val metricLabel = stringResource(thresholdAlertMetricLabelResource(violation.rule.metric))
    val symbol =
        if (violation.rule.direction == ThresholdAlertDirection.ABOVE) {
            "≥"
        } else {
            "≤"
        }
    val numericFormat = LocalUvirNumericFormat.current
    val outOfRange = qualityFlags.isOutOfRange(violation.rule.metric)
    val recordedValue =
        if (outOfRange) {
            stringResource(R.string.out_of_range_short)
        } else {
            displayedUvirIrradianceNumber(violation.value)
        }
    val thresholdValue =
        "$symbol " +
            displayedUvirIrradianceNumber(violation.rule.threshold.toDouble())
    val delta =
        alertThresholdDeltaPercent(
            value = violation.value,
            threshold = violation.rule.threshold.toDouble()
        )
    val deltaText =
        if (outOfRange) {
            "—"
        } else delta?.let { value ->
            val sign =
                when {
                    value > 0.0 -> "+"
                    value < 0.0 -> "−"
                    else -> ""
                }
            "$sign${formatUvirNumber(abs(value), 1, numericFormat)}%"
        } ?: "—"

    Row(
        modifier = Modifier.fillMaxWidth().testTag("saved_row_$key"),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            UvirSpectralDot(violation.rule.metric, Modifier.testTag("saved_dot_$key"))
            Text(
                text = metricLabel,
                modifier = Modifier.weight(1f).testTag("saved_name_$key"),
                color = primaryText,
                fontSize = 12.sp,
                lineHeight = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = recordedValue,
                modifier = Modifier.testTag("saved_value_$key"),
                color = primaryText,
                fontSize = 12.sp,
                textAlign = TextAlign.End,
                maxLines = 1,
                softWrap = false
            )
            Text(
                text = thresholdValue,
                modifier = Modifier.testTag("saved_threshold_$key"),
                color = secondaryText,
                fontSize = 10.sp,
                textAlign = TextAlign.End,
                maxLines = 1,
                softWrap = false
            )
        }
        Text(
            text = deltaText,
            modifier = Modifier.width(72.dp).testTag("saved_percentage_$key"),
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
