package me.mondiversi.uvir

import android.content.res.Configuration
import androidx.test.platform.app.InstrumentationRegistry
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

/** Checks packaged translations without opening the database or connecting to a sensor. */
class UvirAlertEmptyMessageTranslationTest {
    @Test
    fun emptyListMessageMentionsValueAlertsInEverySupportedLanguage() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val translations = mapOf(
            "en" to "No value alerts recorded.",
            "ar" to "لا توجد تنبيهات قيم مسجلة.",
            "de" to "Keine Wertalarme aufgezeichnet.",
            "el" to "Δεν έχουν καταγραφεί ειδοποιήσεις τιμών.",
            "es" to "No hay alertas de valores registradas.",
            "fa" to "هیچ هشدار مقداری ثبت نشده است.",
            "fr" to "Aucune alerte de valeurs enregistrée.",
            "hi" to "कोई मान अलर्ट रिकॉर्ड नहीं किया गया।",
            "it" to "Nessuna allerta valori registrata.",
            "he" to "לא נרשמו התראות ערך.",
            "ja" to "記録された値アラートはありません。",
            "ko-KR" to "기록된 값 경고가 없습니다.",
            "pt" to "Nenhum alerta de valor registrado.",
            "ru" to "Нет зарегистрированных оповещений о значениях.",
            "sw" to "Hakuna tahadhari za thamani zilizorekodiwa.",
            "tr" to "Kaydedilmiş değer uyarısı yok.",
            "zh-CN" to "没有已记录的数值警报。"
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
