package me.mondiversi.uvir

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Presentation only: no updater jobs, sensor commands, settings or real records. */
class UvirUpdateDialogTest {
    @get:Rule val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val asset = UvirUpdateAsset("", 1, "")
    private fun state(busy: Boolean = false, appFirst: Boolean = true) = UvirUpdateState(
        dialog = true, busy = busy, progress = if (busy) 0.4f else null,
        catalog = UvirUpdateCatalog(UvirAppRelease("1.3.1", if (appFirst) BuildConfig.VERSION_CODE + 1 else BuildConfig.VERSION_CODE, 26, asset),
            UvirFirmwareRelease("0.5.104", BuildConfig.VERSION_CODE, asset, "a".repeat(32))),
        sensors = listOf(UvirSensorUpdate("TEST-SENSOR", "Sensor with a deliberately long readable name", "0.5.103"))
    )
    private val usb = UvirUsbSensorState(appConnectionConfirmed = true,
        runtimeInfo = UvirSensorRuntimeInfo(deviceId = "TEST-SENSOR", operationActive = false))
    @Test fun appIsFirstAndSensorCannotBeFlashedBeforeApp() {
        compose.setContent { MaterialTheme { UvirUpdateDialog(state(), usb, {}, {}, {}) } }
        val app = compose.onNodeWithText("Uvir").fetchSemanticsNode().boundsInRoot
        val sensor = compose.onNodeWithText(state().sensors.single().name).fetchSemanticsNode().boundsInRoot
        assertTrue(app.bottom < sensor.top)
        compose.onNodeWithText(context.getString(R.string.update_app)).assertIsEnabled()
        compose.onNodeWithText(context.getString(R.string.update_sensor)).assertIsNotEnabled()
    }
    @Test fun busyButtonsAreDisabledInDarkTheme() {
        compose.setContent { MaterialTheme(colorScheme = darkColorScheme()) { UvirUpdateDialog(state(busy = true), usb, {}, {}, {}, darkTheme = true) } }
        compose.onNodeWithText(context.getString(R.string.update_app)).assertIsNotEnabled()
        compose.onNodeWithText(context.getString(R.string.update_sensor)).assertIsNotEnabled()
        compose.onNodeWithText(context.getString(R.string.update_download)).assertExists()
    }
    @Test fun idleSelectedUsbSensorCanBeUpdatedAfterApp() {
        compose.setContent { MaterialTheme(colorScheme = lightColorScheme()) { UvirUpdateDialog(state(appFirst = false), usb, {}, {}, {}, darkTheme = false) } }
        compose.onNodeWithText(context.getString(R.string.update_sensor)).assertIsEnabled()
        compose.onNodeWithText("1.3.0 → 1.3.1").assertDoesNotExist()
    }
    @Test fun anotherUsbIdentityIsNeverEnabled() {
        compose.setContent { MaterialTheme { UvirUpdateDialog(state(appFirst = false), usb.copy(runtimeInfo = usb.runtimeInfo.copy(deviceId = "OTHER")), {}, {}, {}) } }
        compose.onNodeWithText(context.getString(R.string.update_sensor)).assertIsNotEnabled()
    }
}
