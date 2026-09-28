package me.mondiversi.uvir

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

internal const val SENSOR_SETTINGS_SYNC_MIN_FIRMWARE = "0.5.101"
internal const val SENSOR_SETTINGS_PENDING_PREFS = "uvir_sensor_settings_pending"

/** Reception time is deliberately NOT used as a settings revision. Ties favor hardware. */
internal fun appSensorSettingsAreNewer(appUpdatedAt: Long, sensorUpdatedAt: Long): Boolean =
    appUpdatedAt.coerceAtLeast(0L) > sensorUpdatedAt.coerceAtLeast(0L)

internal fun nextSensorSettingsUpdate(now: Long, appUpdatedAt: Long, sensorUpdatedAt: Long): Long =
    maxOf(now.coerceAtLeast(1L), appUpdatedAt + 1L, sensorUpdatedAt + 1L)

internal data class UvirPendingSensorSettings(val updatedAt: Long, val commands: List<String>)

internal fun sensorSettingsCommandKey(command: String): String? = when {
    command.startsWith("SENSOR_CONFIG ") -> "general"
    command.startsWith("CALIBRATION_CONFIG ") -> "calibration"
    command.startsWith("SAMPLING_CONFIG ") -> "sampling"
    command.startsWith("WIFI_CONFIG ") -> "wifi"
    command.startsWith("INTERNET_CONFIG ") -> "internet"
    command.startsWith("RADIO WIFI ") -> "radio_wifi"
    command.startsWith("RADIO BLUETOOTH ") -> "radio_bluetooth"
    command.startsWith("ALERT_SETTINGS ") -> "alerts"
    else -> null
}

/** Latest edit wins within a group; retain unrelated unsent groups, with radios last. */
internal fun mergePendingSensorSettings(previous: List<String>, edits: List<String>): List<String> {
    val merged = linkedMapOf<String, String>()
    (previous + edits).forEach { command -> sensorSettingsCommandKey(command)?.let { merged[it] = command } }
    return merged.values.sortedBy { if (it.startsWith("RADIO ")) 1 else 0 }
}

internal fun String.sensorSettingsHex(): String = toByteArray(Charsets.UTF_8)
    .joinToString("") { "%02x".format(it.toInt() and 0xff) }

internal fun String.decodeSensorSettingsHex(): String = if (this == "-") "" else {
    require(length % 2 == 0)
    chunked(2).map { it.toInt(16).toByte() }.toByteArray().toString(Charsets.UTF_8)
}

/** Cache and backups include the desired edit, not just the last hardware readback. */
internal fun UvirPendingSensorSettings.applyTo(snapshot: UvirSensorSettingsSnapshot): UvirSensorSettingsSnapshot {
    var result = snapshot
    commands.forEach { command ->
        val t = command.split(' ')
        result = when (t[0]) {
            "SENSOR_CONFIG" -> result.copy(sensorParameters = result.sensorParameters.copy(
                statusLedEnabled = t[1] == "ON", statusLedBrightness = t[2].toInt(), autonomousRecordingEnabled = t[3] == "ON",
                statusBuzzerEnabled = t.getOrNull(4)?.let { it == "ON" } ?: result.sensorParameters.statusBuzzerEnabled,
                statusBuzzerVolume = t.getOrNull(5)?.toInt() ?: result.sensorParameters.statusBuzzerVolume,
                automaticShutdownEnabled = t.getOrNull(6)?.let { it == "ON" } ?: result.sensorParameters.automaticShutdownEnabled,
                automaticShutdownSeconds = t.getOrNull(7)?.toInt() ?: result.sensorParameters.automaticShutdownSeconds,
                externalCommandEnabled = t.getOrNull(8)?.let { it == "ON" } ?: result.sensorParameters.externalCommandEnabled))
            "SAMPLING_CONFIG" -> result.copy(acquisitionParameters = AcquisitionParameters(t[1].toInt(), t[2].toLong(), t[3] == "1"))
            "CALIBRATION_CONFIG" -> result.copy(calibrationSettings = SensorCalibrationSettings(t[1].toFloat(), t[2].toFloat()))
            "WIFI_CONFIG" -> result.copy(wifiSsid = t[1].decodeSensorSettingsHex())
            "INTERNET_CONFIG" -> result.copy(internetEnabled = t[1] == "ON", internetUsePrimaryWifi = t[2] == "PRIMARY",
                internetRelayHost = t[5].decodeSensorSettingsHex(), internetRelayPort = t[6].toInt())
            "RADIO" -> if (t[1] == "WIFI") result.copy(wifiEnabled = t[2] == "ON") else result.copy(bluetoothEnabled = t[2] == "ON")
            "ALERT_SETTINGS" -> result.copy(alertRepeatSeconds = t[1].toInt(), alertRecordingEnabled = t[2] == "SAVE",
                alertStartDelaySeconds = t[3].toLong(), alertDurationSeconds = t[4].toLong(), alertMaxRegistrations = t[5].toInt(),
                alertRules = t[6].decodeSensorSettingsHex().lineSequence().filter(String::isNotBlank).map { line ->
                    val r = line.split(' ')
                    ThresholdAlertRule(ThresholdAlertMetric.valueOf(r[1]), true, ThresholdAlertDirection.valueOf(r[2]), r[3].toFloat())
                }.toList())
            else -> result
        }
    }
    return result.copy(updatedAtMs = updatedAt)
}

