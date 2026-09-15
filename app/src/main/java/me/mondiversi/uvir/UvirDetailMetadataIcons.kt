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
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

internal val UvirDetailMetadataIconSize = 11.dp
internal val UvirDetailMetadataIconStrokeWidth = 1.25.dp

internal enum class UvirDetailMetadataIconKind {
    DATE, START, END, DURATION, ACQUISITION, ALERT, SENSOR, NOTE
}

/** One size and stroke for every icon in the two detail metadata cards. */
@Composable
internal fun UvirDetailMetadataIcon(
    kind: UvirDetailMetadataIconKind,
    tint: Color,
    modifier: Modifier = Modifier
) {
    val iconModifier = modifier.size(UvirDetailMetadataIconSize)
    when (kind) {
        UvirDetailMetadataIconKind.ACQUISITION -> {
            CaptureMeasurementIcon(
                modifier = iconModifier,
                tint = tint,
                useCompactStroke = true
            )
            return
        }
        UvirDetailMetadataIconKind.SENSOR -> {
            ConnectivitySectionIcon(
                type = ConnectivityIconType.SENSOR,
                modifier = iconModifier,
                tint = tint,
                strokeScale = UvirDetailMetadataIconStrokeWidth / 1.6.dp
            )
            return
        }
        else -> Unit
    }
    Canvas(modifier = iconModifier) {
        val strokeWidth = UvirDetailMetadataIconStrokeWidth.toPx()
        val stroke = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
        fun point(x: Float, y: Float) = Offset(size.width * x, size.height * y)
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) {
            drawLine(tint, point(x1, y1), point(x2, y2), strokeWidth, StrokeCap.Round)
        }
        when (kind) {
            UvirDetailMetadataIconKind.DATE -> {
                drawRoundRect(
                    color = tint,
                    topLeft = point(0.14f, 0.21f),
                    size = Size(size.width * 0.72f, size.height * 0.65f),
                    cornerRadius = CornerRadius(size.minDimension * 0.08f),
                    style = stroke
                )
                line(0.14f, 0.40f, 0.86f, 0.40f)
                line(0.34f, 0.10f, 0.34f, 0.29f)
                line(0.66f, 0.10f, 0.66f, 0.29f)
            }
            UvirDetailMetadataIconKind.START -> {
                val path = Path().apply {
                    moveTo(size.width * 0.22f, size.height * 0.12f)
                    lineTo(size.width * 0.86f, size.height * 0.50f)
                    lineTo(size.width * 0.22f, size.height * 0.88f)
                    close()
                }
                drawPath(path, tint, style = stroke)
            }
            UvirDetailMetadataIconKind.END -> {
                drawRoundRect(
                    color = tint,
                    topLeft = point(0.18f, 0.18f),
                    size = Size(size.width * 0.64f, size.height * 0.64f),
                    cornerRadius = CornerRadius(size.minDimension * 0.10f),
                    style = stroke
                )
            }
            UvirDetailMetadataIconKind.DURATION -> {
                val center = point(0.50f, 0.54f)
                drawCircle(tint, size.minDimension * 0.36f, center, style = stroke)
                line(0.50f, 0.54f, 0.50f, 0.31f)
                line(0.50f, 0.54f, 0.68f, 0.62f)
            }
            UvirDetailMetadataIconKind.ALERT -> {
                drawArc(
                    color = tint,
                    startAngle = 190f,
                    sweepAngle = 160f,
                    useCenter = false,
                    topLeft = point(0.24f, 0.16f),
                    size = Size(size.width * 0.52f, size.height * 0.62f),
                    style = stroke
                )
                line(0.24f, 0.57f, 0.14f, 0.78f)
                line(0.76f, 0.57f, 0.86f, 0.78f)
                line(0.14f, 0.78f, 0.86f, 0.78f)
                drawCircle(tint, strokeWidth * 0.72f, point(0.50f, 0.90f))
            }
            UvirDetailMetadataIconKind.NOTE -> {
                drawRoundRect(
                    color = tint,
                    topLeft = point(0.20f, 0.12f),
                    size = Size(size.width * 0.60f, size.height * 0.76f),
                    cornerRadius = CornerRadius(size.minDimension * 0.06f),
                    style = stroke
                )
                line(0.34f, 0.35f, 0.66f, 0.35f)
                line(0.34f, 0.51f, 0.66f, 0.51f)
                line(0.34f, 0.67f, 0.54f, 0.67f)
            }
            else -> Unit
        }
    }
}
