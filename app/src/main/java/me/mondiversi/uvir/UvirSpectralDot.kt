package me.mondiversi.uvir

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Decorative marker; the adjacent neutral label supplies the accessible name. */
@Composable
internal fun UvirSpectralDot(metric: ThresholdAlertMetric, modifier: Modifier = Modifier,
    tint: Color = uvirSpectralColor(metric)) {
    Box(modifier.size(10.dp).background(tint, CircleShape))
}