internal fun UvirPendingSensorSettings.applyTo(credentials: UvirSensorCredentials): UvirSensorCredentials {
    var result = credentials
    commands.forEach { command ->
        val t = command.split(' ')
        result = when (t[0]) {
            "WIFI_CONFIG" -> result.copy(wifiSsid = t[1].decodeSensorSettingsHex(), wifiPassword = t[2].decodeSensorSettingsHex())
            "INTERNET_CONFIG" -> result.copy(internetEnabled = t[1] == "ON", internetUsePrimaryWifi = t[2] == "PRIMARY",
                internetWifiSsid = t[3].decodeSensorSettingsHex(), internetWifiPassword = t[4].decodeSensorSettingsHex(),
                internetRelayHost = t[5].decodeSensorSettingsHex(), internetRelayPort = t[6].toInt(),
                internetMqttUsername = t[7].decodeSensorSettingsHex(), internetMqttPassword = t[8].decodeSensorSettingsHex())
            "RADIO" -> if (t[1] == "WIFI") result.copy(wifiEnabled = t[2] == "ON") else result.copy(bluetoothEnabled = t[2] == "ON")
            else -> result
        }
    }
    return result
}

/** Store only the alert configuration, never its ON/OFF state or session ID. */
internal fun alertSettingsEdit(commands: List<String>): String? {
    val config = commands.lastOrNull { it.startsWith("ALERT_CONFIG ") }?.split(' ') ?: return null
    if (config.size < 4 || "ALERTS_CLEAR" !in commands) return null
    val rules = commands.filter { it.startsWith("ALERT_RULE ") }.joinToString("\n").sensorSettingsHex().ifBlank { "-" }
    return "ALERT_SETTINGS ${config[2]} ${config.getOrElse(4) { "SAVE" }} " +
        "${config.getOrElse(5) { "0" }} ${config.getOrElse(6) { "0" }} ${config.getOrElse(7) { "0" }} $rules"
}

/** Reconcile a genuinely newer cached snapshot without replaying operational state. */
internal fun cachedSensorSettingsCommands(snapshot: UvirSensorSettingsSnapshot, credentials: UvirSensorCredentials): List<String> = buildList {
    add(sensorParametersCommand(snapshot.sensorParameters))
    add(sensorSamplingCommand(snapshot.acquisitionParameters))
    add(sensorCalibrationCommand(snapshot.calibrationSettings))
    alertSettingsEdit(sensorAlertCommands(ThresholdAlertSettings(
        enabled = false, rules = snapshot.alertRules, repeatSeconds = snapshot.alertRepeatSeconds,
        recordEvents = snapshot.alertRecordingEnabled ?: true, startDelaySeconds = snapshot.alertStartDelaySeconds,
        durationSeconds = snapshot.alertDurationSeconds, maxRegistrations = snapshot.alertMaxRegistrations,
        sound = ThresholdAlertSound.SILENT, volume = 0)))?.let(::add)
    validatedSensorWifiConfiguration(credentials.wifiSsid, credentials.wifiPassword)?.let { add(it.protocolCommand) }
    SensorInternetConfiguration(enabled = snapshot.internetEnabled, usePrimaryWifi = snapshot.internetUsePrimaryWifi,
        wifiSsid = credentials.internetWifiSsid, wifiPassword = credentials.internetWifiPassword,
        relayHost = snapshot.internetRelayHost, relayPort = snapshot.internetRelayPort,
        mqttUsername = credentials.internetMqttUsername, mqttPassword = credentials.internetMqttPassword)
        .takeIf { it.isComplete }?.let { add(it.protocolCommand) }
    add("RADIO WIFI ${if (snapshot.wifiEnabled) "ON" else "OFF"}")
    add("RADIO BLUETOOTH ${if (snapshot.bluetoothEnabled) "ON" else "OFF"}")
}

/** Durable, per-hardware outbox. Excluded from cloud backup because commands may contain passwords. */
internal object UvirSensorSettingsSyncStore {
    private fun preferences(context: Context) = context.applicationContext
        .getSharedPreferences(SENSOR_SETTINGS_PENDING_PREFS, Context.MODE_PRIVATE)
    private fun key(id: String) = normalizeSensorDeviceId(id)
    private fun confirmedKey(id: String) = "confirmed." + key(id)
    private fun association(context: Context, id: String): String =
        java.security.MessageDigest.getInstance("SHA-256").digest(
            UvirSensorCredentialStore.loadForDevice(context, id)?.authToken.orEmpty().toByteArray(Charsets.UTF_8)
        ).joinToString("") { "%02x".format(it.toInt() and 0xff) }

