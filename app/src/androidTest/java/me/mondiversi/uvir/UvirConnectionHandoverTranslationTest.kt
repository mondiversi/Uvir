package me.mondiversi.uvir

import android.content.res.Configuration
import androidx.test.platform.app.InstrumentationRegistry
import java.util.Locale
import org.junit.Assert.*
import org.junit.Test

/** Resource-only checks; never sends commands or modifies user preferences. */
class UvirConnectionHandoverTranslationTest {
    @Test fun failureMessageIsTranslatedInEverySupportedLanguage() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        fun text(language: String): String = context.createConfigurationContext(
            Configuration(context.resources.configuration).apply { setLocale(Locale.forLanguageTag(language)) }
        ).getString(R.string.sensor_connection_switch_failed)
        val english = text("en")
        for (language in listOf("it", "ar", "de", "el", "es", "fa", "fr",
            "hi", "he", "ja", "ko-KR", "pt", "ru", "sw", "tr", "zh-CN")) {
            val translated = text(language)
            assertTrue(language, translated.isNotBlank())
            assertNotEquals("Missing translation: $language", english, translated)
        }
        assertEquals("Impossibile confermare il cambio di connessione. Resta selezionata la modalità precedente.", text("it"))
    }
}
