package me.mondiversi.uvir

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

internal val UvirDetailSpectrumGroupIconSize = 20.dp

internal fun SessionChartGroup.spectrumIconGroup(): SensorGroup =
    when (this) {
        SessionChartGroup.UV -> SensorGroup.UV
        SessionChartGroup.VISIBLE -> SensorGroup.VISIBLE
        SessionChartGroup.FAR_RED_NIR -> SensorGroup.NIR
        SessionChartGroup.BIOLOGICAL -> SensorGroup.BIOLOGICAL
    }

internal fun AcquisitionChartSection.spectrumIconGroup(): SensorGroup =
    when (this) {
        AcquisitionChartSection.UV -> SensorGroup.UV
        AcquisitionChartSection.VISIBLE -> SensorGroup.VISIBLE
        AcquisitionChartSection.FAR_RED_NIR -> SensorGroup.NIR
        AcquisitionChartSection.BIOLOGICAL -> SensorGroup.BIOLOGICAL
    }

/** Decorative title symbols, intentionally distinct from the wave/DNA selectors. */
@Composable
internal fun UvirSpectrumGroupIcon(
    group: SensorGroup,
    tint: Color,
    modifier: Modifier = Modifier,
    iconSize: Dp = UvirMeasurementSelectorIconSize
) {
    Canvas(modifier.size(iconSize)) {
        val width = UvirMeasurementSelectorIconStrokeWidth.toPx() *
            (iconSize / UvirMeasurementSelectorIconSize)
        val stroke = Stroke(width = width, cap = StrokeCap.Round)
        fun point(x: Float, y: Float) = Offset(size.width * x, size.height * y)
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) {
            drawLine(tint, point(x1, y1), point(x2, y2), width, StrokeCap.Round)
        }
        when (group) {
            SensorGroup.UV -> {
                drawCircle(tint, size.minDimension * 0.21f, center, style = stroke)
                repeat(8) { index ->
                    val angle = index * Math.PI / 4.0
                    drawLine(
                        tint,
                        center + Offset(cos(angle).toFloat(), sin(angle).toFloat()) * (size.minDimension * 0.33f),
                        center + Offset(cos(angle).toFloat(), sin(angle).toFloat()) * (size.minDimension * 0.43f),
                        width,
                        StrokeCap.Round
                    )
                }
            }
            SensorGroup.VISIBLE -> {
                val eye = Path().apply {
                    moveTo(size.width * 0.07f, size.height * 0.50f)
                    cubicTo(size.width * 0.25f, size.height * 0.07f, size.width * 0.75f, size.height * 0.07f, size.width * 0.93f, size.height * 0.50f)
                    cubicTo(size.width * 0.75f, size.height * 0.93f, size.width * 0.25f, size.height * 0.93f, size.width * 0.07f, size.height * 0.50f)
                    close()
                }
                drawPath(eye, tint, style = stroke)
                drawCircle(tint, size.minDimension * 0.14f, center, style = stroke)
            }
            SensorGroup.NIR -> {
                drawRoundRect(
                    tint,
                    topLeft = point(0.38f, 0.08f),
                    size = Size(size.width * 0.24f, size.height * 0.62f),
                    cornerRadius = CornerRadius(size.width * 0.12f),
                    style = stroke
                )
                drawCircle(tint, size.minDimension * 0.19f, point(0.50f, 0.74f), style = stroke)
                line(0.50f, 0.30f, 0.50f, 0.74f)
                line(0.76f, 0.24f, 0.87f, 0.24f)
                line(0.76f, 0.42f, 0.87f, 0.42f)
            }
            SensorGroup.BIOLOGICAL -> {
                drawOval(
                    tint,
                    topLeft = point(0.10f, 0.14f),
                    size = Size(size.width * 0.80f, size.height * 0.72f),
                    style = stroke
                )
                drawCircle(tint, size.minDimension * 0.14f, point(0.53f, 0.48f), style = stroke)
                drawCircle(tint, size.minDimension * 0.045f, point(0.28f, 0.50f))
                drawCircle(tint, size.minDimension * 0.045f, point(0.65f, 0.70f))
            }
        }
    }
}
