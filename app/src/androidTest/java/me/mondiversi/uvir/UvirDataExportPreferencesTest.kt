package me.mondiversi.uvir

import android.content.Context
import android.content.ContextWrapper
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import java.util.UUID
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

/** Isolated preferences and callback-only exports: no real records, files or settings changed. */
class UvirDataExportPreferencesTest {
    @get:Rule val compose = createComposeRule()
    private val base = InstrumentationRegistry.getInstrumentation().targetContext
    private val prefix = "export-options-trial-${UUID.randomUUID()}-"
    private val context = object : ContextWrapper(base) {
        override fun getApplicationContext(): Context = this
        override fun getSharedPreferences(name: String, mode: Int) =
            base.getSharedPreferences(prefix + name, mode)
    }
    private val open = mutableStateOf(true)
    private val selections = mutableListOf<MeasurementDetailShareSelection>()
    private val destinations = mutableListOf<UvirExportDestination>()
    private val choices = UvirDataExportPreferences(
        chartExportMode = UvirChartExportMode.COMBINED,
        readableTableGrouping = UvirReadableTableGrouping.BY_SPECTRAL_AREA,
        variantChartGrouping = UvirVariantChartGrouping.BY_GROUP
    )

    @After fun cleanUp() {
        base.deleteSharedPreferences(prefix + UVIR_PREFERENCES_NAME)
    }

    private fun seedLegacySelections() {
        context.getSharedPreferences(UVIR_PREFERENCES_NAME, Context.MODE_PRIVATE).edit()
            .putBoolean("export_csv_selected", true)
            .putBoolean("export_readable_table_selected", true)
            .putBoolean("export_charts_selected", true)
            .putString("export_chart_mode", choices.chartExportMode.name)
            .putString("export_readable_table_grouping", choices.readableTableGrouping.name)
            .putString("export_variant_chart_grouping", choices.variantChartGrouping.name)
            .putString(UVIR_EXPORT_MODE_KEY, UvirExportMode.SELECTED.storedValue)
            .commit()
    }

    private fun showExport() {
        compose.setContent {
            val view = LocalView.current
            DisposableEffect(view) {
                val previous = view.keepScreenOn
                view.keepScreenOn = true
                onDispose { view.keepScreenOn = previous }
            }
            CompositionLocalProvider(LocalContext provides context) {
                MaterialTheme {
                    if (open.value) MeasurementDataExportScreen(
                        backgroundColor = Color(0xFFF5F5F5), cardColor = Color.White,
                        primaryText = Color.Black, secondaryText = Color.Gray,
                        readableTableGroupingAvailable = true, readableTableFileCount = 2,
                        variantChartGroupingAvailable = true, combinedChartFileCount = 2,
                        separateChartFileCount = 10, groupedVariantChartFileCount = 5,
                        onDismiss = { open.value = false },
                        onSelectionConfirmed = { selection, destination ->
                            selections.add(selection)
                            destinations.add(destination)
                            open.value = false
                        }
                    ) else TextButton(onClick = { open.value = true }) { Text("Open export trial") }
                }
            }
        }
    }

    private fun clickLabel(id: Int) {
        compose.onNodeWithText(context.getString(id)).performScrollTo().performClick()
    }

    private fun assertNoFormatsSelected() {
        val checkboxes = compose.onAllNodes(isToggleable(), useUnmergedTree = true)
        assertEquals(3, checkboxes.fetchSemanticsNodes().size)
        for (index in 0..2) checkboxes[index].assertIsOff()
        for (id in listOf(R.string.save, R.string.share)) {
            compose.onNodeWithText(context.getString(id)).performScrollTo().assertIsNotEnabled()
        }
    }

    @Test fun legacySelectionsAreRemovedWithoutChangingRadioChoices() {
        seedLegacySelections()
        assertEquals(choices, loadUvirDataExportPreferences(context))
        saveUvirDataExportPreferences(context, choices)
        val prefs = context.getSharedPreferences(UVIR_PREFERENCES_NAME, Context.MODE_PRIVATE)
        for (key in listOf("export_csv_selected", "export_readable_table_selected", "export_charts_selected")) {
            assertFalse(prefs.contains(key))
        }
        assertEquals(choices, loadUvirDataExportPreferences(context))
        assertEquals(UvirExportMode.SELECTED, loadUvirExportMode(context))
    }

    @Test fun reopeningStartsUncheckedButKeepsAllRadioChoicesForSaveAndShare() {
        seedLegacySelections()
        showExport()
        assertNoFormatsSelected()
        clickLabel(R.string.share_as_csv)
        clickLabel(R.string.share_as_readable_table)
        clickLabel(R.string.share_as_charts)
        clickLabel(R.string.save)
        compose.waitForIdle()
        assertEquals(1, selections.size)
        assertEquals(UvirExportDestination.SAVE, destinations.last())
        assertEquals(MeasurementShareFormat.BOTH, selections.last().dataFormat)
        assertEquals(true, selections.last().includeCharts)
        assertEquals(choices.chartExportMode, selections.last().chartExportMode)
        assertEquals(choices.readableTableGrouping, selections.last().readableTableGrouping)
        assertEquals(choices.variantChartGrouping, selections.last().variantChartGrouping)
        compose.onNodeWithText("Open export trial").performClick()
        assertNoFormatsSelected()
        clickLabel(R.string.share_as_charts)
        clickLabel(R.string.share_charts_separate_groups)
        clickLabel(R.string.export_format_international)
        clickLabel(R.string.share)
        compose.waitForIdle()
        assertEquals(UvirExportDestination.SHARE, destinations.last())
        assertEquals(UvirChartExportMode.SEPARATE, loadUvirDataExportPreferences(context).chartExportMode)
        assertEquals(UvirExportMode.INTERNATIONAL, loadUvirExportMode(context))
        compose.onNodeWithText("Open export trial").performClick()
        assertNoFormatsSelected()
        clickLabel(R.string.share_as_charts)
        clickLabel(R.string.save)
        compose.waitForIdle()
        assertEquals(UvirChartExportMode.SEPARATE, selections.last().chartExportMode)
        assertEquals(choices.variantChartGrouping, selections.last().variantChartGrouping)
        assertEquals(choices.readableTableGrouping, selections.last().readableTableGrouping)
    }
}
