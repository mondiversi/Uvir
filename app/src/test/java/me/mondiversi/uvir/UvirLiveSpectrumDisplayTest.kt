package me.mondiversi.uvir

import org.junit.Assert.*
import org.junit.Test

class UvirLiveSpectrumDisplayTest {
    private val sample = SensorSample(uvc = 1.0, uvb = 1.0, uva = 2.0,
        violetto = 2.0, blu = 2.0, verde = 2.0, giallo = 2.0, arancione = 2.0, rosso = 2.0,
        f8 = 2.0, nir = 2.0)
    private val initial = UvirLiveSpectrumDisplay()
    private fun UvirLiveSpectrumDisplay.share(group: SensorGroup, value: Double, source: SensorSample = sample) =
        share(group, value, source, 2, UvirIrradianceUnit.W_M2)

    @Test fun defaultsShowAllChannelsAgainstTheCompleteTotalWithoutCountingHevTwice() {
        assertTrue(initial.excludedChannels.isEmpty())
        assertTrue(initial.groupPercentages.isEmpty())
        assertEquals(20.0, UvirLiveSpectrumDisplay.irradianceGroups.sumOf { initial.total(it, sample) }, 0.0)
        assertEquals(4.0, initial.value(ThresholdAlertMetric.HEV, sample), 0.0)
        assertEquals(.05f, initial.share(SensorGroup.UV, 1.0)!!, 0f)
        assertEquals(.6f, initial.share(SensorGroup.VISIBLE, 12.0)!!, 0f)
    }

    @Test fun percentageBasesAreIndependentPerIsland() {
        val uvGroup = initial.togglePercentageBase(SensorGroup.UV)
        assertEquals(.25f, uvGroup.share(SensorGroup.UV, 1.0)!!, 0f)
        assertEquals(.1f, uvGroup.share(SensorGroup.VISIBLE, 2.0)!!, 0f)
        assertEquals(1f, uvGroup.share(SensorGroup.UV, 4.0)!!, 0f)
        assertEquals(initial, uvGroup.togglePercentageBase(SensorGroup.UV))
    }

    @Test fun headerPercentagesAlwaysUseTheCompleteVisibleTotalRegardlessOfInternalMode() {
        for (state in listOf(initial, initial.toggle(ThresholdAlertMetric.UVC))) {
            for (group in UvirLiveSpectrumDisplay.irradianceGroups) {
                val expected = state.share(group, state.total(group, sample))
                val grouped = state.togglePercentageBase(group)
                assertEquals(expected, grouped.groupShare(group, sample, 2, UvirIrradianceUnit.W_M2))
                assertEquals(state.groupShare(group, sample, 2, UvirIrradianceUnit.W_M2),
                    grouped.groupShare(group, sample, 2, UvirIrradianceUnit.W_M2))
            }
        }
        val saturated = sample.copy(qualityFlags = UVIR_QUALITY_UV_OUT_OF_RANGE)
        assertNull(initial.togglePercentageBase(SensorGroup.VISIBLE)
            .groupShare(SensorGroup.VISIBLE, saturated, 2, UvirIrradianceUnit.W_M2))
    }

    @Test fun excludingOneChannelRecomputesBothBasesAndLeavesOtherValuesIntact() {
        val hidden = initial.toggle(ThresholdAlertMetric.UVC)
        assertFalse(hidden.visible(ThresholdAlertMetric.UVC))
        assertEquals(0.0, hidden.value(ThresholdAlertMetric.UVC, sample), 0.0)
        assertEquals(3.0, hidden.total(SensorGroup.UV, sample), 0.0)
        assertEquals(2.0, hidden.value(ThresholdAlertMetric.UVA, sample), 0.0)
        assertEquals(.105f, hidden.share(SensorGroup.UV, 2.0)!!, 0f)
        assertEquals(.667f, hidden.togglePercentageBase(SensorGroup.UV).share(SensorGroup.UV, 2.0)!!, 0f)
        assertEquals(initial, hidden.toggle(ThresholdAlertMetric.UVC))
    }

