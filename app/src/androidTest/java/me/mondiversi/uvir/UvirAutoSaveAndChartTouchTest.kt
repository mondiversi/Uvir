package me.mondiversi.uvir

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Empty Compose host with stub saves only; no user preferences/DB/sensor writes. */
class UvirAutoSaveAndChartTouchTest {
    @get:Rule val compose = createComposeRule()
    private val saves = CopyOnWriteArrayList<Pair<SettingsSaveGroup, String>>()

    @Composable private fun KeepAwake() {
        val view = LocalView.current
        DisposableEffect(view) {
            val before = view.keepScreenOn
            view.keepScreenOn = true
            onDispose { view.keepScreenOn = before }
        }
    }

    @Test fun numberEditingWaitsForBlurThenNormalizesBeforeSaving() {
        compose.setContent {
            KeepAwake()
            MaterialTheme {
                var text by remember { mutableStateOf("5") }
                var checked by remember { mutableStateOf(false) }
                val scope = rememberCoroutineScope()
                val queue = remember { UvirSettingsSaveQueue(scope, {}) }
                queue.save = { saves.add(it to text) }
                CompositionLocalProvider(LocalSettingsCommit provides
                    UvirSettingsCommitScope(queue, SettingsSaveGroup.PARAMETERS)) {
                    Column {
                        SettingsAutoSaveGroup(SettingsSaveGroup.SAMPLING) {
                            NumberField(text, { text = it }, "Samples", Modifier.testTag("number"),
                                maxChars = 5, onEditingComplete = {
                                    text = normalizeBoundedInteger(text, 1, 21).text
                                })
                        }
                        CheckSettingRow(checked, { checked = it }, "Enabled", compact = true)
                    }
                }
            }
        }
        compose.onNodeWithTag("number").performTextReplacement("99")
        compose.waitForIdle()
        assertTrue(saves.isEmpty())
        compose.onNodeWithText("Enabled").performClick()
        compose.waitUntil(5_000) { saves.size == 2 }
        assertEquals(SettingsSaveGroup.SAMPLING, saves[0].first)
        assertEquals("21", saves[0].second)
        assertEquals(SettingsSaveGroup.PARAMETERS, saves[1].first)
        compose.onNodeWithTag("number").assertTextContains("21")
    }

    @Test fun checkboxAndRadioSaveWithoutAnApplyButton() {
        compose.setContent {
            KeepAwake()
            MaterialTheme {
                var checked by remember { mutableStateOf(false) }
                var selected by remember { mutableStateOf(false) }
                val scope = rememberCoroutineScope()
                val queue = remember { UvirSettingsSaveQueue(scope, {}) }
                queue.save = { saves.add(it to "$checked/$selected") }
                CompositionLocalProvider(LocalSettingsCommit provides
                    UvirSettingsCommitScope(queue, SettingsSaveGroup.PARAMETERS)) {
                    Column {
                        CheckSettingRow(checked, { checked = it }, "Enabled", compact = true)
                        SettingsAutoSaveGroup(SettingsSaveGroup.ALERTS) {
                            SettingsRadioChoiceRow(selected, Color.Black, Color.Gray,
                                onClick = { selected = true }) { Text("Sound") }
                        }
                    }
                }
            }
        }
        compose.onNodeWithText("Enabled").performClick()
        compose.waitUntil(5_000) { saves.size == 1 }
        assertEquals("true/false", saves[0].second)
        compose.onNodeWithText("Sound").performClick()
        compose.waitUntil(5_000) { saves.size == 2 }
        assertEquals(SettingsSaveGroup.ALERTS, saves[1].first)
        assertEquals("true/true", saves[1].second)
    }

    @Test fun sliderSavesOnlyAfterTheDragEnds() {
        compose.setContent {
            KeepAwake()
            MaterialTheme {
                var value by remember { mutableFloatStateOf(10f) }
                val scope = rememberCoroutineScope()
                val queue = remember { UvirSettingsSaveQueue(scope, {}) }
                queue.save = { saves.add(it to value.toString()) }
                CompositionLocalProvider(LocalSettingsCommit provides
                    UvirSettingsCommitScope(queue, SettingsSaveGroup.PARAMETERS)) {
                    SensorParameterSlider("Brightness", value, true, Color.Black, Color.Gray, { value = it })
                }
            }
        }
        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.SetProgress)).performTouchInput {
            down(Offset(width * .1f, height / 2f))
            moveTo(Offset(width * .8f, height / 2f), 300)
        }
        compose.waitForIdle()
        assertTrue(saves.isEmpty())
        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.SetProgress)).performTouchInput { up() }
        compose.waitUntil(5_000) { saves.size == 1 }
        assertTrue(saves[0].second.toFloat() > 50f)
    }

    private fun chartFixture(night: Boolean, rtl: Boolean, bars: Boolean) {
        compose.setContent {
            KeepAwake()
            MaterialTheme(colorScheme = if (night) darkColorScheme() else lightColorScheme()) {
                CompositionLocalProvider(LocalLayoutDirection provides
                    if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                    UvirSavedChartInspector(
                        points = listOf(UvirSavedChartPoint(UvirChartCoordinate(.5f, .5f),
                            "UVA", "12.345 µW/cm²", Color.Blue, "14/09/2026 18:42:01")),
                        modifier = Modifier.size(300.dp, 190.dp).testTag("plot"), bars = bars
                    ) { modifier -> Canvas(modifier) { drawCircle(Color.Blue, 5f, center) } }
                }
            }
        }
        compose.onNodeWithTag("plot").performTouchInput { click(center) }
        compose.onNodeWithText("12.345 µW/cm²").assertIsDisplayed()
        compose.onNodeWithText("14/09/2026 18:42:01").assertIsDisplayed()
        compose.onNodeWithTag("plot").performTouchInput { click(center) }
        compose.onNodeWithText("12.345 µW/cm²").assertDoesNotExist()
    }
    @Test fun savedBarsExposeValuesAndDismissTheMarker() = chartFixture(false, false, true)
    @Test fun savedLinesWorkInNightModeAndRtl() = chartFixture(true, true, false)
}
