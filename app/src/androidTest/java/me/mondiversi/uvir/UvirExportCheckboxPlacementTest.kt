package me.mondiversi.uvir

import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Isolated export rows: no real files, records, settings or sensor commands. */
class UvirExportCheckboxPlacementTest {
    @get:Rule val compose = createComposeRule()
    private val night = mutableStateOf(false)
    private val direction = mutableStateOf(LayoutDirection.Ltr)
    private val selected = List(3) { mutableStateOf(false) }
    private val toggles = IntArray(3)

    @Test fun radiosKeepACompactIndentWithOneExtraStepForNestedChoices() {
        compose.setContent {
            MaterialTheme {
                Column(Modifier.width(320.dp).testTag("export-radio-rows")) {
                    ChartExportModeRow(false, "Grouping trial", Color.Black, Color.Gray) {}
                    ChartExportModeRow(false, "Variant trial", Color.Black, Color.Gray,
                        modifier = Modifier.padding(start = 22.dp)) {}
                }
            }
        }
        val rowBounds = compose.onNodeWithTag("export-radio-rows").fetchSemanticsNode().boundsInRoot
        val radios = compose.onAllNodes(isSelectable(), useUnmergedTree = true)
            .fetchSemanticsNodes()
        assertEquals(2, radios.size)
        val density = InstrumentationRegistry.getInstrumentation()
            .targetContext.resources.displayMetrics.density
        assertEquals(38f * density, radios[0].boundsInRoot.left - rowBounds.left, 1f)
        assertEquals(60f * density, radios[1].boundsInRoot.left - rowBounds.left, 1f)
    }

    @Test fun checkboxesLeadEveryFormatInBothThemesAndLayoutDirections() {
        val chartLabel = InstrumentationRegistry.getInstrumentation()
            .targetContext.getString(R.string.share_as_charts)
        val labels = listOf("CSV trial", "Table trial", chartLabel)
        compose.setContent {
            val view = LocalView.current
            DisposableEffect(view) {
                val previous = view.keepScreenOn
                view.keepScreenOn = true
                onDispose { view.keepScreenOn = previous }
            }
            CompositionLocalProvider(LocalLayoutDirection provides direction.value) {
                MaterialTheme(colorScheme = if (night.value) darkColorScheme() else lightColorScheme()) {
                    val primary = if (night.value) Color.White else Color.Black
                    Column(Modifier.width(320.dp)) {
                        listOf(MeasurementShareFormat.CSV, MeasurementShareFormat.READABLE_TABLE)
                            .forEachIndexed { index, format ->
                                ShareFormatOptionRow(format, selected[index].value, labels[index],
                                    primary, Color.Gray) {
                                    toggles[index]++
                                    selected[index].value = !selected[index].value
                                }
                            }
                        ShareChartsOptionRow(selected[2].value, primary, Color.Gray) {
                            toggles[2]++
                            selected[2].value = !selected[2].value
                        }
                    }
                }
            }
        }

        for (dark in listOf(false, true)) {
            for (layout in listOf(LayoutDirection.Ltr, LayoutDirection.Rtl)) {
                compose.runOnIdle {
                    night.value = dark
                    direction.value = layout
                    selected.forEach { it.value = false }
                    toggles.fill(0)
                }
                val checkboxes = compose.onAllNodes(isToggleable(), useUnmergedTree = true)
                assertEquals(3, checkboxes.fetchSemanticsNodes().size)
                labels.forEachIndexed { index, label ->
                    val checkbox = checkboxes[index]
                    val labelNode = compose.onNodeWithText(label, useUnmergedTree = true)
                    val controlBounds = checkbox.fetchSemanticsNode().boundsInRoot
                    val textBounds = labelNode.fetchSemanticsNode().boundsInRoot
                    assertTrue("Checkbox must precede icon and label: $label / $layout",
                        if (layout == LayoutDirection.Ltr) controlBounds.right < textBounds.left
                        else controlBounds.left > textBounds.right)
                    assertTrue(abs(controlBounds.center.y - textBounds.center.y) < 1f)
                    checkbox.assertIsOff()
                    compose.onNodeWithText(label).performClick()
                    checkbox.assertIsOn()
                    checkbox.performClick()
                    checkbox.assertIsOff()
                    compose.runOnIdle { assertEquals(2, toggles[index]) }
                }
            }
        }
    }
}
