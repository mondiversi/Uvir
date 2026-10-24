package me.mondiversi.uvir

import org.junit.Assert.*
import org.junit.Test

class UvirMainSpectrumPreferencesTest {
    @Test fun unknownNamesAggregatesAndBiologicalProxiesCannotBecomePersistentHiddenChannels() {
        assertEquals(setOf(ThresholdAlertMetric.UVC, ThresholdAlertMetric.BLUE), decodeUvirMainHiddenChannels(
            setOf("UVC", "BLUE", "HEV", "HEB", "UV_TOTAL", "VISIBLE_TOTAL", "NIR_TOTAL", "BIO_DNA_UV", "unknown")))
    }

    @Test fun everyMeasuredChannelCanRoundTripIncludingBothHevComponents() {
        val hidden = UvirLiveSpectrumDisplay.irradianceGroups.fold(UvirLiveSpectrumDisplay()) { display, group ->
            UvirLiveSpectrumDisplay.channels(group).fold(display) { state, metric -> state.toggle(metric) }
        }
        assertEquals(11, hidden.excludedChannels.size)
        assertEquals(hidden.excludedChannels, decodeUvirMainHiddenChannels(hidden.excludedChannels.map { it.name }.toSet()))
        assertEquals(setOf(ThresholdAlertMetric.VIOLET, ThresholdAlertMetric.BLUE),
            UvirLiveSpectrumDisplay().toggle(ThresholdAlertMetric.HEV).excludedChannels)
    }

    @Test fun firstInstallAndInvalidLegacyNamesShowAllChannels() {
        assertTrue(decodeUvirMainHiddenChannels(emptySet()).isEmpty())
        assertTrue(decodeUvirMainHiddenChannels(setOf("", "uvc", "not-a-band")).isEmpty())
    }

    @Test fun visibilityIsAnAppPreferenceAndNeverASensorConfigurationOrOperationalValue() {
        assertTrue(isTransferableAppPreference(KEY_MAIN_HIDDEN_SPECTRUM_CHANNELS))
        assertFalse(KEY_MAIN_HIDDEN_SPECTRUM_CHANNELS in sensorSelectionPreferenceKeys)
        assertFalse(KEY_MAIN_HIDDEN_SPECTRUM_CHANNELS in sensorConfigurationPreferenceKeys)
        assertFalse(KEY_MAIN_HIDDEN_SPECTRUM_CHANNELS in sensorOperationalPreferenceKeys)
    }
}
