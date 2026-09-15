package me.mondiversi.uvir

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class UvirNumericFormattingTest {
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
    fun internationalFormatUsesCommaGroupingAndDotDecimal() {
        assertEquals(
            "1,000.23",
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
    fun exportKeepsUsefulPrecision() {
        assertEquals(
            "13,000.125",
            formatUvirExportNumber(
                value = 13000.125,
                format = UvirNumericFormat.INTERNATIONAL
            )
        )
    }
}
