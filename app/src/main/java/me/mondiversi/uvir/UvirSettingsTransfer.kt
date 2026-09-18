package me.mondiversi.uvir

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

internal const val UVIR_SETTINGS_TRANSFER_SCHEMA = "uvir-settings"
internal const val UVIR_SETTINGS_TRANSFER_VERSION = 2
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
    encryptionPassword: CharArray? = null
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
        suffix = "app",
        payload = payload,
        encryptionPassword = encryptionPassword
    )
}

internal fun createUvirSensorSettingsBackup(
    context: Context,
    database: UvirDatabaseHelper,
    hardwareUid: String,
    includeSensitiveInformation: Boolean = false,
    encryptionPassword: CharArray? = null
): File {
    require(!includeSensitiveInformation || encryptionPassword != null) {
        "Sensitive settings require encryption"
    }
    val normalizedHardwareUid = hardwareUid.trim()
    require(normalizedHardwareUid.isNotEmpty()) { "No selected sensor" }
    val snapshot =
        database.readSensorSettings(normalizedHardwareUid)
            ?: readCachedSensorSettings(
                context = context,
                hardwareUid = normalizedHardwareUid
            )
    val preferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val credentials =
        UvirSensorCredentialStore.loadForDevice(context, normalizedHardwareUid)
            ?: UvirSensorCredentials(deviceId = normalizedHardwareUid)
    val payload =
        snapshot.toTransferJson()
            .put(
                SENSOR_DISPLAY_NAME,
                database.findSensorProfile(normalizedHardwareUid)?.displayName
                    ?: normalizedHardwareUid
            )
            .put(
                SENSOR_PHONE_PREFERENCES,
                preferences.sensorConfigurationToTransferJson()
            )
            // Network names are settings; passwords, pairing PINs, MQTT
            // credentials and the device authentication token are never
            // written to a plain-text export.
            .put("internet_wifi_ssid", credentials.internetWifiSsid)
    if (includeSensitiveInformation) {
        payload.put(
            SENSOR_SENSITIVE_CONNECTION_SETTINGS,
            JSONObject()
                .put("wifi_password", credentials.wifiPassword)
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
        suffix = "sensor",
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
    suffix: String,
    payload: JSONObject,
    encryptionPassword: CharArray?
): File {
    val root =
        JSONObject()
            .put("schema", UVIR_SETTINGS_TRANSFER_SCHEMA)
            .put("version", UVIR_SETTINGS_TRANSFER_VERSION)
            .put("scope", scope)
            .put("exported_at", System.currentTimeMillis())
            .put("payload", payload)
    val directory = File(context.cacheDir, "shared").apply { mkdirs() }
    val encoded =
        if (encryptionPassword == null) {
            root.toString(2)
        } else {
            val encrypted =
                encryptUvirSettingsText(root.toString(), encryptionPassword)
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
        }
    val protectedSuffix =
        if (encryptionPassword == null) suffix else "${suffix}_encrypted"
    return File(
        directory,
        "uvir_settings_${protectedSuffix}_${uvirExportTimestamp(System.currentTimeMillis())}.uvirsettings"
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
): Int {
    val roots =
        uris.map { uri ->
            decodeUvirSettingsRoot(
                JSONObject(readUvirSettingsText(context, uri)),
                decryptionPassword
            )
        }
    roots.forEach { root ->
        require(root.optString("schema") == UVIR_SETTINGS_TRANSFER_SCHEMA)
        require(
            root.optInt("version") in
                UVIR_SETTINGS_TRANSFER_MINIMUM_VERSION..UVIR_SETTINGS_TRANSFER_VERSION
        )
        require(root.getString("scope") in setOf("app", "sensor"))
        root.getJSONObject("payload")
    }
    roots.forEach { root ->
        val payload = root.getJSONObject("payload")
        when (root.getString("scope")) {
            "app" -> importAppSettings(context, payload)
            "sensor" -> importSensorSettings(context, database, payload)
            else -> error("Unsupported settings scope")
        }
    }
    return roots.size
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
    payload: JSONObject
) {
    val currentCredentials = UvirSensorCredentialStore.load(context)
    val hardwareUid = currentCredentials.deviceId.trim()
    require(hardwareUid.isNotEmpty())
    val current =
        database.readSensorSettings(hardwareUid)
            ?: readCachedSensorSettings(context, hardwareUid)
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
        importedSettings = imported
    )
    check(saveSelectedSensorContext(preferences, hardwareUid))
    val sensitiveConnectionSettings =
        payload.optJSONObject(SENSOR_SENSITIVE_CONNECTION_SETTINGS)
    check(
        UvirSensorCredentialStore.save(
            context,
            currentCredentials.copy(
                wifiSsid = imported.wifiSsid,
                wifiEnabled = imported.wifiEnabled,
                bluetoothEnabled = imported.bluetoothEnabled,
                internetEnabled = imported.internetEnabled,
                internetUsePrimaryWifi = imported.internetUsePrimaryWifi,
                internetWifiSsid =
                    payload.optString(
                        "internet_wifi_ssid",
                        currentCredentials.internetWifiSsid
                    ),
                internetRelayHost = imported.internetRelayHost,
                internetRelayPort = imported.internetRelayPort,
                wifiPassword =
                    sensitiveConnectionSettings?.optString(
                        "wifi_password",
                        currentCredentials.wifiPassword
                    ) ?: currentCredentials.wifiPassword,
                internetWifiPassword =
                    sensitiveConnectionSettings?.optString(
                        "internet_wifi_password",
                        currentCredentials.internetWifiPassword
                    ) ?: currentCredentials.internetWifiPassword,
                internetMqttUsername =
                    sensitiveConnectionSettings?.optString(
                        "internet_mqtt_username",
                        currentCredentials.internetMqttUsername
                    ) ?: currentCredentials.internetMqttUsername,
                internetMqttPassword =
                    sensitiveConnectionSettings?.optString(
                        "internet_mqtt_password",
                        currentCredentials.internetMqttPassword
                    ) ?: currentCredentials.internetMqttPassword,
                bluetoothName =
                    sensitiveConnectionSettings?.optString(
                        "bluetooth_name",
                        currentCredentials.bluetoothName
                    ) ?: currentCredentials.bluetoothName,
                bluetoothPin =
                    sensitiveConnectionSettings?.optString(
                        "bluetooth_pin",
                        currentCredentials.bluetoothPin
                    ) ?: currentCredentials.bluetoothPin
            )
        )
    )
}

private fun UvirSensorSettingsSnapshot.toTransferJson(): JSONObject =
    JSONObject()
        .put("settings_schema_version", schemaVersion)
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

private fun SharedPreferences.sensorConfigurationToTransferJson(): JSONObject =
    JSONObject().also { payload ->
        all.filterKeys { it in sensorConfigurationPreferenceKeys }
            .forEach { (key, value) -> payload.putPreferenceValue(key, value) }
    }

private fun importSensorPhonePreferences(
    preferences: SharedPreferences,
    payload: JSONObject?,
    importedSettings: UvirSensorSettingsSnapshot
) {
    val editor = preferences.edit()
    // Version 2 contains the complete phone-side sensor profile. Clearing
    // first also restores defaults for values that were absent in the source
    // profile. Version 1 had no such block, so its unrelated drafts survive.
    if (payload != null) {
        sensorConfigurationPreferenceKeys.forEach(editor::remove)
    }

    editor
        .putBoolean(
            KEY_SENSOR_AUTONOMOUS_RECORDING,
            importedSettings.sensorParameters.autonomousRecordingEnabled
        )
        .putBoolean(
            KEY_SENSOR_AUTOMATIC_SHUTDOWN_ENABLED,
            importedSettings.sensorParameters.automaticShutdownEnabled
        )
        .putInt(
            KEY_SENSOR_AUTOMATIC_SHUTDOWN_SECONDS,
            importedSettings.sensorParameters.automaticShutdownSeconds
        )
        .putBoolean(
            KEY_SENSOR_STATUS_LED_ENABLED,
            importedSettings.sensorParameters.statusLedEnabled
        )
        .putInt(
            KEY_SENSOR_STATUS_LED_BRIGHTNESS,
            importedSettings.sensorParameters.statusLedBrightness
        )
        .putBoolean(
            KEY_SENSOR_STATUS_BUZZER_ENABLED,
            importedSettings.sensorParameters.statusBuzzerEnabled
        )
        .putInt(
            KEY_SENSOR_STATUS_BUZZER_VOLUME,
            importedSettings.sensorParameters.statusBuzzerVolume
        )
        .putBoolean(
            KEY_SENSOR_EXTERNAL_COMMAND_ENABLED,
            importedSettings.sensorParameters.externalCommandEnabled
        )
        .putInt(
            KEY_SAMPLES_PER_MEASUREMENT,
            importedSettings.acquisitionParameters.samplesPerMeasurement
        )
        .putLong(
            KEY_SAMPLE_SPACING_MS,
            importedSettings.acquisitionParameters.sampleSpacingMs
        )
        .putBoolean(
            KEY_DISCARD_EXTREMES,
            importedSettings.acquisitionParameters.discardExtremes
        )
        .putFloat(
            KEY_SENSOR_VISIBLE_CALIBRATION_FACTOR,
            importedSettings.calibrationSettings.visibleFactor
        )
        .putFloat(
            KEY_SENSOR_UV_CALIBRATION_FACTOR,
            importedSettings.calibrationSettings.uvFactor
        )
        .putInt(
            KEY_THRESHOLD_ALERT_REPEAT_SECONDS,
            importedSettings.alertRepeatSeconds
        )

    importedSettings.alertRules.forEach { rule ->
        editor
            .putBoolean(
                thresholdRulePreferenceKey(rule.metric, "enabled"),
                rule.enabled
            )
            .putString(
                thresholdRulePreferenceKey(rule.metric, "direction"),
                rule.direction.name
            )
            .putFloat(
                thresholdRulePreferenceKey(rule.metric, "value"),
                rule.threshold
            )
    }
    payload?.keys()?.forEach { key ->
        if (key in sensorConfigurationPreferenceKeys) {
            editor.putJsonPreferenceValue(key, payload.get(key))
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
