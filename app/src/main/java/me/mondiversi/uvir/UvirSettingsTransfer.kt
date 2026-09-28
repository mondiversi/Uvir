package me.mondiversi.uvir

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

internal const val UVIR_SETTINGS_TRANSFER_SCHEMA = "uvir-settings"
internal const val UVIR_SETTINGS_TRANSFER_VERSION = 3
private const val UVIR_SETTINGS_TRANSFER_MINIMUM_VERSION = 1
private const val SENSOR_PHONE_PREFERENCES = "phone_preferences"
private const val SENSOR_DISPLAY_NAME = "sensor_display_name"
private const val SENSOR_SENSITIVE_CONNECTION_SETTINGS =
    "sensitive_connection_settings"
private const val PREFERENCE_TYPE = "preference_type"
private const val PREFERENCE_VALUE = "value"

private val transientSettingsKeys =
    sensorOperationalPreferenceKeys.toSet() +
        setOf(KEY_UNREAD_ACQUISITION_COUNT, KEY_UNREAD_ALERT_COUNT)

internal fun createUvirAppSettingsBackup(
    context: Context,
    encryptionPassword: CharArray
): File {
    val preferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val payload = JSONObject()
    preferences.all
        .filterKeys(::isTransferableAppPreference)
        .forEach { (key, value) ->
            payload.putPreferenceValue(key, value)
        }
    return writeUvirSettingsFile(
        context = context,
        scope = "app",
        sensorHardwareUid = null,
        payload = payload,
        encryptionPassword = encryptionPassword
    )
}

internal fun createUvirSensorSettingsBackup(
    context: Context,
    database: UvirDatabaseHelper,
    hardwareUid: String,
    includeSensitiveInformation: Boolean = false,
    encryptionPassword: CharArray
): File {
    val normalizedHardwareUid = hardwareUid.trim()
    require(normalizedHardwareUid.isNotEmpty()) { "No selected sensor" }
    val preferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val activeHardwareUid =
        preferences.getString("active_device_id", "").orEmpty()
    val recordingPreferenceKey =
        if (normalizeSensorDeviceId(activeHardwareUid) == normalizeSensorDeviceId(normalizedHardwareUid)) {
            KEY_THRESHOLD_ALERT_RECORD_EVENTS
        } else {
            sensorContextPreferenceKey(normalizedHardwareUid, KEY_THRESHOLD_ALERT_RECORD_EVENTS)
        }
    val snapshot =
        (database.readSensorSettings(normalizedHardwareUid)
            ?: readCachedSensorSettings(
                context = context,
                hardwareUid = normalizedHardwareUid
            )).let { settings ->
            settings.copy(
                alertRecordingEnabled = preferences.getBoolean(
                    recordingPreferenceKey,
                    settings.alertRecordingEnabled ?: true
                )
            )
        }
    val pendingSettings = UvirSensorSettingsSyncStore.pending(context, normalizedHardwareUid)
    val credentials =
        (UvirSensorCredentialStore.loadForDevice(context, normalizedHardwareUid)
            ?: UvirSensorCredentials(deviceId = normalizedHardwareUid)).let { pendingSettings?.applyTo(it) ?: it }
    val payload =
        (pendingSettings?.applyTo(snapshot) ?: snapshot).toTransferJson()
            .put(
                SENSOR_DISPLAY_NAME,
                database.findSensorProfile(normalizedHardwareUid)?.displayName
                    ?: normalizedHardwareUid
            )
            .put(
                SENSOR_PHONE_PREFERENCES,
                preferences.sensorConfigurationToTransferJson(normalizedHardwareUid)
            )
            // Network names are settings; passwords, pairing PINs, MQTT
            // credentials and the device authentication token are never
            // written to a plain-text export.
            .put("internet_wifi_ssid", credentials.internetWifiSsid)
    if (includeSensitiveInformation) {
        require(credentials.isProvisioned) {
            "The selected sensor has no restorable association"
        }
        payload.put(
            SENSOR_SENSITIVE_CONNECTION_SETTINGS,
            JSONObject()
                .put("device_id", credentials.deviceId)
                .put("firmware_version", credentials.firmwareVersion)
                .put("auth_token", credentials.authToken)
                .put("wifi_password", credentials.wifiPassword)
                .put("wifi_host", credentials.wifiHost)
                .put("wifi_port", credentials.wifiPort)
                .put("wifi_discovery_port", credentials.wifiDiscoveryPort)
                .put("internet_wifi_password", credentials.internetWifiPassword)
                .put("internet_mqtt_username", credentials.internetMqttUsername)
                .put("internet_mqtt_password", credentials.internetMqttPassword)
                .put("bluetooth_name", credentials.bluetoothName)
                .put("bluetooth_pin", credentials.bluetoothPin)
        )
    }
    return writeUvirSettingsFile(
        context = context,
        scope = "sensor",
        sensorHardwareUid = normalizedHardwareUid,
        payload = payload,
        encryptionPassword = encryptionPassword
    )
}

