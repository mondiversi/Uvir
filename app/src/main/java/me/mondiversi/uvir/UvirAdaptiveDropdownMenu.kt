package me.mondiversi.uvir

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties

/** Dropdown windows must use the same adaptive scale as their anchor, including their chrome. */
@Composable
internal fun UvirAdaptiveDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    scrollState: ScrollState = rememberScrollState(),
    containerColor: Color = MenuDefaults.containerColor,
    tonalElevation: Dp = MenuDefaults.TonalElevation,
    scrollbarColor: Color = uvirNeutralContentColor().copy(alpha = 0.45f),
    shape: Shape = MenuDefaults.shape,
    dimAmount: Float = 0f,
    alignToEnd: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    if (!expanded) return
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val direction = LocalLayoutDirection.current
    val margin = with(density) { 8.dp.roundToPx() }
    val position = remember(margin, alignToEnd) { UvirDropdownPositionProvider(margin, alignToEnd) }
    Popup(onDismissRequest = onDismissRequest, popupPositionProvider = position,
        properties = PopupProperties(focusable = true)) {
        UvirDropdownWindowDimming(dimAmount)
        CompositionLocalProvider(LocalDensity provides density, LocalConfiguration provides configuration,
            LocalLayoutDirection provides direction) {
            Surface(
                modifier = Modifier
                    .widthIn(max = (configuration.screenWidthDp.dp - 16.dp).coerceAtLeast(1.dp))
                    .heightIn(max = (configuration.screenHeightDp.dp - 48.dp).coerceAtLeast(1.dp)),
                shape = shape, color = containerColor,
                tonalElevation = tonalElevation, shadowElevation = MenuDefaults.ShadowElevation
            ) {
                Column(modifier.scrollbarOverlay(scrollState, scrollbarColor)
                    .padding(vertical = 8.dp).width(IntrinsicSize.Max)
                    .verticalScroll(scrollState), content = content)
            }
        }
    }
}

/** Prefer below/above the anchor, clamp to the window, and mirror the chosen edge in RTL. */
internal class UvirDropdownPositionProvider(
    private val margin: Int,
    private val alignToEnd: Boolean = false
) : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize,
        layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
        val maxX = (windowSize.width - margin - popupContentSize.width).coerceAtLeast(margin)
        val maxY = (windowSize.height - margin - popupContentSize.height).coerceAtLeast(margin)
        val start = if (layoutDirection == LayoutDirection.Ltr) anchorBounds.left
            else anchorBounds.right - popupContentSize.width
        val end = if (layoutDirection == LayoutDirection.Ltr) anchorBounds.right - popupContentSize.width
            else anchorBounds.left
        val preferred = if (alignToEnd) end else start
        val fallback = if (alignToEnd) start else end
        val x = listOf(preferred, fallback).firstOrNull { it in margin..maxX }
            ?: preferred.coerceIn(margin, maxX)
        val y = listOf(anchorBounds.bottom, anchorBounds.top - popupContentSize.height)
            .firstOrNull { it in margin..maxY } ?: anchorBounds.bottom.coerceIn(margin, maxY)
        return IntOffset(x, y)
    }
}
