package me.mondiversi.uvir

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

internal fun restoreUvirApplicationDefaults(
    context: Context,
    database: UvirDatabaseHelper
): Boolean {
    val applicationContext = context.applicationContext
    val recordsCleared =
        runCatching {
            database.resetAllCounters()
            database.deleteAllSensorProfiles()
            true
        }.getOrDefault(false)
    val errorLogCleared = UvirErrorLog.clear(applicationContext)
    val preferencesCleared =
        applicationContext
            .getSharedPreferences(
                PREFS_NAME,
                Context.MODE_PRIVATE
            )
            .edit()
            .clear()
            .commit()
    val credentialsCleared =
        UvirSensorCredentialStore.clear(applicationContext)
    val runtimeInfoCleared =
        UvirSensorRuntimeInfoStore.clear(applicationContext)
    val restored =
        recordsCleared &&
            errorLogCleared &&
            preferencesCleared &&
            credentialsCleared &&
            runtimeInfoCleared

    if (restored) {
        cancelAutomaticAcquisitionNotification(applicationContext)
        cancelThresholdAlertNotification(applicationContext)
        cancelOfflineDisconnectionNotification(applicationContext)
    }

    return restored
}

@Composable
internal fun UvirRestoreDefaultsIcon(
    modifier: Modifier = Modifier,
    tint: Color
) {
    Icon(
        painter = painterResource(R.drawable.ic_restore),
        contentDescription = null,
        tint = tint,
        modifier = modifier.size(UvirTitleActionIconSize)
    )
}

@Composable
internal fun UvirRestoreDefaultsConfirmationDialog(
    cardColor: Color,
    primaryText: Color,
    secondaryText: Color,
    onRestoreDefaults: suspend () -> Boolean,
    onRestored: () -> Unit,
    onDismissRequest: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val failureMessage =
        stringResource(R.string.restore_app_settings_failed)
    var inProgress by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = {
            if (!inProgress) onDismissRequest()
        },
        title = {
            Text(stringResource(R.string.restore_app_settings_title))
        },
        text = {
            Text(stringResource(R.string.restore_app_settings_message))
        },
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (inProgress) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.4.dp
                    )
                    Spacer(Modifier.width(16.dp))
                } else {
                    HoldToConfirmDeleteButton(
                        label =
                            stringResource(
                                R.string.restore_app_settings_confirm
                            ),
                        holdDurationMillis = 5_000L,
                        onConfirmed = {
                            inProgress = true
                            coroutineScope.launch {
                                if (onRestoreDefaults()) {
                                    onRestored()
                                } else {
                                    inProgress = false
                                    showUvirBottomMessage(
                                        context,
                                        failureMessage
                                    )
                                }
                            }
                        }
                    )
                    Spacer(Modifier.width(4.dp))
                }

                TextButton(
                    onClick = onDismissRequest,
                    enabled = !inProgress
                ) {
                    Text(stringResource(R.string.cancel))
                }
            }
        },
        containerColor = cardColor,
        titleContentColor = primaryText,
        textContentColor = secondaryText
    )
}