private fun readCachedSensorSettings(
    context: Context,
    hardwareUid: String
): UvirSensorSettingsSnapshot {
    val preferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val credentials =
        UvirSensorCredentialStore.loadForDevice(context, hardwareUid)
            ?: UvirSensorCredentials(deviceId = hardwareUid)
    val alerts = loadThresholdAlertSettings(preferences)
    return UvirSensorSettingsSnapshot(
        schemaVersion = SENSOR_SETTINGS_SCHEMA_VERSION,
        firmwareVersion = credentials.firmwareVersion,
        sensorParameters =
            SensorParameters(
                autonomousRecordingEnabled =
                    preferences.getBoolean(KEY_SENSOR_AUTONOMOUS_RECORDING, true),
                automaticShutdownEnabled =
                    preferences.getBoolean(KEY_SENSOR_AUTOMATIC_SHUTDOWN_ENABLED, false),
                automaticShutdownSeconds =
                    preferences.getInt(KEY_SENSOR_AUTOMATIC_SHUTDOWN_SECONDS, 1_800)
                        .coerceIn(60, 86_400),
                statusLedEnabled =
                    preferences.getBoolean(KEY_SENSOR_STATUS_LED_ENABLED, true),
                statusLedBrightness =
                    preferences.getInt(KEY_SENSOR_STATUS_LED_BRIGHTNESS, 10)
                        .coerceIn(1, 100),
                statusBuzzerEnabled =
                    preferences.getBoolean(KEY_SENSOR_STATUS_BUZZER_ENABLED, true),
                statusBuzzerVolume =
                    preferences.getInt(KEY_SENSOR_STATUS_BUZZER_VOLUME, 10)
                        .coerceIn(1, 100),
                externalCommandEnabled =
                    preferences.getBoolean(
                        KEY_SENSOR_EXTERNAL_COMMAND_ENABLED,
                        true
                    )
            ),
        acquisitionParameters =
            AcquisitionParameters(
                samplesPerMeasurement =
                    preferences.getInt(KEY_SAMPLES_PER_MEASUREMENT, 5)
                        .coerceIn(1, 21),
                sampleSpacingMs = readSampleSpacingMs(preferences),
                discardExtremes =
                    preferences.getBoolean(KEY_DISCARD_EXTREMES, true)
            ),
        calibrationSettings =
            SensorCalibrationSettings(
                visibleFactor =
                    preferences.getFloat(
                        KEY_SENSOR_VISIBLE_CALIBRATION_FACTOR,
                        DEFAULT_SENSOR_CALIBRATION_FACTOR
                    ).coerceIn(
                        MIN_SENSOR_CALIBRATION_FACTOR,
                        MAX_SENSOR_CALIBRATION_FACTOR
                    ),
                uvFactor =
                    preferences.getFloat(
                        KEY_SENSOR_UV_CALIBRATION_FACTOR,
                        DEFAULT_SENSOR_CALIBRATION_FACTOR
                    ).coerceIn(
                        MIN_SENSOR_CALIBRATION_FACTOR,
                        MAX_SENSOR_CALIBRATION_FACTOR
                    )
            ),
        alertMonitoringEnabled = alerts.hasActiveMonitoring(),
        alertRepeatSeconds = alerts.repeatSeconds,
        alertSessionId =
            preferences.getLong(KEY_THRESHOLD_ALERT_SESSION_ID, 0L)
                .coerceAtLeast(0L),
        alertRules = alerts.rules,
        alertRecordingEnabled = alerts.recordEvents,
        alertStartDelaySeconds = alerts.startDelaySeconds,
        alertDurationSeconds = alerts.durationSeconds,
        alertMaxRegistrations = alerts.maxRegistrations,
        wifiEnabled = credentials.wifiEnabled,
        bluetoothEnabled = credentials.bluetoothEnabled,
        internetEnabled = credentials.internetEnabled,
        internetUsePrimaryWifi = credentials.internetUsePrimaryWifi,
        wifiSsid = credentials.wifiSsid,
        internetRelayHost = credentials.internetRelayHost,
        internetRelayPort = credentials.internetRelayPort
    )
}