    @Test fun hevControlsItsOverlappingComponentsWithoutSubtractingThemTwice() {
        val hidden = initial.toggle(ThresholdAlertMetric.HEV)
        assertFalse(hidden.visible(ThresholdAlertMetric.HEV))
        assertFalse(hidden.visible(ThresholdAlertMetric.VIOLET))
        assertFalse(hidden.visible(ThresholdAlertMetric.BLUE))
        assertTrue(hidden.visible(ThresholdAlertMetric.GREEN))
        assertEquals(8.0, hidden.total(SensorGroup.VISIBLE, sample), 0.0)
        assertEquals(16.0, UvirLiveSpectrumDisplay.irradianceGroups.sumOf { hidden.total(it, sample) }, 0.0)
        val partial = hidden.toggle(ThresholdAlertMetric.VIOLET)
        assertTrue(partial.visible(ThresholdAlertMetric.HEV))
        assertEquals(2.0, partial.value(ThresholdAlertMetric.HEV, sample), 0.0)
        assertEquals(hidden, partial.toggle(ThresholdAlertMetric.HEV))
    }

    @Test fun excludingEverythingProducesZeroTotalsAndFiniteEmptyBars() {
        val hidden = UvirLiveSpectrumDisplay.irradianceGroups.fold(initial) { state, group ->
            UvirLiveSpectrumDisplay.channels(group).fold(state) { current, metric -> current.toggle(metric) }
        }
        for (group in UvirLiveSpectrumDisplay.irradianceGroups) {
            assertEquals(0.0, hidden.total(group, sample), 0.0)
            assertEquals(0f, hidden.share(group, 0.0)!!, 0f)
            assertEquals(0f, hidden.togglePercentageBase(group).share(group, 0.0)!!, 0f)
        }
    }

    @Test fun excludedSaturatedGroupsDoNotInvalidateUnrelatedDisplayedShares() {
        val saturated = sample.copy(qualityFlags = UVIR_QUALITY_UV_OUT_OF_RANGE)
        assertNull(initial.share(SensorGroup.VISIBLE, 2.0, saturated))
        assertEquals(.167f, initial.togglePercentageBase(SensorGroup.VISIBLE)
            .share(SensorGroup.VISIBLE, 2.0, saturated)!!, 0f)
        val noUv = initial.toggle(ThresholdAlertMetric.UV_TOTAL)
        assertEquals(.125f, noUv.share(SensorGroup.VISIBLE, 2.0, saturated)!!, 0f)
        assertFalse(noUv.outOfRange(SensorGroup.UV, saturated))
        assertTrue(initial.toggle(ThresholdAlertMetric.UVC).outOfRange(SensorGroup.UV, saturated))
    }

    @Test fun displayPrecisionStillControlsFractionsInEveryUnit() {
        for (unit in UvirIrradianceUnit.entries) {
            val digits = defaultUvirLiveIrradianceDecimals(unit)
            val tiny = unit.toCanonicalWm2(.01 / Math.pow(10.0, digits.toDouble()))
            val source = SensorSample(uva = tiny)
            for (state in listOf(initial, initial.togglePercentageBase(SensorGroup.UV))) {
                assertEquals(0f, state.share(SensorGroup.UV, tiny, source, digits, unit)!!, 0f)
            }
        }
    }

    @Test fun displaySelectionsCannotModifyRawSamplesBiologicalEffectsAlertsOrScientificFormatting() {
        val original = sample.copy()
        val biology = biologicalEffects(sample)
        val alertValue = sample.thresholdMetricValue(ThresholdAlertMetric.UVC)
        val scientific = formatUvirIrradianceExportNumber(sample.uvc, UvirNumericFormat.EUROPEAN, UvirIrradianceUnit.W_M2)
        val state = initial.toggle(ThresholdAlertMetric.UVC).toggle(ThresholdAlertMetric.HEV)
            .togglePercentageBase(SensorGroup.VISIBLE)
        state.total(SensorGroup.UV, sample)
        state.share(SensorGroup.VISIBLE, state.value(ThresholdAlertMetric.GREEN, sample))
        assertEquals(original, sample)
        assertEquals(biology, biologicalEffects(sample))
        assertEquals(alertValue, sample.thresholdMetricValue(ThresholdAlertMetric.UVC), 0.0)
        assertEquals(scientific, formatUvirIrradianceExportNumber(sample.uvc, UvirNumericFormat.EUROPEAN, UvirIrradianceUnit.W_M2))
        assertEquals(initial, initial.toggle(ThresholdAlertMetric.BIO_DNA_UV))
    }
}
