package me.mondiversi.uvir

import android.content.res.Configuration
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import kotlin.math.roundToInt

class UvirResetScrollbarsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun resetDialogsShowScrollbarOnlyOnOverflowIncludingManySensorsAndShortScreens() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val night = mutableStateOf(false)
        val compact = mutableStateOf(false)
        val app = mutableStateOf(false)
        val epoch = mutableStateOf(0)
        var density = 1f
        val profiles = (1..32).map { UvirSensorProfile(it.toLong(), "UID-$it", "Sensor $it", 0L, 0L) }
        val indicators = profiles.associate {
            normalizeSensorDeviceId(it.hardwareUid) to UvirStatusIndicator(UvirStatusDot.GREEN, false)
        }
        compose.setContent {
            val configuration = Configuration(LocalConfiguration.current)
            if (compact.value) configuration.screenHeightDp = 280
            density = LocalDensity.current.density
            MaterialTheme(colorScheme = if (night.value) darkColorScheme() else lightColorScheme()) {
                CompositionLocalProvider(LocalConfiguration provides configuration,
                    LocalLayoutDirection provides if (night.value) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                    key(epoch.value) {
                        val primary = if (night.value) Color.White else Color.Black
                        val card = if (night.value) Color(0xFF101418) else Color.White
                        if (app.value) {
                            UvirRestoreDefaultsConfirmationDialog(card, primary, Color.Magenta,
                                onRestoreDefaults = { error("No real app reset in this fixture") },
                                onRestored = {}, onDismissRequest = {})
                        } else {
                            SensorRestoreConfirmation(profiles, indicators, false, {}, rememberCoroutineScope(),
                                onRestoreSensor = { error("No real sensor reset in this fixture") },
                                onDismissRequest = {}, card, primary, Color.Magenta)
                        }
                    }
                }
            }
        }
        for (dark in listOf(false, true)) for (short in listOf(false, true)) for (appReset in listOf(false, true)) {
            compose.runOnIdle { night.value = dark; compact.value = short; app.value = appReset; epoch.value++ }
            val prefix = if (appReset) "app" else "sensor"
            val scroll = compose.onNodeWithTag("uvir_dialog_scroll")
            val range = scroll.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]
            val overflow = range.maxValue() > 2f * density
            if (!appReset || short) assertTrue("Expected overflow for $prefix, short=$short", overflow)
            assertEquals("Scrollbar visibility must match overflow for $prefix, short=$short",
                overflow, hasScrollbar(prefix, density))
            if (!appReset) {
                compose.onNodeWithText("Sensor 32").performScrollTo().assertIsDisplayed()
                assertTrue(scroll.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value() > 0f)
                assertTrue(hasScrollbar(prefix, density))
            }
            val label = context.getString(if (appReset) R.string.restore_app_settings_confirm else R.string.sensor_restore_confirm)
            val instruction = context.getString(R.string.hold_action_seconds_confirmation, label, 5)
            compose.onNodeWithText(instruction).performScrollTo().assertIsDisplayed()
            compose.onNodeWithContentDescription(label).assertIsDisplayed()
        }
    }

    private fun hasScrollbar(prefix: String, density: Float): Boolean {
        val dialog = compose.onNodeWithTag("${prefix}_reset_dialog")
        val body = compose.onNodeWithTag("uvir_dialog_scroll")
        val dialogBounds = dialog.getUnclippedBoundsInRoot()
        val bodyBounds = body.getUnclippedBoundsInRoot()
        val pixels = dialog.captureToImage().toPixelMap()
        val x = (pixels.width - 3.5f * density).roundToInt().coerceIn(0, pixels.width - 1)
        val top = ((bodyBounds.top - dialogBounds.top).value * density).roundToInt().coerceIn(0, pixels.height - 1)
        val bottom = ((bodyBounds.bottom - dialogBounds.top).value * density).roundToInt().coerceIn(top, pixels.height)
        return (top until bottom).any { y ->
            val color = pixels[x, y]
            color.red > color.green + 0.15f && color.blue > color.green + 0.15f
        }
    }
}
