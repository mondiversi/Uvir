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
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Isolated disconnected states: never reset associations or connect to real hardware. */
class UvirDisconnectedAddSensorTest {
    @get:Rule val compose = createComposeRule()

    @Test fun disconnectedIslandOffersAddSensorWithoutConnectionOptionsInBothThemes() {
        val night = mutableStateOf(false)
        val rtl = mutableStateOf(false)
        var addRequests = 0
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        compose.setContent {
            val view = LocalView.current
            DisposableEffect(view) {
                val old = view.keepScreenOn; view.keepScreenOn = true
                onDispose { view.keepScreenOn = old }
            }
            CompositionLocalProvider(LocalLayoutDirection provides if (rtl.value) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                MaterialTheme(colorScheme = if (night.value) darkColorScheme() else lightColorScheme()) {
                    UvirDisconnectedSensorIsland(MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.onSurface,
                        MaterialTheme.colorScheme.onSurfaceVariant, { addRequests++ })
                }
            }
        }
        for (dark in listOf(false, true)) for (rightToLeft in listOf(false, true)) {
            compose.runOnIdle { night.value = dark; rtl.value = rightToLeft }
            compose.onNodeWithText(context.getString(R.string.home_sensor_disconnected_hint)).assertDoesNotExist()
            compose.onNodeWithText(context.getString(R.string.home_sensor_disconnected_title)).assertIsDisplayed()
            compose.onNodeWithText(context.getString(R.string.home_sensor_disconnected_description)).assertIsDisplayed()
            compose.onNodeWithTag("disconnected_add_sensor").assertIsDisplayed().assertIsEnabled().performClick()
            compose.onNodeWithTag("disconnected_sensor_connection_grid").assertDoesNotExist()
            SensorConnectionMode.entries.forEach { mode ->
                compose.onNodeWithTag("disconnected_source_option_${mode.name}").assertDoesNotExist()
                compose.onNodeWithContentDescription(context.getString(uvirSensorConnectionLabelRes(mode))).assertDoesNotExist()
            }
        }
        assertEquals(4, addRequests)
    }
}
