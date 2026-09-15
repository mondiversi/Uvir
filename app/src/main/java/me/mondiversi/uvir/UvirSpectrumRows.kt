package me.mondiversi.uvir

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SpectrumCard(
    group: SensorGroup,
    title: String,
    total: Double,
    unit: String,

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

    content:
    @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier =
            Modifier.fillMaxWidth(),

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

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        onToggle()
                    }
                    .padding(UvirIslandContentPadding),

                verticalAlignment =
                    Alignment.CenterVertically
            ) {

                UvirSpectrumGroupIcon(
                    group = group,
                    tint = if (alerted) UvirAttentionColor else primaryText
                )
                Spacer(Modifier.width(8.dp))

                Column(
                    modifier =
                        Modifier.weight(1f)
                ) {

                    Text(
                        text = title,
                        color = if (alerted) UvirAttentionColor else primaryText,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text =
                            "${formatUvirNumber(
                                total,
                                1,
                                LocalUvirNumericFormat.current
                            )} $unit",
                        color =
                            if (alerted) {
                                Color(0xFFF57C00)
                            } else {
                                primaryText
                            },
                        fontSize =
                            13.sp
                    )
                }

                onAlertClick?.let { alertClick ->
                    ThresholdAlertBellButton(
                        active = alertConfigured,
                        onClick = alertClick,
                        inactiveTint = secondaryText
                    )

                    Spacer(
                        Modifier.width(10.dp)
                    )
                }

                ExpansionChevron(
                    expanded = expanded,
                    tint = secondaryText,
                    modifier =
                        Modifier.size(20.dp)
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
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(secondaryText.copy(alpha = 0.20f))
                    )

                    if (showChart) {
                        LiveRollingChart(
                            history = liveHistory,
                            series = liveChartSeries,
                            unit = "µW/cm²",
                            primaryText = primaryText,
                            secondaryText = secondaryText
                        )
                    } else {
                        content()
                    }
                    footnote?.let { note ->
                        Text(
                            text = note,
                            modifier = Modifier.fillMaxWidth(),
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

// =====================================================
// RIGHE SPETTRALI
// =====================================================

@Composable
fun SpectrumRow(
    name: String,
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

            Spacer(
                Modifier.weight(1f)
            )

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
                    "${formatUvirNumber(
                        value,
                        1,
                        LocalUvirNumericFormat.current
                    )} µW/cm²",
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
                    "${formatUvirNumber(
                        percent.toDouble() * 100.0,
                        1,
                        LocalUvirNumericFormat.current
                    )}%",
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
                    "${formatUvirNumber(
                        value,
                        1,
                        LocalUvirNumericFormat.current
                    )} µW/cm²",
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
    barColor: Color
) {

    val fraction =
        percent.coerceIn(
            0f,
            1f
        )

    Canvas(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(7.dp)
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