private fun writeUvirSettingsFile(
    context: Context,
    scope: String,
    sensorHardwareUid: String?,
    payload: JSONObject,
    encryptionPassword: CharArray
): File {
    val root =
        JSONObject()
            .put("schema", UVIR_SETTINGS_TRANSFER_SCHEMA)
            .put("version", UVIR_SETTINGS_TRANSFER_VERSION)
            .put("scope", scope)
            .put("exported_at", System.currentTimeMillis())
            .put("payload", payload)
    val directory = File(context.cacheDir, "shared").apply { mkdirs() }
    val encrypted = encryptUvirSettingsText(root.toString(), encryptionPassword)
    val encoded =
        JSONObject()
            .put("schema", UVIR_ENCRYPTED_SETTINGS_SCHEMA)
            .put("version", UVIR_ENCRYPTED_SETTINGS_VERSION)
            .put("encryption", UVIR_SETTINGS_ENCRYPTION_NAME)
            .put("kdf", UVIR_SETTINGS_KDF_NAME)
            .put("iterations", encrypted.iterations)
            .put("salt", encrypted.salt)
            .put("iv", encrypted.iv)
            .put("ciphertext", encrypted.ciphertext)
            .toString(2)
    return File(
        directory,
        uvirSettingsExportFileName(
            sensorHardwareUid = sensorHardwareUid,
            encrypted = true
        )
    ).apply {
        writeText(encoded, Charsets.UTF_8)
    }
}

internal fun uvirSettingsFilesRequirePassword(
    context: Context,
    uris: List<Uri>
): Boolean =
    uris.any { uri ->
        runCatching {
            readUvirSettingsText(context, uri)
        }.mapCatching(::JSONObject)
            .getOrNull()
            ?.optString("schema") == UVIR_ENCRYPTED_SETTINGS_SCHEMA
    }

internal fun importUvirSettingsFiles(
    context: Context,
    database: UvirDatabaseHelper,
    uris: List<Uri>,
    decryptionPassword: CharArray? = null
): UvirSettingsImportResult {
    val documents =
        uris.map { uri ->
            val encodedRoot = JSONObject(readUvirSettingsText(context, uri))
            DecodedUvirSettingsDocument(
                root = decodeUvirSettingsRoot(encodedRoot, decryptionPassword),
                encrypted =
                    encodedRoot.optString("schema") ==
                        UVIR_ENCRYPTED_SETTINGS_SCHEMA
            )
        }
    documents.forEach { document ->
        val root = document.root
        require(root.optString("schema") == UVIR_SETTINGS_TRANSFER_SCHEMA)
        require(
            root.optInt("version") in
                UVIR_SETTINGS_TRANSFER_MINIMUM_VERSION..UVIR_SETTINGS_TRANSFER_VERSION
        )
        require(root.getString("scope") in setOf("app", "sensor"))
        root.getJSONObject("payload")
    }
    val importedSensors = mutableListOf<UvirImportedSensorSettings>()
    var importedAppFiles = 0
    documents.forEach { document ->
        val root = document.root
        val payload = root.getJSONObject("payload")
        when (root.getString("scope")) {
            "app" -> {
                importAppSettings(context, payload)
                importedAppFiles++
            }
            "sensor" ->
                importedSensors.add(
                    importSensorSettings(
                        context = context,
                        database = database,
                        payload = payload,
                        encrypted = document.encrypted
                    )
                )
            else -> error("Unsupported settings scope")
        }
    }
    return UvirSettingsImportResult(
        fileCount = documents.size,
        importedAppFiles = importedAppFiles,
        importedSensors = importedSensors
    )
}

private data class DecodedUvirSettingsDocument(
    val root: JSONObject,
    val encrypted: Boolean
)

internal data class UvirSensorSettingsImportRoute(
    val hardwareUid: String,
    val restoresAssociation: Boolean
)

internal data class UvirImportedSensorSettings(
    val hardwareUid: String,
    val settings: UvirSensorSettingsSnapshot,
    val credentials: UvirSensorCredentials,
    val wasActive: Boolean
)

internal data class UvirSettingsImportResult(
    val fileCount: Int,
    val importedAppFiles: Int,
    val importedSensors: List<UvirImportedSensorSettings>
)

internal fun resolveUvirSensorSettingsImportRoute(
    activeHardwareUid: String,
    backupHardwareUid: String,
    backupAuthToken: String,
    encrypted: Boolean
): UvirSensorSettingsImportRoute {
    val canRestoreAssociation =
        encrypted && backupHardwareUid.isNotBlank() && backupAuthToken.isNotBlank()
    return UvirSensorSettingsImportRoute(
        hardwareUid =
            if (canRestoreAssociation) backupHardwareUid.trim()
            else activeHardwareUid.trim(),
        restoresAssociation = canRestoreAssociation
    )
}

private fun readUvirSettingsText(context: Context, uri: Uri): String =
    context.contentResolver.openInputStream(uri).use { input ->
        requireNotNull(input).bufferedReader(Charsets.UTF_8).readText()
    }

