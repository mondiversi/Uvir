package me.mondiversi.uvir

import org.junit.Assert.assertEquals
import org.junit.Test

class UvirCalibrationChannelColorsTest {
    @Test fun editorDotsFollowTheLiveChannelOrderAndPalette() {
        val channels = listOf(ThresholdAlertMetric.UVC, ThresholdAlertMetric.UVB, ThresholdAlertMetric.UVA,
            ThresholdAlertMetric.VIOLET, ThresholdAlertMetric.BLUE, ThresholdAlertMetric.GREEN,
            ThresholdAlertMetric.YELLOW, ThresholdAlertMetric.ORANGE, ThresholdAlertMetric.RED,
            ThresholdAlertMetric.FAR_RED, ThresholdAlertMetric.NIR)
        channels.forEachIndexed { index, channel ->
            assertEquals(channel.name, thresholdAlertMetricDisplayColor(channel), calibrationChannelColor(index))
        }
        assertEquals(11, channels.indices.map(::calibrationChannelColor).distinct().size)
    }
}
