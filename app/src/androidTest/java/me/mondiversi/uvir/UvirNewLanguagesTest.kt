package me.mondiversi.uvir

import android.content.res.Configuration
import android.content.res.Resources
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

/** Read-only localized fixtures: no preferences, user records or sensor commands. */
class UvirNewLanguagesTest {
    @get:Rule val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun resources(tag: String): Resources =
        context.createConfigurationContext(Configuration(context.resources.configuration).apply {
            setLocale(Locale.forLanguageTag(tag))
        }).resources

    @Test fun modernAndLegacyIndonesianCodesUseTheSameTranslatedCatalog() {
        for (tag in listOf("id", "id-ID", "in")) {
            val res = resources(tag)
            assertEquals("Pengaturan", res.getString(R.string.acquisition_parameters))
            assertEquals("Bahasa Indonesia", res.getString(R.string.language_indonesian))
            assertEquals("Buka pengaturan", res.getString(R.string.sensor_simulation_open_settings))
            for (count in listOf(0, 1, 2, 5, 21)) {
                assertEquals("$count pengukuran",
                    res.getQuantityString(R.plurals.session_chart_acquisition_count, count, count))
            }
        }
    }

    @Test fun polishAndDutchCountsSelectTheirCorrectPluralForms() {
        val polish = resources("pl-PL")
        for ((count, noun) in mapOf(0 to "pomiarów", 1 to "pomiar", 2 to "pomiary",
            5 to "pomiarów", 12 to "pomiarów", 21 to "pomiarów", 22 to "pomiary")) {
            assertEquals("$count $noun",
                polish.getQuantityString(R.plurals.session_chart_acquisition_count, count, count))
        }
        assertEquals("Zaimportowano 2 pliki ustawień.",
            polish.getQuantityString(R.plurals.settings_imported_count, 2, 2))
        val dutch = resources("nl-NL")
        for (count in listOf(0, 1, 2, 5, 21)) {
            val noun = if (count == 1) "meting" else "metingen"
            assertEquals("$count $noun",
                dutch.getQuantityString(R.plurals.session_chart_acquisition_count, count, count))
        }
    }

    @Test fun settingsDiagnosticsAndExportMessagesResolveInEachNewLanguage() {
        val keys = listOf(R.string.acquisition_parameters, R.string.sensor_info_title,
            R.string.debug_simulate_events_description, R.string.sensor_simulation_open_settings,
            R.string.session_sequence_filter_title, R.string.export_save_failed)
        val english = resources("en")
        for (tag in listOf("pl", "nl", "id")) {
            val res = resources(tag)
            for (key in keys) {
                val text = res.getString(key)
                assertFalse("Empty translation: $tag/$key", text.isBlank())
                assertFalse("English fallback: $tag/$key", text == english.getString(key))
            }
        }
    }

    @Test fun sensorInformationRendersAndScrollsInAllNewLanguagesAndBothThemes() {
        val tag = mutableStateOf("pl")
        val night = mutableStateOf(false)
        compose.setContent {
            val view = LocalView.current
            DisposableEffect(view) {
                val previous = view.keepScreenOn
                view.keepScreenOn = true
                onDispose { view.keepScreenOn = previous }
            }
            CompositionLocalProvider(LocalResources provides resources(tag.value)) {
                MaterialTheme(colorScheme = if (night.value) darkColorScheme() else lightColorScheme()) {
                    val colors = MaterialTheme.colorScheme
                    UvirSensorInfoScreen(SensorConnectionMode.USB,
                        UvirSensorRuntimeInfo(deviceId = "UVIR-LANGUAGE-TEST"),
                        colors.background, colors.onSurface, colors.onSurfaceVariant, colors.surface, {})
                }
            }
        }
        for (language in listOf("pl", "nl", "id")) {
            for (dark in listOf(false, true)) {
                compose.runOnIdle { tag.value = language; night.value = dark }
                val res = resources(language)
                for (key in listOf(R.string.sensor_info_general_information,
                    R.string.sensor_info_components, R.string.sensor_info_date_time)) {
                    compose.onNodeWithText(res.getString(key)).performScrollTo().assertIsDisplayed()
                }
            }
        }
    }
}
