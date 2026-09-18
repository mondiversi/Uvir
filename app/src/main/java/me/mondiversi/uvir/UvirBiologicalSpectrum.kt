package me.mondiversi.uvir

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun BiologicalEffectsContent(
    sample: SensorSample,
    expanded: Boolean,
    onToggle: () -> Unit,
    showChart: Boolean = false,
    liveHistory: List<LiveSamplePoint> = emptyList(),
    alertedMetrics: Set<ThresholdAlertMetric> = emptySet(),
    configuredAlertMetrics: Set<ThresholdAlertMetric> = emptySet(),
    monitoringAlertMetrics: Set<ThresholdAlertMetric> = emptySet(),
    onConfigureAlerts: (() -> Unit)? = null,
    cardColor: Color,
    primaryText: Color,
    secondaryText: Color
) {
    val estimate = biologicalEffects(sample)
    val metrics = remember {
        listOf(
            ThresholdAlertMetric.BIO_DNA_UV,
            ThresholdAlertMetric.BIO_UVA_PHOTOAGING,
            ThresholdAlertMetric.BIO_HEV_OXIDATIVE
        )
    }
    val anyAlertConfigured =
        metrics.any { it in configuredAlertMetrics }
    val anyAlertMonitoring =
        metrics.any { it in monitoringAlertMetrics }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = cardColor
            )
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .uvirLateralGlow(
                        active = anyAlertMonitoring,
                        color = UvirAttentionColor
                    )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle)
                    .padding(UvirIslandContentPadding),
                verticalAlignment = Alignment.CenterVertically
            ) {
                DnaIcon(color = primaryText)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.biological_effects_view),
                    modifier = Modifier.weight(1f),
                    color = primaryText,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )

                onConfigureAlerts?.let { configureAlerts ->
                    ThresholdAlertBellButton(
                        active = anyAlertConfigured,
                        onClick = configureAlerts,
                        inactiveTint = secondaryText
                    )
                    Spacer(Modifier.width(10.dp))
                }

                ExpansionChevron(
                    expanded = expanded,
                    tint = secondaryText,
                    modifier = Modifier.size(20.dp)
                )
            }

            UvirVerticalReveal(expanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = UvirIslandContentPadding,
                        end = UvirIslandContentPadding,
                        bottom = UvirIslandContentPadding
                    ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                HorizontalDivider(
                    color = secondaryText.copy(alpha = 0.20f)
                )

                if (showChart) {
                    LiveRollingChart(
                        history = liveHistory,
                        series = biologicalLiveChartSeries(),
                        unit = LocalUvirIrradianceUnit.current.unitLabel(equivalent = true),
                        valueScale = LocalUvirIrradianceUnit.current::fromCanonicalUwCm2,
                        outOfRange = { it.isOutOfRange(SensorGroup.BIOLOGICAL) },
                        primaryText = primaryText,
                        secondaryText = secondaryText
                    )
                } else {
                    BiologicalEffectRow(
                        title = stringResource(R.string.dna_uv_proxy),
                        description = stringResource(R.string.dna_uv_proxy_description),
                        value = estimate.dnaUvProxy,
                        outOfRange = sample.isOutOfRange(ThresholdAlertMetric.BIO_DNA_UV),
                        score = estimate.dnaUvScore,
                        color = thresholdAlertMetricDisplayColor(ThresholdAlertMetric.BIO_DNA_UV),
                        alerted = ThresholdAlertMetric.BIO_DNA_UV in alertedMetrics,
                        primaryText = primaryText,
                        secondaryText = secondaryText
                    )

                    BiologicalEffectRow(
                        title = stringResource(R.string.uva_photoaging_proxy),
                        description = stringResource(R.string.uva_photoaging_proxy_description),
                        value = estimate.uvaPhotoagingProxy,
                        outOfRange = sample.isOutOfRange(ThresholdAlertMetric.BIO_UVA_PHOTOAGING),
                        score = estimate.uvaPhotoagingScore,
                        color = thresholdAlertMetricDisplayColor(ThresholdAlertMetric.BIO_UVA_PHOTOAGING),
                        alerted = ThresholdAlertMetric.BIO_UVA_PHOTOAGING in alertedMetrics,
                        primaryText = primaryText,
                        secondaryText = secondaryText
                    )

                    BiologicalEffectRow(
                        title = stringResource(R.string.hev_oxidative_proxy),
                        description = stringResource(R.string.hev_oxidative_proxy_description),
                        value = estimate.hevOxidativeProxy,
                        outOfRange = sample.isOutOfRange(ThresholdAlertMetric.BIO_HEV_OXIDATIVE),
                        score = estimate.hevOxidativeScore,
                        color = thresholdAlertMetricDisplayColor(ThresholdAlertMetric.BIO_HEV_OXIDATIVE),
                        alerted = ThresholdAlertMetric.BIO_HEV_OXIDATIVE in alertedMetrics,
                        primaryText = primaryText,
                        secondaryText = secondaryText
                    )
                }

                HorizontalDivider(
                    color = secondaryText.copy(alpha = 0.16f)
                )

                Text(
                    text = stringResource(R.string.biological_effects_disclaimer),
                    color = secondaryText,
                    fontSize = 10.sp,
                    lineHeight = 13.sp
                )
            }
            }
        }
    }
}

