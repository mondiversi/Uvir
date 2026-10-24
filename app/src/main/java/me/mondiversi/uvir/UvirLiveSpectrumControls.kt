package me.mondiversi.uvir

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

internal const val KEY_MAIN_HIDDEN_SPECTRUM_CHANNELS = "main_hidden_spectrum_channels"

internal fun loadUvirMainHiddenChannels(preferences: SharedPreferences): Set<ThresholdAlertMetric> =
    decodeUvirMainHiddenChannels(runCatching {
        preferences.getStringSet(KEY_MAIN_HIDDEN_SPECTRUM_CHANNELS, emptySet()).orEmpty().toSet()
    }.getOrDefault(emptySet()))

/** Only channel visibility is durable. Percentage bases belong to the current Main View visit. */
private class MainSpectrumDisplayState(private val preferences: SharedPreferences) : MutableState<UvirLiveSpectrumDisplay> {
    private var current by mutableStateOf(UvirLiveSpectrumDisplay(excludedChannels = loadUvirMainHiddenChannels(preferences)))

    override var value: UvirLiveSpectrumDisplay
        get() = current
        set(value) {
            val hidden = decodeUvirMainHiddenChannels(value.excludedChannels.map { it.name }.toSet())
            val changed = current.excludedChannels != hidden
            current = value.copy(excludedChannels = hidden)
            if (changed) preferences.edit()
                .putStringSet(KEY_MAIN_HIDDEN_SPECTRUM_CHANNELS, hidden.map { it.name }.toSet()).apply()
        }

    fun reloadVisibility() {
        current = current.copy(excludedChannels = loadUvirMainHiddenChannels(preferences))
    }

    override fun component1(): UvirLiveSpectrumDisplay = value
    override fun component2(): (UvirLiveSpectrumDisplay) -> Unit = { value = it }
}

@Composable
internal fun rememberUvirLiveSpectrumDisplay(sensorKey: String): MutableState<UvirLiveSpectrumDisplay> {
    val context = LocalContext.current
    val preferences = remember(context) { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }
    val state = remember(preferences, sensorKey) { MainSpectrumDisplayState(preferences) }
    DisposableEffect(preferences, state) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == null || key == KEY_MAIN_HIDDEN_SPECTRUM_CHANNELS) state.reloadVisibility()
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        onDispose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return state
}
