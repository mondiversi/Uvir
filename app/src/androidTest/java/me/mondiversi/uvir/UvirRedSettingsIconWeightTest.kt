package me.mondiversi.uvir

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Isolated drawings only: no destructive callbacks or sensor commands. */
class UvirRedSettingsIconWeightTest {
    @get:Rule val compose = createComposeRule()

    @Test fun redActionGlyphsGainWeightWithoutChangingTheirSizeOrDefaultDrawings() {
        val type = mutableIntStateOf(0)
        val emphasized = mutableStateOf(false)
        val rtl = mutableStateOf(false)
        compose.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides if (rtl.value) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                Box(Modifier.size(32.dp).background(Color.White).testTag("drawing"), Alignment.Center) {
                    val scale = if (emphasized.value) UvirDestructiveOutlinedIconStrokeScale else 1f
                    val iconModifier = Modifier.size(20.dp).testTag("icon")
                    when (type.intValue) {
                        0 -> ConnectivitySectionIcon(ConnectivityIconType.TALLY, iconModifier, Color.Black, scale)
                        1 -> ConnectivitySectionIcon(ConnectivityIconType.SENSOR, iconModifier, Color.Black, scale)
                        2 -> ConnectivitySectionIcon(ConnectivityIconType.PHONE, iconModifier, Color.Black, scale)
                        3 -> ConnectivitySectionIcon(ConnectivityIconType.SENSOR_CONNECTION, iconModifier, Color.Black, scale)
                        4 -> UvirMenuIcon(MenuIconType.POWER, iconModifier, Color.Black, scale)
                        else -> UvirButtonGlyphIcon(UvirButtonGlyph.STOP, iconModifier, Color.Black, scale)
                    }
                }
            }
        }
        assertEquals(1.5.dp, UvirDestructiveOutlinedBorderWidth)
        assertEquals(1.25f, UvirDestructiveOutlinedIconStrokeScale, 0f)
        for (rightToLeft in listOf(false, true)) for (glyph in 0..5) {
            compose.runOnIdle { rtl.value = rightToLeft; type.intValue = glyph; emphasized.value = false }
            val regular = ink()
            compose.runOnIdle { emphasized.value = true }
            compose.onNodeWithTag("icon", useUnmergedTree = true)
                .assertWidthIsEqualTo(20.dp).assertHeightIsEqualTo(20.dp)
            assertTrue("Heavier red glyph: $glyph, rtl=$rightToLeft", ink() > regular * 1.05)
            compose.runOnIdle { emphasized.value = false }
            assertEquals("Default glyph remains unchanged", regular, ink(), 0.01)
        }
    }

    private fun ink(): Double {
        val pixels = compose.onNodeWithTag("drawing").captureToImage().toPixelMap()
        var total = 0.0
        for (y in 0 until pixels.height) for (x in 0 until pixels.width) total += 1.0 - pixels[x, y].red
        return total
    }
}
