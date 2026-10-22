package me.mondiversi.uvir

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class UvirNumericFormattingTest {
    @Test fun editableNumbersFollowTheChosenSeparatorWithoutGroupingOrRounding() {
        assertEquals("1234,567890", formatUvirDecimalInputText("1234.567890", UvirNumericFormat.EUROPEAN))
        assertEquals("-0,", formatUvirDecimalInputText("-0.", UvirNumericFormat.EUROPEAN))
        assertEquals("", formatUvirDecimalInputText("", UvirNumericFormat.EUROPEAN))
        assertEquals("1.0", formatUvirDecimalInputText("1.0", UvirNumericFormat.INTERNATIONAL))
        assertEquals("1.0", formatUvirDecimalInputText("1.0", UvirNumericFormat.AMERICAN))
        assertEquals("1,0", formatUvirDecimalInputText("1.0", UvirNumericFormat.EUROPEAN))
    }

    @Test fun systemEditorsFollowLocaleAndPreserveEveryCursorOffset() {
        val previous = Locale.getDefault()
        try {
            for (locale in listOf(Locale.ITALY, Locale.US, Locale.forLanguageTag("ar-EG"))) {
                Locale.setDefault(locale)
                for (canonical in listOf("", "-", "0.", "1.25", "-1234.5678")) {
                    val displayed = formatUvirDecimalInputText(canonical, UvirNumericFormat.SYSTEM)
                    assertEquals(canonical.length, displayed.length)
                    assertEquals(canonical, filterUvirNumericInput(displayed, UvirNumericFormat.SYSTEM, true))
                }
            }
        } finally { Locale.setDefault(previous) }
    }

    @Test fun bothKeyboardSeparatorsAndLocalizedDigitsKeepCanonicalValues() {
        for (format in UvirNumericFormat.entries) {
            assertEquals("1.25", filterUvirNumericInput("1,25", format, true))
            assertEquals("1.25", filterUvirNumericInput("1.25", format, true))
            assertEquals("-12.5", filterUvirNumericInput("-١٢.٥", format, true))
            assertEquals("-125", filterUvirNumericInput("-١٢٥", format, false))
        }
    }

    @Test fun opticalSummaryUsesSelectedFormattingWithoutFloatingPointNoise() {
        val value = OpticalWindowSettings(true).withCommonFactor(1.111f)
        assertEquals("UVC ×1,111", opticalWindowSummary(value, UvirNumericFormat.EUROPEAN).substringBefore(" · "))
        assertEquals("UVC ×1.111", opticalWindowSummary(value, UvirNumericFormat.INTERNATIONAL).substringBefore(" · "))
    }

    @Test
    fun missingPreferenceUsesSystemDefault() {
        assertEquals(
            UvirNumericFormat.SYSTEM,
            UvirNumericFormat.fromStoredValue(null)
        )
    }

    @Test
    fun systemFormatFollowsDeviceLocale() {
        val originalLocale = Locale.getDefault()

        try {
            Locale.setDefault(Locale.ITALY)
            assertEquals(
                "1.000,23",
                formatUvirNumber(
                    value = 1000.23,
                    fractionDigits = 2,
                    format = UvirNumericFormat.SYSTEM
                )
            )

            Locale.setDefault(Locale.US)
            assertEquals(
                "1,000.23",
                formatUvirNumber(
                    value = 1000.23,
                    fractionDigits = 2,
                    format = UvirNumericFormat.SYSTEM
                )
            )
        } finally {
            Locale.setDefault(originalLocale)
        }
    }

    @Test
    fun internationalFormatUsesNarrowSpaceGroupingAndDotDecimal() {
        assertEquals(
            "1\u202F000.23",
            formatUvirNumber(
                value = 1000.23,
                fractionDigits = 2,
                format = UvirNumericFormat.INTERNATIONAL
            )
        )
    }

    @Test
    fun europeanFormatUsesDotGroupingAndCommaDecimal() {
        assertEquals(
            "1.000,23",
            formatUvirNumber(
                value = 1000.23,
                fractionDigits = 2,
                format = UvirNumericFormat.EUROPEAN
            )
        )
    }

    @Test
    fun americanFormatUsesCommaGroupingAndDotDecimal() {
        assertEquals(
            "1,000.23",
            formatUvirNumber(
                value = 1000.23,
                fractionDigits = 2,
                format = UvirNumericFormat.AMERICAN
            )
        )
    }

    @Test
    fun csvExportKeepsUsefulPrecisionWithoutGrouping() {
        assertEquals(
            "13000.125",
            formatUvirExportNumber(
                value = 13000.125,
                format = UvirNumericFormat.INTERNATIONAL
            )
        )
    }

    @Test
    fun europeanCsvExportAlsoOmitsGrouping() {
        assertEquals(
            "13000,125",
            formatUvirExportNumber(
                value = 13000.125,
                format = UvirNumericFormat.EUROPEAN
            )
        )
    }
}
