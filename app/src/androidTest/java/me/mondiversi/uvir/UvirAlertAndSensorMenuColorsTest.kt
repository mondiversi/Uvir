package me.mondiversi.uvir

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Isolated presentation checks: no app database, preferences or sensor commands. */
class UvirAlertAndSensorMenuColorsTest {
    @get:Rule val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val night = mutableStateOf(false)
    private val direction = mutableStateOf(LayoutDirection.Ltr)
    private val enabled = mutableStateOf(true)
    private val metric = mutableStateOf(ThresholdAlertMetric.UVC)
    private val calls = AtomicInteger()
    private val selected = AtomicReference("")
    private var defaultMenuColor = Color.Unspecified

    @Composable private fun Fixture(content: @Composable (Color, Color, Color) -> Unit) {
        val view = LocalView.current
        DisposableEffect(view) {
            val previous = view.keepScreenOn
            view.keepScreenOn = true
            onDispose { view.keepScreenOn = previous }
        }
        val configuration = Configuration(LocalConfiguration.current).apply {
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                if (night.value) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
        }
        CompositionLocalProvider(LocalConfiguration provides configuration,
            LocalLayoutDirection provides direction.value) {
            // Reproduce Uvir's custom night colors over the default Material palette.
            MaterialTheme(colorScheme = lightColorScheme()) {
                val menuColor = MenuDefaults.containerColor
                SideEffect { defaultMenuColor = menuColor }
                content(
                    if (night.value) Color.White else Color(0xFF101418),
                    if (night.value) Color(0xFF90A4AE) else Color(0xFF546E7A),
                    if (night.value) Color(0xFF1C242B) else Color.White
                )
            }
        }
    }

    private fun matches(actual: Color, expected: Color) =
        abs(actual.red - expected.red) < 0.025f &&
            abs(actual.green - expected.green) < 0.025f &&
            abs(actual.blue - expected.blue) < 0.025f

    private fun matchingPixels(tag: String, expected: Color): Int {
        val pixels = compose.onNodeWithTag(tag, useUnmergedTree = true).captureToImage().toPixelMap()
        var count = 0
        for (y in 0 until pixels.height) for (x in 0 until pixels.width) {
            if (matches(pixels[x, y], expected)) count++
        }
        return count
    }

    @Test fun sensorMenuIsSlightlyLighterAtNightAndKeepsDayDefaultsAndSelection() {
        compose.setContent {
            Fixture { primary, secondary, card ->
                UvirSensorSourceDialog(
                    selectedMode = SensorConnectionMode.USB, useFakeSensorData = false,
                    wifiEnabled = true, bluetoothEnabled = true, internetEnabled = true,
                    primaryText = primary, secondaryText = secondary, cardColor = card,
                    sensorInfo = UvirSensorRuntimeInfo(), sensorDisplayName = "Menu test A",
                    sensorProfiles = listOf(UvirSensorProfile(1, "A", "Menu test A", 0, 0),
                        UvirSensorProfile(2, "B", "Menu test B", 0, 0)),
                    selectedSensorDeviceId = "A", sensorSelectionEnabled = true,
                    onSensorSelected = { selected.set(it); calls.incrementAndGet() },
                    sensorConnected = false, sensorPowerOffEnabled = false,
                    onOpenSensorInfo = { error("No hardware actions") },
                    onRequestSensorPowerOff = { error("No hardware actions") },
                    onModeSelected = { error("No connection changes") }, onDismissRequest = {}
                )
            }
        }
        var selections = 0
        for (dark in listOf(false, true)) for (layout in listOf(LayoutDirection.Ltr, LayoutDirection.Rtl)) {
            compose.runOnIdle { night.value = dark; direction.value = layout }
            compose.onNodeWithTag("sensor_selector_arrow", useUnmergedTree = true)
                .performTouchInput { click(center) }
            compose.onNodeWithTag("sensor_profile_menu", useUnmergedTree = true).assertIsDisplayed()
            val container = if (dark) Color(0xFF27323B) else defaultMenuColor
            assertTrue("Menu surface: night=$dark, $layout", matchingPixels("sensor_profile_menu", container) > 100)
            val textColor = if (dark) Color.White else lightColorScheme().onSurface
            for (label in listOf("Menu test B", context.getString(R.string.sensor_associate_action))) {
                val pixels = compose.onNodeWithText(label, useUnmergedTree = true).captureToImage().toPixelMap()
                var ink = 0
                for (y in 0 until pixels.height) for (x in 0 until pixels.width) {
                    if (matches(pixels[x, y], textColor)) ink++
                }
                assertTrue("Readable menu text: $label, night=$dark", ink > 4)
            }
            compose.onNodeWithText("Menu test B").performClick()
            selections++
            assertEquals(selections, calls.get())
            assertEquals("B", selected.get())
            compose.onNodeWithTag("sensor_profile_menu", useUnmergedTree = true).assertDoesNotExist()
        }
    }

    @Test fun alertNamesUseNormalTextAndDotsKeepEveryMetricColorAndRtlOrder() {
        compose.setContent {
            Fixture { primary, secondary, card ->
                Row(Modifier.width(280.dp).background(card), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = false, enabled = enabled.value,
                        onCheckedChange = { calls.incrementAndGet() }, modifier = Modifier.size(40.dp))
                    ThresholdAlertMetricLabel(metric.value, primary, secondary, enabled.value, Modifier.weight(1f))
                }
            }
        }
        for (dark in listOf(false, true)) for (layout in listOf(LayoutDirection.Ltr, LayoutDirection.Rtl)) {
            for (active in listOf(false, true)) for (value in ThresholdAlertMetric.entries) {
                compose.runOnIdle { night.value = dark; direction.value = layout; enabled.value = active; metric.value = value }
                val secondary = if (dark) Color(0xFF90A4AE) else Color(0xFF546E7A)
                val expectedDot = if (active) thresholdAlertMetricDisplayColor(value) else secondary
                val expectedText = if (active) { if (dark) Color.White else Color(0xFF101418) } else secondary
                val dotTag = "threshold_metric_dot_${value.name}"
                val textTag = "threshold_metric_label_${value.name}"
                val pixels = compose.onNodeWithTag(dotTag, useUnmergedTree = true).captureToImage().toPixelMap()
                assertTrue("Exact metric color: $value, night=$dark, enabled=$active",
                    matches(pixels[pixels.width / 2, pixels.height / 2], expectedDot))
                assertTrue("Normal label color: $value, night=$dark, enabled=$active", matchingPixels(textTag, expectedText) > 4)
                val dot = compose.onNodeWithTag(dotTag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                val text = compose.onNodeWithTag(textTag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                assertEquals("Vertically centered dot", text.center.y, dot.center.y, 1f)
                assertTrue("Dot precedes label in $layout", if (layout == LayoutDirection.Ltr)
                    dot.right < text.left else dot.left > text.right)
            }
        }
        assertEquals(0, calls.get())
    }
}
