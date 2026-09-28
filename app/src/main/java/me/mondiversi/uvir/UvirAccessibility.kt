package me.mondiversi.uvir

import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.AccessibilityAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.unit.Dp

@Composable
internal fun UvirAccessibleIconButton(
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    hapticOnPress: Boolean = false,
    selected: Boolean? = null,
    pressedColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary,
    pressedVisualSize: Dp? = null,
    interactionSource: MutableInteractionSource? = null,
    badgeColor: androidx.compose.ui.graphics.Color? = null,
    badgeSize: Dp = UvirActionIconBadgeSize,
    visualScale: Float = 1f,
    content: @Composable () -> Unit
) {
    val pressSource = interactionSource ?: remember { MutableInteractionSource() }
    // Keep pointer, keyboard and accessibility activation consistent when disabled.
    val activateAction: () -> Unit = { if (enabled) onClick() }
    val visualSize = pressedVisualSize ?: badgeColor?.let { badgeSize }
    val visualContent: @Composable () -> Unit = {
        if (badgeColor == null) content()
        else UvirActionIconBadge(badgeColor, enabled, badgeSize, content)
    }
    val pressedIndication =
        ripple(
            bounded = true,
            color = badgeColor?.let(::uvirIconBadgeContentColor) ?: pressedColor
        )

    val clickableModifier =
        if (visualSize == null) {
            modifier
                .uvirHapticOnPress(enabled && hapticOnPress)
                .clip(CircleShape)
                .clickable(
                    enabled = enabled,
                    interactionSource = pressSource,
                    indication = pressedIndication,
                    onClick = activateAction
                )
        } else {
            modifier.uvirHapticOnPress(enabled && hapticOnPress).clickable(
                enabled = enabled,
                interactionSource = pressSource,
                indication = null,
                onClick = activateAction
            )
        }

    Box(
        modifier =
            clickableModifier
                .uvirAccessibleAction(
                    label = contentDescription,
                    enabled = enabled,
                    selectedState = selected,
                    onClick = activateAction
                ),
        contentAlignment = Alignment.Center
    ) {
        if (visualSize == null) {
            Box(Modifier.graphicsLayer { scaleX = visualScale; scaleY = visualScale }, contentAlignment = Alignment.Center) {
                visualContent()
            }
        } else {
            Box(
                modifier =
                    Modifier
                        .size(visualSize)
                        .graphicsLayer { scaleX = visualScale; scaleY = visualScale }
                        .clip(CircleShape)
                        .indication(
                            interactionSource = pressSource,
                            indication = pressedIndication
                        ),
                contentAlignment = Alignment.Center
            ) {
                visualContent()
            }
        }
    }
}

/**
 * Exposes a custom-drawn control as one labelled accessibility node.
 *
 * Pointer input remains owned by the regular clickable/selectable modifier. The semantic click is
 * repeated here so TalkBack, Voice Access and Switch Access invoke the same action without having
 * to focus an unlabelled parent and a separate label child.
 */
internal fun Modifier.uvirAccessibleAction(
    label: String,
    enabled: Boolean = true,
    role: Role = Role.Button,
    selectedState: Boolean? = null,
    checkedState: Boolean? = null,
    stateText: String? = null,
    preserveChildActions: Boolean = false,
    onClick: () -> Unit
): Modifier {
    val clickAction = onClick
    val properties: SemanticsPropertyReceiver.() -> Unit = {
        contentDescription = label
        this.role = role
        selectedState?.let { selected = it }
        checkedState?.let {
            toggleableState =
                if (it) ToggleableState.On else ToggleableState.Off
        }
        stateText?.let { stateDescription = it }
        if (!enabled) disabled()
        this[SemanticsActions.OnClick] =
            AccessibilityAction(label = label) {
                if (enabled) {
                    clickAction()
                    true
                } else {
                    false
                }
            }
    }
    return if (preserveChildActions) semantics(mergeDescendants = false, properties = properties)
        else clearAndSetSemantics(properties)
}

/** Adds state to a parent that intentionally contains another independent accessible action. */
internal fun Modifier.uvirNestedAccessibleAction(
    label: String,
    enabled: Boolean = true,
    stateText: String? = null,
    onClick: () -> Unit
): Modifier {
    val clickAction = onClick
    return semantics(mergeDescendants = false) {
        contentDescription = label
        role = Role.Button
        stateText?.let { stateDescription = it }
        if (!enabled) disabled()
        this[SemanticsActions.OnClick] =
            AccessibilityAction(label = label) {
                if (enabled) {
                    clickAction()
                    true
                } else {
                    false
                }
            }
    }
}
