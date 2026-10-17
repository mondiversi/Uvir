package me.mondiversi.uvir

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Align file counts and descriptions after the leading checkbox and icon.
internal val UvirExportOptionDetailInset = 72.dp

@Composable
private fun ShareFormatIcon(
    format: MeasurementShareFormat,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current
) {
    Box(
        modifier = modifier.size(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier =
                Modifier
                    .fillMaxSize()
                    .graphicsLayer(alpha = tint.alpha)
        ) {
            val tint = tint.copy(alpha = 1f)
            val strokeWidth =
                maxOf(
                    1.5.dp.toPx(),
                    size.minDimension * 0.075f
                )

            when (format) {
                MeasurementShareFormat.CSV -> {
                    val documentPath = Path().apply {
                        moveTo(
                            size.width * 0.22f,
                            size.height * 0.08f
                        )
                        lineTo(
                            size.width * 0.62f,
                            size.height * 0.08f
                        )
                        lineTo(
                            size.width * 0.80f,
                            size.height * 0.27f
                        )
                        lineTo(
                            size.width * 0.80f,
                            size.height * 0.92f
                        )
                        lineTo(
                            size.width * 0.22f,
                            size.height * 0.92f
                        )
                        close()
                    }
                    drawPath(
                        path = documentPath,
                        color = tint,
                        style = Stroke(
                            width = strokeWidth,
                            join = androidx.compose.ui.graphics.StrokeJoin.Round
                        )
                    )
                    drawLine(
                        color = tint,
                        start = Offset(
                            size.width * 0.62f,
                            size.height * 0.08f
                        ),
                        end = Offset(
                            size.width * 0.62f,
                            size.height * 0.27f
                        ),
                        strokeWidth = strokeWidth
                    )
                    drawLine(
                        color = tint,
                        start = Offset(
                            size.width * 0.62f,
                            size.height * 0.27f
                        ),
                        end = Offset(
                            size.width * 0.80f,
                            size.height * 0.27f
                        ),
                        strokeWidth = strokeWidth
                    )
                }

                MeasurementShareFormat.READABLE_TABLE,
                MeasurementShareFormat.BOTH -> {
                    drawRoundRect(
                        color = tint,
                        topLeft = Offset(
                            size.width * 0.08f,
                            size.height * 0.16f
                        ),
                        size = Size(
                            size.width * 0.84f,
                            size.height * 0.68f
                        ),
                        cornerRadius = CornerRadius(
                            strokeWidth,
                            strokeWidth
                        ),
                        style = Stroke(width = strokeWidth)
                    )
                    drawRect(
                        color = tint.copy(alpha = 0.18f),
                        topLeft = Offset(
                            size.width * 0.08f,
                            size.height * 0.16f
                        ),
                        size = Size(
                            size.width * 0.84f,
                            size.height * 0.20f
                        )
                    )
                    listOf(0.36f, 0.60f).forEach { y ->
                        drawLine(
                            color = tint,
                            start = Offset(
                                size.width * 0.08f,
                                size.height * y
                            ),
                            end = Offset(
                                size.width * 0.92f,
                                size.height * y
                            ),
                            strokeWidth = strokeWidth
                        )
                    }
                    drawLine(
                        color = tint,
                        start = Offset(
                            size.width * 0.44f,
                            size.height * 0.16f
                        ),
                        end = Offset(
                            size.width * 0.44f,
                            size.height * 0.84f
                        ),
                        strokeWidth = strokeWidth
                    )
                }
            }
        }

        if (format == MeasurementShareFormat.CSV) {
            Text(
                text = "CSV",
                modifier = Modifier.padding(top = 5.dp),
                color = tint,
                fontSize = 6.5.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1
            )
        }
    }
}

@Composable
internal fun ShareFormatOptionRow(
    format: MeasurementShareFormat,
    selected: Boolean,
    label: String,
    primaryText: Color,
    secondaryText: Color,
    onClick: () -> Unit
) {
    val toggle: () -> Unit = onClick
    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = toggle),
        shape = RoundedCornerShape(10.dp),
        color =
            if (selected) {
                primaryText.copy(alpha = 0.08f)
            } else {
                Color.Transparent
            }
    ) {
        Row(
            modifier =
                Modifier.padding(
                    horizontal = 10.dp,
                    vertical = 9.dp
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CompositionLocalProvider(
                LocalMinimumInteractiveComponentSize provides 0.dp
            ) {
                Checkbox(
                    checked = selected,
                    onCheckedChange = { toggle() },
                    modifier = Modifier.size(24.dp),
                    colors = uvirCheckboxColors(uncheckedColor = secondaryText)
                )
            }

            Spacer(Modifier.width(10.dp))

            ShareFormatIcon(
                format = format,
                modifier = Modifier.size(22.dp),
                tint = primaryText
            )

            Spacer(Modifier.width(10.dp))

            Text(
                text = label,
                modifier = Modifier.weight(1f),
                color = primaryText,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )

        }
    }
}

@Composable
internal fun ShareChartsOptionRow(
    selected: Boolean,
    primaryText: Color,
    secondaryText: Color,
    onClick: () -> Unit
) {
    val toggle: () -> Unit = onClick
    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = toggle),
        shape = RoundedCornerShape(10.dp),
        color =
            if (selected) {
                primaryText.copy(alpha = 0.08f)
            } else {
                Color.Transparent
            }
    ) {
        Row(
            modifier =
                Modifier.padding(
                    horizontal = 10.dp,
                    vertical = 9.dp
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CompositionLocalProvider(
                LocalMinimumInteractiveComponentSize provides 0.dp
            ) {
                Checkbox(
                    checked = selected,
                    onCheckedChange = { toggle() },
                    modifier = Modifier.size(24.dp),
                    colors = uvirCheckboxColors(uncheckedColor = secondaryText)
                )
            }

            Spacer(Modifier.width(10.dp))

            SessionChartIcon(
                modifier = Modifier.size(22.dp),
                tint = primaryText
            )

            Spacer(Modifier.width(10.dp))

            Text(
                text = stringResource(R.string.share_as_charts),
                modifier = Modifier.weight(1f),
                color = primaryText,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )

        }
    }
}

@Composable
internal fun ChartExportModeRow(
    selected: Boolean,
    label: String,
    primaryText: Color,
    secondaryText: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val select: () -> Unit = onClick
    Surface(
        modifier =
            modifier
                .fillMaxWidth()
                .clickable(onClick = select),
        shape = RoundedCornerShape(10.dp),
        color =
            if (selected) {
                primaryText.copy(alpha = 0.06f)
            } else {
                Color.Transparent
            }
    ) {
        Row(
            modifier =
                Modifier.padding(
                    start = 38.dp,
                    end = 10.dp,
                    top = 5.dp,
                    bottom = 5.dp
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CompositionLocalProvider(
                LocalMinimumInteractiveComponentSize provides 0.dp
            ) {
                RadioButton(
                    selected = selected,
                    onClick = select,
                    modifier = Modifier.size(22.dp),
                    colors =
                        uvirRadioButtonColors(unselectedColor = secondaryText)
                )
            }

            Spacer(Modifier.width(8.dp))

            Text(
                text = label,
                modifier = Modifier.weight(1f),
                color = primaryText,
                fontSize = 12.5.sp,
                lineHeight = 15.sp
            )
        }
    }
}
