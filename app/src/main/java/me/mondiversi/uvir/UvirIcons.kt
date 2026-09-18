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
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Title-bar icons share a fixed stroke; smaller in-content icons keep their own sizing. */
@Composable
internal fun UvirTitleActionIcon(
    type: MenuIconType,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current
) {
    val visualVerticalOffset =
        if (type == MenuIconType.EXPORT || type == MenuIconType.IMPORT) {
            (-1.5).dp
        } else {
            0.dp
        }
    UvirMenuIcon(
        type = type,
        modifier =
            modifier
                .size(UvirTitleActionIconSize)
                .offset(y = visualVerticalOffset),
        tint = tint,
        uniformStrokeWidth = UvirTitleActionIconStrokeWidth
    )
}

@Composable
internal fun UvirTitleCollapseAllIcon(
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current
) {
    Canvas(modifier = modifier.size(UvirTitleActionIconSize)) {
        val strokeWidth = UvirTitleActionIconStrokeWidth.toPx()
        fun arrow(tailY: Float, tipY: Float, wingY: Float) {
            drawLine(
                color = tint,
                start = Offset(size.width * 12f / 24f, size.height * tailY / 24f),
                end = Offset(size.width * 12f / 24f, size.height * tipY / 24f),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )
            val head = Path().apply {
                moveTo(size.width * 8f / 24f, size.height * wingY / 24f)
                lineTo(size.width * 12f / 24f, size.height * tipY / 24f)
                lineTo(size.width * 16f / 24f, size.height * wingY / 24f)
            }
            drawPath(
                path = head,
                color = tint,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
        }
        arrow(tailY = 3f, tipY = 9f, wingY = 5f)
        arrow(tailY = 21f, tipY = 15f, wingY = 19f)
    }
}

@Composable
internal fun UvirTitleSaveIcon(
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current
) {
    Canvas(modifier = modifier.size(UvirTitleActionIconSize)) {
        val check = Path().apply {
            moveTo(size.width * 5f / 24f, size.height * 12f / 24f)
            lineTo(size.width * 10f / 24f, size.height * 17f / 24f)
            lineTo(size.width * 19f / 24f, size.height * 7f / 24f)
        }
        drawPath(
            path = check,
            color = tint,
            style = Stroke(
                width = UvirTitleActionIconStrokeWidth.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )
    }
}

internal enum class UvirButtonGlyph {
    PLAY,
    STOP,
    REFRESH
}

@Composable
internal fun UvirButtonGlyphIcon(
    glyph: UvirButtonGlyph,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current
) {
    Canvas(modifier = modifier.size(20.dp)) {
        val stroke = maxOf(1.7.dp.toPx(), size.minDimension * 0.085f)
        when (glyph) {
            UvirButtonGlyph.PLAY -> {
                val path = Path().apply {
                    moveTo(size.width * 0.32f, size.height * 0.22f)
                    lineTo(size.width * 0.78f, size.height * 0.50f)
                    lineTo(size.width * 0.32f, size.height * 0.78f)
                    close()
                }
                drawPath(path, tint)
            }
            UvirButtonGlyph.STOP ->
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(size.width * 0.27f, size.height * 0.27f),
                    size = Size(size.width * 0.46f, size.height * 0.46f),
                    cornerRadius = CornerRadius(size.minDimension * 0.07f)
                )
            UvirButtonGlyph.REFRESH -> {
                drawArc(
                    color = tint,
                    startAngle = -50f,
                    sweepAngle = 285f,
                    useCenter = false,
                    topLeft = Offset(size.width * 0.18f, size.height * 0.18f),
                    size = Size(size.width * 0.64f, size.height * 0.64f),
                    style = Stroke(stroke, cap = StrokeCap.Round)
                )
                val head = Path().apply {
                    moveTo(size.width * 0.76f, size.height * 0.16f)
                    lineTo(size.width * 0.82f, size.height * 0.36f)
                    lineTo(size.width * 0.62f, size.height * 0.31f)
                    close()
                }
                drawPath(head, tint)
            }
        }
    }
}

@Composable
fun UvirMenuIcon(
    type: MenuIconType,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
    strokeScale: Float = 1f,
    uniformStrokeWidth: Dp? = null,
    settingsKnobScale: Float = 1f
) {

    Canvas(
        modifier =
            modifier.size(22.dp)
    ) {

        val iconWidth = size.width
        val iconHeight = size.height
        val strokeWidth =
            uniformStrokeWidth?.toPx() ?: (maxOf(
                1.6.dp.toPx(),
                size.minDimension * 0.08f
            ) * strokeScale)
        fun detailStroke(multiplier: Float): Float =
            if (uniformStrokeWidth != null) strokeWidth else strokeWidth * multiplier

        when (type) {

            MenuIconType.SAVED_MEASUREMENTS -> {
                listOf(
                    0.27f,
                    0.50f,
                    0.73f
                ).forEach { yFraction ->

                    drawCircle(
                        color = tint,
                        radius = strokeWidth * 0.72f,
                        center =
                            Offset(
                                iconWidth * 0.22f,
                                iconHeight * yFraction
                            )
                    )

                    drawLine(
                        color = tint,
                        start =
                            Offset(
                                iconWidth * 0.38f,
                                iconHeight * yFraction
                            ),
                        end =
                            Offset(
                                iconWidth * 0.82f,
                                iconHeight * yFraction
                            ),
                        strokeWidth = strokeWidth,
                        cap = StrokeCap.Round
                    )
                }
            }

            MenuIconType.AUTOMATIC_ACQUISITION -> {
                // A clear camera-style "A" for automatic mode.
                val automaticStrokeWidth =
                    strokeWidth

                drawLine(
                    color = tint,
                    start =
                        Offset(
                            iconWidth * 0.20f,
                            iconHeight * 0.84f
                        ),
                    end =
                        Offset(
                            iconWidth * 0.50f,
                            iconHeight * 0.16f
                        ),
                    strokeWidth = automaticStrokeWidth,
                    cap = StrokeCap.Round
                )

                drawLine(
                    color = tint,
                    start =
                        Offset(
                            iconWidth * 0.50f,
                            iconHeight * 0.16f
                        ),
                    end =
                        Offset(
                            iconWidth * 0.80f,
                            iconHeight * 0.84f
                        ),
                    strokeWidth = automaticStrokeWidth,
                    cap = StrokeCap.Round
                )

                drawLine(
                    color = tint,
                    start =
                        Offset(
                            iconWidth * 0.32f,
                            iconHeight * 0.58f
                        ),
                    end =
                        Offset(
                            iconWidth * 0.68f,
                            iconHeight * 0.58f
                        ),
                    strokeWidth = automaticStrokeWidth,
                    cap = StrokeCap.Round
                )
            }

            MenuIconType.ALERT_LOG -> {
                drawArc(
                    color = tint,
                    startAngle = 190f,
                    sweepAngle = 160f,
                    useCenter = false,
                    topLeft = Offset(
                        iconWidth * 0.24f,
                        iconHeight * 0.18f
                    ),
                    size = Size(
                        iconWidth * 0.52f,
                        iconHeight * 0.62f
                    ),
                    style = Stroke(
                        width = strokeWidth,
                        cap = StrokeCap.Round
                    )
                )
                drawLine(
                    color = tint,
                    start = Offset(
                        iconWidth * 0.24f,
                        iconHeight * 0.58f
                    ),
                    end = Offset(
                        iconWidth * 0.16f,
                        iconHeight * 0.76f
                    ),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = tint,
                    start = Offset(
                        iconWidth * 0.76f,
                        iconHeight * 0.58f
                    ),
                    end = Offset(
                        iconWidth * 0.84f,
                        iconHeight * 0.76f
                    ),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = tint,
                    start = Offset(
                        iconWidth * 0.16f,
                        iconHeight * 0.76f
                    ),
                    end = Offset(
                        iconWidth * 0.84f,
                        iconHeight * 0.76f
                    ),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )
                drawCircle(
                    color = tint,
                    radius = strokeWidth,
                    center = Offset(
                        iconWidth * 0.50f,
                        iconHeight * 0.88f
                    )
                )
                drawCircle(
                    color = tint,
                    radius = strokeWidth * 0.85f,
                    center = Offset(
                        iconWidth * 0.50f,
                        iconHeight * 0.16f
                    )
                )
            }

            MenuIconType.CONNECTIVITY -> {

                drawArc(
                    color = tint,
                    startAngle = 220f,
                    sweepAngle = 100f,
                    useCenter = false,
                    topLeft =
                        Offset(
                            iconWidth * 0.12f,
                            iconHeight * 0.10f
                        ),
                    size =
                        Size(
                            iconWidth * 0.76f,
                            iconHeight * 0.76f
                        ),
                    style =
                        Stroke(
                            width = strokeWidth,
                            cap = StrokeCap.Round
                        )
                )

                drawArc(
                    color = tint,
                    startAngle = 220f,
                    sweepAngle = 100f,
                    useCenter = false,
                    topLeft =
                        Offset(
                            iconWidth * 0.28f,
                            iconHeight * 0.37f
                        ),
                    size =
                        Size(
                            iconWidth * 0.44f,
                            iconHeight * 0.44f
                        ),
                    style =
                        Stroke(
                            width = strokeWidth,
                            cap = StrokeCap.Round
                        )
                )

                drawCircle(
                    color = tint,
                    radius = strokeWidth * 1.15f,
                    center =
                        Offset(
                            iconWidth * 0.50f,
                            iconHeight * 0.82f
                        )
                )
            }

            MenuIconType.ACQUISITION_PARAMETERS -> {

                val rows =
                    listOf(
                        0.25f to 0.36f,
                        0.50f to 0.66f,
                        0.75f to 0.44f
                    )

                rows.forEach {
                        (yFraction, knobFraction) ->

                    drawLine(
                        color = tint,
                        start =
                            Offset(
                                iconWidth * 0.12f,
                                iconHeight * yFraction
                            ),
                        end =
                            Offset(
                                iconWidth * 0.88f,
                                iconHeight * yFraction
                            ),
                        strokeWidth = strokeWidth,
                        cap = StrokeCap.Round
                    )

                    drawCircle(
                        color = tint,
                        radius =
                            strokeWidth * 1.45f * settingsKnobScale,
                        center =
                            Offset(
                                iconWidth * knobFraction,
                                iconHeight * yFraction
                            )
                    )
                }
            }

            MenuIconType.MEASUREMENT_DATE -> {

                drawRoundRect(
                    color = tint,
                    topLeft =
                        Offset(
                            iconWidth * 0.14f,
                            iconHeight * 0.19f
                        ),
                    size =
                        Size(
                            iconWidth * 0.72f,
                            iconHeight * 0.67f
                        ),
                    cornerRadius =
                        CornerRadius(
                            iconWidth * 0.08f,
                            iconWidth * 0.08f
                        ),
                    style =
                        Stroke(
                            width = strokeWidth
                        )
                )

                drawLine(
                    color = tint,
                    start =
                        Offset(
                            iconWidth * 0.14f,
                            iconHeight * 0.39f
                        ),
                    end =
                        Offset(
                            iconWidth * 0.86f,
                            iconHeight * 0.39f
                        ),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )

                listOf(
                    0.34f,
                    0.66f
                ).forEach { xFraction ->

                    drawLine(
                        color = tint,
                        start =
                            Offset(
                                iconWidth * xFraction,
                                iconHeight * 0.10f
                            ),
                        end =
                            Offset(
                                iconWidth * xFraction,
                                iconHeight * 0.28f
                            ),
                        strokeWidth =
                            detailStroke(1.10f),
                        cap = StrokeCap.Round
                    )
                }

                listOf(
                    0.33f to 0.55f,
                    0.52f to 0.55f,
                    0.71f to 0.55f,
                    0.33f to 0.72f,
                    0.52f to 0.72f
                ).forEach {
                        (xFraction, yFraction) ->

                    drawCircle(
                        color = tint,
                        radius =
                            strokeWidth * 0.65f,
                        center =
                            Offset(
                                iconWidth * xFraction,
                                iconHeight * yFraction
                            )
                    )
                }
            }

            MenuIconType.DELETE -> {

                drawLine(
                    color = tint,
                    start =
                        Offset(
                            iconWidth * 0.24f,
                            iconHeight * 0.29f
                        ),
                    end =
                        Offset(
                            iconWidth * 0.76f,
                            iconHeight * 0.29f
                        ),
                    strokeWidth =
                        detailStroke(1.15f),
                    cap = StrokeCap.Round
                )

                drawLine(
                    color = tint,
                    start =
                        Offset(
                            iconWidth * 0.41f,
                            iconHeight * 0.18f
                        ),
                    end =
                        Offset(
                            iconWidth * 0.59f,
                            iconHeight * 0.18f
                        ),
                    strokeWidth =
                        detailStroke(1.15f),
                    cap = StrokeCap.Round
                )

                drawRoundRect(
                    color = tint,
                    topLeft =
                        Offset(
                            iconWidth * 0.29f,
                            iconHeight * 0.36f
                        ),
                    size =
                        Size(
                            iconWidth * 0.42f,
                            iconHeight * 0.48f
                        ),
                    cornerRadius =
                        CornerRadius(
                            iconWidth * 0.06f,
                            iconWidth * 0.06f
                        ),
                    style =
                        Stroke(
                            width = strokeWidth
                        )
                )

                listOf(
                    0.43f,
                    0.57f
                ).forEach { xFraction ->

                    drawLine(
                        color = tint,
                        start =
                            Offset(
                                iconWidth * xFraction,
                                iconHeight * 0.47f
                            ),
                        end =
                            Offset(
                                iconWidth * xFraction,
                                iconHeight * 0.72f
                            ),
                        strokeWidth = strokeWidth,
                        cap = StrokeCap.Round
                    )
                }
            }

            MenuIconType.SELECT -> {

                drawRoundRect(
                    color = tint,
                    topLeft =
                        Offset(
                            iconWidth * 0.14f,
                            iconHeight * 0.14f
                        ),
                    size =
                        Size(
                            iconWidth * 0.72f,
                            iconHeight * 0.72f
                        ),
                    cornerRadius =
                        CornerRadius(
                            iconWidth * 0.10f,
                            iconWidth * 0.10f
                        ),
                    style =
                        Stroke(
                            width = strokeWidth
                        )
                )

                drawLine(
                    color = tint,
                    start =
                        Offset(
                            iconWidth * 0.30f,
                            iconHeight * 0.51f
                        ),
                    end =
                        Offset(
                            iconWidth * 0.44f,
                            iconHeight * 0.66f
                        ),
                    strokeWidth =
                        detailStroke(1.15f),
                    cap = StrokeCap.Round
                )

                drawLine(
                    color = tint,
                    start =
                        Offset(
                            iconWidth * 0.44f,
                            iconHeight * 0.66f
                        ),
                    end =
                        Offset(
                            iconWidth * 0.72f,
                            iconHeight * 0.36f
                        ),
                    strokeWidth =
                        detailStroke(1.15f),
                    cap = StrokeCap.Round
                )
            }

            MenuIconType.EXPORT,
            MenuIconType.IMPORT -> {
                val exporting = type == MenuIconType.EXPORT
                val centerX = iconWidth * 0.50f
                val trayY = iconHeight * 0.72f
                val shaftStartY =
                    if (exporting) iconHeight * 0.63f else iconHeight * 0.27f
                val shaftEndY =
                    if (exporting) iconHeight * 0.25f else iconHeight * 0.65f
                val arrowY = shaftEndY
                val arrowWingY =
                    if (exporting) iconHeight * 0.39f else iconHeight * 0.51f

                drawLine(
                    color = tint,
                    start = Offset(iconWidth * 0.20f, trayY),
                    end = Offset(iconWidth * 0.20f, iconHeight * 0.88f),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = tint,
                    start = Offset(iconWidth * 0.20f, iconHeight * 0.88f),
                    end = Offset(iconWidth * 0.80f, iconHeight * 0.88f),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = tint,
                    start = Offset(iconWidth * 0.80f, iconHeight * 0.88f),
                    end = Offset(iconWidth * 0.80f, trayY),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = tint,
                    start = Offset(centerX, shaftStartY),
                    end = Offset(centerX, shaftEndY),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = tint,
                    start = Offset(centerX, arrowY),
                    end = Offset(iconWidth * 0.35f, arrowWingY),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = tint,
                    start = Offset(centerX, arrowY),
                    end = Offset(iconWidth * 0.65f, arrowWingY),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )
            }

            MenuIconType.SHARE -> {

                val leftCenter =
                    Offset(
                        iconWidth * 0.28f,
                        iconHeight * 0.50f
                    )
                val upperCenter =
                    Offset(
                        iconWidth * 0.70f,
                        iconHeight * 0.27f
                    )
                val lowerCenter =
                    Offset(
                        iconWidth * 0.70f,
                        iconHeight * 0.73f
                    )

                drawLine(
                    color = tint,
                    start = leftCenter,
                    end = upperCenter,
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )

                drawLine(
                    color = tint,
                    start = leftCenter,
                    end = lowerCenter,
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )

                listOf(
                    leftCenter,
                    upperCenter,
                    lowerCenter
                ).forEach { center ->
                    drawCircle(
                        color = tint,
                        radius =
                            size.minDimension * 0.105f,
                        center = center
                    )
                }
            }

            MenuIconType.VERSION_INFO -> {

                val center =
                    Offset(
                        iconWidth / 2f,
                        iconHeight / 2f
                    )

                drawCircle(
                    color = tint,
                    radius =
                        size.minDimension * 0.39f,
                    center = center,
                    style =
                        Stroke(
                            width = strokeWidth
                        )
                )

                drawCircle(
                    color = tint,
                    radius =
                        strokeWidth * 0.70f,
                    center =
                        Offset(
                            iconWidth * 0.50f,
                            iconHeight * 0.31f
                        )
                )

                drawLine(
                    color = tint,
                    start =
                        Offset(
                            iconWidth * 0.50f,
                            iconHeight * 0.45f
                        ),
                    end =
                        Offset(
                            iconWidth * 0.50f,
                            iconHeight * 0.70f
                        ),
                    strokeWidth =
                        detailStroke(1.10f),
                    cap = StrokeCap.Round
                )
            }

            MenuIconType.POWER -> {
                val inset = size.minDimension * 0.16f

                drawArc(
                    color = tint,
                    startAngle = -42f,
                    sweepAngle = 264f,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = Size(
                        iconWidth - inset * 2f,
                        iconHeight - inset * 2f
                    ),
                    style = Stroke(
                        width = strokeWidth,
                        cap = StrokeCap.Round
                    )
                )

                drawLine(
                    color = tint,
                    start = Offset(iconWidth * 0.50f, iconHeight * 0.10f),
                    end = Offset(iconWidth * 0.50f, iconHeight * 0.49f),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )
            }
        }
    }
}

@Composable
fun CaptureMeasurementIcon(
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
    useCompactStroke: Boolean = false
) {

    Canvas(
        modifier =
            modifier.size(24.dp)
    ) {

        val strokeWidth =
            if (useCompactStroke) {
                1.25.dp.toPx()
            } else {
                maxOf(
                    1.6.dp.toPx(),
                    size.minDimension * 0.08f
                )
            }

        val center =
            Offset(
                size.width / 2f,
                size.height / 2f
            )

        val outerRadius =
            size.minDimension * 0.39f

        val innerRadius =
            size.minDimension * 0.13f

        fun pointAt(
            radius: Float,
            angleDegrees: Double
        ): Offset {

            val angle =
                Math.toRadians(
                    angleDegrees
                )

            return Offset(
                x =
                    center.x +
                            kotlin.math.cos(angle)
                                .toFloat() * radius,
                y =
                    center.y +
                            kotlin.math.sin(angle)
                                .toFloat() * radius
            )
        }

        drawCircle(
            color = tint,
            radius = outerRadius,
            center = center,
            style =
                Stroke(
                    width = strokeWidth
                )
        )

        val innerPoints =
            List(6) { index ->
                pointAt(
                    radius = innerRadius,
                    angleDegrees =
                        -60.0 + index * 60.0
                )
            }

        repeat(6) { index ->

            drawLine(
                color = tint,
                start =
                    pointAt(
                        radius = outerRadius,
                        angleDegrees =
                            -90.0 + index * 60.0
                    ),
                end =
                    innerPoints[index],
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )

            drawLine(
                color = tint,
                start =
                    innerPoints[index],
                end =
                    innerPoints[
                        (index + 1) % 6
                    ],
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )
        }
    }
}

@Composable
fun GitHubIcon(
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current
) {
    Canvas(
        modifier = modifier.size(20.dp)
    ) {
        val strokeWidth =
            maxOf(
                1.6.dp.toPx(),
                size.minDimension * 0.08f
            )

        val head = Path().apply {
            moveTo(
                size.width * 0.20f,
                size.height * 0.38f
            )
            lineTo(
                size.width * 0.24f,
                size.height * 0.14f
            )
            lineTo(
                size.width * 0.39f,
                size.height * 0.25f
            )
            cubicTo(
                size.width * 0.46f,
                size.height * 0.22f,
                size.width * 0.54f,
                size.height * 0.22f,
                size.width * 0.61f,
                size.height * 0.25f
            )
            lineTo(
                size.width * 0.76f,
                size.height * 0.14f
            )
            lineTo(
                size.width * 0.80f,
                size.height * 0.38f
            )
            cubicTo(
                size.width * 0.88f,
                size.height * 0.46f,
                size.width * 0.88f,
                size.height * 0.61f,
                size.width * 0.82f,
                size.height * 0.70f
            )
            cubicTo(
                size.width * 0.77f,
                size.height * 0.79f,
                size.width * 0.67f,
                size.height * 0.83f,
                size.width * 0.58f,
                size.height * 0.84f
            )
            lineTo(
                size.width * 0.58f,
                size.height * 0.94f
            )
            lineTo(
                size.width * 0.42f,
                size.height * 0.94f
            )
            lineTo(
                size.width * 0.42f,
                size.height * 0.84f
            )
            cubicTo(
                size.width * 0.30f,
                size.height * 0.82f,
                size.width * 0.20f,
                size.height * 0.77f,
                size.width * 0.16f,
                size.height * 0.67f
            )
            cubicTo(
                size.width * 0.11f,
                size.height * 0.56f,
                size.width * 0.13f,
                size.height * 0.45f,
                size.width * 0.20f,
                size.height * 0.38f
            )
            close()
        }

        drawPath(
            path = head,
            color = tint
        )

        val tail = Path().apply {
            moveTo(
                size.width * 0.43f,
                size.height * 0.87f
            )
            cubicTo(
                size.width * 0.30f,
                size.height * 0.89f,
                size.width * 0.32f,
                size.height * 0.70f,
                size.width * 0.17f,
                size.height * 0.71f
            )
            cubicTo(
                size.width * 0.10f,
                size.height * 0.71f,
                size.width * 0.08f,
                size.height * 0.66f,
                size.width * 0.06f,
                size.height * 0.61f
            )
        }

        drawPath(
            path = tail,
            color = tint,
            style = Stroke(
                width = strokeWidth,
                cap = StrokeCap.Round
            )
        )
    }
}

enum class AutomaticSettingIconType {
    INTERVAL,
    START_DELAY,
    DURATION,
    MAXIMUM,
    CONDITIONAL,
    CONFIGURE,
    EXTERNAL_COMMAND
}

@Composable
fun AutomaticSettingIcon(
    type: AutomaticSettingIconType,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current
) {
    Canvas(
        modifier = modifier.size(20.dp)
    ) {
        val strokeWidth =
            maxOf(
                1.6.dp.toPx(),
                size.minDimension * 0.08f
            )

        when (type) {
            AutomaticSettingIconType.INTERVAL -> {
                val center =
                    Offset(
                        size.width * 0.50f,
                        size.height * 0.53f
                    )

                drawCircle(
                    color = tint,
                    radius = size.minDimension * 0.34f,
                    center = center,
                    style = Stroke(width = strokeWidth)
                )
                drawLine(
                    color = tint,
                    start = center,
                    end = Offset(
                        size.width * 0.50f,
                        size.height * 0.31f
                    ),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = tint,
                    start = center,
                    end = Offset(
                        size.width * 0.68f,
                        size.height * 0.62f
                    ),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )
            }

            AutomaticSettingIconType.START_DELAY -> {
                val play = Path().apply {
                    moveTo(
                        size.width * 0.31f,
                        size.height * 0.22f
                    )
                    lineTo(
                        size.width * 0.31f,
                        size.height * 0.78f
                    )
                    lineTo(
                        size.width * 0.75f,
                        size.height * 0.50f
                    )
                    close()
                }
                drawPath(
                    path = play,
                    color = tint,
                    style = Stroke(
                        width = strokeWidth,
                        join = androidx.compose.ui.graphics.StrokeJoin.Round
                    )
                )
            }

            AutomaticSettingIconType.DURATION -> {
                drawLine(
                    color = tint,
                    start = Offset(
                        size.width * 0.25f,
                        size.height * 0.16f
                    ),
                    end = Offset(
                        size.width * 0.75f,
                        size.height * 0.16f
                    ),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = tint,
                    start = Offset(
                        size.width * 0.25f,
                        size.height * 0.84f
                    ),
                    end = Offset(
                        size.width * 0.75f,
                        size.height * 0.84f
                    ),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = tint,
                    start = Offset(
                        size.width * 0.31f,
                        size.height * 0.23f
                    ),
                    end = Offset(
                        size.width * 0.69f,
                        size.height * 0.77f
                    ),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = tint,
                    start = Offset(
                        size.width * 0.69f,
                        size.height * 0.23f
                    ),
                    end = Offset(
                        size.width * 0.31f,
                        size.height * 0.77f
                    ),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )
            }

            AutomaticSettingIconType.CONDITIONAL -> {
                // A decision diamond, using the other automatic-setting icons' size and stroke.
                val decision = Path().apply {
                    moveTo(size.width * 0.50f, size.height * 0.14f)
                    lineTo(size.width * 0.86f, size.height * 0.50f)
                    lineTo(size.width * 0.50f, size.height * 0.86f)
                    lineTo(size.width * 0.14f, size.height * 0.50f)
                    close()
                }
                drawPath(
                    decision, tint,
                    style = Stroke(width = strokeWidth,
                        join = androidx.compose.ui.graphics.StrokeJoin.Round)
                )
            }

            AutomaticSettingIconType.CONFIGURE -> {
                // Three compact sliders distinguish the action from the
                // decision diamond used by the parent condition section.
                val rows = listOf(
                    Triple(0.28f, 0.72f, 0.38f),
                    Triple(0.28f, 0.72f, 0.62f),
                    Triple(0.28f, 0.72f, 0.50f)
                )
                rows.forEachIndexed { index, (startX, endX, knobX) ->
                    val y = size.height * (0.26f + index * 0.24f)
                    drawLine(
                        color = tint,
                        start = Offset(size.width * startX, y),
                        end = Offset(size.width * endX, y),
                        strokeWidth = strokeWidth,
                        cap = StrokeCap.Round
                    )
                    drawCircle(
                        color = tint,
                        radius = strokeWidth * 1.15f,
                        center = Offset(size.width * knobX, y)
                    )
                }
            }

            AutomaticSettingIconType.EXTERNAL_COMMAND -> {
                // An arrow entering a contact: a physical input commanding the sensor.
                drawLine(
                    color = tint,
                    start = Offset(size.width * 0.16f, size.height * 0.50f),
                    end = Offset(size.width * 0.62f, size.height * 0.50f),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )
                val arrow = Path().apply {
                    moveTo(size.width * 0.47f, size.height * 0.34f)
                    lineTo(size.width * 0.64f, size.height * 0.50f)
                    lineTo(size.width * 0.47f, size.height * 0.66f)
                }
                drawPath(
                    arrow,
                    tint,
                    style = Stroke(
                        width = strokeWidth,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
                drawLine(
                    color = tint,
                    start = Offset(size.width * 0.78f, size.height * 0.25f),
                    end = Offset(size.width * 0.78f, size.height * 0.75f),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )
            }

            AutomaticSettingIconType.MAXIMUM -> {
                listOf(0.38f, 0.62f).forEach { x ->
                    drawLine(
                        color = tint,
                        start = Offset(
                            size.width * x,
                            size.height * 0.18f
                        ),
                        end = Offset(
                            size.width * x,
                            size.height * 0.82f
                        ),
                        strokeWidth = strokeWidth,
                        cap = StrokeCap.Round
                    )
                }
                listOf(0.39f, 0.63f).forEach { y ->
                    drawLine(
                        color = tint,
                        start = Offset(
                            size.width * 0.20f,
                            size.height * y
                        ),
                        end = Offset(
                            size.width * 0.80f,
                            size.height * y
                        ),
                        strokeWidth = strokeWidth,
                        cap = StrokeCap.Round
                    )
                }
            }
        }
    }
}

@Composable
fun WhatsNewIcon(
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current
) {
    Canvas(
        modifier = modifier.size(20.dp)
    ) {
        val strokeWidth =
            maxOf(
                1.6.dp.toPx(),
                size.minDimension * 0.08f
            )

        fun sparkle(
            center: Offset,
            radius: Float
        ) {
            drawLine(
                color = tint,
                start = Offset(center.x, center.y - radius),
                end = Offset(center.x, center.y + radius),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )
            drawLine(
                color = tint,
                start = Offset(center.x - radius, center.y),
                end = Offset(center.x + radius, center.y),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )
        }

        sparkle(
            center = Offset(
                size.width * 0.42f,
                size.height * 0.42f
            ),
            radius = size.minDimension * 0.27f
        )
        sparkle(
            center = Offset(
                size.width * 0.73f,
                size.height * 0.72f
            ),
            radius = size.minDimension * 0.12f
        )
    }
}

@Composable
internal fun UvirPasswordVisibilityIcon(
    visible: Boolean,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current
) {
    Canvas(modifier = modifier.size(22.dp)) {
        val strokeWidth = maxOf(1.6.dp.toPx(), size.minDimension * 0.08f)
        val eye = Path().apply {
            moveTo(size.width * 0.08f, size.height * 0.50f)
            cubicTo(
                size.width * 0.28f,
                size.height * 0.18f,
                size.width * 0.72f,
                size.height * 0.18f,
                size.width * 0.92f,
                size.height * 0.50f
            )
            cubicTo(
                size.width * 0.72f,
                size.height * 0.82f,
                size.width * 0.28f,
                size.height * 0.82f,
                size.width * 0.08f,
                size.height * 0.50f
            )
        }
        drawPath(
            path = eye,
            color = tint,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )
        drawCircle(
            color = tint,
            radius = size.minDimension * 0.12f,
            center = Offset(size.width * 0.50f, size.height * 0.50f)
        )
        if (!visible) {
            drawLine(
                color = tint,
                start = Offset(size.width * 0.18f, size.height * 0.16f),
                end = Offset(size.width * 0.82f, size.height * 0.84f),
                strokeWidth = strokeWidth * 1.15f,
                cap = StrokeCap.Round
            )
        }
    }
}

@Composable
internal fun UvirDisclosureChevron(
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current
) {
    Canvas(
        modifier =
            modifier.size(
                width = 10.dp,
                height = 16.dp
            )
    ) {
        val centerY = size.height / 2f
        val startX = size.width * 0.22f
        val tipX = size.width * 0.76f
        val halfHeight = size.height * 0.32f
        val strokeWidth =
            maxOf(
                1.8.dp.toPx(),
                size.minDimension * 0.17f
            )

        drawLine(
            color = tint,
            start = Offset(startX, centerY - halfHeight),
            end = Offset(tipX, centerY),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
        drawLine(
            color = tint,
            start = Offset(tipX, centerY),
            end = Offset(startX, centerY + halfHeight),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
    }
}
