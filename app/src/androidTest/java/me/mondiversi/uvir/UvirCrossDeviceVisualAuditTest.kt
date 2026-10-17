package me.mondiversi.uvir

import android.content.res.Configuration
import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.*
import androidx.test.espresso.Espresso
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Diagnostic fixtures only: no user preferences, archive or sensor commands. */
class UvirCrossDeviceVisualAuditTest {
    @get:Rule val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Composable private fun KeepAwake(content: @Composable () -> Unit) {
        val view = LocalView.current
        DisposableEffect(view) {
            val previous = view.keepScreenOn
            view.keepScreenOn = true
            onDispose { view.keepScreenOn = previous }
        }
        content()
    }

    private fun textLayout(node: SemanticsNodeInteraction): TextLayoutResult {
        val layouts = mutableListOf<TextLayoutResult>()
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        return layouts.single()
    }

    @Test fun filterDropdownUsesTheSameAdaptiveDensityAsItsHost() {
        val forceEnlarged = mutableStateOf(false)
        val dark = mutableStateOf(false)
        val rtl = mutableStateOf(false)
        var hostDensity = 0f
        compose.setContent {
            KeepAwake {
                val configuration = Configuration(LocalConfiguration.current).apply {
                    if (forceEnlarged.value) smallestScreenWidthDp = 800
                    uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                        if (dark.value) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
                }
                CompositionLocalProvider(LocalConfiguration provides configuration,
                    LocalLayoutDirection provides if (rtl.value) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                    UvirAdaptiveDisplay {
                        val density = LocalDensity.current
                        SideEffect { hostDensity = density.density }
                        MaterialTheme(colorScheme = if (dark.value) darkColorScheme() else lightColorScheme()) {
                            UvirRecordListFilterPanel(UvirRecordListFilters(), mapOf("A" to "Audit sensor"),
                                setOf(true, false), {}, notes = listOf("Audit note"))
                        }
                    }
                }
            }
        }
        val mismatches = mutableListOf<String>()
        for (enlarged in listOf(false, true)) for (night in listOf(false, true))
            for (rightToLeft in listOf(false, true)) {
                compose.runOnIdle { forceEnlarged.value = enlarged; dark.value = night; rtl.value = rightToLeft }
                compose.onNodeWithTag("filter-note").performScrollTo().performTouchInput { click(center) }
                val option = compose.onNodeWithTag("filter-choice-option-Audit note").assertIsDisplayed()
                val actual = textLayout(compose.onNodeWithText("Audit note", true)).layoutInput.density.density
                val report = "filter enlarged=$enlarged night=$night rtl=$rightToLeft host=$hostDensity popup=$actual"
                Log.i("UvirVisualAudit", report)
                if (kotlin.math.abs(hostDensity - actual) > .01f) mismatches += report
                option.performClick()
                Espresso.pressBack()
            }
        assertTrue(mismatches.joinToString("\n"), mismatches.isEmpty())
    }

    @Test fun variantDropdownUsesTheSameAdaptiveDensityAsItsHost() {
        var hostDensity = 0f
        compose.setContent {
            KeepAwake {
                UvirAdaptiveDisplay {
                    val density = LocalDensity.current
                    SideEffect { hostDensity = density.density }
                    MaterialTheme {
                        Column(Modifier.width(192.dp)) {
                            val colors = MaterialTheme.colorScheme
                            UvirSessionCyclePositionSelector(4, 1, colors.surface,
                                colors.onSurface, colors.onSurfaceVariant, {})
                        }
                    }
                }
            }
        }
        val label = context.getString(R.string.session_sequence_filter_position) + " (4)"
        compose.onNodeWithContentDescription(label).performTouchInput { click(center) }
        val option = compose.onNodeWithText("B").assertIsDisplayed()
        val actual = textLayout(option).layoutInput.density.density
        Log.i("UvirVisualAudit", "variant host=$hostDensity popup=$actual")
        option.performClick()
        assertEquals("Variant menu must keep the screen's adaptive scale", hostDensity, actual, .01f)
    }

    @Test fun phoneDialogUsesFullAvailableWidth() {
        compose.setContent {
            KeepAwake {
                // A compact logical phone width must not cap the actual dialog window.
                val configuration = Configuration(LocalConfiguration.current).apply {
                    smallestScreenWidthDp = 320
                    screenWidthDp = 320
                }
                CompositionLocalProvider(LocalConfiguration provides configuration) {
                    MaterialTheme {
                        Box(Modifier.fillMaxSize().testTag("width_host"))
                        UvirAlertDialog(onDismissRequest = {}, confirmButton = null,
                            modifier = Modifier.testTag("width_audit"), title = { Text("Popup") }, text = { Text("Body") })
                    }
                }
            }
        }
        val viewportWidth = compose.onNodeWithTag("width_host").fetchSemanticsNode().boundsInRoot.width
        val actual = compose.onNodeWithTag("width_audit").assertIsDisplayed().fetchSemanticsNode().boundsInRoot.width
        assertEquals("Phone popup has no external lateral margins", viewportWidth, actual, 1f)
    }

    @Test fun tabletDialogUsesFullAvailableWidth() {
        compose.setContent {
            KeepAwake {
                val configuration = Configuration(LocalConfiguration.current).apply {
                    smallestScreenWidthDp = 800
                }
                CompositionLocalProvider(LocalConfiguration provides configuration) {
                    UvirAdaptiveDisplay {
                        MaterialTheme {
                            Box(Modifier.fillMaxSize().testTag("width_host"))
                            UvirAlertDialog(onDismissRequest = {}, confirmButton = null,
                                modifier = Modifier.testTag("width_audit"),
                                title = { Text("Popup") }, text = { Text("Body") })
                        }
                    }
                }
            }
        }
        val actual = compose.onNodeWithTag("width_audit").assertIsDisplayed()
            .fetchSemanticsNode().boundsInRoot.width
        val viewportWidth = compose.onNodeWithTag("width_host").fetchSemanticsNode().boundsInRoot.width
        assertEquals("Tablet popup has no external lateral margins", viewportWidth, actual, 1f)
    }

