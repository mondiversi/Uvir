package me.mondiversi.uvir

import android.content.SharedPreferences
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources

internal const val KEY_APP_THEME = "app_theme"

// Preserve the device theme before applying the app-only override.
internal val LocalUvirSystemDarkTheme = staticCompositionLocalOf<Boolean?> { null }

internal enum class UvirAppTheme(val storedValue: String) {
    DEFAULT("default"), LIGHT("light"), DARK("dark");

    fun isDark(systemDark: Boolean): Boolean = when (this) {
        DEFAULT -> systemDark
        LIGHT -> false
        DARK -> true
    }

    companion object {
        fun fromStoredValue(value: String?): UvirAppTheme =
            entries.firstOrNull { it.storedValue == value } ?: DEFAULT
    }
}

internal fun readUvirAppTheme(preferences: SharedPreferences): UvirAppTheme =
    UvirAppTheme.fromStoredValue(preferences.getString(KEY_APP_THEME, null))

internal fun setUvirAppTheme(preferences: SharedPreferences, theme: UvirAppTheme) {
    preferences.edit().putString(KEY_APP_THEME, theme.storedValue).apply()
}

/** Imports and resets use the same preference as the live theme selector. */
@Composable
internal fun rememberUvirAppTheme(preferences: SharedPreferences): State<UvirAppTheme> {
    val theme = remember(preferences) { mutableStateOf(readUvirAppTheme(preferences)) }
    DisposableEffect(preferences) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == null || key == KEY_APP_THEME) theme.value = readUvirAppTheme(preferences)
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        theme.value = readUvirAppTheme(preferences)
        onDispose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return theme
}

internal fun uvirThemeConfiguration(source: Configuration, theme: UvirAppTheme): Configuration =
    Configuration(source).apply {
        if (theme != UvirAppTheme.DEFAULT) {
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                if (theme == UvirAppTheme.DARK) Configuration.UI_MODE_NIGHT_YES
                else Configuration.UI_MODE_NIGHT_NO
        }
    }

/** Change only presentation. Keep the activity/context, navigation and sensor jobs alive. */
@Composable
internal fun UvirThemeOverride(preferences: SharedPreferences, content: @Composable () -> Unit) {
    val theme by rememberUvirAppTheme(preferences)
    val systemConfiguration = LocalConfiguration.current
    val originalResources = LocalResources.current
    val context = LocalContext.current
    val configuration = remember(systemConfiguration, theme) {
        uvirThemeConfiguration(systemConfiguration, theme)
    }
    val resources = remember(context, originalResources, configuration, theme) {
        if (theme == UvirAppTheme.DEFAULT) originalResources
        else context.createConfigurationContext(configuration).resources
    }
    // isSystemInDarkTheme() reads LocalConfiguration, so all existing screens,
    // dialogs, custom icons and launch animations receive the same override.
    // LocalContext is deliberately unchanged: theme changes are not sensor switches.
    CompositionLocalProvider(
        LocalUvirSystemDarkTheme provides
            (systemConfiguration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES),
        LocalConfiguration provides configuration,
        LocalResources provides resources,
        content = content
    )
}
