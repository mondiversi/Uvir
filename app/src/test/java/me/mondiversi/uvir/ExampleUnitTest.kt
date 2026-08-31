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
    fun measurementExportSchemasStayAligned() {
        assertEquals(
            "en",
            DATA_EXPORT_LANGUAGE
        )
        assertEquals(
            MEASUREMENT_EXPORT_COLUMNS_EN,
            measurementExportColumns(
                DATA_EXPORT_LANGUAGE
            )
        )
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
        assertEquals(
            "ID_acquisizione",
            MEASUREMENT_EXPORT_COLUMNS_IT.first()
        )
        assertEquals(
            "Acquisition_ID",
            MEASUREMENT_EXPORT_COLUMNS_EN.first()
        )
    }
}
