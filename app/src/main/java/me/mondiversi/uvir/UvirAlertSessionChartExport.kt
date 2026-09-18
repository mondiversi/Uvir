package me.mondiversi.uvir

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

internal fun createAlertSessionChartFile(
    context: Context,
    sessionId: Long,
    entries: List<ThresholdAlertLogEntry>,
    metric: ThresholdAlertMetric
): File {
    val exportFormatting = uvirExportFormatting(context)
    val exportContext = exportFormatting.context
    val irradianceUnit = exportFormatting.irradianceUnit
    val sortedEntries = entries.sortedBy { it.timestamp }
    val series =
        alertSessionChartSeries(sortedEntries)
            .first { it.metric == metric }
    val percentageScale = series.usesPercentageScale()
    val metricLabel =
        exportContext.getString(
            thresholdAlertMetricLabelResource(metric)
        )
    val unit = irradianceUnit.symbol + if (metric.isBiologicalEffect()) " eq." else ""
    val chartUnit = if (percentageScale) "%" else unit
    val valueScale: (Double) -> Double = if (percentageScale) {
        { value: Double -> value }
    } else {
        irradianceUnit::fromCanonicalUwCm2
    }

    val width = 1600
    val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(32, 32, 38)
        textSize = 48f
        typeface = Typeface.DEFAULT_BOLD
    }
    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(90, 90, 98)
        textSize = 26f
    }
    val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(222, 222, 228)
        strokeWidth = 2f
    }
    val thresholdPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(245, 124, 0)
        strokeWidth = 4f
        style = Paint.Style.STROKE
        pathEffect = DashPathEffect(floatArrayOf(16f, 12f), 0f)
    }
    val thresholdLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(245, 124, 0)
        textSize = 26f
    }
    val contextLines = wrapChartExportText(
        text = chartExportContextText(
            note = sortedEntries.firstOrNull()?.note.orEmpty(),
            sensorName = exportSensorNames(sortedEntries.map { it.sensorDisplayName }),
            noteLabel = exportContext.getString(R.string.share_note_label),
            sensorLabel = exportContext.getString(R.string.sensor_selector_label),
            emptyNote = exportContext.getString(R.string.no_note)
        ),
        paint = textPaint,
        maxWidth = 1420f
    )
    val headerOffset = (contextLines.size - 1) * 30f
    val height = 1000 + headerOffset.toInt()
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawColor(Color.WHITE)

    canvas.drawText(
        "Uvir ${exportContext.getString(R.string.alert_session_chart_title)}",
        90f,
        85f,
        titlePaint
    )
    canvas.drawText(
        chartExportIdentifierLine(
            recordLabel = exportContext.getString(R.string.alert_chart_id_label),
            recordId = null,
            sessionId = sessionId,
            sessionLabel = exportContext.getString(R.string.share_session_id_label)
        ) + " · $metricLabel · " +
            exportContext.resources.getQuantityString(
                R.plurals.alert_session_chart_alert_count,
                series.points.size,
                series.points.size
            ),
        90f,
        130f,
        textPaint
    )
    contextLines.forEachIndexed { index, line ->
        canvas.drawText(line, 90f, 170f + index * 30f, textPaint)
    }
    canvas.translate(0f, headerOffset)
    canvas.drawText(
        exportContext.getString(R.string.alert_session_chart_unit, chartUnit),
        90f,
        210f,
        textPaint
    )

    val left = 130f
    val top = 250f
    val right = 1510f
    val bottom = 835f
    val startTimestamp = sortedEntries.first().timestamp
    val endTimestamp = sortedEntries.last().timestamp
    val timeSpan = (endTimestamp - startTimestamp).coerceAtLeast(1L)
    val maximum =
        series.points
            .filterNot { it.outOfRange }
            .flatMap { point ->
                listOf(
                    valueScale(point.chartValue(percentageScale)),
                    valueScale(point.chartThreshold(percentageScale))
                )
            }
            .maxOrNull()
            ?.coerceAtLeast(if (percentageScale) 100.0 else 1.0)
            ?: 1.0
    val logExtent =
        if (percentageScale) {
            alertThresholdCenteredLogExtent(
                series.points.filterNot { it.outOfRange }.map { point -> point.chartValue(true) }
            )
        } else {
            1.0
        }
    val logAxisTicks =
        if (percentageScale) {
            alertThresholdCenteredLogTicks(logExtent)
        } else {
            emptyList()
        }
    val tickFractions =
        if (startTimestamp == endTimestamp) {
            listOf(0.5f)
        } else {
            sessionChartTimeTickFractions(
                availableWidth = right - left,
                minimumTickSpacing =
                    textPaint.measureText("0000-00-00 00:00:00") + 28f
            )
        }

    repeat(5) { index ->
        val y = top + (bottom - top) * index / 4f
        canvas.drawLine(left, y, right, y, gridPaint)
        val value =
            if (percentageScale) {
                logAxisTicks[index]
            } else {
                maximum * (4 - index) / 4.0
            }
        canvas.drawText(
            if (percentageScale) {
                formatUvirNumber(
                    value,
                    alertThresholdAxisFractionDigits(value),
                    exportFormatting.numericFormat
                ) + "%"
            } else {
                formatUvirNumber(
                    value,
                    2,
                    exportFormatting.numericFormat
                )
            },
            24f,
            y + 9f,
            textPaint
        )
    }
    tickFractions.drop(1).dropLast(1).forEach { fraction ->
        val x = left + (right - left) * fraction
        canvas.drawLine(x, top, x, bottom, gridPaint)
    }

    val threshold =
        series.points.first().chartThreshold(percentageScale)
    val thresholdPath = Path()
    var hasPreviousThreshold = false
    series.points.forEachIndexed { index, point ->
        if (point.outOfRange) {
            hasPreviousThreshold = false
            return@forEachIndexed
        }
        val x =
            if (startTimestamp == endTimestamp) {
                (left + right) / 2f
            } else {
                left +
                    (right - left) *
                    (point.timestamp - startTimestamp).toFloat() /
                    timeSpan.toFloat()
            }
        val y =
            if (percentageScale) {
                (top + bottom) / 2f
            } else {
                bottom -
                    (valueScale(point.chartThreshold(false)) / maximum).toFloat() *
                    (bottom - top)
            }
        if (series.points.size == 1) {
            thresholdPath.moveTo(left, y)
            thresholdPath.lineTo(right, y)
        } else if (!hasPreviousThreshold) {
            thresholdPath.moveTo(x, y)
        } else {
            thresholdPath.lineTo(x, y)
        }
        hasPreviousThreshold = true
    }
    canvas.drawPath(thresholdPath, thresholdPaint)

    val seriesPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = series.color.toArgb()
        strokeWidth = 6f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    val valuePath = Path()
    var hasPreviousValue = false
    series.points.forEachIndexed { index, point ->
        if (point.outOfRange) {
            hasPreviousValue = false
            return@forEachIndexed
        }
        val x =
            if (startTimestamp == endTimestamp) {
                (left + right) / 2f
            } else {
                left +
                    (right - left) *
                    (point.timestamp - startTimestamp).toFloat() /
                    timeSpan.toFloat()
            }
        val y =
            if (percentageScale) {
                bottom -
                    alertThresholdCenteredLogFraction(
                        point.chartValue(true),
                        logExtent
                    ) * (bottom - top)
            } else {
                bottom -
                    (valueScale(point.chartValue(false)).coerceAtLeast(0.0) / maximum)
                        .toFloat() *
                    (bottom - top)
            }
        if (!hasPreviousValue) valuePath.moveTo(x, y) else valuePath.lineTo(x, y)
        hasPreviousValue = true
    }
    canvas.drawPath(valuePath, seriesPaint)
    seriesPaint.style = Paint.Style.FILL
    series.points.filterNot { it.outOfRange }.forEach { point ->
        val x =
            if (startTimestamp == endTimestamp) {
                (left + right) / 2f
            } else {
                left +
                    (right - left) *
                    (point.timestamp - startTimestamp).toFloat() /
                    timeSpan.toFloat()
            }
        val y =
            if (percentageScale) {
                bottom -
                    alertThresholdCenteredLogFraction(
                        point.chartValue(true),
                        logExtent
                    ) * (bottom - top)
            } else {
                bottom -
                    (valueScale(point.chartValue(false)).coerceAtLeast(0.0) / maximum)
                        .toFloat() *
                    (bottom - top)
            }
        canvas.drawCircle(x, y, 9f, seriesPaint)
    }

    tickFractions.forEach { fraction ->
        val label =
            csvDateTime(
                sessionChartTimestampAt(
                    startTimestamp,
                    endTimestamp,
                    fraction
                ),
                exportFormatting.dateFormat,
                exportContext.resources.configuration.locales[0],
                exportFormatting.timeFormat
            )
        val tickX = left + (right - left) * fraction
        val labelWidth = textPaint.measureText(label)
        val textX =
            (tickX - labelWidth / 2f).coerceIn(
                left,
                right - labelWidth
            )
        canvas.drawText(label, textX, 885f, textPaint)
    }

    canvas.drawCircle(left, 938f, 10f, seriesPaint)
    canvas.drawText(metricLabel, left + 20f, 947f, textPaint)
    val thresholdLabel =
        if (percentageScale) {
            exportContext.getString(R.string.alert_chart_threshold_reference)
        } else {
            val direction =
                if (series.direction == ThresholdAlertDirection.ABOVE) "≥" else "≤"
            exportContext.getString(
                R.string.alert_session_chart_threshold,
                direction,
                formatUvirNumber(
                    threshold,
                    3,
                    exportFormatting.numericFormat
                ),
                chartUnit
            )
        }
    canvas.drawText(
        thresholdLabel,
        840f,
        947f,
        thresholdLabelPaint
    )

    val sharedDirectory =
        File(context.cacheDir, "shared").apply { mkdirs() }
    val file =
        File(
            sharedDirectory,
            "${uvirAlertSessionExportBaseName(sessionId, sortedEntries)}_" +
                "${thresholdAlertMetricExportFileLabel(metric)}.png"
        )
    FileOutputStream(file).use { output ->
        check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
    }
    bitmap.recycle()
    return file
}

internal fun shareAlertSessionCharts(
    context: Context,
    sessionId: Long,
    entries: List<ThresholdAlertLogEntry>,
    metrics: List<ThresholdAlertMetric>
) {
    val file =
        createAlertSessionCombinedChartFile(
            context = context,
            sessionId = sessionId,
            entries = entries,
            metrics = metrics
        )
    val uri =
        FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_SUBJECT, "Uvir alert session $sessionId charts")
        putExtra(Intent.EXTRA_STREAM, uri)
        clipData = ClipData.newUri(context.contentResolver, file.name, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    context.startActivity(
        Intent.createChooser(
            shareIntent,
            context.getString(R.string.alert_session_chart_share)
        )
    )
}
