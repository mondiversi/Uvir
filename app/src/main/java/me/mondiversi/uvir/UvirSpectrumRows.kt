package me.mondiversi.uvir

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SpectrumCard(
    group: SensorGroup,
    title: String,
    total: Double,
    bandLabel: String,
    relativePercent: Float?,
    trackColor: Color,
    barColor: Color,

    expanded: Boolean,
    onToggle: () -> Unit,
    showChart: Boolean,
    liveHistory: List<LiveSamplePoint>,
    liveChartSeries: List<LiveChartSeries>,

    cardColor: Color,
    primaryText: Color,
    secondaryText: Color,
    alerted: Boolean = false,
    alertConfigured: Boolean = false,
    alertMonitoringActive: Boolean = false,
    onAlertClick: (() -> Unit)? = null,
    footnote: String? = null,
    outOfRange: Boolean = false,
    onPercentageBaseToggle: (() -> Unit)? = null,
    chartOutOfRange: ((SensorSample) -> Boolean)? = null,
    reorderControls: (@Composable (String) -> Unit)? = null,
    collapsible: Boolean = true,
    groupVisible: Boolean = true,
    onGroupVisibilityToggle: (() -> Unit)? = null,
    content:
    @Composable ColumnScope.() -> Unit
) {
    val accessibilityState =
        stringResource(
            if (expanded) R.string.accessibility_expanded else R.string.accessibility_collapsed
        )
    val headerInteraction = remember { MutableInteractionSource() }
    val groupMuted = onGroupVisibilityToggle != null && !groupVisible
    val mutedColor = secondaryText.copy(alpha = .55f)
    val titleColor = when {
        groupMuted -> mutedColor
        alerted -> UvirAttentionColor
        else -> primaryText
    }
    val headerAction = when {
        onGroupVisibilityToggle != null -> Modifier.toggleable(
            value = groupVisible, role = Role.Checkbox, interactionSource = headerInteraction,
            indication = null, onValueChange = { onGroupVisibilityToggle() }
        ).uvirAccessibleAction(label = title, role = Role.Checkbox, checkedState = groupVisible,
            preserveChildActions = true, onClick = onGroupVisibilityToggle)
        collapsible -> Modifier.clickable(onClick = onToggle).uvirNestedAccessibleAction(
            label = title, stateText = accessibilityState, onClick = onToggle)
        else -> Modifier
    }
    Card(
        modifier =
            Modifier.fillMaxWidth().testTag("live_spectrum_card_${group.name}"),

        shape =
            RoundedCornerShape(18.dp),

        colors =
            CardDefaults.cardColors(
                containerColor =
                    cardColor
            )
    ) {

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .uvirLateralGlow(
                    active = alertMonitoringActive,
                    color = UvirAttentionColor
                )
        ) {

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (onGroupVisibilityToggle == null) headerAction
                        else Modifier.indication(headerInteraction, ripple(color = primaryText)))
                    .testTag(if (onGroupVisibilityToggle == null) "live_island_header_${group.name}"
                        else "live_island_heading_${group.name}")
                    .padding(UvirIslandContentPadding),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Only the left area toggles visibility. Its interaction source drives
                    // feedback on the full heading without making the other controls toggle it.
                    Row(
                        modifier = Modifier.weight(1f)
                            .then(if (onGroupVisibilityToggle != null) headerAction
                                .testTag("live_island_header_${group.name}") else Modifier),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        UvirSpectrumGroupIcon(
                            group = group,
                            tint = titleColor,
                            modifier = Modifier.testTag("live_group_icon_${group.name}")
                        )
                        Spacer(Modifier.width(8.dp))

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = title,
                                    modifier = Modifier.weight(1f, fill = false).testTag("live_group_title_${group.name}"),
                                    color = titleColor,
                                    fontSize = 16.sp,
                                    lineHeight = 19.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (onGroupVisibilityToggle != null) {
                                    Spacer(Modifier.width(4.dp))
                                    UvirSpectrumVisibilityMark(groupVisible, titleColor,
                                        Modifier.size(16.dp).testTag("live_group_${group.name}_${if (groupVisible) "check" else "cross"}"))
                                }
                                if (collapsible && reorderControls == null && onGroupVisibilityToggle == null) {
                                    Spacer(Modifier.width(5.dp))
                                    ExpansionChevron(expanded = expanded, tint = secondaryText, iconSize = 18.dp)
                                }
                            }

                            Text(
                                text = if (groupMuted) {
                                    "—"
                                } else if (outOfRange) {
                                    stringResource(R.string.out_of_range_short)
                                } else {
                                    "${mainViewIrradianceNumber(total)} ${LocalUvirIrradianceUnit.current.symbol}"
                                },
                                modifier = Modifier.testTag("live_group_total_${group.name}"),
                                color = if (groupMuted) mutedColor else if (alerted) Color(0xFFF57C00) else primaryText,
                                fontSize = 12.sp,
                                lineHeight = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    onAlertClick?.let { alertClick ->
                        ThresholdAlertBellButton(
                            active = alertConfigured,
                            onClick = alertClick,
                            inactiveTint = secondaryText
                        )
                        Spacer(Modifier.width(10.dp))
                    }

                    reorderControls?.invoke(title)

                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text(
                            text = bandLabel,
                            modifier = Modifier
                                .testTag("live_percentage_base_${group.name}")
                                .then(if (onPercentageBaseToggle != null) Modifier.clickable(
                                    role = Role.Button, onClick = onPercentageBaseToggle
                                ).padding(vertical = 2.dp) else Modifier),
                            textDecoration = if (onPercentageBaseToggle != null) TextDecoration.Underline else null,
                            color = if (groupMuted) mutedColor else secondaryText,
                            fontSize = 12.sp,
                            lineHeight = 19.sp,
                            fontWeight = FontWeight.Normal,
                            maxLines = 1
                        )
                        Text(
                            text = relativePercent?.takeUnless { groupMuted }?.let {
                                "${formatUvirNumber(
                                    it.toDouble() * 100.0,
                                    1,
                                    LocalUvirNumericFormat.current
                                )}%"
                            } ?: "—",
                            modifier = Modifier.testTag("live_group_percentage_${group.name}"),
                            color = if (groupMuted) mutedColor else secondaryText,
                            fontSize = 12.sp,
                            lineHeight = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                SpectrumBar(
                    percent = if (groupMuted) 0f else relativePercent ?: 0f,
                    trackColor = trackColor,
                    barColor = barColor,
                    modifier = Modifier.testTag("live_group_bar_${group.name}")
                )
            }

            UvirVerticalReveal(onGroupVisibilityToggle != null || !collapsible || expanded) {
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    UvirIslandHeaderDivider(
                        color = secondaryText.copy(alpha = 0.20f),
                        modifier = Modifier.padding(horizontal = UvirIslandContentPadding)
                    )

                    if (showChart) {
                        Column(Modifier.padding(UvirIslandContentPadding)) {
                            LiveRollingChart(
                                history = liveHistory,
                                series = liveChartSeries,
                                unit = LocalUvirIrradianceUnit.current.symbol,
                                valueScale = LocalUvirIrradianceUnit.current::fromCanonicalWm2,
                                outOfRange = chartOutOfRange ?: { it.isOutOfRange(group) },
                                primaryText = primaryText,
                                secondaryText = secondaryText
                            )
                        }
                    } else {
                        CompositionLocalProvider(
                            LocalUvirDisplayedOutOfRange provides outOfRange,
                            LocalUvirSpectrumRowSideInset provides UvirIslandContentPadding
                        ) {
                            content()
                        }
                    }
                    footnote?.let { note ->
                        Text(
                            text = note,
                            modifier = Modifier.fillMaxWidth().padding(start = UvirIslandContentPadding,
                                end = UvirIslandContentPadding, bottom = UvirIslandContentPadding),
                            color = secondaryText,
                            fontSize = 10.sp,
                            lineHeight = 13.sp
                        )
                    }
                }
            }
        }
    }
}

