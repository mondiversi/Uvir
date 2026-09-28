package me.mondiversi.uvir

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Isolated colors: no preferences/database changes and no hardware commands. */
class UvirDisabledDebugColorsTest {
    @get:Rule val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val dark = mutableStateOf(false)
    private val enabled = mutableStateOf(false)
    private val calls = AtomicInteger()
    private val info = UvirSensorRuntimeInfo(deviceId = "COLOR_TEST", firmwareVersion = "0.5.70")

    @Composable private fun Fixture(content: @Composable (Color, Color, Color) -> Unit) {
        val view = LocalView.current
        DisposableEffect(view) {
            val previous = view.keepScreenOn
            view.keepScreenOn = true
            onDispose { view.keepScreenOn = previous }
        }
        val configuration = Configuration(LocalConfiguration.current).apply {
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                if (dark.value) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
        }
        // Uvir controls must respect its explicit night colors even when a
        // Material default would supply a low-contrast light-theme color.
        CompositionLocalProvider(LocalConfiguration provides configuration) {
            MaterialTheme(colorScheme = lightColorScheme()) {
                content(
                    if (dark.value) Color(0xFFF5F7F8) else Color(0xFF101418),
                    if (dark.value) Color(0xFFB0BEC5) else Color(0xFF546E7A),
                    if (dark.value) Color(0xFF202226) else Color.White
                )
            }
        }
    }

    private fun matches(actual: Color, expected: Color) =
        abs(actual.red - expected.red) < 0.035f &&
            abs(actual.green - expected.green) < 0.035f &&
            abs(actual.blue - expected.blue) < 0.035f

    @Test fun sensorInfoMatchesTextColorInBothThemesAndStates() {
        compose.setContent {
            Fixture { primary, secondary, card ->
                Column(Modifier.fillMaxWidth().background(card)) {
                    UvirSensorInfoButton(
                        primaryText = primary, secondaryText = secondary,
                        enabled = enabled.value,
                        onClick = { calls.incrementAndGet() },
                        modifier = Modifier.testTag("sensor_info_action")
                    )
                }
            }
        }
        for (night in listOf(false, true)) for (active in listOf(false, true)) {
            compose.runOnIdle { dark.value = night; enabled.value = active }
            val card = if (night) Color(0xFF202226) else Color.White
            val expected = if (active) {
                if (night) Color(0xFFF5F7F8) else Color(0xFF101418)
            } else {
                (if (night) Color(0xFFB0BEC5) else Color(0xFF546E7A))
                    .copy(alpha = 0.42f).compositeOver(card)
            }
            val pixels = compose.onNodeWithTag("sensor_info_action", useUnmergedTree = true)
                .captureToImage().toPixelMap()
            var matching = 0
            for (y in 0 until pixels.height) for (x in 0 until pixels.width) {
                if (matches(pixels[x, y], expected)) matching++
            }
            assertTrue("Info/text color: night=$night, enabled=$active", matching > 4)
        }
        assertEquals(0, calls.get())
    }

    @Test fun disabledDiagnosticAndPlaybackUseSharedActionColors() {
        compose.setContent {
            Fixture { primary, secondary, card ->
                Column(Modifier.fillMaxWidth().background(card)) {
                    UvirSensorDiagnosticsContent(enabled.value, info, SensorConnectionMode.USB,
                        "Color test", card, primary, secondary,
                        onProbe = { _, _ -> calls.incrementAndGet(); UvirDiagnosticProbe(UvirDiagnosticOutcome.TIMEOUT) })
                    UvirDebugPerformanceContent(context, enabled.value, true, true, 0,
                        primary, secondary,
                        onStart = { calls.incrementAndGet(); false },
                        onStop = { calls.incrementAndGet(); false })
                }
            }
        }
        for (night in listOf(false, true)) {
            compose.runOnIdle { dark.value = night; enabled.value = false }
            val container = if (night) Color(0xFF6B6B74) else Color(0xFFE2E3E4)
            val foreground = if (night) Color(0xFFF4F4F6) else
                lightColorScheme().onSurface.copy(alpha = 0.38f).compositeOver(container)
            for (key in listOf(R.string.debug_diagnostic, R.string.debug_performance_play)) {
                val label = context.getString(key)
                val button = compose.onNodeWithText(label).assertIsNotEnabled()
                val pixels = button.captureToImage().toPixelMap()
                var surface = 0
                for (y in 0 until pixels.height) for (x in 0 until pixels.width) {
                    if (matches(pixels[x, y], container)) surface++
                }
                assertTrue("Shared disabled surface: $label, night=$night", surface > 100)
                val text = compose.onNodeWithText(label, useUnmergedTree = true).captureToImage().toPixelMap()
                var glyphs = 0
                for (y in 0 until text.height) for (x in 0 until text.width) {
                    if (matches(text[x, y], foreground)) glyphs++
                }
                assertTrue("Legible disabled label: $label, night=$night", glyphs > 4)
            }
            val description = context.getString(R.string.diagnostic_description)
            val text = compose.onNodeWithText(description, useUnmergedTree = true).captureToImage().toPixelMap()
            val card = if (night) Color(0xFF202226) else Color.White
            val secondary = if (night) Color(0xFFB0BEC5) else Color(0xFF546E7A)
            val expected = secondary.copy(alpha = 0.46f).compositeOver(card)
            var glyphs = 0
            for (y in 0 until text.height) for (x in 0 until text.width) {
                if (matches(text[x, y], expected)) glyphs++
            }
            assertTrue("Legible disabled description: night=$night", glyphs > 4)
            compose.runOnIdle { enabled.value = true }
            compose.onNodeWithText(context.getString(R.string.debug_diagnostic)).assertIsEnabled()
            compose.onNodeWithText(context.getString(R.string.debug_performance_play)).assertIsEnabled()
        }
        assertEquals(0, calls.get())
    }

