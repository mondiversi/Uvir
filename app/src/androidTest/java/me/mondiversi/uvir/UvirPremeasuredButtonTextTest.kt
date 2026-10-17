package me.mondiversi.uvir

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Capture the very first actual text layout, not only the result after UI-idle. */
class UvirPremeasuredButtonTextTest {
    @get:Rule val compose = createComposeRule()

    @Test fun labelFitsOnItsFirstLayoutAndCanGrowAgainWhenTheAvailableWidthChanges() {
        // Also fit within the readable 10sp minimum on the phone's 1.1x font scale.
        val width = mutableStateOf(100.dp)
        val layouts = mutableListOf<TextLayoutResult>()
        compose.setContent {
            val view = LocalView.current
            DisposableEffect(view) {
                val old = view.keepScreenOn; view.keepScreenOn = true
                onDispose { view.keepScreenOn = old }
            }
            MaterialTheme {
                Box(Modifier.width(width.value)) {
                    AdaptiveSingleLineButtonText("ESPORTAZIONE", fontWeight = FontWeight.SemiBold,
                        onTextLayout = { layouts += it })
                }
            }
        }
        compose.runOnIdle {
            assertTrue(layouts.isNotEmpty())
            assertFalse("First layout must fit at ${layouts.first().layoutInput.style.fontSize}", layouts.first().hasVisualOverflow)
            assertTrue(layouts.first().layoutInput.style.fontSize.value < 14f)
            assertEquals(1, layouts.map { it.layoutInput.style.fontSize }.distinct().size)
            layouts.clear()
            width.value = 240.dp
        }
        compose.waitUntil(3_000) { layouts.isNotEmpty() }
        compose.runOnIdle {
            assertEquals(14f, layouts.first().layoutInput.style.fontSize.value)
            assertFalse(layouts.first().hasVisualOverflow)
        }
    }

    @Test fun pairedResetLabelsUseTheSameSizeAlreadyOnTheFirstLayout() {
        val sensorLayouts = mutableListOf<TextLayoutResult>()
        val appLayouts = mutableListOf<TextLayoutResult>()
        compose.setContent {
            MaterialTheme {
                Column(Modifier.width(60.dp)) {
                    val labels = listOf("SENSORE", "APP")
                    AdaptiveSingleLineButtonText(labels[0], sizingTexts = labels, fontWeight = FontWeight.SemiBold,
                        onTextLayout = { sensorLayouts += it })
                    AdaptiveSingleLineButtonText(labels[1], sizingTexts = labels, fontWeight = FontWeight.SemiBold,
                        onTextLayout = { appLayouts += it })
                }
            }
        }
        compose.runOnIdle {
            assertFalse(sensorLayouts.first().hasVisualOverflow)
            assertFalse(appLayouts.first().hasVisualOverflow)
            assertEquals(sensorLayouts.first().layoutInput.style.fontSize, appLayouts.first().layoutInput.style.fontSize)
        }
    }
}
