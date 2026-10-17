package me.mondiversi.uvir

import android.content.SharedPreferences

private const val RETIRED_CAMERA_CONTEXT = "__camera_source__"

/** Retired phone-only source state is never restored by a settings backup. */
internal fun isRetiredCameraPreference(key: String): Boolean =
    key.startsWith("camera_") ||
        key.startsWith(sensorContextPreferenceKey(RETIRED_CAMERA_CONTEXT, ""))

/**
 * Upgrade-only cleanup, not a selectable source. Restore the last physical UI
 * context atomically, without touching credentials, live chip jobs or records.
 * A prepared diagnostic simulation owns the global values until it is disabled.
 */
@Synchronized
internal fun removeRetiredCameraSelection(preferences: SharedPreferences, realDeviceId: String) {
    val values = preferences.all
    val retiredKeys = values.keys.filter(::isRetiredCameraPreference)
    if (retiredKeys.isEmpty()) return
    val editor = preferences.edit()
    if (preferences.getBoolean("camera_context_prepared", false) &&
        !preferences.getBoolean(KEY_SIMULATION_CONTEXT_PREPARED, false)) {
        sensorSelectionPreferenceKeys.forEach { key ->
            editor.remove(key)
            if (realDeviceId.isNotBlank()) {
                values[sensorContextPreferenceKey(realDeviceId, key)]?.let {
                    editor.putSensorContextValue(key, it)
                }
            }
        }
    }
    retiredKeys.forEach(editor::remove)
    check(editor.commit())
}
