package me.mondiversi.uvir

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Isolated tablet controls: no transports, records or changes to user preferences. */
class UvirTabletPopupTest {
    @get:Rule val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun floatingHintsKeepTheSameAdaptiveTextAndDimensionsAsTheirHost() {
        val night = mutableStateOf(false)
        val label = mutableStateOf("Acquisizione manuale")
        var expectedWidth = 0f
        compose.setContent {
            val view = LocalView.current
            DisposableEffect(view) {
                val previous = view.keepScreenOn
                view.keepScreenOn = true
                onDispose { view.keepScreenOn = previous }
            }
            val configuration = Configuration(LocalConfiguration.current).apply {
                smallestScreenWidthDp = 800
                screenWidthDp = 800
                screenHeightDp = 1280
            }
            CompositionLocalProvider(LocalConfiguration provides configuration) {
                UvirAdaptiveDisplay {
                    val density = LocalDensity.current
                    SideEffect { expectedWidth = with(density) { 280.dp.toPx() } }
                    MaterialTheme(colorScheme = if (night.value) darkColorScheme() else lightColorScheme()) {
                        Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.BottomEnd) {
                            Text("Hint reference", style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.align(Alignment.TopStart).testTag("tablet_hint_reference"))
                            UvirFloatingActionHint(label.value, visible = true)
                        }
                    }
                }
            }
        }
        for (dark in listOf(false, true)) {
            for (text in listOf("Acquisizione manuale", "Acquisizione automatica",
                "Registrazione allerte", "Avvia acquisizione", "Ferma acquisizione",
                "Avvia registrazione", "Ferma registrazione")) {
                compose.runOnIdle { night.value = dark; label.value = text }
                val hint = compose.onNodeWithTag("floating_action_hint").assertIsDisplayed()
                    .fetchSemanticsNode().boundsInRoot
                val referenceText = compose.onNodeWithTag("tablet_hint_reference").fetchSemanticsNode().boundsInRoot
                val hintText = compose.onNodeWithText(text, useUnmergedTree = true)
                    .assertIsDisplayed().fetchSemanticsNode().boundsInRoot
                assertEquals("The popup keeps the tablet width", expectedWidth, hint.width, 1f)
                assertEquals("The hint uses the host's enlarged text", referenceText.height, hintText.height, 1f)
                assertTrue("Hint text fits inside its bubble", hintText.width <= hint.width)
            }
        }
    }

    @Test fun emptySensorListStillOpensFromTheNameAndStatus() {
        val expanded = mutableStateOf(false)
        val night = mutableStateOf(false)
        compose.setContent {
            val configuration = Configuration(LocalConfiguration.current).apply {
                smallestScreenWidthDp = 800
                screenWidthDp = 800
                screenHeightDp = 1280
            }
            CompositionLocalProvider(LocalConfiguration provides configuration) {
                UvirAdaptiveDisplay {
                    MaterialTheme(colorScheme = if (night.value) darkColorScheme() else lightColorScheme()) {
                        val colors = MaterialTheme.colorScheme
                        UvirHomeHeader(
                            sensorConnectionMode = SensorConnectionMode.USB, useFakeSensorData = true,
                            usbSensorStatus = UsbSensorConnectionStatus.DISCONNECTED,
                            usbAppConnectionConfirmed = false,
                            wirelessSensorStatus = WirelessSensorConnectionStatus.DISCONNECTED,
                            wirelessSensorMode = SensorConnectionMode.WIFI,
                            wirelessAppConnectionConfirmed = false,
                            sensorActivityInProgress = false, versionInfoDescription = "Info",
                            primaryText = colors.onSurface, secondaryText = colors.onSurfaceVariant,
                            onOpenVersionInfo = {}, onOpenSettings = {},
                            sensorSelectionExpanded = expanded.value,
                            onSensorSelectionExpandedChange = { expanded.value = it },
                            sensorDisplayName = "", sensorSelectionEnabled = true,
                            sensorProfiles = emptyList(), selectedSensorDeviceId = "",
                            onSensorSelected = {}, onOpenSensorInfo = {}, onSensorConnectionModeChanged = {},
                            wifiEnabled = true, bluetoothEnabled = true, internetEnabled = true
                        )
                    }
                }
            }
        }
        for (dark in listOf(false, true)) {
            compose.runOnIdle { night.value = dark }
            compose.onNodeWithText("—", useUnmergedTree = true).assertIsDisplayed()
            for (tag in listOf("home_sensor_name", "home_sensor_status")) {
                compose.onNodeWithTag(tag).performTouchInput { click() }
                compose.onNodeWithText(context.getString(R.string.sensor_selection_title)).assertIsDisplayed()
                compose.onNodeWithTag("home_sensor_option_none").assertDoesNotExist()
                compose.onNodeWithTag("home_sensor_connection_footer").assertDoesNotExist()
                compose.onNodeWithTag("home_sensor_simulation_warning").assertIsDisplayed()
                compose.onNodeWithContentDescription(context.getString(R.string.close)).performClick()
                compose.onNodeWithTag("home_sensor_selection_menu").assertDoesNotExist()
            }
        }
    }
}
