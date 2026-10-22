package me.mondiversi.uvir

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

/** A normal Settings action opens the SAME hold-to-confirm dialog used elsewhere. */
@Composable
internal fun UvirCalibrationResetButton(
    enabled: Boolean, message: String, cardColor: Color, primaryText: Color, secondaryText: Color,
    modifier: Modifier = Modifier, onConfirmed: () -> Unit
) {
    var confirmationOpen by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    val label = uvirActionLabel(R.string.calibration_reset_values)
    OutlinedButton(
        onClick = { focus.clearFocus(); confirmationOpen = true },
        enabled = enabled, modifier = modifier.fillMaxWidth(),
        colors = uvirDestructiveOutlinedButtonColors(),
        border = uvirDestructiveOutlinedButtonBorder(enabled, secondaryText)
    ) {
        UvirLabeledButtonContent(label) {
            UvirRestoreDefaultsIcon(Modifier.size(20.dp), LocalContentColor.current)
        }
    }
    if (confirmationOpen) {
        val action = stringResource(R.string.calibration_reset_confirm_action)
        UvirAlertDialog(
            modifier = Modifier.testTag("calibration_reset_dialog"),
            onDismissRequest = { confirmationOpen = false },
            title = { Text(stringResource(R.string.calibration_reset_confirmation_title)) },
            text = {
                UvirHoldConfirmationMessage(message, action, holdDurationSeconds = 2,
                    replaceEmbeddedInstruction = false)
            },
            dismissButton = {
                HoldToConfirmActionButton(label = action, enabled = enabled,
                    modifier = Modifier.testTag("calibration_reset_confirm"),
                    onConfirmed = { onConfirmed(); confirmationOpen = false })
            },
            confirmButton = {
                TextButton(onClick = { confirmationOpen = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier.testTag("calibration_reset_cancel")) {
                    Text(stringResource(R.string.cancel))
                }
            },
            containerColor = cardColor, titleContentColor = primaryText, textContentColor = secondaryText
        )
    }
}
