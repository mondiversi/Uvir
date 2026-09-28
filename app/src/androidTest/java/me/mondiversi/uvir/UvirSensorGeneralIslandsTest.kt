package me.mondiversi.uvir

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Real settings content, with memory-only callbacks and no physical commands. */
class UvirSensorGeneralIslandsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun sixIslandsSeparateProfileFromOperationAndKeepOfflineGuards() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val night = mutableStateOf(false)
        val rtl = mutableStateOf(false)
        val online = mutableStateOf(true)
        val epoch = mutableStateOf(0)
        val changes = mutableListOf<String>()
        val saves = mutableListOf<SettingsSaveGroup>()
        compose.setContent {
            MaterialTheme(colorScheme = if (night.value) darkColorScheme() else lightColorScheme()) {
                CompositionLocalProvider(
                    LocalUvirSettingsNeutralIcons provides true,
                    LocalLayoutDirection provides if (rtl.value) LayoutDirection.Rtl else LayoutDirection.Ltr,
                    LocalUvirSettingsNavigation provides UvirSettingsNavigation(UvirSettingsPage.SENSOR_PARAMETERS, {})
                ) {
                    key(epoch.value) {
                        var led by remember { mutableStateOf(false) }
                        var buzzer by remember { mutableStateOf(false) }
                        var external by remember { mutableStateOf(false) }
                        val scope = rememberCoroutineScope()
                        val queue = remember(scope) { UvirSettingsSaveQueue(scope, {}) }
                        queue.save = { saves += it }
                        CompositionLocalProvider(LocalSettingsCommit provides UvirSettingsCommitScope(queue, SettingsSaveGroup.PARAMETERS)) {
                            val colors = MaterialTheme.colorScheme
                            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(8.dp)) {
                                UvirSensorParametersSettings(
                                    context = context, expanded = true, onExpandedChange = {},
                                    sensorSettingsEnabled = online.value,
                                    sensorName = "Fixture", onSensorNameChange = {}, sensorNameEditable = true,
                                    autonomousRecordingEnabled = true, onAutonomousRecordingEnabledChange = {},
                                    automaticShutdownEnabled = false, onAutomaticShutdownEnabledChange = {},
                                    automaticShutdownHoursText = "0", onAutomaticShutdownHoursTextChange = {},
                                    automaticShutdownMinutesText = "5", onAutomaticShutdownMinutesTextChange = {},
                                    automaticShutdownSecondsText = "0", onAutomaticShutdownSecondsTextChange = {},
                                    statusLedEnabled = led, onStatusLedEnabledChange = { led = it; changes += "LED:$it" },
                                    statusLedBrightness = 50f, onStatusLedBrightnessChange = {},
                                    statusBuzzerEnabled = buzzer, onStatusBuzzerEnabledChange = { buzzer = it; changes += "BUZZER:$it" },
                                    statusBuzzerVolume = 50f, onStatusBuzzerVolumeChange = {},
                                    externalCommandEnabled = external, onExternalCommandEnabledChange = { external = it; changes += "EXTERNAL:$it" },
                                    statusLedTestEnabled = false, onTestStatusLed = { error("No device test") },
                                    statusBuzzerTestEnabled = false, onTestStatusBuzzer = { error("No device test") },
                                    sensorAssociated = true, sensorPowerOffEnabled = online.value,
                                    onSensorPowerOffRequested = { error("No shutdown") },
                                    onDisassociateSensor = { error("No disassociation") },
                                    cardColor = colors.surface, primaryText = colors.onSurface,
                                    secondaryText = colors.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
        for (dark in listOf(false, true)) for (rightToLeft in listOf(false, true)) {
            compose.runOnIdle { night.value = dark; rtl.value = rightToLeft; online.value = true; epoch.value++ }
            compose.onNodeWithText(context.getString(R.string.settings_section_sensor_parameters)).assertDoesNotExist()
            compose.onNodeWithText(context.getString(R.string.sensor_parameters_description)).assertDoesNotExist()
            val titles = listOf(R.string.sensor_general_profile_title, R.string.sensor_general_operation_title, R.string.sensor_status_led,
                R.string.sensor_status_buzzer, R.string.external_command, R.string.sensor_general_actions_title)
            val bounds = titles.map { compose.onNodeWithText(context.getString(it), useUnmergedTree = true).getUnclippedBoundsInRoot() }
            bounds.zipWithNext().forEach { (a, b) -> assertTrue(a.bottom < b.top) }
            val identityBounds = compose.onNodeWithText("Fixture").getUnclippedBoundsInRoot()
            val autonomousBounds = compose.onNodeWithText(context.getString(R.string.sensor_autonomous_recording)).getUnclippedBoundsInRoot()
            val shutdownBounds = compose.onNodeWithText(context.getString(R.string.sensor_automatic_shutdown)).getUnclippedBoundsInRoot()
            assertTrue(bounds[0].bottom < identityBounds.top)
            assertTrue(identityBounds.bottom < bounds[1].top)
            assertTrue(bounds[1].bottom < autonomousBounds.top)
            assertTrue(autonomousBounds.bottom < shutdownBounds.top)
            assertTrue(shutdownBounds.bottom < bounds[2].top)
            for (description in listOf(R.string.sensor_general_profile_description, R.string.sensor_general_operation_description, R.string.sensor_general_actions_description)) {
                assertDescriptionStyle(context.getString(description))
            }
            val featureResources = listOf(
                R.string.sensor_status_led to R.string.sensor_status_led_description,
                R.string.sensor_status_buzzer to R.string.sensor_status_buzzer_description,
                R.string.external_command to R.string.sensor_external_command_info_description
            )
            for ((title, description) in featureResources) {
                val header = compose.onNodeWithContentDescription(context.getString(title))
                header.performScrollTo().assertIsOff()
                compose.onNodeWithText(context.getString(description)).assertDoesNotExist()
                val previous = changes.size
                val previousSaves = saves.size
                header.performClick().assertIsOn()
                assertDescriptionStyle(context.getString(description))
                compose.waitUntil(3_000) { saves.size == previousSaves + 1 }
                compose.runOnIdle {
                    assertEquals(previous + 1, changes.size)
                    assertEquals(SettingsSaveGroup.PARAMETERS, saves.last())
                }
            }
            // An info action remains available independently and must not toggle the LED setting.
            val beforeInfo = changes.size
            compose.onNodeWithContentDescription(context.getString(R.string.sensor_led_info_action))
                .performScrollTo().performClick()
            compose.onNodeWithText(context.getString(R.string.sensor_led_info_title)).assertIsDisplayed()
            androidx.test.espresso.Espresso.pressBack()
            compose.runOnIdle { assertEquals(beforeInfo, changes.size); online.value = false }
            for ((title, _) in featureResources) {
                compose.onNodeWithContentDescription(context.getString(title)).performScrollTo().assertIsNotEnabled()
            }
            for (title in listOf(R.string.sensor_autonomous_recording, R.string.sensor_automatic_shutdown)) {
                compose.onNodeWithText(context.getString(title)).performScrollTo().assertIsNotEnabled()
            }
            // The app-local display name remains editable while hardware settings are offline.
            compose.onNodeWithText("Fixture").performScrollTo().assertIsEnabled()
            compose.onNodeWithText(context.getString(R.string.sensor_power_off)).performScrollTo().assertIsNotEnabled()
            for (action in listOf(R.string.sensor_power_off, R.string.sensor_disassociate_action)) {
                val layouts = mutableListOf<TextLayoutResult>()
                compose.onNodeWithText(context.getString(action), useUnmergedTree = true)
                    .performScrollTo().performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
                assertEquals(FontWeight.Bold, layouts.single().layoutInput.style.fontWeight)
                assertFalse(layouts.single().hasVisualOverflow)
            }
        }
    }

    private fun assertDescriptionStyle(text: String) {
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(text).performScrollTo().assertIsDisplayed()
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertEquals(13.sp, layouts.single().layoutInput.style.fontSize)
        assertEquals(18.sp, layouts.single().layoutInput.style.lineHeight)
    }
}
