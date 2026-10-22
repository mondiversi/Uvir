package me.mondiversi.uvir

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class UvirActionLabelsTest {
    @Test fun actionCaseUsesTheSelectedLanguageNotTheComputerLanguage() {
        assertEquals("AVVIA MISURA", formatUvirActionLabel("Avvia misura", Locale.ITALIAN))
        assertEquals("IMPOSTA OFFSET", formatUvirActionLabel("Imposta offset", Locale.ITALIAN))
        assertEquals("ÖLÇÜMÜ BAŞLAT", formatUvirActionLabel("Ölçümü başlat", Locale.forLanguageTag("tr")))
        assertEquals("İÇE AKTAR", formatUvirActionLabel("İçe aktar", Locale.forLanguageTag("tr")))
        assertEquals("CONFIGURA SOGLIE (3)", formatUvirActionLabel("Configura soglie (3)", Locale.ITALIAN))
    }

    @Test fun languagesWithoutLetterCaseAndAlreadyUppercaseCommandsRemainUnchanged() {
        for (text in listOf("开始测量", "測定を開始", "بدء القياس", "मापन शुरू करें", "ANNULLA")) {
            assertEquals(text, formatUvirActionLabel(text, Locale.ROOT))
        }
    }
}
