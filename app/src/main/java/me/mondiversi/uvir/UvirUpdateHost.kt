package me.mondiversi.uvir

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties

@Composable
internal fun UvirUpdateHost(manager: UvirUsbSensorManager) {
    val context = LocalContext.current
    val state by UvirUpdates.state.collectAsState()
    val usb by manager.state.collectAsState()
    val installer = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        UvirUpdates.installerReturned(context, it.resultCode)
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (context.packageManager.canRequestPackageInstalls()) {
            UvirUpdates.continueInstall(context) { apk ->
                installer.launch(UvirUpdates.installIntent(context, apk))
            }
        } else {
            UvirErrorLog.record(context, "update_install", "Android installation permission denied")
            android.widget.Toast.makeText(context, R.string.update_permission, android.widget.Toast.LENGTH_LONG).show()
        }
    }
    LaunchedEffect(Unit) { UvirUpdates.start(context) }
    val view = LocalView.current
    DisposableEffect(state.busy) {
        val previous = view.keepScreenOn
        if (state.busy) view.keepScreenOn = true
        onDispose { view.keepScreenOn = previous }
    }
    UvirUpdateDialog(state, usb,
        onDismiss = { UvirUpdates.dismiss() },
        onAppUpdate = {
            UvirUpdates.appUpdate(context) { apk ->
                if (context.packageManager.canRequestPackageInstalls()) installer.launch(UvirUpdates.installIntent(context, apk))
                else permission.launch(UvirUpdates.permissionIntent(context))
            }
        },
        onSensorUpdate = { UvirUpdates.firmware(context.applicationContext, manager, it) }
    )
}

@Composable
internal fun UvirUpdateDialog(
    state: UvirUpdateState,
    usb: UvirUsbSensorState,
    onDismiss: () -> Unit,
    onAppUpdate: () -> Unit,
    onSensorUpdate: (UvirSensorUpdate) -> Unit,
    darkTheme: Boolean = isSystemInDarkTheme()
) {
    if (state.dialog) {
        val catalog = state.catalog ?: return
        val appFirst = catalog.app.code > BuildConfig.VERSION_CODE
        val text = if (darkTheme) Color.White else Color(0xFF101418)
        UvirAlertDialog(
            onDismissRequest = { if (!state.busy) onDismiss() },
            confirmButton = null,
            dismissButton = if (state.busy) null else {
                { UvirDialogCloseButton(onDismiss) }
            },
            containerColor = if (darkTheme) Color(0xFF1C242B) else Color.White,
            titleContentColor = text,
            textContentColor = text,
            properties = DialogProperties(dismissOnBackPress = !state.busy, dismissOnClickOutside = !state.busy),
            title = { Text(stringResource(R.string.update_available)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (appFirst) {
                        Text("Uvir", fontWeight = FontWeight.SemiBold)
                        Text("${BuildConfig.VERSION_NAME} → ${catalog.app.version}")
                        UvirUpdateTextAction(R.string.update_app, !state.busy && !state.checking,
                            "update_app_action", onAppUpdate)
                    }
                    state.sensors.forEach { sensor ->
                        HorizontalDivider()
                        Text(sensor.name, fontWeight = FontWeight.SemiBold)
                        Text("${sensor.current} → ${catalog.sensor.version}")
                        val available = uvirFirmwareUpdateReady(usb, sensor.uid, catalog.sensor)
                        val appRequired = appFirst || BuildConfig.VERSION_CODE < catalog.sensor.minAppCode
                        Text(stringResource(if (appRequired) R.string.update_app_first else R.string.update_usb_required),
                            style = MaterialTheme.typography.bodySmall)
                        UvirUpdateTextAction(R.string.update_sensor,
                            !state.busy && !state.checking && !appRequired && available,
                            "update_sensor_action_${sensor.uid}") { onSensorUpdate(sensor) }
                    }
                    if (state.busy) {
                        HorizontalDivider()
                        Text(stringResource(state.stage))
                        val progress = state.progress
                        if (progress == null) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        else LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                        if (state.operation == UvirUpdateOperation.FIRMWARE) {
                            Text(stringResource(R.string.update_keep_connected), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    state.message?.let {
                        val failure = it == R.string.update_failed || it == R.string.update_app_failed
                        Text(stringResource(it), color = if (failure) UvirDestructiveActionColor else text)
                    }
                }
            }
        )
    }
}

/** Popup actions are text-only; use the same day/night purple as Close. */
@Composable
private fun UvirUpdateTextAction(label: Int, enabled: Boolean, tag: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        TextButton(onClick = onClick, enabled = enabled, modifier = Modifier.testTag(tag),
            colors = ButtonDefaults.textButtonColors(containerColor = Color.Transparent,
                disabledContainerColor = Color.Transparent, contentColor = MaterialTheme.colorScheme.primary)) {
            Text(uvirActionLabel(label))
        }
    }
}
