package me.mondiversi.uvir

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.material3.Text
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal const val LIVE_CHART_WINDOW_MILLIS = 60_000L
internal const val LIVE_CHART_MAX_POINTS = 600

data class LiveSamplePoint(
    val timestamp: Long,
    val sample: SensorSample
)

data class LiveChartSeries(
    val label: String,
    val color: Color,
    val value: (SensorSample) -> Double
)

@Composable
internal fun LiveIslandModeButton(
    showChart: Boolean,
    onToggle: () -> Unit,
    tint: Color
) {
    val description =
        stringResource(
            if (showChart) {
                R.string.show_live_values
            } else {
                R.string.show_live_chart
            }
        )

    Box(
        modifier =
            Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(tint.copy(alpha = 0.09f))
                .clickable(onClick = onToggle)
                .semantics {
                    contentDescription = description
                },
        contentAlignment = Alignment.Center
    ) {
        LiveIslandModeIcon(
            showChart = showChart,
            tint = tint,
            modifier = Modifier.size(19.dp)
        )
    }
}

@Composable
internal fun LiveIslandModeIcon(
    showChart: Boolean,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier =
            modifier
                .size(UvirMeasurementSelectorIconSize)
                .graphicsLayer(alpha = tint.alpha)
    ) {
        val tint = tint.copy(alpha = 1f)
        val stroke = UvirMeasurementSelectorIconStrokeWidth.toPx()

        if (showChart) {
                drawLine(
                    color = tint,
                    start = Offset(size.width * 0.08f, size.height * 0.90f),
                    end = Offset(size.width * 0.08f, size.height * 0.10f),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = tint,
                    start = Offset(size.width * 0.08f, size.height * 0.90f),
                    end = Offset(size.width * 0.94f, size.height * 0.90f),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round
                )

                val path = Path().apply {
                    moveTo(size.width * 0.16f, size.height * 0.72f)
                    lineTo(size.width * 0.38f, size.height * 0.48f)
                    lineTo(size.width * 0.58f, size.height * 0.62f)
                    lineTo(size.width * 0.88f, size.height * 0.22f)
                }
                drawPath(
                    path = path,
                    color = tint,
                    style = Stroke(
                        width = stroke,
                        cap = StrokeCap.Round
                    )
                )
        } else {
                val ys = listOf(0.22f, 0.50f, 0.78f)
                val lengths = listOf(0.72f, 0.50f, 0.82f)

                ys.forEachIndexed { index, y ->
                    drawCircle(
                        color = tint,
                        radius = stroke * 0.80f,
                        center = Offset(size.width * 0.12f, size.height * y)
                    )
                    drawLine(
                        color = tint,
                        start = Offset(size.width * 0.28f, size.height * y),
                        end = Offset(size.width * lengths[index], size.height * y),
                        strokeWidth = stroke,
                        cap = StrokeCap.Round
                    )
                }
        }
    }
}

@Composable
internal fun LiveRollingChart(
    history: List<LiveSamplePoint>,
    series: List<LiveChartSeries>,
    unit: String,
    valueScale: (Double) -> Double = { it },
    outOfRange: (SensorSample) -> Boolean = { false },
    primaryText: Color,
    secondaryText: Color
) {
    // Recalculate on data/series changes, not on every frame of the scale animation.
    val window by remember(history, series, outOfRange) {
        derivedStateOf {
            val latestTimestamp = history.lastOrNull()?.timestamp ?: System.currentTimeMillis()
            val start = latestTimestamp - LIVE_CHART_WINDOW_MILLIS
            val points = history.filter { it.timestamp >= start }
            val maximum = uvirChartMaximum(points.asSequence().flatMap { point ->
                series.asSequence().mapNotNull { item ->
                    if (outOfRange(point.sample)) null else item.value(point.sample)
                }
            }.asIterable())
            Triple(start, points, maximum)
        }
    }
    val windowStart = window.first
    val visiblePoints = window.second
    val canonicalMaximum = window.third
    // Animate in canonical units so changing the unit does not animate a different geometry.
    val floatMaximum = canonicalMaximum.toFloat().takeIf { it.isFinite() && it > 0f }
    val animatedMaximum by animateFloatAsState(
        targetValue = floatMaximum ?: 1f,
        animationSpec = tween(300),
        label = "liveChartScale"
    )
    // A new peak must remain visible even while the axis is expanding.
    val animatedCanonicalMaximum = if (floatMaximum != null) animatedMaximum.toDouble() else canonicalMaximum
    val maxValue = valueScale(maxOf(animatedCanonicalMaximum, canonicalMaximum / UVIR_CHART_HEADROOM))

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = unit,
                color = secondaryText,
                fontSize = 11.sp
            )

            Text(
                text = stringResource(R.string.live_chart_last_minute),
                color = secondaryText,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        }

        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(166.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(secondaryText.copy(alpha = 0.045f))
                    .padding(10.dp)
        ) {
            UvirChartYAxis(
                maximum = maxValue,
                fractionDigits = 1,
                secondaryText = secondaryText,
                modifier = Modifier.fillMaxWidth().height(146.dp)
            ) { chartModifier ->
                Canvas(modifier = chartModifier) {
                    repeat(5) { index ->
                        val y = size.height * index / 4f
                        drawLine(
                            color = secondaryText.copy(alpha = 0.13f),
                            start = Offset(0f, y),
                            end = Offset(size.width, y),
                            strokeWidth = 1.dp.toPx()
                        )
                    }

                    repeat(5) { index ->
                        val x = size.width * index / 4f
                        drawLine(
                            color = secondaryText.copy(alpha = 0.08f),
                            start = Offset(x, 0f),
                            end = Offset(x, size.height),
                            strokeWidth = 1.dp.toPx()
                        )
                    }

                    series.forEach { item ->
                        val path = Path()
                        var hasPoint = false

                        visiblePoints.forEach { point ->
                            if (outOfRange(point.sample) || !item.value(point.sample).isFinite()) {
                                hasPoint = false
                                return@forEach
                            }
                            val elapsed =
                                (point.timestamp - windowStart)
                                    .coerceIn(0L, LIVE_CHART_WINDOW_MILLIS)
                            val x =
                                size.width *
                                        elapsed.toFloat() /
                                        LIVE_CHART_WINDOW_MILLIS.toFloat()
                            val normalized =
                                (valueScale(item.value(point.sample))
                                    .coerceAtLeast(0.0) / maxValue)
                                    .toFloat()
                                    .coerceIn(0f, 1f)
                            val y = size.height * (1f - normalized)

                            if (hasPoint) {
                                path.lineTo(x, y)
                            } else {
                                path.moveTo(x, y)
                                hasPoint = true
                            }
                        }

                        if (hasPoint) {
                            drawPath(
                                path = path,
                                color = item.color,
                                style = Stroke(
                                    width = 2.dp.toPx(),
                                    cap = StrokeCap.Round
                                )
                            )
                        }
                    }
                }
            }

            if (visiblePoints.isEmpty()) {
                Text(
                    text = stringResource(R.string.live_chart_waiting),
                    modifier = Modifier.align(Alignment.Center),
                    color = secondaryText,
                    fontSize = 12.sp
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = stringResource(R.string.live_chart_ago),
                color = secondaryText,
                fontSize = 10.sp
            )
            Text(
                text = stringResource(R.string.live_chart_now),
                color = secondaryText,
                fontSize = 10.sp
            )
        }

        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            series.forEach { item ->
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier =
                            Modifier
                                .size(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(item.color)
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = item.label,
                        color = primaryText,
                        fontSize = 11.sp,
                        maxLines = 1
                    )
                }
            }
        }
    }
}
