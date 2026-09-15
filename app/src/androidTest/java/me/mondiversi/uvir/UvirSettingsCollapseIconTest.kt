package me.mondiversi.uvir

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Isolated icon/header only: no application preferences, records or sensor commands. */
class UvirSettingsCollapseIconTest {
    @get:Rule val compose = createComposeRule()

    @Test fun inwardArrowsKeepTheirSizeStrokeAndSymmetry() {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(4f)) {
                Box(Modifier.size(32.dp).background(Color.White).testTag("drawing"), Alignment.Center) {
                    UvirTitleCollapseAllIcon(Modifier.testTag("icon"), tint = Color.Red)
                }
            }
        }
        compose.onNodeWithTag("icon", useUnmergedTree = true)
            .assertWidthIsEqualTo(UvirTitleActionIconSize)
            .assertHeightIsEqualTo(UvirTitleActionIconSize)
        val pixels = compose.onNodeWithTag("drawing").captureToImage().toPixelMap()
        val row = 16 + 3 * 4
        val stroke = (0 until pixels.width).sumOf { x -> (1f - pixels[x, row].green).toDouble() }.toFloat()
        assertEquals(UvirTitleActionIconStrokeWidth.value * 4f, stroke, 0.35f)
        var largestDifference = 0f
        for (y in 0 until pixels.height) for (x in 0 until pixels.width) {
            largestDifference = maxOf(largestDifference,
                kotlin.math.abs(pixels[x, y].green - pixels[x, pixels.height - y - 1].green))
        }
        assertTrue("Arrows must be vertically symmetric", largestDifference < 0.08f)
    }

    @Test fun settingsCollapseButtonKeepsItsCallbackInBothThemesAndDirections() {
        val dark = mutableStateOf(false)
        val direction = mutableStateOf(LayoutDirection.Ltr)
        var clicks = 0
        val description = InstrumentationRegistry.getInstrumentation().targetContext
            .getString(R.string.collapse_all_settings_content_description)
        compose.setContent {
            val view = LocalView.current
            DisposableEffect(view) {
                val wasAwake = view.keepScreenOn
                view.keepScreenOn = true
                onDispose { view.keepScreenOn = wasAwake }
            }
            CompositionLocalProvider(LocalLayoutDirection provides direction.value) {
                MaterialTheme(colorScheme = if (dark.value) darkColorScheme() else lightColorScheme()) {
                    UvirSettingsHeader("Settings") { clicks++ }
                }
            }
        }
        for (night in listOf(false, true)) for (layout in listOf(LayoutDirection.Ltr, LayoutDirection.Rtl)) {
            compose.runOnIdle { dark.value = night; direction.value = layout }
            val titleBounds = compose.onNodeWithText("Settings").fetchSemanticsNode().boundsInRoot
            val buttonBounds = compose.onNodeWithContentDescription(description).fetchSemanticsNode().boundsInRoot
            assertEquals("Collapse button and title share the vertical center",
                titleBounds.center.y, buttonBounds.center.y, 0.5f)
            compose.onNodeWithContentDescription(description).assertIsDisplayed()
                .assertWidthIsEqualTo(UvirTitleActionButtonSize).performClick()
        }
        assertEquals(4, clicks)
    }
}
