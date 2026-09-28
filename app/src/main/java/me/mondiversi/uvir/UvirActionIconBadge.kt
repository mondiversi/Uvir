package me.mondiversi.uvir

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal val UvirActionIconBadgeSize = 32.dp

/** Pick the legible glyph color for both theme accents and live status colors. */
internal fun uvirIconBadgeContentColor(background: Color): Color {
    val dark = Color(0xFF101418)
    val brightness = background.luminance()
    val darkContrast = (brightness + 0.05f) / (dark.luminance() + 0.05f)
    val lightContrast = 1.05f / (brightness + 0.05f)
    return if (darkContrast > lightContrast) dark else Color.White
}

@Composable
internal fun UvirActionIconBadge(
    color: Color,
    enabled: Boolean = true,
    badgeSize: Dp = UvirActionIconBadgeSize,
    content: @Composable () -> Unit
) {
    val background = if (enabled) color else uvirDisabledActionContainerColor()
    val foreground = if (enabled) uvirIconBadgeContentColor(color) else uvirDisabledActionContentColor()
    Box(
        modifier = Modifier.size(badgeSize).background(background, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        CompositionLocalProvider(LocalContentColor provides foreground, content = content)
    }
}
