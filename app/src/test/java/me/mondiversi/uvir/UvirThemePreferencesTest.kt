package me.mondiversi.uvir

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class UvirThemePreferencesTest {
    private fun source(name: String): String {
        val directory = listOf(File("src/main/java/me/mondiversi/uvir"),
            File("app/src/main/java/me/mondiversi/uvir")).first { it.isDirectory }
        return File(directory, name).readText()
    }

    @Test fun freshInstallAndUnknownValuesFollowTheDevice() {
        assertEquals(UvirAppTheme.DEFAULT, UvirAppTheme.fromStoredValue(null))
        assertEquals(UvirAppTheme.DEFAULT, UvirAppTheme.fromStoredValue(""))
        assertEquals(UvirAppTheme.DEFAULT, UvirAppTheme.fromStoredValue("unsupported"))
        assertFalse(UvirAppTheme.DEFAULT.isDark(false))
        assertTrue(UvirAppTheme.DEFAULT.isDark(true))
    }

    @Test fun overridesIgnoreTheSystemAndAllChoicesRoundTrip() {
        for (theme in UvirAppTheme.entries) {
            assertEquals(theme, UvirAppTheme.fromStoredValue(theme.storedValue))
        }
        for (systemDark in listOf(false, true)) {
            assertFalse(UvirAppTheme.LIGHT.isDark(systemDark))
            assertTrue(UvirAppTheme.DARK.isDark(systemDark))
        }
    }

    @Test fun themeIsAnAppSettingAndBackupsIncludeItsUntouchedDefault() {
        assertTrue(isTransferableAppPreference(KEY_APP_THEME))
        assertFalse(KEY_APP_THEME in sensorConfigurationPreferenceKeys)
        assertFalse(KEY_APP_THEME in sensorOperationalPreferenceKeys)
        assertTrue(source("UvirSettingsTransfer.kt").contains(
            "payload.putPreferenceValue(KEY_APP_THEME, readUvirAppTheme(preferences).storedValue)"))
        assertTrue(source("UvirRestoreDefaults.kt").contains(
            ".putString(KEY_APP_THEME, UvirAppTheme.DEFAULT.storedValue)"))
    }

    @Test fun appearanceContainsThemeThenDisplayAndFormatsRetainTheirExistingRoute() {
        val sections = source("UvirSettingsGeneralSections.kt")
        val appearance = sections.substringAfter("settingsPage = UvirSettingsPage.APPEARANCE,")
            .substringBefore("    notificationsSection()")
        assertTrue(appearance.contains("R.string.settings_section_appearance"))
        assertTrue(appearance.contains("titleIcon = ConnectivityIconType.PALETTE"))
        assertTrue(appearance.contains("UvirThemeSettingsIsland("))
        assertTrue(appearance.indexOf("UvirThemeSettingsIsland(") <
            appearance.indexOf("UvirDisplaySettingsIsland("))
        assertFalse(appearance.contains("R.string.irradiance_view"))
        val formats = sections.substringAfter("settingsPage = UvirSettingsPage.LANGUAGE_AND_FORMATS,")
            .substringBefore("val settingsNavigation =")
        assertTrue(formats.contains("titleIcon = ConnectivityIconType.NUMERIC_FORMAT"))
        assertTrue(formats.contains("UVIR_NUMERIC_FORMAT_EXPANDED_KEY"))
        assertFalse(formats.contains("UvirThemeSettingsIsland("))
        assertFalse(formats.contains("UvirDisplaySettingsIsland("))
        for (title in listOf("irradiance_view", "format_numbers_heading", "format_date_time_heading", "format_time_heading")) {
            assertTrue(formats.contains("R.string.$title"))
        }
        assertTrue(source("UvirThemeSettingsIsland.kt").contains("LocalSettingsCommit provides null"))
        // Theme presentation must not tear down transports or the settings route.
        val provider = source("UvirThemePreferences.kt")
        assertFalse(provider.contains("LocalContext provides"))
        assertFalse(provider.contains(".recreate("))
        assertTrue(source("MainActivity.kt").indexOf("UvirThemeOverride(") <
            source("MainActivity.kt").indexOf("UvirActionTheme {"))
    }

    @Test fun appSectionsUseAppearanceNotificationsLanguageFormatsOrderAndPreserveNotificationAutosave() {
        val sections = source("UvirSettingsGeneralSections.kt")
        val appearance = sections.indexOf("settingsPage = UvirSettingsPage.APPEARANCE,")
        val notifications = sections.indexOf("    notificationsSection()")
        val language = sections.indexOf("settingsPage = UvirSettingsPage.LANGUAGE,")
        val formats = sections.indexOf("settingsPage = UvirSettingsPage.LANGUAGE_AND_FORMATS,")
        assertTrue(appearance >= 0 && appearance < notifications && notifications < language && language < formats)
        val live = source("UvirLiveScreen.kt")
        assertTrue(live.substringAfter("UvirSettingsPage.APPEARANCE ->").trimStart()
            .startsWith("R.string.settings_section_appearance"))
        assertTrue(live.contains("val appSettingsCommit = LocalSettingsCommit.current"))
        assertTrue(live.contains("CompositionLocalProvider(LocalSettingsCommit provides appSettingsCommit)"))
        assertEquals(1, Regex("renderSamplingAndAlerts\\(false, true\\)").findAll(live).count())
        val slot = live.substringAfter("notificationsSection = {")
        assertTrue(slot.contains("SettingsAutoSaveGroup(SettingsSaveGroup.ALERTS)"))
        assertTrue(source("UvirConnectivityIcons.kt").contains("ConnectivityIconType.PALETTE ->"))
    }
}
