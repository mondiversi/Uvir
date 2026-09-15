package me.mondiversi.uvir

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import me.mondiversi.uvir.ui.theme.Purple80

/** Isolated UI only: no app preferences, database, connection or sensor commands. */
class UvirActionThemeAndMotionTest {
    @get:Rule val compose = createComposeRule()

    @Composable private fun Theme(night: Boolean, rtl: Boolean = false, content: @Composable () -> Unit) {
        val config = Configuration(LocalConfiguration.current).apply {
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                if (night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
        }
        CompositionLocalProvider(LocalConfiguration provides config,
            LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
            MaterialTheme(colorScheme = lightColorScheme()) { UvirActionTheme(content) }
        }
    }

    @Test fun dayPaletteAndNonPurpleNightColorsAreUnchanged() {
        val base = lightColorScheme()
        assertSame(base, uvirActionColorScheme(base, false))
        val night = uvirActionColorScheme(base, true)
        assertEquals(Purple80, night.primary)
        assertEquals(base.background, night.background)
        assertEquals(base.surface, night.surface)
        assertEquals(base.error, night.error)
        assertEquals(base.secondary, night.secondary)
        assertEquals(base.tertiary, night.tertiary)
        assertEquals(base.primaryContainer, night.primaryContainer)
        val contrast = (night.primary.luminance() + 0.05f) / (night.onPrimary.luminance() + 0.05f)
        assertTrue("Filled night actions need readable dark text, not white on pale purple", contrast >= 4.5f)
    }

    @Test fun nightIconsFieldsButtonsAndExpandedTitlesUseFilterPurple() {
        var palette: ColorScheme? = null
        var buttonColors: ButtonColors? = null
        var fieldAccent = Color.Unspecified
        compose.setContent {
            Theme(night = true) {
                palette = MaterialTheme.colorScheme
                buttonColors = uvirPrimaryButtonColors()
                fieldAccent = UvirOutlinedTextFieldColors().focusedIndicatorColor
                Column(Modifier.width(280.dp)) {
                    Row {
                        Box(Modifier.testTag("filter")) { UvirRecordListFilterButton(false, true, {}) }
                        Box(Modifier.testTag("back")) { UvirBackButton({}) }
                    }
                    TextButton({}, Modifier.testTag("action")) { Text("Close action") }
                    FloatingActionButton({}, Modifier.testTag("capture")) {
                        CaptureMeasurementIcon(Modifier.size(25.dp), tint = MaterialTheme.colorScheme.onPrimary)
                    }
                    SettingsSection("Settings", Color.White, Color.Black, Color.Gray,
                        expanded = true, onExpandedChange = {}) { Text("Details") }
                }
            }
        }
        assertEquals(Purple80, palette!!.primary)
        assertEquals(fieldAccent, palette!!.primary)
        assertEquals(fieldAccent, buttonColors!!.containerColor)
        for (text in listOf("Close action", "Settings")) {
            val layouts = mutableListOf<TextLayoutResult>()
            compose.onNodeWithText(text, useUnmergedTree = true)
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertEquals("Shared purple for $text", fieldAccent, layouts.single().layoutInput.style.color)
        }
        for (tag in listOf("filter", "back")) {
            val pixels = compose.onNodeWithTag(tag).captureToImage().toPixelMap()
            var purplePixels = 0
            for (x in 0 until pixels.width) for (y in 0 until pixels.height) {
                val pixel = pixels[x,y]
                if (kotlin.math.abs(pixel.red - Purple80.red) < 0.015f &&
                    kotlin.math.abs(pixel.green - Purple80.green) < 0.015f &&
                    kotlin.math.abs(pixel.blue - Purple80.blue) < 0.015f) purplePixels++
            }
            assertTrue("$tag must actually draw the same night purple", purplePixels > 10)
        }
        val capture = compose.onNodeWithTag("capture").captureToImage().toPixelMap()
        val glyphColor = palette!!.onPrimary
        var glyphPixels = 0
        for (x in 0 until capture.width) for (y in 0 until capture.height) {
            val pixel = capture[x,y]
            if (kotlin.math.abs(pixel.red - glyphColor.red) < 0.015f &&
                kotlin.math.abs(pixel.green - glyphColor.green) < 0.015f &&
                kotlin.math.abs(pixel.blue - glyphColor.blue) < 0.015f) glyphPixels++
        }
        assertTrue("Capture glyph must use the contrasting night content color", glyphPixels > 10)
    }

    @Test fun verticalRevealHasIntermediateHeightsAndCanReverseImmediately() {
        compose.mainClock.autoAdvance = false
        val open = mutableStateOf(false)
        val night = mutableStateOf(false)
        val rtl = mutableStateOf(false)
        compose.setContent {
            Theme(night.value, rtl.value) {
                Column(Modifier.width(260.dp).testTag("root")) {
                    Text("Toggle", Modifier.clickable { open.value = !open.value })
                    UvirVerticalReveal(open.value) { Box(Modifier.fillMaxWidth().height(180.dp)) }
                }
            }
        }
        fun height() = compose.onNodeWithTag("root").fetchSemanticsNode().boundsInRoot.height
        for (dark in listOf(false, true)) for (rightToLeft in listOf(false, true)) {
            compose.runOnIdle { night.value = dark; rtl.value = rightToLeft }
            val closed = height()
            compose.onNodeWithText("Toggle").performClick()
            compose.runOnIdle { assertTrue("State changes immediately", open.value) }
            compose.mainClock.advanceTimeBy(150)
            val halfway = height()
            compose.mainClock.advanceTimeBy(200)
            val full = height()
            assertTrue("Opening has intermediate geometry", halfway > closed + 1 && halfway < full - 1)
            compose.onNodeWithText("Toggle").performClick()
            compose.mainClock.advanceTimeBy(100)
            val closing = height()
            assertTrue("Closing has intermediate geometry", closing > closed + 1 && closing < full - 1)
            compose.onNodeWithText("Toggle").performClick()
            // Allow the interrupted transition to settle, then check its final geometry.
            compose.mainClock.advanceTimeBy(650)
            assertEquals("A reversed animation settles fully open", full, height(), 1f)
            compose.onNodeWithText("Toggle").performClick()
            compose.mainClock.advanceTimeBy(350)
            assertEquals(closed, height(), 1f)
        }
    }

    @Test fun horizontalSelectionRevealIsSmoothInLtrAndRtl() {
        compose.mainClock.autoAdvance = false
        val visible = mutableStateOf(false)
        val rtl = mutableStateOf(false)
        compose.setContent {
            Theme(true, rtl.value) {
                Row(Modifier.testTag("row"), verticalAlignment = Alignment.CenterVertically) {
                    UvirHorizontalReveal(visible.value) {
                        Checkbox(false, {})
                        Spacer(Modifier.width(8.dp))
                    }
                    Text("Row content")
                }
            }
        }
        fun width() = compose.onNodeWithTag("row").fetchSemanticsNode().boundsInRoot.width
        for (rightToLeft in listOf(false, true)) {
            compose.runOnIdle { rtl.value = rightToLeft }
            val closed = width()
            compose.runOnIdle { visible.value = true }
            compose.mainClock.advanceTimeBy(150)
            val halfway = width()
            compose.mainClock.advanceTimeBy(200)
            val full = width()
            assertTrue(halfway > closed + 1 && halfway < full - 1)
            compose.runOnIdle { visible.value = false }
            compose.mainClock.advanceTimeBy(150)
            val closing = width()
            assertTrue(closing > closed + 1 && closing < full - 1)
            compose.mainClock.advanceTimeBy(200)
            assertEquals(closed, width(), 1f)
        }
    }

    @Test fun collapsedChartReopensWithLegendBelowTheChartAndNoDuplicates() {
        compose.mainClock.autoAdvance = false
        val open = mutableStateOf(true)
        var toggles = 0
        compose.setContent {
            Theme(true) {
                UvirCollapsibleChartCard("Chart", expanded = open.value,
                    onToggle = { open.value = !open.value; toggles++ },
                    cardColor = Color.White, primaryText = Color.Black, secondaryText = Color.Gray) {
                    Column(Modifier.fillMaxWidth().padding(12.dp)) {
                        Box(Modifier.fillMaxWidth().height(150.dp).background(Color.Gray).testTag("plot"))
                        Text("Legend", Modifier.testTag("legend"))
                    }
                }
            }
        }
        fun checkLegend() {
            val plot = compose.onNodeWithTag("plot").fetchSemanticsNode().boundsInRoot
            val legend = compose.onNodeWithTag("legend").fetchSemanticsNode().boundsInRoot
            assertTrue("Legend must stay after the plot", legend.top >= plot.bottom - 1f)
            compose.onAllNodesWithText("Legend").assertCountEquals(1)
        }
        checkLegend()
        repeat(3) {
            compose.onNodeWithText("Chart").performClick()
            compose.mainClock.advanceTimeBy(350)
            compose.onNodeWithTag("legend").assertDoesNotExist()
            compose.onNodeWithText("Chart").performClick()
            compose.mainClock.advanceTimeBy(350)
            checkLegend()
        }
        assertEquals(6, toggles)
    }

    @Test fun exitingContentIsNotInteractiveOrAccessible() {
        compose.mainClock.autoAdvance = false
        val visible = mutableStateOf(true)
        var taps = 0
        compose.setContent {
            Column(Modifier.width(260.dp).testTag("root")) {
                Text("Close", Modifier.clickable { visible.value = false })
                UvirVerticalReveal(visible.value) {
                    Box(Modifier.fillMaxWidth().height(100.dp).clickable { taps++ }.testTag("body")) {
                        Text("Body")
                    }
                }
            }
        }
        val root = compose.onNodeWithTag("root").fetchSemanticsNode().boundsInRoot
        val body = compose.onNodeWithTag("body").fetchSemanticsNode().boundsInRoot
        val tap = Offset(body.center.x - root.left, body.top + 5f - root.top)
        compose.onNodeWithText("Close").performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithText("Body").assertDoesNotExist()
        compose.onNodeWithTag("root").performTouchInput { click(tap) }
        compose.runOnIdle { assertEquals("A closing panel must not accept new actions", 0, taps) }
        compose.mainClock.advanceTimeBy(350)
        compose.onNodeWithTag("body").assertDoesNotExist()
    }
}
