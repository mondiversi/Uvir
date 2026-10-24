package me.mondiversi.uvir

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal val UvirSpectrumRowSpacing = 12.dp
internal val LocalUvirSpectrumRowSideInset = staticCompositionLocalOf { 0.dp }

/** Each row owns its padding, so both hit testing and the native ripple cover it. */
internal fun spectrumRowPressTopInset(metric: ThresholdAlertMetric?): Dp =
    if (metric in setOf(ThresholdAlertMetric.UVC, ThresholdAlertMetric.HEV, ThresholdAlertMetric.FAR_RED))
        UvirSpectrumRowSpacing else UvirSpectrumRowSpacing / 2f

internal fun spectrumRowPressBottomInset(metric: ThresholdAlertMetric?): Dp =
    if (metric in setOf(ThresholdAlertMetric.UVA, ThresholdAlertMetric.RED, ThresholdAlertMetric.NIR))
        UvirIslandContentPadding else UvirSpectrumRowSpacing / 2f
