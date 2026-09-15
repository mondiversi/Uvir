package me.mondiversi.uvir

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal data class UvirSavedChartPoint(
    val coordinate: UvirChartCoordinate,
    val label: String,
    val value: String,
    val color: Color,
    val timestamp: String = ""
)

/** UI-only inspection: exports and live graphs never include this overlay. */
@Composable
internal fun UvirSavedChartInspector(
    points: List<UvirSavedChartPoint>,
    modifier: Modifier = Modifier,
    bars: Boolean = false,
    chart: @Composable (Modifier) -> Unit
) {
    var selected by remember(points) { mutableStateOf<Int?>(null) }
    val radius = with(LocalDensity.current) { 8.dp.toPx() }
    val coordinates = remember(points) { points.map { it.coordinate } }
    BoxWithConstraints(
        modifier = modifier.pointerInput(points, bars) {
            detectTapGestures { tap ->
                selected = selectSavedChartPoint(
                    coordinates, tap.x, tap.y, size.width.toFloat(), size.height.toFloat(),
                    selected, bars, radius)
            }
        }
    ) {
        chart(Modifier.fillMaxSize())
        selected?.let { index ->
            val point = points.getOrNull(index) ?: return@let
            Canvas(Modifier.fillMaxSize()) {
                val x = point.coordinate.x.coerceIn(0f, 1f) * size.width
                val y = point.coordinate.y.coerceIn(0f, 1f) * size.height
                drawLine(point.color.copy(alpha = 0.7f), Offset(x, 0f),
                    Offset(x, size.height), 1.dp.toPx())
                drawCircle(Color.White, 5.dp.toPx(), Offset(x, y))
                drawCircle(point.color, 3.dp.toPx(), Offset(x, y))
            }
            Surface(
                modifier = Modifier.align(Alignment.TopStart).padding(4.dp)
                    .widthIn(max = minOf(210.dp, maxWidth * 0.72f)),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                contentColor = MaterialTheme.colorScheme.onSurface,
                shadowElevation = 2.dp
            ) {
                Column(Modifier.padding(horizontal = 8.dp, vertical = 5.dp)) {
                    Text(point.label, fontSize = 11.sp, lineHeight = 13.sp)
                    Text(point.value, fontSize = 12.sp, lineHeight = 14.sp)
                    if (point.timestamp.isNotEmpty()) {
                        Text(point.timestamp, fontSize = 10.sp, lineHeight = 12.sp)
                    }
                }
            }
        }
    }
}
