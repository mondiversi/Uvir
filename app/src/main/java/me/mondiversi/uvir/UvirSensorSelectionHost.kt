package me.mondiversi.uvir

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Rebuild only the sensor UI context; retained transports and chip jobs survive. */
@Composable
fun UvirApp(
    remoteNetworkEnabled: Boolean,
    usbSensorManager: UvirUsbSensorManager,
    wirelessSensorManager: UvirWirelessSensorManager,
    onRequestBluetoothPermission: () -> Unit,
    currentWifiSsid: String?,
    onRequestCurrentWifiSsid: () -> Unit,
    openHomeRequestId: Long = 0L
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var epoch by rememberSaveable { mutableIntStateOf(0) }
    var switching by remember { mutableStateOf(false) }
    var contentSuspended by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
    if (!contentSuspended) key(epoch) {
        UvirAppContent(
            remoteNetworkEnabled, usbSensorManager, wirelessSensorManager,
            onRequestBluetoothPermission, currentWifiSsid, onRequestCurrentWifiSsid,
            openHomeRequestId,
            onSensorSelected = { request ->
                val newAssociation = request == ASSOCIATE_NEW_SENSOR_REQUEST
                val deviceId = if (newAssociation) "" else request
                val oldId = UvirSensorCredentialStore.load(context).deviceId
                if (!switching && (deviceId.isBlank() ||
                    (!oldId.equals(deviceId, ignoreCase = true) &&
                    normalizeSensorDeviceId(deviceId) in UvirSensorCredentialStore.associatedDeviceIds(context)))
                ) scope.launch {
                    switching = true
                    val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    var activeContextChanged = false
                    val result = runCatching {
                        // Keep the old receiver alive until both old transports
                        // have exited, so final arriving records are still handled.
                        withContext(Dispatchers.IO) {
                            usbSensorManager.disconnectForSensorSelection()
                            wirelessSensorManager.disconnectForSensorSelection()
                        }
                        withFrameNanos { }
                        contentSuspended = true
                        // Dispose old save effects before replacing active values.
                        // Neither the Activity nor sensor sessions are restarted.
                        withFrameNanos { }
                        withFrameNanos { }
                        withContext(Dispatchers.IO) {
                            check(saveSelectedSensorContext(preferences, oldId))
                            activeContextChanged = true
                            if (newAssociation) check(UvirSensorCredentialStore.releaseActiveSensor(context))
                            if (deviceId.isNotBlank()) check(UvirSensorCredentialStore.activateAssociatedSensor(context, deviceId))
                            check(restoreSelectedSensorContext(preferences, deviceId))
                            check(UvirSensorRuntimeInfoStore.activate(context, deviceId))
                            cancelAutomaticAcquisitionNotification(context)
                            cancelThresholdAlertNotification(context)
                            cancelOfflineDisconnectionNotification(context)
                        }
                    }.onFailure { error ->
                        withContext(Dispatchers.IO) {
                            if (activeContextChanged && oldId.isNotBlank()) {
                                UvirSensorCredentialStore.activateAssociatedSensor(context, oldId)
                                restoreSelectedSensorContext(preferences, oldId)
                                UvirSensorRuntimeInfoStore.activate(context, oldId)
                            }
                            UvirErrorLog.record(context, "sensor_selection", error)
                        }
                    }
                    withContext(Dispatchers.IO) {
                        if (deviceId.isNotBlank() || result.isFailure) usbSensorManager.refreshSelectedSensor()
                    }
                    epoch++
                    contentSuspended = false
                    switching = false
                    if (result.isFailure) showUvirBottomMessage(context, context.getString(R.string.sensor_selection_failed))
                    else if (newAssociation) showUvirBottomMessage(context, context.getString(R.string.sensor_associate_usb_hint))
                }
            }
        )
    }
    if (switching) {
        Box(
            Modifier.fillMaxSize()
                .background(MaterialTheme.colorScheme.background.copy(alpha = 0.95f))
                .clickable(indication = null, interactionSource = remember {
                    androidx.compose.foundation.interaction.MutableInteractionSource()
                }) {},
            contentAlignment = Alignment.Center
        ) { CircularProgressIndicator() }
    }
    }
}
