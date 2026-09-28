package me.mondiversi.uvir

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Isolated text only: no user preferences, names, records or sensor commands. */
class UvirScrollingTextTest {
    @get:Rule val compose = createComposeRule()
    private val name = mutableStateOf("M".repeat(50))
    private val direction = mutableStateOf(LayoutDirection.Ltr)
    private val dark = mutableStateOf(false)

    private fun show() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            val view = LocalView.current
            DisposableEffect(view) {
                val previous = view.keepScreenOn
                view.keepScreenOn = true
                onDispose { view.keepScreenOn = previous }
            }
            CompositionLocalProvider(LocalLayoutDirection provides direction.value) {
                MaterialTheme(colorScheme = if (dark.value) darkColorScheme() else lightColorScheme()) {
                    Box(Modifier.width(150.dp).background(if (dark.value) Color.Black else Color.White)) {
                        UvirScrollingText(name.value, Modifier.testTag("name"),
                            color = if (dark.value) Color.White else Color.Black,
                            style = TextStyle(fontSize = 22.sp))
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun range() = compose.onNodeWithText(name.value, useUnmergedTree = true)
        .fetchSemanticsNode().config[SemanticsProperties.HorizontalScrollAxisRange]

    private fun advanceUntil(condition: () -> Boolean) {
        compose.waitUntil(timeoutMillis = 5_000L) {
            compose.mainClock.advanceTimeBy(64)
            condition()
        }
    }

    @Test fun overflowingTextTravelsBothWaysWithFixedViewportInBothDirections() {
        show()
        for (layout in listOf(LayoutDirection.Ltr, LayoutDirection.Rtl)) {
            compose.runOnIdle { direction.value = layout }
            compose.mainClock.advanceTimeBy(32)
            val before = compose.onNodeWithTag("name", true).fetchSemanticsNode().boundsInRoot
            assertTrue(range().maxValue() > 0)
            advanceUntil { range().value() > 0 }
            compose.mainClock.advanceTimeBy(8_100)
            val end = range().value()
            assertEquals(range().maxValue(), end, 1f)
            advanceUntil { range().value() < end - 1f }
            compose.mainClock.advanceTimeBy(8_100)
            assertEquals(0f, range().value(), 1f)
            assertEquals(before, compose.onNodeWithTag("name", true).fetchSemanticsNode().boundsInRoot)
        }
    }

    @Test fun initialEdgeFadesInBothThemesAndDirectionsWithoutHidingTheFullAccessibleName() {
        show()
        for (night in listOf(false, true)) for (layout in listOf(LayoutDirection.Ltr, LayoutDirection.Rtl)) {
            compose.runOnIdle { dark.value = night; direction.value = layout }
            compose.mainClock.advanceTimeBy(32)
            assertTrue(range().maxValue() > 0)
            val pixels = compose.onNodeWithTag("name", true).captureToImage().toPixelMap()
            val edgeColumns = if (layout == LayoutDirection.Ltr) pixels.width - 8 until pixels.width else 0 until 8
            fun contrast(x: Int, y: Int): Float =
                if (night) pixels[x, y].red else 1f - pixels[x, y].red
            val edgeInk = edgeColumns.maxOf { x -> (0 until pixels.height).maxOf { y -> contrast(x, y) } }
            val centerInk = (pixels.width / 3..pixels.width * 2 / 3)
                .maxOf { x -> (0 until pixels.height).maxOf { y -> contrast(x, y) } }
            assertTrue("Text remains fully legible away from the faded edge", centerInk > .9f)
            assertTrue("The overflow edge fades into the theme background", edgeInk < .4f)
            val fullText = compose.onNodeWithText(name.value, useUnmergedTree = true)
                .fetchSemanticsNode().config[SemanticsProperties.Text].single().text
            assertEquals(name.value, fullText)
        }
    }

    @Test fun fittingNameDoesNotMoveAndChangingNameResetsTheOldOffset() {
        show()
        advanceUntil { range().value() > 0 }
        compose.runOnIdle { name.value = "Uvir" }
        compose.mainClock.advanceTimeBy(64)
        assertEquals(0f, range().maxValue(), 0f)
        assertEquals(0f, range().value(), 0f)
        val before = compose.onNodeWithTag("name", true).captureToImage().toPixelMap()
        compose.mainClock.advanceTimeBy(10_000)
        val after = compose.onNodeWithTag("name", true).captureToImage().toPixelMap()
        assertEquals(before.width, after.width)
        assertEquals(before.height, after.height)
        for (y in 0 until before.height) for (x in 0 until before.width) assertEquals(before[x, y], after[x, y])
        compose.runOnIdle { name.value = "N".repeat(60) }
        compose.mainClock.advanceTimeBy(32)
        assertTrue(range().maxValue() > 0)
        assertEquals(0f, range().value(), 0f)
    }
}
