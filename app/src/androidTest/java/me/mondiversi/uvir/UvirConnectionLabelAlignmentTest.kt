package me.mondiversi.uvir

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Real painted bounds, including descenders and two-line sensor names; no app data. */
class UvirConnectionLabelAlignmentTest {
    @get:Rule val compose = createComposeRule()

    @Test fun onlyUsbAndWifiHaveAnAdditionalOpticalLift() {
        ConnectivityIconType.entries.forEach { type ->
            val expected = if (type == ConnectivityIconType.USB || type == ConnectivityIconType.WIFI) 1.dp else 0.dp
            assertEquals("Optical lift for $type", expected, uvirConnectionLabelOpticalLift(type))
        }
    }

    @Test fun connectionInkKeepsItsOpticalLiftOnStatusAndSensorNameInEitherThemeAndDirection() {
        val dark = mutableStateOf(false)
        val direction = mutableStateOf(LayoutDirection.Ltr)
        val scale = mutableStateOf(1f)
        val mode = mutableStateOf(SensorConnectionMode.WIFI)
        val label = mutableStateOf("Ricerca sensore...")
        val status = mutableStateOf(true)
        val width = mutableStateOf(180.dp)
        compose.setContent {
            val view = LocalView.current
            DisposableEffect(view) {
                val previous = view.keepScreenOn
                view.keepScreenOn = true
                onDispose { view.keepScreenOn = previous }
            }
            CompositionLocalProvider(
                LocalLayoutDirection provides direction.value,
                LocalDensity provides Density(LocalDensity.current.density, scale.value)
            ) {
                MaterialTheme(colorScheme = if (dark.value) darkColorScheme() else lightColorScheme()) {
                    Box(Modifier.background(MaterialTheme.colorScheme.surface).testTag("label").padding(12.dp)) {
                        UvirConnectionLabel(
                            text = label.value, type = uvirSensorConnectionIconType(mode.value),
                            iconColor = Color.Red, textColor = Color.Blue,
                            iconSize = if (status.value) UvirHomeStatusConnectionIconSize else 16.dp,
                            style = if (status.value) androidx.compose.ui.text.TextStyle(fontSize = 11.sp)
                                else sensorRadioOptionTextStyle(),
                            strokeScale = if (status.value) 1.28f * .6f else 1.28f,
                            maxLines = if (status.value) 1 else 2,
                            modifier = Modifier.width(width.value)
                        )
                    }
                }
            }
        }
        for (night in listOf(false, true)) for (layout in listOf(LayoutDirection.Ltr, LayoutDirection.Rtl))
            for (font in listOf(1f, 1.5f)) for (source in SensorConnectionMode.entries)
                for (name in listOf("Ricerca sensore...", "Portatile", "Sensore con nome lungo")) {
                    compose.runOnIdle {
                        dark.value = night; direction.value = layout; scale.value = font; mode.value = source
                        label.value = name; status.value = name == "Ricerca sensore..."
                        width.value = if (name == "Sensore con nome lungo") 100.dp else 180.dp
                    }
                    val pixels = compose.onNodeWithTag("label").captureToImage().toPixelMap()
                    fun center(red: Boolean): Float {
                        var top = pixels.height
                        var bottom = -1
                        for (y in 0 until pixels.height) for (x in 0 until pixels.width) {
                            val c = pixels[x, y]
                            val ink = if (red) c.red > .7f && c.green < .35f && c.blue < .35f
                                else c.blue > .7f && c.red < .35f && c.green < .35f
                            if (ink) { top = minOf(top, y); bottom = maxOf(bottom, y) }
                        }
                        assertTrue("Visible ${if (red) "icon" else "text"}: $name", bottom >= top)
                        return (top + bottom) / 2f
                    }
                    val liftPx = if (source == SensorConnectionMode.USB || source == SensorConnectionMode.WIFI)
                        InstrumentationRegistry.getInstrumentation().targetContext.resources.displayMetrics.density else 0f
                    assertEquals("Optical alignment: $source, $name, night=$night, layout=$layout, font=$font",
                        center(false) - liftPx, center(true), 2f)
                }
    }
}
