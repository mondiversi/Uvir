package me.mondiversi.uvir

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UvirExportSensorMetadataTest {
    @Test
    fun chartContextIncludesOnlyTheNoteAndNoSensorProvenance() {
        assertEquals(
            "Note: #3 Morning",
            chartExportContextText("#3 Morning", "Balcony – חיישן")
        )
        assertEquals(
            "Session note: No note",
            chartExportContextText("", "Garden", "Session note")
        )
    }

    @Test
    fun unavailableSensorIsNotReplacedWithAnotherAssociation() {
        assertEquals("—", exportSensorName(""))
        assertEquals("Note: Reading", chartExportContextText("Reading", ""))
    }

    @Test
    fun sessionMetadataIncludesDistinctNamesWithoutRepeatingThem() {
        assertEquals("Garden · Balcony", exportSensorNames(listOf("Garden", "Garden", "Balcony")))
        assertEquals("—", exportSensorNames(listOf("", "—")))
    }

    @Test
    fun acquisitionCsvOmitsRetiredSensorNamesAndKeepsReferenceModels() {
        val csv = measurementCsv(
            listOf(
                SavedRecordDetail(1L, 1L, "Morning", false, SensorSample(), sensorId = 7L, sensorDisplayName = "Garden"),
                SavedRecordDetail(2L, 2L, "Evening", false, SensorSample(), sensorId = 8L, sensorDisplayName = "Balcony")
            ),
            UvirNumericFormat.INTERNATIONAL
        )
        val lines = csv.trim().lines()
        assertTrue(lines[0].endsWith("Saturation;Optical_window;UV_sensor_model;VIS_IR_sensor_model"))
        assertTrue(lines[1].endsWith(";0;;AS7331;AS7343"))
        assertTrue(lines[2].endsWith(";0;;AS7331;AS7343"))
        assertTrue(!csv.contains("Garden") && !csv.contains("Balcony"))
        lines.forEach {
            assertEquals(
                measurementExportColumns(DATA_EXPORT_LANGUAGE).size,
                it.split(';').size
            )
        }
    }

    @Test
    fun acquisitionCsvPreservesUnicodeInNotesAndDiscardsOldAliases() {
        val sensorName = "Balcony; \"חיישן\""
        val csv = measurementCsv(
            listOf(SavedRecordDetail(1L, 1L, "Note; \"🌙\"", false, SensorSample(), sensorDisplayName = sensorName)),
            UvirNumericFormat.INTERNATIONAL
        )
        assertTrue(csv.trim().endsWith(";0;;AS7331;AS7343"))
        assertTrue(!csv.contains("חיישן"))
        assertTrue(csv.contains(csvCell("Note; \"🌙\"")))
    }
}
