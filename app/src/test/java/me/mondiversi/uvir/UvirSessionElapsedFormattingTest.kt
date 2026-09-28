package me.mondiversi.uvir

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UvirSessionElapsedFormattingTest {
    @Test
    fun formatsElapsedTimeFromThePreviousDisplayedRecord() {
        assertEquals(
            "+01:01:01",
            sessionRecordElapsedText(
                currentTimestamp = 3_661_999L,
                previousTimestamp = 999L
            )
        )
    }

    @Test
    fun usesZeroForTheFirstRecordAndOmitsInvalidIntervals() {
        assertEquals("+00:00:00", sessionRecordElapsedText(1_000L, null))
        assertNull(sessionRecordElapsedText(0L, 1_000L))
        assertNull(sessionRecordElapsedText(1_000L, 2_000L))
    }
}
