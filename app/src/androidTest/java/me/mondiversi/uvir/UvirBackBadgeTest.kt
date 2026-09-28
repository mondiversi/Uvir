package me.mondiversi.uvir

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import kotlin.math.abs

/** Isolated rendering and callback counters, without app data or sensor commands. */
class UvirBackBadgeTest {
    @get:Rule val compose = createComposeRule()

    @Test fun backKeepsItsSizeFullTargetOpticalCenterAndThemeContrast() {
        val night = mutableStateOf(false)
        val direction = mutableStateOf(LayoutDirection.Ltr)
        var accent = Color.Unspecified
        var background = Color.Unspecified
        var taps = 0
        val label = InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.navigate_back)
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
            CompositionLocalProvider(LocalConfiguration provides configuration,
                LocalLayoutDirection provides direction.value) {
                MaterialTheme(colorScheme = if (night.value) darkColorScheme() else lightColorScheme()) {
                    UvirActionTheme {
                        accent = MaterialTheme.colorScheme.primary
                        background = MaterialTheme.colorScheme.background
                        Box(Modifier.background(background)) { UvirBackButton { taps++ } }
                    }
                }
            }
        }
        assertEquals(15.36f, UvirTitleBackIconSize.value, .001f)
        for (dark in listOf(false, true)) for (layout in listOf(LayoutDirection.Ltr, LayoutDirection.Rtl)) {
            compose.runOnIdle { night.value = dark; direction.value = layout }
            compose.mainClock.advanceTimeBy(600L)
            val button = compose.onNodeWithContentDescription(label)
                .assertIsEnabled().assertHasClickAction()
                .assertWidthIsEqualTo(40.dp).assertHeightIsEqualTo(40.dp)
            val pixels = button.captureToImage().toPixelMap()
            val outside = with(compose.density) { 8.dp.toPx().toInt() }
            val inside = with(compose.density) { 12.dp.toPx().toInt() }
            assertEquals(background, pixels[outside, pixels.height / 2])
            assertEquals(background, pixels[pixels.width / 2, outside])
            assertEquals(background, pixels[inside, pixels.height / 2])
            assertEquals(background, pixels[pixels.width / 2, inside])
            assertEquals(background, pixels[inside, inside]) // No square corners.
            val glyphColor = accent
            assertTrue("The larger arrow stays visible in either theme",
                (0 until pixels.height).any { y -> (0 until pixels.width).any { x -> pixels[x, y] == glyphColor } })
            // Measure the visible stroke, not its canvas/bounding box. Rounded
            // caps and the overlapping tip make those centers differ.
            val dr = glyphColor.red - background.red
            val dg = glyphColor.green - background.green
            val db = glyphColor.blue - background.blue
            val contrast = dr * dr + dg * dg + db * db
            val radius = with(compose.density) { 6.dp.toPx() }
            val centerX = pixels.width / 2f
            val centerY = pixels.height / 2f
            var weight = 0f
            var weightedX = 0f
            var weightedY = 0f
            for (y in 0 until pixels.height) for (x in 0 until pixels.width) {
                if (abs(x + 0.5f - centerX) > radius || abs(y + 0.5f - centerY) > radius) continue
                val color = pixels[x, y]
                val ink = (((color.red - background.red) * dr + (color.green - background.green) * dg +
                    (color.blue - background.blue) * db) / contrast).coerceIn(0f, 1f)
                weight += ink
                weightedX += (x + 0.5f) * ink
                weightedY += (y + 0.5f) * ink
            }
            assertTrue(weight > 0f)
            val tolerance = with(compose.density) { 0.2.dp.toPx() }
            assertEquals("Optical horizontal center, dark=$dark, $layout", centerX, weightedX / weight, tolerance)
            assertEquals("Optical vertical center, dark=$dark, $layout", centerY, weightedY / weight, tolerance)
            val previous = taps
            // Use the accessibility action between captures to avoid sampling
            // Android's native ripple animation as a resting palette.
            button.performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }
            compose.runOnIdle { assertEquals(previous + 1, taps) }
        }
        val previous = taps
        // The smaller bare arrow must not shrink the actual touch target.
        compose.onNodeWithContentDescription(label).performTouchInput {
            click(Offset(2.dp.toPx(), height / 2f))
        }
        compose.runOnIdle { assertEquals(previous + 1, taps) }
    }
}