private val LocalUvirDisplayedOutOfRange =
    compositionLocalOf { false }

// =====================================================
// RIGHE SPETTRALI
// =====================================================

@Composable
fun SpectrumRow(
    name: String,
    band: String,
    value: Double,
    percent: Float?,
    primaryText: Color,
    secondaryText: Color,
    trackColor: Color,
    barColor: Color,
    alerted: Boolean = false,
    alertConfigured: Boolean = false,
    onAlertClick: (() -> Unit)? = null,
    metric: ThresholdAlertMetric? = null,
    visible: Boolean = true,
    onVisibilityToggle: (() -> Unit)? = null
) {
    val rowPrimaryText = if (visible) primaryText else secondaryText.copy(alpha = .55f)
    val rowSecondaryText = if (visible) secondaryText else rowPrimaryText
    val rowNameText = if (alerted && visible) Color(0xFFF57C00) else rowPrimaryText
    val interactive = metric != null && onVisibilityToggle != null
    val sideInset = LocalUvirSpectrumRowSideInset.current
    val topInset = if (sideInset > 0.dp) spectrumRowPressTopInset(metric) else 0.dp
    val bottomInset = if (sideInset > 0.dp) spectrumRowPressBottomInset(metric) else 0.dp
    val rowInteraction = if (interactive) {
        Modifier
            .toggleable(
                value = visible,
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(color = primaryText),
                role = Role.Checkbox,
                onValueChange = { onVisibilityToggle?.invoke() }
            )
    } else Modifier
    Column(
        modifier =
            Modifier.fillMaxWidth()
                .then(rowInteraction)
                .testTag("live_row_${metric?.name ?: name}")
                .padding(start = sideInset, end = sideInset, top = topInset, bottom = bottomInset),

        verticalArrangement =
            Arrangement.spacedBy(2.dp)
    ) {

        Row(
            modifier =
                Modifier.fillMaxWidth(),

            horizontalArrangement =
                Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = name,
                modifier = Modifier.testTag("live_name_${metric?.name ?: name}"),
                color = rowNameText,
                fontSize =
                    16.sp,
                lineHeight = 19.sp,
                fontWeight =
                    FontWeight.Bold
            )

            if (interactive) {
                Spacer(Modifier.width(4.dp))
                // Fixed decorative slot after the name; the full row owns the action.
                Box(Modifier.size(16.dp).testTag("live_selection_slot_${metric?.name}"),
                    contentAlignment = Alignment.Center) {
                    UvirSpectrumVisibilityMark(visible, rowNameText,
                        Modifier.testTag("live_${if (visible) "check" else "cross"}_${metric?.name}"))
                }
            }

            Spacer(
                Modifier.weight(1f)
            )

            Text(
                text = band,
                color = rowSecondaryText,
                fontSize =
                    12.sp,
                lineHeight = 19.sp
            )

        }

        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = 1.dp),

            horizontalArrangement =
                Arrangement.SpaceBetween
        ) {

            Text(
                text =
                    if (!visible) {
                        "—"
                    } else if (LocalUvirDisplayedOutOfRange.current) {
                        stringResource(R.string.out_of_range_short)
                    } else {
                        "${mainViewIrradianceNumber(value)} ${LocalUvirIrradianceUnit.current.symbol}"
                    },
                modifier = Modifier.testTag("live_value_${metric?.name ?: name}"),
                color =
                    if (alerted && visible) {
                        Color(0xFFF57C00)
                    } else {
                        rowPrimaryText
                    },
                fontSize =
                    12.sp,
                lineHeight = 15.sp
            )

            Text(
                text =
                    percent?.takeIf { visible }?.let { "${formatUvirNumber(
                        it.toDouble() * 100.0,
                        1,
                        LocalUvirNumericFormat.current
                    )}%" } ?: "—",
                modifier = Modifier.testTag("live_percentage_${metric?.name ?: name}"),
                color = rowSecondaryText,
                fontSize =
                    12.sp,
                lineHeight = 15.sp
            )
        }

        SpectrumBar(
            percent = if (visible) percent ?: 0f else 0f,
            trackColor = trackColor,
            barColor = barColor,
            modifier = Modifier.testTag("live_bar_${metric?.name ?: name}")
        )
    }
}

