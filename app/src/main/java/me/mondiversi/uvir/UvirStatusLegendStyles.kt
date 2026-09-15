package me.mondiversi.uvir

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Shared container treatment for the signal currently reproduced by a
 * sensor test. Dark surfaces need a little more chroma to remain obvious. */
@Composable
internal fun uvirStatusLegendContainerColor(
    highlighted: Boolean,
    accentColor: Color,
    primaryText: Color
): Color =
    if (highlighted) {
        accentColor.copy(
            alpha = if (isSystemInDarkTheme()) 0.30f else 0.20f
        )
    } else {
        primaryText.copy(alpha = 0.055f)
    }
