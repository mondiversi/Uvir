package me.mondiversi.uvir

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UvirIrradianceFormattingTest {
    @Test
    fun biologicalUnitUsesTheSameEquivalentAbbreviationAsSavedRecords() {
        assertEquals("mW/cm² equiv.", UvirIrradianceUnit.MW_CM2.unitLabel(equivalent = true))
    }

    @Test
    fun missingPreferenceUsesWattsPerSquareMetre() {
        assertEquals(
            UvirIrradianceUnit.W_M2,
            UvirIrradianceUnit.fromStoredValue(null)
        )
    }

    @Test
    fun savedUserUnitsAreNotReplacedByTheNewDefault() {
        for (unit in UvirIrradianceUnit.entries) {
            assertEquals(unit, UvirIrradianceUnit.fromStoredValue(unit.storedValue))
        }
    }

    @Test
    fun convertsCanonicalWattsWithoutLosingScale() {
        val canonicalValue = 10.0

        assertEquals(
            10.0,
            UvirIrradianceUnit.W_M2.fromCanonicalWm2(canonicalValue),
            0.0
        )
        assertEquals(
            1.0,
            UvirIrradianceUnit.MW_CM2.fromCanonicalWm2(canonicalValue),
            0.0
        )
        assertEquals(
            1000.0,
            UvirIrradianceUnit.UW_CM2.fromCanonicalWm2(canonicalValue),
            0.0
        )
    }

    @Test
    fun conversionRoundTripsThroughCanonicalUnit() {
        UvirIrradianceUnit.entries.forEach { unit ->
            val displayed = unit.fromCanonicalWm2(12_345.678)
            assertEquals(
                12_345.678,
                unit.toCanonicalWm2(displayed),
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
