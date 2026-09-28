package me.mondiversi.uvir

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

enum class ConnectivityIconType {
    SENSOR_CONNECTION,
    DISCONNECTED,
    SENSOR,
    GENERAL,
    ACTIONS,
    PHONE,
    TOOLS,
    MEASUREMENT,
    CALIBRATION,
    SAMPLING,
    SIMULATION,
    MUSIC,
    ALERT,
    SOUND,
    LED,
    IRRADIANCE,
    NUMERIC_FORMAT,
    DATE,
    TIME,
    LANGUAGE,
    DEBUG,
    EXPORT,
    SECURITY,
    USB,
    BLUETOOTH,
    WIFI,
    INTERNET,
    DATA,
    DATABASE,
    COUNTERS,
    TALLY
}

@Composable
fun ConnectivitySectionIcon(
    type: ConnectivityIconType,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
    strokeScale: Float = 1f
) {
    val effectiveStrokeScale = maxOf(strokeScale, LocalUvirActionGlyphStrokeScale.current)
    Canvas(
        modifier =
            modifier
                .size(20.dp)
                .graphicsLayer(alpha = tint.alpha)
    ) {
        val tint = tint.copy(alpha = 1f)
        val strokeWidth =
            maxOf(
                1.6.dp.toPx(),
                size.minDimension * 0.08f
            ) * effectiveStrokeScale

        when (type) {
            ConnectivityIconType.ACTIONS -> {
                // Separate command tiles, with no overlapping strokes.
                for (x in listOf(0.15f, 0.57f)) for (y in listOf(0.15f, 0.57f)) {
                    drawRoundRect(
                        color = tint,
                        topLeft = Offset(size.width * x, size.height * y),
                        size = Size(size.width * 0.28f, size.height * 0.28f),
                        cornerRadius = CornerRadius(size.minDimension * 0.04f),
                        style = Stroke(width = strokeWidth)
                    )
                }
            }
            ConnectivityIconType.LED -> {
                val dome = Path().apply {
                    moveTo(size.width * 0.28f, size.height * 0.62f)
                    lineTo(size.width * 0.28f, size.height * 0.43f)
                    cubicTo(size.width * 0.28f, size.height * 0.14f,
                        size.width * 0.72f, size.height * 0.14f,
                        size.width * 0.72f, size.height * 0.43f)
                    lineTo(size.width * 0.72f, size.height * 0.62f)
                    close()
                }
                drawPath(dome, tint, style = Stroke(width = strokeWidth,
                    join = androidx.compose.ui.graphics.StrokeJoin.Round))
                for (x in listOf(0.39f, 0.61f)) {
                    drawLine(tint, Offset(size.width * x, size.height * 0.69f),
                        Offset(size.width * x, size.height * 0.88f),
                        strokeWidth, cap = StrokeCap.Round)
                }
                drawLine(tint, Offset(size.width * 0.12f, size.height * 0.31f),
                    Offset(size.width * 0.18f, size.height * 0.36f), strokeWidth, cap = StrokeCap.Round)
                drawLine(tint, Offset(size.width * 0.82f, size.height * 0.36f),
                    Offset(size.width * 0.88f, size.height * 0.31f), strokeWidth, cap = StrokeCap.Round)
            }
            ConnectivityIconType.SIMULATION -> {
                // Laboratory flask: distinct from the sampling waveform.
                val flask = Path().apply {
                    moveTo(size.width * 0.38f, size.height * 0.12f)
                    lineTo(size.width * 0.38f, size.height * 0.40f)
                    lineTo(size.width * 0.17f, size.height * 0.78f)
                    quadraticTo(size.width * 0.12f, size.height * 0.88f,
                        size.width * 0.25f, size.height * 0.88f)
                    lineTo(size.width * 0.75f, size.height * 0.88f)
                    quadraticTo(size.width * 0.88f, size.height * 0.88f,
                        size.width * 0.83f, size.height * 0.78f)
                    lineTo(size.width * 0.62f, size.height * 0.40f)
                    lineTo(size.width * 0.62f, size.height * 0.12f)
                }
                drawPath(flask, tint, style = Stroke(strokeWidth, cap = StrokeCap.Round))
                drawLine(tint, Offset(size.width * 0.32f, size.height * 0.12f),
                    Offset(size.width * 0.68f, size.height * 0.12f), strokeWidth, StrokeCap.Round)
                drawLine(tint, Offset(size.width * 0.29f, size.height * 0.64f),
                    Offset(size.width * 0.71f, size.height * 0.64f), strokeWidth, StrokeCap.Round)
            }
            ConnectivityIconType.MUSIC -> {
                val notes = Path().apply {
                    moveTo(size.width * 0.35f, size.height * 0.75f)
                    lineTo(size.width * 0.35f, size.height * 0.24f)
                    lineTo(size.width * 0.79f, size.height * 0.12f)
                    lineTo(size.width * 0.79f, size.height * 0.63f)
                }
                drawPath(notes, tint, style = Stroke(strokeWidth, cap = StrokeCap.Round))
                drawOval(tint, Offset(size.width * 0.10f, size.height * 0.68f),
                    Size(size.width * 0.29f, size.height * 0.20f))
                drawOval(tint, Offset(size.width * 0.54f, size.height * 0.56f),
                    Size(size.width * 0.29f, size.height * 0.20f))
            }
            ConnectivityIconType.SECURITY -> {
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(size.width * 0.22f, size.height * 0.43f),
                    size = Size(size.width * 0.56f, size.height * 0.45f),
                    cornerRadius = CornerRadius(
                        size.minDimension * 0.08f,
                        size.minDimension * 0.08f
                    ),
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
                drawArc(
                    color = tint,
                    startAngle = 180f,
                    sweepAngle = 180f,
                    useCenter = false,
                    topLeft = Offset(size.width * 0.32f, size.height * 0.12f),
                    size = Size(size.width * 0.36f, size.height * 0.52f),
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
                drawCircle(
                    color = tint,
                    radius = strokeWidth * 0.92f,
                    center = Offset(size.width * 0.50f, size.height * 0.64f)
                )
            }

            ConnectivityIconType.SENSOR_CONNECTION -> {
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(
                        size.width * 0.08f,
                        size.height * 0.32f
                    ),
                    size = Size(
                        size.width * 0.34f,
                        size.height * 0.36f
                    ),
                    cornerRadius = CornerRadius(
                        size.minDimension * 0.10f,
                        size.minDimension * 0.10f
                    ),
                    style = Stroke(width = strokeWidth)
                )
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(
                        size.width * 0.58f,
                        size.height * 0.32f
                    ),
                    size = Size(
                        size.width * 0.34f,
                        size.height * 0.36f
                    ),
                    cornerRadius = CornerRadius(
                        size.minDimension * 0.10f,
                        size.minDimension * 0.10f
                    ),
                    style = Stroke(width = strokeWidth)
                )
                drawLine(
                    color = tint,
                    start = Offset(
                        size.width * 0.42f,
                        size.height * 0.50f
                    ),
                    end = Offset(
                        size.width * 0.58f,
                        size.height * 0.50f
                    ),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )
                drawCircle(
                    color = tint,
                    radius = strokeWidth * 0.85f,
                    center = Offset(
                        size.width * 0.25f,
                        size.height * 0.50f
                    )
                )
                drawCircle(
                    color = tint,
                    radius = strokeWidth * 0.85f,
                    center = Offset(
                        size.width * 0.75f,
                        size.height * 0.50f
                    )
                )
            }

            ConnectivityIconType.DISCONNECTED -> {
                listOf(0.09f, 0.59f).forEach { x ->
                    drawRoundRect(
                        color = tint,
                        topLeft = Offset(size.width * x, size.height * 0.34f),
                        size = Size(size.width * 0.32f, size.height * 0.32f),
                        cornerRadius = CornerRadius(size.minDimension * 0.09f),
                        style = Stroke(width = strokeWidth)
                    )
                }
                drawLine(
                    color = tint,
                    start = Offset(size.width * 0.43f, size.height * 0.73f),
                    end = Offset(size.width * 0.57f, size.height * 0.27f),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )
            }

            ConnectivityIconType.SENSOR -> {
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(
                        size.width * 0.27f,
                        size.height * 0.27f
                    ),
                    size = Size(
                        size.width * 0.46f,
                        size.height * 0.46f
                    ),
                    cornerRadius = CornerRadius(
                        strokeWidth,
                        strokeWidth
                    ),
                    style = Stroke(width = strokeWidth)
                )

                listOf(0.34f, 0.50f, 0.66f)
                    .forEach { position ->
                        drawLine(
                            color = tint,
                            start = Offset(
                                size.width * position,
                                size.height * 0.13f
                            ),
                            end = Offset(
                                size.width * position,
                                size.height * 0.27f
                            ),
                            strokeWidth = strokeWidth,
                            cap = StrokeCap.Round
                        )
                        drawLine(
                            color = tint,
                            start = Offset(
                                size.width * position,
                                size.height * 0.73f
                            ),
                            end = Offset(
                                size.width * position,
                                size.height * 0.87f
                            ),
                            strokeWidth = strokeWidth,
                            cap = StrokeCap.Round
                        )
                        drawLine(
                            color = tint,
                            start = Offset(
                                size.width * 0.13f,
                                size.height * position
                            ),
                            end = Offset(
                                size.width * 0.27f,
                                size.height * position
                            ),
                            strokeWidth = strokeWidth,
                            cap = StrokeCap.Round
                        )
                        drawLine(
                            color = tint,
                            start = Offset(
                                size.width * 0.73f,
                                size.height * position
                            ),
                            end = Offset(
                                size.width * 0.87f,
                                size.height * position
                            ),
                            strokeWidth = strokeWidth,
                            cap = StrokeCap.Round
                        )
                    }

                drawCircle(
                    color = tint,
                    radius = strokeWidth * 1.35f,
                    center = Offset(
                        size.width * 0.50f,
                        size.height * 0.50f
                    )
                )
            }

            ConnectivityIconType.GENERAL -> {
                listOf(0.24f to 0.38f, 0.50f to 0.66f, 0.76f to 0.44f)
                    .forEach { (vertical, horizontal) ->
                        val centerX = size.width * horizontal
                        val centerY = size.height * vertical
                        val knobRadius = size.minDimension * 0.085f
                        val trackGap = knobRadius + strokeWidth * 0.65f
                        drawLine(
                            color = tint,
                            start = Offset(size.width * 0.12f, centerY),
                            end = Offset(centerX - trackGap, centerY),
                            strokeWidth = strokeWidth,
                            cap = StrokeCap.Round
                        )
                        drawLine(
                            color = tint,
                            start = Offset(centerX + trackGap, centerY),
                            end = Offset(size.width * 0.88f, centerY),
                            strokeWidth = strokeWidth,
                            cap = StrokeCap.Round
                        )
                        drawCircle(
                            color = tint,
                            radius = knobRadius,
                            center = Offset(centerX, centerY),
                            style = Stroke(width = strokeWidth)
                        )
                    }
            }

            ConnectivityIconType.PHONE -> {
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(size.width * 0.27f, size.height * 0.08f),
                    size = Size(size.width * 0.46f, size.height * 0.84f),
                    cornerRadius = CornerRadius(size.minDimension * 0.10f),
                    style = Stroke(width = strokeWidth)
                )
                drawLine(
                    color = tint,
                    start = Offset(size.width * 0.43f, size.height * 0.18f),
                    end = Offset(size.width * 0.57f, size.height * 0.18f),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )
                drawCircle(
                    color = tint,
                    radius = strokeWidth * 0.65f,
                    center = Offset(size.width * 0.50f, size.height * 0.82f)
                )
            }

            ConnectivityIconType.TOOLS -> {
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(size.width * 0.12f, size.height * 0.37f),
                    size = Size(size.width * 0.76f, size.height * 0.49f),
                    cornerRadius = CornerRadius(size.minDimension * 0.07f),
                    style = Stroke(width = strokeWidth)
                )
                val handle = Path().apply {
                    moveTo(size.width * 0.36f, size.height * 0.37f)
                    lineTo(size.width * 0.36f, size.height * 0.20f)
                    lineTo(size.width * 0.64f, size.height * 0.20f)
                    lineTo(size.width * 0.64f, size.height * 0.37f)
                }
                drawPath(handle, tint, style = Stroke(width = strokeWidth, cap = StrokeCap.Round))
                drawLine(
                    color = tint,
                    start = Offset(size.width * 0.12f, size.height * 0.60f),
                    end = Offset(size.width * 0.40f, size.height * 0.60f),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = tint,
                    start = Offset(size.width * 0.60f, size.height * 0.60f),
                    end = Offset(size.width * 0.88f, size.height * 0.60f),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(size.width * 0.43f, size.height * 0.54f),
                    size = Size(size.width * 0.14f, size.height * 0.12f),
                    cornerRadius = CornerRadius(size.minDimension * 0.02f),
                    style = Stroke(width = strokeWidth)
                )
            }

            ConnectivityIconType.MEASUREMENT -> {
                drawArc(
                    color = tint,
                    startAngle = 155f,
                    sweepAngle = 230f,
                    useCenter = false,
                    topLeft = Offset(size.width * 0.13f, size.height * 0.16f),
                    size = Size(size.width * 0.74f, size.height * 0.74f),
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
                drawLine(
                    color = tint,
                    start = Offset(size.width * 0.51f, size.height * 0.60f),
                    end = Offset(size.width * 0.70f, size.height * 0.38f),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )
                drawCircle(
                    color = tint,
                    radius = strokeWidth * 0.75f,
                    center = Offset(size.width * 0.50f, size.height * 0.61f)
                )
            }

            ConnectivityIconType.CALIBRATION -> {
                drawCircle(
                    color = tint,
                    radius = size.minDimension * 0.34f,
                    center = Offset(size.width * 0.50f, size.height * 0.50f),
                    style = Stroke(width = strokeWidth)
                )
                drawCircle(
                    color = tint,
                    radius = size.minDimension * 0.12f,
                    center = Offset(size.width * 0.50f, size.height * 0.50f),
                    style = Stroke(width = strokeWidth)
                )
                drawLine(
                    color = tint,
                    start = Offset(size.width * 0.50f, size.height * 0.06f),
                    end = Offset(size.width * 0.50f, size.height * 0.25f),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = tint,
                    start = Offset(size.width * 0.50f, size.height * 0.75f),
                    end = Offset(size.width * 0.50f, size.height * 0.94f),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = tint,
                    start = Offset(size.width * 0.06f, size.height * 0.50f),
                    end = Offset(size.width * 0.25f, size.height * 0.50f),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = tint,
                    start = Offset(size.width * 0.75f, size.height * 0.50f),
                    end = Offset(size.width * 0.94f, size.height * 0.50f),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )
            }

            ConnectivityIconType.SAMPLING -> {
                val samples =
                    listOf(
                        Offset(size.width * 0.12f, size.height * 0.68f),
                        Offset(size.width * 0.31f, size.height * 0.36f),
                        Offset(size.width * 0.50f, size.height * 0.62f),
                        Offset(size.width * 0.69f, size.height * 0.25f),
                        Offset(size.width * 0.88f, size.height * 0.48f)
                    )

                samples.zipWithNext().forEach { (start, end) ->
                    drawLine(
                        color = tint,
                        start = start,
                        end = end,
                        strokeWidth = strokeWidth,
                        cap = StrokeCap.Round
                    )
                }

                samples.forEach { sample ->
                    drawCircle(
                        color = tint,
                        radius = strokeWidth * 1.25f,
                        center = sample
                    )
                }
            }

            ConnectivityIconType.ALERT -> {
                drawArc(
                    color = tint,
                    startAngle = 190f,
                    sweepAngle = 160f,
                    useCenter = false,
                    topLeft = Offset(
                        size.width * 0.24f,
                        size.height * 0.18f
                    ),
                    size = Size(
                        size.width * 0.52f,
                        size.height * 0.62f
                    ),
                    style = Stroke(
                        width = strokeWidth,
                        cap = StrokeCap.Round
                    )
                )
                drawLine(
                    color = tint,
                    start = Offset(
                        size.width * 0.24f,
                        size.height * 0.58f
                    ),
                    end = Offset(
                        size.width * 0.16f,
                        size.height * 0.76f
                    ),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = tint,
                    start = Offset(
                        size.width * 0.76f,
                        size.height * 0.58f
                    ),
                    end = Offset(
                        size.width * 0.84f,
                        size.height * 0.76f
                    ),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = tint,
                    start = Offset(
                        size.width * 0.16f,
                        size.height * 0.76f
                    ),
                    end = Offset(
                        size.width * 0.84f,
                        size.height * 0.76f
                    ),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )
                drawCircle(
                    color = tint,
                    radius = strokeWidth,
                    center = Offset(
                        size.width * 0.50f,
                        size.height * 0.88f
                    )
                )
                drawCircle(
                    color = tint,
                    radius = strokeWidth * 0.85f,
                    center = Offset(
                        size.width * 0.50f,
                        size.height * 0.16f
                    )
                )
            }

            ConnectivityIconType.SOUND -> {
                val speaker = Path().apply {
                    moveTo(size.width * 0.12f, size.height * 0.40f)
                    lineTo(size.width * 0.31f, size.height * 0.40f)
                    lineTo(size.width * 0.53f, size.height * 0.20f)
                    lineTo(size.width * 0.53f, size.height * 0.80f)
                    lineTo(size.width * 0.31f, size.height * 0.60f)
                    lineTo(size.width * 0.12f, size.height * 0.60f)
                    close()
                }
                drawPath(
                    path = speaker,
                    color = tint,
                    style = Stroke(
                        width = strokeWidth,
                        join = androidx.compose.ui.graphics.StrokeJoin.Round
                    )
                )
                drawArc(
                    color = tint,
                    startAngle = -50f,
                    sweepAngle = 100f,
                    useCenter = false,
                    topLeft = Offset(size.width * 0.47f, size.height * 0.29f),
                    size = Size(size.width * 0.27f, size.height * 0.42f),
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
                drawArc(
                    color = tint,
                    startAngle = -50f,
                    sweepAngle = 100f,
                    useCenter = false,
                    topLeft = Offset(size.width * 0.42f, size.height * 0.16f),
                    size = Size(size.width * 0.48f, size.height * 0.68f),
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            }

            ConnectivityIconType.IRRADIANCE -> {
                val wave = Path().apply {
                    moveTo(size.width * 0.08f, size.height * 0.50f)
                    cubicTo(
                        size.width * 0.20f, size.height * 0.12f,
                        size.width * 0.32f, size.height * 0.12f,
                        size.width * 0.44f, size.height * 0.50f
                    )
                    cubicTo(
                        size.width * 0.56f, size.height * 0.88f,
                        size.width * 0.68f, size.height * 0.88f,
                        size.width * 0.80f, size.height * 0.50f
                    )
                    lineTo(size.width * 0.92f, size.height * 0.50f)
                }
                drawPath(wave, tint, style = Stroke(width = strokeWidth, cap = StrokeCap.Round))
            }

            ConnectivityIconType.DATE -> {
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(size.width * 0.12f, size.height * 0.20f),
                    size = Size(size.width * 0.76f, size.height * 0.68f),
                    cornerRadius = CornerRadius(size.minDimension * 0.08f),
                    style = Stroke(width = strokeWidth)
                )
                drawLine(
                    tint,
                    Offset(size.width * 0.12f, size.height * 0.40f),
                    Offset(size.width * 0.88f, size.height * 0.40f),
                    strokeWidth,
                    StrokeCap.Round
                )
                listOf(0.34f, 0.66f).forEach { x ->
                    drawLine(
                        tint,
                        Offset(size.width * x, size.height * 0.10f),
                        Offset(size.width * x, size.height * 0.29f),
                        strokeWidth,
                        StrokeCap.Round
                    )
                }
            }

            ConnectivityIconType.TIME -> {
                drawCircle(
                    color = tint,
                    radius = size.minDimension * 0.37f,
                    center = center,
                    style = Stroke(width = strokeWidth)
                )
                drawLine(
                    tint,
                    center,
                    Offset(size.width * 0.50f, size.height * 0.29f),
                    strokeWidth,
                    StrokeCap.Round
                )
                drawLine(
                    tint,
                    center,
                    Offset(size.width * 0.68f, size.height * 0.60f),
                    strokeWidth,
                    StrokeCap.Round
                )
            }

            ConnectivityIconType.NUMERIC_FORMAT -> {
                drawLine(
                    color = tint,
                    start = Offset(
                        size.width * 0.20f,
                        size.height * 0.28f
                    ),
                    end = Offset(
                        size.width * 0.20f,
                        size.height * 0.74f
                    ),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = tint,
                    start = Offset(
                        size.width * 0.12f,
                        size.height * 0.36f
                    ),
                    end = Offset(
                        size.width * 0.20f,
                        size.height * 0.28f
                    ),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )
                drawCircle(
                    color = tint,
                    radius = strokeWidth * 0.85f,
                    center = Offset(
                        size.width * 0.43f,
                        size.height * 0.69f
                    )
                )
                drawOval(
                    color = tint,
                    topLeft = Offset(
                        size.width * 0.58f,
                        size.height * 0.27f
                    ),
                    size = Size(
                        size.width * 0.28f,
                        size.height * 0.48f
                    ),
                    style = Stroke(width = strokeWidth)
                )
            }

            ConnectivityIconType.LANGUAGE -> {
                drawCircle(
                    color = tint,
                    radius = size.minDimension * 0.37f,
                    center = Offset(
                        size.width * 0.50f,
                        size.height * 0.50f
                    ),
                    style = Stroke(width = strokeWidth)
                )
                drawOval(
                    color = tint,
                    topLeft = Offset(
                        size.width * 0.34f,
                        size.height * 0.13f
                    ),
                    size = Size(
                        size.width * 0.32f,
                        size.height * 0.74f
                    ),
                    style = Stroke(width = strokeWidth)
                )
                listOf(0.36f, 0.64f).forEach { y ->
                    drawLine(
                        color = tint,
                        start = Offset(
                            size.width * 0.16f,
                            size.height * y
                        ),
                        end = Offset(
                            size.width * 0.84f,
                            size.height * y
                        ),
                        strokeWidth = strokeWidth,
                        cap = StrokeCap.Round
                    )
                }
            }

            ConnectivityIconType.DEBUG -> {
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(
                        size.width * 0.30f,
                        size.height * 0.24f
                    ),
                    size = Size(
                        size.width * 0.40f,
                        size.height * 0.58f
                    ),
                    cornerRadius = CornerRadius(
                        strokeWidth * 1.5f,
                        strokeWidth * 1.5f
                    ),
                    style = Stroke(width = strokeWidth)
                )

                drawLine(
                    color = tint,
                    start = Offset(
                        size.width * 0.34f,
                        size.height * 0.42f
                    ),
                    end = Offset(
                        size.width * 0.66f,
                        size.height * 0.42f
                    ),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )

                listOf(0.36f, 0.54f, 0.72f).forEach { y ->
                    drawLine(
                        color = tint,
                        start = Offset(
                            size.width * 0.30f,
                            size.height * y
                        ),
                        end = Offset(
                            size.width * 0.13f,
                            size.height * (y - 0.07f)
                        ),
                        strokeWidth = strokeWidth,
                        cap = StrokeCap.Round
                    )
                    drawLine(
                        color = tint,
                        start = Offset(
                            size.width * 0.70f,
                            size.height * y
                        ),
                        end = Offset(
                            size.width * 0.87f,
                            size.height * (y - 0.07f)
                        ),
                        strokeWidth = strokeWidth,
                        cap = StrokeCap.Round
                    )
                }

                drawLine(
                    color = tint,
                    start = Offset(
                        size.width * 0.42f,
                        size.height * 0.24f
                    ),
                    end = Offset(
                        size.width * 0.34f,
                        size.height * 0.10f
                    ),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = tint,
                    start = Offset(
                        size.width * 0.58f,
                        size.height * 0.24f
                    ),
                    end = Offset(
                        size.width * 0.66f,
                        size.height * 0.10f
                    ),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )
            }

            ConnectivityIconType.EXPORT -> {
                drawOval(
                    color = tint,
                    topLeft = Offset(
                        size.width * 0.16f,
                        size.height * 0.56f
                    ),
                    size = Size(
                        size.width * 0.68f,
                        size.height * 0.22f
                    ),
                    style = Stroke(width = strokeWidth)
                )
                drawLine(
                    color = tint,
                    start = Offset(
                        size.width * 0.16f,
                        size.height * 0.67f
                    ),
                    end = Offset(
                        size.width * 0.16f,
                        size.height * 0.88f
                    ),
                    strokeWidth = strokeWidth
                )
                drawLine(
                    color = tint,
                    start = Offset(
                        size.width * 0.84f,
                        size.height * 0.67f
                    ),
                    end = Offset(
                        size.width * 0.84f,
                        size.height * 0.88f
                    ),
                    strokeWidth = strokeWidth
                )
                drawArc(
                    color = tint,
                    startAngle = 0f,
                    sweepAngle = 180f,
                    useCenter = false,
                    topLeft = Offset(
                        size.width * 0.16f,
                        size.height * 0.77f
                    ),
                    size = Size(
                        size.width * 0.68f,
                        size.height * 0.22f
                    ),
                    style = Stroke(width = strokeWidth)
                )
                drawLine(
                    color = tint,
                    start = Offset(
                        size.width * 0.50f,
                        size.height * 0.12f
                    ),
                    end = Offset(
                        size.width * 0.50f,
                        size.height * 0.58f
                    ),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = tint,
                    start = Offset(
                        size.width * 0.50f,
                        size.height * 0.12f
                    ),
                    end = Offset(
                        size.width * 0.34f,
                        size.height * 0.30f
                    ),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = tint,
                    start = Offset(
                        size.width * 0.50f,
                        size.height * 0.12f
                    ),
                    end = Offset(
                        size.width * 0.66f,
                        size.height * 0.30f
                    ),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )
            }

            ConnectivityIconType.USB -> {
                val nodeRadius = strokeWidth * 1.2f
                val nodeStroke = Stroke(width = strokeWidth * 0.8f)
                val leftNode = Offset(size.width * 0.25f, size.height * 0.33f)
                val bottomNode = Offset(size.width * 0.50f, size.height * 0.83f)
                drawLine(
                    color = tint,
                    start = Offset(size.width * 0.50f, size.height * 0.18f),
                    end = Offset(bottomNode.x, bottomNode.y - nodeRadius),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = tint,
                    start = Offset(size.width * 0.50f, size.height * 0.48f),
                    end = Offset(
                        leftNode.x + nodeRadius * 0.86f,
                        leftNode.y + nodeRadius * 0.52f
                    ),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = tint,
                    start = Offset(size.width * 0.50f, size.height * 0.61f),
                    end = Offset(size.width * 0.69f, size.height * 0.45f),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )
                drawCircle(
                    color = tint,
                    radius = nodeRadius,
                    center = leftNode,
                    style = nodeStroke
                )
                drawRect(
                    color = tint,
                    topLeft = Offset(size.width * 0.69f, size.height * 0.39f),
                    size = Size(size.width * 0.12f, size.height * 0.12f)
                )
                val arrow = Path().apply {
                    moveTo(size.width * 0.50f, size.height * 0.08f)
                    lineTo(size.width * 0.39f, size.height * 0.24f)
                    lineTo(size.width * 0.61f, size.height * 0.24f)
                    close()
                }
                drawPath(path = arrow, color = tint)
                drawCircle(
                    color = tint,
                    radius = nodeRadius,
                    center = bottomNode,
                    style = nodeStroke
                )
            }

            ConnectivityIconType.BLUETOOTH -> {
                val center = Offset(size.width * 0.47f, size.height * 0.50f)
                val top = Offset(size.width * 0.47f, size.height * 0.10f)
                val bottom = Offset(size.width * 0.47f, size.height * 0.90f)
                val upper = Offset(size.width * 0.73f, size.height * 0.30f)
                val lower = Offset(size.width * 0.73f, size.height * 0.70f)

                listOf(
                    top to bottom,
                    center to upper,
                    upper to top,
                    center to lower,
                    lower to bottom,
                    Offset(size.width * 0.23f, size.height * 0.30f) to lower,
                    Offset(size.width * 0.23f, size.height * 0.70f) to upper
                ).forEach { (start, end) ->
                    drawLine(
                        color = tint,
                        start = start,
                        end = end,
                        strokeWidth = strokeWidth,
                        cap = StrokeCap.Round
                    )
                }
            }

            ConnectivityIconType.WIFI -> {
                val signalCenter =
                    Offset(
                        size.width * 0.50f,
                        size.height * 0.75f
                    )
                val signalStroke =
                    Stroke(
                        width = strokeWidth,
                        cap = StrokeCap.Round
                    )
                listOf(0.59f, 0.39f, 0.20f).forEach { radiusFraction ->
                    val radius = size.minDimension * radiusFraction
                    drawArc(
                        color = tint,
                        startAngle = 225f,
                        sweepAngle = 90f,
                        useCenter = false,
                        topLeft =
                            Offset(
                                signalCenter.x - radius,
                                signalCenter.y - radius
                            ),
                        size = Size(radius * 2f, radius * 2f),
                        style = signalStroke
                    )
                }
                drawCircle(
                    color = tint,
                    radius = strokeWidth * 0.85f,
                    center =
                        Offset(
                            size.width * 0.50f,
                            size.height * 0.82f
                        )
                )
            }

            ConnectivityIconType.INTERNET -> {
                val globeStroke =
                    Stroke(
                        width = strokeWidth,
                        cap = StrokeCap.Round
                    )
                drawCircle(
                    color = tint,
                    radius = size.minDimension * 0.39f,
                    center = Offset(size.width * 0.50f, size.height * 0.50f),
                    style = globeStroke
                )
                drawOval(
                    color = tint,
                    topLeft = Offset(size.width * 0.34f, size.height * 0.11f),
                    size = Size(size.width * 0.32f, size.height * 0.78f),
                    style = globeStroke
                )
                drawLine(
                    color = tint,
                    start = Offset(size.width * 0.11f, size.height * 0.50f),
                    end = Offset(size.width * 0.89f, size.height * 0.50f),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )
            }

            ConnectivityIconType.DATA -> {
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(size.width * 0.16f, size.height * 0.12f),
                    size = Size(size.width * 0.68f, size.height * 0.76f),
                    cornerRadius = CornerRadius(size.minDimension * 0.10f),
                    style = Stroke(width = strokeWidth)
                )
                listOf(0.38f, 0.62f).forEach { y ->
                    drawLine(
                        color = tint,
                        start = Offset(size.width * 0.28f, size.height * y),
                        end = Offset(size.width * 0.72f, size.height * y),
                        strokeWidth = strokeWidth,
                        cap = StrokeCap.Round
                    )
                }
            }

            ConnectivityIconType.DATABASE -> {
                val cylinderStroke = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                val left = size.width * 0.18f
                val right = size.width * 0.82f
                drawOval(
                    color = tint,
                    topLeft = Offset(left, size.height * 0.12f),
                    size = Size(right - left, size.height * 0.24f),
                    style = cylinderStroke
                )
                listOf(left, right).forEach { x ->
                    drawLine(
                        color = tint,
                        start = Offset(x, size.height * 0.24f),
                        end = Offset(x, size.height * 0.76f),
                        strokeWidth = strokeWidth,
                        cap = StrokeCap.Round
                    )
                }
                listOf(0.37f, 0.64f).forEach { y ->
                    drawArc(
                        color = tint,
                        startAngle = 0f,
                        sweepAngle = 180f,
                        useCenter = false,
                        topLeft = Offset(left, size.height * y),
                        size = Size(right - left, size.height * 0.24f),
                        style = cylinderStroke
                    )
                }
            }

            ConnectivityIconType.COUNTERS -> {
                drawArc(
                    color = tint,
                    startAngle = 205f,
                    sweepAngle = 235f,
                    useCenter = false,
                    topLeft = Offset(
                        size.width * 0.14f,
                        size.height * 0.14f
                    ),
                    size = Size(
                        size.width * 0.72f,
                        size.height * 0.72f
                    ),
                    style = Stroke(
                        width = strokeWidth,
                        cap = StrokeCap.Round
                    )
                )

                val arrow = Path().apply {
                    moveTo(
                        size.width * 0.16f,
                        size.height * 0.26f
                    )
                    lineTo(
                        size.width * 0.16f,
                        size.height * 0.49f
                    )
                    lineTo(
                        size.width * 0.37f,
                        size.height * 0.38f
                    )
                    close()
                }
                drawPath(
                    path = arrow,
                    color = tint
                )

                drawCircle(
                    color = tint,
                    radius = strokeWidth * 1.25f,
                    center = Offset(
                        size.width * 0.50f,
                        size.height * 0.50f
                    )
                )
            }

            ConnectivityIconType.TALLY -> {
                listOf(0.24f, 0.40f, 0.56f, 0.72f).forEach { x ->
                    drawLine(
                        color = tint,
                        start = Offset(size.width * x, size.height * 0.18f),
                        end = Offset(size.width * x, size.height * 0.82f),
                        strokeWidth = strokeWidth,
                        cap = StrokeCap.Round
                    )
                }
                drawLine(
                    color = tint,
                    start = Offset(size.width * 0.15f, size.height * 0.68f),
                    end = Offset(size.width * 0.81f, size.height * 0.32f),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )
            }
        }
    }
}
