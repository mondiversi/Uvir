package me.mondiversi.uvir

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource

@Composable
internal fun UvirStopAutomaticDialog(
    cardColor: Color,
    primaryText: Color,
    secondaryText: Color,
    stopEnabled: Boolean,
    onStop: () -> Unit,
    onContinue: () -> Unit,
    onDismissRequest: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Text(
                stringResource(
                    R.string.stop_automatic_confirmation_title
                )
            )
        },
        text = {
            Text(
                stringResource(
                    R.string.stop_automatic_confirmation_message
                )
            )
        },
        dismissButton = {
            TextButton(
                onClick = onStop,
                enabled = stopEnabled,
                colors =
                    ButtonDefaults.textButtonColors(
                        contentColor = UvirDestructiveActionColor
                    )
            ) {
                Text(stringResource(R.string.stop))
            }
        },
        confirmButton = {
            TextButton(onClick = onContinue) {
                Text(stringResource(R.string.continue_acquisition))
            }
        },
        containerColor = cardColor,
        titleContentColor = primaryText,
        textContentColor = secondaryText
    )
}

@Composable
internal fun UvirAutomaticBackgroundWarningDialog(
    cardColor: Color,
    primaryText: Color,
    secondaryText: Color,
    onOpenSettings: () -> Unit,
    onContinue: () -> Unit,
    onDismissRequest: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Text(stringResource(R.string.automatic_background_title))
        },
        text = {
            Text(stringResource(R.string.automatic_background_message))
        },
        confirmButton = {
            TextButton(onClick = onOpenSettings) {
                Text(
                    stringResource(
                        R.string.automatic_background_settings
                    )
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onContinue) {
                Text(
                    stringResource(
                        R.string.automatic_background_continue
                    )
                )
            }
        },
        containerColor = cardColor,
        titleContentColor = primaryText,
        textContentColor = secondaryText
    )
}

@Composable
internal fun UvirInterruptManualSessionDialog(
    cardColor: Color,
    primaryText: Color,
    secondaryText: Color,
    onInterrupt: () -> Unit,
    onContinueManualSession: () -> Unit,
    onDismissRequest: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Text(stringResource(R.string.interrupt_manual_session_title))
        },
        text = {
            Text(stringResource(R.string.interrupt_manual_session_message))
        },
        dismissButton = {
            TextButton(
                onClick = onInterrupt,
                colors =
                    ButtonDefaults.textButtonColors(
                        contentColor = UvirDestructiveActionColor
                    )
            ) {
                Text(stringResource(R.string.interrupt_manual_session_action))
            }
        },
        confirmButton = {
            TextButton(onClick = onContinueManualSession) {
                Text(stringResource(R.string.continue_manual_session_action))
            }
        },
        containerColor = cardColor,
        titleContentColor = primaryText,
        textContentColor = secondaryText
    )
}
