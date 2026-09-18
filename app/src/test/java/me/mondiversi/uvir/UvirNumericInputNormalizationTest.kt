package me.mondiversi.uvir

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UvirNumericInputNormalizationTest {
    @Test
    fun durationCarriesSecondsAndMinutes() {
        val normalized = normalizeDuration("1", "61", "60")

        assertEquals("2", normalized.hoursText)
        assertEquals("2", normalized.minutesText)
        assertEquals("0", normalized.secondsText)
        assertEquals(7_320L, normalized.totalSeconds)
        assertTrue(normalized.corrected)
    }

    @Test
    fun negativeDurationComponentsBecomeZero() {
        val normalized = normalizeDuration("-1", "2", "-3")

        assertEquals("0", normalized.hoursText)
        assertEquals("2", normalized.minutesText)
        assertEquals("0", normalized.secondsText)
    }

    @Test
    fun alertControlIntervalIsKeptWithinOneSecondAndOneDay() {
        val minimum = normalizeDuration("0", "0", "0", 1L, MAX_ALERT_REPEAT_SECONDS)
        val maximum = normalizeDuration("48", "0", "0", 1L, MAX_ALERT_REPEAT_SECONDS)

        assertEquals(1L, minimum.totalSeconds)
        assertEquals("1", minimum.secondsText)
        assertEquals(MAX_ALERT_REPEAT_SECONDS, maximum.totalSeconds)
        assertEquals("24", maximum.hoursText)
    }

    @Test
    fun boundedValuesAreClamped() {
        assertEquals(21, normalizeBoundedInteger("99", 1, 21).value)
        assertEquals(150L, normalizeBoundedLong("-1", 150L, 5_000L).value)
        assertEquals(10f, normalizeBoundedDecimal("12.5", 0.1f, 10f).value)
        assertEquals(0f, normalizeNonNegativeDecimal("-2").value)
    }

    @Test
    fun firmwareCompatibilityUsesSemanticVersionOrder() {
        assertTrue(firmwareIsCurrentForApp(UVIR_REQUIRED_SENSOR_FIRMWARE))
        assertTrue(firmwareIsCurrentForApp("0.6.0"))
        assertEquals(false, firmwareIsCurrentForApp("0.5.42"))
        assertEquals(false, firmwareIsCurrentForApp("v0.5.42"))
        assertEquals(false, firmwareIsCurrentForApp(""))
    }
}