private fun decodeUvirSettingsRoot(
    encodedRoot: JSONObject,
    decryptionPassword: CharArray?
): JSONObject {
    if (encodedRoot.optString("schema") != UVIR_ENCRYPTED_SETTINGS_SCHEMA) {
        return encodedRoot
    }
    require(encodedRoot.optInt("version") == UVIR_ENCRYPTED_SETTINGS_VERSION)
    require(encodedRoot.optString("encryption") == UVIR_SETTINGS_ENCRYPTION_NAME)
    require(encodedRoot.optString("kdf") == UVIR_SETTINGS_KDF_NAME)
    val password =
        decryptionPassword ?: throw UvirSettingsPasswordRequiredException()
    return JSONObject(
        decryptUvirSettingsText(
            UvirEncryptedSettingsData(
                iterations = encodedRoot.getInt("iterations"),
                salt = encodedRoot.getString("salt"),
                iv = encodedRoot.getString("iv"),
                ciphertext = encodedRoot.getString("ciphertext")
            ),
            password
        )
    )
}

private fun importAppSettings(context: Context, payload: JSONObject) {
    val preferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val editor = preferences.edit()
    preferences.all.keys
        .filter(::isTransferableAppPreference)
        .forEach(editor::remove)
    payload.keys().forEach { key ->
        if (!isTransferableAppPreference(key)) {
            return@forEach
        }
        editor.putJsonPreferenceValue(key, payload.get(key))
    }
    check(editor.commit())
}

private fun importSensorSettings(
    context: Context,
    database: UvirDatabaseHelper,
    payload: JSONObject,
    encrypted: Boolean
): UvirImportedSensorSettings {
    val activeCredentials = UvirSensorCredentialStore.load(context)
    // Association identity is honored only inside an encrypted document.
    // Old and non-sensitive backups remain portable settings templates.
    val sensitiveConnectionSettings =
        payload.optJSONObject(SENSOR_SENSITIVE_CONNECTION_SETTINGS)
            ?.takeIf { encrypted }
    val importRoute =
        resolveUvirSensorSettingsImportRoute(
            activeHardwareUid = activeCredentials.deviceId,
            backupHardwareUid =
                sensitiveConnectionSettings?.optString("device_id").orEmpty(),
            backupAuthToken =
                sensitiveConnectionSettings?.optString("auth_token").orEmpty(),
            encrypted = encrypted
        )
    val restoredCredentials =
        if (importRoute.restoresAssociation) {
            sensitiveConnectionSettings?.toRestoredSensorCredentials(payload)
        } else {
            null
        }
    val hardwareUid = importRoute.hardwareUid
    require(hardwareUid.isNotEmpty())
    val targetWasActive =
        activeCredentials.deviceId.equals(hardwareUid, ignoreCase = true)
    val existingCredentials =
        UvirSensorCredentialStore.loadForDevice(context, hardwareUid)
    val targetCredentials =
        restoredCredentials
            ?: existingCredentials
            ?: activeCredentials.takeIf { targetWasActive }
            ?: error("No restorable sensor association")
    val current =
        database.readSensorSettings(hardwareUid)
            ?: if (targetWasActive) {
                readCachedSensorSettings(context, hardwareUid)
            } else {
                defaultImportedSensorSettings(targetCredentials)
            }
    val imported = payload.toSensorSettingsSnapshot(current)
    check(
        database.upsertSensorSettings(
            hardwareUid,
            imported,
            syncedAt = current.lastSyncedAt
        )
    )
    payload.optString(SENSOR_DISPLAY_NAME)
        .takeIf(String::isNotBlank)
        ?.let { checkNotNull(database.renameSensor(hardwareUid, it)) }

    val preferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    importSensorPhonePreferences(
        preferences = preferences,
        payload = payload.optJSONObject(SENSOR_PHONE_PREFERENCES),
        importedSettings = imported,
        hardwareUid = hardwareUid,
        activeSensor = targetWasActive
    )
    if (targetWasActive) {
        check(saveSelectedSensorContext(preferences, hardwareUid))
    }
    val importedCredentials =
        targetCredentials.copy(
                wifiSsid = imported.wifiSsid,
                wifiEnabled = imported.wifiEnabled,
                bluetoothEnabled = imported.bluetoothEnabled,
                internetEnabled = imported.internetEnabled,
                internetUsePrimaryWifi = imported.internetUsePrimaryWifi,
                internetWifiSsid =
                    payload.optString(
                        "internet_wifi_ssid",
                        targetCredentials.internetWifiSsid
                    ),
                internetRelayHost = imported.internetRelayHost,
                internetRelayPort = imported.internetRelayPort,
                wifiPassword =
                    sensitiveConnectionSettings?.optString(
                        "wifi_password",
                        targetCredentials.wifiPassword
                    ) ?: targetCredentials.wifiPassword,
                internetWifiPassword =
                    sensitiveConnectionSettings?.optString(
                        "internet_wifi_password",
                        targetCredentials.internetWifiPassword
                    ) ?: targetCredentials.internetWifiPassword,
                internetMqttUsername =
                    sensitiveConnectionSettings?.optString(
                        "internet_mqtt_username",
                        targetCredentials.internetMqttUsername
                    ) ?: targetCredentials.internetMqttUsername,
                internetMqttPassword =
                    sensitiveConnectionSettings?.optString(
                        "internet_mqtt_password",
                        targetCredentials.internetMqttPassword
                    ) ?: targetCredentials.internetMqttPassword,
                bluetoothName =
                    sensitiveConnectionSettings?.optString(
                        "bluetooth_name",
                        targetCredentials.bluetoothName
                    ) ?: targetCredentials.bluetoothName,
                bluetoothPin =
                    sensitiveConnectionSettings?.optString(
                        "bluetooth_pin",
                        targetCredentials.bluetoothPin
                    ) ?: targetCredentials.bluetoothPin
            )
    return UvirImportedSensorSettings(
        hardwareUid = hardwareUid,
        settings = imported,
        credentials = importedCredentials,
        wasActive = targetWasActive
    )
}

