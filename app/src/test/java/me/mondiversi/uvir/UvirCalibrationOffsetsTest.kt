package me.mondiversi.uvir

import org.junit.Assert.*
import org.junit.Test

class UvirCalibrationOffsetsTest {
    @Test fun defaultOffsetsAreZeroAndTotalsHaveNoIndependentOffset() {
        assertEquals(11, CalibrationOffsets().values.size)
        assertTrue(CalibrationOffsets().values.all { it == 0f })
        assertEquals(listOf("UVC", "UVB", "UVA"), calibrationOffsetChannelNames.take(3))
        assertFalse(calibrationOffsetChannelNames.any { it == "HEV" || it == "UV" })
        assertEquals(11, calibrationOffsetReferences.size)
        assertEquals(UvirSensorBands.VIOLET_CHANNELS, calibrationOffsetReferences[3])
    }

    @Test fun eachPhysicalOffsetRoundTripsAcrossEveryUnitWithoutChangingItsMeaning() {
        for (value in listOf(0f, Float.MIN_VALUE, .00000012f, .001f, .1f, .3f, 2.65625f, 2.953125f,
            3.1f, 3.3f, 10f, 1000f, 1234.567f, MAX_CALIBRATION_OFFSET_W_M2)) {
            for (unit in UvirIrradianceUnit.entries) {
                val text = calibrationOffsetInput(value, unit)
                assertEquals("$unit/$value", value, calibrationOffsetFromInput(text, unit))
                assertEquals("European $unit/$value", value, calibrationOffsetFromInput(text.replace('.', ','), unit))
                assertFalse(text.contains('E'))
            }
        }
        assertEquals(3.0, UvirIrradianceUnit.W_M2.fromCanonicalWm2(3.0), 0.0)
        assertEquals(.3, UvirIrradianceUnit.MW_CM2.fromCanonicalWm2(3.0), 0.0)
        assertEquals("3.1", calibrationOffsetInput(3.1f, UvirIrradianceUnit.W_M2))
        assertEquals("0.33", calibrationOffsetInput(3.3f, UvirIrradianceUnit.MW_CM2))
        for (invalid in listOf("", "NaN", "Infinity", "-1", "1000001"))
            assertNull(calibrationOffsetFromInput(invalid, UvirIrradianceUnit.UW_CM2))
    }

    @Test fun commandReplacesTheCompleteOffsetSetIncludingAnExplicitClear() {
        for (offsets in listOf(CalibrationOffsets(), CalibrationOffsets(List(15) { it * .125f }))) {
            val settings = SensorCalibrationSettings(1.2f, .8f, offsets = offsets)
            val command = sensorCalibrationCommand(settings)
            assertEquals(if (offsets.isLegacyPhysical) 31 else 27, command.split(' ').size)
            assertEquals(settings, parseSensorCalibrationCommand(command))
            assertFalse(sensorCalibrationCommandsSupported(listOf(command), "0.5.106"))
            assertEquals(offsets.isLegacyPhysical, sensorCalibrationCommandsSupported(listOf(command), "0.5.107"))
            assertTrue(sensorCalibrationCommandsSupported(listOf(command), "0.5.108"))
            assertNull(parseSensorCalibrationCommand(command + " 1"))
            assertNull(parseSensorCalibrationCommand(command.replace("OFFSETS", "OFFSET")))
            assertNull(parseSensorCalibrationCommand(command.substringBeforeLast(' ')))
        }
    }

    @Test fun legacySettingsAreNotMutatedAndOnlyAnEditConvertsToOneOffsetPerColor() {
        val legacy = CalibrationOffsets(List(15) { it.toFloat() })
        val grouped = legacy.forColorEditor()
        assertEquals(listOf(0f, 1f, 2f, 3.5f, 5.5f, 7f, 8.5f, 10f, 11f, 12.5f, 14f), grouped.values)
        assertEquals(List(15) { it.toFloat() }, legacy.values)
        assertEquals(1, legacy.version)
        assertEquals(2, grouped.version)
        val edited = grouped.withChannel(3, 2f)
        assertEquals(2f, edited.values[3], 0f)
        assertEquals(grouped.values.filterIndexed { index, _ -> index != 3 },
            edited.values.filterIndexed { index, _ -> index != 3 })
    }

    @Test fun oneOffsetDoesNotChangeAnyOtherChannel() {
        val value = CalibrationOffsets().withChannel(2, 2.65625f)
        assertEquals(2.65625f, value.values[2], 0f)
        assertTrue(value.values.filterIndexed { index, _ -> index != 2 }.all { it == 0f })
        for (invalid in listOf(Float.NaN, Float.POSITIVE_INFINITY, -1f, 1000001f)) {
            assertThrows(IllegalArgumentException::class.java) { value.withChannel(0, invalid) }
        }
    }
}
