package me.mondiversi.uvir

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.key
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UvirSensorSimulationSettingsContentTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun diagnosticSwitchesRemainUsableWithRunningActivitiesWithoutLeavingTheirPage() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val fakeTitle = context.getString(R.string.debug_fake_data)
        val olTitle = context.getString(R.string.debug_fake_out_of_range)
        val dark = mutableStateOf(false)
        val rtl = mutableStateOf(false)
        compose.setContent {
            MaterialTheme(colorScheme = if (dark.value) darkColorScheme() else lightColorScheme()) {
                CompositionLocalProvider(LocalLayoutDirection provides
                    if (rtl.value) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                    val view = LocalView.current
                    DisposableEffect(view) {
                        val previous = view.keepScreenOn
                        view.keepScreenOn = true
                        onDispose { view.keepScreenOn = previous }
                    }
                    var current by remember { mutableStateOf(UvirSensorSimulationSettings()) }
                    val colors = MaterialTheme.colorScheme
                    Column {
                        Text("Acquisition and alert jobs stay active")
                        UvirSensorSimulationSettingsContent(
                            enabled = current.enabled,
                            outOfRange = current.outOfRange,
                            onEnabledChange = { current = current.copy(enabled = it) },
                            onOutOfRangeChange = { current = current.copy(outOfRange = it) },
                            primaryText = colors.onSurface,
                            secondaryText = colors.onSurfaceVariant
                        )
                    }
                }
            }
        }
        // Labels belong to the clickable row; toggle state belongs to its checkbox.
        fun fake() = compose.onAllNodes(isToggleable())[0]
        fun ol() = compose.onAllNodes(isToggleable())[1]
        for (night in listOf(false, true)) for (rightToLeft in listOf(false, true)) {
            compose.runOnIdle { dark.value = night; rtl.value = rightToLeft }
            compose.onNodeWithText(fakeTitle).assertIsDisplayed()
            compose.onNodeWithText(olTitle).assertIsDisplayed()
            fake().assertIsEnabled().assertIsOff().performClick()
            ol().assertIsEnabled().assertIsOff().performClick()
            ol().assertIsOn()
            fake().performClick().assertIsOff()
            ol().assertIsOn().assertIsNotEnabled()
            fake().performClick()
            fake().assertIsOn()
            ol().assertIsOn().performClick()
            fake().performClick().assertIsOff()
            compose.onNodeWithText("Acquisition and alert jobs stay active").assertIsDisplayed()
        }
    }

    @Test
    fun settingsRouteAndScrollSurviveSimulationUiRecreationAndStateRestoration() {
        val restoration = StateRestorationTester(compose)
        val simulation = mutableStateOf(UvirSensorSimulationSettings())
        val generation = mutableStateOf(0)
        lateinit var route: UvirSettingsRouteState
        restoration.setContent {
            route = rememberUvirSettingsRouteState()
            val open = route.isOpen.value
            MaterialTheme {
                if (!open) {
                    androidx.compose.material3.TextButton(onClick = {
                        route.isOpen.value = true
                        route.page.value = UvirSettingsPage.DEBUG
                    }, modifier = Modifier.testTag("open-diagnostics")) { Text("Open diagnostics") }
                } else {
                    Column {
                        Text(route.page.value!!.name, Modifier.testTag("settings-page"))
                        Column(Modifier.fillMaxWidth().height(220.dp).verticalScroll(route.scrollState)
                            .testTag("diagnostics-scroll")) {
                            key(generation.value) {
                                Text("Diagnostic introduction", Modifier.height(80.dp))
                                UvirSensorSimulationSettingsContent(
                                    enabled = simulation.value.enabled,
                                    outOfRange = simulation.value.outOfRange,
                                    onEnabledChange = {
                                        simulation.value = simulation.value.copy(enabled = it)
                                        generation.value++ // Same context rebuild as the sensor UI host.
                                    },
                                    onOutOfRangeChange = {
                                        simulation.value = simulation.value.copy(outOfRange = it)
                                    },
                                    primaryText = MaterialTheme.colorScheme.onSurface,
                                    secondaryText = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                repeat(20) { Text("Diagnostic content $it") }
                            }
                        }
                        androidx.compose.material3.TextButton(onClick = { route.close() },
                            modifier = Modifier.testTag("diagnostics-back")) {
                            Text("Back")
                        }
                    }
                }
            }
        }
        compose.onNodeWithTag("open-diagnostics").performClick()
        compose.onNodeWithTag("diagnostics-scroll").performSemanticsAction(
            androidx.compose.ui.semantics.SemanticsActions.ScrollBy) { it(0f, 32f) }
        var initialScroll = 0
        compose.runOnIdle {
            initialScroll = route.scrollState.value
            org.junit.Assert.assertTrue(initialScroll > 0)
        }
        compose.onAllNodes(isToggleable())[0].performClick().assertIsOn()
        compose.onNodeWithTag("settings-page").assertTextEquals(UvirSettingsPage.DEBUG.name)
        compose.onNodeWithTag("open-diagnostics").assertDoesNotExist()
        compose.runOnIdle { org.junit.Assert.assertEquals(initialScroll, route.scrollState.value) }
        compose.onAllNodes(isToggleable())[1].performClick().assertIsOn()
        compose.onNodeWithTag("settings-page").assertTextEquals(UvirSettingsPage.DEBUG.name)
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("settings-page").assertTextEquals(UvirSettingsPage.DEBUG.name)
        compose.runOnIdle { org.junit.Assert.assertEquals(initialScroll, route.scrollState.value) }
        compose.onAllNodes(isToggleable())[0].performClick().assertIsOff()
        compose.onNodeWithTag("settings-page").assertTextEquals(UvirSettingsPage.DEBUG.name)
        compose.runOnIdle { org.junit.Assert.assertEquals(initialScroll, route.scrollState.value) }
        compose.onNodeWithTag("diagnostics-back").performClick()
        compose.onNodeWithTag("open-diagnostics").assertIsDisplayed()
        compose.runOnIdle { org.junit.Assert.assertNull(route.page.value) }
    }

    @Test
    fun simulateEventsButtonIsAlwaysAvailableAndStillBlocksDuplicatePreparation() {
        val busy = mutableStateOf(false)
        var clicks = 0
        compose.setContent {
            MaterialTheme {
                UvirSimulateEventsButton(
                    preparing = busy.value,
                    onClick = { clicks++ },
                    primaryText = MaterialTheme.colorScheme.onSurface,
                    secondaryText = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        compose.onNodeWithTag("simulate_events_button").assertIsEnabled().performClick()
        compose.runOnIdle { org.junit.Assert.assertEquals(1, clicks); busy.value = true }
        compose.onNodeWithTag("simulate_events_button").assertIsNotEnabled()
        compose.runOnIdle { busy.value = false }
        compose.onNodeWithTag("simulate_events_button").assertIsEnabled().performClick()
        compose.runOnIdle { org.junit.Assert.assertEquals(2, clicks) }
        compose.onNodeWithTag("simulate_events_button").assertIsEnabled()
    }

    @Test
    fun menuShortcutOpensDiagnosticsAtMockControlsWithoutChangingSimulation() {
        val simulation = mutableStateOf(UvirSensorSimulationSettings(enabled = true))
        val generation = mutableStateOf(0)
        lateinit var route: UvirSettingsRouteState
        compose.setContent {
            route = rememberUvirSettingsRouteState()
            MaterialTheme {
                if (!route.isOpen.value) {
                    UvirHomeSensorSelectionDialog(
                        expanded = true,
                        onDismissRequest = {},
                        sensorProfiles = emptyList(),
                        selectedSensorDeviceId = "",
                        sensorConnectionModes = emptyMap(),
                        selectedConnectionMode = SensorConnectionMode.WIFI,
                        sensorInfoEnabled = false,
                        onOpenSensorInfo = {},
                        primaryText = MaterialTheme.colorScheme.onSurface,
                        secondaryText = MaterialTheme.colorScheme.onSurfaceVariant,
                        containerColor = MaterialTheme.colorScheme.surface,
                        onSensorSelected = {},
                        simulationActive = simulation.value.enabled,
                        onOpenSimulationSettings = route::openSimulationSettings
                    )
                } else {
                    Column {
                        Text(route.page.value!!.name, Modifier.testTag("shortcut-page"))
                        Column(Modifier.fillMaxWidth().height(240.dp).verticalScroll(route.scrollState)) {
                            key(generation.value) {
                                Text("Full diagnostic test", Modifier.height(600.dp))
                                UvirSensorSimulationSettingsContent(
                                    enabled = simulation.value.enabled,
                                    outOfRange = simulation.value.outOfRange,
                                    onEnabledChange = {
                                        simulation.value = simulation.value.copy(enabled = it)
                                        generation.value++
                                    },
                                    onOutOfRangeChange = {
                                        simulation.value = simulation.value.copy(outOfRange = it)
                                    },
                                    primaryText = MaterialTheme.colorScheme.onSurface,
                                    secondaryText = MaterialTheme.colorScheme.onSurfaceVariant,
                                    scrollIntoView = route.simulationFocusRequested.value,
                                    onShown = { route.simulationFocusRequested.value = false }
                                )
                                Text("Sensor concert", Modifier.height(600.dp))
                            }
                        }
                    }
                }
            }
        }
        compose.onNodeWithTag("home_sensor_simulation_settings").performClick()
        compose.onNodeWithTag("home_sensor_dialog").assertDoesNotExist()
        compose.onNodeWithTag("shortcut-page").assertTextEquals(UvirSettingsPage.DEBUG.name)
        compose.waitUntil(5_000) { !route.simulationFocusRequested.value }
        compose.onNodeWithTag("simulation_settings_controls").assertIsDisplayed()
        compose.onAllNodes(isToggleable())[0].assertIsOn()
        var initialScroll = 0
        compose.runOnIdle {
            initialScroll = route.scrollState.value
            org.junit.Assert.assertTrue(initialScroll > 0)
            org.junit.Assert.assertTrue(simulation.value.enabled)
        }
        compose.onAllNodes(isToggleable())[0].performClick().assertIsOff()
        compose.onNodeWithTag("shortcut-page").assertTextEquals(UvirSettingsPage.DEBUG.name)
        compose.runOnIdle { org.junit.Assert.assertEquals(initialScroll, route.scrollState.value) }
    }
}
