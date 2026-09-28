package me.mondiversi.uvir

import android.content.res.Configuration
import androidx.test.platform.app.InstrumentationRegistry
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Resource-only verification: does not connect sensors or change app data. */
class UvirSensorConnectionAnnouncementTranslationTest {
    @Test fun bothAnnouncementsIncludeTheNameInEveryAppLanguage() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        for (language in listOf("en", "it", "ar", "de", "el", "es", "fa", "fr",
            "hi", "he", "ja", "ko-KR", "pt", "ru", "sw", "tr", "zh-CN")) {
            val configuration = Configuration(context.resources.configuration).apply {
                setLocale(Locale.forLanguageTag(language))
            }
            val resources = context.createConfigurationContext(configuration).resources
            for (key in listOf(R.string.sensor_connection_toast_connected,
                R.string.sensor_connection_toast_disconnected)) {
                val template = resources.getString(key)
                assertTrue("Missing name placeholder: $language", template.contains("%1\$s"))
                assertEquals("Duplicate name placeholder: $language", 1,
                    Regex("%1\\\$s").findAll(template).count())
                val message = resources.getString(key, "Portatile")
                assertTrue("Missing name: $language", message.contains("Portatile"))
                assertTrue("Unresolved placeholder: $language", !message.contains("%1\$s"))
            }
        }
    }

    @Test fun italianMessagesUseTheRequestedWordingAndSupportHardwareIds() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val configuration = Configuration(context.resources.configuration).apply {
            setLocale(Locale.ITALIAN)
        }
        val resources = context.createConfigurationContext(configuration).resources
        assertEquals("Portatile collegato",
            resources.getString(R.string.sensor_connection_toast_connected, "Portatile"))
        assertEquals("Portatile scollegato",
            resources.getString(R.string.sensor_connection_toast_disconnected, "Portatile"))
        assertEquals("UVIR-1234 collegato",
            resources.getString(R.string.sensor_connection_toast_connected, "UVIR-1234"))
    }
}
