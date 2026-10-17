package me.mondiversi.uvir

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import kotlin.math.abs
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Isolated title controls: no user filters, records, settings or hardware commands. */
class UvirTitleActionStateColorsTest {
    @get:Rule val compose = createComposeRule()
    private val night = mutableStateOf(false)
    private val direction = mutableStateOf(LayoutDirection.Ltr)
    private val active = mutableStateOf(false)
    private val enabled = mutableStateOf(true)
    private var background = Color.Unspecified
    private var accent = Color.Unspecified
    private var destructive = Color.Unspecified
    private var taps = 0

    @Composable private fun Fixture(content: @Composable () -> Unit) {
        val view = LocalView.current
        DisposableEffect(view) {
            val previous = view.keepScreenOn
            view.keepScreenOn = true
            onDispose { view.keepScreenOn = previous }
        }
        val config = Configuration(LocalConfiguration.current).apply {
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                if (night.value) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
        }
        CompositionLocalProvider(LocalConfiguration provides config, LocalLayoutDirection provides direction.value) {
            MaterialTheme(colorScheme = if (night.value) darkColorScheme() else lightColorScheme()) {
                UvirActionTheme {
                    background = MaterialTheme.colorScheme.background
                    accent = MaterialTheme.colorScheme.primary
                    destructive = UvirDestructiveActionColor
                    Row(Modifier.background(background)) { content() }
                }
            }
        }
    }

    private fun neutral(dark: Boolean) = if (dark) Color.White else Color(0xFF101418)
    private fun matches(actual: Color, expected: Color) =
        abs(actual.red - expected.red) < .035f && abs(actual.green - expected.green) < .035f &&
            abs(actual.blue - expected.blue) < .035f

    private fun colorCount(pixels: PixelMap, expected: Color): Int {
        var count = 0
        for (y in 0 until pixels.height) for (x in 0 until pixels.width) {
            if (matches(pixels[x, y], expected)) count++
        }
        return count
    }

    @Test fun activeFiltersAndVariantsUseTheThemedPurpleGlyphWithoutARestingBackground() {
        compose.setContent {
            Fixture {
                Box(Modifier.size(40.dp).testTag("filter-fixture")) {
                    UvirRecordListFilterButton(active.value, enabled.value, compact = true) { taps++ }
                }
                Box(Modifier.size(40.dp).testTag("variants-fixture")) {
                    UvirSessionCycleFilterButton(active.value, enabled.value, compact = true) { taps++ }
                }
            }
        }
        for (dark in listOf(false, true)) for (layout in listOf(LayoutDirection.Ltr, LayoutDirection.Rtl))
            for (selected in listOf(false, true)) for (clickable in listOf(false, true)) {
                compose.runOnIdle {
                    night.value = dark; direction.value = layout
                    active.value = selected; enabled.value = clickable
                }
                for ((fixture, action, oldMarker) in listOf(
                    Triple("filter-fixture", "list-filter-toggle", "list-filter-active-dot"),
                    Triple("variants-fixture", "session-cycle-filter-toggle", "session-cycle-active-dot")
                )) {
                    val button = compose.onNodeWithTag(action).assertWidthIsEqualTo(40.dp).assertHeightIsEqualTo(40.dp)
                    if (selected) button.assertIsSelected() else button.assertIsNotSelected()
                    if (clickable) button.assertIsEnabled() else button.assertIsNotEnabled()
                    compose.onNodeWithTag(oldMarker, useUnmergedTree = true).assertDoesNotExist()
                    val pixels = compose.onNodeWithTag(fixture).captureToImage().toPixelMap()
                    val expected = when {
                        !clickable -> neutral(dark).copy(alpha = .38f).compositeOver(background)
                        selected -> accent
                        else -> neutral(dark)
                    }
                    assertTrue("Title action palette: $action, night=$dark, active=$selected, enabled=$clickable",
                        colorCount(pixels, expected) > 4)
                    if (!selected || !clickable) assertEquals(0, colorCount(pixels, accent))
                    // Only the pressed ripple is circular; resting buttons stay transparent.
                    fun px(value: Float) = with(compose.density) { value.dp.toPx().toInt() }
                    assertTrue(matches(pixels[px(if (layout == LayoutDirection.Ltr) 34f else 6f), px(34f)], background))
                    assertTrue("No resting circle: $action, night=$dark, enabled=$clickable",
                        matches(pixels[px(20f), px(5f)], background))
                    assertTrue(matches(pixels[px(20f), px(37f)], background))
                    val previous = taps
                    button.performSemanticsAction(SemanticsActions.OnClick) { it() }
                    compose.runOnIdle { assertEquals(previous + if (clickable) 1 else 0, taps) }
                }
            }
    }