private fun JSONObject.toRestoredSensorCredentials(
    payload: JSONObject
): UvirSensorCredentials? {
    val deviceId = optString("device_id").trim()
    val authToken = optString("auth_token")
    if (deviceId.isBlank() || authToken.isBlank()) return null
    return UvirSensorCredentials(
        deviceId = deviceId,
        firmwareVersion = optString("firmware_version"),
        authToken = authToken,
        wifiSsid = payload.optString("wifi_ssid"),
        wifiPassword = optString("wifi_password"),
        wifiHost = optString("wifi_host"),
        wifiPort = optInt("wifi_port", 8733).coerceIn(1, 65_535),
        wifiDiscoveryPort =
            optInt("wifi_discovery_port", 8732).coerceIn(1, 65_535),
        wifiEnabled = payload.optBoolean("wifi_enabled", true),
        internetEnabled = payload.optBoolean("internet_enabled", false),
        internetUsePrimaryWifi =
            payload.optBoolean("internet_use_primary_wifi", true),
        internetWifiSsid = payload.optString("internet_wifi_ssid"),
        internetWifiPassword = optString("internet_wifi_password"),
        internetRelayHost = payload.optString("internet_relay_host"),
        internetRelayPort =
            payload.optInt("internet_relay_port", DEFAULT_UVIR_RELAY_PORT)
                .coerceIn(1, 65_535),
        internetMqttUsername = optString("internet_mqtt_username"),
        internetMqttPassword = optString("internet_mqtt_password"),
        bluetoothName = optString("bluetooth_name"),
        bluetoothPin = optString("bluetooth_pin"),
        bluetoothEnabled = payload.optBoolean("bluetooth_enabled", true)
    )
}

private fun defaultImportedSensorSettings(
    credentials: UvirSensorCredentials
): UvirSensorSettingsSnapshot =
    UvirSensorSettingsSnapshot(
        schemaVersion = SENSOR_SETTINGS_SCHEMA_VERSION,
        firmwareVersion = credentials.firmwareVersion,
        sensorParameters = SensorParameters(
            autonomousRecordingEnabled = true,
            automaticShutdownEnabled = false,
            automaticShutdownSeconds = 1_800,
            statusLedEnabled = true,
            statusLedBrightness = 10,
            statusBuzzerEnabled = true,
            statusBuzzerVolume = 10,
            externalCommandEnabled = true
        ),
        acquisitionParameters = AcquisitionParameters(
            samplesPerMeasurement = 5,
            sampleSpacingMs = DEFAULT_SAMPLE_SPACING_MS,
            discardExtremes = true
        ),
        calibrationSettings = SensorCalibrationSettings(),
        alertMonitoringEnabled = false,
        alertRepeatSeconds = 30,
        alertSessionId = 0L,
        alertRules = emptyList(),
        wifiEnabled = credentials.wifiEnabled,
        bluetoothEnabled = credentials.bluetoothEnabled,
        internetEnabled = credentials.internetEnabled,
        internetUsePrimaryWifi = credentials.internetUsePrimaryWifi,
        wifiSsid = credentials.wifiSsid,
        internetRelayHost = credentials.internetRelayHost,
        internetRelayPort = credentials.internetRelayPort,
        lastSyncedAt = 0L
    )

