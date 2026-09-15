package me.mondiversi.uvir

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import kotlin.math.sqrt
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Isolated drawings and callback counters: no app database, preferences or sensor commands. */
class UvirTitleIconStrokeTest {
    @get:Rule val compose = createComposeRule()

    private fun ink(pixels: PixelMap, x: Int, y: Int): Float {
        val background = pixels[0, 0]
        val baseline = background.red - background.green
        val color = pixels[x, y]
        return ((color.red - color.green - baseline) / (1f - baseline)).coerceIn(0f, 1f)
    }

    @Test
    fun selectionAndDeletionShareStrokeWithoutLocalEmphasis() {
        val dark = mutableStateOf(false)
        val direction = mutableStateOf(LayoutDirection.Ltr)
        val type = mutableStateOf(MenuIconType.SELECT)
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(4f), LocalLayoutDirection provides direction.value) {
                MaterialTheme(colorScheme = if (dark.value) darkColorScheme() else lightColorScheme()) {
                    Box(Modifier.size(32.dp).background(MaterialTheme.colorScheme.surface).testTag("drawing"), Alignment.Center) {
                        UvirTitleActionIcon(type.value, tint = Color.Red)
                    }
                }
            }
        }
        val expectedPixels = 2.21f * 4f
        assertEquals(2.21f, UvirTitleActionIconStrokeWidth.value, 0f)
        for (darkTheme in listOf(false, true)) {
            for (layout in listOf(LayoutDirection.Ltr, LayoutDirection.Rtl)) {
                for (icon in listOf(MenuIconType.SELECT, MenuIconType.DELETE)) {
                    compose.runOnIdle { dark.value = darkTheme; direction.value = layout; type.value = icon }
                    val pixels = compose.onNodeWithTag("drawing").captureToImage().toPixelMap()
                    val inset = 16
                    val size = 96
                    val row = inset + (size * if (icon == MenuIconType.SELECT) 0.40f else 0.57f).toInt()
                    val right = inset + (size * if (icon == MenuIconType.SELECT) 0.24f else 0.37f).toInt()
                    val border = (0..right).sumOf { x -> ink(pixels, x, row).toDouble() }.toFloat()
                    assertEquals("Shared border: $icon, dark=$darkTheme, $layout", expectedPixels, border, 0.35f)
                    if (icon == MenuIconType.SELECT) {
                        val y = inset + (size * 0.48f).toInt()
                        val diagonal = ((inset + (size * 0.50f).toInt())..(inset + (size * 0.74f).toInt()))
                            .sumOf { x -> ink(pixels, x, y).toDouble() }.toFloat()
                        val horizontalFactor = sqrt(1f + (0.28f / 0.30f) * (0.28f / 0.30f))
                        assertEquals("Selection check has no +15% emphasis", expectedPixels, diagonal / horizontalFactor, 0.45f)
                    } else {
                        val x = inset + (size * 0.25f).toInt()
                        val lid = ((inset + (size * 0.20f).toInt())..(inset + (size * 0.35f).toInt()))
                            .sumOf { y -> ink(pixels, x, y).toDouble() }.toFloat()
                        assertEquals("Delete lid has no +15% emphasis", expectedPixels, lid, 0.35f)
                    }
                }
            }
        }
    }

    @Test
    fun onlyBackHasExtraVisualWeightWithoutChangingIconDimensions() {
        val dark = mutableStateOf(false)
        val direction = mutableStateOf(LayoutDirection.Ltr)
        val type = mutableStateOf(0)
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(4f), LocalLayoutDirection provides direction.value) {
                MaterialTheme(colorScheme = if (dark.value) darkColorScheme(primary = Color.Red) else lightColorScheme(primary = Color.Red)) {
                    Box(Modifier.size(56.dp).background(MaterialTheme.colorScheme.surface).testTag("open_drawing"), Alignment.Center) {
                        when (type.value) {
                            0 -> UvirTitleSaveIcon(tint = Color.Red)
                            1 -> DoubleExpansionChevron(true, Color.Red, iconSize = UvirTitleActionIconSize)
                            else -> UvirBackButton {}
                        }
                    }
                }
            }
        }
        assertEquals(2.6f, UvirTitleBackIconStrokeWidth.value, 0f)
        for (darkTheme in listOf(false, true)) {
            for (layout in listOf(LayoutDirection.Ltr, LayoutDirection.Rtl)) {
                for (icon in 0..2) {
                    compose.runOnIdle { dark.value = darkTheme; direction.value = layout; type.value = icon }
                    val pixels = compose.onNodeWithTag("open_drawing").captureToImage().toPixelMap()
                    val inset = 64
                    val size = 96
                    val y = inset + (size * if (icon == 0) 0.50f else 0.36f).toInt()
                    val range = when (icon) {
                        0 -> (inset + 44)..(inset + 80)
                        1 -> 0..(inset + size / 2)
                        else -> 0 until pixels.width
                    }
                    val diagonal = range.sumOf { x -> ink(pixels, x, y).toDouble() }.toFloat()
                    val ratio = when (icon) {
                        0 -> 9f / 10f
                        1 -> 0.23f / 0.16f
                        else -> 0.34f / 0.30f
                    }
                    val horizontalFactor = sqrt(1f + ratio * ratio)
                    val expectedStroke = if (icon == 2) UvirTitleBackIconStrokeWidth else UvirTitleActionIconStrokeWidth
                    assertEquals("Title icon: $icon, dark=$darkTheme, $layout",
                        expectedStroke.value * 4f, diagonal / horizontalFactor, 0.45f)
                }
            }
        }
    }

    @Test
    fun settingsShareAndBackKeepDimensionsAndCallbacksInBothDirectionsAndThemes() {
        val dark = mutableStateOf(false)
        val direction = mutableStateOf(LayoutDirection.Ltr)
        val clicks = IntArray(4)
        val backDescription = InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.navigate_back)
        compose.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides direction.value) {
                MaterialTheme(colorScheme = if (dark.value) darkColorScheme() else lightColorScheme()) {
                    Row {
                        IconButton(onClick = { clicks[0]++ }, Modifier.size(UvirTitleActionButtonSize).testTag("save")) {
                            UvirTitleSaveIcon(Modifier.testTag("save_icon"))
                        }
                        IconButton(onClick = { clicks[1]++ }, Modifier.size(UvirTitleActionButtonSize).testTag("collapse")) {
                            DoubleExpansionChevron(true, MaterialTheme.colorScheme.primary,
                                Modifier.testTag("collapse_icon"), UvirTitleActionIconSize)
                        }
                        IconButton(onClick = { clicks[2]++ }, Modifier.size(UvirTitleActionButtonSize).testTag("share")) {
                            UvirTitleActionIcon(MenuIconType.SHARE, Modifier.testTag("share_icon"))
                        }
                        UvirBackButton { clicks[3]++ }
                    }
                }
            }
        }
        for (darkTheme in listOf(false, true)) {
            for (layout in listOf(LayoutDirection.Ltr, LayoutDirection.Rtl)) {
                compose.runOnIdle { dark.value = darkTheme; direction.value = layout }
                for (tag in listOf("save", "collapse", "share")) {
                    compose.onNodeWithTag(tag).assertIsDisplayed().assertWidthIsEqualTo(UvirTitleActionButtonSize).performClick()
                    compose.onNodeWithTag("${tag}_icon", useUnmergedTree = true)
                        .assertIsDisplayed().assertWidthIsEqualTo(UvirTitleActionIconSize)
                }
                compose.onNodeWithContentDescription(backDescription).assertIsDisplayed().performClick()
            }
        }
        assertEquals(4, clicks[0])
        assertEquals(4, clicks[1])
        assertEquals(4, clicks[2])
        assertEquals(4, clicks[3])
    }
}
