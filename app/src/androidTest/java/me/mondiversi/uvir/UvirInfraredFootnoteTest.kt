package me.mondiversi.uvir

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Isolated live island: no sensor connection, commands or app database. */
class UvirInfraredFootnoteTest {
    @get:Rule val compose = createComposeRule()

    @Test fun technicalNotesAndChannelPeaksAreAvailableInInfoInEveryLanguage() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        for (language in listOf("en", "it", "ar", "de", "el", "es", "fa", "fr", "hi", "he", "ja", "ko-KR", "pt", "ru", "sw", "pl", "nl", "id", "tr", "zh-CN")) {
            val configuration = Configuration(context.resources.configuration)
            configuration.setLocale(Locale.forLanguageTag(language))
            val translated = context.createConfigurationContext(configuration)
            val rows = uvirSensorSpectralInfoRows(translated.resources)
            assertEquals(3, rows.size)
            org.junit.Assert.assertFalse("No conventional passbands remain: $language",
                rows[1].second.contains("≈"))
            org.junit.Assert.assertTrue(rows[1].second.contains(
                translated.getString(R.string.spectral_channel_peaks, "405 / 425")))
            org.junit.Assert.assertTrue(rows[2].second.contains(
                translated.getString(R.string.measured_infrared_bands_note)))
        }
    }

    @Test fun compactInfraredChannelsHaveNoNewTechnicalNotesInEitherThemeOrDirection() {
        val dark = mutableStateOf(false)
        val direction = mutableStateOf(LayoutDirection.Ltr)
        val chart = mutableStateOf(false)
        val expanded = mutableStateOf(true)
        val sample = SensorSample(f8 = 4.0, nir = 6.0)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val title = context.getString(R.string.far_red_nir)
        val footnote = context.getString(R.string.measured_infrared_bands_note)
        val farRedPeak = UvirSensorBands.FAR_RED_CHANNELS
        val nirPeak = UvirSensorBands.NIR_CHANNEL
        compose.setContent {
            val view = LocalView.current
            DisposableEffect(view) {
                val previous = view.keepScreenOn
                view.keepScreenOn = true
                onDispose { view.keepScreenOn = previous }
            }
            CompositionLocalProvider(LocalLayoutDirection provides direction.value) {
                MaterialTheme(colorScheme = if (dark.value) darkColorScheme() else lightColorScheme()) {
                    val colors = MaterialTheme.colorScheme
                    Box(Modifier.width(280.dp)) {
                        SensorGroupContent(
                            group = SensorGroup.NIR,
                            expanded = expanded.value,
                            onToggle = { expanded.value = !expanded.value },
                            showChart = chart.value,
                            liveHistory = listOf(LiveSamplePoint(5_000L, sample), LiveSamplePoint(6_000L, sample)),
                            sample = sample,
                            uvTotal = 0.0,
                            visibleTotal = 0.0,
                            nirTotal = 10.0,
                            hev = 0.0,
                            cardColor = colors.surfaceContainer,
                            primaryText = colors.onSurface,
                            secondaryText = colors.onSurfaceVariant,
                            trackColor = colors.surfaceVariant
                        )
                    }
                }
            }
        }
        for (darkTheme in listOf(false, true)) {
            for (layoutDirection in listOf(LayoutDirection.Ltr, LayoutDirection.Rtl)) {
                for (showChart in listOf(false, true)) {
                    compose.runOnIdle {
                        dark.value = darkTheme
                        direction.value = layoutDirection
                        chart.value = showChart
                    }
                    val farRedLabel = if (showChart) {
                        context.getString(R.string.session_chart_series_far_red)
                    } else {
                        "Far-red"
                    }
                    compose.onNodeWithText(farRedLabel).assertIsDisplayed()
                    compose.onNodeWithText("NIR").assertIsDisplayed()
                    compose.onNodeWithText(footnote).assertDoesNotExist()
                    compose.onNodeWithText(context.getString(R.string.spectral_channel_peaks,
                        "690 / 745 / 855")).assertDoesNotExist()
                    compose.onNodeWithText(context.getString(R.string.measured_channel_sum)).assertDoesNotExist()
                    compose.onNodeWithText("Far-red²").assertDoesNotExist()
                    compose.onNodeWithText("NIR²").assertDoesNotExist()
                    compose.onNodeWithText("Far-red¹").assertDoesNotExist()
                    compose.onNodeWithText("NIR¹").assertDoesNotExist()
                    if (showChart) {
                        compose.onNodeWithText(farRedPeak).assertDoesNotExist()
                        compose.onNodeWithText(nirPeak).assertDoesNotExist()
                    } else {
                        compose.onNodeWithText(farRedPeak).assertIsDisplayed()
                        compose.onNodeWithText(nirPeak).assertIsDisplayed()
                    }
                    compose.onNode(hasText(title) and hasClickAction()).performClick()
                    compose.onNodeWithText(footnote).assertDoesNotExist()
                    compose.onNodeWithText(farRedLabel).assertDoesNotExist()
                    compose.onNodeWithText("NIR").assertDoesNotExist()
                    compose.onNodeWithText(farRedPeak).assertDoesNotExist()
                    compose.onNodeWithText(nirPeak).assertDoesNotExist()
                    compose.onNode(hasText(title) and hasClickAction()).performClick()
                    compose.onNodeWithText(footnote).assertDoesNotExist()
                }
            }
        }
    }
}
