package me.mondiversi.uvir

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun SessionAcquisitionValuesCard(
    records: List<SavedRecordDetail>,
    group: SessionChartGroup,
    showRecordHeader: Boolean = true,
    showRelativeBreakdown: Boolean = false,
    expanded: Boolean,
    onToggle: () -> Unit,
    cardColor: Color,
    primaryText: Color,
    secondaryText: Color
) {
    val series =
        remember(records, group) {
            sessionChartSeries(records, group)
        }
    val darkMode = isSystemInDarkTheme()
    val numericFormat = LocalUvirNumericFormat.current
    val irradianceUnit = LocalUvirIrradianceUnit.current

    UvirCollapsibleChartCard(
        title = stringResource(group.titleResource),
        iconGroup = group.spectrumIconGroup(),
        subtitle =
            stringResource(
                if (group.biological) {
                    R.string.session_chart_unit_biological
                } else {
                    R.string.session_chart_unit
                }
            ).withUvirIrradianceUnit(irradianceUnit),
        expanded = expanded,
        onToggle = onToggle,
        cardColor = cardColor,
        primaryText = primaryText,
        secondaryText = secondaryText
    ) {
        if (records.isEmpty()) {
            Text(
                text = stringResource(R.string.session_chart_empty),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                color = secondaryText,
                textAlign = TextAlign.Center
            )
        } else {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                records.forEachIndexed { recordIndex, record ->
                    if (showRecordHeader) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text =
                                    "#${record.sessionSequence ?: recordIndex + 1}",
                                modifier = Modifier.weight(1f),
                                color = primaryText,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = formatDateTime(
                                    record.timestamp,
                                    LocalUvirDateFormat.current,
                                    LocalUvirTimeFormat.current
                                ),
                                color = secondaryText,
                                fontSize = 11.sp,
                                maxLines = 1
                            )
                        }
                    }

                    val biologicalRelevance =
                        if (showRelativeBreakdown && group.biological) {
                            biologicalEffects(record.sample).let { effects ->
                                listOf(
                                    effects.dnaUvScore,
                                    effects.uvaPhotoagingScore,
                                    effects.hevOxidativeScore
                                )
                            }
                        } else {
                            emptyList()
                        }

                    series.forEachIndexed { itemIndex, item ->
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text =
                                        item.displayLabelResource
                                            ?.let { resource ->
                                                stringResource(resource)
                                            }
                                            ?: item.label,
                                    modifier = Modifier.weight(1f),
                                    color =
                                        uvirSessionValueLabelColor(
                                            base = item.color,
                                            darkMode = darkMode
                                        ),
                                    fontSize = 12.sp,
                                    lineHeight = 14.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                val valueOutOfRange =
                                    if (group.biological) {
                                        record.sample.isOutOfRange(
                                            if (itemIndex < 2) SensorGroup.UV
                                            else SensorGroup.VISIBLE
                                        )
                                    } else {
                                        record.sample.isOutOfRange(group.spectrumIconGroup())
                                    }
                                Text(
                                    text =
                                        if (valueOutOfRange) {
                                            stringResource(R.string.out_of_range_short)
                                        } else {
                                            formatUvirIrradianceNumber(
                                                item.values[recordIndex],
                                                3,
                                                numericFormat,
                                                irradianceUnit
                                            )
                                        },
                                    color = primaryText,
                                    fontSize = 12.sp,
                                    maxLines = 1
                                )

                                if (showRelativeBreakdown && !group.biological) {
                                    val total = series.first().values[recordIndex]
                                    val showsContribution =
                                        itemIndex > 0
                                    val contribution =
                                        if (showsContribution) {
                                            stringResource(
                                                R.string.biological_compact_relevance,
                                                percentage(
                                                    item.values[recordIndex],
                                                    total
                                                ) * 100f
                                            )
                                        } else {
                                            ""
                                        }

                                    Text(
                                        text = contribution,
                                        modifier = Modifier.width(44.dp),
                                        color = primaryText,
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                        textAlign = TextAlign.End
                                    )
                                }
                            }

                            if (showRelativeBreakdown && group.biological) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = stringResource(R.string.relative_spectral_index),
                                        modifier = Modifier.weight(1f),
                                        color = secondaryText,
                                        fontSize = 11.sp
                                    )
                                    Text(
                                        text =
                                            stringResource(
                                                R.string.biological_compact_relevance,
                                                biologicalRelevance[itemIndex] * 100f
                                            ),
                                        color = primaryText,
                                        fontSize = 11.sp,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }

                    if (showRecordHeader && recordIndex < records.lastIndex) {
                        Spacer(Modifier.height(1.dp))
                        HorizontalDivider(
                            color = secondaryText.copy(alpha = 0.16f)
                        )
                    }
                }

            }
        }
    }
}

internal fun uvirSessionValueLabelColor(
    base: Color,
    darkMode: Boolean
): Color =
    if (darkMode) {
        lerp(base, Color.White, 0.28f)
    } else {
        lerp(base, Color.Black, 0.18f)
    }
