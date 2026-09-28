package me.mondiversi.uvir

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
    secondaryText: Color,
    onOpenRecord: ((SavedRecordDetail) -> Unit)? = null
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
        secondaryText = secondaryText,
        titleFontWeight = if (group.biological) FontWeight.SemiBold else FontWeight.Bold
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
                modifier = Modifier.padding(vertical = 15.dp),
                verticalArrangement = Arrangement.spacedBy(15.dp)
            ) {
                records.forEachIndexed { recordIndex, record ->
                    if (showRecordHeader) {
                        val chevronInteractionSource =
                            remember(record.id) { MutableInteractionSource() }
                        val chevronPressed by
                            chevronInteractionSource.collectIsPressedAsState()
                        val elapsedText =
                            sessionRecordElapsedText(
                                currentTimestamp = record.timestamp,
                                previousTimestamp =
                                    records.getOrNull(recordIndex - 1)?.timestamp
                            )
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(
                                        horizontal = 16.dp,
                                        vertical = 0.dp
                                    ),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
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
                            onOpenRecord?.let { openRecord ->
                                Box(
                                    modifier =
                                        Modifier
                                            .size(32.dp)
                                            .clickable(
                                                interactionSource =
                                                    chevronInteractionSource,
                                                indication = null,
                                                onClick = { openRecord(record) }
                                            ),
                                    contentAlignment = Alignment.CenterEnd
                                ) {
                                    Box(
                                        modifier =
                                            Modifier
                                                .size(28.dp)
                                                .background(
                                                    color = if (chevronPressed) {
                                                        secondaryText.copy(alpha = 0.10f)
                                                    } else {
                                                        Color.Transparent
                                                    },
                                                    shape = CircleShape
                                                ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        UvirDisclosureChevron(tint = secondaryText)
                                    }
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
                        Column(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
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
                                    fontWeight = if (itemIndex == 0 && !group.biological) {
                                        FontWeight.Bold
                                    } else {
                                        FontWeight.SemiBold
                                    }
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
                                    fontWeight = if (itemIndex == 0 && !group.biological) {
                                        FontWeight.Bold
                                    } else {
                                        FontWeight.Normal
                                    },
                                    maxLines = 1
                                )

                                if (!group.biological) {
                                    val total = series.first().values[recordIndex]
                                    val contribution =
                                        if (itemIndex == 0) {
                                            record.sample.spectralTotals()
                                                .share(group.spectrumIconGroup())
                                                ?.let { share ->
                                                    stringResource(
                                                        R.string.biological_compact_relevance,
                                                        share * 100f
                                                    )
                                                } ?: "—"
                                        } else if (showRelativeBreakdown && !valueOutOfRange) {
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
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
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
