package me.mondiversi.uvir

import android.content.Context
import android.content.res.Configuration
import androidx.test.platform.app.InstrumentationRegistry
import java.util.Locale
import org.junit.Assert.*
import org.junit.Test

/** Resource checks only: no activity, preferences, database or sensor commands. */
class UvirAlertStopWordingTest {
    private fun localized(tag: String): Context {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val configuration = Configuration(base.resources.configuration).apply {
            setLocale(Locale.forLanguageTag(tag))
        }
        return base.createConfigurationContext(configuration)
    }

    @Test fun alertConfirmationReusesTheAutomaticAcquisitionStopLabelInEveryLanguage() {
        for (tag in listOf("en", "it", "ar", "de", "el", "es", "fa", "fr", "hi", "he",
            "ja", "ko", "pt", "ru", "sw", "tr", "zh-CN")) {
            val context = localized(tag)
            assertEquals("Both stop confirmations must use the same label: $tag",
                context.getString(R.string.stop), context.getString(R.string.stop_all_value_alerts_action))
            assertTrue(context.getString(R.string.stop_all_value_alerts_title).isNotBlank())
            assertTrue(context.getString(R.string.stop_all_value_alerts_message).isNotBlank())
        }
    }

    @Test fun italianUsesASingularSessionAndKeepsTheStopActionAndRetentionNotice() {
        val italian = localized("it")
        assertEquals("Fermare la sessione allerta valori?",
            italian.getString(R.string.stop_all_value_alerts_title))
        assertEquals("FERMA", italian.getString(R.string.stop_all_value_alerts_action))
        val message = italian.getString(R.string.stop_all_value_alerts_message)
        assertTrue(message.contains("La sessione allerta valori in corso verrà terminata."))
        assertTrue(message.contains("verranno conservate"))
        assertFalse(message.contains("sessione allerte valori"))
        assertEquals("Avviare la sessione allerta valori?",
            italian.getString(R.string.start_value_alert_session_title))
        val english = localized("en")
        assertEquals("Stop value alert session?", english.getString(R.string.stop_all_value_alerts_title))
        assertEquals("STOP", english.getString(R.string.stop_all_value_alerts_action))
    }
}
