package me.mondiversi.uvir

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val calibrationChannelMetrics = listOf(
    ThresholdAlertMetric.UVC, ThresholdAlertMetric.UVB, ThresholdAlertMetric.UVA,
    ThresholdAlertMetric.VIOLET, ThresholdAlertMetric.BLUE, ThresholdAlertMetric.GREEN,
    ThresholdAlertMetric.YELLOW, ThresholdAlertMetric.ORANGE, ThresholdAlertMetric.RED,
    ThresholdAlertMetric.FAR_RED, ThresholdAlertMetric.NIR
)

/** Same channel order as gains, offsets and window factors; reuse the live-value palette. */
internal fun calibrationChannelColor(index: Int): Color =
    thresholdAlertMetricDisplayColor(calibrationChannelMetrics[index])

/** Decorative only: the adjacent localized name remains the accessible identifier. */
@Composable
internal fun UvirCalibrationChannelDot(index: Int, enabled: Boolean = true, modifier: Modifier = Modifier) {
    val color = calibrationChannelColor(index)
    Box(modifier.size(10.dp).background(if (enabled) color else color.copy(alpha = .38f), CircleShape))
}
