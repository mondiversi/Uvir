package me.mondiversi.uvir

import android.graphics.Bitmap
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Isolated presentation only: no sensor commands, real warnings, records or preferences. */
class UvirDialogClosureUiTest {
    @get:Rule val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val page = mutableIntStateOf(0)
    private val dark = mutableStateOf(false)
    private val open = mutableStateOf(true)
    private val simulation = mutableStateOf(false)
    private var closures = 0
    private var operations = 0
    private var expectedPurple = Color.Unspecified
    private var expectedRed = Color.Unspecified

    private fun fixture() {
        compose.setContent {
            KeepUvirVerificationScreenOn()
            val configuration = uvirThemeConfiguration(LocalConfiguration.current,
                if (dark.value) UvirAppTheme.DARK else UvirAppTheme.LIGHT)
            CompositionLocalProvider(LocalConfiguration provides configuration) {
                MaterialTheme(colorScheme = if (dark.value) darkColorScheme() else lightColorScheme()) {
                    val colors = MaterialTheme.colorScheme
                    val red = UvirDestructiveActionColor
                    SideEffect { expectedPurple = colors.primary; expectedRed = red }
                    val dismiss = { closures++; open.value = false }
                    if (open.value) when (page.intValue) {
                        0 -> UvirStatusLedInfoDialog(colors.onSurface, colors.onSurfaceVariant,
                            colors.surface, false, { operations++; false }, dismiss)
                        1 -> UvirStatusBuzzerInfoDialog(colors.onSurface, colors.onSurfaceVariant,
                            colors.surface, false, { operations++; false }, dismiss)
                        2 -> UvirExternalCommandInfoDialog(colors.onSurface, colors.onSurfaceVariant,
                            colors.surface, dismiss)
                        3 -> UvirRtcClockWarningDialog(listOf("TEST-SENSOR"), emptyList(),
                            colors.surface, colors.onSurface, dismiss)
                        4 -> SensorSyncCompleteDialog(SensorSyncSummary(2, 1, 0), colors.surface,
                            colors.onSurface, colors.onSurfaceVariant, dismiss)
                        5 -> SensorSyncIncompleteDialog(SensorSyncIncompleteSummary(2, 1, 0), colors.surface,
                            colors.onSurface, colors.onSurfaceVariant, dismiss)
                        6 -> UvirAutomaticBackgroundWarningDialog(colors.surface, colors.onSurface,
                            colors.onSurfaceVariant, { operations++ }, { operations++ }, dismiss)
                        7 -> UvirHomeSensorSelectionDialog(expanded = true,
                            onDismissRequest = dismiss, sensorProfiles = emptyList(),
                            selectedSensorDeviceId = "", sensorConnectionModes = emptyMap(),
                            selectedConnectionMode = SensorConnectionMode.USB,
                            sensorInfoEnabled = false, onOpenSensorInfo = { operations++ },
                            primaryText = colors.onSurface, secondaryText = colors.onSurfaceVariant,
                            containerColor = colors.surface, onSensorSelected = { operations++ },
                            simulationActive = simulation.value,
                            onDisableSimulation = { operations++; simulation.value = false })
                    }
                }
            }
        }
    }

    @Test fun informationalPopupsCloseInPurpleWithoutStartingAnythingInBothThemes() {
        fixture()
        for (night in listOf(false, true)) for (index in 0..6) {
            compose.runOnIdle { dark.value = night; page.intValue = index; open.value = true }
            val close = compose.onNodeWithText(context.getString(R.string.close))
            close.assertIsDisplayed().assertIsEnabled().assertUvirActionTextColor(expectedPurple)
            if (index in 0..1) {
                val test = compose.onNodeWithText(context.getString(
                    if (index == 0) R.string.sensor_led_test else R.string.sensor_buzzer_test))
                test.assertIsNotEnabled()
                val first = close.getUnclippedBoundsInRoot()
                val second = test.getUnclippedBoundsInRoot()
                assertTrue("Close precedes the test", first.left < second.left || first.top < second.top)
            }
            if (index == 0 || index == 4) {
                File(context.getExternalFilesDir(null), "popup-close-$index-${if (night) "night" else "day"}.png")
                    .outputStream().use {
                        assertTrue(compose.onNode(isDialog()).captureToImage().asAndroidBitmap()
                            .compress(Bitmap.CompressFormat.PNG, 100, it))
                    }
            }
            val before = closures
            close.performClick()
            compose.runOnIdle { assertFalse(open.value); assertEquals(before + 1, closures); assertEquals(0, operations) }
        }
    }

    @Test fun onlyMockSensorSelectionHasPurpleCloseBeforeRedDisableWithoutChangingSimulation() {
        page.intValue = 7
        fixture()
        for (night in listOf(false, true)) for (simulated in listOf(false, true)) {
            compose.runOnIdle { dark.value = night; simulation.value = simulated }
            if (simulated) {
                val close = compose.onNodeWithTag("home_sensor_simulation_close")
                close.assertIsDisplayed().assertIsEnabled()
                compose.onNodeWithText(context.getString(R.string.close)).assertUvirActionTextColor(expectedPurple)
                val disable = compose.onNodeWithTag("home_sensor_simulation_disable")
                disable.assertIsDisplayed().assertTextEquals(formatUvirActionLabel(
                    context.getString(R.string.sensor_simulation_disable), context.resources.configuration.locales[0]))
                compose.onNodeWithText(formatUvirActionLabel(context.getString(R.string.sensor_simulation_disable),
                    context.resources.configuration.locales[0])).assertUvirActionTextColor(expectedRed)
                assertTrue(close.getUnclippedBoundsInRoot().left < disable.getUnclippedBoundsInRoot().left)
                File(context.getExternalFilesDir(null), "sensor-mock-close-${if (night) "night" else "day"}.png")
                    .outputStream().use {
                        assertTrue(compose.onNode(isDialog()).captureToImage().asAndroidBitmap()
                            .compress(Bitmap.CompressFormat.PNG, 100, it))
                    }
            } else {
                compose.onNodeWithText(context.getString(R.string.close)).assertDoesNotExist()
                compose.onNodeWithTag("home_sensor_simulation_close").assertDoesNotExist()
            }
            compose.onNodeWithContentDescription(context.getString(R.string.close)).assertIsDisplayed()
        }
        compose.onNodeWithTag("home_sensor_simulation_close").performClick()
        compose.runOnIdle {
            assertEquals(1, closures)
            assertFalse(open.value)
            assertTrue(simulation.value)
            assertEquals(0, operations)
        }
    }

    @Test fun mockDisableActionKeepsTheNormalPickerOpenAndDoesNotDismissOrOpenSettings() {
        page.intValue = 7
        simulation.value = true
        fixture()
        compose.onNodeWithTag("home_sensor_simulation_disable").performClick()
        compose.onNodeWithTag("home_sensor_dialog").assertIsDisplayed()
        compose.onNodeWithTag("home_sensor_add").assertIsDisplayed()
        compose.onNodeWithTag("home_sensor_connection_footer").assertIsDisplayed()
        compose.onNodeWithTag("home_sensor_simulation_warning").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(0, closures)
            assertEquals(1, operations)
            assertTrue(open.value)
            assertFalse(simulation.value)
        }
    }
}
