package me.mondiversi.uvir

import android.content.Context
import org.json.JSONObject

internal data class UvirStoredSensorRuntimeInfo(
    val capturedAtMs: Long,
    val connectionMode: SensorConnectionMode,
    val info: UvirSensorRuntimeInfo
)

internal object UvirSensorRuntimeInfoStore {
    private const val PREFERENCES_NAME = "uvir_sensor_runtime_info"
    private const val SNAPSHOT_KEY = "last_sensor_snapshot"
    private const val MINIMUM_WRITE_INTERVAL_MS = 10_000L

    private val profileWriteTimes = mutableMapOf<Pair<Int, String>, Long>()
    private var lastPreferenceIdentity = 0
    private var lastSavedAtMs = 0L
    private var lastFingerprint = ""
    private var lastConnectionMode: SensorConnectionMode? = null

    @Synchronized
    fun save(
        context: Context,
        connectionMode: SensorConnectionMode,
        info: UvirSensorRuntimeInfo,
        capturedAtMs: Long = System.currentTimeMillis()
    ) {
        if (!info.hasDiagnosticIdentity()) {
            return
        }

        val preferenceIdentity = System.identityHashCode(context.applicationContext
            .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE))
        val fingerprint = info.stableDiagnosticFingerprint()
        if (
            preferenceIdentity == lastPreferenceIdentity &&
            fingerprint == lastFingerprint &&
            connectionMode == lastConnectionMode &&
            capturedAtMs - lastSavedAtMs < MINIMUM_WRITE_INTERVAL_MS
        ) {
            return
        }

        val snapshot =
            JSONObject()
                .put("captured_at_ms", capturedAtMs)
                .put("connection_mode", connectionMode.name)
                .put("info", info.toStoredJson())

        context.applicationContext
            .getSharedPreferences(
                PREFERENCES_NAME,
                Context.MODE_PRIVATE
            )
            .edit()
            .putString(SNAPSHOT_KEY, snapshot.toString())
            .putString(profileSnapshotKey(info.deviceId), snapshot.toString())
            .apply()

        lastPreferenceIdentity = preferenceIdentity
        lastSavedAtMs = capturedAtMs
        lastFingerprint = fingerprint
        lastConnectionMode = connectionMode
    }

    /** Background reports are scoped; they must not replace the selected diagnostic snapshot. */
    @Synchronized
    fun saveForDevice(context: Context, connectionMode: SensorConnectionMode, info: UvirSensorRuntimeInfo) {
        if (info.deviceId.isBlank()) return
        if (UvirSensorCredentialStore.load(context).deviceId.equals(info.deviceId, true)) {
            save(context, connectionMode, info)
            return
        }
        val now = System.currentTimeMillis()
        val preferences = context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        val uid = System.identityHashCode(preferences) to normalizeSensorDeviceId(info.deviceId)
        if (now - (profileWriteTimes[uid] ?: 0L) < MINIMUM_WRITE_INTERVAL_MS &&
            preferences.contains(profileSnapshotKey(info.deviceId))) return
        profileWriteTimes[uid] = now
        val encoded = JSONObject().put("captured_at_ms", now)
            .put("connection_mode", connectionMode.name).put("info", info.toStoredJson())
        val previous = preferences.getString(profileSnapshotKey(info.deviceId), null)
        if (previous != null) {
            val snapshot = runCatching { JSONObject(previous) }.getOrNull()
            if (snapshot?.optString("connection_mode") == connectionMode.name &&
                snapshot.optJSONObject("info")?.toString() == info.toStoredJson().toString() &&
                System.currentTimeMillis() - snapshot.optLong("captured_at_ms") < MINIMUM_WRITE_INTERVAL_MS) return
        }
        preferences.edit().putString(profileSnapshotKey(info.deviceId), encoded.toString()).apply()
    }

    fun load(context: Context): UvirStoredSensorRuntimeInfo? {
        val stored =
            context.applicationContext
                .getSharedPreferences(
                    PREFERENCES_NAME,
                    Context.MODE_PRIVATE
                )
                .getString(SNAPSHOT_KEY, null)
                ?: return null

        return runCatching {
            val snapshot = JSONObject(stored)
            val connectionMode =
                SensorConnectionMode.fromStoredValue(
                    snapshot.optString("connection_mode")
                )
            val info =
                UvirSensorRuntimeInfo().updatedFrom(
                    snapshot.getJSONObject("info")
                )

            UvirStoredSensorRuntimeInfo(
                capturedAtMs = snapshot.getLong("captured_at_ms"),
                connectionMode = connectionMode,
                info = info
            )
        }.getOrNull()
    }

    private fun profileSnapshotKey(deviceId: String): String =
        "sensor_snapshot." + normalizeSensorDeviceId(deviceId)

    @Synchronized
    fun activate(context: Context, deviceId: String): Boolean {
        val preferences = context.applicationContext.getSharedPreferences(
            PREFERENCES_NAME, Context.MODE_PRIVATE
        )
        val current = load(context)
        val editor = preferences.edit()
        if (current != null && current.info.deviceId.isNotBlank()) {
            editor.putString(profileSnapshotKey(current.info.deviceId), preferences.getString(SNAPSHOT_KEY, null))
        }
        val target = if (current?.info?.deviceId.equals(deviceId, ignoreCase = true)) {
            preferences.getString(SNAPSHOT_KEY, null)
        } else preferences.getString(profileSnapshotKey(deviceId), null)
        editor.remove(SNAPSHOT_KEY)
        if (deviceId.isNotBlank() && target != null) editor.putString(SNAPSHOT_KEY, target)
        lastFingerprint = ""
        lastSavedAtMs = 0L
        lastConnectionMode = null
        return editor.commit()
    }

    @Synchronized
    fun clearActive(context: Context): Boolean {
        val currentId = load(context)?.info?.deviceId.orEmpty()
        val preferences = context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        lastFingerprint = ""
        lastSavedAtMs = 0L
        lastConnectionMode = null
        return preferences.edit().remove(SNAPSHOT_KEY).remove(profileSnapshotKey(currentId)).commit()
    }

    @Synchronized
    fun clear(context: Context): Boolean {
        val cleared =
            context.applicationContext
                .getSharedPreferences(
                    PREFERENCES_NAME,
                    Context.MODE_PRIVATE
                )
                .edit()
                .clear()
                .commit()

        if (cleared) {
            lastSavedAtMs = 0L
            lastFingerprint = ""
            lastConnectionMode = null
        }

        return cleared
    }
}

