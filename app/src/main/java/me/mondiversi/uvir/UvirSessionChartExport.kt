package me.mondiversi.uvir

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.toArgb
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal fun createSessionChartFile(
    context: Context,
    sessionId: Long,
    records: List<SavedRecordDetail>,
    group: SessionChartGroup
): File {
    require(records.isNotEmpty())

    val width = 1600
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.rgb(32, 32, 38)
        textSize = 48f
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }
    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.rgb(90, 90, 98)
        textSize = 26f
    }
    val notePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.rgb(82, 82, 90)
        textSize = 22f
    }
    val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.rgb(222, 222, 228)
        strokeWidth = 2f
    }
    val noteLines =
        wrapChartExportText(
            text = chartExportContextText(
                note = sessionChartNote(records),
                sensorName = exportSensorNames(records.map { it.sensorDisplayName })
            ),
            paint = notePaint,
            maxWidth = 1420f
        )
    val headerOffset =
        (noteLines.size * 30f + 20f - 10f)
            .coerceAtLeast(40f)
    val height = 1060 + headerOffset.toInt()
    val bitmap =
        Bitmap.createBitmap(
            width,
            height,
            Bitmap.Config.ARGB_8888
        )
    val canvas = AndroidCanvas(bitmap)
    canvas.drawColor(AndroidColor.WHITE)

    canvas.drawText(
        "Uvir session acquisitions chart",
        90f,
        85f,
        titlePaint
    )
    canvas.drawText(
        chartExportIdentifierLine(
            recordLabel = "Acquisition",
            recordId = null,
            sessionId = sessionId
        ) + " · ${group.exportTitle} · ${records.size} acquisitions",
        90f,
        130f,
        textPaint
    )
    noteLines.forEachIndexed { index, line ->
        canvas.drawText(
            line,
            90f,
            170f + index * 30f,
            notePaint
        )
    }
    canvas.drawText(
        if (group.biological) {
            "Weighted irradiance (µW/cm² equiv.)"
        } else {
            "Irradiance (µW/cm²)"
        },
        90f,
        180f + headerOffset,
        textPaint
    )

    val left = 130f
    val top = 225f + headerOffset
    val right = 1510f
    val bottom = 835f + headerOffset
    val series = sessionChartSeries(records, group)
    val startTimestamp = records.first().timestamp
    val endTimestamp = records.last().timestamp
    val timeSpan =
        (endTimestamp - startTimestamp).coerceAtLeast(1L)
    val timeTickFractions =
        if (startTimestamp == endTimestamp) {
            listOf(0.5f)
        } else {
            sessionChartTimeTickFractions(
                availableWidth = right - left,
                minimumTickSpacing =
                    textPaint.measureText(
                        "0000-00-00 00:00:00"
                    ) + 28f
            )
        }
    val maximum =
        series
            .flatMap { it.values }
            .maxOrNull()
            ?.coerceAtLeast(1.0)
            ?: 1.0

    repeat(5) { index ->
        val y = top + (bottom - top) * index / 4f
        canvas.drawLine(left, y, right, y, gridPaint)
        val value = maximum * (4 - index) / 4.0
        canvas.drawText(
            String.format(Locale.US, "%.2f", value),
            24f,
            y + 9f,
            textPaint
        )
    }

    timeTickFractions
        .drop(1)
        .dropLast(1)
        .forEach { fraction ->
            val x =
                left + (right - left) * fraction
            canvas.drawLine(
                x,
                top,
                x,
                bottom,
                gridPaint
            )
        }

    series.forEach { item ->
        paint.color = item.color.toArgb()
        paint.strokeWidth = 6f
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND

        val path = android.graphics.Path()
        item.values.forEachIndexed { index, value ->
            val x =
                if (item.values.size <= 1) {
                    (left + right) / 2f
                } else {
                    left +
                            (right - left) *
                            (
                                    records[index].timestamp -
                                            startTimestamp
                                    ).toFloat() /
                            timeSpan.toFloat()
                }
            val y =
                bottom -
                        (value.coerceAtLeast(0.0) / maximum)
                            .toFloat() *
                        (bottom - top)

            if (index == 0) {
                path.moveTo(x, y)
            } else {
                path.lineTo(x, y)
            }
        }
        canvas.drawPath(path, paint)

        if (item.values.size == 1) {
            paint.style = Paint.Style.FILL
            canvas.drawCircle(
                (left + right) / 2f,
                bottom -
                        (item.values.first().coerceAtLeast(0.0) /
                                maximum).toFloat() *
                        (bottom - top),
                9f,
                paint
            )
        }
    }

    val dateFormat =
        SimpleDateFormat(
            "yyyy-MM-dd HH:mm:ss",
            Locale.US
        )
    timeTickFractions.forEach { fraction ->
        val label =
            dateFormat.format(
                Date(
                    sessionChartTimestampAt(
                        startTimestamp = startTimestamp,
                        endTimestamp = endTimestamp,
                        fraction = fraction
                    )
                )
            )
        val tickX =
            left + (right - left) * fraction
        val textX =
            (tickX - textPaint.measureText(label) / 2f)
                .coerceIn(
                    left,
                    right - textPaint.measureText(label)
                )
        canvas.drawText(
            label,
            textX,
            885f + headerOffset,
            textPaint
        )
    }

    var legendX = left
    var legendY = 950f + headerOffset
    series.forEach { item ->
        paint.color = item.color.toArgb()
        paint.style = Paint.Style.FILL
        canvas.drawCircle(
            legendX,
            legendY - 9f,
            10f,
            paint
        )
        canvas.drawText(
            item.label,
            legendX + 20f,
            legendY,
            textPaint
        )
        legendX +=
            40f + textPaint.measureText(item.label) + 55f
        if (legendX > 1380f) {
            legendX = left
            legendY += 34f
        }
    }

    val sharedDirectory =
        File(context.cacheDir, "shared").apply {
            mkdirs()
        }
    val file =
        File(
            sharedDirectory,
            "${uvirSessionAcquisitionsExportBaseName(sessionId, records)}_" +
                when (group) {
                    SessionChartGroup.UV -> "UV"
                    SessionChartGroup.VISIBLE -> "Visible_Light"
                    SessionChartGroup.FAR_RED_NIR -> "Infrared"
                    SessionChartGroup.BIOLOGICAL -> "Biological_Effects"
                } +
                ".png"
        )

    FileOutputStream(file).use { output ->
        check(
            bitmap.compress(
                Bitmap.CompressFormat.PNG,
                100,
                output
            )
        )
    }
    bitmap.recycle()

    return file
}
