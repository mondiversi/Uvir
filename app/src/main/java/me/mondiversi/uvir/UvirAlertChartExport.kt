package me.mondiversi.uvir

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

internal fun createAlertChartFile(
    context: Context,
    entry: ThresholdAlertLogEntry
): File {
    val bars = alertChartBars(entry)
    val legendRows = (bars.size + 1) / 2
    val width = 1600
    val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(32, 32, 38)
        textSize = 48f
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }
    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(90, 90, 98)
        textSize = 25f
    }
    val smallPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(82, 82, 90)
        textSize = 22f
    }
    val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(222, 222, 228)
        strokeWidth = 2f
    }
    val thresholdPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(239, 108, 0)
        strokeWidth = 4f
        pathEffect = DashPathEffect(floatArrayOf(18f, 12f), 0f)
    }
    val thresholdLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(239, 108, 0)
        textSize = 22f
    }
    val barPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    val contextLines = wrapChartExportText(
        text = chartExportContextText(
            note = entry.note,
            sensorName = entry.sensorDisplayName,
            noteLabel = "Session note"
        ),
        paint = textPaint,
        maxWidth = 1420f
    )
    val headerOffset = (contextLines.size - 1) * 30f
    val height = 1080 + legendRows * 58 + headerOffset.toInt()
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawColor(Color.WHITE)

    canvas.drawText("Uvir value alert chart", 90f, 85f, titlePaint)
    canvas.drawText(
        chartExportIdentifierLine(
            recordLabel = "Alert",
            recordId = entry.id,
            sessionId = entry.sessionId
        ) + " · " +
            SimpleDateFormat(
                "yyyy-MM-dd HH:mm:ss",
                Locale.US
            ).format(Date(entry.timestamp)),
        90f,
        130f,
        textPaint
    )
    contextLines.forEachIndexed { index, line ->
        canvas.drawText(line, 90f, 170f + index * 30f, textPaint)
    }
    canvas.translate(0f, headerOffset)
    canvas.drawText("Threshold comparison (%)", 90f, 210f, textPaint)

    val left = 130f
    val top = 250f
    val right = 1510f
    val bottom = 720f
    val logExtent =
        alertThresholdCenteredLogExtent(
            bars.map { it.thresholdPercent }
        )
    val axisTicks = alertThresholdCenteredLogTicks(logExtent)

    repeat(5) { index ->
        val y = top + (bottom - top) * index / 4f
        canvas.drawLine(left, y, right, y, gridPaint)
        val value = axisTicks[index]
        canvas.drawText(
            String.format(
                Locale.US,
                when (alertThresholdAxisFractionDigits(value)) {
                    0 -> "%.0f%%"
                    1 -> "%.1f%%"
                    else -> "%.2f%%"
                },
                value
            ),
            28f,
            y + 8f,
            smallPaint
        )
    }

    val thresholdY = (top + bottom) / 2f
    canvas.drawLine(left, thresholdY, right, thresholdY, thresholdPaint)
    canvas.drawText(
        "Threshold · 100%",
        right - 195f,
        thresholdY - 10f,
        thresholdLabelPaint
    )

    if (bars.isNotEmpty()) {
        val slotWidth = (right - left) / bars.size
        val barWidth = (slotWidth * 0.48f).coerceAtMost(90f)
        bars.forEachIndexed { index, bar ->
            val normalized =
                alertThresholdCenteredLogFraction(
                    bar.thresholdPercent,
                    logExtent
                )
            val barHeight = (bottom - top) * normalized
            val barLeft =
                left + slotWidth * index +
                    (slotWidth - barWidth) / 2f
            barPaint.color = bar.color.toArgb()
            canvas.drawRoundRect(
                barLeft,
                bottom - barHeight,
                barLeft + barWidth,
                bottom,
                14f,
                14f,
                barPaint
            )
            val labelWidth = smallPaint.measureText(bar.shortLabel)
            canvas.drawText(
                bar.shortLabel,
                barLeft + (barWidth - labelWidth) / 2f,
                760f,
                smallPaint
            )
        }
    }

    val legendTop = 830f
    val legendColumnWidth = (right - left) / 2f
    bars.forEachIndexed { index, bar ->
        val metricLabel =
            when (bar.metric) {
                ThresholdAlertMetric.UV_TOTAL -> "Ultraviolet"
                ThresholdAlertMetric.UVC -> "UVC"
                ThresholdAlertMetric.UVB -> "UVB"
                ThresholdAlertMetric.UVA -> "UVA"
                ThresholdAlertMetric.HEV -> "HEV"
                ThresholdAlertMetric.HEB -> "HEB"
                ThresholdAlertMetric.VISIBLE_TOTAL -> "Visible light"
                ThresholdAlertMetric.VIOLET -> "Violet"
                ThresholdAlertMetric.BLUE -> "Blue"
                ThresholdAlertMetric.GREEN -> "Green"
                ThresholdAlertMetric.YELLOW -> "Yellow"
                ThresholdAlertMetric.ORANGE -> "Orange"
                ThresholdAlertMetric.RED -> "Red"
                ThresholdAlertMetric.NIR_TOTAL -> "Infrared"
                ThresholdAlertMetric.FAR_RED -> "Far-red"
                ThresholdAlertMetric.NIR -> "NIR"
                ThresholdAlertMetric.BIO_DNA_UV -> "UV DNA damage"
                ThresholdAlertMetric.BIO_UVA_PHOTOAGING -> "UVA photoaging"
                ThresholdAlertMetric.BIO_HEV_OXIDATIVE -> "HEV oxidative stress"
            }
        val delta = bar.thresholdDeltaPercent
        val deltaText =
            (if (delta < 0.0) "−" else "+") +
                String.format(Locale.US, "%.1f%%", abs(delta))
        val column = index % 2
        val row = index / 2
        val legendX = left + legendColumnWidth * column
        val legendY = legendTop + row * 58f
        barPaint.color = bar.color.toArgb()
        canvas.drawCircle(legendX, legendY - 8f, 10f, barPaint)
        canvas.drawText(
            metricLabel,
            legendX + 22f,
            legendY,
            smallPaint
        )
        canvas.drawText(
            deltaText,
            legendX + 22f,
            legendY + 26f,
            smallPaint
        )
    }

    val sharedDirectory = File(context.cacheDir, "shared").apply { mkdirs() }
    val file =
        File(
            sharedDirectory,
            "${uvirAlertExportBaseName(entry)}_Chart.png"
        )
    FileOutputStream(file).use { output ->
        check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
    }
    bitmap.recycle()
    return file
}

internal fun shareAlertChart(
    context: Context,
    entry: ThresholdAlertLogEntry
) {
    val file = createAlertChartFile(context, entry)
    val uri =
        FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    val shareIntent =
        Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_SUBJECT, "Uvir value alert chart")
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData =
                ClipData.newUri(
                    context.contentResolver,
                    file.name,
                    uri
                )
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    context.startActivity(
        Intent.createChooser(
            shareIntent,
            context.getString(R.string.alert_chart_share)
        )
    )
}
