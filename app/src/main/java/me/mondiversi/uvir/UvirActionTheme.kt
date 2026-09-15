package me.mondiversi.uvir

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import me.mondiversi.uvir.ui.theme.Purple80

/** Keep the existing surfaces and day palette; share only the night action accent. */
internal fun uvirActionColorScheme(base: ColorScheme, darkTheme: Boolean): ColorScheme =
    if (darkTheme) {
        base.copy(
            primary = Purple80,
            onPrimary = Color(0xFF381E72),
            inversePrimary = Purple80,
            surfaceTint = Purple80
        )
    } else {
        base
    }

@Composable
internal fun UvirActionTheme(content: @Composable () -> Unit) {
    // MaterialTheme also provides bodyLarge as LocalTextStyle. Preserve the
    // incoming style so sharing action colors does not change text metrics.
    val inheritedTextStyle = LocalTextStyle.current
    MaterialTheme(
        colorScheme = uvirActionColorScheme(MaterialTheme.colorScheme, isSystemInDarkTheme()),
        typography = MaterialTheme.typography,
        shapes = MaterialTheme.shapes,
    ) {
        CompositionLocalProvider(LocalTextStyle provides inheritedTextStyle) {
            content()
        }
    }
}
