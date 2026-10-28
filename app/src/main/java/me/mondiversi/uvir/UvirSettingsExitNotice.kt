package me.mondiversi.uvir

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources

/** Retains pending confirmation across configuration changes; successful hardware writes remain asynchronous. */
@Composable
internal fun rememberUvirSettingsExitNotice(page: String?): UvirSettingsExitConfirmation {
    val context = LocalContext.current
    val resources = LocalResources.current
    val emit by rememberUpdatedState({ showUvirBottomMessage(context, resources.getString(R.string.parameters_saved)) })
    val confirmation = rememberSaveable(page, saver = Saver<UvirSettingsExitConfirmation, Boolean>(
        save = { it.changed }, restore = { UvirSettingsExitConfirmation(it) }
    )) { UvirSettingsExitConfirmation() }
    val transition = remember { UvirSettingsExitTransition() }
    SideEffect { transition.updatePage(page, confirmation) { emit() } }
    val preferences = remember(context) { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }
    DisposableEffect(confirmation, preferences) {
        val entered = page
        // These local presentation fields do not pass through the sensor save queue.
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (entered != null && key in setOf(KEY_APP_THEME, KEY_SHOW_RTC_BATTERY_WARNING)) confirmation.saved()
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            preferences.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }
    return confirmation
}
