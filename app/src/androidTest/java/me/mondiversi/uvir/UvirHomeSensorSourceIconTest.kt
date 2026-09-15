package me.mondiversi.uvir

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Isolated drawings: never opens the app database or sends sensor commands. */
class UvirHomeSensorSourceIconTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun wifiUsesThreeWideArcsWithoutCircularDotAtBothStrokeWidths() {
        val strokeScale = mutableStateOf(1f)
        val dark = mutableStateOf(false)
        compose.setContent {
            MaterialTheme(colorScheme = if (dark.value) darkColorScheme() else lightColorScheme()) {
                Box(
                    Modifier.size(42.dp).background(MaterialTheme.colorScheme.surface).testTag("wifi"),
                    contentAlignment = Alignment.Center
                ) {
                    ConnectivitySectionIcon(
                        type = ConnectivityIconType.WIFI,
                        modifier = Modifier.size(24.dp),
                        strokeScale = strokeScale.value,
                        tint = Color.Red
                    )
                }
            }
        }

        for (darkTheme in listOf(false, true)) {
            for (scale in listOf(1f, 1.6f)) {
                compose.runOnIdle { dark.value = darkTheme; strokeScale.value = scale }
                val pixels = compose.onNodeWithTag("wifi").captureToImage().toPixelMap()
                val bands = mutableListOf<Pair<Int, Int>>()
                var bandTop = -1
                for (y in 0..pixels.height) {
                    val hasInk = y < pixels.height && (0 until pixels.width).any { x ->
                        val color = pixels[x, y]
                        color.red > 0.6f && color.red > color.green * 2f && color.red > color.blue * 2f
                    }
                    if (hasInk && bandTop < 0) bandTop = y
                    if (!hasInk && bandTop >= 0) {
                        bands += bandTop to y - 1
                        bandTop = -1
                    }
                }
                assertEquals("Three distinct Wi-Fi arcs, dark=$darkTheme, stroke=$scale", 3, bands.size)
                for ((top, bottom) in bands) {
                    var left = pixels.width
                    var right = -1
                    for (y in top..bottom) {
                        for (x in 0 until pixels.width) {
                            val color = pixels[x, y]
                            if (color.red > 0.6f && color.red > color.green * 2f && color.red > color.blue * 2f) {
                                left = minOf(left, x)
                                right = maxOf(right, x)
                            }
                        }
                    }
                    assertTrue("Each signal must be wider than tall, not a circular dot", right - left > bottom - top)
                }
                assertEquals("Wi-Fi visible drawing stays vertically centered",
                    (pixels.height - 1) / 2f, (bands.first().first + bands.last().second) / 2f, 1f)
            }
        }
    }

    @Test
    fun allSourceDrawingsShareSettingsVerticalCenterInBothThemes() {
        val source = mutableStateOf(ConnectivityIconType.USB)
        val dark = mutableStateOf(false)
        compose.setContent {
            MaterialTheme(colorScheme = if (dark.value) darkColorScheme() else lightColorScheme()) {
                Row(Modifier.background(MaterialTheme.colorScheme.surface).testTag("icons")) {
                    Box(Modifier.size(42.dp), contentAlignment = Alignment.Center) {
                        UvirHomeSensorSourceIcon(type = source.value, tint = Color.Red)
                    }
                    Box(Modifier.size(42.dp), contentAlignment = Alignment.Center) {
                        UvirHomeSettingsIcon(tint = Color.Blue)
                    }
                }
            }
        }

        val types = listOf(
            ConnectivityIconType.USB,
            ConnectivityIconType.WIFI,
            ConnectivityIconType.BLUETOOTH,
            ConnectivityIconType.INTERNET,
            ConnectivityIconType.DEBUG
        )
        for (darkTheme in listOf(false, true)) {
            for (type in types) {
                compose.runOnIdle { source.value = type; dark.value = darkTheme }
                val pixels = compose.onNodeWithTag("icons").captureToImage().toPixelMap()
                var sourceTop = pixels.height
                var sourceBottom = -1
                var settingsTop = pixels.height
                var settingsBottom = -1
                for (y in 0 until pixels.height) {
                    for (x in 0 until pixels.width) {
                        val color = pixels[x, y]
                        if (color.red > 0.6f && color.red > color.green * 2 && color.red > color.blue * 2) {
                            sourceTop = minOf(sourceTop, y)
                            sourceBottom = maxOf(sourceBottom, y)
                        }
                        if (color.blue > 0.6f && color.blue > color.green * 2 && color.blue > color.red * 2) {
                            settingsTop = minOf(settingsTop, y)
                            settingsBottom = maxOf(settingsBottom, y)
                        }
                    }
                }
                assertTrue("Source $type must be visible", sourceBottom >= sourceTop)
                assertTrue("Settings must be visible", settingsBottom >= settingsTop)
                assertEquals(
                    "Visible centers for $type, dark=$darkTheme",
                    (settingsTop + settingsBottom) / 2f,
                    (sourceTop + sourceBottom) / 2f,
                    1f
                )
            }
        }
    }
}
