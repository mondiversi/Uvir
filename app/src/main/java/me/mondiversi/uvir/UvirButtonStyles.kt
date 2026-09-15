package me.mondiversi.uvir

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

internal val UvirDestructiveActionBaseColor = Color(0xFFD32F2F)

/** Text actions and icons share the filled buttons' lighter red at night. */
internal val UvirDestructiveActionColor: Color
    @Composable get() = if (isSystemInDarkTheme())
        uvirDestructiveButtonContainerColor() else UvirDestructiveActionBaseColor

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

/** Shared treatment for full-width outlined actions. Disabled actions use an
 * opaque surface, so their legibility does not depend on the parent card. */
@Composable
internal fun uvirOutlinedActionColors(contentColor: Color): ButtonColors =
    ButtonDefaults.outlinedButtonColors(
        containerColor = Color.Transparent,
        contentColor = contentColor,
        disabledContainerColor = uvirDisabledActionContainerColor(),
        disabledContentColor = uvirDisabledActionContentColor()
    )

internal fun uvirOutlinedActionBorder(
    enabled: Boolean,
    secondaryText: Color
): BorderStroke =
    BorderStroke(
        width = 1.dp,
        color = secondaryText.copy(alpha = if (enabled) 0.72f else 0.30f)
    )