    @Synchronized
    fun pending(context: Context, deviceId: String): UvirPendingSensorSettings? = runCatching {
        val raw = preferences(context).getString(key(deviceId), null) ?: return null
        val json = JSONObject(raw)
        if (json.optString("association") != association(context, deviceId)) {
            preferences(context).edit().remove(key(deviceId)).commit()
            return null // A factory reset creates new access credentials; discard the old association's edits.
        }
        val array = json.getJSONArray("commands")
        UvirPendingSensorSettings(json.getLong("updated_at"),
            List(array.length()) { array.getString(it) }.filter { sensorSettingsCommandKey(it) != null })
    }.getOrNull()

    @Synchronized
    fun stage(context: Context, deviceId: String, sensorUpdatedAt: Long, edits: List<String>): UvirPendingSensorSettings {
        require(deviceId.isNotBlank())
        val previous = pending(context, deviceId)?.takeIf { appSensorSettingsAreNewer(it.updatedAt, sensorUpdatedAt) }
        val pending = UvirPendingSensorSettings(
            nextSensorSettingsUpdate(System.currentTimeMillis(), previous?.updatedAt ?: 0L, sensorUpdatedAt),
            mergePendingSensorSettings(previous?.commands.orEmpty(), edits))
        check(preferences(context).edit().putString(key(deviceId), JSONObject()
            .put("association", association(context, deviceId))
            .put("updated_at", pending.updatedAt).put("commands", JSONArray(pending.commands)).toString()).commit())
        UvirDatabaseHelper(context).use { database ->
            database.readSensorSettings(deviceId)?.let { snapshot ->
                check(database.upsertSensorSettings(deviceId, pending.applyTo(snapshot), syncedAt = snapshot.lastSyncedAt))
            }
        }
        return pending
    }

    @Synchronized
    fun reconcile(context: Context, deviceId: String, snapshot: UvirSensorSettingsSnapshot,
                  database: UvirDatabaseHelper): UvirPendingSensorSettings? {
        val pending = pending(context, deviceId)
        if (pending != null && appSensorSettingsAreNewer(pending.updatedAt, snapshot.updatedAtMs)) return pending
        val fingerprint = association(context, deviceId)
        val cached = database.readSensorSettings(deviceId)
        if (cached != null && appSensorSettingsAreNewer(cached.updatedAtMs, snapshot.updatedAtMs) &&
            preferences(context).getString(confirmedKey(deviceId), null) == fingerprint) {
            val credentials = UvirSensorCredentialStore.loadForDevice(context, deviceId) ?: UvirSensorCredentials(deviceId = deviceId)
            val retry = UvirPendingSensorSettings(cached.updatedAtMs, cachedSensorSettingsCommands(cached, credentials))
            check(preferences(context).edit().putString(key(deviceId), JSONObject().put("association", fingerprint)
                .put("updated_at", retry.updatedAt).put("commands", JSONArray(retry.commands)).toString()).commit())
            return retry // Also protects against a delayed HELLO received after a newer acknowledgement.
        }
        // Serialized with stage: a stale readback cannot race with a newly queued edit.
        check(database.upsertSensorSettings(deviceId, snapshot))
        if (pending != null && pending.updatedAt == snapshot.updatedAtMs) {
            UvirSensorCredentialStore.loadForDevice(context, deviceId)?.let { credentials ->
                check(UvirSensorCredentialStore.updateAssociatedSensor(context, pending.applyTo(credentials)))
            }
        }
        preferences(context).edit().remove(key(deviceId)).putString(confirmedKey(deviceId), fingerprint).commit()
        return null
    }

    @Synchronized
    fun forget(context: Context, deviceId: String) {
        preferences(context).edit().remove(key(deviceId)).remove(confirmedKey(deviceId)).commit()
    }

    @Synchronized
    fun clear(context: Context) { preferences(context).edit().clear().commit() }
}

internal fun UvirPendingSensorSettings.protocolCommand(): String =
    "SETTINGS_APPLY $updatedAt ${commands.joinToString("\n").sensorSettingsHex()}"

/** Persist before writing; a successful socket write alone never clears the outbox. */
internal fun sendDatedSensorSettings(
    context: Context, deviceId: String, runtime: UvirSensorRuntimeInfo,
    commands: List<String>, sendRaw: (List<String>) -> Boolean
): Boolean {
    if (deviceId.isBlank() || compareFirmwareVersions(runtime.firmwareVersion, SENSOR_SETTINGS_SYNC_MIN_FIRMWARE) < 0) {
        return sendRaw(commands)
    }
    val edits = commands.filter { sensorSettingsCommandKey(it) != null } + listOfNotNull(alertSettingsEdit(commands))
    if (edits.isEmpty()) return sendRaw(commands)
    val pending = runCatching { UvirSensorSettingsSyncStore.stage(context, deviceId, runtime.settingsUpdatedAtMs ?: 0L, edits) }
        .getOrElse { return false }
    val remainder = commands.filter {
        sensorSettingsCommandKey(it) == null && it != "ALERTS_CLEAR" && !it.startsWith("ALERT_RULE ")
    }
    return sendRaw(listOf(pending.protocolCommand()) + remainder)
}
