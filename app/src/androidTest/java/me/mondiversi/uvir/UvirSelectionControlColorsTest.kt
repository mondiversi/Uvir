package me.mondiversi.uvir

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Palette/interaction fixtures only: never change real settings, records or sensor state. */
class UvirSelectionControlColorsTest {
    @get:Rule val compose = createComposeRule()
    private val night = mutableStateOf(false)
    private val enabled = mutableStateOf(true)
    private val checked = mutableStateOf(true)
    private val value = mutableFloatStateOf(0.5f)
    private var background = Color.Unspecified
    private var accent = Color.Unspecified
    private var onAccent = Color.Unspecified
    private var disabledInk = Color.Unspecified
    private var disabledSliderThumb = Color.Unspecified
    private var taps = 0

    private fun showControls() {
        compose.setContent {
            val view = LocalView.current
            DisposableEffect(view) {
                val previous = view.keepScreenOn
                view.keepScreenOn = true
                onDispose { view.keepScreenOn = previous }
            }
            val config = Configuration(LocalConfiguration.current).apply {
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                    if (night.value) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            }
            CompositionLocalProvider(LocalConfiguration provides config) {
                MaterialTheme(colorScheme = if (night.value) darkColorScheme() else lightColorScheme()) {
                    UvirActionTheme {
                        background = MaterialTheme.colorScheme.background
                        accent = MaterialTheme.colorScheme.primary
                        onAccent = MaterialTheme.colorScheme.onPrimary
                        disabledInk = MaterialTheme.colorScheme.onSurface.copy(alpha = .38f)
                        disabledSliderThumb = disabledInk.compositeOver(MaterialTheme.colorScheme.surface)
                        Column(Modifier.width(300.dp).background(background).testTag("controls")) {
                            Row {
                                Checkbox(checked.value, { checked.value = it; taps++ },
                                    Modifier.testTag("checkbox"), enabled.value, colors = uvirCheckboxColors())
                                TriStateCheckbox(ToggleableState.Indeterminate, { taps++ },
                                    Modifier.testTag("partial"), enabled.value, colors = uvirCheckboxColors())
                                RadioButton(true, { taps++ }, Modifier.testTag("radio"), enabled.value,
                                    colors = uvirRadioButtonColors())
                            }
                            Slider(value.floatValue, { value.floatValue = it; taps++ },
                                Modifier.fillMaxWidth().testTag("slider"), enabled.value,
                                steps = 9, colors = uvirSliderColors())
                            FilterChip(true, { taps++ }, { Text("Choice") }, Modifier.testTag("chip"),
                                enabled.value, colors = uvirSelectionChipColors(uvirNeutralContentColor()))
                        }
                    }
                }
            }
        }
    }

    private fun matches(actual: Color, expected: Color) =
        abs(actual.red - expected.red) < .035f && abs(actual.green - expected.green) < .035f &&
            abs(actual.blue - expected.blue) < .035f

    private fun colorCount(pixels: PixelMap, expected: Color): Int {
        var count = 0
        for (y in 0 until pixels.height) for (x in 0 until pixels.width)
            if (matches(pixels[x, y], expected)) count++
        return count
    }

    @Test fun formSelectionsAndSliderUseThemePurpleWithContrastingMarks() {
        showControls()
        for (dark in listOf(false, true)) {
            compose.runOnIdle { night.value = dark }
            compose.waitForIdle()
            val active = accent
            val onActive = onAccent
            val contrast = (max(active.luminance(), onActive.luminance()) + .05f) /
                (min(active.luminance(), onActive.luminance()) + .05f)
            assertTrue("Checkbox mark must contrast against its selected box", contrast >= 3f)
            for (tag in listOf("checkbox", "partial", "radio", "slider", "chip")) {
                val node = compose.onNodeWithTag(tag).assertIsEnabled()
                val pixels = node.captureToImage().toPixelMap()
                assertTrue("Purple selected palette: $tag, night=$dark", colorCount(pixels, active) > 8)
                if (tag == "checkbox" || tag == "partial") {
                    val radius = with(compose.density) { 6.dp.toPx().toInt() }
                    var marks = 0
                    for (y in pixels.height / 2 - radius until pixels.height / 2 + radius)
                        for (x in pixels.width / 2 - radius until pixels.width / 2 + radius)
                            if (matches(pixels[x, y], onActive)) marks++
                    assertTrue("Contrasting check/partial mark: night=$dark", marks > 4)
                }
            }
            val directory = InstrumentationRegistry.getInstrumentation().targetContext.externalCacheDir
            requireNotNull(directory)
            File(directory, "form-controls-${if (dark) "night" else "day"}.png").outputStream().use {
                compose.onNodeWithTag("controls").captureToImage().asAndroidBitmap()
                    .compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
        }
    }

    @Test fun disabledControlsAreMutedAndCannotBeActivatedByTouch() {
        showControls()
        for (dark in listOf(false, true)) {
            compose.runOnIdle { night.value = dark; enabled.value = false }
            compose.waitForIdle()
            val disabled = disabledInk.compositeOver(background)
            for (tag in listOf("checkbox", "partial", "radio", "slider", "chip")) {
                val node = compose.onNodeWithTag(tag).assertIsNotEnabled()
                val pixels = node.captureToImage().toPixelMap()
                if (tag != "chip") assertTrue("Muted disabled control: $tag, night=$dark",
                    colorCount(pixels, if (tag == "slider") disabledSliderThumb else disabled) > 4)
                assertEquals(0, colorCount(pixels, accent))
                val previous = taps
                node.performTouchInput { click() }
                compose.runOnIdle { assertEquals(previous, taps) }
            }
        }
    }

    @Test fun purplePaletteDoesNotChangeSelectionOrSliderActions() {
        showControls()
        compose.onNodeWithTag("checkbox").assertIsOn().performClick().assertIsOff()
        compose.onNodeWithTag("checkbox").performClick().assertIsOn()
        compose.onNodeWithTag("radio").assertIsSelected().performClick()
        compose.onNodeWithTag("slider").performSemanticsAction(SemanticsActions.SetProgress) { it(.8f) }
        compose.onNodeWithTag("chip").performClick()
        compose.runOnIdle {
            assertEquals(5, taps)
            assertTrue(value.floatValue > .7f)
        }
    }
}
