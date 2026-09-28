package me.mondiversi.uvir

import org.junit.Assert.*
import org.junit.Test

class UvirChartScaleTest {
    @Test fun subunitValuesUseTheirPeakRatherThanAMinimumOfOne() {
        assertEquals(0.022, uvirChartMaximum(listOf(0.005, 0.02, 0.01)), 1e-12)
        assertEquals(22.0, uvirChartMaximum(listOf(5.0, 20.0, 10.0)), 1e-12)
    }

    @Test fun allThreeUnitsPreserveEveryPointOfTheChart() {
        val samples = listOf(0.001, 1.0, 5.0, 20.0)
        val canonicalMaximum = uvirChartMaximum(samples)
        for (unit in UvirIrradianceUnit.entries) {
            val converted = samples.map(unit::fromCanonicalUwCm2)
            val maximum = uvirChartMaximum(converted, unit.fromCanonicalUwCm2(1.0))
            assertEquals(unit.fromCanonicalUwCm2(canonicalMaximum), maximum, 1e-12)
            for (index in samples.indices) {
                assertEquals(samples[index] / canonicalMaximum, converted[index] / maximum, 1e-12)
            }
        }
    }

    @Test fun valuesMuchSmallerThanOneStillOccupyTheChart() {
        for (peak in listOf(1e-12, 0.00002, 0.02, 20.0, 2e6)) {
            assertEquals(1.0 / UVIR_CHART_HEADROOM, peak / uvirChartMaximum(listOf(peak)), 1e-12)
        }
    }

    @Test fun noDataAndZeroHaveAFinitePositiveFallbackInEachUnit() {
        for (unit in UvirIrradianceUnit.entries) {
            val fallback = unit.fromCanonicalUwCm2(1.0)
            assertEquals(fallback, uvirChartMaximum(emptyList(), fallback), 0.0)
            assertEquals(fallback, uvirChartMaximum(listOf(0.0, -1.0), fallback), 0.0)
        }
        assertEquals(1.0, uvirChartMaximum(emptyList(), Double.NaN), 0.0)
    }

    @Test fun nonFiniteSamplesDoNotDistortTheScale() {
        assertEquals(0.022, uvirChartMaximum(listOf(Double.NaN, Double.POSITIVE_INFINITY,
            Double.NEGATIVE_INFINITY, -200.0, 0.02)), 1e-12)
    }

    @Test fun headroomCannotOverflow() {
        assertEquals(Double.MAX_VALUE, uvirChartMaximum(listOf(Double.MAX_VALUE)), 0.0)
        assertTrue(uvirChartMaximum(listOf(Double.MIN_VALUE)) > 0.0)
    }

    @Test fun variantAndVisibleWindowScalesUseOnlyTheProvidedSamples() {
        assertEquals(0.022, uvirChartMaximum(listOf(0.005, 0.02)), 1e-12)
        assertEquals(220.0, uvirChartMaximum(listOf(100.0, 200.0)), 1e-12)
        // A common scale for side-by-side variants pads their shared peak exactly once.
        val commonPeak = 200.0
        assertEquals(220.0, uvirChartMaximum(listOf(commonPeak)), 1e-12)
    }

    @Test fun ticksAreDistinctAcrossAllUnitsAndOrdersOfMagnitude() {
        for (peak in listOf(0.000001, 0.005, 5.0, 20.0, 20_000.0, 2e9)) {
            for (unit in UvirIrradianceUnit.entries) {
                val maximum = unit.fromCanonicalUwCm2(uvirChartMaximum(listOf(peak)))
                val labels = (0..4).map { step ->
                    formatUvirChartAxisValue(maximum * step / 4, maximum,
                        unit.displayFractionDigits(2), UvirNumericFormat.INTERNATIONAL)
                }
                assertEquals("maximum=$maximum, labels=$labels", 5, labels.toSet().size)
                assertTrue(labels.all { it.length <= 10 })
            }
        }
    }

    @Test fun axisFormattingRespectsDecimalConventionIncludingScientificNotation() {
        assertEquals("0.0220", formatUvirChartAxisValue(0.022, 0.022, 1, UvirNumericFormat.INTERNATIONAL))
        assertEquals("0,0220", formatUvirChartAxisValue(0.022, 0.022, 1, UvirNumericFormat.EUROPEAN))
        assertEquals("2.20e-5", formatUvirChartAxisValue(0.000022, 0.000022, 1, UvirNumericFormat.INTERNATIONAL))
        assertEquals("2,20e-5", formatUvirChartAxisValue(0.000022, 0.000022, 1, UvirNumericFormat.EUROPEAN))
        assertEquals("0", formatUvirChartAxisValue(0.0, 0.000022, 1, UvirNumericFormat.INTERNATIONAL))
    }

    @Test fun scientificLabelsHandleExtremeFiniteValuesWithoutBecomingZero() {
        assertFalse(formatUvirChartAxisValue(Double.MIN_VALUE, Double.MIN_VALUE, 1,
            UvirNumericFormat.INTERNATIONAL).startsWith("0.00"))
        assertFalse(formatUvirChartAxisValue(Double.MAX_VALUE, Double.MAX_VALUE, 1,
            UvirNumericFormat.INTERNATIONAL).contains("Infinity"))
    }
}
