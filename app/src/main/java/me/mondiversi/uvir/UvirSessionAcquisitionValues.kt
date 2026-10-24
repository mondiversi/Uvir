package me.mondiversi.uvir

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
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
    secondaryText: Color,
    display: UvirSavedValueDisplay,
    onDisplayChange: (UvirSavedValueDisplay) -> Unit,
    onOpenRecord: ((SavedRecordDetail) -> Unit)? = null
) {
    val series =
        remember(records, group) {
            sessionChartSeries(records, group)
        }
    val metrics = remember(group) { savedAcquisitionMetrics(group) }
    val numericFormat = LocalUvirNumericFormat.current
    val irradianceUnit = LocalUvirIrradianceUnit.current
    val displayDigits = currentUvirLiveIrradianceDecimals()

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
        secondaryText = secondaryText,
        titleFontWeight = if (group.biological) FontWeight.SemiBold else FontWeight.Bold,
        titleColor = if (!group.biological && !display.spectrum.groupVisible(group.spectrumIconGroup()))
            secondaryText.copy(alpha = .55f) else primaryText,
        headerTrailing = if (!group.biological && showRelativeBreakdown) {
            { UvirSavedPercentageBase(group, display, onDisplayChange, secondaryText) }
        } else null
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
                modifier = Modifier.padding(vertical = UvirSavedValueHalfGap)
            ) {
                records.forEachIndexed { recordIndex, record ->
                    if (showRecordHeader) {
                        val elapsedText =
                            sessionRecordElapsedText(
                                currentTimestamp = record.timestamp,
                                previousTimestamp =
                                    records.getOrNull(recordIndex - 1)?.timestamp
                            )
                        UvirSessionRecordHeader("acquisition_${group.name}_${record.id}", primaryText, secondaryText,
                            onOpenRecord?.let { open -> { open(record) } }) {
                            Row(
                                modifier = Modifier.weight(1f),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text =
                                        "#${record.sessionSequence ?: recordIndex + 1}",
                                    color = primaryText,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                                if (
                                    record.positionIndex != null &&
                                    record.variantIndex != null
                                ) {
                                    Text(
                                        text =
                                            "(" +
                                                uvirCompactVariantPositionLabel(
                                                    variantIndex = record.variantIndex,
                                                    positionIndex = record.positionIndex
                                                ) +
                                                ")",
                                        color = secondaryText,
                                        fontSize = 12.sp,
                                        lineHeight = 15.sp,
                                        fontWeight = FontWeight.Normal,
                                        maxLines = 1
                                    )
                                }
                            }
                            Column(
                                horizontalAlignment = Alignment.End,
                                verticalArrangement = Arrangement.spacedBy(0.dp)
                            ) {
                                Text(
                                    text = formatDateTime(
                                        record.timestamp,
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
                        val metric = metrics[itemIndex]
                        val totalRow = itemIndex == 0 && !group.biological
                        val included = display.acquisitionVisible(metric)
                        val key = "acquisition_${record.id}_${metric.name}"
                        val value = if (group.biological) item.values[recordIndex] else display.acquisitionValue(metric, record.sample)
                        val valueColor = if (included) primaryText else secondaryText.copy(alpha = .55f)
                        UvirSavedValueRow(key, included, primaryText,
                            onToggle = { onDisplayChange(display.toggleAcquisition(metric)) }) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically) {
                                    UvirSpectralDot(metric, Modifier.testTag("saved_dot_$key"),
                                        tint = if (included) uvirSpectralColor(metric) else valueColor)
                                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = item.displayLabelResource?.let { stringResource(it) } ?: item.label,
                                            modifier = Modifier.weight(1f, fill = false).testTag("saved_name_$key"),
                                            color = valueColor,
                                            fontSize = 12.sp,
                                            lineHeight = 14.sp,
                                            fontWeight = if (itemIndex == 0 && !group.biological) FontWeight.Bold else FontWeight.SemiBold
                                        )
                                        UvirSavedValueMark(key, included, valueColor)
                                    }
                                }
                                val valueOutOfRange = display.acquisitionOutOfRange(metric, record.sample)
                                Text(
                                    text =
                                        if (!included) {
                                            "—"
                                        } else if (valueOutOfRange) {
                                            stringResource(R.string.out_of_range_short)
                                        } else {
                                            displayedUvirIrradianceNumber(value)
                                        },
                                    modifier = Modifier.testTag("saved_value_$key"),
                                    color = valueColor,
                                    fontSize = 12.sp,
                                    fontWeight = if (itemIndex == 0 && !group.biological) {
                                        FontWeight.Bold
                                    } else {
                                        FontWeight.Normal
                                    },
                                    maxLines = 1
                                )

                                if (!group.biological) {
                                    val contribution =
                                        if (!included) {
                                            "—"
                                        } else if (totalRow || showRelativeBreakdown) {
                                            display.share(group, totalRow, value, record.sample, displayDigits, irradianceUnit)
                                                ?.let { share ->
                                                    formatUvirNumber(share.toDouble() * 100.0, 1, numericFormat) + "%"
                                                } ?: "—"
                                        } else {
                                            ""
                                        }

                                    Text(
                                        text = contribution,
                                        modifier = Modifier.width(44.dp).testTag("saved_percentage_$key"),
                                        color = valueColor,
                                        fontSize = 12.sp,
                                        fontWeight = if (itemIndex == 0) {
                                            FontWeight.Bold
                                        } else {
                                            FontWeight.Normal
                                        },
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
                                        color = if (included) secondaryText else valueColor,
                                        fontSize = 11.sp
                                    )
                                    Text(
                                        text =
                                            if (!included) "—" else stringResource(
                                                R.string.biological_compact_relevance,
                                                biologicalRelevance[itemIndex] * 100f
                                            ),
                                        color = valueColor,
                                        fontSize = 11.sp,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }

                    if (showRecordHeader && recordIndex < records.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = UvirSavedValueHalfGap),
                            color = secondaryText.copy(alpha = 0.16f)
                        )
                    }
                }

            }
        }
    }
}
