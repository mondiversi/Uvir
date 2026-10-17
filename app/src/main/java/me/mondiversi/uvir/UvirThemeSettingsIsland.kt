package me.mondiversi.uvir

import android.content.Context
import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

@Composable
internal fun UvirThemeSettingsIsland(cardColor: Color, primaryText: Color, secondaryText: Color) {
    val context = LocalContext.current
    val preferences = remember(context) { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }
    val selected by rememberUvirAppTheme(preferences)
    val systemDark = LocalUvirSystemDarkTheme.current ?:
        (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES)
    val defaultExample = stringResource(
        if (systemDark) R.string.settings_theme_dark else R.string.settings_theme_light
    )
    SettingsIsland(containerColor = cardColor, contentColor = primaryText) {
        SettingsIslandHeader(
            title = stringResource(R.string.settings_theme_title),
            titleColor = primaryText,
            titleIcon = ConnectivityIconType.THEME
        )
        Column(
            modifier = Modifier.selectableGroup(),
            verticalArrangement = Arrangement.spacedBy(UvirSettingsChoiceSpacing)
        ) {
            UvirFormatDescription(stringResource(R.string.settings_theme_description), secondaryText)
            Spacer(Modifier.height(4.dp))
            // This app-only preference must not commit or resend sensor settings.
            CompositionLocalProvider(LocalSettingsCommit provides null) {
                UvirAppTheme.entries.forEach { theme ->
                    UvirSettingsRadioOption(
                        selected = selected == theme,
                        title = stringResource(when (theme) {
                            UvirAppTheme.DEFAULT -> R.string.settings_theme_default
                            UvirAppTheme.LIGHT -> R.string.settings_theme_light
                            UvirAppTheme.DARK -> R.string.settings_theme_dark
                        }),
                        example = defaultExample.takeIf { theme == UvirAppTheme.DEFAULT },
                        primaryText = primaryText,
                        secondaryText = secondaryText,
                        onClick = { if (selected != theme) setUvirAppTheme(preferences, theme) }
                    )
                }
            }
        }
    }
}
