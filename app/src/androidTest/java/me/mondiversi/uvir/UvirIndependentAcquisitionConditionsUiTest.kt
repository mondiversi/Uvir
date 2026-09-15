package me.mondiversi.uvir

import android.content.Context
import android.content.res.Configuration
import java.util.Locale
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Isolated drafts and composables only. No real sensor, records or app preferences. */
class UvirIndependentAcquisitionConditionsUiTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun rules() = acquisitionConditionMetrics().map {
        ThresholdAlertRule(it, it == ThresholdAlertMetric.UVC, ThresholdAlertDirection.BELOW, 7f)
    }
    private fun withPreferences(block: (android.content.SharedPreferences, String) -> Unit) {
        val name = "independent_conditions_test_" + System.nanoTime()
        try { block(context.getSharedPreferences(name, Context.MODE_PRIVATE), name) }
        finally { context.deleteSharedPreferences(name) }
    }

    @Test fun conditionsPersistAndNeverInheritOrOverwriteValueAlerts() = withPreferences { prefs, name ->
        val alerts = ThresholdAlertSettings(false,
            listOf(ThresholdAlertRule(ThresholdAlertMetric.UVA, true, ThresholdAlertDirection.ABOVE, 3f)),
            30, ThresholdAlertSound.SINGLE_BEEP, 10)
        saveThresholdAlertSettings(prefs, alerts)
        assertFalse(loadAcquisitionConditions(prefs).any { it.enabled })
        assertTrue(saveAcquisitionConditions(prefs, rules()))
        assertEquals(alerts.rules, loadThresholdAlertSettings(prefs).rules.filter { it.enabled })
        saveThresholdAlertSettings(prefs, alerts.copy(rules = alerts.rules.map { it.copy(threshold = 50f) }))
        val reopened = loadAcquisitionConditions(context.getSharedPreferences(name, Context.MODE_PRIVATE))
        // Verify the actual persisted key without relying on a separate global setting.
        assertEquals(rules(), loadAcquisitionConditions(prefs))
        assertEquals(rules(), reopened)
        assertEquals(50f, loadThresholdAlertSettings(prefs).rules.first { it.enabled }.threshold)
        val snapshot = conditionalPlanFromRules(loadAcquisitionConditions(prefs),
            AcquisitionConditionMatch.ANY, AcquisitionConditionAction.ACQUIRE)!!
        assertEquals(7f, snapshot.rules.single().threshold)
    }

    @Test fun dedicatedCriteriaStayWithTheirOwnSensorProfile() = withPreferences { prefs, _ ->
        assertTrue(saveAcquisitionConditions(prefs, rules()))
        assertTrue(saveSelectedSensorContext(prefs, "condition_sensor_a"))
        assertTrue(restoreSelectedSensorContext(prefs, "condition_sensor_b"))
        assertFalse(loadAcquisitionConditions(prefs).any { it.enabled })
        val other = rules().map { it.copy(threshold = 12f) }
        assertTrue(saveAcquisitionConditions(prefs, other))
        assertTrue(saveSelectedSensorContext(prefs, "condition_sensor_b"))
        assertTrue(restoreSelectedSensorContext(prefs, "condition_sensor_a"))
        assertEquals(rules(), loadAcquisitionConditions(prefs))
        assertTrue(restoreSelectedSensorContext(prefs, "condition_sensor_b"))
        assertEquals(other, loadAcquisitionConditions(prefs))
    }

    @Test fun islandAlwaysExpandsButChoicesRequireConfiguredConditions() {
        val saved = mutableStateOf(rules().map { it.copy(enabled = false) })
        val selected = mutableStateOf(false)
        val dark = mutableStateOf(false)
        val show = mutableStateOf(false)
        var matchCalls = 0; var actionCalls = 0
        var title = ""; var configure = ""; var save = ""; var cancel = ""; var warning = ""
        compose.setContent {
            val local = LocalContext.current
            title = local.getString(R.string.conditional_acquisition)
            configure = local.getString(R.string.conditional_configure)
            save = local.getString(R.string.save); cancel = local.getString(R.string.cancel)
            warning = local.getString(R.string.conditional_no_rules)
            MaterialTheme(colorScheme = if (dark.value) darkColorScheme() else lightColorScheme()) {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    UvirConditionalAcquisitionCard(selected.value, AcquisitionConditionMatch.ANY,
                        AcquisitionConditionAction.ACQUIRE, saved.value, false, false,
                        MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.onSurface,
                        MaterialTheme.colorScheme.onSurfaceVariant, UvirNumericFormat.SYSTEM,
                        { selected.value = it }, { matchCalls++ }, { actionCalls++ }, { show.value = true })
                }
                if (show.value) UvirAcquisitionConditionsDialog(saved.value,
                    MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.onSurface,
                    MaterialTheme.colorScheme.onSurfaceVariant,
                    onSave = { saved.value = it; show.value = false },
                    onDismissRequest = { show.value = false })
            }
        }
        val choiceLabels = listOf(R.string.conditional_any, R.string.conditional_all,
            R.string.conditional_none, R.string.conditional_start, R.string.conditional_stop,
            R.string.conditional_record).map { context.getString(it) }
        for (night in listOf(false, true)) {
            compose.runOnIdle { dark.value = night }
            compose.onNodeWithText(title).assertIsEnabled().performClick()
            compose.onNodeWithText(configure).assertIsEnabled().assertIsDisplayed()
            compose.onNodeWithText(warning).assertDoesNotExist()
            choiceLabels.forEach {
                compose.onNodeWithText(it).performScrollTo().assertIsNotEnabled().performClick()
            }
            compose.runOnIdle { assertTrue(selected.value); assertEquals(0, matchCalls); assertEquals(0, actionCalls) }
            compose.onNodeWithText(title).performScrollTo().performClick()
            compose.onNodeWithText(configure).assertDoesNotExist()
            choiceLabels.forEach { compose.onNodeWithText(it).assertDoesNotExist() }
        }
        compose.onNodeWithText(title).performClick()
        compose.onNodeWithText(configure).performScrollTo().performClick()
        compose.onNodeWithTag("acquisition_condition_check_UVC", useUnmergedTree = true).performClick()
        compose.onNodeWithText(save).performClick()
        compose.onNodeWithText(title).assertIsEnabled()
        compose.runOnIdle { assertTrue(selected.value); assertTrue(saved.value.any { it.enabled }) }
        choiceLabels.forEach { compose.onNodeWithText(it).performScrollTo().assertIsEnabled() }
        compose.onNodeWithText(context.getString(R.string.conditional_description)).assertDoesNotExist()
        compose.onNodeWithText(context.getString(thresholdAlertMetricLabelResource(ThresholdAlertMetric.UVC)),
            substring = true).assertDoesNotExist()
        listOf(R.string.conditional_when, R.string.conditional_any, R.string.conditional_all,
            R.string.conditional_none, R.string.conditional_action, R.string.conditional_start,
            R.string.conditional_stop, R.string.conditional_record).forEach {
            compose.onNodeWithText(context.getString(it)).performScrollTo().assertIsDisplayed()
        }
        val before = saved.value
        compose.onNodeWithText(configure).performScrollTo().performClick()
        compose.onNodeWithTag("acquisition_condition_check_UVC", useUnmergedTree = true).performClick()
        compose.onNodeWithText(cancel).performClick()
        compose.runOnIdle { assertEquals(before, saved.value) }
    }

    @Test fun runningSnapshotRemainsReadOnlyEvenIfTheSavedDraftIsDifferent() {
        var configure = ""; var waiting = ""; var title = ""
        var enabledChanges = 0
        compose.setContent {
            configure = LocalContext.current.getString(R.string.conditional_configure)
            title = LocalContext.current.getString(R.string.conditional_acquisition)
            waiting = LocalContext.current.getString(R.string.conditional_waiting)
            MaterialTheme {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    UvirConditionalAcquisitionCard(true, AcquisitionConditionMatch.ALL,
                        AcquisitionConditionAction.START, rules(), true, true,
                        MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.onSurface,
                        MaterialTheme.colorScheme.onSurfaceVariant, UvirNumericFormat.SYSTEM, { enabledChanges++ }, {}, {})
                }
            }
        }
        compose.onNodeWithText(configure).assertIsNotEnabled()
        compose.onNodeWithText(waiting).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(title).performScrollTo().assertIsEnabled().performClick()
        compose.onNodeWithText(configure).assertDoesNotExist()
        compose.onNodeWithText(waiting).assertDoesNotExist()
        compose.onNodeWithText(title).performClick()
        compose.onNodeWithText(configure).assertIsNotEnabled()
        compose.onNodeWithText(waiting).performScrollTo().assertIsDisplayed()
        assertEquals(0, enabledChanges)
    }

    @Test fun removingTheLastCriterionKeepsTheIslandOpenAndDisablesItsChoices() {
        val saved = mutableStateOf(rules())
        val selected = mutableStateOf(true)
        val match = mutableStateOf(AcquisitionConditionMatch.ANY)
        val action = mutableStateOf(AcquisitionConditionAction.ACQUIRE)
        val dark = mutableStateOf(false)
        compose.setContent {
            MaterialTheme(colorScheme = if (dark.value) darkColorScheme() else lightColorScheme()) {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    UvirConditionalAcquisitionCard(selected.value, match.value, action.value,
                        saved.value, false, false, MaterialTheme.colorScheme.surface,
                        MaterialTheme.colorScheme.onSurface, MaterialTheme.colorScheme.onSurfaceVariant,
                        UvirNumericFormat.SYSTEM, { selected.value = it }, { match.value = it },
                        { action.value = it })
                }
            }
        }
        val all = context.getString(R.string.conditional_all)
        val start = context.getString(R.string.conditional_start)
        val configure = context.getString(R.string.conditional_configure)
        for (night in listOf(false, true)) {
            compose.runOnIdle {
                dark.value = night
                saved.value = rules().map { it.copy(enabled = false) }
                match.value = AcquisitionConditionMatch.ANY
                action.value = AcquisitionConditionAction.ACQUIRE
            }
            compose.onNodeWithText(configure).performScrollTo().assertIsDisplayed().assertIsEnabled()
            compose.onNodeWithText(all).performScrollTo().assertIsNotEnabled().performClick()
            compose.onNodeWithText(start).performScrollTo().assertIsNotEnabled().performClick()
            compose.runOnIdle {
                assertTrue(selected.value)
                assertEquals(AcquisitionConditionMatch.ANY, match.value)
                assertEquals(AcquisitionConditionAction.ACQUIRE, action.value)
                saved.value = rules()
            }
            compose.onNodeWithText(all).performScrollTo().assertIsEnabled().performClick()
            compose.onNodeWithText(start).performScrollTo().assertIsEnabled().performClick()
            compose.runOnIdle {
                assertEquals(AcquisitionConditionMatch.ALL, match.value)
                assertEquals(AcquisitionConditionAction.START, action.value)
            }
        }
    }

    @Test fun shortWindowKeepsTheSaveActionOutsideTheScrollableEditor() {
        var save = ""
        compose.setContent {
            val original = LocalContext.current
            val config = Configuration(original.resources.configuration).apply {
                screenHeightDp = 320
                setLocale(Locale.GERMAN)
            }
            val translated = original.createConfigurationContext(config)
            CompositionLocalProvider(LocalContext provides translated,
                LocalConfiguration provides config,
                LocalResources provides translated.resources) {
                save = translated.getString(R.string.save)
                MaterialTheme {
                    UvirAcquisitionConditionsDialog(rules().map { it.copy(enabled = true) },
                        MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.onSurface,
                        MaterialTheme.colorScheme.onSurfaceVariant, onSave = {}, onDismissRequest = {})
                }
            }
        }
        compose.onNodeWithTag("acquisition_condition_threshold_BIO_HEV_OXIDATIVE", useUnmergedTree = true)
            .performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(save).assertIsDisplayed()
    }

    @Test fun longTranslatedLabelsAndLargerTextKeepTheEntireEditorReachable() {
        var save = ""
        compose.setContent {
            val original = LocalContext.current
            val config = Configuration(original.resources.configuration).apply {
                setLocale(Locale.GERMAN)
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or Configuration.UI_MODE_NIGHT_YES
            }
            val translated = original.createConfigurationContext(config)
            val density = LocalDensity.current
            CompositionLocalProvider(LocalContext provides translated,
                LocalConfiguration provides config,
                LocalResources provides translated.resources,
                LocalDensity provides Density(density.density, 1.5f)) {
                save = translated.getString(R.string.save)
                MaterialTheme(colorScheme = darkColorScheme()) {
                    UvirAcquisitionConditionsDialog(rules().map { it.copy(enabled = true) },
                        MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.onSurface,
                        MaterialTheme.colorScheme.onSurfaceVariant, onSave = {}, onDismissRequest = {})
                }
            }
        }
        compose.onNodeWithTag("acquisition_condition_threshold_BIO_HEV_OXIDATIVE", useUnmergedTree = true)
            .performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(save).assertIsDisplayed()
    }
}
