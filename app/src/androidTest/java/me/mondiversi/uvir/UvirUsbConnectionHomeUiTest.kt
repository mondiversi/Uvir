package me.mondiversi.uvir

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Frozen states only. Does not start the runtime, open USB or access the real archive. */
class UvirUsbConnectionHomeUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun allStatusMessagesUseTheRequestedIndicatorColorsInBothThemes() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertTrue(context.packageName.endsWith(".verification"))
        val runtime = UvirUsbPhoneRuntime(context)
        var state by mutableStateOf(UsbPhoneState())
        var dark by mutableStateOf(false)
        var background = Color.Unspecified
        var dotDiameterPx = 0f
        compose.setContent {
            MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
                val colors = MaterialTheme.colorScheme
                background = colors.background
                dotDiameterPx = with(LocalDensity.current) { 9.dp.toPx() }
                UsbPhoneHome(runtime, state, colors.background, colors.surface, colors.onSurface,
                    colors.onSurfaceVariant, Color.Gray, {}, {}, {}, {}, {}, {})
            }
        }
        val cases = listOf(
            Triple(UsbPhoneState(), R.string.sensor_status_no_sensor, UvirStatusDot.RED),
            Triple(UsbPhoneState(status = UsbPhoneStatus.PERMISSION), R.string.usb_permission, UvirStatusDot.YELLOW),
            Triple(UsbPhoneState(status = UsbPhoneStatus.INITIALIZING), R.string.usb_initializing, UvirStatusDot.YELLOW),
            Triple(UsbPhoneState(status = UsbPhoneStatus.READY, sample = SensorSample()), R.string.usb_ready, UvirStatusDot.GREEN),
            Triple(UsbPhoneState(status = UsbPhoneStatus.READY), R.string.usb_waiting, UvirStatusDot.GREEN),
            Triple(UsbPhoneState(status = UsbPhoneStatus.READY, sample = SensorSample(), automaticSession = 1),
                R.string.sensor_info_activity, UvirStatusDot.GREEN),
            Triple(UsbPhoneState(status = UsbPhoneStatus.READY, overloadSample = SensorSample(qualityFlags = 3)),
                R.string.sensor_saturation, UvirStatusDot.GREEN),
            Triple(UsbPhoneState(status = UsbPhoneStatus.ERROR), R.string.usb_error, UvirStatusDot.RED),
            Triple(UsbPhoneState(status = UsbPhoneStatus.ERROR, simulation = true, sample = SensorSample()),
                R.string.sensor_status_simulated, UvirStatusDot.DEBUG)
        )
        fun close(a: Color, b: Color) = kotlin.math.abs(a.red - b.red) < .025f &&
            kotlin.math.abs(a.green - b.green) < .025f && kotlin.math.abs(a.blue - b.blue) < .025f
        for (theme in listOf(false, true)) for ((snapshot, label, dot) in cases) {
            compose.runOnIdle { dark = theme; state = snapshot }
            compose.onNodeWithTag("home_sensor_status_text", useUnmergedTree = true)
                .assertTextEquals(context.getString(label))
            val pixels = compose.onNodeWithTag("home_sensor_glyph", useUnmergedTree = true)
                .captureToImage().toPixelMap()
            // Existing pulses vary opacity, never hue. The orange simulation dot
            // takes precedence over a physical USB error.
            val expected = (0..50).map { step -> Color(dot.colorArgb).copy(alpha = .5f + step / 100f).compositeOver(background) }
            var matches = 0
            for (x in 0 until pixels.width) for (y in 0 until pixels.height) {
                if (expected.any { close(pixels[x, y], it) }) matches++
            }
            assertTrue("Wrong glyph color for ${context.getString(label)}, night=$theme", matches > 2)
            // Optical alignment follows the text's ink, so the circle is not
            // necessarily centred in the captured layout slot.
            val ink = buildList {
                for (x in 0 until pixels.width) for (y in 0 until pixels.height)
                    if (expected.any { close(pixels[x, y], it) }) add(x to y)
            }
            val width = ink.maxOf { it.first } - ink.minOf { it.first } + 1
            val height = ink.maxOf { it.second } - ink.minOf { it.second } + 1
            assertEquals("Original status dot width", dotDiameterPx, width.toFloat(), 2f)
            assertEquals("Original status dot height", dotDiameterPx, height.toFloat(), 2f)
            val cx = (ink.minOf { it.first } + ink.maxOf { it.first }) / 2f
            val cy = (ink.minOf { it.second } + ink.maxOf { it.second }) / 2f
            val radius = dotDiameterPx / 2 + 1f
            var edgeInk = 0
            for (x in 0 until pixels.width) for (y in 0 until pixels.height) {
                if (kotlin.math.hypot(x - cx, y - cy) > radius &&
                    expected.any { close(pixels[x, y], it) }) edgeInk++
            }
            assertEquals("Status must use a dot instead of a USB or debug glyph", 0, edgeInk)
        }
    }

    @Test fun saturatedVisibleKeepsTheIslandsAndValidUvInsteadOfShowingDisconnected() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertTrue(context.packageName.endsWith(".verification"))
        val runtime = UvirUsbPhoneRuntime(context)
        var dark by mutableStateOf(false)
        var simulation by mutableStateOf(false)
        var sample by mutableStateOf(SensorSample(uva = 2.0,
            qualityFlags = UVIR_QUALITY_VISIBLE_NIR_OUT_OF_RANGE))
        compose.setContent {
            MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
                val colors = MaterialTheme.colorScheme
                UsbPhoneHome(runtime, UsbPhoneState(status = UsbPhoneStatus.READY, simulation = simulation,
                    sample = sample.takeIf { simulation }, overloadSample = sample.takeUnless { simulation }),
                    colors.background, colors.surface, colors.onSurface, colors.onSurfaceVariant,
                    Color.Gray, {}, {}, {}, {}, {}, {})
            }
        }
        val ol = context.getString(R.string.sensor_saturation)
        for (theme in listOf(false, true)) {
            compose.runOnIdle { dark = theme }
            compose.onNodeWithTag("disconnected_island").assertDoesNotExist()
            compose.onNodeWithTag("home_sensor_status_text", useUnmergedTree = true).assertTextEquals(ol)
            compose.onNodeWithTag("live_group_total_VISIBLE", useUnmergedTree = true).assertTextEquals(ol)
            compose.onNodeWithTag("live_group_total_NIR", useUnmergedTree = true).assertTextEquals(ol)
            compose.onNodeWithTag("live_value_BLUE", useUnmergedTree = true).assertTextEquals(ol)
            compose.onNodeWithTag("live_group_total_UV", useUnmergedTree = true)
                .assert(SemanticsMatcher("UV must remain a value") { node ->
                    val text = node.config[androidx.compose.ui.semantics.SemanticsProperties.Text]
                        .joinToString { it.text }
                    text.isNotBlank() && text != ol && text != "—"
                })
        }
        compose.runOnIdle { sample = sample.copy(qualityFlags = 3) }
        compose.onNodeWithTag("disconnected_island").assertDoesNotExist()
        compose.onNodeWithTag("live_group_total_UV", useUnmergedTree = true).assertTextEquals(ol)
        compose.runOnIdle { simulation = true }
        compose.onNodeWithTag("home_sensor_status_text", useUnmergedTree = true)
            .assertTextEquals(context.getString(R.string.sensor_status_simulated))
        compose.onNodeWithTag("live_group_total_UV", useUnmergedTree = true)
            .assertTextEquals(context.getString(R.string.sensor_saturation))
    }

    @Test fun disabledCaptureUsesNeutralContainerAndGlyphInBothThemes() {
        var night by mutableStateOf(false)
        var container = Color.Unspecified
        var content = Color.Unspecified
        compose.setContent {
            val configuration = android.content.res.Configuration(LocalConfiguration.current).apply {
                uiMode = (uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK.inv()) or
                    if (night) android.content.res.Configuration.UI_MODE_NIGHT_YES else android.content.res.Configuration.UI_MODE_NIGHT_NO
            }
            CompositionLocalProvider(LocalConfiguration provides configuration) {
                MaterialTheme(colorScheme = (if (night) darkColorScheme() else lightColorScheme())
                    .copy(primary = Color.Magenta, onPrimary = Color.Magenta)) {
                    container = uvirDisabledActionContainerColor()
                    content = uvirDisabledActionContentColor()
                    Box(Modifier.testTag("disabled_capture")) {
                        UvirHomeCaptureMenu(false, {}, {}, {}, manualEnabled = false)
                    }
                }
            }
        }
        fun close(a: Color, b: Color) = kotlin.math.abs(a.red - b.red) < .02f &&
            kotlin.math.abs(a.green - b.green) < .02f && kotlin.math.abs(a.blue - b.blue) < .02f
        for (theme in listOf(false, true)) {
            compose.runOnIdle { night = theme }
            val pixels = compose.onNodeWithTag("disabled_capture").captureToImage().toPixelMap()
            var glyphPixels = 0
            var purplePixels = 0
            val glyph = content.compositeOver(container)
            for (x in 0 until pixels.width) for (y in 0 until pixels.height) {
                if (close(pixels[x, y], glyph)) glyphPixels++
                if (close(pixels[x, y], Color.Magenta)) purplePixels++
            }
            assertTrue("Disabled capture glyph must use the neutral disabled color, night=$theme", glyphPixels > 10)
            assertEquals("Disabled capture cannot retain primary purple, night=$theme", 0, purplePixels)
        }
    }

    @Test fun everyUnavailableStateUsesTheSameCenteredIslandWithoutAReconnectCard() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertTrue(context.packageName.endsWith(".verification"))
        val runtime = UvirUsbPhoneRuntime(context)
        var state by mutableStateOf(UsbPhoneState())
        var dark by mutableStateOf(false)
        compose.setContent {
            MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
                val colors = MaterialTheme.colorScheme
                Surface(Modifier.fillMaxSize().testTag("home_test_root")) {
                    UsbPhoneHome(runtime, state, colors.background, colors.surface, colors.onSurface,
                        colors.onSurfaceVariant, Color.Gray, {}, {}, {}, {}, {}, {})
                }
            }
        }
        for (theme in listOf(false, true)) {
            for (status in UsbPhoneStatus.entries) {
                compose.runOnIdle {
                    dark = theme
                    state = UsbPhoneState(status = status, error = "Technical error stays in diagnostics")
                }
                val island = compose.onNodeWithTag("disconnected_island").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
                val root = compose.onNodeWithTag("home_content").fetchSemanticsNode().boundsInRoot
                assertEquals("Centered for $status", root.center.y, island.center.y, 6f)
                compose.onNodeWithText(context.getString(R.string.home_sensor_disconnected_title)).assertIsDisplayed()
                compose.onNodeWithText(context.getString(R.string.usb_retry), ignoreCase = true).assertDoesNotExist()
                compose.onNodeWithText(context.getString(R.string.usb_hardware_pending)).assertDoesNotExist()
                compose.onNodeWithText("Technical error stays in diagnostics").assertDoesNotExist()
                compose.onNodeWithTag("home_capture_menu").assertExists()
            }
        }
        compose.runOnIdle { state = UsbPhoneState(status = UsbPhoneStatus.READY, sample = SensorSample()) }
        compose.onNodeWithTag("disconnected_island").assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.usb_retry), ignoreCase = true).assertDoesNotExist()
    }
}