@Composable
fun DerivedSpectrumRow(
    name: String,
    subtitle: String,
    band: String,
    value: Double,
    percent: Float,
    primaryText: Color,
    secondaryText: Color,
    trackColor: Color,
    barColor: Color,
    alerted: Boolean = false,
    alertConfigured: Boolean = false,
    onAlertClick: (() -> Unit)? = null
) {

    Column(
        modifier =
            Modifier.fillMaxWidth(),

        verticalArrangement =
            Arrangement.spacedBy(4.dp)
    ) {

        Row(
            modifier =
                Modifier.fillMaxWidth(),

            horizontalArrangement =
                Arrangement.SpaceBetween
        ) {

            Column(
                modifier =
                    Modifier.weight(1f)
            ) {

                Text(
                    text = name,
                    color =
                        if (alerted) {
                            Color(0xFFF57C00)
                        } else {
                            primaryText
                        },
                    fontSize =
                        16.sp,
                    fontWeight =
                        FontWeight.Bold
                )

                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        color =
                            if (alerted) {
                                Color(0xFFF57C00)
                            } else {
                                secondaryText
                            },
                        fontSize =
                            10.sp
                    )
                }
            }

            Text(
                text = band,
                color = secondaryText,
                fontSize =
                    12.sp
            )

        }

        Row(
            modifier =
                Modifier.fillMaxWidth(),

            horizontalArrangement =
                Arrangement.SpaceBetween
        ) {

            Text(
                text =
                    if (LocalUvirDisplayedOutOfRange.current) {
                        stringResource(R.string.out_of_range_short)
                    } else {
                        "${mainViewIrradianceNumber(value)} ${LocalUvirIrradianceUnit.current.symbol}"
                    },
                color =
                    if (alerted) {
                        Color(0xFFF57C00)
                    } else {
                        primaryText
                    },
                fontSize =
                    12.sp
            )

            Text(
                text =
                    stringResource(
                        R.string.percent_of_visible,
                        formatUvirNumber(
                            percent.toDouble() * 100.0,
                            1,
                            LocalUvirNumericFormat.current
                        )
                    ),
                color =
                    secondaryText,
                fontSize =
                    12.sp
            )
        }

        SpectrumBar(
            percent = percent,
            trackColor = trackColor,
            barColor = barColor
        )
    }
}

@Composable
fun SpectrumBar(
    percent: Float,
    trackColor: Color,
    barColor: Color,
    modifier: Modifier = Modifier
) {

    val fraction =
        (percent.takeIf { it.isFinite() } ?: 0f).coerceIn(
            0f,
            1f
        )

    Canvas(
        modifier =
            modifier
                .fillMaxWidth()
                .height(7.dp)
                .semantics { progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f) }
    ) {
        val cornerRadius =
            CornerRadius(
                size.height / 2f,
                size.height / 2f
            )

        drawRoundRect(
            color = trackColor,
            cornerRadius = cornerRadius
        )

        if (fraction > 0f) {
            drawRoundRect(
                color = barColor,
                size =
                    Size(
                        size.width * fraction,
                        size.height
                    ),
                cornerRadius = cornerRadius
            )
        }
    }
}
