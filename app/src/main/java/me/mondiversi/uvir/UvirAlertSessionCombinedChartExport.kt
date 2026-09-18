package me.mondiversi.uvir

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import androidx.compose.ui.graphics.toArgb
import java.io.File
import java.io.FileOutputStream

internal fun createAlertSessionCombinedChartFile(
    context: Context,
    sessionId: Long,
    entries: List<ThresholdAlertLogEntry>,
    metrics: List<ThresholdAlertMetric> =
        alertSessionChartSeries(entries).map { it.metric }
): File {
    require(entries.isNotEmpty())
    val selectedMetrics = metrics.toSet()
    val selectedSeries =
        alertSessionChartSeries(entries).filter { it.metric in selectedMetrics }
    require(selectedSeries.isNotEmpty())
    val categories =
        listOf(false, true).filter { biological ->
            selectedSeries.any { it.metric.isBiologicalEffect() == biological }
        }
    val panels = categories.mapIndexed { index, biological ->
        createAlertSessionCategoryPanel(
            context = context,
            sessionId = sessionId,
            entries = entries,
            series = selectedSeries.filter {
                it.metric.isBiologicalEffect() == biological
            },
            biological = biological,
            includeHeader = index == 0
        )
    }
    val outputFile =
        File(
            File(context.cacheDir, "shared"),
            "${uvirAlertSessionExportBaseName(sessionId, entries)}_Charts.png"
        )
    return combineChartExportFiles(outputFile, panels)
}

internal fun alertSessionChartGroupCount(
    entries: List<ThresholdAlertLogEntry>
): Int {
    val series = alertSessionChartSeries(entries)
    return listOf(false, true).count { biological ->
        series.any { it.metric.isBiologicalEffect() == biological }
    }
}

internal fun createAlertSessionSeparateChartFiles(
    context: Context,
    sessionId: Long,
    entries: List<ThresholdAlertLogEntry>
): List<File> {
    val series = alertSessionChartSeries(entries)
    return listOf(false, true).mapNotNull { biological ->
        val categorySeries =
            series.filter { it.metric.isBiologicalEffect() == biological }
        if (categorySeries.isEmpty()) {
            null
        } else {
            createAlertSessionCategoryPanel(
                context = context,
                sessionId = sessionId,
                entries = entries,
                series = categorySeries,
                biological = biological,
                includeHeader = true
            )
        }
    }
}

