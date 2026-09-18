package me.mondiversi.uvir

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun UvirCounterResetConfirmationDialog(
    cardColor: Color,
    primaryText: Color,
    secondaryText: Color,
    onResetAllCounters: () -> Unit,
    onDismissRequest: () -> Unit
) {
    val context = LocalContext.current
    val confirmationTitle =
        stringResource(R.string.reset_all_records_confirmation_title)
    val confirmationMessage =
        stringResource(R.string.reset_all_records_confirmation_message)
    val confirmationAction =
        stringResource(R.string.reset_confirmation_action)
    val resetCompleteMessage =
        stringResource(R.string.all_counters_reset_complete)

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Text(confirmationTitle)
        },
        text = {
            Text(confirmationMessage)
        },
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                HoldToConfirmDeleteButton(
                    label = confirmationAction,
                    holdDurationMillis = 5_000L,
                    onConfirmed = {
                        onResetAllCounters()
                        onDismissRequest()
                        showUvirBottomMessage(
                            context,
                            resetCompleteMessage,
                            longDuration = false
                        )
                    }
                )

                TextButton(onClick = onDismissRequest) {
                    Text(stringResource(R.string.cancel))
                }
            }
        },
        containerColor = cardColor,
        titleContentColor = primaryText,
        textContentColor = secondaryText
    )
}

@Composable
internal fun UvirDataAndRestoreContent(
    autoEnabled: Boolean,
    secondaryText: Color,
    onResetRequested: () -> Unit,
    onRestoreDefaultsRequested: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement =
            Arrangement.spacedBy(UvirSettingsControlGap)
    ) {
        Column(
            verticalArrangement =
                Arrangement.spacedBy(UvirSettingsRelatedGap)
        ) {
            Button(
                onClick = onResetRequested,
                enabled = !autoEnabled,
                modifier = Modifier.fillMaxWidth(),
                colors = uvirDestructiveButtonColors()
            ) {
                UvirLabeledButtonContent(
                    text = stringResource(R.string.reset_all_records_action)
                ) {
                    UvirMenuIcon(
                        type = MenuIconType.DELETE,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            if (autoEnabled) {
                Text(
                    text = stringResource(R.string.reset_counters_stop_auto),
                    color = secondaryText,
                    fontSize = 11.sp
                )
            }
        }

        Button(
            onClick = onRestoreDefaultsRequested,
            modifier = Modifier.fillMaxWidth(),
            colors = uvirDestructiveButtonColors()
        ) {
            UvirLabeledButtonContent(
                text = stringResource(R.string.restore_app_settings_action)
            ) {
                UvirRestoreDefaultsIcon(
                    modifier = Modifier.size(20.dp),
                    tint = androidx.compose.material3.LocalContentColor.current
                )
            }
        }
    }
}
