package me.mondiversi.uvir

import org.junit.Assert.assertEquals
import org.junit.Test

class UvirSpectrumRowPressBoundsTest {
    private val groups = UvirLiveSpectrumDisplay.irradianceGroups.map { group ->
        if (group == SensorGroup.VISIBLE) listOf(ThresholdAlertMetric.HEV) + UvirLiveSpectrumDisplay.channels(group)
        else UvirLiveSpectrumDisplay.channels(group)
    }

    @Test fun neighbouringPressSurfacesMeetWithoutOverlapOrEmptyGaps() {
        for (channels in groups) for ((before, after) in channels.zipWithNext()) {
            assertEquals(UvirSpectrumRowSpacing, spectrumRowPressBottomInset(before) + spectrumRowPressTopInset(after))
        }
    }

    @Test fun edgeRowsUseTheExistingOuterSpaceWithoutMovingTheReadings() {
        for (channels in groups) {
            assertEquals(UvirSpectrumRowSpacing, spectrumRowPressTopInset(channels.first()))
            assertEquals(UvirIslandContentPadding, spectrumRowPressBottomInset(channels.last()))
            val before = UvirSpectrumRowSpacing * channels.size + UvirIslandContentPadding
            val after = channels.map { spectrumRowPressTopInset(it) + spectrumRowPressBottomInset(it) }
                .reduce { a, b -> a + b }
            assertEquals(before, after)
        }
    }
}
