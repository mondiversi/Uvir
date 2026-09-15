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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

enum class ConnectivityIconType {
    SENSOR_CONNECTION,
    SENSOR,
    CALIBRATION,
    SAMPLING,
    ALERT,
    NUMERIC_FORMAT,
    LANGUAGE,
    DEBUG,
    EXPORT,
    USB,
    BLUETOOTH,
    WIFI,
    INTERNET,
    COUNTERS
}

@Composable
fun ConnectivitySectionIcon(
    type: ConnectivityIconType,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
    strokeScale: Float = 1f
) {
    Canvas(
        modifier = modifier.size(20.dp)
    ) {
        val strokeWidth =
            maxOf(
                1.6.dp.toPx(),
                size.minDimension * 0.08f
            ) * strokeScale

        when (type) {
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
                drawLine(
                    color = tint,
                    start = Offset(size.width * 0.50f, size.height * 0.18f),
                    end = Offset(size.width * 0.50f, size.height * 0.80f),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = tint,
                    start = Offset(size.width * 0.50f, size.height * 0.48f),
                    end = Offset(size.width * 0.25f, size.height * 0.33f),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = tint,
                    start = Offset(size.width * 0.50f, size.height * 0.61f),
                    end = Offset(size.width * 0.75f, size.height * 0.45f),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )
                drawCircle(
                    color = tint,
                    radius = strokeWidth * 1.15f,
                    center = Offset(size.width * 0.25f, size.height * 0.33f)
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
                    radius = strokeWidth * 1.35f,
                    center = Offset(size.width * 0.50f, size.height * 0.83f)
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
                // Three vertically centered signal arcs, without a separate bottom dot.
                drawArc(
                    color = tint,
                    startAngle = 220f,
                    sweepAngle = 100f,
                    useCenter = false,
                    topLeft = Offset(size.width * 0.10f, size.height * 0.1736f),
                    size = Size(size.width * 0.80f, size.height * 0.80f),
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
                drawArc(
                    color = tint,
                    startAngle = 220f,
                    sweepAngle = 100f,
                    useCenter = false,
                    topLeft = Offset(size.width * 0.28f, size.height * 0.4736f),
                    size = Size(size.width * 0.44f, size.height * 0.44f),
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
                drawArc(
                    color = tint,
                    startAngle = 220f,
                    sweepAngle = 100f,
                    useCenter = false,
                    topLeft = Offset(size.width * 0.38f, size.height * 0.7836f),
                    size = Size(size.width * 0.24f, size.height * 0.24f),
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
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
        }
    }
}
