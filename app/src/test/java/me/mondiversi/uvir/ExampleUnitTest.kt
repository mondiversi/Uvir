package me.mondiversi.uvir

import org.junit.Test

import org.junit.Assert.*

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }

    @Test
    fun automaticMeasurementNumberIsAppendedToDefaultNote() {
        assertEquals(
            "Outdoor test #12",
            formatAutomaticMeasurementNote(
                "  Outdoor test  ",
                12
            )
        )
    }

    @Test
    fun automaticMeasurementWithoutDefaultNoteContainsOnlyNumber() {
        assertEquals(
            "#1",
            formatAutomaticMeasurementNote(
                "   ",
                1
            )
        )
    }
}
