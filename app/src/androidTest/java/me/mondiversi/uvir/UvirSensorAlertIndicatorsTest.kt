package me.mondiversi.uvir

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Callback-only UI fixture: never starts sessions, adds records or sends sensor commands. */
class UvirSensorAlertIndicatorsTest {
    @get:Rule val compose = createComposeRule()
    private val dark = mutableStateOf(false)
    private val direction = mutableStateOf(LayoutDirection.Ltr)
    private val selected = mutableStateOf("A")
    private val alerts = mutableStateOf(emptySet<String>())
    private val expanded = mutableStateOf(false)
    private var selectionCount = 0

    private fun showSelector() {
        compose.setContent {
            val view = LocalView.current
            DisposableEffect(view) {
                val previous = view.keepScreenOn
                view.keepScreenOn = true
                onDispose { view.keepScreenOn = previous }
            }
            val configuration = Configuration(LocalConfiguration.current).apply {
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                    if (dark.value) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            }
            CompositionLocalProvider(LocalConfiguration provides configuration,
                LocalLayoutDirection provides direction.value) {
                MaterialTheme(colorScheme = if (dark.value) darkColorScheme() else lightColorScheme()) {
                    val colors = MaterialTheme.colorScheme
                    Box(Modifier.width(330.dp).background(colors.background)) {
                        UvirSensorSelector(
                            sensorDisplayName = "Sensor ${selected.value}", sensorSelectionEnabled = true,
                            sensorInfoEnabled = true,
                            sensorProfiles = listOf("A", "B", "C").mapIndexed { index, id ->
                                UvirSensorProfile(index.toLong() + 1, id, "Sensor $id", 0, 0) },
                            sensorConnectionModes = emptyMap(), selectedConnectionMode = SensorConnectionMode.WIFI,
                            selectedSensorDeviceId = selected.value,
                            primaryText = colors.onSurface, secondaryText = colors.onSurfaceVariant,
                            onOpenSensorInfo = {}, onSensorSelected = { selected.value = it; selectionCount++ },
                            selectionExpanded = expanded.value, onSelectionExpandedChange = { expanded.value = it },
                            sensorStatusIndicators = listOf("a", "b", "c").associateWith {
                                uvirStatusIndicator(false, true, false, false) },
                            alertMonitoringDeviceIds = alerts.value
                        )
                    }
                }
            }
        }
        compose.mainClock.autoAdvance = false
        settle()
    }

    private fun settle() {
        compose.mainClock.advanceTimeBy(32)
        compose.waitForIdle()
    }

    private fun nameColor(): Color {
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText("Sensor ${selected.value}", useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        return layouts.single().layoutInput.style.color
    }

    private fun rowBackground(id: String): Color {
        val pixels = compose.onNodeWithTag("home_sensor_option_$id").captureToImage().toPixelMap()
        // Interior of the rounded surface, above text/dot/radio content.
        return pixels[pixels.width / 2, 3]
    }

    private fun dotColor(id: String): Color {
        val pixels = compose.onNodeWithTag("home_sensor_status_$id", useUnmergedTree = true)
            .captureToImage().toPixelMap()
        // Connection symbols have holes: the center can show the row's alert pulse.
        val expected = Color(UvirStatusDot.GREEN.colorArgb)
        return (0 until pixels.height).flatMap { y -> (0 until pixels.width).map { x -> pixels[x, y] } }
            .minBy { kotlin.math.abs(it.red - expected.red) + kotlin.math.abs(it.green - expected.green) + kotlin.math.abs(it.blue - expected.blue) }
    }

    @Test fun foreignAlertsPulseOnlyTheNameAndMatchingRowsWhileChevronStaysUnchanged() {
        showSelector()
        for (night in listOf(false, true)) for (layout in listOf(LayoutDirection.Ltr, LayoutDirection.Rtl)) {
            compose.runOnIdle {
                dark.value = night; direction.value = layout; selected.value = "A"
                alerts.value = emptySet(); expanded.value = false
            }
            settle()
            val normalName = nameColor()
            val normalChevron = compose.onNodeWithTag("home_sensor_name_chevron", true)
                .captureToImage().toPixelMap()
            compose.runOnIdle { alerts.value = setOf("b", "c") }
            settle()
            val fadingName = nameColor()
            val dimChevron = compose.onNodeWithTag("home_sensor_name_chevron", true)
                .captureToImage().toPixelMap()
            compose.mainClock.advanceTimeBy(1_100)
            compose.waitForIdle()
            val brightChevron = compose.onNodeWithTag("home_sensor_name_chevron", true)
                .captureToImage().toPixelMap()
            assertNotEquals("Name fades when another sensor has alerts", normalName, fadingName)
            assertNotEquals("Name animation changes over time", fadingName, nameColor())
            assertTrue("Chevron retains its original color throughout the name pulse",
                (0 until normalChevron.width).all { x ->
                    (0 until normalChevron.height).all { y ->
                        normalChevron[x, y] == dimChevron[x, y] && normalChevron[x, y] == brightChevron[x, y]
                    }
                })

            compose.onNodeWithTag("home_sensor_name").performClick()
            settle()
            val aBefore = rowBackground("A")
            val bBefore = rowBackground("B")
            val cBefore = rowBackground("C")
            val connectionDot = dotColor("B")
            compose.mainClock.advanceTimeBy(1_100)
            compose.waitForIdle()
            assertEquals("Unrelated sensor row stays unchanged", aBefore, rowBackground("A"))
            assertNotEquals("B row pulses", bBefore, rowBackground("B"))
            assertNotEquals("C row pulses independently of selection", cBefore, rowBackground("C"))
            for (color in listOf(connectionDot, dotColor("B"))) {
                val expected = Color(UvirStatusDot.GREEN.colorArgb)
                assertEquals("Connection tint stays green while its background pulses", expected.red, color.red, .025f)
                assertEquals(expected.green, color.green, .025f)
                assertEquals(expected.blue, color.blue, .025f)
            }
            compose.onNodeWithTag("home_sensor_option_A").assertIsSelected()
            compose.onNodeWithTag("home_sensor_option_B").assertIsNotSelected()

            compose.runOnIdle { alerts.value = setOf("b") }
            settle()
            compose.onNodeWithTag("home_sensor_option_B").performClick()
            settle()
            assertEquals("B", selected.value)
            assertEquals("No foreign warning when only the selected sensor has alerts", normalName, nameColor())
            compose.runOnIdle { alerts.value = emptySet(); expanded.value = true }
            settle()
            val stopped = rowBackground("B")
            compose.mainClock.advanceTimeBy(1_100)
            compose.waitForIdle()
            assertEquals("Stopping the last alert restores a stable background", stopped, rowBackground("B"))
            compose.runOnIdle { expanded.value = false }
            settle()
        }
        assertEquals(4, selectionCount)
        compose.mainClock.autoAdvance = true
    }
}
