package me.mondiversi.uvir

import android.content.SharedPreferences

internal data class UvirSensorSimulationSettings(
    val enabled: Boolean = false,
    val outOfRange: Boolean = false
)

/** Phone-only diagnostic settings, keyed by hardware UID, never sent to the firmware. */
internal object UvirSensorSimulationSettingsStore {
    internal fun preferenceKey(deviceId: String, name: String): String =
        "sensor_simulation.${normalizeSensorDeviceId(deviceId).ifBlank { "__demo__" }}.$name"

    /** Preserve an old global choice for its current sensor, not for every association. */
    @Synchronized
    private fun migrateLegacy(preferences: SharedPreferences) {
        if (!preferences.contains(KEY_USE_FAKE_SENSOR_DATA) &&
            !preferences.contains(KEY_FAKE_SENSOR_OUT_OF_RANGE)) return
        val owner = preferences.getString("active_device_id", "").orEmpty()
            .ifBlank { preferences.getString("device_id", "").orEmpty() }
        val enabledKey = preferenceKey(owner, KEY_USE_FAKE_SENSOR_DATA)
        val outOfRangeKey = preferenceKey(owner, KEY_FAKE_SENSOR_OUT_OF_RANGE)
        val editor = preferences.edit()
        if (!preferences.contains(enabledKey)) {
            editor.putBoolean(enabledKey, preferences.getBoolean(KEY_USE_FAKE_SENSOR_DATA, true))
        }
        if (!preferences.contains(outOfRangeKey)) {
            editor.putBoolean(outOfRangeKey, preferences.getBoolean(KEY_FAKE_SENSOR_OUT_OF_RANGE, false))
        }
        editor.remove(KEY_USE_FAKE_SENSOR_DATA).remove(KEY_FAKE_SENSOR_OUT_OF_RANGE).apply()
    }

    fun load(preferences: SharedPreferences, deviceId: String): UvirSensorSimulationSettings {
        migrateLegacy(preferences)
        val firstRunDemo = deviceId.isBlank() && associatedSensorDeviceIds(preferences).isEmpty() &&
            !preferences.getBoolean("sensor_selection_disabled", false)
        return UvirSensorSimulationSettings(
            // Preserve the first-run demo without enabling simulation on newly associated hardware.
            enabled = preferences.getBoolean(preferenceKey(deviceId, KEY_USE_FAKE_SENSOR_DATA), firstRunDemo),
            outOfRange = preferences.getBoolean(preferenceKey(deviceId, KEY_FAKE_SENSOR_OUT_OF_RANGE), false)
        )
    }

    fun setEnabled(preferences: SharedPreferences, deviceId: String, enabled: Boolean) {
        migrateLegacy(preferences)
        preferences.edit().putBoolean(preferenceKey(deviceId, KEY_USE_FAKE_SENSOR_DATA), enabled).apply()
    }

    fun setOutOfRange(preferences: SharedPreferences, deviceId: String, enabled: Boolean) {
        migrateLegacy(preferences)
        preferences.edit().putBoolean(preferenceKey(deviceId, KEY_FAKE_SENSOR_OUT_OF_RANGE), enabled).apply()
    }
}

/** The no-sensor selection pauses all transports; simulation pauses only its own hardware. */
internal fun uvirSensorRealTransportEnabled(
    preferences: SharedPreferences,
    deviceId: String,
    selectionDisabled: Boolean
): Boolean = !selectionDisabled && !UvirSensorSimulationSettingsStore.load(preferences, deviceId).enabled