private fun UvirSensorSettingsSnapshot.toTransferJson(): JSONObject =
    JSONObject()
        .put("settings_schema_version", schemaVersion)
        .put("settings_updated_at_ms", updatedAtMs)
        .put("firmware_version", firmwareVersion)
        .put("autonomous_recording_enabled", sensorParameters.autonomousRecordingEnabled)
        .put("automatic_shutdown_enabled", sensorParameters.automaticShutdownEnabled)
        .put("automatic_shutdown_seconds", sensorParameters.automaticShutdownSeconds)
        .put("status_led_enabled", sensorParameters.statusLedEnabled)
        .put("status_led_brightness", sensorParameters.statusLedBrightness)
        .put("status_buzzer_enabled", sensorParameters.statusBuzzerEnabled)
        .put("status_buzzer_volume", sensorParameters.statusBuzzerVolume)
        .put("external_command_enabled", sensorParameters.externalCommandEnabled)
        .put("samples_per_measurement", acquisitionParameters.samplesPerMeasurement)
        .put("sample_spacing_ms", acquisitionParameters.sampleSpacingMs)
        .put("discard_extremes", acquisitionParameters.discardExtremes)
        .put("visible_calibration_factor", calibrationSettings.visibleFactor.toDouble())
        .put("uv_calibration_factor", calibrationSettings.uvFactor.toDouble())
        .put("alert_repeat_seconds", alertRepeatSeconds)
        .put("alert_recording_enabled", alertRecordingEnabled)
        .put("alert_start_delay_seconds", alertStartDelaySeconds)
        .put("alert_duration_seconds", alertDurationSeconds)
        .put("alert_max_registrations", alertMaxRegistrations)
        .put("wifi_enabled", wifiEnabled)
        .put("bluetooth_enabled", bluetoothEnabled)
        .put("internet_enabled", internetEnabled)
        .put("internet_use_primary_wifi", internetUsePrimaryWifi)
        .put("wifi_ssid", wifiSsid)
        .put("internet_relay_host", internetRelayHost)
        .put("internet_relay_port", internetRelayPort)
        .put(
            "alert_rules",
            JSONArray().apply {
                alertRules.forEach { rule ->
                    put(
                        JSONObject()
                            .put("metric", rule.metric.name)
                            .put("enabled", rule.enabled)
                            .put("direction", rule.direction.name)
                            .put("threshold", rule.threshold.toDouble())
                    )
                }
            }
        )

private fun JSONObject.toSensorSettingsSnapshot(
    current: UvirSensorSettingsSnapshot
): UvirSensorSettingsSnapshot =
    UvirSensorSettingsSnapshot(
        schemaVersion = optInt("settings_schema_version", current.schemaVersion),
        updatedAtMs = optLong("settings_updated_at_ms", current.updatedAtMs).coerceAtLeast(0L),
        // Firmware and active-session state describe the selected device now;
        // they are not user settings that should be restored from a file.
        firmwareVersion = current.firmwareVersion,
        sensorParameters = SensorParameters(
            autonomousRecordingEnabled = getBoolean("autonomous_recording_enabled"),
            automaticShutdownEnabled = getBoolean("automatic_shutdown_enabled"),
            automaticShutdownSeconds = getInt("automatic_shutdown_seconds").coerceIn(60, 86_400),
            statusLedEnabled = getBoolean("status_led_enabled"),
            statusLedBrightness = getInt("status_led_brightness").coerceIn(1, 100),
            statusBuzzerEnabled = getBoolean("status_buzzer_enabled"),
            statusBuzzerVolume = getInt("status_buzzer_volume").coerceIn(1, 100),
            externalCommandEnabled =
                optBoolean(
                    "external_command_enabled",
                    current.sensorParameters.externalCommandEnabled
                )
        ),
        acquisitionParameters = AcquisitionParameters(
            samplesPerMeasurement = getInt("samples_per_measurement").coerceIn(1, 21),
            sampleSpacingMs = getLong("sample_spacing_ms").coerceIn(150L, 5_000L),
            discardExtremes = getBoolean("discard_extremes")
        ),
        calibrationSettings = SensorCalibrationSettings(
            visibleFactor = getDouble("visible_calibration_factor").toFloat()
                .coerceIn(MIN_SENSOR_CALIBRATION_FACTOR, MAX_SENSOR_CALIBRATION_FACTOR),
            uvFactor = getDouble("uv_calibration_factor").toFloat()
                .coerceIn(MIN_SENSOR_CALIBRATION_FACTOR, MAX_SENSOR_CALIBRATION_FACTOR)
        ),
        alertMonitoringEnabled = current.alertMonitoringEnabled,
        alertRepeatSeconds = getInt("alert_repeat_seconds").coerceIn(1, 86_400),
        alertRecordingEnabled = optBoolean("alert_recording_enabled", current.alertRecordingEnabled ?: true),
        alertStartDelaySeconds = optLong("alert_start_delay_seconds", current.alertStartDelaySeconds).coerceIn(0L, 31_536_000L),
        alertDurationSeconds = optLong("alert_duration_seconds", current.alertDurationSeconds).coerceIn(0L, 31_536_000L),
        alertMaxRegistrations = optInt("alert_max_registrations", current.alertMaxRegistrations).coerceIn(0, MAX_AUTOMATIC_ACQUISITIONS),
        alertSessionId = current.alertSessionId,
        alertRules = buildList {
            val rules = optJSONArray("alert_rules") ?: JSONArray()
            for (index in 0 until rules.length()) {
                val rule = rules.getJSONObject(index)
                val metric =
                    runCatching { ThresholdAlertMetric.valueOf(rule.getString("metric")) }
                        .getOrNull() ?: continue
                val direction =
                    runCatching { ThresholdAlertDirection.valueOf(rule.getString("direction")) }
                        .getOrNull() ?: continue
                add(
                    ThresholdAlertRule(
                        metric = metric,
                        enabled = rule.optBoolean("enabled", true),
                        direction = direction,
                        threshold = rule.getDouble("threshold").toFloat().coerceAtLeast(0f)
                    )
                )
            }
        },
        wifiEnabled = getBoolean("wifi_enabled"),
        bluetoothEnabled = getBoolean("bluetooth_enabled"),
        internetEnabled = getBoolean("internet_enabled"),
        internetUsePrimaryWifi = getBoolean("internet_use_primary_wifi"),
        wifiSsid = optString("wifi_ssid", current.wifiSsid),
        internetRelayHost =
            optString("internet_relay_host", current.internetRelayHost),
        internetRelayPort =
            optInt("internet_relay_port", current.internetRelayPort)
                .coerceIn(1, 65_535)
    )

