package me.mondiversi.uvir

import kotlin.math.hypot

internal data class UvirChartCoordinate(val x: Float, val y: Float)

/** Uses screen distance, not raw values, including for logarithmic axes. */
internal fun selectSavedChartPoint(
    points: List<UvirChartCoordinate>,
    tapX: Float,
    tapY: Float,
    width: Float,
    height: Float,
    previous: Int? = null,
    bars: Boolean = false,
    overlapRadius: Float = 8f
): Int? {
    if (points.isEmpty() || width <= 0f || height <= 0f ||
        !tapX.isFinite() || !tapY.isFinite()) return null
    val distances = points.mapIndexedNotNull { index, point ->
        if (!point.x.isFinite() || !point.y.isFinite()) null
        else index to hypot((point.x * width - tapX).toDouble(),
            if (bars) 0.0 else (point.y * height - tapY).toDouble())
    }
    val nearest = distances.minByOrNull { it.second } ?: return null
    val nearby = distances.filter { it.second <= nearest.second + overlapRadius }
        .sortedBy { it.second }.map { it.first }
    // Repeated taps can inspect overlapping series, then dismiss the marker.
    val current = nearby.indexOf(previous)
    return if (current < 0) nearest.first else nearby.getOrNull(current + 1)
}