private fun createAlertSessionCategoryPanel(
    context: Context,
    sessionId: Long,
    entries: List<ThresholdAlertLogEntry>,
    series: List<AlertSessionChartSeries>,
    biological: Boolean,
    includeHeader: Boolean
): File {
    require(series.isNotEmpty())
    val exportFormatting = uvirExportFormatting(context)
    val exportContext = exportFormatting.context
    val irradianceUnit = exportFormatting.irradianceUnit
    val sortedEntries = entries.sortedBy { it.timestamp }
    val percentageScale = series.all { it.usesPercentageScale() }
    val valueScale: (Double) -> Double =
        if (percentageScale) ({ value -> value })
        else irradianceUnit::fromCanonicalUwCm2
    val chartValues =
        series.flatMap { item ->
            item.points.filterNot { it.outOfRange }.map { point ->
                valueScale(point.chartValue(percentageScale))
            }
        }
    val chartThresholds =
        series.flatMap { item ->
            item.points.filterNot { it.outOfRange }.map { point ->
                valueScale(point.chartThreshold(percentageScale))
            }
        }
    val maximum =
        (chartValues + chartThresholds)
            .maxOrNull()
            ?.coerceAtLeast(if (percentageScale) 100.0 else 1.0)
            ?: 1.0
    val logExtent =
        if (percentageScale) alertThresholdCenteredLogExtent(chartValues) else 1.0
    val logTicks =
        if (percentageScale) alertThresholdCenteredLogTicks(logExtent) else emptyList()

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
    val sectionTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(48, 48, 56)
        textSize = 34f
        typeface = Typeface.DEFAULT_BOLD
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
        color = Color.rgb(245, 124, 0)
        strokeWidth = 4f
        style = Paint.Style.STROKE
        pathEffect = DashPathEffect(floatArrayOf(16f, 12f), 0f)
    }
    val thresholdLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(245, 124, 0)
        textSize = 24f
    }
    val contextLines =
        wrapChartExportText(
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
    val legendRows = (series.size + 1) / 2
    val spansMultipleDays =
        sessionChartSpansMultipleDays(sortedEntries.first().timestamp, sortedEntries.last().timestamp)
    val timeAxisExtra = if (spansMultipleDays) 30 else 0
    val height = 1155 + timeAxisExtra + legendRows * 48 + headerOffset.toInt()
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawColor(Color.WHITE)

    val categoryTitle =
        exportContext.getString(
            if (biological) R.string.biological_effects_group_name else R.string.irradiance_view
        )
    if (includeHeader) {
        canvas.drawText(
            "Uvir ${exportContext.getString(R.string.alert_session_chart_title)}",
            90f,
            85f,
            titlePaint
        )
        val sessionDateRange =
            csvDateTime(
                sortedEntries.first().timestamp,
                exportFormatting.dateFormat,
                exportContext.resources.configuration.locales[0],
                exportFormatting.timeFormat
            ) + " – " +
                csvDateTime(
                    sortedEntries.last().timestamp,
                    exportFormatting.dateFormat,
                    exportContext.resources.configuration.locales[0],
                    exportFormatting.timeFormat
                )
        canvas.drawText(
            chartExportIdentifierLine(
                recordLabel = exportContext.getString(R.string.alert_chart_id_label),
                recordId = null,
                sessionId = sessionId,
                sessionLabel = exportContext.getString(R.string.share_session_id_label)
            ) + " · " +
                exportContext.resources.getQuantityString(
                    R.plurals.alert_session_chart_alert_count,
                    sortedEntries.size,
                    sortedEntries.size
                ),
            90f,
            130f,
            textPaint
        )
        canvas.drawText(
            sessionDateRange,
            90f,
            165f,
            textPaint
        )
        contextLines.forEachIndexed { index, line ->
            canvas.drawText(line, 90f, 200f + index * 30f, textPaint)
        }
    }
    canvas.translate(0f, headerOffset)
    canvas.drawText(
        categoryTitle,
        90f,
        240f,
        sectionTitlePaint
    )
    canvas.drawText(
        if (percentageScale) {
            exportContext.getString(R.string.alert_chart_percentage_value)
        } else {
            exportContext.getString(
                R.string.alert_session_chart_unit,
                irradianceUnit.symbol + if (biological) " eq." else ""
            )
        },
        90f,
        278f,
        textPaint
    )

    val left = 130f
    val top = 325f
    val right = 1510f
    val bottom = 910f
    val startTimestamp = sortedEntries.first().timestamp
    val endTimestamp = sortedEntries.last().timestamp
    val timeSpan = (endTimestamp - startTimestamp).coerceAtLeast(1L)
    val tickFractions =
        if (startTimestamp == endTimestamp) listOf(0.5f)
        else sessionChartTimeTickFractions(
            availableWidth = right - left,
            minimumTickSpacing =
                maxOf(
                    textPaint.measureText(
                        formatUvirTimeOnly(
                            startTimestamp,
                            exportFormatting.timeFormat,
                            exportContext.resources.configuration.locales[0]
                        )
                    ),
                    if (spansMultipleDays) {
                        textPaint.measureText(
                            formatUvirDateOnly(
                                startTimestamp,
                                exportFormatting.dateFormat,
                                exportContext.resources.configuration.locales[0]
                            )
                        )
                    } else {
                        0f
                    }
                ) + 28f
        )

    repeat(5) { index ->
        val y = top + (bottom - top) * index / 4f
        canvas.drawLine(left, y, right, y, gridPaint)
        val value =
            if (percentageScale) logTicks[index]
            else maximum * (4 - index) / 4.0
        canvas.drawText(
            formatUvirNumber(
                value,
                if (percentageScale) alertThresholdAxisFractionDigits(value)
                else irradianceUnit.displayFractionDigits(2),
                exportFormatting.numericFormat
            ) + if (percentageScale) "%" else "",
            24f,
            y + 9f,
            smallPaint
        )
    }
    tickFractions.drop(1).dropLast(1).forEach { fraction ->
        val x = left + (right - left) * fraction
        canvas.drawLine(x, top, x, bottom, gridPaint)
    }

    if (percentageScale) {
        canvas.drawLine(left, (top + bottom) / 2f, right, (top + bottom) / 2f, thresholdPaint)
    } else {
        series.forEach { item ->
            val path = Path()
            var previous = false
            item.points.forEach { point ->
                if (point.outOfRange) {
                    previous = false
                    return@forEach
                }
                val x = chartExportX(point.timestamp, startTimestamp, endTimestamp, timeSpan, left, right)
                val y = bottom -
                    (valueScale(point.threshold) / maximum).toFloat() * (bottom - top)
                if (!previous) path.moveTo(x, y) else path.lineTo(x, y)
                previous = true
            }
            canvas.drawPath(path, thresholdPaint)
        }
    }

    series.forEach { item ->
        val seriesPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = item.color.toArgb()
            strokeWidth = 6f
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
        }
        val path = Path()
        var previous = false
        item.points.forEach { point ->
            if (point.outOfRange) {
                previous = false
                return@forEach
            }
            val value = valueScale(point.chartValue(percentageScale))
            val x = chartExportX(point.timestamp, startTimestamp, endTimestamp, timeSpan, left, right)
            val y = chartExportY(value, percentageScale, logExtent, maximum, top, bottom)
            if (!previous) path.moveTo(x, y) else path.lineTo(x, y)
            previous = true
        }
        canvas.drawPath(path, seriesPaint)
        seriesPaint.style = Paint.Style.FILL
        item.points.filterNot { it.outOfRange }.forEach { point ->
            val value = valueScale(point.chartValue(percentageScale))
            canvas.drawCircle(
                chartExportX(point.timestamp, startTimestamp, endTimestamp, timeSpan, left, right),
                chartExportY(value, percentageScale, logExtent, maximum, top, bottom),
                8f,
                seriesPaint
            )
        }
    }

    tickFractions.forEach { fraction ->
        val timestamp = sessionChartTimestampAt(startTimestamp, endTimestamp, fraction)
        val dateLabel =
            formatUvirDateOnly(
                timestamp,
                exportFormatting.dateFormat,
                exportContext.resources.configuration.locales[0]
            )
        val timeLabel =
            formatUvirTimeOnly(
                timestamp,
                exportFormatting.timeFormat,
                exportContext.resources.configuration.locales[0]
            )
        val label = if (spansMultipleDays) dateLabel else timeLabel
        val x = left + (right - left) * fraction
        val labelWidth = textPaint.measureText(label)
        canvas.drawText(label, (x - labelWidth / 2f).coerceIn(left, right - labelWidth), 960f, textPaint)
        if (spansMultipleDays) {
            val timeWidth = textPaint.measureText(timeLabel)
            canvas.drawText(
                timeLabel,
                (x - timeWidth / 2f).coerceIn(left, right - timeWidth),
                990f,
                textPaint
            )
        }
    }

    val legendTop = 1020f + timeAxisExtra
    val legendColumnWidth = (right - left) / 2f
    series.forEachIndexed { index, item ->
        val x = left + (index % 2) * legendColumnWidth
        val y = legendTop + (index / 2) * 48f
        val seriesPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = item.color.toArgb() }
        canvas.drawCircle(x, y - 8f, 10f, seriesPaint)
        canvas.drawText(
            exportContext.getString(thresholdAlertMetricLabelResource(item.metric)),
            x + 22f,
            y,
            smallPaint
        )
    }
    if (percentageScale) {
        canvas.drawText(
            exportContext.getString(R.string.alert_chart_threshold_reference),
            1110f,
            1000f + timeAxisExtra,
            thresholdLabelPaint
        )
    }

    val outputFile =
        File(
            File(context.cacheDir, "shared"),
            "${uvirAlertSessionExportBaseName(sessionId, sortedEntries)}_" +
                if (biological) "Biological_Effects.png" else "Irradiance.png"
        )
    val cropTop = (195f + headerOffset).toInt()
    val exportBitmap =
        if (includeHeader) bitmap
        else Bitmap.createBitmap(bitmap, 0, cropTop, width, height - cropTop)
    FileOutputStream(outputFile).use { output ->
        check(exportBitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
    }
    if (exportBitmap !== bitmap) exportBitmap.recycle()
    bitmap.recycle()
    return outputFile
}

private fun chartExportX(
    timestamp: Long,
    startTimestamp: Long,
    endTimestamp: Long,
    timeSpan: Long,
    left: Float,
    right: Float
): Float =
    if (startTimestamp == endTimestamp) (left + right) / 2f
    else left + (right - left) *
        (timestamp - startTimestamp).toFloat() / timeSpan.toFloat()

private fun chartExportY(
    value: Double,
    percentageScale: Boolean,
    logExtent: Double,
    maximum: Double,
    top: Float,
    bottom: Float
): Float =
    if (percentageScale) {
        bottom - alertThresholdCenteredLogFraction(value, logExtent) * (bottom - top)
    } else {
        bottom - (value.coerceAtLeast(0.0) / maximum).toFloat() * (bottom - top)
    }
