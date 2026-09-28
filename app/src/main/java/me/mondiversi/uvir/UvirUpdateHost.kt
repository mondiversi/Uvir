package me.mondiversi.uvir

import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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
            UvirUpdates.downloadedInstaller()?.let { apk -> installer.launch(UvirUpdates.installIntent(context, apk)) }
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
    if (state.dialog) {
        val catalog = state.catalog ?: return
        val appFirst = catalog.app.code > BuildConfig.VERSION_CODE
        UvirAlertDialog(
            onDismissRequest = { UvirUpdates.dismiss() },
            confirmButton = null,
            properties = DialogProperties(dismissOnBackPress = !state.busy, dismissOnClickOutside = !state.busy),
            title = { Text(stringResource(R.string.update_available)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (appFirst) {
                        Text("Uvir", fontWeight = FontWeight.SemiBold)
                        Text("${BuildConfig.VERSION_NAME} → ${catalog.app.version}")
                        OutlinedButton(enabled = !state.busy, modifier = Modifier.fillMaxWidth(),
                            colors = uvirPrimaryOutlinedButtonColors(),
                            border = uvirPrimaryOutlinedButtonBorder(!state.busy, MaterialTheme.colorScheme.onSurfaceVariant), onClick = {
                            UvirUpdates.appUpdate(context) { apk ->
                                if (context.packageManager.canRequestPackageInstalls()) installer.launch(UvirUpdates.installIntent(context, apk))
                                else permission.launch(UvirUpdates.permissionIntent(context))
                            }
                        }) { Text(stringResource(R.string.update_app)) }
                    }
                    state.sensors.forEach { sensor ->
                        HorizontalDivider()
                        Text(sensor.name, fontWeight = FontWeight.SemiBold)
                        Text("${sensor.current} → ${catalog.sensor.version}")
                        val available = usb.runtimeInfo.deviceId.equals(sensor.uid, true) &&
                            usb.runtimeInfo.operationActive == false && usb.appConnectionConfirmed
                        Text(stringResource(if (appFirst) R.string.update_app_first else R.string.update_usb_required), style = MaterialTheme.typography.bodySmall)
                        OutlinedButton(enabled = !state.busy && !appFirst && available, modifier = Modifier.fillMaxWidth(),
                            colors = uvirPrimaryOutlinedButtonColors(),
                            border = uvirPrimaryOutlinedButtonBorder(!state.busy && !appFirst && available, MaterialTheme.colorScheme.onSurfaceVariant), onClick = {
                            UvirUpdates.firmware(context.applicationContext, manager, sensor)
                        }) { Text(stringResource(R.string.update_sensor)) }
                    }
                    if (state.busy) {
                        HorizontalDivider()
                        Text(stringResource(state.stage))
                        val progress = state.progress
                        if (progress == null) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        else LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                        Text(stringResource(R.string.update_keep_connected), style = MaterialTheme.typography.bodySmall)
                    }
                    state.message?.let { Text(stringResource(it), color = if (it == R.string.update_failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface) }
                }
            }
        )
    }
}
