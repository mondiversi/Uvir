package me.mondiversi.uvir

import android.content.res.Configuration
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
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
import kotlin.math.abs

/** UI-only regression tests: no real preferences, database or sensor commands. */
class UvirScrollbarAndDestructiveColorsTest {
    @get:Rule val compose = createComposeRule()

    @Composable private fun Theme(night: Boolean, rtl: Boolean = false, content: @Composable () -> Unit) {
        val configuration = Configuration(LocalConfiguration.current).apply {
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                if (night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
        }
        CompositionLocalProvider(LocalConfiguration provides configuration,
            LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
            MaterialTheme { UvirActionTheme(content) }
        }
    }

    @Test fun lazyScrollbarHandlesKeyboardSizedTracksInBothThemesAndDirections() {
        val height = mutableStateOf(21.dp) // 71px at the reporting phone's density, below the 28dp minimum.
        val night = mutableStateOf(false)
        val rtl = mutableStateOf(false)
        compose.setContent {
            Theme(night.value, rtl.value) {
                val state = rememberLazyListState()
                LazyColumn(Modifier.width(240.dp).height(height.value).testTag("track")
                    .lazyScrollbarOverlay(state, Color.Magenta), state = state) {
                    items(40) { Box(Modifier.fillMaxWidth().height(24.dp).background(Color.Black)) }
                }
            }
        }
        for (dark in listOf(false, true)) for (rightToLeft in listOf(false, true)) {
            compose.runOnIdle { night.value = dark; rtl.value = rightToLeft }
            for (size in listOf(21.dp, 1.dp, 0.dp, 20.dp, 28.dp, 300.dp, 21.dp)) {
                compose.runOnIdle { height.value = size }
                compose.waitForIdle()
                if (size > 0.dp) compose.onNodeWithTag("track").captureToImage()
            }
        }
    }

    @Test fun ordinaryScrollbarHandlesTinyOrFullyInsetTracksWithoutDrawingOutsideThem() {
        val height = mutableStateOf(20.dp)
        val inset = mutableStateOf(0.dp)
        val night = mutableStateOf(false)
        val rtl = mutableStateOf(false)
        compose.setContent {
            Theme(night.value, rtl.value) {
                val state = rememberScrollState()
                Column(Modifier.width(240.dp).height(height.value).testTag("track")
                    .scrollbarOverlay(state, Color.Magenta, inset.value, inset.value)
                    .verticalScroll(state)) {
                    Box(Modifier.fillMaxWidth().height(1000.dp).background(Color.Black))
                }
            }
        }
        for (dark in listOf(false, true)) for (rightToLeft in listOf(false, true)) {
            compose.runOnIdle { night.value = dark; rtl.value = rightToLeft }
            for (size in listOf(20.dp, 1.dp, 0.dp, 40.dp, 300.dp)) {
                for (padding in listOf(0.dp, 25.dp)) {
                    compose.runOnIdle { height.value = size; inset.value = padding }
                    compose.waitForIdle()
                    if (size > 0.dp) {
                        val pixels = compose.onNodeWithTag("track").captureToImage().toPixelMap()
                        if (size <= padding * 2) assertEquals("No phantom thumb when insets use the whole track",
                            0, matchingPixels(pixels, Color.Magenta))
                    }
                }
            }
        }
    }

    @Test fun scrollbarTouchUsesTheCurrentHeightAfterKeyboardOrLayoutResize() {
        val height = mutableStateOf(160.dp)
        lateinit var scroll: ScrollState
        compose.setContent {
            Theme(true) {
                scroll = rememberScrollState()
                Column(Modifier.width(240.dp).height(height.value).testTag("track")
                    .scrollbarOverlay(scroll, Color.Magenta).verticalScroll(scroll)) {
                    Box(Modifier.fillMaxWidth().height(1000.dp).background(Color.Black))
                }
            }
        }
        compose.runOnIdle { height.value = 40.dp }
        compose.onNodeWithTag("track").performTouchInput {
            click(Offset(width - 2f, this.height / 2f))
        }
        compose.runOnIdle {
            assertEquals("Middle of resized track must still mean middle of content",
                scroll.maxValue / 2f, scroll.value.toFloat(), 3f)
        }
    }

    @Test fun animatedListResizeAndRapidFilterResultChangesStaySafe() {
        compose.mainClock.autoAdvance = false
        val height = mutableStateOf(160.dp)
        val count = mutableIntStateOf(80)
        compose.setContent {
            Theme(true) {
                val state = rememberLazyListState()
                val animatedHeight by animateDpAsState(height.value, tween(300))
                LazyColumn(Modifier.width(240.dp).height(animatedHeight).testTag("track")
                    .lazyScrollbarOverlay(state, Color.Magenta), state = state) {
                    items(count.intValue) { Box(Modifier.fillMaxWidth().height(24.dp).background(Color.Black)) }
                }
            }
        }
        for (target in listOf(0.dp, 160.dp, 21.dp, 160.dp, 0.dp, 21.dp)) {
            compose.runOnIdle { height.value = target }
            repeat(20) { frame ->
                compose.runOnIdle { count.intValue = listOf(80, 3, 0, 40)[frame % 4] }
                compose.mainClock.advanceTimeBy(16)
                val bounds = compose.onNodeWithTag("track").fetchSemanticsNode().boundsInRoot
                if (bounds.height > 0f) compose.onNodeWithTag("track").captureToImage()
            }
        }
    }

    @Test fun redTextAndIconsUseNightButtonRedWithoutChangingDayOrFilledButtonColors() {
        val night = mutableStateOf(false)
        var action = Color.Unspecified
        var button: ButtonColors? = null
        var disabled = Color.Unspecified
        compose.setContent {
            Theme(night.value) {
                action = UvirDestructiveActionColor
                button = uvirDestructiveButtonColors()
                disabled = uvirDisabledActionContentColor()
                Text("Palette")
            }
        }
        val dayDisabled = disabled
        assertEquals(Color(0xFFD32F2F), action)
        assertEquals(Color(0xFFCA3434), button!!.containerColor)
        assertEquals(dayDisabled, button!!.disabledContentColor)
        compose.runOnIdle { night.value = true }
        compose.waitForIdle()
        assertEquals(Color(0xFFD94343), action)
        assertEquals(action, button!!.containerColor)
        assertEquals(Color.White, button!!.contentColor)
        assertEquals(Color(0xFF6B6B74), button!!.disabledContainerColor)
        assertEquals(Color(0xFFF4F4F6), button!!.disabledContentColor)
    }

    @Test fun actualTextAndTrashIconDrawTheAdaptiveRedInBothThemes() {
        val night = mutableStateOf(false)
        compose.setContent {
            Theme(night.value) {
                Column(Modifier.width(240.dp).background(Color.Black)) {
                    TextButton({}, colors = ButtonDefaults.textButtonColors(contentColor = UvirDestructiveActionColor)) {
                        Text("Delete")
                    }
                    UvirTitleActionIcon(MenuIconType.DELETE, Modifier.testTag("trash"), UvirDestructiveActionColor)
                    UvirTitleActionIcon(MenuIconType.DELETE, Modifier.testTag("disabled-trash"), Color.Gray)
                }
            }
        }
        for (dark in listOf(false, true)) {
            compose.runOnIdle { night.value = dark }
            val expected = if (dark) Color(0xFFD94343) else Color(0xFFD32F2F)
            val layouts = mutableListOf<TextLayoutResult>()
            compose.onNodeWithText("Delete", useUnmergedTree = true)
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertEquals(expected, layouts.single().layoutInput.style.color)
            assertTrue(matchingPixels(compose.onNodeWithTag("trash").captureToImage().toPixelMap(), expected) > 10)
            assertTrue(matchingPixels(compose.onNodeWithTag("disabled-trash").captureToImage().toPixelMap(), Color.Gray) > 10)
        }
    }

    @Test fun redFloatingStopButtonSurfaceIsUnchangedInBothThemes() {
        val night = mutableStateOf(false)
        compose.setContent {
            Theme(night.value) { UvirStopAllAlertsFloatingButton(true, {}, Modifier.testTag("stop")) }
        }
        for (dark in listOf(false, true)) {
            compose.runOnIdle { night.value = dark }
            val pixels = compose.onNodeWithTag("stop").captureToImage().toPixelMap()
            assertTrue(matchingPixels(pixels, Color(0xFFD32F2F)) > pixels.width * pixels.height / 4)
            assertEquals(0, matchingPixels(pixels, Color(0xFFD94343)))
        }
    }

    private fun matchingPixels(pixels: androidx.compose.ui.graphics.PixelMap, color: Color): Int {
        var count = 0
        for (x in 0 until pixels.width) for (y in 0 until pixels.height) {
            val pixel = pixels[x, y]
            if (abs(pixel.red - color.red) < 0.015f && abs(pixel.green - color.green) < 0.015f &&
                abs(pixel.blue - color.blue) < 0.015f) count++
        }
        return count
    }
}
