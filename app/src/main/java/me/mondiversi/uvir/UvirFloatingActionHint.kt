package me.mondiversi.uvir

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.delay

internal const val UvirFloatingHintDelayMillis = 180L

/** Observes the existing press: it neither consumes the gesture nor adds a haptic event. */
@Composable
internal fun uvirFloatingActionHintVisible(
    interactionSource: MutableInteractionSource,
    enabled: Boolean = true
): Boolean {
    val pressed by interactionSource.collectIsPressedAsState()
    var held by remember { mutableStateOf(false) }
    LaunchedEffect(pressed, enabled) {
        held = false
        if (pressed && enabled) {
            delay(UvirFloatingHintDelayMillis)
            held = true
        }
    }
    return pressed && enabled && held
}

/** All floating actions share a window-centred label above the full radial action area. */
internal class UvirFloatingHintPositionProvider(
    private val gap: Int,
    private val actionAreaHeight: Int
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize
    ): IntOffset {
        val x = ((windowSize.width - popupContentSize.width) / 2)
            .coerceIn(gap, (windowSize.width - popupContentSize.width - gap).coerceAtLeast(gap))
        val y = (anchorBounds.bottom - actionAreaHeight - gap - popupContentSize.height)
            .coerceIn(gap, (windowSize.height - popupContentSize.height - gap).coerceAtLeast(gap))
        return IntOffset(x, y)
    }
}

/** Non-focusable visual label. Anchor to the unscaled action/menu Box, never a hovered button. */
@Composable
internal fun UvirFloatingActionHint(text: String, visible: Boolean) {
    if (!visible) return
    val density = LocalDensity.current
    val gap = with(density) { 12.dp.roundToPx() }
    val areaHeight = with(density) {
        (56.dp + UvirSecondaryFloatingControlSize + UvirFloatingControlSpacing).roundToPx()
    }
    val positionProvider = remember(gap, areaHeight) {
        UvirFloatingHintPositionProvider(gap, areaHeight)
    }
    Popup(
        popupPositionProvider = positionProvider,
        properties = PopupProperties(
            focusable = false,
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        )
    ) {
        Surface(
            modifier = Modifier.width(280.dp).heightIn(min = 56.dp).testTag("floating_action_hint"),
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.inverseSurface,
            contentColor = MaterialTheme.colorScheme.inverseOnSurface,
            shadowElevation = 3.dp
        ) {
            Box(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelLarge,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }
}
