package me.mondiversi.uvir

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
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
    @After fun cleanup() {
        runtime.stopAutomatic(); runtime.stopAlerts(); runtime.setSimulation(false)
        compose.waitUntil(5000) { !runtime.state.value.simulation && runtime.state.value.automaticSession == null && runtime.state.value.alertSession == null }
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
                val image = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
                java.io.File(context.getExternalFilesDir(null), "usb_${expected}_${theme.name}.png").outputStream().use {
                    image.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
                image.recycle()
                compose.onNodeWithTag("home_settings_menu_button").performClick()
                compose.onNodeWithTag("home_settings_item_SENSOR_MEASUREMENT").performScrollTo().performClick()
                compose.onNodeWithTag("calibration_description").assertExists()
                Espresso.pressBack()
                awaitHome()
            }
        }
    }

    @Test fun menuContainsOnlyApplicableUsbAndAppPages() {
        compose.waitUntil(10000) { compose.onAllNodesWithTag("home_settings_menu_button").fetchSemanticsNodes().isNotEmpty() }
        for (page in listOf("SENSOR_MEASUREMENT", "DEBUG", "APPEARANCE", "ALERTS", "LANGUAGE", "LANGUAGE_AND_FORMATS", "DATA_RESTORE", "INFO")) {
            compose.onNodeWithTag("home_settings_menu_button").performClick()
            compose.onNodeWithTag("home_settings_item_SENSOR_CONNECTION").assertDoesNotExist()
            compose.onNodeWithTag("home_settings_item_SENSOR_PARAMETERS").assertDoesNotExist()
            compose.onNodeWithTag("home_settings_item_$page").performScrollTo().performClick()
            compose.waitForIdle()
            if (page == "ALERTS") compose.onNodeWithText(compose.activity.getString(R.string.rtc_battery_notification_toggle)).assertDoesNotExist()
            Espresso.pressBack()
            compose.onNodeWithTag("home_settings_menu_button").assertIsDisplayed()
        }
        assertEquals(UsbPhoneStatus.DETACHED, runtime.state.value.status)
        assertFalse(runtime.state.value.ready)
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
