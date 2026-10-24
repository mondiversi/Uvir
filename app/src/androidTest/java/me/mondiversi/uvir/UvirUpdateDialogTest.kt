package me.mondiversi.uvir

import android.graphics.Bitmap
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Presentation only: no updater jobs, sensor commands, settings or real records. */
class UvirUpdateDialogTest {
    @get:Rule val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private fun action(resource: Int) = formatUvirActionLabel(context.getString(resource), context.resources.configuration.locales[0])
    private val asset = UvirUpdateAsset("", 1, "")
    private fun state(busy: Boolean = false, appFirst: Boolean = true) = UvirUpdateState(
        dialog = true, busy = busy, progress = if (busy) 0.4f else null,
        operation = if (busy) UvirUpdateOperation.APP else null,
        catalog = UvirUpdateCatalog(UvirAppRelease("1.4.1", if (appFirst) BuildConfig.VERSION_CODE + 1 else BuildConfig.VERSION_CODE, 26, asset),
            UvirFirmwareRelease("0.5.104", BuildConfig.VERSION_CODE, asset, "a".repeat(32))),
        sensors = listOf(UvirSensorUpdate("TEST-SENSOR", "Sensor with a deliberately long readable name", "0.5.103"))
    )
    private val usb = UvirUsbSensorState(status = UsbSensorConnectionStatus.CONNECTED,
        attached = true, appConnectionConfirmed = true, offlineSyncComplete = true,
        runtimeInfo = UvirSensorRuntimeInfo(deviceId = "TEST-SENSOR", operationActive = false,
            boardName = "ESP32 Dev Module", chipModel = "ESP32-D0WDQ6", firmwareVersion = "0.5.103",
            appPartitionSizeBytes = UvirUpdatePolicy.APP_CAPACITY.toLong(),
            offlineAcquisitions = 0, offlineAlerts = 0, offlineErrors = 0))