@Composable
private fun BiologicalEffectRow(
    title: String,
    description: String,
    value: Double,
    outOfRange: Boolean,
    score: Float,
    color: Color,
    alerted: Boolean,
    primaryText: Color,
    secondaryText: Color
) {
    val normalizedScore = score.coerceIn(0f, 1f)
    val highlightedColor =
        if (alerted) UvirAttentionColor else primaryText

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = title,
            color = highlightedColor,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text =
                    if (outOfRange) {
                        stringResource(R.string.out_of_range_short)
                    } else {
                        "${formatUvirIrradianceNumber(
                            value,
                            3,
                            LocalUvirNumericFormat.current,
                            LocalUvirIrradianceUnit.current
                        )} ${LocalUvirIrradianceUnit.current.unitLabel(equivalent = true)}"
                    },
                color =
                    if (alerted) UvirAttentionColor else primaryText,
                fontSize = 12.sp
            )

            Text(
                text =
                    stringResource(
                        R.string.biological_compact_relevance,
                        normalizedScore * 100f
                    ),
                color = secondaryText,
                fontSize = 12.sp
            )
        }

        SpectrumBar(
            percent = normalizedScore,
            trackColor = secondaryText.copy(alpha = 0.18f),
            barColor = color
        )
        Text(
            text = description,
            color = secondaryText,
            fontSize = 10.sp,
            lineHeight = 13.sp
        )
    }
}

@Composable
private fun biologicalLiveChartSeries(): List<LiveChartSeries> {
    val dnaTitle = stringResource(R.string.dna_uv_proxy)
    val photoagingTitle = stringResource(R.string.uva_photoaging_proxy)
    val oxidativeTitle = stringResource(R.string.hev_oxidative_proxy)

    return listOf(
        LiveChartSeries(
            label = dnaTitle,
            color =
                thresholdAlertMetricDisplayColor(
                    ThresholdAlertMetric.BIO_DNA_UV
                ),
            value = { biologicalEffects(it).dnaUvProxy }
        ),
        LiveChartSeries(
            label = photoagingTitle,
            color =
                thresholdAlertMetricDisplayColor(
                    ThresholdAlertMetric.BIO_UVA_PHOTOAGING
                ),
            value = { biologicalEffects(it).uvaPhotoagingProxy }
        ),
        LiveChartSeries(
            label = oxidativeTitle,
            color =
                thresholdAlertMetricDisplayColor(
                    ThresholdAlertMetric.BIO_HEV_OXIDATIVE
                ),
            value = { biologicalEffects(it).hevOxidativeProxy }
        )
    )
}
