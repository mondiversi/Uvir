package me.mondiversi.uvir

import android.content.SharedPreferences
import java.util.Locale

internal const val ASSOCIATED_SENSOR_IDS_KEY = "associated_sensor_ids"
internal const val KEY_MANUAL_ACQUISITION_NOTE = "manual_acquisition_note"
internal const val ASSOCIATE_NEW_SENSOR_REQUEST = "__associate_new_sensor__"

internal fun normalizeSensorDeviceId(deviceId: String): String =
    deviceId.trim().lowercase(Locale.ROOT)

/** Old saved profiles may be dissociated: migrate only the current association. */
internal fun associatedSensorDeviceIds(preferences: SharedPreferences): Set<String> {
    if (preferences.contains(ASSOCIATED_SENSOR_IDS_KEY)) {
        return preferences.getStringSet(ASSOCIATED_SENSOR_IDS_KEY, emptySet()).orEmpty()
            .map(::normalizeSensorDeviceId).filter(String::isNotBlank).toSet()
    }
    val activeId = preferences.getString("active_device_id", "").orEmpty()
        .ifBlank { preferences.getString("device_id", "").orEmpty() }
    return setOf(normalizeSensorDeviceId(activeId)).filter(String::isNotBlank).toSet()
}

private const val SENSOR_CONTEXT_PREFIX = "selected_sensor_context."

internal val sensorSelectionPreferenceKeys: List<String> =
    (sensorOperationalPreferenceKeys + listOf(
        KEY_SENSOR_CONNECTION_MODE, KEY_LAST_WIRELESS_SENSOR_CONNECTION_MODE,
        KEY_SAMPLES_PER_MEASUREMENT, KEY_SAMPLE_SPACING_MS, KEY_DISCARD_EXTREMES,
        KEY_SENSOR_AUTONOMOUS_RECORDING, KEY_SENSOR_AUTOMATIC_SHUTDOWN_ENABLED,
        KEY_SENSOR_AUTOMATIC_SHUTDOWN_SECONDS, KEY_SENSOR_STATUS_LED_ENABLED,
        KEY_SENSOR_STATUS_LED_BRIGHTNESS, KEY_SENSOR_STATUS_BUZZER_ENABLED,
        KEY_SENSOR_STATUS_BUZZER_VOLUME, KEY_SENSOR_VISIBLE_CALIBRATION_FACTOR,
        KEY_SENSOR_UV_CALIBRATION_FACTOR, KEY_MANUAL_SAVE_MODE, KEY_MANUAL_ACQUISITION_NOTE,
        KEY_AUTO_INTERVAL_SECONDS, KEY_AUTO_NOTE, KEY_AUTO_USE_START_DELAY,
        KEY_AUTO_START_DELAY_SECONDS, KEY_AUTO_USE_DURATION, KEY_AUTO_DURATION_SECONDS,
        KEY_AUTO_LIMIT_ENABLED, KEY_AUTO_MAX_COUNT, KEY_THRESHOLD_ALERT_NOTE,
        KEY_AUTO_CONDITIONAL_ENABLED, KEY_AUTO_CONDITIONAL_MATCH, KEY_AUTO_CONDITIONAL_ACTION,
        KEY_AUTO_CONDITIONAL_RULES, KEY_AUTO_FIRST_ALLOWED_MS,
        KEY_THRESHOLD_ALERT_REPEAT_SECONDS, KEY_THRESHOLD_ALERT_DURATION_SECONDS,
        KEY_THRESHOLD_ALERT_SOUND, KEY_THRESHOLD_ALERT_VOLUME,
        KEY_THRESHOLD_ALERT_CHANNEL, KEY_THRESHOLD_ALERT_DIRECTION, KEY_THRESHOLD_ALERT_VALUE
    ) + ThresholdAlertMetric.entries.flatMap { metric ->
        listOf("value", "direction").map { thresholdRulePreferenceKey(metric, it) }
    }).distinct()

private fun sensorContextPrefix(deviceId: String): String =
    SENSOR_CONTEXT_PREFIX + normalizeSensorDeviceId(deviceId) + "."

/** Phone-only drafts and last-known operational state, never a command to the chip. */
internal fun saveSelectedSensorContext(preferences: SharedPreferences, deviceId: String): Boolean {
    if (deviceId.isBlank()) return true
    val values = preferences.all
    val prefix = sensorContextPrefix(deviceId)
    val editor = preferences.edit()
    sensorSelectionPreferenceKeys.forEach { key ->
        editor.remove(prefix + key)
        values[key]?.let { editor.putSensorContextValue(prefix + key, it) }
    }
    return editor.commit()
}

internal fun restoreSelectedSensorContext(preferences: SharedPreferences, deviceId: String): Boolean {
    val values = preferences.all
    val prefix = sensorContextPrefix(deviceId)
    val editor = preferences.edit()
    sensorSelectionPreferenceKeys.forEach { key ->
        editor.remove(key)
        if (deviceId.isNotBlank()) {
            values[prefix + key]?.let { editor.putSensorContextValue(key, it) }
        }
    }
    return editor.commit()
}

internal fun forgetSelectedSensorOperationalContext(preferences: SharedPreferences, deviceId: String): Boolean {
    val editor = preferences.edit()
    sensorOperationalPreferenceKeys.forEach { editor.remove(sensorContextPrefix(deviceId) + it) }
    return editor.commit()
}

/** Deleted session IDs must not reappear from a previously selected sensor. */
internal fun clearCachedSensorOperationalContexts(preferences: SharedPreferences): Boolean {
    val editor = preferences.edit()
    preferences.all.keys.filter { key ->
        key.startsWith(SENSOR_CONTEXT_PREFIX) &&
            sensorOperationalPreferenceKeys.any { key.endsWith("." + it) }
    }.forEach(editor::remove)
    return editor.commit()
}

private fun SharedPreferences.Editor.putSensorContextValue(key: String, value: Any) {
    when (value) {
        is String -> putString(key, value)
        is Boolean -> putBoolean(key, value)
        is Int -> putInt(key, value)
        is Long -> putLong(key, value)
        is Float -> putFloat(key, value)
        is Set<*> -> putStringSet(key, value.filterIsInstance<String>().toSet())
        else -> error("Unsupported sensor preference type")
    }
}
