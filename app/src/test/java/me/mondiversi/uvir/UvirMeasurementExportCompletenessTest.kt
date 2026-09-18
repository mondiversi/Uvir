package me.mondiversi.uvir

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UvirMeasurementExportCompletenessTest {
    @Test
    fun csvIncludesGroupTotalsAndRelativePercentagesWithoutRedundantAutomaticFlag() {
        val record =
            SavedRecordDetail(
                id = 1L,
                timestamp = 1L,
                note = "",
                automatic = true,
                sample =
                    SensorSample(
                        uvc = 1.0,
                        uvb = 1.0,
                        uva = 2.0,
                        violetto = 2.0,
                        blu = 2.0,
                        verde = 2.0,
                        giallo = 2.0,
                        arancione = 1.0,
                        rosso = 1.0,
                        f8 = 3.0,
                        nir = 1.0
                    )
            )

        val lines =
            measurementCsv(
                records = listOf(record),
                numericFormat = UvirNumericFormat.INTERNATIONAL
            ).trim().lines()
        val headers = lines[0].split(';')
        val values = lines[1].split(';')
        val row = headers.zip(values).toMap()

        assertFalse("Automatic" in headers)
        assertEquals("4", row["Ultraviolet_total_uW_cm2"])
        assertEquals("25.0", row["UVC_percent"])
        assertEquals("50.0", row["UVA_percent"])
        assertEquals("10", row["Visible_total_uW_cm2"])
        assertEquals("40.0", row["HEV_percent"])
        assertEquals("4", row["Infrared_total_uW_cm2"])
        assertEquals("75.0", row["FarRed_percent"])
        assertEquals("25.0", row["NIR_percent"])
        assertEquals(headers.size, values.size)
        assertTrue(headers.size > 40)
    }
}
