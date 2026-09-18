package me.mondiversi.uvir

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UvirIrradianceFormattingTest {
    @Test
    fun missingPreferenceUsesMilliwattsPerSquareCentimetre() {
        assertEquals(
            UvirIrradianceUnit.MW_CM2,
            UvirIrradianceUnit.fromStoredValue(null)
        )
    }

    @Test
    fun convertsCanonicalMicrowattsWithoutLosingScale() {
        val canonicalValue = 1_000.0

        assertEquals(
            10.0,
            UvirIrradianceUnit.W_M2.fromCanonicalUwCm2(canonicalValue),
            0.0
        )
        assertEquals(
            1.0,
            UvirIrradianceUnit.MW_CM2.fromCanonicalUwCm2(canonicalValue),
            0.0
        )
        assertEquals(
            canonicalValue,
            UvirIrradianceUnit.UW_CM2.fromCanonicalUwCm2(canonicalValue),
            0.0
        )
    }

    @Test
    fun conversionRoundTripsThroughCanonicalUnit() {
        UvirIrradianceUnit.entries.forEach { unit ->
            val displayed = unit.fromCanonicalUwCm2(12_345.678)
            assertEquals(
                12_345.678,
                unit.toCanonicalUwCm2(displayed),
                0.000_001
            )
        }
    }

    @Test
    fun outOfRangeFlagsRemainIndependentBySensorGroup() {
        val uvOnly = SensorSample(qualityFlags = UVIR_QUALITY_UV_OUT_OF_RANGE)
        val visibleOnly = SensorSample(qualityFlags = UVIR_QUALITY_VISIBLE_NIR_OUT_OF_RANGE)

        assertTrue(uvOnly.isOutOfRange(SensorGroup.UV))
        assertFalse(uvOnly.isOutOfRange(SensorGroup.VISIBLE))
        assertFalse(visibleOnly.isOutOfRange(SensorGroup.UV))
        assertTrue(visibleOnly.isOutOfRange(SensorGroup.VISIBLE))
        assertTrue(visibleOnly.hasAnyOutOfRangeValue())
    }
}
