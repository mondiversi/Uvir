package me.mondiversi.uvir

import org.junit.Assert.*
import org.junit.Test

class UvirSpectrumGroupVisibilityTest {
    private val initial = UvirLiveSpectrumDisplay()

    @Test fun everyGroupToggleExcludesOnlyItsOwnMeasuredChildrenAndRestoresThemTogether() {
        for (group in UvirLiveSpectrumDisplay.irradianceGroups) {
            val hidden = initial.toggleGroup(group)
            assertFalse(hidden.groupVisible(group))
            assertEquals(UvirLiveSpectrumDisplay.channels(group).toSet(), hidden.excludedChannels)
            UvirLiveSpectrumDisplay.channels(group).forEach { assertFalse(hidden.visible(it)) }
            UvirLiveSpectrumDisplay.irradianceGroups.filter { it != group }.forEach { assertTrue(hidden.groupVisible(it)) }
            assertEquals(initial, hidden.toggleGroup(group))
        }
    }

    @Test fun aPartiallyVisibleGroupIsCheckedAndItsNextToggleHidesAllRemainingChildren() {
        for (group in UvirLiveSpectrumDisplay.irradianceGroups) {
            val partial = initial.toggle(UvirLiveSpectrumDisplay.channels(group).first())
            assertTrue(partial.groupVisible(group))
            val hidden = partial.toggleGroup(group)
            assertFalse(hidden.groupVisible(group))
            assertEquals(initial, hidden.toggleGroup(group))
        }
    }

    @Test fun visibleGroupToggleAlsoTracksOverlappingHevWithoutChangingOtherGroupsOrBiology() {
        val hidden = initial.toggleGroup(SensorGroup.VISIBLE)
        for (metric in listOf(ThresholdAlertMetric.HEV, ThresholdAlertMetric.VISIBLE_TOTAL)) assertFalse(hidden.visible(metric))
        val restoredBlue = hidden.toggle(ThresholdAlertMetric.BLUE)
        assertTrue(restoredBlue.groupVisible(SensorGroup.VISIBLE))
        assertTrue(restoredBlue.visible(ThresholdAlertMetric.HEV))
        assertFalse(restoredBlue.visible(ThresholdAlertMetric.VIOLET))
        assertEquals(hidden, restoredBlue.toggleGroup(SensorGroup.VISIBLE))
        assertEquals(initial, initial.toggleGroup(SensorGroup.BIOLOGICAL))
    }

    @Test fun groupChoicesUseOnlyExistingMainVisibilityPreferencesAndKeepThePercentageBasis() {
        val grouped = initial.togglePercentageBase(SensorGroup.UV)
        val hidden = grouped.toggleGroup(SensorGroup.UV).toggleGroup(SensorGroup.NIR)
        assertEquals(hidden.excludedChannels,
            decodeUvirMainHiddenChannels(hidden.excludedChannels.map { it.name }.toSet()))
        assertEquals(grouped.groupPercentages, hidden.groupPercentages)
    }

    @Test fun savedGroupExclusionAffectsEveryRecordsPresentationWithoutChangingRawData() {
        val first = SensorSample(uvc = 1.0, uvb = 2.0, uva = 3.0, blu = 4.0)
        val second = first.copy(uva = 8.0)
        val before = listOf(first.copy(), second.copy())
        val display = UvirSavedValueDisplay().copy(spectrum = initial.toggleGroup(SensorGroup.UV))
        for (sample in listOf(first, second)) {
            for (metric in savedAcquisitionMetrics(SessionChartGroup.UV)) {
                assertFalse(display.acquisitionVisible(metric))
                assertEquals(0.0, display.acquisitionValue(metric, sample), 0.0)
            }
            assertEquals(4.0, display.acquisitionValue(ThresholdAlertMetric.BLUE, sample), 0.0)
            assertTrue(display.acquisitionVisible(ThresholdAlertMetric.BIO_DNA_UV))
        }
        assertEquals(before, listOf(first, second))
        assertTrue(UvirSavedValueDisplay().spectrum.groupVisible(SensorGroup.UV))
    }
}
