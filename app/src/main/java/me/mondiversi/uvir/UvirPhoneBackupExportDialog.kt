package me.mondiversi.uvir

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException

/** One phone-owned backup: no source selector and no password field. */
@Composable
internal fun UvirPhoneBackupExportDialog(
    titleRes: Int,
    descriptionRes: Int,
    format: UvirExportFileFormat,
    cardColor: Color,
    primaryText: Color,
    secondaryText: Color,
    onDismiss: () -> Unit,
    onExport: suspend (UvirExportDestination) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    val export: (UvirExportDestination) -> Unit = { destination ->
        if (!busy) {
            busy = true
            scope.launch {
                try {
                    onExport(destination)
                } catch (error: Exception) {
                    if (error is CancellationException) throw error
                    UvirErrorLog.record(context, "export_phone_backup", error)
                    showUvirBottomMessage(context, context.getString(R.string.export_save_failed))
                } finally {
                    busy = false
                }
            }
        }
    }
    UvirAlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = {
            UvirClosableDialogTitle(stringResource(titleRes), onDismiss = { if (!busy) onDismiss() })
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(UvirSettingsControlGap)) {
                Text(stringResource(descriptionRes), color = secondaryText)
                UvirExportFileCountText(format, 1, secondaryText)
            }
        },
        confirmButton = {
            TextButton(enabled = !busy, onClick = { export(UvirExportDestination.SAVE) }) {
                Text(uvirActionLabel(R.string.save))
            }
            TextButton(enabled = !busy, onClick = { export(UvirExportDestination.SHARE) }) {
                Text(uvirActionLabel(R.string.share))
            }
        },
        dismissButton = {
            UvirDialogCancelButton(onDismiss, enabled = !busy,
                modifier = Modifier.testTag("phone_backup_export_cancel"))
        },
        containerColor = cardColor,
        titleContentColor = primaryText,
        textContentColor = secondaryText
    )
}
