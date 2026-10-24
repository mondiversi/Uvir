package me.mondiversi.uvir

import org.junit.Assert.*
import org.junit.Test

class UvirExportPrecisionTest {
    @Test fun scientificNumbersRetainRoundTrippablePrecisionAndTinyValues() {
        for (value in listOf(0.0, 0.12345678901234567, 1.234567890123456e-14, 987654321.1234567, Double.MIN_VALUE)) {
            val text = formatUvirExportNumber(value, UvirNumericFormat.INTERNATIONAL)
            assertEquals(value.toBits(), text.toDouble().toBits())
            assertFalse(text.contains(','))
        }
        assertEquals("0,0000000000123456789", formatUvirExportNumber(.0000000000123456789, UvirNumericFormat.EUROPEAN))
    }

    @Test fun customNumbersUseTheChosenUnitDigitsAndTrailingZerosWithoutChangingTheSample() {
        val original = .0123456789012345
        for (unit in UvirIrradianceUnit.entries) for (digits in 0..6) {
            val expected = formatUvirLiveIrradianceNumber(original, digits, UvirNumericFormat.EUROPEAN, unit)
            assertEquals(expected, formatUvirIrradianceExportNumber(original, UvirNumericFormat.EUROPEAN, unit, digits))
        }
        assertEquals("1,20", formatUvirIrradianceExportNumber(.012, UvirNumericFormat.EUROPEAN, UvirIrradianceUnit.UW_CM2, 2))
        assertEquals(.0123456789012345, original, 0.0)
    }

    @Test fun csvCustomPrecisionAffectsIrradiancePercentagesAndNotIdentifiersOrTimestamps() {
        val record = SavedRecordDetail(123, 123456789, "Keep", false, SensorSample(uva = .0123456789012345))
        val lines = measurementCsv(listOf(record), UvirNumericFormat.EUROPEAN,
            irradianceUnit = UvirIrradianceUnit.UW_CM2, irradianceDecimals = 2).lines()
        val row = lines[0].split(';').zip(lines[1].split(';')).toMap()
        assertEquals("1,23", row["UVA_315_410_nm_uW_cm2"])
        assertEquals("100,00", row["UVA_Percentages_of_total"])
        assertEquals("123", row["Acquisition_ID"])
        assertEquals("123456789", row["Timestamp_ms"])
    }

    @Test fun scientificCsvDoesNotDropSmallIrradianceAndKeepsSIHeaders() {
        val value = 1.234567890123456e-14
        val record = SavedRecordDetail(1, 1, "", false, SensorSample(uva = value))
        val lines = measurementCsv(listOf(record), UvirNumericFormat.INTERNATIONAL,
            irradianceUnit = UvirIrradianceUnit.W_M2).lines()
        val row = lines[0].split(';').zip(lines[1].split(';')).toMap()
        assertEquals(value.toBits(), row.getValue("UVA_315_410_nm_W_m2").toDouble().toBits())
        assertFalse(lines[0].contains("uW_cm2"))
    }

    @Test fun modelsComeFromReportedHardwareOrTheKnownV1SchemaNotAnotherSensor() {
        assertEquals(UvirExportHardwareModels(), exportHardwareModels(null))
        assertEquals(UvirExportHardwareModels("AS7331", "AS7343"),
            exportHardwareModels(UvirSensorRuntimeInfo(protocol = "uvir-sensor-v1")))
        assertEquals(UvirExportHardwareModels("UV-X", "VIS-Y"),
            exportHardwareModels(UvirSensorRuntimeInfo(uvSensorModel = "UV-X", visibleSensorModel = "VIS-Y")))
        assertEquals(UvirExportHardwareModels(), exportHardwareModels(UvirSensorRuntimeInfo(protocol = "other")))
        assertEquals(UvirExportHardwareModels(), emptyMap<Long, UvirExportHardwareModels>().models(99))
    }
}
