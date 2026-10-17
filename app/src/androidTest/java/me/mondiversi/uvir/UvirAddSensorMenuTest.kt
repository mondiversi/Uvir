package me.mondiversi.uvir

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.LayoutDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** In-process UI fixtures only; no hardware, permissions, photos or user data. */
class UvirAddSensorMenuTest {
    @get:Rule val compose = createComposeRule()

    @Test fun addSensorRemainsFirstWithoutCameraAndDoesNotSelectAProfile() {
        val night = mutableStateOf(false)
        val rtl = mutableStateOf(false)
        val profiles = mutableStateOf(emptyList<UvirSensorProfile>())
        var addRequests = 0
        val selections = mutableListOf<String>()
        compose.setContent {
            val view = LocalView.current
            DisposableEffect(view) {
                val previous = view.keepScreenOn
                view.keepScreenOn = true
                onDispose { view.keepScreenOn = previous }
            }
            CompositionLocalProvider(LocalLayoutDirection provides if (rtl.value) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                MaterialTheme(colorScheme = if (night.value) darkColorScheme() else lightColorScheme()) {
                    UvirHomeSensorSelectionDialog(true, {}, profiles.value,
                        if (profiles.value.isEmpty()) "" else "fixture", emptyMap(), SensorConnectionMode.USB,
                        false, {}, MaterialTheme.colorScheme.onSurface, MaterialTheme.colorScheme.onSurfaceVariant,
                        MaterialTheme.colorScheme.surface, onSensorSelected = { selections += it },
                        onAddSensor = { addRequests++ })
                }
            }
        }
        for (dark in listOf(false, true)) for (rightToLeft in listOf(false, true)) {
            for (hasHardware in listOf(false, true)) {
                compose.runOnIdle {
                    night.value = dark; rtl.value = rightToLeft
                    profiles.value = if (hasHardware) listOf(UvirSensorProfile(1, "fixture", "Fixture", 0, 0)) else emptyList()
                }
                val add = compose.onNodeWithTag("home_sensor_add").assertIsDisplayed()
                val option = compose.onNodeWithTag("home_sensor_option_fixture")
                compose.onNodeWithTag("home_sensor_option_none").assertDoesNotExist()
                compose.onNodeWithTag("home_sensor_option_camera").assertDoesNotExist()
                compose.onNodeWithTag("camera_source_warning").assertDoesNotExist()
                if (hasHardware) {
                    option.assertIsSelected()
                    assertTrue(add.fetchSemanticsNode().boundsInRoot.bottom <= option.fetchSemanticsNode().boundsInRoot.top)
                } else {
                    option.assertDoesNotExist()
                    compose.onNodeWithTag("home_sensor_profiles").assertDoesNotExist()
                }
                add.performClick()
                if (hasHardware) option.assertIsSelected()
                compose.onNodeWithTag("home_sensor_connection_grid").assertIsDisplayed()
                if (!hasHardware) SensorConnectionMode.entries.forEach {
                    compose.onNodeWithTag("home_source_option_${it.name}").assertIsNotEnabled()
                }
            }
        }
        assertEquals(8, addRequests)
        assertTrue(selections.isEmpty())
    }
}
