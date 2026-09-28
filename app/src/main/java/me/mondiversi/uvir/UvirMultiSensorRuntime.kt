package me.mondiversi.uvir

import android.content.Context
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

internal data class UvirConnectionResources(val active: Boolean = false, val wifiRequired: Boolean = false)

/** Retained, UI-independent owner: one authenticated wireless worker per associated UID. */
class UvirMultiSensorRuntime(
    context: Context,
    private val usb: UvirUsbSensorManager
) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    internal val database = UvirDatabaseHelper(appContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val wireless = ConcurrentHashMap<String, UvirWirelessSensorManager>()
    private val disconnectedAt = mutableMapOf<String, Long>()
    private val announced = mutableMapOf<String, Boolean>()
    private val lastActivity = mutableMapOf<String, Boolean>()
    private val lastAlertMonitoring = mutableMapOf<String, Boolean>()
    private val settingsAttempts = mutableMapOf<String, String>()
    private val reconciledSnapshots = mutableMapOf<String, Pair<UvirSensorSettingsSnapshot, Long>>()
    private val mutableIndicators = MutableStateFlow<Map<String, UvirStatusIndicator>>(emptyMap())
    internal val indicators = mutableIndicators.asStateFlow()
    private val mutableConnectionStates = MutableStateFlow<List<UvirConnectionSensorState>>(emptyList())
    internal val connectionStates = mutableConnectionStates.asStateFlow()
    private val mutableAlertMonitoringDeviceIds = MutableStateFlow<Set<String>>(emptySet())
    internal val alertMonitoringDeviceIds = mutableAlertMonitoringDeviceIds.asStateFlow()
    private val mutableResources = MutableStateFlow(UvirConnectionResources())
    internal val resources = mutableResources.asStateFlow()
    private val mutableChanges = MutableSharedFlow<Pair<String, Boolean>>(extraBufferCapacity = 64)
    internal val connectionChanges = mutableChanges.asSharedFlow()
    private var receiver: Job? = null
    private var refresh: Job? = null

    internal fun wirelessFor(deviceId: String): UvirWirelessSensorManager =
        wireless.computeIfAbsent(normalizeSensorDeviceId(deviceId)) {
            UvirWirelessSensorManager(appContext, deviceId.trim())
        }

    internal fun selectedWireless() = wirelessFor(UvirSensorCredentialStore.load(appContext).deviceId)

    internal fun start() {
        if (receiver != null) return
        database.writableDatabase
        val persistence = UvirMultiSensorPersistence(appContext, database, ::send, ::completeSync)
        receiver = scope.launch {
            UvirSensorEventHub.frames.collect { frame ->
                try { persistence.handle(frame) }
                catch (error: Exception) {
                    if (error is CancellationException) throw error
                    // No ACK on failure: the durable sensor queue remains the recovery source.
                    UvirErrorLog.record(appContext, "multisensor:${frame.deviceId}", error)
                }
            }
        }
        refresh = scope.launch {
            while (isActive) {
                runCatching { refreshConnections() }.onFailure {
                    UvirErrorLog.record(appContext, "multisensor_connections", it)
                }
                delay(500L)
            }
        }
    }

    /** The UI never stops the physical USB transport just because another UID is selected. */
    internal fun configureSelected(mode: SensorConnectionMode, enabled: Boolean, intervalMs: Long) {
        val selected = UvirSensorCredentialStore.load(appContext).deviceId
        val usbId = usb.state.value.deviceId.orEmpty()
        if (usbId.isBlank() || usbId.equals(selected, ignoreCase = true)) {
            usb.setStreaming(enabled && mode == SensorConnectionMode.USB, intervalMs)
        }
        val worker = wirelessFor(selected)
        if (!enabled) worker.setStreaming(mode, false, intervalMs)
        else if (mode != SensorConnectionMode.USB) worker.setStreaming(mode, true, intervalMs)
        else if (usbId.equals(selected, ignoreCase = true) && usb.state.value.attached) {
            worker.setStreaming(SensorConnectionMode.USB, false, intervalMs)
        }
    }

    private suspend fun refreshConnections() {
        val associated = UvirSensorCredentialStore.associatedDeviceIds(appContext)
        val paused = UvirSensorCredentialStore.isSensorSelectionDisabled(appContext)
        val selected = UvirSensorCredentialStore.load(appContext).deviceId
        wireless.keys.filter { it.isNotBlank() && it !in associated }.forEach { id ->
            wireless.remove(id)?.stop()
            announced.remove(id)
            disconnectedAt.remove(id)
            settingsAttempts.remove(id)
            reconciledSnapshots.remove(id)
            lastActivity.remove(id)
            lastAlertMonitoring.remove(id)
        }
        val states = linkedMapOf<String, UvirStatusIndicator>()
        val notificationStates = mutableListOf<UvirConnectionSensorState>()
        val alertIds = linkedSetOf<String>()
        var wifiRequired = false
        var realTransportActive = false
        val usbState = usb.state.value
        val usbUid = normalizeSensorDeviceId(usbState.deviceId.orEmpty())
        for (id in associated) {
            val transportEnabled = uvirSensorRealTransportEnabled(preferences, id, paused)
            val simulated = UvirSensorSimulationSettingsStore.load(preferences, id).enabled
            realTransportActive = realTransportActive || transportEnabled
            val worker = wirelessFor(id)
            val currentMode = SensorConnectionMode.fromStoredValue(preferences.getString(KEY_SENSOR_CONNECTION_MODE, null))
            val preferred = loadSensorContextConnectionMode(preferences, id, selected, currentMode)
            val usbOwnsDevice = id == usbUid && usbState.attached &&
                preferred == SensorConnectionMode.USB
            val key = if (id.equals(selected, true)) KEY_LAST_WIRELESS_SENSOR_CONNECTION_MODE
                else sensorContextPreferenceKey(id, KEY_LAST_WIRELESS_SENSOR_CONNECTION_MODE)
            val fallback = SensorConnectionMode.fromStoredValue(preferences.getString(key, null))
                .takeUnless { it == SensorConnectionMode.USB } ?: SensorConnectionMode.WIFI
            val mode = if (preferred == SensorConnectionMode.USB && !usbOwnsDevice) fallback else preferred
            if (transportEnabled && !usbOwnsDevice &&
                (mode == SensorConnectionMode.WIFI || mode == SensorConnectionMode.INTERNET)) wifiRequired = true
            val interval = if (id.equals(selected, ignoreCase = true))
                preferences.getLong(KEY_SAMPLE_SPACING_MS, 1_000L) else 1_000L
            worker.setStreaming(mode, transportEnabled && !usbOwnsDevice, interval, requestSamples = true)
            if (id == usbUid && usbState.attached) usb.setStreaming(transportEnabled && usbOwnsDevice, interval)
            val wirelessState = worker.state.value
            val connected = transportEnabled && if (usbOwnsDevice) usbState.appConnectionConfirmed &&
                usbState.status == UsbSensorConnectionStatus.CONNECTED
                else wirelessState.appConnectionConfirmed && wirelessState.status == WirelessSensorConnectionStatus.CONNECTED
            val connecting = transportEnabled && if (usbOwnsDevice) !connected else
                wirelessState.status == WirelessSensorConnectionStatus.CONNECTING ||
                    (wirelessState.status == WirelessSensorConnectionStatus.CONNECTED && !connected)
            val info = if (usbOwnsDevice) usbState.runtimeInfo else wirelessState.runtimeInfo
            val previousAlerts = lastAlertMonitoring.getOrPut(id) {
                database.readSensorSettings(id)?.alertMonitoringEnabled == true
            }
            val alertsActive = if (simulated) {
                val enabledKey = if (id.equals(selected, true)) KEY_THRESHOLD_ALERT_ENABLED
                    else sensorContextPreferenceKey(id, KEY_THRESHOLD_ALERT_ENABLED)
                preferences.getBoolean(enabledKey, false)
            } else uvirSensorAlertMonitoringActive(id, info, previousAlerts)
            lastAlertMonitoring[id] = alertsActive
            if (alertsActive) alertIds.add(id)
            if (info.deviceId.equals(id, true) &&
                (info.offlineRecording != null || info.alertMonitoringEnabled != null)) {
                lastActivity[id] = info.offlineRecording == true || info.alertMonitoringEnabled == true
            }
            val initializing = transportEnabled && !connected && if (usbOwnsDevice) {
                usbState.status == UsbSensorConnectionStatus.CONNECTED
            } else wirelessState.status == WirelessSensorConnectionStatus.CONNECTED
            val connectionState = uvirConnectionSensorState(id, !paused && simulated, connected,
                initializing, connecting && !initializing,
                lastActivity[id] == true || (connected && info.operationActive == true))
            // The menu and notification must distinguish search (red) from handshake (yellow).
            states[id] = connectionState.indicator
            notificationStates += connectionState
            announceConnection(id, connected)
            if (connected && info.deviceId.equals(id, ignoreCase = true)) {
                UvirSensorRuntimeInfoStore.saveForDevice(appContext,
                    if (usbOwnsDevice) SensorConnectionMode.USB else mode, info)
                if (!id.equals(selected, ignoreCase = true)) {
                    info.toSensorSettingsSnapshotOrNull()?.let { snapshot ->
                        val fingerprint = snapshot to database.sensorProfileRevision.value
                        if (reconciledSnapshots[id] == fingerprint &&
                            UvirSensorSettingsSyncStore.pending(appContext, id) == null) return@let
                        val pending = reconcileBackgroundSensorSnapshot(appContext, database, id, snapshot)
                        if (pending != null) {
                            reconciledSnapshots.remove(id)
                            val attempt = "${pending.updatedAt}:${snapshot.updatedAtMs}"
                            if (settingsAttempts[id] != attempt) {
                                settingsAttempts[id] = attempt
                                if (usbOwnsDevice) usb.replayPendingSettings(id, pending)
                                else worker.replayPendingSettings(id, pending)
                            }
                        } else {
                            settingsAttempts.remove(id)
                            reconciledSnapshots[id] = snapshot to database.sensorProfileRevision.value
                        }
                    }
                }
            } else {
                settingsAttempts.remove(id)
                reconciledSnapshots.remove(id)
            }
        }
        mutableIndicators.value = states
        mutableConnectionStates.value = notificationStates
        mutableAlertMonitoringDeviceIds.value = alertIds
        mutableResources.value = UvirConnectionResources(realTransportActive, wifiRequired)
    }

    private suspend fun announceConnection(id: String, connected: Boolean) {
        val previous = announced[id]
        if (previous == null) {
            announced[id] = connected
            if (connected) database.ensureSensorProfile(id)
            return
        }
        if (connected == previous) { disconnectedAt.remove(id); return }
        if (!connected) {
            val now = android.os.SystemClock.elapsedRealtime()
            val since = disconnectedAt.getOrPut(id) { now }
            if (now - since < 1_200L) return
        }
        database.ensureSensorProfile(id)
        announced[id] = connected
        disconnectedAt.remove(id)
        mutableChanges.emit(id to connected)
    }

    private fun send(id: String, source: SensorSyncSource, commands: List<String>): Boolean =
        when (source) {
            SensorSyncSource.USB -> usb.state.value.deviceId.orEmpty().equals(id, true) &&
                usb.sendSensorControlCommands(commands)
            SensorSyncSource.WIRELESS -> wireless[normalizeSensorDeviceId(id)]?.sendSensorControlCommands(commands) == true
        }

    private fun completeSync(id: String, source: SensorSyncSource) {
        if (source == SensorSyncSource.USB) {
            if (usb.state.value.deviceId.orEmpty().equals(id, true)) usb.markOfflineSyncComplete()
        } else wireless[normalizeSensorDeviceId(id)]?.markOfflineSyncComplete()
    }

    internal fun retryAll() { wireless.values.forEach { it.retryIfNeeded() } }
    internal fun restoreSensorDefaults(deviceId: String): Boolean =
        restoreConnectedUvirSensor(deviceId, usb, wireless[normalizeSensorDeviceId(deviceId)])

    internal fun disconnectAll() {
        usb.disconnectForSensorSelection()
        wireless.values.forEach { it.disconnectForSensorSelection() }
    }

    internal fun stop() {
        refresh?.cancel()
        receiver?.cancel()
        scope.cancel()
        wireless.values.forEach { it.stop() }
        wireless.clear()
        // Database is closed only after its retained receiver has left its transaction.
        runBlocking { receiver?.join(); refresh?.join() }
        database.close()
    }
}

/** Finish background alert sessions only after an authoritative, authenticated readback. */
internal fun reconcileBackgroundSensorSnapshot(
    context: Context,
    database: UvirDatabaseHelper,
    deviceId: String,
    snapshot: UvirSensorSettingsSnapshot
): UvirPendingSensorSettings? {
    val previous = database.readSensorSettings(deviceId)
    val pending = UvirSensorSettingsSyncStore.reconcile(context, deviceId, snapshot, database)
    if (pending != null) return pending
    if (snapshot.alertMonitoringEnabled && snapshot.alertSessionId > 0L &&
        (previous?.alertMonitoringEnabled != true || previous.alertSessionId != snapshot.alertSessionId)) {
        database.ensureAlertSession(snapshot.alertSessionId,
            database.readAlertSessionNote(snapshot.alertSessionId), System.currentTimeMillis(), deviceId)
    }
    val previousId = previous?.alertSessionId ?: 0L
    if (previousId > 0L && previous?.alertMonitoringEnabled == true &&
        (!snapshot.alertMonitoringEnabled || snapshot.alertSessionId != previousId)) {
        database.finishAlertSession(previousId)
    }
    return null
}
