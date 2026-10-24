package me.mondiversi.uvir

import org.junit.Assert.*
import org.junit.Test

class UvirLiveIrradianceBarsTest {
    // Preserve the physical values of the pre-SI noise/boundary fixtures.
    private fun legacyFixtureFraction(value: Double, total: Double, digits: Int, unit: UvirIrradianceUnit,
        percentDigits: Int = 1) = uvirLiveIrradianceFraction(value / 100, total / 100, digits, unit, percentDigits)
    private fun legacyFixtureGroupShare(value: Double, uv: Double, visible: Double, ir: Double,
        digits: Int, unit: UvirIrradianceUnit) = uvirLiveIrradianceGroupShare(value / 100, uv / 100, visible / 100, ir / 100, digits, unit)
    @Test fun displayedZerosHaveEmptyBarsInEveryUnit() {
        for (unit in UvirIrradianceUnit.entries) {
            val digits = defaultUvirLiveIrradianceDecimals(unit)
            val halfDisplayStepInLegacyUnits = unit.toCanonicalWm2(
                .5 / Math.pow(10.0, digits.toDouble())) * 100
            for (fraction in listOf(.001, .01, .1, .99)) {
                val value = halfDisplayStepInLegacyUnits * fraction
                assertEquals(0f, legacyFixtureFraction(value, value, digits, unit), 0f)
                assertEquals(0f, legacyFixtureGroupShare(value, value, value, value, digits, unit), 0f)
            }
        }
    }

    @Test fun fluctuationsThatDoNotChangeDisplayedValuesDoNotMoveBars() {
        val unit = UvirIrradianceUnit.UW_CM2
        assertEquals(.5f, legacyFixtureFraction(1.01, 2.01, 0, unit), 0f)
        assertEquals(.5f, legacyFixtureFraction(1.49, 2.49, 0, unit), 0f)
        assertEquals(.5f, legacyFixtureGroupShare(1.01, 1.01, 1.49, .49, 0, unit), 0f)
        assertEquals(.5f, legacyFixtureGroupShare(1.49, 1.49, 1.01, .01, 0, unit), 0f)
    }

    @Test fun roundingBoundaryAndPrecisionChangesMatchTheDisplayedNumbers() {
        val unit = UvirIrradianceUnit.UW_CM2
        assertEquals(0f, legacyFixtureFraction(.49, 1.0, 0, unit), 0f)
        assertEquals(1f, legacyFixtureFraction(.5, 1.0, 0, unit), 0f)
        assertEquals(.49f, legacyFixtureFraction(.49, 1.0, 2, unit), 0f)
        assertEquals(1f, legacyFixtureGroupShare(.5, .5, .49, .49, 0, unit), 0f)
    }

    @Test fun barPrecisionAlsoMatchesTheWrittenPercentage() {
        val unit = UvirIrradianceUnit.UW_CM2
        assertEquals(.333f, legacyFixtureFraction(1.0, 3.0, 6, unit), 0f)
        assertEquals(.33f, legacyFixtureFraction(1.0, 3.0, 6, unit, percentDigits = 0), 0f)
        assertEquals(0f, legacyFixtureFraction(.001, 1.0, 2, unit, percentDigits = 0), 0f)
        assertEquals(.54f, legacyFixtureFraction(.0161, .03, 6, unit, percentDigits = 0), 0f)
    }

    @Test fun invalidOrEmptyTotalsNeverCreateInvalidFills() {
        val unit = UvirIrradianceUnit.W_M2
        assertEquals(0f, legacyFixtureFraction(0.0, 0.0, 2, unit), 0f)
        assertEquals(0f, legacyFixtureFraction(Double.NaN, 1.0, 2, unit), 0f)
        assertEquals(0f, legacyFixtureFraction(1.0, Double.POSITIVE_INFINITY, 2, unit), 0f)
    }

    @Test fun rawSampleAndScientificCalculationsRemainUnchanged() {
        val sample = SensorSample(uva = .0001, blu = .0002, nir = .0003)
        val totals = sample.spectralTotals()
        val effects = biologicalEffects(sample)
        assertEquals(0f, legacyFixtureGroupShare(.01, .01, .02, .03, 2, UvirIrradianceUnit.W_M2), 0f)
        assertEquals(.0001, sample.uva, 0.0)
        assertEquals(totals, sample.spectralTotals())
        assertEquals(effects, biologicalEffects(sample))
        assertTrue(totals.share(SensorGroup.UV)!! > 0f)
        assertTrue(effects.uvaPhotoagingScore > 0f)
        assertEquals("0,0001", formatUvirIrradianceExportNumber(sample.uva,
            UvirNumericFormat.EUROPEAN, UvirIrradianceUnit.W_M2))
    }
}
