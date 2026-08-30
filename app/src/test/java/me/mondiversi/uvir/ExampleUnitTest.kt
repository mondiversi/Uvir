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

    @Test
    fun automaticSessionNameRemovesMeasurementSequence() {
        assertEquals(
            "Outdoor test",
            automaticSessionNoteName(
                "Outdoor test #12",
                12
            )
        )
        assertEquals(
            "",
            automaticSessionNoteName(
                "#1",
                1
            )
        )
    }

    @Test
    fun measurementExportSchemasStayAligned() {
        assertEquals(
            31,
            MEASUREMENT_EXPORT_COLUMNS_IT.size
        )
        assertEquals(
            MEASUREMENT_EXPORT_COLUMNS_IT.size,
            MEASUREMENT_EXPORT_COLUMNS_EN.size
        )
        assertEquals(
            "Modello_biologico",
            MEASUREMENT_EXPORT_COLUMNS_IT[24]
        )
        assertEquals(
            "Biological_model",
            MEASUREMENT_EXPORT_COLUMNS_EN[24]
        )
    }
}
