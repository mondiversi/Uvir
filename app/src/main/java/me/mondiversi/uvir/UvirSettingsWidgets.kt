package me.mondiversi.uvir

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ExpansionChevron(
    expanded: Boolean,
    tint: Color,
    modifier: Modifier = Modifier,
    iconSize: Dp = 20.dp
) {
    val expansion by animateFloatAsState(
        targetValue = if (expanded) 1f else 0f,
        animationSpec = tween(UvirExpansionDurationMillis),
        label = "island-chevron"
    )
    Canvas(
        modifier = modifier.size(iconSize)
    ) {
        val strokeWidth =
            maxOf(
                1.6.dp.toPx(),
                size.minDimension * 0.08f
            )
        val left =
            Offset(
                size.width * 0.27f,
                size.height * (0.38f + 0.24f * expansion)
            )
        val center =
            Offset(
                size.width * 0.50f,
                size.height * (0.62f - 0.24f * expansion)
            )
        val right =
            Offset(
                size.width * 0.73f,
                size.height * (0.38f + 0.24f * expansion)
            )

        drawLine(
            color = tint,
            start = left,
            end = center,
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
        drawLine(
            color = tint,
            start = center,
            end = right,
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
    }
}

@Composable
internal fun DoubleExpansionChevron(
    expanded: Boolean,
    tint: Color,
    modifier: Modifier = Modifier,
    iconSize: Dp = 20.dp
) {
    Canvas(modifier = modifier.size(iconSize)) {
        val strokeWidth = UvirTitleActionIconStrokeWidth.toPx()
        val downwardCenterLevels = floatArrayOf(0.44f, 0.72f)

        downwardCenterLevels.forEach { downwardCenterLevel ->
            val centerLevel =
                if (expanded) {
                    1f - downwardCenterLevel
                } else {
                    downwardCenterLevel
                }
            val downwardSideLevel = downwardCenterLevel - 0.16f
            val sideLevel =
                if (expanded) {
                    1f - downwardSideLevel
                } else {
                    downwardSideLevel
                }
            val left =
                Offset(
                    size.width * 0.27f,
                    size.height * sideLevel
                )
            val center =
                Offset(
                    size.width * 0.50f,
                    size.height * centerLevel
                )
            val right =
                Offset(
                    size.width * 0.73f,
                    size.height * sideLevel
                )

            drawLine(
                color = tint,
                start = left,
                end = center,
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )
            drawLine(
                color = tint,
                start = center,
                end = right,
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )
        }
    }
}

@Composable
internal fun AdaptiveSingleLineButtonText(
    text: String,
    maxFontSize: TextUnit = 14.sp,
    minFontSize: TextUnit = 10.sp
) {
    var fontSize by remember(text, maxFontSize, minFontSize) {
        mutableStateOf(maxFontSize)
    }

    Text(
        text = text,
        modifier = Modifier.fillMaxWidth(),
        fontSize = fontSize,
        maxLines = 1,
        softWrap = false,
        textAlign = TextAlign.Center,
        onTextLayout = { result ->
            if (
                result.hasVisualOverflow &&
                fontSize.value > minFontSize.value
            ) {
                fontSize =
                    (fontSize.value - 0.5f)
                        .coerceAtLeast(minFontSize.value)
                        .sp
            }
        }
    )
}

@Composable
internal fun RowScope.UvirLabeledButtonContent(
    text: String,
    maxFontSize: TextUnit = 14.sp,
    minFontSize: TextUnit = 10.sp,
    icon: @Composable () -> Unit
) {
    icon()
    Spacer(Modifier.width(8.dp))
    AdaptiveSingleLineButtonText(
        text = text,
        maxFontSize = maxFontSize,
        minFontSize = minFontSize
    )
}

@Composable
fun CheckSettingRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    title: String,
    leadingIcon: AutomaticSettingIconType? = null,
    emphasized: Boolean = false,
    compact: Boolean = false,
    enabled: Boolean = true,
    trailingContent: (@Composable () -> Unit)? = null
) {
    val commit = LocalSettingsCommit.current
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val change: (Boolean) -> Unit = { value ->
        if (commit != null) focusManager.clearFocus()
        onCheckedChange(value)
        commit?.commit()
    }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(enabled = enabled) {
                    change(
                        !checked
                    )
                },

        verticalAlignment =
            Alignment.CenterVertically
    ) {

        leadingIcon?.let { icon ->
            AutomaticSettingIcon(
                type = icon,
                modifier = Modifier.size(20.dp),
                tint = LocalContentColor.current.copy(alpha = if (enabled) 1f else 0.62f)
            )

            Spacer(
                Modifier.width(8.dp)
            )
        }

        if (compact) {

            CompositionLocalProvider(
                LocalMinimumInteractiveComponentSize provides 0.dp
            ) {
                Checkbox(
                    checked =
                        checked,
                    onCheckedChange =
                        change,
                    enabled = enabled,
                    modifier =
                        Modifier.size(24.dp)
                )
            }

        } else {

            Checkbox(
                checked =
                    checked,
                onCheckedChange =
                    change,
                enabled = enabled
            )
        }

        Spacer(
            Modifier.width(
                6.dp
            )
        )

        Text(
            text = title,
            color =
                LocalContentColor.current.copy(
                    alpha = if (enabled) 1f else 0.62f
                ),
            fontSize =
                14.sp,
            fontWeight =
                if (emphasized)
                    FontWeight.Bold
                else
                    FontWeight.Normal
        )

        trailingContent?.let { content ->
            Spacer(Modifier.width(5.dp))
            content()
        }
    }
}
