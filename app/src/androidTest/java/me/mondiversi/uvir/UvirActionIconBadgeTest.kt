package me.mondiversi.uvir

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class UvirActionIconBadgeTest {
    @get:Rule val compose = createComposeRule()

    @Test fun badgeColorsHaveLegibleGlyphsNeutralDisabledStatesAndUnchangedClickTargets() {
        val night = mutableStateOf(false)
        val enabled = mutableStateOf(true)
        var colors = emptyList<Color>()
        var disabled = Color.Unspecified
        var taps = 0
        compose.setContent {
            val configuration = Configuration(LocalConfiguration.current).apply {
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                    if (night.value) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            }
            CompositionLocalProvider(LocalConfiguration provides configuration) {
                MaterialTheme(colorScheme = if (night.value) darkColorScheme() else lightColorScheme()) {
                    UvirActionTheme {
                        colors = listOf(MaterialTheme.colorScheme.primary, UvirDestructiveActionColor) +
                            UvirStatusDot.entries.map { Color(it.colorArgb) }
                        disabled = uvirDisabledActionContainerColor()
                        Row(Modifier.background(MaterialTheme.colorScheme.background)) {
                            colors.forEachIndexed { index, color ->
                                UvirAccessibleIconButton("Badge $index", { taps++ },
                                    modifier = Modifier.size(40.dp), enabled = enabled.value, badgeColor = color) {
                                    Box(Modifier.size(12.dp).background(LocalContentColor.current))
                                }
                            }
                        }
                    }
                }
            }
        }
        for (dark in listOf(false, true)) for (active in listOf(false, true)) {
            compose.runOnIdle { night.value = dark; enabled.value = active }
            compose.waitForIdle()
            for ((index, color) in colors.withIndex()) {
                val node = compose.onNodeWithContentDescription("Badge $index")
                if (active) node.assertIsEnabled() else node.assertIsNotEnabled()
                val pixels = node.captureToImage().toPixelMap()
                val x = with(compose.density) { 5.dp.toPx().toInt() }
                assertEquals(if (active) color else disabled, pixels[x, pixels.height / 2])
                if (active) {
                    val foreground = uvirIconBadgeContentColor(color)
                    assertEquals(foreground, pixels[pixels.width / 2, pixels.height / 2])
                    val brighter = maxOf(color.luminance(), foreground.luminance())
                    val darker = minOf(color.luminance(), foreground.luminance())
                    // These are non-text icons: preserve at least 3:1 contrast.
                    assertTrue((brighter + 0.05f) / (darker + 0.05f) >= 3f)
                }
            }
            val previous = taps
            compose.onNodeWithContentDescription("Badge 0").performSemanticsAction(
                androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }
            compose.runOnIdle { assertEquals(previous + if (active) 1 else 0, taps) }
        }
    }
}
