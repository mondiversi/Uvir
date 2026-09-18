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
import java.util.Locale

internal fun createSessionChartFile(
    context: Context,
    sessionId: Long,
    records: List<SavedRecordDetail>,
    group: SessionChartGroup,
    includeHeader: Boolean = true,
    variantIndex: Int? = null,
    variantCount: Int? = null,
    exportBaseName: String = uvirSessionAcquisitionsExportBaseName(sessionId, records)
): File {
    require(records.isNotEmpty())
    val exportFormatting = uvirExportFormatting(context)
    val exportContext = exportFormatting.context
    val irradianceUnit = exportFormatting.irradianceUnit
    val valueScale: (Double) -> Double = irradianceUnit::fromCanonicalUwCm2

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
    val sectionTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.rgb(48, 48, 56)
        textSize = 34f
        typeface = android.graphics.Typeface.DEFAULT_BOLD
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
                note = sessionChartNote(
                    records,
                    exportContext.getString(R.string.no_note)
                ),
                sensorName = exportSensorNames(records.map { it.sensorDisplayName }),
                noteLabel = exportContext.getString(R.string.share_note_label),
                sensorLabel = exportContext.getString(R.string.sensor_selector_label),
                emptyNote = exportContext.getString(R.string.no_note)
            ),
            paint = notePaint,
            maxWidth = 1420f
        )
    val headerOffset =
        (noteLines.size * 30f + 20f - 10f)
            .coerceAtLeast(40f)
    val spansMultipleDays =
        sessionChartSpansMultipleDays(records.first().timestamp, records.last().timestamp)
    val timeAxisExtra = if (spansMultipleDays) 30 else 0
    val height = 1145 + timeAxisExtra + headerOffset.toInt()
    val bitmap =
        Bitmap.createBitmap(
            width,
            height,
            Bitmap.Config.ARGB_8888
        )
    val canvas = AndroidCanvas(bitmap)
    canvas.drawColor(AndroidColor.WHITE)

    if (includeHeader) {
        canvas.drawText(
            "Uvir ${exportContext.getString(R.string.session_chart_title)}",
            90f,
            85f,
            titlePaint
        )
        val sessionDateRange =
            csvDateTime(
                records.first().timestamp,
                exportFormatting.dateFormat,
                exportContext.resources.configuration.locales[0],
                exportFormatting.timeFormat
            ) + " – " +
                csvDateTime(
                    records.last().timestamp,
                    exportFormatting.dateFormat,
                    exportContext.resources.configuration.locales[0],
                    exportFormatting.timeFormat
                )
        canvas.drawText(
            chartExportIdentifierLine(
                recordLabel = exportContext.getString(R.string.share_measurement_id_label),
                recordId = null,
                sessionId = sessionId,
                sessionLabel = exportContext.getString(R.string.share_session_id_label)
            ) +
                if (
                    variantIndex != null &&
                    variantCount != null &&
                    variantCount > 1
                ) {
                    " · " + exportContext.getString(
                        R.string.session_variant_heading,
                        variantIndex,
                        variantCount
                    )
                } else {
                    ""
                } + " · " +
                exportContext.resources.getQuantityString(
                    R.plurals.session_chart_acquisition_count,
                    records.size,
                    records.size
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
        noteLines.forEachIndexed { index, line ->
            canvas.drawText(
                line,
                90f,
                200f + index * 30f,
                notePaint
            )
        }
    }
    canvas.drawText(
        exportContext.getString(group.titleResource),
        90f,
        240f + headerOffset,
        sectionTitlePaint
    )
    canvas.drawText(
        exportContext.getString(
            if (group.biological) {
                R.string.session_chart_unit_biological
            } else {
                R.string.session_chart_unit
            }
        ).withUvirIrradianceUnit(irradianceUnit),
        90f,
        278f + headerOffset,
        textPaint
    )

    val left = 130f
    val top = 310f + headerOffset
    val right = 1510f
    val bottom = 920f + headerOffset
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
        }
    val maximum =
        series
            .flatMap { item ->
                item.values.filterIndexed { index, _ -> item.outOfRange.getOrNull(index) != true }
            }
            .map(valueScale)
            .maxOrNull()
            ?.coerceAtLeast(1.0)
            ?: 1.0

    repeat(5) { index ->
        val y = top + (bottom - top) * index / 4f
        canvas.drawLine(left, y, right, y, gridPaint)
        val value = maximum * (4 - index) / 4.0
        canvas.drawText(
            formatUvirNumber(
                value,
                irradianceUnit.displayFractionDigits(2),
                exportFormatting.numericFormat
            ),
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
        var hasPreviousPoint = false
        item.values.forEachIndexed { index, value ->
            if (item.outOfRange.getOrNull(index) == true) {
                hasPreviousPoint = false
                return@forEachIndexed
            }
            val displayValue = valueScale(value)
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
                        (displayValue.coerceAtLeast(0.0) / maximum)
                            .toFloat() *
                        (bottom - top)

            if (!hasPreviousPoint) {
                path.moveTo(x, y)
            } else {
                path.lineTo(x, y)
            }
            hasPreviousPoint = true
        }
        canvas.drawPath(path, paint)

        if (item.values.size == 1 && item.outOfRange.firstOrNull() != true) {
            paint.style = Paint.Style.FILL
            canvas.drawCircle(
                (left + right) / 2f,
                bottom -
                        (valueScale(item.values.first()).coerceAtLeast(0.0) /
                                maximum).toFloat() *
                        (bottom - top),
                9f,
                paint
            )
        }
    }

    timeTickFractions.forEach { fraction ->
        val timestamp =
            sessionChartTimestampAt(
                startTimestamp = startTimestamp,
                endTimestamp = endTimestamp,
                fraction = fraction
            )
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
            970f + headerOffset,
            textPaint
        )
        if (spansMultipleDays) {
            val timeTextX =
                (tickX - textPaint.measureText(timeLabel) / 2f)
                    .coerceIn(left, right - textPaint.measureText(timeLabel))
            canvas.drawText(
                timeLabel,
                timeTextX,
                1000f + headerOffset,
                textPaint
            )
        }
    }

    var legendX = left
    var legendY = 1035f + timeAxisExtra + headerOffset
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
            item.displayLabelResource?.let { exportContext.getString(it) } ?: item.label,
            legendX + 20f,
            legendY,
            textPaint
        )
        legendX +=
            40f +
                textPaint.measureText(
                    item.displayLabelResource?.let { exportContext.getString(it) }
                        ?: item.label
                ) + 55f
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
            exportBaseName +
                if (
                    variantIndex != null &&
                    variantCount != null &&
                    variantCount > 1
                ) {
                    "_Var${String.format(Locale.US, "%02d", variantIndex)}"
                } else {
                    ""
                } + "_" +
                when (group) {
                    SessionChartGroup.UV -> "UV"
                    SessionChartGroup.VISIBLE -> "Visible_Light"
                    SessionChartGroup.FAR_RED_NIR -> "Infrared"
                    SessionChartGroup.BIOLOGICAL -> "Biological_Effects"
                } +
                ".png"
        )

    val cropTop = (200f + headerOffset).toInt()
    val exportBitmap =
        if (includeHeader) bitmap
        else Bitmap.createBitmap(bitmap, 0, cropTop, width, height - cropTop)
    FileOutputStream(file).use { output ->
        check(
            exportBitmap.compress(
                Bitmap.CompressFormat.PNG,
                100,
                output
            )
        )
    }
    if (exportBitmap !== bitmap) exportBitmap.recycle()
    bitmap.recycle()

    return file
}

