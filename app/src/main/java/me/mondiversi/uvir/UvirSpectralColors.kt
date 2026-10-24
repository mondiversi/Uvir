package me.mondiversi.uvir

import androidx.compose.ui.graphics.Color

/** Single spectral palette, taken from the Main View. Keep plots, labels and exports identical. */
internal fun uvirSpectralColor(metric: ThresholdAlertMetric): Color = when (metric) {
    ThresholdAlertMetric.UV_TOTAL -> Color(0xFF6A1B9A)
    ThresholdAlertMetric.UVC -> Color(0xFF9C27B0)
    ThresholdAlertMetric.UVB -> Color(0xFF673AB7)
    ThresholdAlertMetric.UVA -> Color(0xFF3F51B5)
    ThresholdAlertMetric.VISIBLE_TOTAL -> Color(0xFF00897B)
    ThresholdAlertMetric.HEV -> Color(0xFF3949AB)
    ThresholdAlertMetric.HEB, ThresholdAlertMetric.VIOLET -> Color(0xFF8E24AA)
    ThresholdAlertMetric.BLUE -> Color(0xFF1E88E5)
    ThresholdAlertMetric.GREEN -> Color(0xFF43A047)
    ThresholdAlertMetric.YELLOW -> Color(0xFFFDD835)
    ThresholdAlertMetric.ORANGE -> Color(0xFFFB8C00)
    ThresholdAlertMetric.RED -> Color(0xFFE53935)
    ThresholdAlertMetric.NIR_TOTAL -> Color(0xFF6D4C41)
    ThresholdAlertMetric.FAR_RED -> Color(0xFFD32F2F)
    ThresholdAlertMetric.NIR -> Color(0xFF8D6E63)
    ThresholdAlertMetric.BIO_DNA_UV -> Color(0xFF673AB7)
    ThresholdAlertMetric.BIO_UVA_PHOTOAGING -> Color(0xFF3F51B5)
    ThresholdAlertMetric.BIO_HEV_OXIDATIVE -> Color(0xFF1E88E5)
}