private fun UvirSensorRuntimeInfo.hasDiagnosticIdentity(): Boolean =
    deviceId.isNotBlank() ||
        sensorName.isNotBlank() ||
        firmwareVersion.isNotBlank()

private fun UvirSensorRuntimeInfo.stableDiagnosticFingerprint(): String =
    listOf(
        protocol,
        deviceId,
        boardName,
        chipModel,
        chipCores,
        cpuFrequencyMhz,
        sensorName,
        firmwareVersion,
        unit,
        calibrationKind,
        visibleCalibrationFactor,
        uvCalibrationFactor,
        offlineAlertRepeatSeconds,
        sensorAvailable,
        uvAvailable,
        activeTransport,
        wifiConfigured,
        wifiEnabled,
        wifiNetwork,
        wifiHostname,
        wifiAddress,
        bluetoothEnabled,
        bluetoothName,
        integrationMs,
        gain,
        storageBackend,
        storageRecordSizeBytes,
        sdAvailable,
        sdForeign,
        sdType,
        sdTotalBytes,
        sdUsedBytes,
        sdFreeBytes,
        sdRecordCapacityTotal,
        sdRecordCapacityFree,
        sdInvalidRecords,
        sdMountErrors,
        sdWriteErrors,
        rtcAvailable,
        rtcValid,
        rtcOscillatorStopped,
        rtcCurrentTimeMs,
        timeSource,
        rtcReadErrors,
        rtcWriteErrors,
        autonomousRecordingEnabled,
        automaticShutdownEnabled,
        automaticShutdownSeconds,
        statusLedEnabled,
        statusLedBrightness,
        statusBuzzerEnabled,
        statusBuzzerVolume,
        externalCommandEnabled
    ).joinToString("|")

