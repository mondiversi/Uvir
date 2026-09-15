package me.mondiversi.uvir

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class UvirHomeSettingsStrokeTest {
    @get:Rule val compose = createComposeRule()

    @Test fun settingsUsesTheLighterBaseStrokeInBothThemes() {
        val dark = mutableStateOf(false)
        var density = 1f
        compose.setContent {
            MaterialTheme(colorScheme = if (dark.value) darkColorScheme() else lightColorScheme()) {
                density = LocalDensity.current.density
                Row(Modifier.testTag("icons")) {
                    Box(Modifier.size(42.dp), contentAlignment = Alignment.Center) {
                        UvirHomeSensorSourceIcon(ConnectivityIconType.WIFI, Color.Red)
                    }
                    Box(Modifier.size(42.dp), contentAlignment = Alignment.Center) {
                        UvirHomeSettingsIcon(Color.Blue)
                    }
                }
            }
        }
        for (night in listOf(false, true)) {
            compose.runOnIdle { dark.value = night }
            val pixels = compose.onNodeWithTag("icons").captureToImage().toPixelMap()
            fun firstBand(x: Int, blue: Boolean): Int {
                var height = 0
                for (y in 0 until pixels.height) {
                    val c = pixels[x, y]
                    val ink = if (blue) c.blue > 0.6f && c.blue > c.red * 2f
                        else c.red > 0.6f && c.red > c.blue * 2f
                    if (ink) height++ else if (height > 0) break
                }
                return height
            }
            val connection = firstBand((21 * density).toInt(), false)
            val settings = firstBand(((42 + 9 + 24 * 0.20f) * density).toInt(), true)
            assertTrue(connection > 0 && settings > 0)
            assertTrue("Settings should not look heavier: connection=$connection, settings=$settings",
                settings <= connection + 1)
            assertEquals(2.21f, UvirHomeSettingsStrokeWidth.value, 0.001f)
            assertTrue(UvirHomeSettingsStrokeWidth < UvirHomeHeaderEffectiveStrokeWidth)
            assertEquals(2.4576f, UvirHomeHeaderEffectiveStrokeWidth.value, 0.001f)
        }
    }
}
