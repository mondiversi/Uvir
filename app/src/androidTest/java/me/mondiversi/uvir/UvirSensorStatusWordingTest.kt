package me.mondiversi.uvir

import android.content.res.Configuration
import androidx.test.platform.app.InstrumentationRegistry
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Resource-only checks; no sensors, settings or user records are changed. */
class UvirSensorStatusWordingTest {
    @Test fun statusAndNoSelectionHaveDistinctLocalizedWordingInEveryLanguage() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val translations = mapOf(
            "en" to listOf("Not connected", "Connecting…", "Search in progress…", "No sensor"),
            "ar" to listOf("غير متصل", "جارٍ الاتصال…", "جارٍ البحث…", "لا يوجد مستشعر"),
            "de" to listOf("Nicht verbunden", "Verbinden…", "Suche läuft…", "Kein Sensor"),
            "el" to listOf("Χωρίς σύνδεση", "Σύνδεση…", "Αναζήτηση σε εξέλιξη…", "Κανένας αισθητήρας"),
            "es" to listOf("Sin conexión", "Conectando…", "Búsqueda en curso…", "Ningún sensor"),
            "fa" to listOf("متصل نیست", "در حال اتصال…", "در حال جستجو…", "بدون حسگر"),
            "fr" to listOf("Non connecté", "Connexion…", "Recherche en cours…", "Aucun capteur"),
            "hi" to listOf("कनेक्ट नहीं है", "कनेक्ट हो रहा है…", "खोज जारी है…", "कोई सेंसर नहीं"),
            "it" to listOf("Non connesso", "Connessione…", "Ricerca in corso…", "Nessun sensore"),
            "he" to listOf("לא מחובר", "מתחבר…", "חיפוש מתבצע…", "אין חיישן"),
            "ja" to listOf("未接続", "接続中…", "検索中…", "センサーなし"),
            "ko-KR" to listOf("연결 안 됨", "연결 중…", "검색 중…", "센서 없음"),
            "pt" to listOf("Não conectado", "Conectando…", "Pesquisa em curso…", "Nenhum sensor"),
            "ru" to listOf("Не подключён", "Подключение…", "Поиск выполняется…", "Нет датчика"),
            "sw" to listOf("Haijaunganishwa", "Inaunganisha…", "Utafutaji unaendelea…", "Hakuna kihisi"),
            "tr" to listOf("Bağlı değil", "Bağlanıyor…", "Arama devam ediyor…", "Sensör yok"),
            "zh-CN" to listOf("未连接", "正在连接…", "正在搜索…", "无传感器")
        )
        val keys = listOf(R.string.sensor_status_no_sensor, R.string.sensor_info_connecting,
            R.string.sensor_info_searching, R.string.sensor_no_selection)
        for ((language, expected) in translations) {
            val configuration = Configuration(context.resources.configuration).apply {
                setLocale(Locale.forLanguageTag(language))
            }
            val resources = context.createConfigurationContext(configuration).resources
            val actual = keys.map { resources.getString(it) }
            assertEquals("Status translations: $language", expected, actual)
            assertNotEquals("Connection state must differ from no selection: $language", actual[0], actual[3])
            assertTrue("Connecting ellipsis: $language", actual[1].endsWith("…"))
            assertTrue("Searching ellipsis: $language", actual[2].endsWith("…"))
            assertTrue("No selection must not be an em dash: $language", actual[3] != "—")
        }
    }
}
