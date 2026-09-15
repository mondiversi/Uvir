package me.mondiversi.uvir

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UvirDateFormattingTest {
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
        assertTrue(formatted.contains("  "))
    }
}
