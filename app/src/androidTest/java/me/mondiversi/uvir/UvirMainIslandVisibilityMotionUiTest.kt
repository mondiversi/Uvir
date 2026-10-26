package me.mondiversi.uvir

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Sample the real card's geometry during expansion, without a sensor or persistent settings. */
class UvirMainIslandVisibilityMotionUiTest {
    @get:Rule val compose = createComposeRule()

    private fun verify(landscape: Boolean) {
        compose.setContent {
            KeepUvirVerificationScreenOn()
            var uvVisible by remember { mutableStateOf(true) }
            val order = UvirDefaultMainIslandOrder
            val reordering = rememberUvirMainIslandReordering(order)
            MaterialTheme {
                val draw: @Composable (SensorGroup) -> Unit = { group ->
                    val included = group != SensorGroup.UV || uvVisible
                    SpectrumCard(group, group.name, 1.0, "% totale", .5f, Color.LightGray, Color.Blue,
                        expanded = true, onToggle = {}, showChart = false, liveHistory = emptyList(),
                        liveChartSeries = emptyList(), cardColor = Color.White, primaryText = Color.Black,
                        secondaryText = Color.Gray, collapsible = false, groupVisible = included,
                        onGroupVisibilityToggle = { if (group == SensorGroup.UV) uvVisible = !uvVisible },
                        reorderControls = { title -> UvirMainIslandMoveControls(group, title, Color.Gray, null, null) }
                    ) { Box(Modifier.fillMaxWidth().height(80.dp)) }
                }
                LazyColumn(Modifier.requiredWidth(if (landscape) 640.dp else 320.dp).height(580.dp),
                    verticalArrangement = Arrangement.spacedBy(UvirIslandSpacing)) {
                    if (landscape) item { UvirLiveLandscapeIslands({ _, _ -> }, order, draw) }
                    else items(order, key = { it.name }) { group ->
                        UvirAnimatedMainIsland(reordering) { draw(group) }
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        val card = compose.onNodeWithTag("live_spectrum_card_UV", true)
        val next = compose.onNodeWithTag("live_spectrum_card_${if (landscape) "NIR" else "VISIBLE"}", true)
        val toggle = compose.onNodeWithTag("live_island_header_UV", true)
        fun height() = card.fetchSemanticsNode().boundsInRoot.height
        fun gap() = next.fetchSemanticsNode().boundsInRoot.top - card.fetchSemanticsNode().boundsInRoot.bottom
        fun advance(ms: Long) { compose.mainClock.advanceTimeBy(ms); compose.waitForIdle() }
        val expanded = height()
        val originalGap = gap()
        toggle.performClick()
        advance(16)
        assertEquals("Removing the bar must not jump before the reveal starts", expanded, height(), 2f)
        advance(100)
        val middle = height()
        assertTrue("The entire card must shrink gradually", middle < expanded - 2f)
        assertEquals("The following card must track the same edge without a second animation", originalGap, gap(), 2f)
        advance(UvirExpansionDurationMillis.toLong() + 32)
        val compact = height()
        assertTrue(compact + 2f < middle)
        compose.onNodeWithTag("live_group_bar_UV", true).assertDoesNotExist()
        assertEquals(originalGap, gap(), 2f)

        toggle.performClick()
        advance(16)
        assertEquals("The bar must not add its full height before the body opens", compact, height(), 2f)
        advance(100)
        assertTrue(height() > compact + 2f && height() < expanded - 2f)
        assertEquals(originalGap, gap(), 2f)
        // Reverse the animation while the body is opening.
        val partial = height()
        // Invoke immediately: a simulated finger press would let the opening continue during the press.
        toggle.performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.waitForIdle()
        assertEquals("Reversal must start at the current size", partial, height(), 2f)
        advance(16)
        assertTrue("An interrupted animation can retain velocity, but must stay between its end states",
            height() > compact && height() < expanded)
        advance(UvirExpansionDurationMillis * 2L + 32)
        assertEquals(compact, height(), 2f)
        assertEquals(originalGap, gap(), 2f)
        toggle.performClick()
        advance(UvirExpansionDurationMillis.toLong() + 64)
        assertEquals(expanded, height(), 2f)
        compose.onNodeWithTag("live_group_bar_UV", true).assertExists()
    }

    @Test fun portraitBarBodyAndFollowingCardsShareOneTransition() = verify(false)
    @Test fun landscapeBarBodyAndFollowingCardsShareOneTransition() = verify(true)
}
