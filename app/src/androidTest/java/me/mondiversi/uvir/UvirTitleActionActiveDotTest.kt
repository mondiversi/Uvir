package me.mondiversi.uvir

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import kotlin.math.abs
import kotlin.math.hypot

/** Isolated controls; no filters are applied to user records or sensor commands. */
class UvirTitleActionActiveDotTest {
    @get:Rule val compose = createComposeRule()

    @Test fun filtersAndVariantsUseTheSameUnclippedDotOnlyWhileActive() {
        val night = mutableStateOf(false)
        val direction = mutableStateOf(LayoutDirection.Ltr)
        val active = mutableStateOf(false)
        val enabled = mutableStateOf(true)
        var background = Color.Unspecified
        var dotColor = Color.Unspecified
        var taps = 0
        compose.setContent {
            val config = Configuration(LocalConfiguration.current).apply {
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                    if (night.value) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            }
            CompositionLocalProvider(LocalConfiguration provides config, LocalLayoutDirection provides direction.value) {
                MaterialTheme(colorScheme = if (night.value) darkColorScheme() else lightColorScheme()) {
                    UvirActionTheme {
                        background = MaterialTheme.colorScheme.background
                        dotColor = if (enabled.value) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        Row(Modifier.background(background)) {
                            Box(Modifier.size(40.dp).testTag("filter-fixture")) {
                                UvirRecordListFilterButton(active.value, enabled.value, compact = true) { taps++ }
                            }
                            Box(Modifier.size(40.dp).testTag("variants-fixture")) {
                                UvirSessionCycleFilterButton(active.value, enabled.value, compact = true) { taps++ }
                            }
                        }
                    }
                }
            }
        }
        for (dark in listOf(false, true)) for (layout in listOf(LayoutDirection.Ltr, LayoutDirection.Rtl))
            for (selected in listOf(false, true)) for (clickable in listOf(false, true)) {
                compose.runOnIdle { night.value = dark; direction.value = layout; active.value = selected; enabled.value = clickable }
                compose.waitForIdle()
                for ((fixture, action, marker) in listOf(
                    Triple("filter-fixture", "list-filter-toggle", "list-filter-active-dot"),
                    Triple("variants-fixture", "session-cycle-filter-toggle", "session-cycle-active-dot")
                )) {
                    val button = compose.onNodeWithTag(action).assertWidthIsEqualTo(40.dp).assertHeightIsEqualTo(40.dp)
                    if (selected) button.assertIsSelected() else button.assertIsNotSelected()
                    if (clickable) button.assertIsEnabled() else button.assertIsNotEnabled()
                    val root = compose.onNodeWithTag(fixture).fetchSemanticsNode().boundsInRoot
                    val pixels = compose.onNodeWithTag(fixture).captureToImage().toPixelMap()
                    fun px(value: Float) = with(compose.density) { value.dp.toPx().toInt() }
                    val markerX = if (layout == LayoutDirection.Ltr) 34f else 6f
                    val expected = if (selected) dotColor.compositeOver(background) else background
                    assertColor(expected, pixels[px(markerX), px(34f)])
                    // This point would be clipped by the old 40dp circular touch layer.
                    assertColor(expected, pixels[px(if (layout == LayoutDirection.Ltr) 35f else 5f), px(35f)])
                    // No underline should remain below the icon.
                    assertColor(background, pixels[px(20f), px(37f)])
                    val dot = compose.onNodeWithTag(marker, useUnmergedTree = true)
                    if (selected) {
                        dot.assertHasNoClickAction().assertWidthIsEqualTo(5.dp).assertHeightIsEqualTo(5.dp)
                        val bounds = dot.fetchSemanticsNode().boundsInRoot
                        assertEquals(root.center.x + px(if (layout == LayoutDirection.Ltr) 14f else -14f), bounds.center.x, 1f)
                        assertEquals(root.center.y + px(14f), bounds.center.y, 1f)
                        val distance = hypot(bounds.center.x - root.center.x, bounds.center.y - root.center.y)
                        assertTrue("Dot has a visible gap from the 32dp badge", distance - bounds.width / 2f > px(16f))
                        assertTrue(root.contains(bounds.topLeft) && root.contains(bounds.bottomRight))
                    } else dot.assertDoesNotExist()
                    val previous = taps
                    button.performSemanticsAction(SemanticsActions.OnClick) { it() }
                    compose.runOnIdle { assertEquals(previous + if (clickable) 1 else 0, taps) }
                }
            }
    }

    private fun assertColor(expected: Color, actual: Color) {
        assertTrue("Expected $expected, got $actual", abs(expected.red - actual.red) < 0.01f &&
            abs(expected.green - actual.green) < 0.01f && abs(expected.blue - actual.blue) < 0.01f)
    }
}