    @Test fun idleClosureUsesTheThemePurpleInBothThemes() {
        val dark = mutableStateOf(false)
        var expected = Color.Unspecified
        compose.setContent {
            KeepUvirVerificationScreenOn()
            val configuration = uvirThemeConfiguration(LocalConfiguration.current,
                if (dark.value) UvirAppTheme.DARK else UvirAppTheme.LIGHT)
            CompositionLocalProvider(LocalConfiguration provides configuration) {
                MaterialTheme(colorScheme = if (dark.value) darkColorScheme() else lightColorScheme()) {
                    val color = MaterialTheme.colorScheme.primary
                    SideEffect { expected = color }
                    UvirUpdateDialog(state(), usb, {}, {}, {}, darkTheme = dark.value)
                }
            }
        }
        for (night in listOf(false, true)) {
            compose.runOnIdle { dark.value = night }
            compose.waitForIdle()
            compose.onNodeWithText(action(R.string.close), useUnmergedTree = true)
                .assertUvirActionTextColor(expected)
            compose.onNodeWithText(context.getString(R.string.cancel)).assertDoesNotExist()
        }
    }
    @Test fun appIsFirstAndSensorCannotBeFlashedBeforeApp() {
        compose.setContent { MaterialTheme { UvirUpdateDialog(state(), usb, {}, {}, {}) } }
        val app = compose.onNodeWithText("Uvir").fetchSemanticsNode().boundsInRoot
        val sensor = compose.onNodeWithText(state().sensors.single().name).fetchSemanticsNode().boundsInRoot
        assertTrue(app.bottom < sensor.top)
        compose.onNodeWithText(action(R.string.update_app)).assertIsEnabled()
        compose.onNodeWithText(action(R.string.update_sensor)).assertIsNotEnabled()
        compose.onNodeWithText(action(R.string.close)).assertIsEnabled()
    }
    @Test fun busyButtonsAreDisabledInDarkTheme() {
        compose.setContent { MaterialTheme(colorScheme = darkColorScheme()) { UvirUpdateDialog(state(busy = true), usb, {}, {}, {}, darkTheme = true) } }
        compose.onNodeWithText(action(R.string.update_app)).assertIsNotEnabled()
        compose.onNodeWithText(action(R.string.update_sensor)).assertIsNotEnabled()
        compose.onNodeWithText(context.getString(R.string.cancel)).assertDoesNotExist()
        compose.onNodeWithText(action(R.string.close)).assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.stop)).assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.update_download)).assertExists()
        compose.onNodeWithText(context.getString(R.string.update_keep_connected)).assertDoesNotExist()
    }
    @Test fun idleSelectedUsbSensorCanBeUpdatedAfterApp() {
        compose.setContent { MaterialTheme(colorScheme = lightColorScheme()) { UvirUpdateDialog(state(appFirst = false), usb, {}, {}, {}, darkTheme = false) } }
        compose.onNodeWithText(action(R.string.update_sensor)).assertIsEnabled()
        compose.onNodeWithText("${BuildConfig.VERSION_NAME} → 1.4.1").assertDoesNotExist()
    }
    @Test fun anotherUsbIdentityIsNeverEnabled() {
        compose.setContent { MaterialTheme { UvirUpdateDialog(state(appFirst = false), usb.copy(runtimeInfo = usb.runtimeInfo.copy(deviceId = "OTHER")), {}, {}, {}) } }
        compose.onNodeWithText(action(R.string.update_sensor)).assertIsNotEnabled()
    }

    @Test fun firmwareWarningsAppearOnlyDuringFirmwareUpdates() {
        val operation = mutableStateOf(UvirUpdateOperation.APP)
        compose.setContent {
            KeepUvirVerificationScreenOn()
            MaterialTheme { UvirUpdateDialog(state(busy = true, appFirst = false)
                .copy(operation = operation.value), usb, {}, {}, {}) }
        }
        compose.onNodeWithText(context.getString(R.string.update_keep_connected)).assertDoesNotExist()
        compose.runOnIdle { operation.value = UvirUpdateOperation.FIRMWARE }
        compose.onNodeWithText(context.getString(R.string.update_keep_connected)).assertExists()
        compose.runOnIdle { operation.value = UvirUpdateOperation.APP }
        compose.onNodeWithText(context.getString(R.string.update_keep_connected)).assertDoesNotExist()
    }

    @Test fun appVerificationAndFailureNeverShowSensorRecoveryMessages() {
        val failed = mutableStateOf(false)
        compose.setContent {
            KeepUvirVerificationScreenOn()
            MaterialTheme { UvirUpdateDialog(state(busy = !failed.value)
                .copy(stage = R.string.update_app_verify,
                    message = if (failed.value) R.string.update_app_failed else null), usb, {}, {}, {}) }
        }
        compose.onNodeWithText(context.getString(R.string.update_app_verify)).assertExists()
        compose.onNodeWithText(context.getString(R.string.update_verify)).assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.update_keep_connected)).assertDoesNotExist()
        compose.runOnIdle { failed.value = true }
        compose.onNodeWithText(context.getString(R.string.update_app_failed)).assertExists()
        compose.onNodeWithText(context.getString(R.string.update_failed)).assertDoesNotExist()
    }

    @Test fun disconnectedBusyAndPendingQueueStatesNeverEnableTheFirmwareAction() {
        val current = mutableStateOf(usb)
        compose.setContent {
            KeepUvirVerificationScreenOn()
            MaterialTheme { UvirUpdateDialog(state(appFirst = false), current.value, {}, {}, {}) }
        }
        compose.onNodeWithText(action(R.string.update_sensor)).assertIsEnabled()
        for (blocked in listOf(usb.copy(status = UsbSensorConnectionStatus.DISCONNECTED),
            usb.copy(attached = false), usb.copy(appConnectionConfirmed = false),
            usb.copy(offlineSyncComplete = false),
            usb.copy(runtimeInfo = usb.runtimeInfo.copy(operationActive = true)),
            usb.copy(runtimeInfo = usb.runtimeInfo.copy(offlineAcquisitions = 1)),
            usb.copy(runtimeInfo = usb.runtimeInfo.copy(offlineAlerts = 1)),
            usb.copy(runtimeInfo = usb.runtimeInfo.copy(offlineErrors = 1)))) {
            compose.runOnIdle { current.value = blocked }
            compose.onNodeWithText(action(R.string.update_sensor)).assertIsNotEnabled()
        }
        compose.runOnIdle { current.value = usb }
        compose.onNodeWithText(action(R.string.update_sensor)).assertIsEnabled()
    }

    @Test fun appAndSensorActionsAreTransparentPurpleTextInBothThemes() {
        val night = mutableStateOf(false)
        val appFirst = mutableStateOf(true)
        var purple = Color.Unspecified
        var appClicks = 0
        var sensorClicks = 0
        compose.setContent {
            KeepUvirVerificationScreenOn()
            val configuration = uvirThemeConfiguration(LocalConfiguration.current,
                if (night.value) UvirAppTheme.DARK else UvirAppTheme.LIGHT)
            CompositionLocalProvider(LocalConfiguration provides configuration) {
                MaterialTheme(colorScheme = if (night.value) darkColorScheme() else lightColorScheme()) {
                    UvirActionTheme {
                        purple = MaterialTheme.colorScheme.primary
                        UvirUpdateDialog(state(appFirst = appFirst.value), usb, {}, { appClicks++ },
                            { sensorClicks++ }, darkTheme = night.value)
                    }
                }
            }
        }
        for (dark in listOf(false, true)) for (app in listOf(true, false)) {
            compose.runOnIdle { night.value = dark; appFirst.value = app }
            compose.waitForIdle()
            val tag = if (app) "update_app_action" else "update_sensor_action_TEST-SENSOR"
            val label = if (app) R.string.update_app else R.string.update_sensor
            compose.onNodeWithText(action(label), useUnmergedTree = true).assertUvirActionTextColor(purple)
            val pixels = compose.onNodeWithTag(tag).assertIsEnabled().captureToImage().toPixelMap()
            val sample = pixels[pixels.width / 2, pixels.height - with(compose.density) { 6.dp.toPx().toInt() }]
            val card = if (dark) Color(0xFF1C242B) else Color.White
            assertTrue("Update text action has a resting fill: $app/$dark", abs(sample.red - card.red) < .02f &&
                abs(sample.green - card.green) < .02f && abs(sample.blue - card.blue) < .02f)
            if (app) {
                val disabled = compose.onNodeWithTag("update_sensor_action_TEST-SENSOR")
                    .assertIsNotEnabled().captureToImage().toPixelMap()
                val background = disabled[disabled.width / 2,
                    disabled.height - with(compose.density) { 6.dp.toPx().toInt() }]
                assertTrue("Disabled popup text action has a fill: $dark", abs(background.red - card.red) < .02f &&
                    abs(background.green - card.green) < .02f && abs(background.blue - card.blue) < .02f)
            }
            screenshot("update-${if (app) "app" else "sensor"}-${if (dark) "night" else "day"}.png")
        }
        // Exercise callbacks only after all resting snapshots: Android's native
        // ripple uses wall time, not the Compose test clock, and may still be
        // fading when the next theme/disabled-state fixture is rendered.
        repeat(2) {
            compose.runOnIdle { appFirst.value = true }
            compose.onNodeWithTag("update_app_action").performClick()
            compose.runOnIdle { appFirst.value = false }
            compose.onNodeWithTag("update_sensor_action_TEST-SENSOR").performClick()
        }
        assertEquals(2, appClicks)
        assertEquals(2, sensorClicks)
    }

    private fun screenshot(name: String) {
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        File(context.getExternalFilesDir(null), name).outputStream().use {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
        bitmap.recycle()
    }
}