    @Test fun navigationAndExportStayNeutralWhileDeleteRemainsRedInBothThemes() {
        compose.setContent {
            Fixture {
                UvirBackButton { taps++ }
                UvirTitleActionButton("Export", { taps++ }, Modifier.testTag("export-action"), enabled.value) {
                    UvirTitleActionIcon(MenuIconType.EXPORT)
                }
                UvirTitleActionButton("Collapse", { taps++ }, Modifier.testTag("collapse-action"), enabled.value) {
                    UvirTitleCollapseAllIcon()
                }
                UvirTitleActionButton("Delete", { taps++ }, Modifier.testTag("delete-action"), enabled.value,
                    iconColor = UvirDestructiveActionColor) {
                    UvirTitleActionIcon(MenuIconType.DELETE)
                }
            }
        }
        val backLabel = InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.navigate_back)
        for (dark in listOf(false, true)) for (layout in listOf(LayoutDirection.Ltr, LayoutDirection.Rtl))
            for (clickable in listOf(false, true)) {
                compose.runOnIdle { night.value = dark; direction.value = layout; enabled.value = clickable }
                for (tag in listOf("export-action", "collapse-action", "delete-action")) {
                    val button = compose.onNodeWithTag(tag).assertWidthIsEqualTo(40.dp)
                    if (clickable) button.assertIsEnabled() else button.assertIsNotEnabled()
                    val expected = if (!clickable) neutral(dark).copy(alpha = .38f).compositeOver(background)
                        else if (tag == "delete-action") destructive else neutral(dark)
                    val pixels = button.captureToImage().toPixelMap()
                    assertTrue("Neutral actions and red deletion: $tag, night=$dark, enabled=$clickable",
                        colorCount(pixels, expected) > 4)
                    assertEquals(0, colorCount(pixels, accent))
                }
                val back = compose.onNodeWithContentDescription(backLabel).assertIsEnabled()
                val pixels = back.captureToImage().toPixelMap()
                assertTrue("Back arrow is neutral: night=$dark", colorCount(pixels, neutral(dark)) > 4)
                assertEquals(0, colorCount(pixels, accent))
            }
    }

    @Test fun expandedPressedCircleStaysInsideTheExistingTargetAndLeavesRoundCorners() {
        compose.setContent {
            Fixture {
                UvirTitleActionButton("Export", { taps++ }, Modifier.testTag("export-action")) {
                    UvirTitleActionIcon(MenuIconType.EXPORT)
                }
                Box(Modifier.size(40.dp).testTag("filter-fixture")) {
                    UvirRecordListFilterButton(active.value, true, compact = true) { taps++ }
                }
                Box(Modifier.size(40.dp).testTag("variants-fixture")) {
                    UvirSessionCycleFilterButton(active.value, true, compact = true) { taps++ }
                }
            }
        }
        for (dark in listOf(false, true)) for (selected in listOf(false, true)) {
            compose.runOnIdle { night.value = dark; active.value = selected }
            for ((fixture, action) in listOf("export-action" to "export-action",
                "filter-fixture" to "list-filter-toggle", "variants-fixture" to "session-cycle-filter-toggle")) {
                val button = compose.onNodeWithTag(action).assertWidthIsEqualTo(40.dp).assertHeightIsEqualTo(40.dp)
                val before = compose.onNodeWithTag(fixture).captureToImage().toPixelMap()
                button.performTouchInput { down(center) }
                try {
                    android.os.SystemClock.sleep(400)
                    compose.mainClock.advanceTimeBy(600)
                    val pressed = compose.onNodeWithTag(fixture).captureToImage().toPixelMap()
                    fun px(value: Float) = with(compose.density) { value.dp.toPx().toInt() }
                    // Beyond the old 32dp circle's edge but inside the new circle.
                    assertFalse("Larger press halo: $action, night=$dark",
                        matches(pressed[px(20f), px(3f)], before[px(20f), px(3f)]))
                    assertTrue("Round corners remain clear: $action",
                        matches(pressed[px(2f), px(2f)], background))
                } finally {
                    button.performTouchInput { cancel() }
                }
                compose.runOnIdle { assertEquals(0, taps) }
            }
        }
    }
}
