package me.mondiversi.uvir

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Isolated drawings: never opens the app database or sends sensor commands. */
class UvirHomeSensorSourceIconTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun usbKeepsBothRoundTerminalsHollowAtHomeSizeInBothThemes() {
        val dark = mutableStateOf(false)
        compose.setContent {
            MaterialTheme(colorScheme = if (dark.value) darkColorScheme() else lightColorScheme()) {
                Box(
                    Modifier.size(42.dp).background(MaterialTheme.colorScheme.surface).testTag("usb"),
                    contentAlignment = Alignment.Center
                ) {
                    UvirHomeSensorSourceIcon(ConnectivityIconType.USB, Color.Red)
                }
            }
        }

        val density = InstrumentationRegistry.getInstrumentation()
            .targetContext.resources.displayMetrics.density
        for (darkTheme in listOf(false, true)) {
            compose.runOnIdle { dark.value = darkTheme }
            val pixels = compose.onNodeWithTag("usb").captureToImage().toPixelMap()
            val iconSizePx = 24f * density
            val stroke = UvirHomeHeaderEffectiveStrokeWidth.value * density
            val iconLeft = (pixels.width - iconSizePx) / 2f - 0.03f * iconSizePx + 0.8f * stroke
            val iconTop = (pixels.height - iconSizePx) / 2f + 0.045f * iconSizePx - 0.8f * stroke
            fun isRed(x: Int, y: Int): Boolean {
                val color = pixels[x, y]
                return color.red > 0.6f && color.red > color.green * 2f &&
                    color.red > color.blue * 2f
            }
            listOf(0.25f to 0.33f, 0.50f to 0.83f).forEach { (xFraction, yFraction) ->
                val centerX = (iconLeft + xFraction * iconSizePx).toInt()
                val centerY = (iconTop + yFraction * iconSizePx).toInt()
                assertTrue("USB terminal center must remain open, dark=$darkTheme",
                    !isRed(centerX, centerY))
                val surroundingInk = (-12..12).sumOf { dy ->
                    (-12..12).count { dx ->
                        val x = centerX + dx
                        val y = centerY + dy
                        x in 0 until pixels.width && y in 0 until pixels.height && isRed(x, y)
                    }
                }
                assertTrue("USB terminal outline must remain visible, dark=$darkTheme",
                    surroundingInk > 12)
            }
        }
    }

    @Test
    fun wifiUsesThreeArcsAndOneDotAtBothStrokeWidths() {
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
                    // Arc endpoints overlap vertically with the next arc. Inspect their
                    // central peaks instead of projecting all ink onto the vertical axis.
                    val hasInk = y < pixels.height && (pixels.width / 2 - 1..pixels.width / 2 + 1).any { x ->
                        val color = pixels[x, y]
                        color.red > 0.6f && color.red > color.green * 2f && color.red > color.blue * 2f
                    }
                    if (hasInk && bandTop < 0) bandTop = y
                    if (!hasInk && bandTop >= 0) {
                        bands += bandTop to y - 1
                        bandTop = -1
                    }
                }
                assertEquals("Three Wi-Fi arc peaks and one terminal dot, dark=$darkTheme, stroke=$scale", 4, bands.size)
                val iconSizePx = 24f * InstrumentationRegistry.getInstrumentation()
                    .targetContext.resources.displayMetrics.density
                // First arc peak: .75 - .59, with half-stroke above it.
                // Bottom dot: .82, with .85-stroke below it. Their ink center
                // shifts slightly with thickness, even though the canvas does not.
                val expectedInkCenter = (pixels.height - 1) / 2f +
                    (0.49f + 0.175f * 0.08f * scale - 0.5f) * iconSizePx
                assertEquals("Wi-Fi ink matches the arc/dot geometry, dark=$darkTheme, stroke=$scale",
                    expectedInkCenter, (bands.first().first + bands.last().second) / 2f, 1f)
            }
        }
    }

    @Test
    fun allSourceDrawingsAreCenteredInsideTheirBadgesInBothThemesAndDirections() {
        val source = mutableStateOf(ConnectivityIconType.USB)
        val dark = mutableStateOf(false)
        val direction = mutableStateOf(LayoutDirection.Ltr)
        compose.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides direction.value) {
                MaterialTheme(colorScheme = if (dark.value) darkColorScheme() else lightColorScheme()) {
                    Row(Modifier.background(MaterialTheme.colorScheme.surface)) {
                        Box(Modifier.size(42.dp).testTag("source"), contentAlignment = Alignment.Center) {
                            UvirActionIconBadge(Color.Red) {
                                UvirHomeSensorSourceIcon(type = source.value, tint = Color.White)
                            }
                        }
                        Box(Modifier.size(42.dp).testTag("settings"), contentAlignment = Alignment.Center) {
                            UvirActionIconBadge(Color.Blue) {
                                UvirHomeSettingsIcon(tint = Color.White)
                            }
                        }
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
            for (layout in listOf(LayoutDirection.Ltr, LayoutDirection.Rtl)) for (type in types) {
                compose.runOnIdle { source.value = type; dark.value = darkTheme; direction.value = layout }
                for (tag in listOf("source", "settings")) {
                    val pixels = compose.onNodeWithTag(tag).captureToImage().toPixelMap()
                    var left = pixels.width
                    var right = -1
                    var top = pixels.height
                    var bottom = -1
                    for (y in 0 until pixels.height) for (x in 0 until pixels.width) {
                        if (pixels[x, y] == Color.White) {
                            left = minOf(left, x)
                            right = maxOf(right, x)
                            top = minOf(top, y)
                            bottom = maxOf(bottom, y)
                        }
                    }
                    val description = "$tag type=$type dark=$darkTheme layout=$layout"
                    assertTrue("Visible glyph: $description", right >= left && bottom >= top)
                    // Compare the actual painted bounds, not Compose's canvas bounds.
                    assertEquals("Horizontal centering: $description",
                        (pixels.width - 1) / 2f, (left + right) / 2f, 1.25f)
                    assertEquals("Vertical centering: $description",
                        (pixels.height - 1) / 2f, (top + bottom) / 2f, 1.25f)
                }
            }
        }
    }
}
