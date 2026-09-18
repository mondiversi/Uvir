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
    fun automaticMeasurementKeepsOnlyTheDefaultNote() {
        assertEquals(
            "Outdoor test",
            formatAutomaticMeasurementNote(
                "  Outdoor test  "
            )
        )
    }

    @Test
    fun automaticMeasurementWithoutDefaultNoteStaysBlank() {
        assertEquals(
            "",
            formatAutomaticMeasurementNote(
                "   "
            )
        )
    }

    @Test
    fun listStartIsStoredAsAnEdgeAnchor() {
        assertEquals(
            ListEdgeAnchor.START,
            resolveListEdgeAnchor(
                firstVisibleItemIndex = 0,
                firstVisibleItemScrollOffset = 0,
                totalItemsCount = 12,
                canScrollForward = true
            )
        )
    }

    @Test
    fun listEndIsStoredAsAnEdgeAnchor() {
        assertEquals(
            ListEdgeAnchor.END,
            resolveListEdgeAnchor(
                firstVisibleItemIndex = 8,
                firstVisibleItemScrollOffset = 14,
                totalItemsCount = 12,
                canScrollForward = false
            )
        )
    }

    @Test
    fun intermediateListPositionRemainsExact() {
        assertEquals(
            ListEdgeAnchor.MIDDLE,
            resolveListEdgeAnchor(
                firstVisibleItemIndex = 4,
                firstVisibleItemScrollOffset = 14,
                totalItemsCount = 12,
                canScrollForward = true
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
            MEASUREMENT_EXPORT_COLUMNS_EN + "Out_of_range",
            measurementExportColumns(
                DATA_EXPORT_LANGUAGE
            )
        )
        assertEquals(
            44,
            MEASUREMENT_EXPORT_COLUMNS_IT.size
        )
        assertEquals(
            MEASUREMENT_EXPORT_COLUMNS_IT.size,
            MEASUREMENT_EXPORT_COLUMNS_EN.size
        )
        assertEquals(
            "Modello_biologico",
            MEASUREMENT_EXPORT_COLUMNS_IT[36]
        )
        assertEquals(
            "Biological_model",
            MEASUREMENT_EXPORT_COLUMNS_EN[36]
        )
        assertEquals(
            "ID_acquisizione",
            MEASUREMENT_EXPORT_COLUMNS_IT.first()
        )
        assertEquals(
            "Acquisition_ID",
            MEASUREMENT_EXPORT_COLUMNS_EN.first()
        )
        assertFalse("Automatic" in MEASUREMENT_EXPORT_COLUMNS_EN)
        assertTrue("UVC_percent" in MEASUREMENT_EXPORT_COLUMNS_EN)
        assertTrue("Ultraviolet_total_uW_cm2" in MEASUREMENT_EXPORT_COLUMNS_EN)
        assertTrue("Visible_total_uW_cm2" in MEASUREMENT_EXPORT_COLUMNS_EN)
        assertTrue("Infrared_total_uW_cm2" in MEASUREMENT_EXPORT_COLUMNS_EN)
    }
}
