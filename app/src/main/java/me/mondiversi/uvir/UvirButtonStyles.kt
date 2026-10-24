package me.mondiversi.uvir

import androidx.compose.foundation.BorderStroke
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

internal val UvirDestructiveActionBaseColor = Color(0xFFD32F2F)
internal val UvirDestructiveOutlinedBorderWidth = 1.dp
internal const val UvirDestructiveOutlinedIconStrokeScale = 1.25f
/** Standard action typography everywhere; dialog text actions explicitly opt out. */
internal val LocalUvirSettingsActionButtons = staticCompositionLocalOf { true }
internal val LocalUvirActionGlyphStrokeScale = staticCompositionLocalOf { 1f }
internal val UvirOutlinedActionIconStrokeWidth = 2.dp

@Composable
private fun uvirOutlinedDisabledContainer(): Color =
    if (isSystemInDarkTheme()) Color(0xFF33343C) else Color(0xFFECEDEF)

@Composable
private fun uvirOutlinedDisabledContent(): Color =
    if (isSystemInDarkTheme()) Color(0xFFA1A5AE) else Color(0xFF808790)

@Composable
private fun uvirOutlinedButtonBorder(
    enabled: Boolean,
    color: Color
): BorderStroke {
    val borderColor = if (enabled) {
        color.copy(alpha = if (isSystemInDarkTheme()) 0.48f else 0.38f)
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = if (isSystemInDarkTheme()) 0.20f else 0.12f)
    }
    return BorderStroke(1.dp, borderColor)
}

internal const val UvirFloatingPressedTargetScale = 1.11f

/** Visual feedback only: the control's layout and touch target do not change. */
@Composable
internal fun uvirFloatingPressedScale(
    interactionSource: MutableInteractionSource,
    enabled: Boolean = true
): Float {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (enabled && pressed) UvirFloatingPressedTargetScale else 1f,
        animationSpec = tween(durationMillis = if (pressed) 90 else 140),
        label = "floatingButtonPressedScale"
    )
    return scale
}

/** Text actions and icons share the filled buttons' lighter red at night. */
internal val UvirDestructiveActionColor: Color
    @Composable get() = if (isSystemInDarkTheme())
        uvirDestructiveButtonContainerColor() else UvirDestructiveActionBaseColor

/** Cancel/stop captions share the same semantic red; disabled actions stay muted. */
@Composable
internal fun uvirCancelTextButtonColors(): ButtonColors =
    ButtonDefaults.textButtonColors(contentColor = UvirDestructiveActionColor)

/** A quieter red reserved for the filled surface of destructive buttons.
 * Day text/icons keep their existing red; night text/icons reuse this surface red. */
@Composable
internal fun uvirDestructiveButtonContainerColor(): Color =
    if (isSystemInDarkTheme()) {
        Color(0xFFD94343)
    } else {
        Color(0xFFCA3434)
    }

/** Shared colors for every filled primary action, including a clearly visible
 * disabled state in both app themes. */
@Composable
internal fun uvirDisabledActionContainerColor(): Color =
    if (isSystemInDarkTheme()) {
        Color(0xFF6B6B74)
    } else {
        // Keep the disabled surface visually identical on white cards and on
        // the slightly tinted main background. A translucent Material color
        // was composited differently over the two containers.
        Color(0xFFE2E3E4)
    }

@Composable
internal fun uvirDisabledActionContentColor(): Color =
    if (isSystemInDarkTheme()) {
        Color(0xFFF4F4F6)
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    }

@Composable
internal fun uvirPrimaryButtonColors(): ButtonColors =
    ButtonDefaults.buttonColors(
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        disabledContainerColor = uvirDisabledActionContainerColor(),
        disabledContentColor = uvirDisabledActionContentColor()
    )

/** Destructive filled actions use the same legible disabled treatment as
 * primary buttons and the shared red while enabled. */
@Composable
internal fun uvirDestructiveButtonColors(): ButtonColors =
    ButtonDefaults.buttonColors(
        containerColor = uvirDestructiveButtonContainerColor(),
        contentColor = Color.White,
        disabledContainerColor = uvirDisabledActionContainerColor(),
        disabledContentColor = uvirDisabledActionContentColor()
    )

/** Warning actions in settings stay red without a solid red surface. */
@Composable
internal fun uvirDestructiveOutlinedActionColor(): Color =
    if (isSystemInDarkTheme()) Color(0xFFFF6B6B)
    else UvirDestructiveActionBaseColor

@Composable
internal fun uvirDestructiveOutlinedButtonColors(): ButtonColors =
    ButtonDefaults.outlinedButtonColors(
        containerColor = Color.Transparent,
        contentColor = uvirDestructiveOutlinedActionColor(),
        disabledContainerColor = uvirOutlinedDisabledContainer(),
        disabledContentColor = uvirOutlinedDisabledContent()
    )

@Composable
@Suppress("UNUSED_PARAMETER")
internal fun uvirDestructiveOutlinedButtonBorder(
    enabled: Boolean,
    secondaryText: Color
): BorderStroke? = uvirOutlinedButtonBorder(enabled, uvirDestructiveOutlinedActionColor())

/** Purple actions outside dialogs use the same quiet outlined treatment as
 * the red warning actions. The theme provides the correct day/night purple. */
@Composable
internal fun uvirPrimaryOutlinedButtonColors(): ButtonColors =
    ButtonDefaults.outlinedButtonColors(
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.primary,
        disabledContainerColor = uvirOutlinedDisabledContainer(),
        disabledContentColor = uvirOutlinedDisabledContent()
    )

@Composable
@Suppress("UNUSED_PARAMETER")
internal fun uvirPrimaryOutlinedButtonBorder(
    enabled: Boolean,
    secondaryText: Color
): BorderStroke? = uvirOutlinedButtonBorder(enabled, MaterialTheme.colorScheme.primary)

/** All standard outlined actions use transparent active surfaces and a neutral disabled fill. */
@Composable
internal fun uvirOutlinedActionColors(contentColor: Color): ButtonColors =
    ButtonDefaults.outlinedButtonColors(
        containerColor = Color.Transparent,
        contentColor = contentColor,
        disabledContainerColor = uvirOutlinedDisabledContainer(),
        disabledContentColor = uvirOutlinedDisabledContent()
    )

/** Neutral and accent actions share outline opacity; the neutral hue still matches its label. */
@Composable
internal fun uvirOutlinedActionBorder(
    enabled: Boolean,
    contentColor: Color
): BorderStroke? = uvirOutlinedButtonBorder(enabled, contentColor)
