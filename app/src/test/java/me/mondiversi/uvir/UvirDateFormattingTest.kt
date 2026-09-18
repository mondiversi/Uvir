package me.mondiversi.uvir

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.util.Locale
import java.util.TimeZone

class UvirDateFormattingTest {
    @Test
    fun unavailableSensorTimestampIsNeverShownAs1970() {
        assertEquals("—", formatUvirDateTime(0L, UvirDateFormat.INTERNATIONAL))
        assertEquals("—", formatClockTime(0L))
    }
    private val exampleTimestamp =
        Instant.parse("2026-09-15T12:34:56Z")
            .toEpochMilli()

    @Test
    fun internationalDateUsesIsoOrder() {
        assertEquals(
            "2026-09-15",
            formatUvirDateOnly(
                exampleTimestamp,
                UvirDateFormat.INTERNATIONAL,
                Locale.ITALIAN,
                TimeZone.getTimeZone("UTC")
            )
        )
    }

    @Test
    fun chartDateAppearsOnlyWhenTheSessionCrossesAcalendarDay() {
        val utc = TimeZone.getTimeZone("UTC")
        val sameDayStart = Instant.parse("2026-09-15T01:00:00Z").toEpochMilli()
        val sameDayEnd = Instant.parse("2026-09-15T23:00:00Z").toEpochMilli()
        val nextDayEnd = Instant.parse("2026-09-16T00:01:00Z").toEpochMilli()

        assertFalse(sessionChartSpansMultipleDays(sameDayStart, sameDayEnd, utc))
        assertTrue(sessionChartSpansMultipleDays(sameDayStart, nextDayEnd, utc))
    }

    @Test
    fun americanDateUsesMonthFirst() {
        assertEquals(
            "09/15/2026",
            formatUvirDateOnly(
                exampleTimestamp,
                UvirDateFormat.AMERICAN,
                Locale.ITALIAN,
                TimeZone.getTimeZone("UTC")
            )
        )
    }

    @Test
    fun europeanDateUsesDayFirst() {
        assertEquals(
            "15/09/2026",
            formatUvirDateOnly(
                exampleTimestamp,
                UvirDateFormat.EUROPEAN,
                Locale.ITALIAN,
                TimeZone.getTimeZone("UTC")
            )
        )
    }

    @Test
    fun internationalExportDateTimeUsesIsoOrder() {
        val previousTimeZone = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
            assertEquals(
                "2026-09-15 12:34:56",
                formatUvirInternationalDateTime(exampleTimestamp)
            )
        } finally {
            TimeZone.setDefault(previousTimeZone)
        }
    }

    @Test
    fun exportDateTimeCanUseTheSelectedAmericanOrder() {
        val previousTimeZone = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
            assertEquals(
                "09/15/2026 12:34:56",
                csvDateTime(
                    timestamp = exampleTimestamp,
                    dateFormat = UvirDateFormat.AMERICAN,
                    locale = Locale.US
                )
            )
        } finally {
            TimeZone.setDefault(previousTimeZone)
        }
    }

    @Test
    fun timeCanUse24HourFormat() {
        assertEquals(
            "12:34:56",
            formatUvirTimeOnly(
                timestamp = exampleTimestamp,
                format = UvirTimeFormat.H24,
                locale = Locale.US,
                timeZone = TimeZone.getTimeZone("UTC")
            )
        )
    }

    @Test
    fun timeCanUse12HourFormat() {
        assertEquals(
            "12:34:56 PM",
            formatUvirTimeOnly(
                timestamp = exampleTimestamp,
                format = UvirTimeFormat.H12,
                locale = Locale.US,
                timeZone = TimeZone.getTimeZone("UTC")
            )
        )
    }

    @Test
    fun selectedExportCanUse12HourFormat() {
        val previousTimeZone = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
            assertEquals(
                "09/15/2026 12:34:56 PM",
                csvDateTime(
                    timestamp = exampleTimestamp,
                    dateFormat = UvirDateFormat.AMERICAN,
                    locale = Locale.US,
                    timeFormat = UvirTimeFormat.H12
                )
            )
        } finally {
            TimeZone.setDefault(previousTimeZone)
        }
    }

    @Test
    fun unknownExportModeFallsBackToInternational() {
        assertEquals(
            UvirExportMode.INTERNATIONAL,
            UvirExportMode.fromStoredValue("unknown")
        )
    }

    @Test
    fun newInstallUsesScientificInternationalExportDefaults() {
        assertEquals(
            UvirExportMode.INTERNATIONAL,
            UvirExportMode.fromStoredValue(null)
        )
        assertEquals(
            UvirIrradianceUnit.MW_CM2,
            UvirIrradianceUnit.fromStoredValue(null)
        )
        assertEquals(
            UvirNumericFormat.SYSTEM,
            UvirNumericFormat.fromStoredValue(null)
        )
        assertEquals(
            UvirDateFormat.SYSTEM,
            UvirDateFormat.fromStoredValue(null)
        )
        assertEquals(
            UvirTimeFormat.SYSTEM,
            UvirTimeFormat.fromStoredValue(null)
        )
        assertEquals(
            AppLanguage.SYSTEM,
            AppLanguage.fromStoredValue(null)
        )
    }

    @Test
    fun unknownStoredDateFormatFallsBackToSystem() {
        assertEquals(
            UvirDateFormat.SYSTEM,
            UvirDateFormat.fromStoredValue("unknown")
        )
    }

    @Test
    fun unknownStoredTimeFormatFallsBackToSystem() {
        assertEquals(
            UvirTimeFormat.SYSTEM,
            UvirTimeFormat.fromStoredValue("unknown")
        )
    }

    @Test
    fun sensorActivityUsesTwoCompactLines() {
        val timestamp = 1_789_290_123_000L
        val formatted = formatSensorListLastActivity(timestamp)

        assertEquals(2, formatted.lines().size)
        assertEquals(formatDetailDateTime(timestamp).replace("  ", "\n"), formatted)
        assertTrue(formatted.lines()[1].contains(":"))
    }

    @Test
    fun missingSensorActivityUsesDash() {
        assertEquals("—", formatSensorListLastActivity(0L))
        assertEquals("—", formatSensorListLastActivity(-1L))
    }

    @Test
    fun detailDateTimeStaysOnOneLine() {
        val formatted = formatDetailDateTime(0L)

        assertFalse(formatted.contains('\n'))
        assertFalse(formatted.contains("\\n"))
        assertEquals(1, formatted.lines().size)
        assertEquals("—", formatted)
    }
}
