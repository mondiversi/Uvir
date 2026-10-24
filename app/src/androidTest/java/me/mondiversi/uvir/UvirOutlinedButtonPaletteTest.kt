package me.mondiversi.uvir

import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import kotlin.math.abs

/** Visual fixtures only: no reset, playback or physical sensor commands. */
class UvirOutlinedButtonPaletteTest {
    @get:Rule val compose = createComposeRule()

    @Test fun allOutlinedFamiliesUseTransparentSurfacesAndTheSameDisabledStyleOutsideSettingsToo() {
        val night = mutableStateOf(false)
        val enabled = mutableStateOf(true)
        var palettes = emptyList<ButtonColors>()
        var borders = emptyList<BorderStroke?>()
        var red = Color.Unspecified
        var purple = Color.Unspecified
        var card = Color.Unspecified
        var neutralOnSurface = Color.Unspecified
        compose.setContent {
            val configuration = Configuration(LocalConfiguration.current).apply {
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                    if (night.value) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            }
            CompositionLocalProvider(LocalConfiguration provides configuration) {
                MaterialTheme(colorScheme = if (night.value) darkColorScheme() else lightColorScheme()) {
                    UvirActionTheme {
                        val primary = if (night.value) Color.White else Color(0xFF101418)
                        val secondary = if (night.value) Color(0xFF90A4AE) else Color(0xFF546E7A)
                        card = if (night.value) Color(0xFF1C242B) else Color.White
                        red = uvirDestructiveOutlinedActionColor()
                        purple = MaterialTheme.colorScheme.primary
                        neutralOnSurface = MaterialTheme.colorScheme.onSurface
                        palettes = listOf(uvirOutlinedActionColors(primary),
                            uvirDestructiveOutlinedButtonColors(), uvirPrimaryOutlinedButtonColors())
                        borders = listOf(uvirOutlinedActionBorder(enabled.value, primary),
                            uvirDestructiveOutlinedButtonBorder(enabled.value, secondary),
                            uvirPrimaryOutlinedButtonBorder(enabled.value, secondary))
                        Column(Modifier.width(250.dp).background(card)) {
                            palettes.forEachIndexed { index, palette ->
                                OutlinedButton(onClick = {}, enabled = enabled.value,
                                    colors = palette, border = borders[index],
                                    modifier = Modifier.fillMaxWidth().testTag("button-$index")) {
                                    Box(Modifier.size(12.dp).background(LocalContentColor.current)
                                        .testTag("glyph-$index"))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Action $index")
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
            val normal = palettes[0]
            assertEquals(red, palettes[1].contentColor)
            assertEquals(purple, palettes[2].contentColor)
            assertNotEquals(normal.contentColor, palettes[1].contentColor)
            assertNotEquals(normal.contentColor, palettes[2].contentColor)
            assertEquals(Color.Transparent, normal.containerColor)
            assertEquals(if (dark) Color(0xFF33343C) else Color(0xFFECEDEF), normal.disabledContainerColor)
            assertEquals(if (dark) Color(0xFFA1A5AE) else Color(0xFF808790), normal.disabledContentColor)
            val neutralBorder = if (active) normal.contentColor.copy(alpha = if (dark) .48f else .38f)
                else neutralOnSurface.copy(alpha = if (dark) .20f else .12f)
            assertEquals(BorderStroke(1.dp, neutralBorder), borders[0])
            for (index in 1..2) {
                val accent = if (index == 1) red else purple
                assertEquals(Color.Transparent, palettes[index].containerColor)
                if (active) {
                    assertEquals(BorderStroke(1.dp, accent.copy(alpha = if (dark) 0.48f else 0.38f)), borders[index])
                    assertNotEquals(borders[0], borders[index])
                } else assertEquals(borders[0], borders[index])
            }
            for (index in palettes.indices) {
                val palette = palettes[index]
                assertEquals(normal.disabledContainerColor, palette.disabledContainerColor)
                assertEquals(normal.disabledContentColor, palette.disabledContentColor)
                val button = compose.onNodeWithTag("button-$index")
                if (active) button.assertIsEnabled() else button.assertIsNotEnabled()
                val surface = (if (active) palette.containerColor else palette.disabledContainerColor)
                    .compositeOver(card)
                val pixels = button.captureToImage().toPixelMap()
                val inset = with(compose.density) { 8.dp.toPx().toInt() }
                assertColor(surface, pixels[pixels.width / 2, pixels.height - inset])
                val glyph = compose.onNodeWithTag("glyph-$index", useUnmergedTree = true)
                    .captureToImage().toPixelMap()
                val foreground = (if (active) palette.contentColor else palette.disabledContentColor)
                    .compositeOver(surface)
                assertColor(foreground, glyph[glyph.width / 2, glyph.height / 2])
            }
        }
    }

    private fun assertColor(expected: Color, actual: Color) {
        assertTrue("Expected $expected, got $actual",
            abs(expected.red - actual.red) < 0.02f &&
                abs(expected.green - actual.green) < 0.02f &&
                abs(expected.blue - actual.blue) < 0.02f)
    }
}
