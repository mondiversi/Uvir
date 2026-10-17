package me.mondiversi.uvir

import android.content.SharedPreferences

internal data class UvirSensorSimulationSettings(
    val enabled: Boolean = false,
    val outOfRange: Boolean = false
)

// A preference namespace only, NEVER a hardware UID, association or database sensor ID.
internal const val SIMULATION_SENSOR_CONTEXT = "__demo__"
internal const val KEY_SIMULATION_CONTEXT_ACTIVE = "simulation_context_active"
internal const val KEY_SIMULATION_CONTEXT_PREPARED = "simulation_context_prepared"
private const val KEY_SIMULATION_OUT_OF_RANGE = "simulation_context_out_of_range"

/** Simulation is a phone-only context, independent of every real sensor. */
internal object UvirSensorSimulationSettingsStore {
    internal fun preferenceKey(deviceId: String, name: String): String =
        "sensor_simulation.${normalizeSensorDeviceId(deviceId).ifBlank { "__demo__" }}.$name"

    /** Only the previous active choice is migrated; stale choices of other UIDs stay inactive. */
    @Synchronized
    fun migrateLegacy(preferences: SharedPreferences, selectedDeviceId: String) {
        if (preferences.contains(KEY_SIMULATION_CONTEXT_ACTIVE)) return
        val enabledKey = preferenceKey(selectedDeviceId, KEY_USE_FAKE_SENSOR_DATA)
        val rangeKey = preferenceKey(selectedDeviceId, KEY_FAKE_SENSOR_OUT_OF_RANGE)
        val demoEnabledKey = preferenceKey("", KEY_USE_FAKE_SENSOR_DATA)
        val demoRangeKey = preferenceKey("", KEY_FAKE_SENSOR_OUT_OF_RANGE)
        val hasLegacy = listOf(enabledKey, rangeKey, demoEnabledKey, demoRangeKey,
            KEY_USE_FAKE_SENSOR_DATA, KEY_FAKE_SENSOR_OUT_OF_RANGE).any(preferences::contains)
        if (!hasLegacy) return
        preferences.edit()
            .putBoolean(KEY_SIMULATION_CONTEXT_ACTIVE, preferences.getBoolean(enabledKey,
                preferences.getBoolean(demoEnabledKey, preferences.getBoolean(KEY_USE_FAKE_SENSOR_DATA, false))))
            .putBoolean(KEY_SIMULATION_OUT_OF_RANGE, preferences.getBoolean(rangeKey,
                preferences.getBoolean(demoRangeKey, preferences.getBoolean(KEY_FAKE_SENSOR_OUT_OF_RANGE, false))))
            .remove(KEY_USE_FAKE_SENSOR_DATA).remove(KEY_FAKE_SENSOR_OUT_OF_RANGE).commit()
    }

    @Suppress("UNUSED_PARAMETER")
    fun load(preferences: SharedPreferences, deviceId: String = ""): UvirSensorSimulationSettings {
        return UvirSensorSimulationSettings(
            // Simulation is opt-in, including first launch with no associated hardware.
            enabled = preferences.getBoolean(KEY_SIMULATION_CONTEXT_ACTIVE, false),
            outOfRange = preferences.getBoolean(KEY_SIMULATION_OUT_OF_RANGE, false)
        )
    }

    @Suppress("UNUSED_PARAMETER")
    fun setEnabled(preferences: SharedPreferences, deviceId: String, enabled: Boolean) {
        preferences.edit().putBoolean(KEY_SIMULATION_CONTEXT_ACTIVE, enabled).commit()
    }

    @Suppress("UNUSED_PARAMETER")
    fun setOutOfRange(preferences: SharedPreferences, deviceId: String, enabled: Boolean) {
        preferences.edit().putBoolean(KEY_SIMULATION_OUT_OF_RANGE, enabled).apply()
    }
}

/** The hardware stays associated and connected while its UI context is hidden. */
internal fun selectedUiSensorDeviceId(preferences: SharedPreferences, realDeviceId: String): String =
    if (UvirSensorSimulationSettingsStore.load(preferences).enabled) "" else realDeviceId

/** Called only after the old UI's saving effects have been disposed. */
@Synchronized
internal fun switchSimulationContext(
    preferences: SharedPreferences, realDeviceId: String, enabled: Boolean
) {
    val wasPrepared = preferences.getBoolean(KEY_SIMULATION_CONTEXT_PREPARED, false)
    val editor = preferences.edit()
    if (wasPrepared != enabled) {
        val values = preferences.all
        val source = if (wasPrepared) SIMULATION_SENSOR_CONTEXT else realDeviceId
        val target = if (enabled) SIMULATION_SENSOR_CONTEXT else realDeviceId
        val reportedKeys = setOf(KEY_AUTO_SESSION_ID, KEY_AUTO_ENABLED, KEY_AUTO_COMPLETED_COUNT,
            KEY_AUTO_NEXT_SAVE_MS, KEY_AUTO_END_MS, KEY_AUTO_CONDITIONAL_PLAN, KEY_AUTO_CONDITIONAL_WAITING)
        sensorSelectionPreferenceKeys.forEach { key ->
            val savedKey = sensorContextPreferenceKey(source, key)
            if (source.isNotBlank() && (wasPrepared || key !in reportedKeys || !values.containsKey(savedKey))) {
                editor.remove(savedKey)
                values[key]?.let { editor.putSensorContextValue(savedKey, it) }
            }
            editor.remove(key)
            if (target.isNotBlank()) values[sensorContextPreferenceKey(target, key)]?.let {
                editor.putSensorContextValue(key, it)
            }
        }
    }
    // Switch ownership and values in one commit, so real receivers cannot see demo job IDs.
    check(editor.putBoolean(KEY_SIMULATION_CONTEXT_ACTIVE, enabled)
        .putBoolean(KEY_SIMULATION_CONTEXT_PREPARED, enabled).commit())
}

/** Simulation must never pause a hardware transport or its autonomous job. */
@Suppress("UNUSED_PARAMETER")
internal fun uvirSensorRealTransportEnabled(
    preferences: SharedPreferences,
    deviceId: String,
    selectionDisabled: Boolean
): Boolean = !selectionDisabled