    @Test fun dialogInputAndActionsRemainReachableWithKeyboardAndLargeFonts() {
        val fontScale = mutableStateOf(1f)
        var text by mutableStateOf("")
        compose.setContent {
            KeepAwake {
                val nativeDensity = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(nativeDensity.density, fontScale.value)) {
                    UvirAdaptiveDisplay {
                        MaterialTheme {
                            UvirAlertDialog(onDismissRequest = {}, title = { Text("Modifica la nota") },
                                confirmButton = { TextButton({}, Modifier.testTag("input_confirm")) { Text("SALVA") } },
                                dismissButton = { TextButton({}, Modifier.testTag("input_cancel")) { Text("ANNULLA") } },
                                text = {
                                    OutlinedTextField(text, { text = it }, label = { Text("Nota") },
                                        modifier = Modifier.fillMaxWidth().testTag("audit_note"))
                                    repeat(20) { Text("Riga diagnostica $it", Modifier.testTag("input_row_$it")) }
                                })
                        }
                    }
                }
            }
        }
        for (scale in listOf(1f, 1.6f)) {
            compose.runOnIdle { fontScale.value = scale }
            compose.onNodeWithTag("audit_note").performScrollTo().performClick().performTextReplacement("Solo prova")
            compose.onNodeWithTag("input_confirm").assertIsDisplayed()
            compose.onNodeWithTag("input_cancel").assertIsDisplayed()
            compose.onNodeWithTag("audit_note").assertIsDisplayed()
            Espresso.closeSoftKeyboard()
            compose.onNodeWithTag("input_row_19").performScrollTo().assertIsDisplayed()
        }
    }

    @Test fun exportOptionsAndActionsRemainCompleteWithLargeTranslatedText() {
        val language = mutableStateOf("it")
        val fontScale = mutableStateOf(1f)
        val epoch = mutableIntStateOf(0)
        var localized = context
        compose.setContent {
            KeepAwake {
                val configuration = Configuration(LocalConfiguration.current).apply {
                    setLocale(java.util.Locale.forLanguageTag(language.value))
                }
                val translated = context.createConfigurationContext(configuration)
                val density = LocalDensity.current
                SideEffect { localized = translated }
                CompositionLocalProvider(LocalContext provides translated,
                    LocalResources provides translated.resources, LocalConfiguration provides configuration,
                    LocalDensity provides Density(density.density, fontScale.value),
                    LocalLayoutDirection provides if (language.value == "ar") LayoutDirection.Rtl else LayoutDirection.Ltr) {
                    UvirAdaptiveDisplay {
                        MaterialTheme {
                            UvirActionTheme {
                                val colors = MaterialTheme.colorScheme
                                key(epoch.intValue) {
                                    MeasurementDataExportScreen(colors.background, colors.surface,
                                        colors.onSurface, colors.onSurfaceVariant,
                                        readableTableFileCount = 2, readableTableGroupingAvailable = true,
                                        showReadableTableGroupingScopeNote = true,
                                        combinedChartFileCount = 2, separateChartFileCount = 10,
                                        variantChartGroupingAvailable = true, groupedVariantChartFileCount = 5,
                                        showVariantChartGroupingScopeNote = true,
                                        onDismiss = {}, onSelectionConfirmed = { _, _ -> error("No export during visual audit") })
                                }
                            }
                        }
                    }
                }
            }
        }
        val labels = listOf(R.string.share_as_csv, R.string.share_as_readable_table,
            R.string.share_charts_combined_groups, R.string.share_charts_separate_groups,
            R.string.share_variant_charts_by_variant, R.string.share_variant_charts_by_group,
            R.string.share_readable_table_by_acquisition, R.string.share_readable_table_by_spectral_area)
        for (tag in listOf("it", "de", "fr", "ar")) for (scale in listOf(1f, 1.6f)) {
            compose.runOnIdle { language.value = tag; fontScale.value = scale; epoch.intValue++ }
            // Wait for the localized context's SideEffect before constructing selectors.
            compose.waitForIdle()
            for (id in listOf(R.string.share_as_csv, R.string.share_as_readable_table)) {
                compose.onNodeWithText(localized.getString(id)).performScrollTo().performClick()
            }
            compose.onNodeWithText(localized.getString(R.string.share_as_charts)).performScrollTo().performClick()
            compose.onNodeWithText(localized.getString(R.string.share_charts_combined_groups))
                .performScrollTo().performClick()
            for (label in labels.map { localized.getString(it) }.distinct()) {
                val nodes = compose.onAllNodesWithText(label, useUnmergedTree = true)
                assertTrue("Missing export label: $tag $label", nodes.fetchSemanticsNodes().isNotEmpty())
                for (index in nodes.fetchSemanticsNodes().indices) {
                    val node = nodes[index].performScrollTo().assertIsDisplayed()
                    val layout = textLayout(node)
                    assertFalse("Export label is clipped: $tag scale=$scale label=$label", layout.didOverflowHeight)
                    for (line in 0 until layout.lineCount) {
                        assertFalse("Export label is ellipsized: $tag scale=$scale label=$label", layout.isLineEllipsized(line))
                    }
                }
            }
            compose.onNodeWithText(localized.getString(R.string.save)).performScrollTo().assertIsDisplayed().assertIsEnabled()
            compose.onNodeWithText(localized.getString(R.string.share)).performScrollTo().assertIsDisplayed().assertIsEnabled()
        }
    }
}