private fun UvirSensorRuntimeInfo.toStoredJson(): JSONObject =
    JSONObject()
        .put("protocol", protocol)
        .put("device_id", deviceId)
        .put("board", boardName)
        .put("chip_model", chipModel)
        .putIfNotNull("chip_cores", chipCores)
        .putIfNotNull("cpu_frequency_mhz", cpuFrequencyMhz)
        .put("sensor", sensorName)
        .put("firmware", firmwareVersion)
        .put("unit", unit)
        .put("calibration", calibrationKind)
        .putIfNotNull("visible_calibration_factor", visibleCalibrationFactor)
        .putIfNotNull("uv_calibration_factor", uvCalibrationFactor)
        .putIfNotNull("sensor_available", sensorAvailable)
        .putIfNotNull("uv_available", uvAvailable)
        .putIfNotNull("uptime_ms", uptimeMs)
        .putIfNotNull("streaming", streaming)
        .putIfNotNull("stream_interval_ms", streamIntervalMs)
        .put("active_transport", activeTransport)
        .putIfNotNull("wifi_configured", wifiConfigured)
        .putIfNotNull("wifi_enabled", wifiEnabled)
        .putIfNotNull("wifi_connected", wifiConnected)
        .put("wifi_network", wifiNetwork)
        .put("wifi_hostname", wifiHostname)
        .put("wifi_ip", wifiAddress)
        .putIfNotNull("wifi_rssi_dbm", wifiRssiDbm)
        .putIfNotNull("bluetooth_enabled", bluetoothEnabled)
        .putIfNotNull("bluetooth_connected", bluetoothConnected)
        .put("bluetooth_name", bluetoothName)
        .putIfNotNull("bluetooth_rssi_delta", bluetoothRssiDelta)
        .putIfNotNull("integration_ms", integrationMs)
        .putIfNotNull("gain", gain)
        .putIfNotNull("seq", sampleSequence)
        .putIfNotNull("flash_size_bytes", flashSizeBytes)
        .putIfNotNull("app_partition_size_bytes", appPartitionSizeBytes)
        .putIfNotNull("firmware_size_bytes", firmwareSizeBytes)
        .putIfNotNull("free_app_partition_bytes", freeAppPartitionBytes)
        .putIfNotNull("heap_size_bytes", heapSizeBytes)
        .putIfNotNull("free_heap_bytes", freeHeapBytes)
        .putIfNotNull("filesystem_total_bytes", filesystemTotalBytes)
        .putIfNotNull("filesystem_used_bytes", filesystemUsedBytes)
        .put("storage_backend", storageBackend)
        .putIfNotNull("storage_record_size_bytes", storageRecordSizeBytes)
        .putIfNotNull("sd_available", sdAvailable)
        .putIfNotNull("sd_foreign", sdForeign)
        .put("sd_type", sdType)
        .putIfNotNull("sd_total_bytes", sdTotalBytes)
        .putIfNotNull("sd_used_bytes", sdUsedBytes)
        .putIfNotNull("sd_free_bytes", sdFreeBytes)
        .putIfNotNull("sd_record_capacity_total", sdRecordCapacityTotal)
        .putIfNotNull("sd_record_capacity_free", sdRecordCapacityFree)
        .putIfNotNull("sd_invalid_records", sdInvalidRecords)
        .putIfNotNull("sd_mount_errors", sdMountErrors)
        .putIfNotNull("sd_write_errors", sdWriteErrors)
        .putIfNotNull("sd_read_write_ok", sdReadWriteOk)
        .putIfNotNull("time_synced", timeSynced)
        .putIfNotNull("rtc_available", rtcAvailable)
        .putIfNotNull("rtc_valid", rtcValid)
        .putIfNotNull("rtc_oscillator_stopped", rtcOscillatorStopped)
        .putIfNotNull("rtc_read_ok", rtcReadOk)
        .putIfNotNull("rtc_current_time_ms", rtcCurrentTimeMs)
        .put("time_source", timeSource)
        .putIfNotNull("rtc_read_errors", rtcReadErrors)
        .putIfNotNull("rtc_write_errors", rtcWriteErrors)
        .putIfNotNull("offline_storage_available", offlineStorageAvailable)
        .putIfNotNull("offline_capacity", offlineCapacity)
        .putIfNotNull("offline_used", offlineUsed)
        .putIfNotNull("offline_remaining", offlineRemaining)
        .putIfNotNull("offline_storage_full", offlineStorageFull)
        .putIfNotNull("offline_acquisitions", offlineAcquisitions)
        .putIfNotNull("offline_alerts", offlineAlerts)
        .putIfNotNull("alert_recording_enabled", alertRecordingEnabled)
        .putIfNotNull("alert_start_delay_seconds", alertStartDelaySeconds)
        .putIfNotNull("alert_duration_seconds", alertDurationSeconds)
        .putIfNotNull("alert_max_registrations", alertMaxRegistrations)
        .putIfNotNull("alert_completed_registrations", alertCompletedRegistrations)
        .putIfNotNull(
            "offline_alert_repeat_seconds",
            offlineAlertRepeatSeconds
        )
        .putIfNotNull("offline_errors", offlineErrors)
        .putIfNotNull("offline_recording", offlineRecording)
        .putIfNotNull("offline_external_command", offlineExternalCommand)
        .put("offline_condition_plan", offlineConditionPlan?.encode() ?: "")
        .putIfNotNull("offline_condition_waiting", offlineConditionWaiting)
        .putIfNotNull("offline_end_ms", offlineEndAtMs)
        .putIfNotNull("offline_started_ms", offlineStartedAtMs)
        .putIfNotNull(
            "autonomous_recording_enabled",
            autonomousRecordingEnabled
        )
        .putIfNotNull(
            "automatic_shutdown_enabled",
            automaticShutdownEnabled
        )
        .putIfNotNull(
            "automatic_shutdown_seconds",
            automaticShutdownSeconds
        )
        .putIfNotNull("status_led_enabled", statusLedEnabled)
        .putIfNotNull("status_led_brightness", statusLedBrightness)
        .putIfNotNull("status_buzzer_enabled", statusBuzzerEnabled)
        .putIfNotNull("status_buzzer_volume", statusBuzzerVolume)
        .putIfNotNull("external_command_enabled", externalCommandEnabled)

private fun JSONObject.putIfNotNull(
    key: String,
    value: Any?
): JSONObject =
    apply {
        if (value != null) {
            put(key, value)
        }
    }
