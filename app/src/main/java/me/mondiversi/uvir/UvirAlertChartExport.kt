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
import kotlin.math.abs

internal fun createAlertChartFile(
    context: Context,
    entry: ThresholdAlertLogEntry
): File {
    val allBars = alertChartBars(entry)
    val categories =
        listOf(false, true).filter { biological ->
            allBars.any { it.metric.isBiologicalEffect() == biological }
        }
    require(categories.isNotEmpty())
    val panels = categories.mapIndexed { index, biological ->
        createAlertChartPanelFile(
            context = context,
            entry = entry,
            biological = biological,
            includeHeader = index == 0
        )
    }
    val outputFile =
        File(
            File(context.cacheDir, "shared"),
            "${uvirAlertExportBaseName(entry)}_Chart.png"
        )
    return combineChartExportFiles(outputFile, panels)
}

internal fun alertChartGroupCount(entry: ThresholdAlertLogEntry): Int {
    val bars = alertChartBars(entry)
    return listOf(false, true).count { biological ->
        bars.any { it.metric.isBiologicalEffect() == biological }
    }
}

internal fun createAlertSeparateChartFiles(
    context: Context,
    entry: ThresholdAlertLogEntry
): List<File> {
    val bars = alertChartBars(entry)
    return listOf(false, true).mapNotNull { biological ->
        if (bars.any { it.metric.isBiologicalEffect() == biological }) {
            createAlertChartPanelFile(
                context = context,
                entry = entry,
                biological = biological,
                includeHeader = true
            )
        } else {
            null
        }
    }
}

private fun createAlertChartPanelFile(
    context: Context,
    entry: ThresholdAlertLogEntry,
    biological: Boolean,
    includeHeader: Boolean
): File {
    val exportFormatting = uvirExportFormatting(context)
    val exportContext = exportFormatting.context
    val bars =
        alertChartBars(entry).filter {
            it.metric.isBiologicalEffect() == biological
        }
    require(bars.isNotEmpty())
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
    val sectionTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(48, 48, 56)
        textSize = 34f
        typeface = android.graphics.Typeface.DEFAULT_BOLD
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
            noteLabel = exportContext.getString(R.string.share_note_label),
            sensorLabel = exportContext.getString(R.string.sensor_selector_label),
            emptyNote = exportContext.getString(R.string.no_note)
        ),
        paint = textPaint,
        maxWidth = 1420f
    )
    val headerOffset = (contextLines.size - 1) * 30f
    val height = 1125 + legendRows * 58 + headerOffset.toInt()
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawColor(Color.WHITE)

    if (includeHeader) {
        canvas.drawText(
            "Uvir ${exportContext.getString(R.string.alert_chart_title)}",
            90f,
            85f,
            titlePaint
        )
        canvas.drawText(
            chartExportIdentifierLine(
                recordLabel = exportContext.getString(R.string.alert_chart_id_label),
                recordId = entry.id,
                sessionId = entry.sessionId,
                sessionLabel = exportContext.getString(R.string.share_session_id_label)
            ) + " · " +
                csvDateTime(
                    entry.timestamp,
                    exportFormatting.dateFormat,
                    exportContext.resources.configuration.locales[0],
                    exportFormatting.timeFormat
                ),
            90f,
            130f,
            textPaint
        )
        contextLines.forEachIndexed { index, line ->
            canvas.drawText(line, 90f, 170f + index * 30f, textPaint)
        }
    }
    canvas.translate(0f, headerOffset)
    canvas.drawText(
        exportContext.getString(
            if (biological) {
                R.string.biological_effects_group_name
            } else {
                R.string.irradiance_view
            }
        ),
        90f,
        210f,
        sectionTitlePaint
    )
    canvas.drawText(
        "${exportContext.getString(R.string.alert_chart_scale)} (%)",
        90f,
        248f,
        textPaint
    )

    val left = 130f
    val top = 295f
    val right = 1510f
    val bottom = 765f
    val logExtent =
        alertThresholdCenteredLogExtent(
            bars.filterNot { it.outOfRange }.map { it.thresholdPercent }
        )
    val axisTicks = alertThresholdCenteredLogTicks(logExtent)

    repeat(5) { index ->
        val y = top + (bottom - top) * index / 4f
        canvas.drawLine(left, y, right, y, gridPaint)
        val value = axisTicks[index]
        canvas.drawText(
            formatUvirNumber(
                value,
                alertThresholdAxisFractionDigits(value),
                exportFormatting.numericFormat
            ) + "%",
            28f,
            y + 8f,
            smallPaint
        )
    }

    val thresholdY = (top + bottom) / 2f
    canvas.drawLine(left, thresholdY, right, thresholdY, thresholdPaint)
    canvas.drawText(
        exportContext.getString(R.string.alert_chart_threshold_reference),
        right - 195f,
        thresholdY - 10f,
        thresholdLabelPaint
    )

    if (bars.isNotEmpty()) {
        val slotWidth = (right - left) / bars.size
        val barWidth = (slotWidth * 0.48f).coerceAtMost(90f)
        bars.forEachIndexed { index, bar ->
            if (bar.outOfRange) return@forEachIndexed
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
                805f,
                smallPaint
            )
        }
    }

    val legendTop = 875f
    val legendColumnWidth = (right - left) / 2f
    bars.forEachIndexed { index, bar ->
        val metricLabel =
            exportContext.getString(
                thresholdAlertMetricLabelResource(bar.metric)
            )
        val delta = bar.thresholdDeltaPercent
        val deltaText =
            if (bar.outOfRange) {
                exportContext.getString(R.string.out_of_range_short)
            } else {
                (if (delta < 0.0) "−" else "+") +
                    formatUvirNumber(
                        abs(delta),
                        1,
                        exportFormatting.numericFormat
                    ) + "%"
            }
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
            "${uvirAlertExportBaseName(entry)}_" +
                if (biological) "Biological_Effects.png" else "Irradiance.png"
        )
    val cropTop = (165f + headerOffset).toInt()
    val exportBitmap =
        if (includeHeader) bitmap
        else Bitmap.createBitmap(bitmap, 0, cropTop, width, height - cropTop)
    FileOutputStream(file).use { output ->
        check(exportBitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
    }
    if (exportBitmap !== bitmap) exportBitmap.recycle()
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
