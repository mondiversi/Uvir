package me.mondiversi.uvir

import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember

internal const val KEY_SHOW_RTC_BATTERY_WARNING = "show_rtc_battery_warning"

internal fun showUvirRtcBatteryWarning(preferences: SharedPreferences): Boolean =
    preferences.getBoolean(KEY_SHOW_RTC_BATTERY_WARNING, true)

internal fun setUvirRtcBatteryWarning(preferences: SharedPreferences, enabled: Boolean) {
    preferences.edit().putBoolean(KEY_SHOW_RTC_BATTERY_WARNING, enabled).apply()
}

/** One app-wide preference, also refreshed by imports and the app reset. */
@Composable
internal fun rememberUvirRtcBatteryWarning(preferences: SharedPreferences): State<Boolean> {
    val enabled = remember(preferences) { mutableStateOf(showUvirRtcBatteryWarning(preferences)) }
    DisposableEffect(preferences) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == null || key == KEY_SHOW_RTC_BATTERY_WARNING) {
                enabled.value = showUvirRtcBatteryWarning(preferences)
            }
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        // Avoid missing an import between composition and listener registration.
        enabled.value = showUvirRtcBatteryWarning(preferences)
        onDispose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return enabled
}
