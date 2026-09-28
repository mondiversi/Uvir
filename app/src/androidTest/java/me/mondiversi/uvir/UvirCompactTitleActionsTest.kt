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
import androidx.compose.ui.platform.LocalView
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

/** Callback-only controls: no user data, sensor commands or settings are changed. */
class UvirCompactTitleActionsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun titleActionsUseBareThemedGlyphsWithUnchangedTargetsAndDisabledStates() {
        val night = mutableStateOf(false)
        val direction = mutableStateOf(LayoutDirection.Ltr)
        val enabled = mutableStateOf(true)
        var accent = Color.Unspecified
        var destructive = Color.Unspecified
        var background = Color.Unspecified
        var disabled = Color.Unspecified
        var taps = 0
        compose.setContent {
            val view = LocalView.current
            DisposableEffect(view) {
                val previous = view.keepScreenOn
                view.keepScreenOn = true
                onDispose { view.keepScreenOn = previous }
            }
            val configuration = Configuration(LocalConfiguration.current).apply {
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                    if (night.value) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            }
            CompositionLocalProvider(
                LocalConfiguration provides configuration,
                LocalLayoutDirection provides direction.value
            ) {
                MaterialTheme(colorScheme = if (night.value) darkColorScheme() else lightColorScheme()) {
                    UvirActionTheme {
                        accent = MaterialTheme.colorScheme.primary
                        destructive = UvirDestructiveActionColor
                        background = MaterialTheme.colorScheme.background
                        disabled = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        Column(Modifier.background(background)) {
                            Row {
                                for (type in listOf(MenuIconType.EXPORT, MenuIconType.DELETE, MenuIconType.SHARE)) {
                                    UvirTitleActionButton(
                                        type.name, { taps++ }, Modifier.testTag(type.name),
                                        enabled = enabled.value,
                                        iconColor = if (type == MenuIconType.DELETE) destructive else accent
                                    ) { UvirTitleActionIcon(type) }
                                }
                                UvirTitleActionButton("Save", { taps++ }, Modifier.testTag("save"),
                                    enabled = enabled.value) { UvirTitleSaveIcon() }
                            }
                            Row {
                                UvirRecordListFilterButton(active = true, enabled = enabled.value,
                                    compact = true) { taps++ }
                                UvirSessionCycleFilterButton(active = true, enabled = enabled.value,
                                    compact = true) { taps++ }
                                UvirHomeHeaderActionButton("Settings", { taps++ }, Modifier.testTag("settings"),
                                    enabled = enabled.value) {
                                    UvirHomeSettingsIcon(if (enabled.value) accent else disabled)
                                }
                                UvirBackButton { taps++ }
                            }
                        }
                    }
                }
            }
        }
        assertEquals(.96f, UvirTitleActionVisualScale)
        assertEquals(23.04f, UvirTitleActionIconSize.value * UvirTitleActionVisualScale, .001f)
        assertEquals(15.36f, UvirTitleBackIconSize.value, .001f)
        for (dark in listOf(false, true)) {
            for (layout in listOf(LayoutDirection.Ltr, LayoutDirection.Rtl)) {
                for (active in listOf(false, true)) {
                    compose.runOnIdle { night.value = dark; direction.value = layout; enabled.value = active }
                    compose.mainClock.advanceTimeBy(600L)
                    for (tag in listOf("EXPORT", "DELETE", "SHARE", "save", "settings",
                        "list-filter-toggle", "session-cycle-filter-toggle")) {
                        val button = compose.onNodeWithTag(tag)
                            .assertWidthIsEqualTo(40.dp).assertHeightIsEqualTo(40.dp)
                        if (active) button.assertIsEnabled() else button.assertIsNotEnabled()
                        val pixels = button.captureToImage().toPixelMap()
                        val inset = with(compose.density) { 6.dp.toPx().toInt() }
                        assertColor(background, pixels[inset, pixels.height / 2])
                        assertColor(background, pixels[pixels.width / 2, inset])
                        assertColor(background, pixels[0, 0])
                        val expected = (when {
                            !active -> disabled
                            tag == "DELETE" -> destructive
                            else -> accent
                        })
                            .compositeOver(background)
                        assertTrue("Bare themed glyph: $tag, night=$dark, enabled=$active",
                            (0 until pixels.height).any { y ->
                                (0 until pixels.width).any { x -> matches(expected, pixels[x, y]) }
                            })
                        val previous = taps
                        button.performSemanticsAction(SemanticsActions.OnClick) { it() }
                        compose.runOnIdle { assertEquals(previous + if (active) 1 else 0, taps) }
                    }
                }
            }
        }
    }

    private fun matches(expected: Color, actual: Color) =
        abs(expected.red - actual.red) < .02f && abs(expected.green - actual.green) < .02f &&
            abs(expected.blue - actual.blue) < .02f

    private fun assertColor(expected: Color, actual: Color) =
        assertTrue("Expected $expected, got $actual", matches(expected, actual))
}