internal fun createSessionCombinedChartFile(
    context: Context,
    sessionId: Long,
    records: List<SavedRecordDetail>,
    groups: List<SessionChartGroup> = SessionChartGroup.entries,
    variantIndex: Int? = null,
    variantCount: Int? = null,
    exportBaseName: String = uvirSessionAcquisitionsExportBaseName(sessionId, records)
): File {
    require(records.isNotEmpty())
    val selectedGroups = groups.distinct()
    require(selectedGroups.isNotEmpty())
    val panelFiles =
        selectedGroups.mapIndexed { index, group ->
            createSessionChartFile(
                context = context,
                sessionId = sessionId,
                records = records,
                group = group,
                includeHeader = index == 0,
                variantIndex = variantIndex,
                variantCount = variantCount,
                exportBaseName = exportBaseName
            )
        }
    val outputFile =
        File(
            File(context.cacheDir, "shared"),
            exportBaseName +
                if (
                    variantIndex != null &&
                    variantCount != null &&
                    variantCount > 1
                ) {
                    "_Var${String.format(Locale.US, "%02d", variantIndex)}"
                } else {
                    ""
                } + "_Chart.png"
        )
    return combineChartExportFiles(outputFile, panelFiles)
}

internal fun createSessionVariantChartFiles(
    context: Context,
    sessionId: Long,
    records: List<SavedRecordDetail>,
    mode: UvirChartExportMode = UvirChartExportMode.COMBINED
): List<File> {
    val variantGroups = acquisitionVariantGroups(records)
    val exportBaseName = uvirSessionAcquisitionsExportBaseName(sessionId, records)
    return if (variantGroups.isEmpty()) {
        when (mode) {
            UvirChartExportMode.COMBINED ->
                listOf(createSessionCombinedChartFile(context, sessionId, records))
            UvirChartExportMode.SEPARATE ->
                SessionChartGroup.entries.map { group ->
                    createSessionChartFile(
                        context = context,
                        sessionId = sessionId,
                        records = records,
                        group = group,
                        includeHeader = true,
                        exportBaseName = exportBaseName
                    )
                }
        }
    } else {
        variantGroups.flatMap { variant ->
            when (mode) {
                UvirChartExportMode.COMBINED ->
                    listOf(
                        createSessionCombinedChartFile(
                            context = context,
                            sessionId = sessionId,
                            records = variant.records,
                            variantIndex = variant.index,
                            variantCount = variant.count,
                            exportBaseName = exportBaseName
                        )
                    )
                UvirChartExportMode.SEPARATE ->
                    SessionChartGroup.entries.map { group ->
                        createSessionChartFile(
                            context = context,
                            sessionId = sessionId,
                            records = variant.records,
                            group = group,
                            includeHeader = true,
                            variantIndex = variant.index,
                            variantCount = variant.count,
                            exportBaseName = exportBaseName
                        )
                    }
            }
        }
    }
}

internal fun acquisitionSessionChartFileCount(
    records: List<SavedRecordDetail>,
    mode: UvirChartExportMode
): Int {
    val variants = acquisitionVariantGroups(records).size.coerceAtLeast(1)
    return variants *
        if (mode == UvirChartExportMode.SEPARATE) {
            SessionChartGroup.entries.size
        } else {
            1
        }
}
