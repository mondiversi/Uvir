package me.mondiversi.uvir

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.UUID
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Real placement animations with synthetic cards and private preferences; no device/archive actions. */
class UvirMainIslandAnimationUiTest {
    @get:Rule val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val preferenceName = "island-motion-${UUID.randomUUID()}-"
    private val isolated = object : ContextWrapper(context) {
        override fun getSharedPreferences(name: String, mode: Int) = context.getSharedPreferences(preferenceName + name, mode)
    }
    private val preferences get() = isolated.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val open = mutableStateOf(true)
    private val tokens = mutableMapOf<SensorGroup, Any>()
    @After fun cleanUp() { context.deleteSharedPreferences(preferenceName + PREFS_NAME) }

    private fun fixture(landscape: Boolean) {
        compose.setContent {
            KeepUvirVerificationScreenOn()
            CompositionLocalProvider(LocalContext provides isolated) {
                MaterialTheme(colorScheme = if (landscape) darkColorScheme() else lightColorScheme()) {
                    val order = rememberUvirMainIslandOrder()
                    val reordering = rememberUvirMainIslandReordering(order.value)
                    val draw: @Composable (SensorGroup) -> Unit = { group ->
                        val token = remember { Any() }
                        SideEffect { tokens[group] = token }
                        val height = when (group) {
                            SensorGroup.UV -> 96.dp
                            SensorGroup.VISIBLE -> 136.dp
                            else -> 80.dp
                        }
                        Row(Modifier.fillMaxWidth().height(height)
                            .background(uvirSpectralColor(when (group) {
                                SensorGroup.UV -> ThresholdAlertMetric.UV_TOTAL
                                SensorGroup.VISIBLE -> ThresholdAlertMetric.VISIBLE_TOTAL
                                else -> ThresholdAlertMetric.NIR_TOTAL
                            })).testTag("motion_island_${group.name}"),
                            verticalAlignment = Alignment.CenterVertically) {
                            Text(group.name, Modifier.weight(1f), color = Color.White)
                            val index = order.value.indexOf(group)
                            UvirMainIslandMoveControls(group, group.name, Color.White,
                                if (index > 0) ({ order.value = moveUvirMainIsland(order.value, group, -1) }) else null,
                                if (index < order.value.lastIndex) ({ order.value = moveUvirMainIsland(order.value, group, 1) }) else null)
                        }
                    }
                    Surface {
                        if (open.value) {
                            LazyColumn(Modifier.width(320.dp).height(520.dp).testTag("motion_root"),
                                verticalArrangement = Arrangement.spacedBy(UvirIslandSpacing)) {
                                if (landscape) item("islands") {
                                    UvirLiveLandscapeIslands({ _, _ -> }, order.value, draw)
                                } else items(order.value, key = { it.name }) { group ->
                                    UvirAnimatedMainIsland(reordering) { draw(group) }
                                }
                            }
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
    }

    private fun island(group: SensorGroup) = compose.onNodeWithTag("motion_island_${group.name}", useUnmergedTree = true)
    private fun position(group: SensorGroup) = island(group).fetchSemanticsNode().boundsInRoot.topLeft
    private fun move(up: Boolean) = compose.onNodeWithTag("main_island_move_${if (up) "up" else "down"}_UV", useUnmergedTree = true).performClick()
    private fun advance(time: Long) { compose.mainClock.advanceTimeBy(time); compose.waitForIdle() }
    private fun capture(name: String) {
        File(context.getExternalFilesDir(null), "main-island-motion-$name.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
    private fun assertBetween(label: String, value: Float, from: Float, to: Float) {
        assertTrue("$label must pass through intermediate positions: $from -> $value -> $to",
            value > minOf(from, to) + 1f && value < maxOf(from, to) - 1f)
    }

    @Test fun portraitChevronsSlideBothCardsThroughIntermediatePositionsAndPersistTheOrder() {
        fixture(false)
        val initial = position(SensorGroup.UV)
        val visible = island(SensorGroup.VISIBLE).fetchSemanticsNode().boundsInRoot
        val uv = island(SensorGroup.UV).fetchSemanticsNode().boundsInRoot
        val targetY = initial.y + visible.height + (visible.top - uv.bottom)
        val identities = tokens.toMap()
        capture("portrait-start")
        move(false)
        compose.mainClock.advanceTimeByFrame()
        advance(100)
        assertBetween("UV vertical slide", position(SensorGroup.UV).y, initial.y, targetY)
        assertBetween("Visible vertical slide", position(SensorGroup.VISIBLE).y, visible.top, initial.y)
        capture("portrait-middle")
        advance(UvirMainIslandMoveDurationMillis.toLong() + 80)
        assertEquals(targetY, position(SensorGroup.UV).y, 1f)
        assertEquals(initial.y, position(SensorGroup.VISIBLE).y, 1f)
        assertEquals(listOf(SensorGroup.VISIBLE, SensorGroup.UV, SensorGroup.NIR), loadUvirMainIslandOrder(preferences))
        identities.forEach { (group, token) -> assertSame("Retain $group", token, tokens[group]) }
        capture("portrait-end")
    }

    @Test fun landscapeCardsSlideAcrossColumnsWithoutRecreatingTheirContent() {
        fixture(true)
        val initial = position(SensorGroup.UV)
        val visible = position(SensorGroup.VISIBLE)
        val nir = position(SensorGroup.NIR)
        val identities = tokens.toMap()
        capture("landscape-start")
        move(false)
        compose.mainClock.advanceTimeByFrame()
        advance(100)
        assertBetween("UV crossing columns", position(SensorGroup.UV).x, initial.x, visible.x)
        assertBetween("Visible crossing columns", position(SensorGroup.VISIBLE).x, visible.x, initial.x)
        assertTrue(position(SensorGroup.NIR).y > nir.y)
        capture("landscape-middle")
        advance(UvirMainIslandMoveDurationMillis.toLong() + 80)
        assertEquals(visible.x, position(SensorGroup.UV).x, 1f)
        assertEquals(initial.x, position(SensorGroup.VISIBLE).x, 1f)
        identities.forEach { (group, token) -> assertSame("Retain $group across columns", token, tokens[group]) }
        capture("landscape-end")
    }

    @Test fun rapidReversalRetargetsTheMotionAndReopeningLoadsTheOrderWithoutAnEntranceAnimation() {
        fixture(true)
        val initial = UvirDefaultMainIslandOrder.associateWith(::position)
        val identities = tokens.toMap()
        for (up in listOf(false, false, true, true)) {
            move(up)
            compose.mainClock.advanceTimeByFrame()
            advance(65)
        }
        advance(UvirMainIslandMoveDurationMillis.toLong() + 80)
        initial.forEach { (group, target) ->
            assertEquals(target.x, position(group).x, 1f)
            assertEquals(target.y, position(group).y, 1f)
            assertSame(identities[group], tokens[group])
        }
        assertEquals(UvirDefaultMainIslandOrder, loadUvirMainIslandOrder(preferences))
        compose.runOnIdle { open.value = false }
        compose.mainClock.advanceTimeByFrame()
        compose.runOnIdle { open.value = true }
        compose.mainClock.advanceTimeByFrame()
        initial.forEach { (group, target) -> assertEquals(target, position(group)) }
    }
}
