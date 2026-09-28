package me.mondiversi.uvir

import android.content.res.Configuration
import androidx.test.platform.app.InstrumentationRegistry
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

/** Checks packaged translations without opening the database or connecting to a sensor. */
class UvirAlertEmptyMessageTranslationTest {
    @Test
    fun emptyListMessageMentionsAlertsInEverySupportedLanguage() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val translations = mapOf(
            "en" to "No alerts recorded.",
            "ar" to "لا توجد تنبيهات مسجلة.",
            "de" to "Keine Alarme aufgezeichnet.",
            "el" to "Δεν έχουν καταγραφεί ειδοποιήσεις.",
            "es" to "No hay alertas registradas.",
            "fa" to "هیچ هشداری ثبت نشده است.",
            "fr" to "Aucune alerte enregistrée.",
            "hi" to "कोई अलर्ट रिकॉर्ड नहीं किया गया।",
            "it" to "Nessuna allerta registrata.",
            "he" to "לא נרשמו התראות.",
            "ja" to "記録されたアラートはありません。",
            "ko-KR" to "기록된 알림이 없습니다.",
            "pt" to "Nenhum alerta registrado.",
            "ru" to "Нет зарегистрированных оповещений.",
            "sw" to "Hakuna tahadhari zilizorekodiwa.",
            "tr" to "Kaydedilmiş uyarı yok.",
            "zh-CN" to "没有已记录的警报。"
        )
        for ((language, expected) in translations) {
            val configuration = Configuration(context.resources.configuration).apply {
                setLocale(Locale.forLanguageTag(language))
            }
            val translatedContext = context.createConfigurationContext(configuration)
            assertEquals("Empty alert list message in $language", expected,
                translatedContext.getString(R.string.threshold_alert_log_empty))
        }
    }
}