internal fun isTransferableAppPreference(key: String): Boolean =
    key !in sensorSelectionPreferenceKeys &&
        key !in transientSettingsKeys &&
        !key.startsWith("selected_sensor_context.")

private fun SharedPreferences.sensorConfigurationToTransferJson(hardwareUid: String): JSONObject =
    JSONObject().also { payload ->
        val activeHardwareUid = getString("active_device_id", "").orEmpty()
        val isActive = normalizeSensorDeviceId(activeHardwareUid) == normalizeSensorDeviceId(hardwareUid)
        sensorConfigurationPreferenceKeys.forEach { key ->
            val storedKey = if (isActive) key else sensorContextPreferenceKey(hardwareUid, key)
            all[storedKey]?.let { value -> payload.putPreferenceValue(key, value) }
        }
    }

private fun importSensorPhonePreferences(
    preferences: SharedPreferences,
    payload: JSONObject?,
    importedSettings: UvirSensorSettingsSnapshot,
    hardwareUid: String,
    activeSensor: Boolean
) {
    fun destinationKey(key: String): String =
        if (activeSensor) key else sensorContextPreferenceKey(hardwareUid, key)
    val editor = preferences.edit()
    // Version 2 contains the complete phone-side sensor profile. Clearing
    // first also restores defaults for values that were absent in the source
    // profile. Version 1 had no such block, so its unrelated drafts survive.
    if (payload != null) {
        sensorConfigurationPreferenceKeys.forEach { key ->
            editor.remove(destinationKey(key))
        }
    }

    editor
        .putBoolean(
            destinationKey(KEY_SENSOR_AUTONOMOUS_RECORDING),
            importedSettings.sensorParameters.autonomousRecordingEnabled
        )
        .putBoolean(
            destinationKey(KEY_SENSOR_AUTOMATIC_SHUTDOWN_ENABLED),
            importedSettings.sensorParameters.automaticShutdownEnabled
        )
        .putInt(
            destinationKey(KEY_SENSOR_AUTOMATIC_SHUTDOWN_SECONDS),
            importedSettings.sensorParameters.automaticShutdownSeconds
        )
        .putBoolean(
            destinationKey(KEY_SENSOR_STATUS_LED_ENABLED),
            importedSettings.sensorParameters.statusLedEnabled
        )
        .putInt(
            destinationKey(KEY_SENSOR_STATUS_LED_BRIGHTNESS),
            importedSettings.sensorParameters.statusLedBrightness
        )
        .putBoolean(
            destinationKey(KEY_SENSOR_STATUS_BUZZER_ENABLED),
            importedSettings.sensorParameters.statusBuzzerEnabled
        )
        .putInt(
            destinationKey(KEY_SENSOR_STATUS_BUZZER_VOLUME),
            importedSettings.sensorParameters.statusBuzzerVolume
        )
        .putBoolean(
            destinationKey(KEY_SENSOR_EXTERNAL_COMMAND_ENABLED),
            importedSettings.sensorParameters.externalCommandEnabled
        )
        .putInt(
            destinationKey(KEY_SAMPLES_PER_MEASUREMENT),
            importedSettings.acquisitionParameters.samplesPerMeasurement
        )
        .putLong(
            destinationKey(KEY_SAMPLE_SPACING_MS),
            importedSettings.acquisitionParameters.sampleSpacingMs
        )
        .putBoolean(
            destinationKey(KEY_DISCARD_EXTREMES),
            importedSettings.acquisitionParameters.discardExtremes
        )
        .putFloat(
            destinationKey(KEY_SENSOR_VISIBLE_CALIBRATION_FACTOR),
            importedSettings.calibrationSettings.visibleFactor
        )
        .putFloat(
            destinationKey(KEY_SENSOR_UV_CALIBRATION_FACTOR),
            importedSettings.calibrationSettings.uvFactor
        )
        .putInt(
            destinationKey(KEY_THRESHOLD_ALERT_REPEAT_SECONDS),
            importedSettings.alertRepeatSeconds
        )
        .putBoolean(
            destinationKey(KEY_THRESHOLD_ALERT_RECORD_EVENTS),
            importedSettings.alertRecordingEnabled ?: true
        )
        .putLong(
            destinationKey(KEY_THRESHOLD_ALERT_START_DELAY_SECONDS),
            importedSettings.alertStartDelaySeconds
        )
        .putLong(
            destinationKey(KEY_THRESHOLD_ALERT_SESSION_DURATION_SECONDS),
            importedSettings.alertDurationSeconds
        )
        .putInt(
            destinationKey(KEY_THRESHOLD_ALERT_MAX_REGISTRATIONS),
            importedSettings.alertMaxRegistrations
        )

    importedSettings.alertRules.forEach { rule ->
        editor
            .putBoolean(
                destinationKey(thresholdRulePreferenceKey(rule.metric, "enabled")),
                rule.enabled
            )
            .putString(
                destinationKey(thresholdRulePreferenceKey(rule.metric, "direction")),
                rule.direction.name
            )
            .putFloat(
                destinationKey(thresholdRulePreferenceKey(rule.metric, "value")),
                rule.threshold
            )
    }
    payload?.keys()?.forEach { key ->
        if (key in sensorConfigurationPreferenceKeys) {
            editor.putJsonPreferenceValue(destinationKey(key), payload.get(key))
        }
    }
    check(editor.commit())
}

