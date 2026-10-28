package me.mondiversi.uvir

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.test.espresso.Espresso
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.*
import org.junit.Assert.*

/** Only the disposable verification package. Never opens the installed user's archive or USB sensor. */
class UvirUsbPhoneUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val runtime get() = UsbPhoneRuntimeStore.get(context)
    @Before fun isolatedOnly() {
        assertTrue(context.packageName.endsWith(".verification"))
        awaitHome()
    }
    private fun awaitHome() = compose.waitUntil(10000) {
        runCatching {
            compose.onAllNodesWithTag("home_settings_menu_button").fetchSemanticsNodes().isNotEmpty() &&
                compose.onAllNodesWithTag("uvir_launch_overlay").fetchSemanticsNodes().isEmpty()
        }.getOrDefault(false)
    }
    private fun openCaptureAction(description: Int) {
        compose.onNodeWithContentDescription(compose.activity.getString(R.string.save_measurement))
            .performSemanticsAction(SemanticsActions.OnLongClick) { it() }
        compose.onNodeWithContentDescription(compose.activity.getString(description)).performClick()
    }
    private fun captureUi(filename: String) {
        compose.waitForIdle()
        // Let the Android window finish drawing after navigation/theme/orientation changes.
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        Thread.sleep(200)
        val screenshot = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        java.io.File(context.getExternalFilesDir(null), filename).outputStream().use {
            screenshot.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        screenshot.recycle()
    }
    @After fun cleanup() {
        runtime.stopAutomatic(); runtime.stopAlerts()
        compose.waitUntil(5000) { runtime.state.value.automaticSession == null && runtime.state.value.alertSession == null }
        runtime.setSimulation(false)
        compose.waitUntil(5000) { !runtime.state.value.simulation }
        compose.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }

    @Test fun mainAndMeasurementLayoutsWorkInBothThemesAndOrientations() {
        compose.waitUntil(10000) { compose.onAllNodesWithTag("home_settings_menu_button").fetchSemanticsNodes().isNotEmpty() }
        compose.runOnIdle { runtime.setSimulation(true) }
        compose.waitUntil(10000) { runtime.state.value.ready }
        for ((orientation, expected) in listOf(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT to Configuration.ORIENTATION_PORTRAIT,
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE to Configuration.ORIENTATION_LANDSCAPE)) {
            compose.runOnIdle { compose.activity.requestedOrientation = orientation }
            compose.waitUntil(8000) { compose.activity.resources.configuration.orientation == expected }
            for (theme in listOf(UvirAppTheme.LIGHT, UvirAppTheme.DARK)) {
                compose.runOnIdle { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putString(KEY_APP_THEME, theme.storedValue).commit() }
                awaitHome()
                compose.onNodeWithTag("home_settings_menu_button").assertIsDisplayed()
                compose.onNodeWithTag("home_app_version", useUnmergedTree = true).assertIsDisplayed()
                captureUi("usb_${expected}_${theme.name}.png")
                compose.onNodeWithTag("home_settings_menu_button").performClick()
                compose.onNodeWithTag("home_settings_item_SENSOR_MEASUREMENT").performScrollTo().performClick()
                compose.onNodeWithTag("calibration_description").assertExists()
                captureUi("usb_measurement_${expected}_${theme.name}.png")
                Espresso.pressBack()
                awaitHome()
            }
        }
    }

    @Test fun menuContainsOnlyApplicableUsbAndAppPages() {
        compose.waitUntil(10000) { compose.onAllNodesWithTag("home_settings_menu_button").fetchSemanticsNodes().isNotEmpty() }
        for (page in listOf("SENSOR_MEASUREMENT", "DEBUG", "GENERAL", "LANGUAGE", "LANGUAGE_AND_FORMATS", "DATA_RESTORE", "INFO")) {
            compose.onNodeWithTag("home_settings_menu_button").performClick()
            compose.onNodeWithTag("home_settings_item_SENSOR_CONNECTION").assertDoesNotExist()
            compose.onNodeWithTag("home_settings_item_SENSOR_PARAMETERS").assertDoesNotExist()
            compose.onNodeWithTag("home_settings_item_APPEARANCE").assertDoesNotExist()
            compose.onNodeWithTag("home_settings_item_ALERTS").assertDoesNotExist()
            compose.onNodeWithTag("home_settings_item_$page").performScrollTo().performClick()
            compose.waitForIdle()
            captureUi("usb_settings_$page.png")
            if (page == "GENERAL") {
                compose.onNodeWithText(compose.activity.getString(R.string.rtc_battery_notification_toggle)).assertDoesNotExist()
                compose.onNodeWithText(compose.activity.getString(R.string.settings_theme_title)).assertExists()
                compose.onNodeWithText(compose.activity.getString(R.string.saved_measurements)).assertExists()
                compose.onNodeWithText(compose.activity.getString(R.string.threshold_alerts_title)).assertExists()
            }
            Espresso.pressBack()
            compose.onNodeWithTag("home_settings_menu_button").assertIsDisplayed()
        }
        assertEquals(UsbPhoneStatus.DETACHED, runtime.state.value.status)
        assertFalse(runtime.state.value.ready)
    }

    @Test fun dataManagementUsesSimpleExportsAndSideBySideResetTargets() {
        fun label(id: Int) = compose.activity.getString(id)
        for ((orientation, expected, theme) in listOf(
            Triple(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, Configuration.ORIENTATION_PORTRAIT, UvirAppTheme.LIGHT),
            Triple(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, Configuration.ORIENTATION_PORTRAIT, UvirAppTheme.DARK),
            Triple(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE, Configuration.ORIENTATION_LANDSCAPE, UvirAppTheme.DARK))) {
            compose.runOnIdle {
                compose.activity.requestedOrientation = orientation
                context.getSharedPreferences(PREFS_NAME, 0).edit().putString(KEY_APP_THEME, theme.storedValue).commit()
            }
            compose.waitUntil(8000) { compose.activity.resources.configuration.orientation == expected }
            awaitHome()
            compose.onNodeWithTag("home_settings_menu_button").performClick()
            compose.onNodeWithTag("home_settings_item_DATA_RESTORE").performScrollTo().performClick()
            for ((action, description, format) in listOf(
                Triple(R.string.export_settings_title, R.string.usb_settings_export_description, UvirExportFileFormat.JSON),
                Triple(R.string.export_database, R.string.export_database_dialog_description, UvirExportFileFormat.DATABASE))) {
                compose.onNodeWithContentDescription(label(action)).performScrollTo().performClick()
                compose.onNodeWithText(label(description)).assertIsDisplayed()
                compose.onNodeWithText(compose.activity.getString(R.string.export_file_count, format.displayName, 1)).assertIsDisplayed()
                compose.onAllNodes(hasSetTextAction()).assertCountEquals(0)
                compose.onAllNodes(isToggleable()).assertCountEquals(0)
                compose.onNodeWithText(uvirTestActionLabel(R.string.save)).assertIsEnabled()
                compose.onNodeWithText(uvirTestActionLabel(R.string.share)).assertIsEnabled()
                captureUi("usb_export_${format.name}_${expected}_${theme.name}.png")
                compose.onNodeWithTag("phone_backup_export_cancel").performClick()
            }
            val counters = compose.onNodeWithContentDescription(label(R.string.reset_all_records_action)).performScrollTo()
            val app = compose.onNodeWithContentDescription(label(R.string.restore_app_settings_action))
            val left = counters.fetchSemanticsNode().boundsInRoot
            val right = app.fetchSemanticsNode().boundsInRoot
            assertTrue(left.center.x < right.center.x)
            assertEquals(left.top, right.top, 1f)
            assertEquals(left.width, right.width, 1f)
            for (id in listOf(R.string.data_management_counters_button, R.string.data_management_app_button)) {
                compose.onNodeWithText(uvirTestActionLabel(id), useUnmergedTree = true)
                    .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { action ->
                        val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
                        assertTrue(action(layouts))
                        assertTrue(layouts.isNotEmpty())
                        assertFalse("Reset label must fit: ${label(id)}", layouts.single().hasVisualOverflow)
                    }
            }
            captureUi("usb_reset_${expected}_${theme.name}.png")
            counters.performClick()
            compose.onNodeWithText(label(R.string.reset_all_records_confirmation_title)).assertIsDisplayed()
            compose.onNodeWithText(label(R.string.cancel), ignoreCase = true).performClick()
            Espresso.pressBack()
        }
    }

    private fun uvirTestActionLabel(id: Int) = compose.activity.getString(id).uppercase(compose.activity.resources.configuration.locales[0])

    @Test fun generalCombinesThemeAndNotificationIslandsInBothOrientations() {
        for ((orientation, expected) in listOf(
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT to Configuration.ORIENTATION_PORTRAIT,
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE to Configuration.ORIENTATION_LANDSCAPE)) {
            compose.runOnIdle { compose.activity.requestedOrientation = orientation }
            compose.waitUntil(8000) { compose.activity.resources.configuration.orientation == expected }
            for (theme in listOf(UvirAppTheme.LIGHT, UvirAppTheme.DARK)) {
                compose.runOnIdle {
                    context.getSharedPreferences(PREFS_NAME, 0).edit().putString(KEY_APP_THEME, theme.storedValue).commit()
                }
                awaitHome()
                compose.onNodeWithTag("home_settings_menu_button").performClick()
                compose.onNodeWithTag("home_settings_item_GENERAL").performScrollTo().performClick()
                val themeTitle = compose.onNodeWithText(compose.activity.getString(R.string.settings_theme_title), useUnmergedTree = true)
                val acquisitions = compose.onNodeWithText(compose.activity.getString(R.string.saved_measurements), useUnmergedTree = true)
                val alerts = compose.onNodeWithText(compose.activity.getString(R.string.threshold_alerts_title), useUnmergedTree = true)
                themeTitle.assertExists(); acquisitions.assertExists(); alerts.assertExists()
                compose.onNodeWithText(compose.activity.getString(R.string.settings_section_sensor_parameters)).assertIsDisplayed()
                if (expected == Configuration.ORIENTATION_LANDSCAPE) {
                    val left = themeTitle.fetchSemanticsNode().boundsInRoot
                    val right = acquisitions.fetchSemanticsNode().boundsInRoot
                    assertTrue("Islands use both columns", left.center.x < right.center.x)
                    assertEquals(left.top, right.top, 2f)
                }
                captureUi("usb_general_${expected}_${theme.name}.png")
                alerts.performScrollTo().assertIsDisplayed()
                Espresso.pressBack()
            }
        }
    }

    @Test fun headerAndOfflineMeasurementControlsUsePhoneSettings() {
        compose.onNodeWithTag("home_app_title", useUnmergedTree = true).assertTextEquals(compose.activity.getString(R.string.app_name))
        compose.onNodeWithTag("home_app_version", useUnmergedTree = true).assertTextEquals("V. ${BuildConfig.VERSION_NAME}")
        compose.onNodeWithText(compose.activity.getString(R.string.home_sensor_disconnected_description)).assertIsDisplayed()
        compose.onNodeWithTag("disconnected_add_sensor").assertDoesNotExist()
        val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val previousInterval = preferences.getString("usb_auto_interval", null)
        val calibration = runtime.state.value.calibration
        try {
            openCaptureAction(R.string.automatic_acquisition)
            compose.onNodeWithTag("usb_auto_field_${R.string.usb_interval}").performTextReplacement("17")
            compose.onNodeWithContentDescription(compose.activity.getString(R.string.start)).assertIsNotEnabled()
            Espresso.closeSoftKeyboard()
            Espresso.pressBack()
            awaitHome()
            openCaptureAction(R.string.automatic_acquisition)
            compose.onNodeWithTag("usb_auto_field_${R.string.usb_interval}").assertTextContains("17")
            Espresso.pressBack()
            awaitHome()
            compose.onNodeWithTag("home_settings_menu_button").performClick()
            compose.onNodeWithTag("home_settings_item_SENSOR_MEASUREMENT").performScrollTo().performClick()
            for (index in 0..10) {
                compose.onNodeWithTag("calibration_channel_factor_$index").performScrollTo().assertIsEnabled()
                compose.onNodeWithTag("calibration_offset_$index").performScrollTo().assertIsEnabled()
            }
            for (tag in listOf("dark_offsets_uv", "dark_offsets_vis"))
                compose.onNodeWithTag(tag).performScrollTo().assertIsNotEnabled()
            compose.onNodeWithTag("optical_common_factor").performScrollTo().assertIsEnabled().performTextReplacement("1,25")
            compose.onNodeWithTag("optical_channel_0").performScrollTo().performClick()
            compose.waitUntil(5000) { runtime.state.value.calibration.windowMaster == 1.25 }
            for (index in 0..10) compose.onNodeWithTag("optical_channel_$index").performScrollTo().assertIsEnabled()
            compose.onNodeWithTag("optical_window_calculate").performScrollTo().assertIsNotEnabled()
            Espresso.closeSoftKeyboard()
            Espresso.pressBack()
            awaitHome()
            compose.onNodeWithTag("home_settings_menu_button").performClick()
            compose.onNodeWithTag("home_settings_item_SENSOR_MEASUREMENT").performScrollTo().performClick()
            compose.onNodeWithTag("optical_common_factor").performScrollTo().assertIsEnabled()
            assertEquals(1.25, runtime.state.value.calibration.windowMaster, 0.0)
        } finally { compose.runOnIdle {
            runtime.setCalibration(calibration)
            preferences.edit().apply {
                if (previousInterval == null) remove("usb_auto_interval") else putString("usb_auto_interval", previousInterval)
            }.commit()
        } }
    }

    @Test fun toolbarKeepsHistoryInTheCenterAndUsesOneExpandableCaptureControl() {
        fun bounds(description: Int) = compose.onNodeWithContentDescription(compose.activity.getString(description)).fetchSemanticsNode().boundsInRoot
        val irradiance = bounds(R.string.irradiance_view)
        val values = bounds(R.string.show_live_values)
        val history = bounds(R.string.saved_measurements)
        val alerts = bounds(R.string.threshold_alert_log_title)
        assertEquals("Both selector segments have the same width", irradiance.width, values.width, 2f)
        assertTrue(history.left > irradiance.right && alerts.right < values.left)
        assertEquals(history.center.y, values.center.y, 2f)
        compose.onNodeWithTag("home_sensor_glyph", useUnmergedTree = true).assertExists()
        assertTrue(compose.onNodeWithTag("home_capture_menu").fetchSemanticsNode().boundsInRoot.top > history.bottom)
        compose.onNodeWithContentDescription(compose.activity.getString(R.string.automatic_acquisition)).assertDoesNotExist()
        openCaptureAction(R.string.start_value_alert_session_accessibility)
        compose.onNodeWithText(compose.activity.getString(R.string.alert_registration_title)).assertIsDisplayed()
        compose.onNodeWithTag("usb_alert_registration_action").assertExists()
        val configured = loadThresholdAlertSettings(context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)).rules.count { it.enabled }
        compose.onNodeWithText(compose.activity.getString(R.string.alert_configure_values_count, configured).uppercase(java.util.Locale.getDefault()))
            .performScrollTo().performClick()
        compose.onNodeWithText(compose.activity.getString(R.string.alert_configure_values)).assertIsDisplayed()
        Espresso.pressBack()
        compose.onNodeWithText(compose.activity.getString(R.string.alert_registration_title)).assertIsDisplayed()
        Espresso.pressBack()
        awaitHome()
        compose.onNodeWithTag("home_settings_menu_button").performClick()
        compose.onNodeWithTag("home_settings_item_GENERAL").performScrollTo().performClick()
        compose.onNodeWithText(compose.activity.getString(R.string.alert_configure_values)).assertDoesNotExist()
        compose.onNodeWithTag("usb_alert_registration_action").assertDoesNotExist()
    }

    @Test fun activeAcquisitionCounterOpensItsPageBeforeStopping() {
        compose.runOnIdle { runtime.setSimulation(true) }
        compose.waitUntil(10000) { runtime.state.value.ready }
        compose.runOnIdle { runtime.startAutomatic(AutomaticAcquisitionRequest(60, "USB UI test", false, 0, false, 0, false, 0)) }
        compose.waitUntil(5000) { runtime.state.value.automaticSession != null && runtime.state.value.automaticCount > 0 }
        val count = runtime.state.value.automaticCount
        compose.onNodeWithContentDescription(compose.activity.getString(R.string.automatic_completed_count, count)).performClick()
        compose.onNodeWithTag("usb_automatic_action").assertExists()
        assertNotNull(runtime.state.value.automaticSession)
        compose.onNodeWithContentDescription(compose.activity.getString(R.string.stop_with_count, count)).performClick()
        compose.onNodeWithText(compose.activity.getString(R.string.stop_automatic_confirmation_title)).assertIsDisplayed()
        assertNotNull("Opening confirmation does not stop recording", runtime.state.value.automaticSession)
        Espresso.pressBack()
    }

    @Test fun phoneRecordsSiDataAndDisconnectNeverBackfills() {
        compose.runOnIdle { runtime.setSimulation(true) }
        compose.waitUntil(10000) { runtime.state.value.ready }
        val before = runtime.database.readAllRecords().size
        compose.runOnIdle { runtime.acquire("USB test") }
        compose.waitUntil(5000) { runtime.database.readAllRecords().size == before + 1 }
        val manual = runtime.database.readAllRecords().maxBy { it.id }
        assertEquals("USB test", manual.note)
        assertTrue(manual.sample.uva >= 0 && manual.sample.uva < .01)
        assertNull(manual.sensorId)
        compose.runOnIdle { runtime.startAutomatic(AutomaticAcquisitionRequest(1, "USB automatic test", false, 0, false, 0, true, 2)) }
        compose.waitUntil(8000) { runtime.state.value.automaticCount == 2 && runtime.state.value.automaticSession == null }
        assertEquals(before + 3, runtime.database.readAllRecords().size)
        compose.runOnIdle { runtime.startAutomatic(AutomaticAcquisitionRequest(1, "USB interrupted test", false, 0, false, 0, false, 0)) }
        compose.waitUntil(5000) { runtime.state.value.automaticCount > 0 }
        compose.runOnIdle { runtime.disconnect() }
        compose.waitUntil(5000) { runtime.state.value.automaticSession == null }
        val stopped = runtime.database.readAllRecords().size
        Thread.sleep(1500)
        assertEquals(stopped, runtime.database.readAllRecords().size)
        assertNull(runtime.state.value.sample)
    }

    @Test fun mockAlertsUsePhoneRulesAndStopAtTheLimit() {
        compose.runOnIdle { runtime.setSimulation(true) }
        compose.waitUntil(10000) { runtime.state.value.ready }
        val before = runtime.database.readThresholdAlertLog().size
        compose.runOnIdle { runtime.startAlerts(ThresholdAlertSettings(true,
            listOf(ThresholdAlertRule(ThresholdAlertMetric.UVA, true, ThresholdAlertDirection.ABOVE, 0f)),
            1, ThresholdAlertSound.SILENT, 0, maxRegistrations = 1)) }
        compose.waitUntil(5000) { runtime.state.value.alertCount == 1 && runtime.state.value.alertSession == null }
        assertEquals(before + 1, runtime.database.readThresholdAlertLog().size)
        assertNull(runtime.database.readThresholdAlertLog().maxBy { it.id }.sensorId)
    }
}
