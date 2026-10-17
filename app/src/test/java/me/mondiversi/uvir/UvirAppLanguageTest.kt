package me.mondiversi.uvir

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UvirAppLanguageTest {
    @Test fun newLanguagesRoundTripThroughPreferencesWithModernLanguageTags() {
        for ((language, tag) in mapOf(
            AppLanguage.POLISH to "pl", AppLanguage.DUTCH to "nl", AppLanguage.INDONESIAN to "id"
        )) {
            assertEquals(tag, language.storedValue)
            assertEquals(tag, language.languageTag)
            assertEquals(language, AppLanguage.fromStoredValue(language.storedValue))
        }
        assertEquals(20, AppLanguage.entries.count { it != AppLanguage.SYSTEM })
        assertEquals(AppLanguage.entries.size, AppLanguage.entries.map { it.storedValue }.toSet().size)
    }

    @Test fun systemLanguageRecognizesIndonesianIncludingItsLegacyAndroidCode() {
        for (code in listOf("id", "in")) {
            assertEquals(AppLanguage.INDONESIAN, resolvedUvirAppLanguage(AppLanguage.SYSTEM, Locale.forLanguageTag("$code-ID")))
        }
        assertEquals(AppLanguage.POLISH, resolvedUvirAppLanguage(AppLanguage.SYSTEM, Locale.forLanguageTag("pl-PL")))
        assertEquals(AppLanguage.DUTCH, resolvedUvirAppLanguage(AppLanguage.SYSTEM, Locale.forLanguageTag("nl-NL")))
        assertEquals(AppLanguage.HEBREW, resolvedUvirAppLanguage(AppLanguage.SYSTEM, Locale.forLanguageTag("iw-IL")))
    }

    @Test fun explicitLanguageIsNotReplacedByTheSystemLocale() {
        for (language in listOf(AppLanguage.POLISH, AppLanguage.DUTCH, AppLanguage.INDONESIAN)) {
            assertEquals(language, resolvedUvirAppLanguage(language, Locale.ITALIAN))
        }
        assertEquals(AppLanguage.ENGLISH, resolvedUvirAppLanguage(AppLanguage.SYSTEM, Locale.forLanguageTag("zz")))
        assertTrue(AppLanguage.fromStoredValue("unknown") == AppLanguage.SYSTEM)
    }
}
