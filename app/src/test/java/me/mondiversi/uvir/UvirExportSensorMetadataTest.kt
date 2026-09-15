package me.mondiversi.uvir

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UvirExportSensorMetadataTest {
    @Test
    fun chartContextIncludesNoteAndItsOwnSensorName() {
        assertEquals(
            "Note: #3 Morning\nSensor: Balcony – חיישן",
            chartExportContextText("#3 Morning", "Balcony – חיישן")
        )
        assertEquals("Session note: —\nSensor: Garden", chartExportContextText("", "Garden", "Session note"))
    }

    @Test
    fun unavailableSensorIsNotReplacedWithAnotherAssociation() {
        assertEquals("—", exportSensorName(""))
        assertEquals("Note: Reading\nSensor: —", chartExportContextText("Reading", ""))
    }

    @Test
    fun sessionMetadataIncludesDistinctNamesWithoutRepeatingThem() {
        assertEquals("Garden · Balcony", exportSensorNames(listOf("Garden", "Garden", "Balcony")))
        assertEquals("—", exportSensorNames(listOf("", "—")))
    }

    @Test
    fun acquisitionCsvCarriesTheCorrectSensorForEachRecord() {
        val csv = measurementCsv(
            listOf(
                SavedRecordDetail(1L, 1L, "Morning", false, SensorSample(), sensorId = 7L, sensorDisplayName = "Garden"),
                SavedRecordDetail(2L, 2L, "Evening", false, SensorSample(), sensorId = 8L, sensorDisplayName = "Balcony")
            ),
            UvirNumericFormat.INTERNATIONAL
        )
        val lines = csv.trim().lines()
        assertTrue(lines[0].endsWith("Sensor_name"))
        assertTrue(lines[1].endsWith(";Garden"))
        assertTrue(lines[2].endsWith(";Balcony"))
        lines.forEach { assertEquals(MEASUREMENT_EXPORT_COLUMNS_EN.size, it.split(';').size) }
    }

    @Test
    fun acquisitionCsvPreservesUnicodeAndEscapesSensorName() {
        val sensorName = "Balcony; \"חיישן\""
        val csv = measurementCsv(
            listOf(SavedRecordDetail(1L, 1L, "", false, SensorSample(), sensorDisplayName = sensorName)),
            UvirNumericFormat.INTERNATIONAL
        )
        assertTrue(csv.trim().endsWith(";" + csvCell(sensorName)))
        assertTrue(csv.contains("חיישן"))
    }
}
