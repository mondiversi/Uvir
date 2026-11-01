package me.mondiversi.uvir

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

@Composable
internal fun UvirErrorLogClearDialog(card: Color, text: Color, secondary: Color, busy: Boolean,
    onDismiss: () -> Unit, onClear: () -> Unit) {
    val action = uvirActionLabel(R.string.delete_all)
    val direction = LocalLayoutDirection.current
    UvirAlertDialog(onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(stringResource(R.string.error_log_clear_title)) },
        text = {
            UvirHoldConfirmationMessage(stringResource(R.string.error_log_clear_description),
                action, holdDurationSeconds = 5, replaceEmbeddedInstruction = false)
        }, confirmButton = {
            CompositionLocalProvider(LocalLayoutDirection provides direction) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically) {
                HoldToConfirmActionButton(action, onClear, enabled = !busy, holdDurationMillis = 5_000L,
                    modifier = Modifier.testTag("error_log_clear_confirm"))
                TextButton(onClick = onDismiss, enabled = !busy,
                    modifier = Modifier.testTag("error_log_clear_cancel")) {
                    Text(uvirActionLabel(R.string.cancel))
                }
            }
            }
        }, containerColor = card, titleContentColor = text, textContentColor = secondary)
}
