package me.mondiversi.uvir

import android.content.res.Configuration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.platform.app.InstrumentationRegistry
import java.util.Locale
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Isolated legend: no saved preferences, database operations or test commands. */
class UvirActivityLegendTranslationTest {
    @get:Rule val compose = createComposeRule()

    @Test fun activityAndSavedDescriptionsFitTwoLinesInAllLanguagesAndThemes() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val localeResources = mutableStateOf(context.resources)
        val dark = mutableStateOf(false)
        val overflows = mutableListOf<String>()
        compose.setContent {
            CompositionLocalProvider(LocalResources provides localeResources.value) {
                MaterialTheme(colorScheme = if (dark.value) darkColorScheme() else lightColorScheme()) {
                    val colors = MaterialTheme.colorScheme
                    UvirStatusLedInfoDialog(colors.onSurface, colors.onSurfaceVariant, colors.surface,
                        testEnabled = false, onTestLed = { false }, onDismissRequest = {})
                }
            }
        }
        for (language in listOf("en", "it", "ar", "de", "el", "es", "fa", "fr", "hi", "he", "ja", "ko-KR", "pt", "ru", "sw", "tr", "zh-CN")) {
            val config = Configuration(context.resources.configuration).apply {
                setLocale(Locale.forLanguageTag(language))
            }
            val localized = context.createConfigurationContext(config).resources
            for (night in listOf(false, true)) {
                compose.runOnIdle { localeResources.value = localized; dark.value = night }
                for (key in listOf(R.string.sensor_led_signal_operation_active_description, R.string.sensor_led_signal_recorded_description, R.string.sensor_led_signal_time_unavailable_description)) {
                    val layouts = mutableListOf<TextLayoutResult>()
                    compose.onNodeWithText(localized.getString(key)).performSemanticsAction(SemanticsActions.GetTextLayoutResult) {
                        it(layouts)
                    }
                    assertTrue("Missing layout: $language", layouts.isNotEmpty())
                    // Android fallback fonts can report an exceeded height at
                    // fractional pixel boundaries even with every glyph shown.
                    // Verify the last laid-out character instead of that flag.
                    for (layout in layouts) {
                        val end = layout.getLineEnd(layout.lineCount - 1, visibleEnd = false)
                        if (end < layout.layoutInput.text.length || layout.lineCount > 2) {
                            overflows += "$language, night=$night: ${localized.getString(key)} (end=$end/${layout.layoutInput.text.length}, ${layout.lineCount} lines)"
                        }
                    }
                }
            }
        }
        assertTrue("Legend overflow: ${overflows.joinToString("; ")}", overflows.isEmpty())
    }
}
