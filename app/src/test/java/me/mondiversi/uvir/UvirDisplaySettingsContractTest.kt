package me.mondiversi.uvir

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class UvirDisplaySettingsContractTest {
    private fun source(name: String): String {
        val directory = listOf(File("src/main/java/me/mondiversi/uvir"),
            File("app/src/main/java/me/mondiversi/uvir")).first { it.isDirectory }
        return File(directory, name).readText()
    }

    @Test fun theSettingsListHasNoDisplayPageAndTheHeaderModeSelectorIsHiddenButPreserved() {
        val live = source("UvirLiveScreen.kt")
        val sections = source("UvirSettingsGeneralSections.kt")
        assertFalse(live.contains("UvirDisplaySettings("))
        assertFalse(sections.contains("afterFormats()"))
        assertTrue(live.contains("UvirSettingsModeSelector("))
        assertFalse(UVIR_SETTINGS_MODE_SELECTOR_VISIBLE)
        assertTrue(Regex("if \\(selectedSettingsPage == null && !UVIR_SETTINGS_MODE_SELECTOR_VISIBLE\\) \\{\\s+null")
            .containsMatchIn(live))
    }

    @Test fun warningPreferenceIsAppWideAndNeverIncludedInSensorSettings() {
        assertTrue(isTransferableAppPreference(KEY_SHOW_RTC_BATTERY_WARNING))
        assertFalse(KEY_SHOW_RTC_BATTERY_WARNING in sensorConfigurationPreferenceKeys)
        assertFalse(KEY_SHOW_RTC_BATTERY_WARNING in sensorOperationalPreferenceKeys)
        assertTrue(source("UvirSettingsTransfer.kt").contains(
            "payload.putPreferenceValue(KEY_SHOW_RTC_BATTERY_WARNING, showUvirRtcBatteryWarning(preferences))"))
    }

    @Test fun resetExplicitlyRestoresTheEnabledDefaultOnOlderAndroidVersionsToo() {
        assertTrue(source("UvirRestoreDefaults.kt").contains(".putBoolean(KEY_SHOW_RTC_BATTERY_WARNING, true)"))
        assertTrue(source("UvirDisplayPreferences.kt").contains("getBoolean(KEY_SHOW_RTC_BATTERY_WARNING, true)"))
    }

    @Test fun tabletModeStaysOnPhoneAndOnlyReportsItsAvailability() {
        val settings = source("UvirDisplaySettings.kt")
        val live = source("UvirLiveScreen.kt")
        assertTrue(settings.contains("selected = true"))
        assertTrue(settings.contains("selected = false"))
        assertTrue(settings.contains("onClick = onTabletClick"))
        assertTrue(live.contains("R.string.settings_mode_large_screen_unavailable"))
        assertTrue(live.contains("R.string.settings_mode_small_screen_active"))
        assertTrue(settings.contains("R.string.settings_mode_small_screen"))
        assertTrue(settings.contains("type = ConnectivityIconType.DISPLAY"))
        assertFalse(settings.contains("type = ConnectivityIconType.TABLET"))
        assertFalse(settings.contains("UvirSettingsPage.DISPLAY"))
    }

    @Test fun appearanceDisplayIslandKeepsSmallSelectedAndBothOptionsDisabled() {
        val display = source("UvirDisplaySettings.kt").substringAfter("internal fun UvirDisplaySettingsIsland(")
            .substringBefore("internal fun UvirDisplayCheckbox(")
        assertTrue(display.contains("R.string.settings_section_display"))
        assertTrue(display.contains("R.string.settings_display_description"))
        assertTrue(display.contains("R.string.settings_mode_small_screen to true"))
        assertTrue(display.contains("R.string.settings_mode_large_screen to false"))
        assertTrue(display.contains("enabled = false"))
        assertTrue(display.contains("onClick = {}"))
        assertTrue(display.indexOf("R.string.settings_display_unavailable") >
            display.indexOf("enabled = false"))
        assertFalse(display.contains("getSharedPreferences"))
        val radio = source("UvirSettingsGeneralSections.kt").substringAfter("internal fun UvirSettingsRadioOption(")
            .substringBefore("internal fun UvirFormatDescription(")
        assertTrue(radio.contains("enabled = enabled"))
        assertTrue(radio.contains("if (enabled) primaryText else secondaryText.copy(alpha = 0.62f)"))
    }

    @Test fun irradianceChoicesShowOnlyUnitsAndKeepOtherFormatExamples() {
        val sections = source("UvirSettingsGeneralSections.kt")
        val irradianceChoices = sections
            .substringAfter("UvirIrradianceUnit.entries.forEach { unit ->")
            .substringBefore("UvirNumericFormat.entries.forEach")
        assertTrue(irradianceChoices.contains("title = unit.symbol"))
        assertTrue(irradianceChoices.contains("selected = irradianceUnitValue == unit.storedValue"))
        assertTrue(irradianceChoices.contains("onIrradianceUnitValueChange(unit.storedValue)"))
        assertFalse(irradianceChoices.contains("example ="))
        assertTrue(sections.contains("example = formatUvirNumber(1000.23, 2, format)"))
        assertTrue(sections.contains("example = formatUvirDateOnly(dateExampleTimestamp, format)"))
        assertTrue(sections.contains("formatUvirTimeOnly("))
    }

    @Test fun miscellaneousIsLastInNotifications() {
        val notifications = source("UvirSamplingAndAlertSettings.kt")
        val acquisition = notifications.indexOf("R.string.saved_measurements")
        val alerts = notifications.indexOf("R.string.threshold_alerts_title")
        val miscellaneous = notifications.indexOf("R.string.notifications_misc_title")
        assertTrue(acquisition >= 0 && acquisition < alerts && alerts < miscellaneous)
        assertTrue(notifications.indexOf("R.string.notifications_misc_description") <
            notifications.indexOf("R.string.rtc_battery_notification_toggle"))
        assertTrue(notifications.indexOf("R.string.rtc_battery_notification_toggle") <
            notifications.indexOf("R.string.rtc_battery_notification_description"))
        assertTrue(notifications.contains("R.string.settings_section_notifications"))
        assertTrue(notifications.contains("LocalSettingsCommit provides null"))
    }

    @Test fun popupUsesTheSamePositivePreferenceAndKeepsTheCheckboxAfterTheSensorText() {
        val popup = source("UvirRtcClockWarningDialog.kt")
        assertTrue(popup.contains("checked = showWarnings"))
        assertTrue(popup.contains("onCheckedChange = onShowWarningsChange"))
        assertTrue(popup.indexOf("R.string.rtc_clock_warning_batteries_guidance") <
            popup.indexOf("R.string.rtc_clock_warning_show_each_launch"))
        assertFalse(popup.contains("checked = !showWarnings"))
    }
}