    @Test fun exportSaveAndShareUseSharedDisabledColorsAndKeepTheirEnabledStyle() {
        compose.setContent {
            Fixture { primary, secondary, card ->
                Column(Modifier.fillMaxWidth().background(card)) {
                    // Each palette gets fresh controls, without a prior test click's ripple.
                    key(dark.value) {
                        UvirExportBottomActions(
                            enabled = enabled.value, primaryText = primary, secondaryText = secondary,
                            onSave = { calls.incrementAndGet() }, onShare = { calls.incrementAndGet() }
                        )
                    }
                }
            }
        }
        var expectedCalls = 0
        for (night in listOf(false, true)) {
            compose.runOnIdle { dark.value = night; enabled.value = false }
            compose.mainClock.advanceTimeBy(400)
            compose.waitForIdle()
            val container = if (night) Color(0xFF6B6B74) else Color(0xFFE2E3E4)
            val foreground = if (night) Color(0xFFF4F4F6) else
                lightColorScheme().onSurface.copy(alpha = 0.38f).compositeOver(container)
            for (key in listOf(R.string.save, R.string.share)) {
                val button = compose.onNodeWithText(context.getString(key)).assertIsNotEnabled()
                val pixels = button.captureToImage().toPixelMap()
                var surfacePixels = 0
                var foregroundPixels = 0
                for (y in 0 until pixels.height) for (x in 0 until pixels.width) {
                    if (matches(pixels[x, y], container)) surfacePixels++
                    if (matches(pixels[x, y], foreground)) foregroundPixels++
                }
                assertTrue("Export disabled background: $key, night=$night", surfacePixels > 100)
                val nonSurfaceColors = if (foregroundPixels <= 4) {
                    val histogram = mutableMapOf<Int, Int>()
                    for (y in 0 until pixels.height) for (x in 0 until pixels.width) {
                        val color = pixels[x, y]
                        if (!matches(color, container)) histogram.merge(color.toArgb(), 1, Int::plus)
                    }
                    histogram.entries.sortedByDescending { it.value }.take(8)
                        .joinToString { "${it.key.toUInt().toString(16)}=${it.value}" }
                } else ""
                assertTrue("Export disabled text/icons: $key, night=$night, pixels=$foregroundPixels, colors=$nonSurfaceColors", foregroundPixels > 4)
                button.performTouchInput { click(center) }
            }
            compose.runOnIdle { assertEquals(expectedCalls, calls.get()); enabled.value = true }
            compose.mainClock.advanceTimeBy(400)
            compose.waitForIdle()
            val primary = if (night) Color(0xFFF5F7F8) else Color(0xFF101418)
            val card = if (night) Color(0xFF202226) else Color.White
            val activeContainer = primary.copy(alpha = if (night) 0.16f else 0.04f).compositeOver(card)
            for (key in listOf(R.string.save, R.string.share)) {
                val button = compose.onNodeWithText(context.getString(key)).assertIsEnabled()
                val pixels = button.captureToImage().toPixelMap()
                var activeSurfacePixels = 0
                for (y in 0 until pixels.height) for (x in 0 until pixels.width) {
                    if (matches(pixels[x, y], activeContainer)) activeSurfacePixels++
                }
                assertTrue("Enabled export style unchanged: $key, night=$night", activeSurfacePixels > 100)
                button.performClick()
                expectedCalls++
                compose.runOnIdle { assertEquals(expectedCalls, calls.get()) }
            }
        }
    }
}
