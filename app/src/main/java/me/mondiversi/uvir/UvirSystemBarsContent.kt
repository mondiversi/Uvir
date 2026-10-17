package me.mondiversi.uvir

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/**
 * Protect every page, including collapsed title bars, from system UI overlap.
 * Insets remain physical pixels even inside the enlarged tablet density. The
 * padding modifier consumes them so nested Scaffolds do not add them twice;
 * legacy windows that already fit system bars receive no extra padding.
 */
@Composable
internal fun UvirSystemBarsContent(
    backgroundColor: Color,
    insets: WindowInsets = WindowInsets.safeDrawing,
    content: @Composable () -> Unit
) {
    Box(
        Modifier.fillMaxSize()
            .background(backgroundColor)
            .windowInsetsPadding(insets)
    ) {
        content()
    }
}