private fun JSONObject.putPreferenceValue(key: String, value: Any?) {
    val encoded =
        when (value) {
            is String -> typedPreference("string", value)
            is Boolean -> typedPreference("boolean", value)
            is Int -> typedPreference("int", value)
            is Long -> typedPreference("long", value)
            is Float -> typedPreference("float", value.toDouble())
            is Double -> typedPreference("float", value)
            is Set<*> ->
                typedPreference(
                    "string_set",
                    JSONArray(value.filterIsInstance<String>())
                )
            else -> null
        }
    if (encoded != null) put(key, encoded)
}

private fun SharedPreferences.Editor.putJsonPreferenceValue(key: String, value: Any) {
    when (value) {
        is JSONObject -> {
            when (value.getString(PREFERENCE_TYPE)) {
                "string" -> putString(key, value.getString(PREFERENCE_VALUE))
                "boolean" -> putBoolean(key, value.getBoolean(PREFERENCE_VALUE))
                "int" -> putInt(key, value.getInt(PREFERENCE_VALUE))
                "long" -> putLong(key, value.getLong(PREFERENCE_VALUE))
                "float" -> putFloat(key, value.getDouble(PREFERENCE_VALUE).toFloat())
                "string_set" ->
                    putStringSet(
                        key,
                        value.getJSONArray(PREFERENCE_VALUE).toStringSet()
                    )
                else -> error("Unsupported preference type")
            }
        }
        is String -> putString(key, value)
        is Boolean -> putBoolean(key, value)
        is Int -> putInt(key, value)
        is Long -> putLong(key, value)
        is Float -> putFloat(key, value)
        is Double -> putFloat(key, value.toFloat())
        is JSONArray ->
            putStringSet(
                key,
                buildSet {
                    for (index in 0 until value.length()) {
                        add(value.getString(index))
                    }
                }
            )
        else -> error("Unsupported preference type")
    }
}

private fun typedPreference(type: String, value: Any): JSONObject =
    JSONObject()
        .put(PREFERENCE_TYPE, type)
        .put(PREFERENCE_VALUE, value)

private fun JSONArray.toStringSet(): Set<String> =
    buildSet {
        for (index in 0 until length()) {
            add(getString(index))
        }
    }
