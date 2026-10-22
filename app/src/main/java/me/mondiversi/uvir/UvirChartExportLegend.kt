package me.mondiversi.uvir

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint

internal fun chartExportChannelLabel(context: Context, resource: Int?, fallback: String): String =
    sensorChannelLabel(resource?.let(context::getString) ?: fallback, resource, fallback)

internal data class UvirChartLegendEntry(val label: String, val color: Int, val value: String = "")

/** Measured cells reserve their actual line count; translated wavelengths never overlap values. */
internal class UvirChartExportLegend(
    entries: List<UvirChartLegendEntry>,
    val columns: Int,
    width: Float,
    private val paint: Paint
) {
    private val cellWidth = width / columns
    private val lineHeight = paint.fontSpacing.coerceAtLeast(26f)
    private val rows = entries.chunked(columns).map { row ->
        row.map { entry -> entry to (wrapChartExportText(entry.label, paint, cellWidth - 42f) +
            if (entry.value.isNotEmpty()) wrapChartExportText(entry.value, paint, cellWidth - 42f) else emptyList()) }
    }
    val height: Float = rows.sumOf { row -> (row.maxOf { it.second.size } * lineHeight + 12f).toDouble() }.toFloat()

    fun draw(canvas: Canvas, left: Float, top: Float) {
        val marker = Paint(Paint.ANTI_ALIAS_FLAG)
        var y = top
        rows.forEach { row ->
            row.forEachIndexed { column, (entry, lines) ->
                val x = left + column * cellWidth
                marker.color = entry.color
                canvas.drawCircle(x, y - 8f, 8f, marker)
                lines.forEachIndexed { index, line -> canvas.drawText(line, x + 20f, y + index * lineHeight, paint) }
            }
            y += row.maxOf { it.second.size } * lineHeight + 12f
        }
    }
}
