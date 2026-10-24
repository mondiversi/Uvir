package me.mondiversi.uvir

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/** Decorative only: the full spectral row owns the accessible toggle action. */
@Composable
internal fun UvirSpectrumVisibilityMark(included: Boolean, tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(16.dp).graphicsLayer(alpha = tint.alpha)) {
        val color = tint.copy(alpha = 1f)
        val stroke = 1.2.dp.toPx()
        fun point(x: Float, y: Float) = Offset(size.width * x / 24f, size.height * y / 24f)
        if (included) {
            drawLine(color, point(5f, 12f), point(10f, 17f), stroke, StrokeCap.Round)
            drawLine(color, point(10f, 17f), point(19f, 7f), stroke, StrokeCap.Round)
        } else {
            drawLine(color, point(7f, 7f), point(17f, 17f), stroke, StrokeCap.Round)
            drawLine(color, point(7f, 17f), point(17f, 7f), stroke, StrokeCap.Round)
        }
    }
}
