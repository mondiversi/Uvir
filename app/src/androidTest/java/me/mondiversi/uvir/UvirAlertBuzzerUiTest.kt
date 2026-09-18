package me.mondiversi.uvir

import android.content.res.Configuration
import java.util.Locale
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Composables only: no live sensor commands, app preferences or recorded data. */
class UvirAlertBuzzerUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun everySignalIsReachableInDayAndNightAndDisabledTestDoesNotSendAnything() {
        val dark = mutableStateOf(false)
        var titles = emptyList<String>()
        var testText = ""
        var calls = 0
        compose.setContent {
            val resources = LocalResources.current
            titles = statusBuzzerSignals.map { resources.getString(it.title) }
            testText = resources.getString(R.string.sensor_buzzer_test)
            MaterialTheme(colorScheme = if (dark.value) darkColorScheme() else lightColorScheme()) {
                UvirStatusBuzzerInfoDialog(
                    primaryText = if (dark.value) Color.White else Color.Black,
                    secondaryText = if (dark.value) Color.LightGray else Color.DarkGray,
                    cardColor = if (dark.value) Color(0xff202026) else Color.White,
                    testEnabled = false,
                    onTestBuzzer = { calls++; true },
                    onDismissRequest = {}
                )
            }
        }
        for (night in listOf(false, true)) {
            compose.runOnIdle { dark.value = night }
            titles.forEach { compose.onNodeWithText(it).performScrollTo().assertIsDisplayed() }
            compose.onNodeWithText(testText).assertIsNotEnabled()
        }
        assertEquals(0, calls)
    }

    @Test fun aRejectedSensorTestNeverHighlightsOrLocksTheLegend() {
        var testText = ""
        var calls = 0
        compose.setContent {
            testText = LocalResources.current.getString(R.string.sensor_buzzer_test)
            MaterialTheme {
                UvirStatusBuzzerInfoDialog(Color.Black, Color.DarkGray, Color.White,
                    testEnabled = true, onTestBuzzer = { calls++; false }, onDismissRequest = {})
            }
        }
        compose.onNodeWithText(testText).performClick()
        compose.waitForIdle()
        compose.onNodeWithText(testText).assertIsEnabled()
        assertEquals(1, calls)
    }

    @Test fun longerBuzzerTitlesRemainReachableWithLargerText() {
        var titles = emptyList<String>()
        var testAction = ""
        compose.setContent {
            val original = LocalContext.current
            val config = Configuration(LocalConfiguration.current).apply {
                setLocale(Locale.FRENCH)
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or Configuration.UI_MODE_NIGHT_YES
            }
            val translated = original.createConfigurationContext(config)
            val density = LocalDensity.current
            CompositionLocalProvider(LocalContext provides translated,
                LocalConfiguration provides config,
                LocalResources provides translated.resources,
                LocalDensity provides Density(density.density, 1.5f)) {
                titles = statusBuzzerSignals.map { translated.getString(it.title) }
                testAction = translated.getString(R.string.sensor_buzzer_test)
                MaterialTheme(colorScheme = darkColorScheme()) {
                    UvirStatusBuzzerInfoDialog(Color.White, Color.LightGray, Color(0xff202026),
                        testEnabled = false, onTestBuzzer = { error("No sensor commands in this test") },
                        onDismissRequest = {})
                }
            }
        }
        titles.forEach { compose.onNodeWithText(it).performScrollTo().assertIsDisplayed() }
        compose.onNodeWithText(testAction).assertIsDisplayed().assertIsNotEnabled()
    }

    @Test fun alertMetricDotsUseTheSlightlyLargerSharedSizeInBothThemes() {
        val dark = mutableStateOf(false)
        compose.setContent {
            MaterialTheme(colorScheme = if (dark.value) darkColorScheme() else lightColorScheme()) {
                ThresholdAlertMetricLabel(ThresholdAlertMetric.UVA,
                    if (dark.value) Color.White else Color.Black, Color.Gray, enabled = true)
            }
        }
        for (night in listOf(false, true)) {
            compose.runOnIdle { dark.value = night }
            compose.onNodeWithTag("threshold_metric_dot_UVA")
                .assertWidthIsEqualTo(10.dp).assertHeightIsEqualTo(10.dp)
        }
    }
}
