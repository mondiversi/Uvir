package me.mondiversi.uvir

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal fun createAlertSessionChartFile(
    context: Context,
    sessionId: Long,
    entries: List<ThresholdAlertLogEntry>,
    metric: ThresholdAlertMetric
): File {
    val sortedEntries = entries.sortedBy { it.timestamp }
    val series =
        alertSessionChartSeries(sortedEntries)
            .first { it.metric == metric }
    val percentageScale = series.usesPercentageScale()
    val exportContext =
        context.createConfigurationContext(
            Configuration(context.resources.configuration).apply {
                setLocale(Locale.ENGLISH)
                setLayoutDirection(Locale.ENGLISH)
            }
        )
    val metricLabel =
        exportContext.getString(
            thresholdAlertMetricLabelResource(metric)
        )
    val unit =
        if (metric.isBiologicalEffect()) {
            "µW/cm² equiv."
        } else {
            "µW/cm²"
        }
    val chartUnit = if (percentageScale) "%" else unit

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
            noteLabel = "Session note"
        ),
        paint = textPaint,
        maxWidth = 1420f
    )
    val headerOffset = (contextLines.size - 1) * 30f
    val height = 1000 + headerOffset.toInt()
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawColor(Color.WHITE)

    canvas.drawText("Uvir alert session chart", 90f, 85f, titlePaint)
    canvas.drawText(
        chartExportIdentifierLine(
            recordLabel = "Alert",
            recordId = null,
            sessionId = sessionId
        ) + " · $metricLabel · ${series.points.size} alerts",
        90f,
        130f,
        textPaint
    )
    contextLines.forEachIndexed { index, line ->
        canvas.drawText(line, 90f, 170f + index * 30f, textPaint)
    }
    canvas.translate(0f, headerOffset)
    canvas.drawText("Value ($chartUnit)", 90f, 210f, textPaint)

    val left = 130f
    val top = 250f
    val right = 1510f
    val bottom = 835f
    val startTimestamp = sortedEntries.first().timestamp
    val endTimestamp = sortedEntries.last().timestamp
    val timeSpan = (endTimestamp - startTimestamp).coerceAtLeast(1L)
    val maximum =
        series.points
            .flatMap { point ->
                listOf(
                    point.chartValue(percentageScale),
                    point.chartThreshold(percentageScale)
                )
            }
            .maxOrNull()
            ?.coerceAtLeast(if (percentageScale) 100.0 else 1.0)
            ?: 1.0
    val logExtent =
        if (percentageScale) {
            alertThresholdCenteredLogExtent(
                series.points.map { point -> point.chartValue(true) }
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
                String.format(
                    Locale.US,
                    when (alertThresholdAxisFractionDigits(value)) {
                        0 -> "%.0f%%"
                        1 -> "%.1f%%"
                        else -> "%.2f%%"
                    },
                    value
                )
            } else {
                String.format(Locale.US, "%.2f", value)
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
    series.points.forEachIndexed { index, point ->
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
                    (point.chartThreshold(false) / maximum).toFloat() *
                    (bottom - top)
            }
        if (series.points.size == 1) {
            thresholdPath.moveTo(left, y)
            thresholdPath.lineTo(right, y)
        } else if (index == 0) {
            thresholdPath.moveTo(x, y)
        } else {
            thresholdPath.lineTo(x, y)
        }
    }
    canvas.drawPath(thresholdPath, thresholdPaint)

    val seriesPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = series.color.toArgb()
        strokeWidth = 6f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    val valuePath = Path()
    series.points.forEachIndexed { index, point ->
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
                    (point.chartValue(false).coerceAtLeast(0.0) / maximum)
                        .toFloat() *
                    (bottom - top)
            }
        if (index == 0) valuePath.moveTo(x, y) else valuePath.lineTo(x, y)
    }
    canvas.drawPath(valuePath, seriesPaint)
    seriesPaint.style = Paint.Style.FILL
    series.points.forEach { point ->
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
                    (point.chartValue(false).coerceAtLeast(0.0) / maximum)
                        .toFloat() *
                    (bottom - top)
            }
        canvas.drawCircle(x, y, 9f, seriesPaint)
    }

    val dateFormat =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
    tickFractions.forEach { fraction ->
        val label =
            dateFormat.format(
                Date(
                    sessionChartTimestampAt(
                        startTimestamp,
                        endTimestamp,
                        fraction
                    )
                )
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
            "Threshold · 100%"
        } else {
            val direction =
                if (series.direction == ThresholdAlertDirection.ABOVE) "≥" else "≤"
            "Threshold $direction ${String.format(Locale.US, "%.3f", threshold)} $chartUnit"
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
    require(entries.isNotEmpty())
    val availableMetrics =
        alertSessionChartSeries(entries).map { it.metric }.toSet()
    val selectedMetrics =
        metrics.distinct().filter { it in availableMetrics }
    require(selectedMetrics.isNotEmpty())

    val files =
        selectedMetrics.map { metric ->
            createAlertSessionChartFile(
                context = context,
                sessionId = sessionId,
                entries = entries,
                metric = metric
            )
        }
    val uris =
        ArrayList(
            files.map { file ->
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
            }
        )
    val shareIntent =
        Intent(
            if (uris.size == 1) {
                Intent.ACTION_SEND
            } else {
                Intent.ACTION_SEND_MULTIPLE
            }
        ).apply {
            type = "image/png"
            putExtra(
                Intent.EXTRA_SUBJECT,
                "Uvir alert session $sessionId chart"
            )
            if (uris.size == 1) {
                putExtra(Intent.EXTRA_STREAM, uris.first())
            } else {
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            }
            clipData =
                ClipData.newUri(
                    context.contentResolver,
                    files.first().name,
                    uris.first()
                ).apply {
                    uris.drop(1).forEach { uri ->
                        addItem(ClipData.Item(uri))
                    }
                }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

    context.startActivity(
        Intent.createChooser(
            shareIntent,
            context.getString(R.string.alert_session_chart_share)
        )
    )
}
