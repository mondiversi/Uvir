package me.mondiversi.uvir

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Shared selectors only: no sensor commands, preferences, sessions or records. */
class UvirSegmentedSelectionTest {
    @get:Rule val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val night = mutableStateOf(false)
    private val direction = mutableStateOf(LayoutDirection.Ltr)
    private val mode = mutableStateOf(ViewMode.IRRADIANCE)
    private val chart = mutableStateOf(false)
    private val threshold = mutableStateOf(ThresholdAlertDirection.BELOW)
    private var disabledCalls = 0

    private fun primary(dark: Boolean) = if (dark) Color.White else Color.Black
    private fun secondary(dark: Boolean) = if (dark) Color(0xFFB0BEC5) else Color(0xFF546E7A)
    private fun card(dark: Boolean) = if (dark) Color(0xFF202226) else Color.White
    private fun accent(dark: Boolean) = uvirActionColorScheme(
        if (dark) darkColorScheme() else lightColorScheme(), dark
    ).primary

    private fun showSelectors() {
        compose.setContent {
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
            CompositionLocalProvider(
                LocalConfiguration provides configuration,
                LocalLayoutDirection provides direction.value
            ) {
                MaterialTheme(colorScheme = if (night.value) darkColorScheme() else lightColorScheme()) {
                    UvirActionTheme {
                        key(night.value, direction.value) {
                            val primary = primary(night.value)
                            val secondary = secondary(night.value)
                            val card = card(night.value)
                            Column(Modifier.width(300.dp).background(card)) {
                                Box(Modifier.testTag("main-measurement")) {
                                    MeasurementViewSelector(mode.value, { mode.value = it }, card, primary, secondary)
                                }
                                Box(Modifier.testTag("main-presentation")) {
                                    MeasurementDataChartSelector(chart.value, { chart.value = it }, card, primary,
                                        secondary, Modifier.fillMaxWidth())
                                }
                                Box(Modifier.testTag("detail-selectors")) {
                                    // All four saved-record screens use this shared component.
                                    MeasurementDetailViewSelector(mode.value, { mode.value = it }, chart.value,
                                        { chart.value = it }, card, primary, secondary)
                                }
                                Box(Modifier.testTag("threshold-direction")) {
                                    ThresholdDirectionSelector(threshold.value, { threshold.value = it }, card,
                                        primary, secondary, modifier = Modifier.fillMaxWidth())
                                }
                                ViewModeButton(selected = true, alerted = false,
                                    onClick = { disabledCalls++ }, contentDescription = "Disabled selection",
                                    modifier = Modifier.width(140.dp), primaryText = primary,
                                    secondaryText = secondary, enabled = false) {
                                    Text("D")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun option(host: String, label: Int): SemanticsNodeInteraction = compose.onNode(
        hasContentDescription(context.getString(label)) and hasAnyAncestor(hasTestTag(host))
    )

    private fun matches(actual: Color, expected: Color, tolerance: Float = .035f) =
        abs(actual.red - expected.red) < tolerance &&
            abs(actual.green - expected.green) < tolerance &&
            abs(actual.blue - expected.blue) < tolerance

    private fun colorCount(pixels: PixelMap, color: Color): Int {
        var count = 0
        for (y in 0 until pixels.height) for (x in 0 until pixels.width) {
            if (matches(pixels[x, y], color)) count++
        }
        return count
    }

    private fun assertSelectionPalette(host: String, selected: Int, inactive: Int, dark: Boolean) {
        val selectedNode = option(host, selected).assertIsSelected()
        val inactiveNode = option(host, inactive).assertIsNotSelected()
        val density = context.resources.displayMetrics.density
        val fill = uvirSegmentedSelectedContainerColor(primary(dark)).compositeOver(card(dark))
        var pixels = selectedNode.captureToImage().toPixelMap()
        // Native ripple timing is wall-clock based, independent of the Compose clock.
        // Check the resting palette after the physical click feedback has finished.
        compose.waitUntil(timeoutMillis = 2_000) {
            pixels = selectedNode.captureToImage().toPixelMap()
            matches(pixels[(5f * density).toInt(), pixels.height / 2], fill, .01f)
        }
        val inactivePixels = inactiveNode.captureToImage().toPixelMap()
        assertTrue("Subtle neutral selection fill: $host, night=$dark",
            matches(pixels[(5f * density).toInt(), pixels.height / 2], fill, .01f))
        assertTrue("Selected glyph stays neutral: $host, night=$dark",
            colorCount(pixels, primary(dark)) > 4)
        assertEquals("Segmented switches must stay neutral", 0, colorCount(pixels, accent(dark)))
        assertTrue("Unselected glyph stays neutral: $host, night=$dark",
            colorCount(inactivePixels, secondary(dark)) > 4)
        assertEquals("Selection must not change the control height", 40f * density,
            selectedNode.fetchSemanticsNode().boundsInRoot.height, 1f)
        assertEquals("Equal-width options", selectedNode.fetchSemanticsNode().boundsInRoot.width,
            inactiveNode.fetchSemanticsNode().boundsInRoot.width, 1f)
    }

    private fun assertAllPalettes(dark: Boolean, biological: Boolean, showChart: Boolean, above: Boolean) {
        val measurement = if (biological) R.string.biological_effects_view else R.string.irradiance_view
        val otherMeasurement = if (biological) R.string.irradiance_view else R.string.biological_effects_view
        val presentation = if (showChart) R.string.show_live_chart else R.string.show_live_values
        val otherPresentation = if (showChart) R.string.show_live_values else R.string.show_live_chart
        assertSelectionPalette("main-measurement", measurement, otherMeasurement, dark)
        assertSelectionPalette("main-presentation", presentation, otherPresentation, dark)
        assertSelectionPalette("detail-selectors", measurement, otherMeasurement, dark)
        assertSelectionPalette("detail-selectors", presentation, otherPresentation, dark)
        assertSelectionPalette("threshold-direction",
            if (above) R.string.threshold_direction_above else R.string.threshold_direction_below,
            if (above) R.string.threshold_direction_below else R.string.threshold_direction_above, dark)
    }

    @Test fun mainAndSavedRecordSelectorsShareNeutralPaletteInBothThemesAndDirections() {
        showSelectors()
        for (dark in listOf(false, true)) for (layout in listOf(LayoutDirection.Ltr, LayoutDirection.Rtl)) {
            compose.runOnIdle {
                night.value = dark
                direction.value = layout
                mode.value = ViewMode.IRRADIANCE
                chart.value = false
                threshold.value = ThresholdAlertDirection.BELOW
            }
            compose.mainClock.advanceTimeBy(800)
            assertAllPalettes(dark, biological = false, showChart = false, above = false)
            option("main-measurement", R.string.biological_effects_view).performClick()
            option("main-presentation", R.string.show_live_chart).performClick()
            option("threshold-direction", R.string.threshold_direction_above).performClick()
            compose.mainClock.advanceTimeBy(800)
            assertAllPalettes(dark, biological = true, showChart = true, above = true)
            // Also exercise switching from the detail-screen controls themselves.
            option("detail-selectors", R.string.irradiance_view).performClick()
            option("detail-selectors", R.string.show_live_values).performClick()
            compose.mainClock.advanceTimeBy(800)
            assertAllPalettes(dark, biological = false, showChart = false, above = true)
        }
    }

    @Test fun disabledSelectionKeepsNeutralFrameAndCannotInvokeAction() {
        showSelectors()
        for (dark in listOf(false, true)) {
            compose.runOnIdle { night.value = dark }
            val node = compose.onNodeWithContentDescription("Disabled selection")
                .assertIsSelected().assertIsNotEnabled()
            val pixels = node.captureToImage().toPixelMap()
            val fill = uvirSegmentedSelectedContainerColor(primary(dark)).compositeOver(card(dark))
            val ink = secondary(dark).copy(alpha = .46f).compositeOver(fill)
            assertTrue("Disabled glyph stays gray: night=$dark", colorCount(pixels, ink) > 4)
            assertEquals("Disabled selection must not look active", 0, colorCount(pixels, accent(dark)))
            node.performTouchInput { click(center) }
        }
        compose.runOnIdle { assertEquals(0, disabledCalls) }
    }
}
