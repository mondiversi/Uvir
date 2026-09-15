package me.mondiversi.uvir

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import kotlin.math.abs

/** Isolated UI only: never opens the app database or sends sensor commands. */
class UvirRecordListPressTargetsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun sessionHeaderKeepsTapAndLongPressSeparateInBothThemesAndDirections() {
        val dark = mutableStateOf(false)
        val rtl = mutableStateOf(false)
        var taps = 0
        var longPresses = 0
        compose.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides
                if (rtl.value) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                MaterialTheme(colorScheme = if (dark.value) darkColorScheme() else lightColorScheme()) {
                    Row(Modifier.width(280.dp).testTag("session")
                        .uvirRecordSessionHeaderPressTarget(
                            onClick = { taps++ }, onLongClick = { longPresses++ })
                        .padding(UvirRecordSessionHeaderContentPadding),
                        verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(24.dp))
                        Text("Session", Modifier.weight(1f))
                        Box(Modifier.size(32.dp))
                    }
                }
            }
        }
        val row = compose.onNodeWithTag("session")
        for (night in listOf(false, true)) for (rightToLeft in listOf(false, true)) {
            compose.runOnIdle { dark.value = night; rtl.value = rightToLeft }
            val beforeTaps = taps
            val beforeLong = longPresses
            row.performTouchInput { longClick(center) }
            assertEquals(beforeLong + 1, longPresses)
            assertEquals("Long press must select, not also open the session", beforeTaps, taps)
            row.performTouchInput { click(center) }
            assertEquals(beforeTaps + 1, taps)
        }
    }

    @Test fun wholeCardPaddingRespondsAndLongPressKeepsItsSeparateAction() {
        val dark = mutableStateOf(false)
        val rtl = mutableStateOf(false)
        var taps = 0
        var longPresses = 0
        compose.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides
                if (rtl.value) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                MaterialTheme(colorScheme = if (dark.value) darkColorScheme() else lightColorScheme()) {
                    Card(Modifier.width(280.dp).height(140.dp).testTag("record")
                        .uvirRecordListPressTarget({ taps++ }, { longPresses++ })) {
                        Box(Modifier.fillMaxSize().padding(24.dp)) { Text("Record") }
                    }
                }
            }
        }
        val card = compose.onNodeWithTag("record")
        for (night in listOf(false, true)) for (rightToLeft in listOf(false, true)) {
            compose.runOnIdle { dark.value = night; rtl.value = rightToLeft }
            val before = taps
            card.performTouchInput { click(Offset(4f, height / 2f)) }
            card.performTouchInput { click(Offset(width - 4f, height / 2f)) }
            assertEquals(before + 2, taps)
            val beforeLong = longPresses
            card.performTouchInput { longClick(center) }
            assertEquals(beforeLong + 1, longPresses)
            assertEquals("Long press must not also open the detail", before + 2, taps)
        }
    }

    @Test fun sessionRowAndCheckboxToggleSelectionOnceIncludingBothVerticalGaps() {
        val selected = mutableStateOf(false)
        var calls = 0
        val toggle: () -> Unit = { selected.value = !selected.value; calls++ }
        compose.setContent {
            MaterialTheme {
                Row(Modifier.width(280.dp).testTag("session")
                    .uvirRecordSessionHeaderPressTarget(toggle).padding(UvirRecordSessionHeaderContentPadding),
                    verticalAlignment = Alignment.CenterVertically) {
                    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                        TriStateCheckbox(if (selected.value) ToggleableState.On else ToggleableState.Off,
                            onClick = toggle, modifier = Modifier.size(24.dp))
                    }
                    Text("Session", Modifier.weight(1f))
                    Box(Modifier.size(32.dp))
                }
            }
        }
        val row = compose.onNodeWithTag("session")
        row.performTouchInput { click(Offset(width / 2f, height - 2f)) }
        compose.onNode(isToggleable()).assertIsOn()
        assertEquals(1, calls)
        compose.onNode(isToggleable()).performClick().assertIsOff()
        assertEquals("Nested checkbox must not trigger the parent twice", 2, calls)
        row.performTouchInput { click(Offset(width / 2f, height / 2f)) }
        compose.onNode(isToggleable()).assertIsOn()
        assertEquals(3, calls)
        row.performTouchInput { click(Offset(width / 2f, 2f)) }
        compose.onNode(isToggleable()).assertIsOff()
        assertEquals("The upper and lower gaps must both be interactive", 4, calls)
    }

    @Test fun sessionHeaderHasSymmetricVerticalInsetsAndMirroredLeadingGap() {
        compose.setContent {
            MaterialTheme {
                Row(Modifier.width(280.dp).testTag("header")
                    .uvirRecordSessionHeaderPressTarget({})
                    .padding(UvirRecordSessionHeaderContentPadding),
                    verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(24.dp).testTag("badge"))
                    Text("Session", Modifier.weight(1f))
                    Box(Modifier.size(32.dp).testTag("arrow"))
                }
            }
        }
        val header = compose.onNodeWithTag("header").fetchSemanticsNode().boundsInRoot
        val badge = compose.onNodeWithTag("badge", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val arrow = compose.onNodeWithTag("arrow", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertEquals(header.center.y, badge.center.y, 0.6f)
        assertEquals(header.center.y, arrow.center.y, 0.6f)
        assertEquals("Content must not be flush with the press box", 12f,
            with(compose.density) { (badge.left - header.left).toDp().value }, 0.6f)
        assertEquals(arrow.top - header.top, header.bottom - arrow.bottom, 0.6f)
    }

    @Test fun headerGapMatchesTheReducedGapBetweenSessionCards() {
        var header = Rect.Zero
        var firstCard = Rect.Zero
        var secondCard = Rect.Zero
        compose.setContent {
            MaterialTheme {
                Column(Modifier.width(280.dp)) {
                    Row(Modifier.fillMaxWidth()
                        .onGloballyPositioned { header = it.boundsInRoot() }
                        .uvirRecordSessionHeaderPressTarget({})
                        .padding(UvirRecordSessionHeaderContentPadding),
                        verticalAlignment = Alignment.CenterVertically) {
                        SessionIdBadge(1, MaterialTheme.colorScheme.onSurface)
                        Text("Session", Modifier.weight(1f))
                        Box(Modifier.size(32.dp))
                    }
                    Spacer(Modifier.height(UvirRecordSessionItemGap))
                    Box(Modifier.fillMaxWidth().height(40.dp)
                        .onGloballyPositioned { firstCard = it.boundsInRoot() })
                    Spacer(Modifier.height(UvirRecordSessionItemGap))
                    Box(Modifier.fillMaxWidth().height(40.dp)
                        .onGloballyPositioned { secondCard = it.boundsInRoot() })
                }
            }
        }
        compose.waitForIdle()
        val firstGap = firstCard.top - header.bottom
        val nextGap = secondCard.top - firstCard.bottom
        assertEquals("Header gap must match the gap between records", nextGap, firstGap, 0.6f)
        assertEquals(5f, with(compose.density) { firstGap.toDp().value }, 0.6f)
    }

    @Test fun standardPressEffectCoversCardPaddingWithoutSpillingOutsideCorners() {
        val dark = mutableStateOf(false)
        compose.setContent {
            MaterialTheme(colorScheme = if (dark.value) darkColorScheme() else lightColorScheme()) {
                Box(Modifier.background(MaterialTheme.colorScheme.background)) {
                    Card(Modifier.width(280.dp).height(140.dp).testTag("record")
                        .uvirRecordListPressTarget({}, {})) {
                        Box(Modifier.fillMaxSize().padding(24.dp)) { Text("Record") }
                    }
                }
            }
        }
        val card = compose.onNodeWithTag("record")
        for (night in listOf(false, true)) {
            compose.runOnIdle { dark.value = night }
            compose.waitForIdle()
            val before = card.captureToImage().toPixelMap()
            compose.mainClock.autoAdvance = false
            card.performTouchInput { down(center) }
            compose.mainClock.advanceTimeBy(300)
            // Android's native ripple runs on the render thread, not the Compose test clock.
            android.os.SystemClock.sleep(350L)
            val pressed = card.captureToImage().toPixelMap()
            val x = 4
            val y = pressed.height / 2
            val old = before[x, y]
            val current = pressed[x, y]
            val change = maxOf(abs(old.red - current.red), abs(old.green - current.green),
                abs(old.blue - current.blue))
            assertTrue("Normal press indication reaches outer card padding, night=$night, change=$change, before=$old, pressed=$current", change > 0.015f)
            assertEquals("Top-left corner stays outside the rounded indication", before[0, 0], pressed[0, 0])
            card.performTouchInput { up() }
            compose.mainClock.autoAdvance = true
            compose.waitForIdle()
        }
    }
    @Test fun sessionRailIncludesHeaderGapAndEveryCardWithMirroredEdges() {
        val rtl = mutableStateOf(false)
        val rail = mutableStateOf(androidx.compose.ui.graphics.Color(0xFF43A047))
        compose.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides
                if (rtl.value) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                Column(Modifier.width(280.dp).background(androidx.compose.ui.graphics.Color.White)
                    .testTag("rail")) {
                    Column(Modifier.fillMaxWidth().uvirRecordSessionRail(rail.value, true, false)) {
                        Row(Modifier.fillMaxWidth().height(38.dp)
                            .uvirRecordSessionHeaderPressTarget({}).padding(UvirRecordSessionHeaderContentPadding)) {
                            Text("Session")
                        }
                        Spacer(Modifier.height(UvirRecordSessionItemGap))
                        Box(Modifier.fillMaxWidth().height(45.dp)
                            .padding(start = 14.dp, bottom = UvirRecordSessionItemGap))
                    }
                    Box(Modifier.fillMaxWidth().height(45.dp)
                        .uvirRecordSessionRail(rail.value, false, true)
                        .padding(start = 14.dp, bottom = UvirRecordSessionItemGap))
                }
            }
        }
        for (color in listOf(androidx.compose.ui.graphics.Color(0xFF43A047),
            androidx.compose.ui.graphics.Color(0xFFF28C28))) for (rightToLeft in listOf(false, true)) {
            compose.runOnIdle { rtl.value = rightToLeft; rail.value = color }
            val pixels = compose.onNodeWithTag("rail").captureToImage().toPixelMap()
            val inset = with(compose.density) { 4.dp.toPx() }.toInt()
            val x = if (rightToLeft) pixels.width - inset else inset
            val top = with(compose.density) { 2.dp.toPx() }.toInt()
            val bottom = pixels.height - with(compose.density) { 6.dp.toPx() }.toInt()
            for (y in top until bottom) {
                val actual = pixels[x, y]
                assertEquals("Rail must be continuous from header to last card", color.red, actual.red, 0.03f)
                assertEquals(color.green, actual.green, 0.03f)
                assertEquals(color.blue, actual.blue, 0.03f)
            }
            val outside = pixels[x, pixels.height - 2]
            assertEquals("Last record gap must not join the next session", 1f, outside.red, 0.01f)
            assertEquals(1f, outside.green, 0.01f)
            assertEquals(1f, outside.blue, 0.01f)
        }
    }

    @Test fun sessionHeaderAndRecordHaveTheSameFootprintOutsideTheRail() {
        val rtl = mutableStateOf(false)
        var headerRect: Rect? = null
        var cardRect: Rect? = null
        compose.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides
                if (rtl.value) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                Column(Modifier.width(280.dp)) {
                    Row(Modifier.fillMaxWidth()
                        .padding(start = UvirRecordSessionContentInset)
                        .onGloballyPositioned { headerRect = it.boundsInRoot() }
                        .uvirRecordSessionHeaderPressTarget({})
                        .padding(UvirRecordSessionHeaderContentPadding),
                        verticalAlignment = Alignment.CenterVertically) {
                        SessionIdBadge(1L, androidx.compose.ui.graphics.Color.Black)
                        Text("Session", Modifier.weight(1f))
                        Box(Modifier.size(32.dp))
                    }
                    Spacer(Modifier.height(UvirRecordSessionItemGap))
                    Box(Modifier.fillMaxWidth().padding(start = UvirRecordSessionContentInset,
                        bottom = UvirRecordSessionItemGap)) {
                        Card(Modifier.fillMaxWidth().height(60.dp)
                            .onGloballyPositioned { cardRect = it.boundsInRoot() }) {
                            Text("Record")
                        }
                    }
                }
            }
        }
        for (rightToLeft in listOf(false, true)) {
            compose.runOnIdle { rtl.value = rightToLeft }
            compose.runOnIdle {
                assertEquals(cardRect!!.left, headerRect!!.left, 0.6f)
                assertEquals(cardRect!!.right, headerRect!!.right, 0.6f)
                assertTrue("The header must stay a compact row, not a full-height card",
                    headerRect!!.height < cardRect!!.height)
                assertEquals(with(compose.density) { UvirRecordSessionItemGap.toPx() },
                    cardRect!!.top - headerRect!!.bottom, 0.6f)
            }
        }
    }

}
