package me.mondiversi.uvir

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.drawToBitmap
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
    multiSensorRuntime: UvirMultiSensorRuntime? = null,
    openHomeRequestId: Long = 0L
) {
    val context = LocalContext.current
    val view = LocalView.current
    val resources = androidx.compose.ui.platform.LocalResources.current
    val scope = rememberCoroutineScope()
    var epoch by rememberSaveable { mutableIntStateOf(0) }
    var switching by remember { mutableStateOf(false) }
    var contentSuspended by remember { mutableStateOf(false) }
    var frozenFrame by remember { mutableStateOf<ImageBitmap?>(null) }
    val transitionBackground =
        if (isSystemInDarkTheme()) Color(0xFF101418) else Color(0xFFF4F7F9)
    // Observe USB identity changes so initial physical pairing acquires its own worker.
    val usbState by usbSensorManager.state.collectAsState()
    val selectedManager = remember(usbState.deviceId, epoch, multiSensorRuntime) {
        multiSensorRuntime?.wirelessFor(UvirSensorCredentialStore.load(context).deviceId)
            ?: wirelessSensorManager
    }
    LaunchedEffect(multiSensorRuntime) {
        multiSensorRuntime?.connectionChanges?.collect { (id, connected) ->
            val profiles = withContext(Dispatchers.IO) { multiSensorRuntime.database.readSensorProfiles() }
            val name = uvirConnectionAnnouncementSensorName(id, profiles)
            if (name.isNotBlank()) showUvirBottomMessage(context,
                resources.getString(if (connected) R.string.sensor_connection_toast_connected
                    else R.string.sensor_connection_toast_disconnected,
                    androidx.core.text.BidiFormatter.getInstance(resources.configuration.locales[0]).unicodeWrap(name)))
        }
    }
    Box(Modifier.fillMaxSize()) {
    if (!contentSuspended) key(epoch) {
        UvirAppContent(
            remoteNetworkEnabled, usbSensorManager, selectedManager,
            onRequestBluetoothPermission, currentWifiSsid, onRequestCurrentWifiSsid,
            openHomeRequestId,
            multiSensorRuntime = multiSensorRuntime,
            onSensorSelected = { request ->
                val newAssociation = request == ASSOCIATE_NEW_SENSOR_REQUEST
                val noSensorSelected = request == NO_SENSOR_SELECTED_REQUEST
                val deviceId = if (newAssociation || noSensorSelected) "" else request
                val oldId = UvirSensorCredentialStore.load(context).deviceId
                if (!switching && (newAssociation || noSensorSelected ||
                    (!oldId.equals(deviceId, ignoreCase = true) &&
                    normalizeSensorDeviceId(deviceId) in UvirSensorCredentialStore.associatedDeviceIds(context)))
                ) scope.launch {
                    // Keep the last frame visible while the old Compose tree is
                    // disposed and its sensor-specific state is replaced.
                    frozenFrame = runCatching { view.drawToBitmap().asImageBitmap() }.getOrNull()
                    switching = true
                    val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    var activeContextChanged = false
                    val result = runCatching {
                        // Keep the old receiver alive until both old transports
                        // have exited, so final arriving records are still handled.
                        withContext(Dispatchers.IO) {
                            if (multiSensorRuntime == null) {
                                usbSensorManager.disconnectForSensorSelection()
                                wirelessSensorManager.disconnectForSensorSelection()
                            } else if (noSensorSelected) multiSensorRuntime.disconnectAll()
                            else if (newAssociation) usbSensorManager.disconnectForSensorSelection()
                        }
                        withFrameNanos { }
                        contentSuspended = true
                        // Dispose old save effects before replacing active values.
                        // Neither the Activity nor sensor sessions are restarted.
                        withFrameNanos { }
                        withFrameNanos { }
                        withContext(Dispatchers.IO) {
                            check(saveSelectedSensorContext(preferences, oldId, preserveSensorReadback = multiSensorRuntime != null))
                            activeContextChanged = true
                            if (newAssociation) check(UvirSensorCredentialStore.releaseActiveSensor(context))
                            if (noSensorSelected) check(UvirSensorCredentialStore.deactivateSensorSelection(context))
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
                        if ((deviceId.isNotBlank() || newAssociation || result.isFailure) &&
                            !usbSensorManager.state.value.attached) usbSensorManager.refreshSelectedSensor()
                    }
                    epoch++
                    contentSuspended = false
                    withFrameNanos { }
                    withFrameNanos { }
                    switching = false
                    frozenFrame = null
                    if (result.isFailure) showUvirBottomMessage(context, resources.getString(R.string.sensor_selection_failed))
                    else if (newAssociation) showUvirBottomMessage(context, resources.getString(R.string.sensor_associate_usb_hint))
                }
            }
        )
    }
    if (switching) {
        Box(
            Modifier.fillMaxSize()
                .background(
                    if (frozenFrame == null && contentSuspended) transitionBackground
                    else Color.Transparent
                )
                .clickable(indication = null, interactionSource = remember {
                    androidx.compose.foundation.interaction.MutableInteractionSource()
                }) {},
            contentAlignment = Alignment.Center
        ) {
            frozenFrame?.let { frame ->
                Image(
                    bitmap = frame,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.FillBounds
                )
            }
            CircularProgressIndicator()
        }
    }
    }
}
