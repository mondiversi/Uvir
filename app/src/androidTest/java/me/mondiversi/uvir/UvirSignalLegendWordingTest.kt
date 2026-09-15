package me.mondiversi.uvir

import android.content.res.Configuration
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import java.util.Locale
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Only isolated legend composables and localized resources; never sends a sensor command. */
class UvirSignalLegendWordingTest {
    @get:Rule val compose = createComposeRule()

    @Composable private fun Localized(language: String, night: Boolean, content: @Composable () -> Unit) {
        val original = LocalContext.current
        val config = Configuration(original.resources.configuration).apply {
            setLocale(Locale.forLanguageTag(language))
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                (if (night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO)
        }
        val context = original.createConfigurationContext(config)
        CompositionLocalProvider(LocalContext provides context, LocalConfiguration provides config,
            LocalResources provides context.resources) {
            MaterialTheme(colorScheme = if (night) darkColorScheme() else lightColorScheme()) {
                content()
            }
        }
    }

    @Test fun buzzerShowsTheSoundAboveItsMeaningInBothThemes() {
        val dark = mutableStateOf(false)
        val pairs = listOf(
            "Two ascending tones" to "Connection.",
            "Two descending tones" to "Disconnection.",
            "Three ascending tones" to "Start of an automatic acquisition or value-alert session.",
            "Three descending tones" to "End of an automatic acquisition or value-alert session.",
            "One short beep" to "Manual or automatic acquisition.",
            "Three short beeps in a row" to "Value-alert recording."
        )
        compose.setContent {
            Localized("en", dark.value) {
                UvirStatusBuzzerInfoDialog(MaterialTheme.colorScheme.onSurface,
                    MaterialTheme.colorScheme.onSurfaceVariant, MaterialTheme.colorScheme.surface,
                    false, { error("No hardware tests") }, {})
            }
        }
        for (night in listOf(false, true)) {
            compose.runOnIdle { dark.value = night }
            pairs.forEach { (sound, meaning) ->
                compose.onNodeWithText(sound).performScrollTo().assertIsDisplayed()
                compose.onNodeWithText(meaning).performScrollTo().assertIsDisplayed()
                val soundBounds = compose.onNodeWithText(sound).fetchSemanticsNode().boundsInRoot
                val meaningBounds = compose.onNodeWithText(meaning).fetchSemanticsNode().boundsInRoot
                assertTrue(soundBounds.top < meaningBounds.top)
                assertEquals(soundBounds.left, meaningBounds.left, 0.5f)
            }
        }
    }

    @Test fun ledLegendUsesConciseDescriptionsAndKeepsPendingDataSignals() {
        val dark = mutableStateOf(false)
        var pendingDescriptions = emptyList<String>()
        compose.setContent {
            Localized("it", dark.value) {
                pendingDescriptions = listOf(R.string.sensor_led_signal_pending_connected_description,
                    R.string.sensor_led_signal_pending_disconnected_description)
                    .map { LocalContext.current.getString(it) }
                UvirStatusLedInfoDialog(MaterialTheme.colorScheme.onSurface,
                    MaterialTheme.colorScheme.onSurfaceVariant, MaterialTheme.colorScheme.surface,
                    false, { error("No hardware tests") }, {})
            }
        }
        for (night in listOf(false, true)) {
            compose.runOnIdle { dark.value = night }
            (listOf("LED blu · Fisso", "Sensore alimentato ma non connesso.", "Sensore connesso.",
                "Connessione in corso.", "LED blu · 3 lampeggi", "Acquisizione/allerta valori.") +
                pendingDescriptions).forEach {
                compose.onNodeWithText(it).performScrollTo().assertIsDisplayed()
            }
            compose.onNodeWithText("spegnimenti", substring = true).assertDoesNotExist()
        }
    }
}
