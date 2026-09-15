package me.mondiversi.uvir

import android.content.res.Configuration
import android.os.LocaleList
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class UvirConditionalActionDescriptionsTest {
    @get:Rule val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val descriptions = listOf(R.string.conditional_start_description,
        R.string.conditional_stop_description, R.string.conditional_record_description,
        R.string.conditional_limits_description)
    private val rules = listOf(ThresholdAlertRule(ThresholdAlertMetric.UVA, true,
        ThresholdAlertDirection.BELOW, 3f))

    @Test fun explanationsRemainScrollableWithLargeTextInBothThemes() {
        val dark = mutableStateOf(false)
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.5f)) {
                MaterialTheme(colorScheme = if (dark.value) darkColorScheme() else lightColorScheme()) {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                        UvirConditionalAcquisitionCard(true, AcquisitionConditionMatch.ANY,
                            AcquisitionConditionAction.ACQUIRE, rules, false, false,
                            MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.onSurface,
                            MaterialTheme.colorScheme.onSurfaceVariant, UvirNumericFormat.SYSTEM, {}, {}, {})
                    }
                }
            }
        }
        for (night in listOf(false, true)) {
            compose.runOnIdle { dark.value = night }
            descriptions.forEach { compose.onNodeWithText(context.getString(it))
                .performScrollTo().assertIsDisplayed() }
        }
    }

    @Test fun onlyConditionAndActionHeadingsAreBold() {
        compose.setContent {
            MaterialTheme {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    UvirConditionalAcquisitionCard(true, AcquisitionConditionMatch.ANY,
                        AcquisitionConditionAction.ACQUIRE, rules, false, false,
                        MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.onSurface,
                        MaterialTheme.colorScheme.onSurfaceVariant, UvirNumericFormat.SYSTEM, {}, {}, {})
                }
            }
        }
        fun weight(id: Int): FontWeight? {
            val results = mutableListOf<TextLayoutResult>()
            compose.onNodeWithText(context.getString(id)).performScrollTo()
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { action -> action(results) }
            assertTrue(results.isNotEmpty())
            return results.first().layoutInput.style.fontWeight
        }
        listOf(R.string.conditional_when, R.string.conditional_action).forEach {
            assertEquals(FontWeight.Bold, weight(it))
        }
        (descriptions + listOf(R.string.conditional_any, R.string.conditional_all,
            R.string.conditional_none, R.string.conditional_start, R.string.conditional_stop,
            R.string.conditional_record)).forEach { assertNotEquals(FontWeight.Bold, weight(it)) }
    }

    @Test fun runningSessionRetainsItsSelectedActionExplanation() {
        compose.setContent {
            MaterialTheme {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    UvirConditionalAcquisitionCard(true, AcquisitionConditionMatch.ANY,
                        AcquisitionConditionAction.START, rules, true, true,
                        MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.onSurface,
                        MaterialTheme.colorScheme.onSurfaceVariant, UvirNumericFormat.SYSTEM, {}, {}, {})
                }
            }
        }
        compose.onNodeWithText(context.getString(R.string.conditional_start_description))
            .performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.conditional_limits_description))
            .performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.conditional_configure)).assertIsNotEnabled()
    }

    @Test fun everySupportedLanguageContainsTheFourExplanations() {
        for (tag in listOf("en","it","es","fr","pt","de","ar","he","fa","ru","tr","el","hi","ja","zh-CN","ko-KR","sw")) {
            val configuration = Configuration(context.resources.configuration)
            configuration.setLocales(LocaleList.forLanguageTags(tag))
            val localized = context.createConfigurationContext(configuration)
            descriptions.forEach { assertTrue("$tag: $it", localized.getString(it).isNotBlank()) }
        }
    }
}
