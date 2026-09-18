package me.mondiversi.uvir

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.core.content.FileProvider
import androidx.compose.ui.graphics.toArgb
import java.io.File
import java.io.FileOutputStream

internal fun createAcquisitionChartFiles(
    context: Context,
    record: SavedRecordDetail,
    group: AcquisitionChartGroup,
    includeHeader: Boolean = true
): List<File> {
    val exportContext = uvirExportFormatting(context).context
    val bars = acquisitionChartBars(record.sample, group)
    var shouldIncludeHeader = includeHeader
    return AcquisitionChartSection.entries.mapNotNull { section ->
        bars.filter { it.section == section }
            .takeIf { it.isNotEmpty() }
            ?.let { sectionBars ->
                createAcquisitionChartFile(
                    context = exportContext,
                    record = record,
                    group = group,
                    section = section,
                    bars = sectionBars,
                    includeHeader = shouldIncludeHeader
                ).also { shouldIncludeHeader = false }
            }
    }
}

internal fun createAcquisitionCombinedChartFile(
    context: Context,
    record: SavedRecordDetail,
    groups: List<AcquisitionChartGroup> = AcquisitionChartGroup.entries
): File {
    val selectedGroups = groups.distinct()
    require(selectedGroups.isNotEmpty())
    var includeHeader = true
    val panelFiles =
        selectedGroups.flatMap { group ->
            createAcquisitionChartFiles(
                context = context,
                record = record,
                group = group,
                includeHeader = includeHeader
            ).also {
                if (it.isNotEmpty()) includeHeader = false
            }
        }
    val outputFile =
        File(
            File(context.cacheDir, "shared"),
            "${uvirAcquisitionExportBaseName(record)}_Charts.png"
        )
    return combineChartExportFiles(outputFile, panelFiles)
}

internal fun acquisitionChartGroupCount(
    record: SavedRecordDetail,
    groups: List<AcquisitionChartGroup> = AcquisitionChartGroup.entries
): Int =
    groups.distinct().sumOf { group ->
        val bars = acquisitionChartBars(record.sample, group)
        AcquisitionChartSection.entries.count { section ->
            bars.any { it.section == section }
        }
    }

internal fun createAcquisitionSeparateChartFiles(
    context: Context,
    record: SavedRecordDetail,
    groups: List<AcquisitionChartGroup> = AcquisitionChartGroup.entries
): List<File> =
    groups.distinct().flatMap { group ->
        val bars = acquisitionChartBars(record.sample, group)
        AcquisitionChartSection.entries.mapNotNull { section ->
            bars.filter { it.section == section }
                .takeIf { it.isNotEmpty() }
                ?.let { sectionBars ->
                    createAcquisitionChartFile(
                        context = context,
                        record = record,
                        group = group,
                        section = section,
                        bars = sectionBars,
                        includeHeader = true
                    )
                }
        }
    }

private fun createAcquisitionChartFile(
    context: Context,
    record: SavedRecordDetail,
    group: AcquisitionChartGroup,
    section: AcquisitionChartSection,
    bars: List<AcquisitionChartBar>,
    includeHeader: Boolean
): File {
    val exportFormatting = uvirExportFormatting(context)
    val exportContext = exportFormatting.context
    val irradianceUnit = exportFormatting.irradianceUnit
    val valueScale: (Double) -> Double = irradianceUnit::fromCanonicalUwCm2
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
    val barPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    val noteLines =
        wrapChartExportText(
            text = chartExportContextText(
                note = acquisitionDisplayNote(
                    note = record.note,
                    automatic = record.automatic,
                    sessionSequence = record.sessionSequence,
                    emptyNote = exportContext.getString(R.string.no_note)
                ),
                sensorName = record.sensorDisplayName,
                noteLabel = exportContext.getString(R.string.share_note_label),
                sensorLabel = exportContext.getString(R.string.sensor_selector_label),
                emptyNote = exportContext.getString(R.string.no_note)
            ),
            paint = smallPaint,
            maxWidth = 1420f
        )
    val headerOffset =
        (noteLines.size * 30f + 20f - 10f)
            .coerceAtLeast(40f)
    val sections = listOf(section to bars)
    val sectionHeights =
        sections.sumOf { (_, sectionBars) ->
            val legendRows = (sectionBars.size + 3) / 4
            500 + legendRows * 58
        }
    val height =
        225 + headerOffset.toInt() + sectionHeights + 50
    val bitmap =
        Bitmap.createBitmap(
            width,
            height,
            Bitmap.Config.ARGB_8888
        )
    val canvas = Canvas(bitmap)
    canvas.drawColor(Color.WHITE)

    if (includeHeader) {
        canvas.drawText(
            "Uvir ${exportContext.getString(R.string.acquisition_chart_title)}",
            90f,
            85f,
            titlePaint
        )
        canvas.drawText(
            chartExportIdentifierLine(
                recordLabel = exportContext.getString(R.string.share_measurement_id_label),
                recordId = record.id,
                sessionId = record.sessionId,
                sessionLabel = exportContext.getString(R.string.share_session_id_label)
            ) + " · " +
                csvDateTime(
                    record.timestamp,
                    exportFormatting.dateFormat,
                    exportContext.resources.configuration.locales[0],
                    exportFormatting.timeFormat
                ),
            90f,
            130f,
            textPaint
        )
        noteLines.forEachIndexed { index, line ->
            canvas.drawText(
                line,
                90f,
                170f + index * 30f,
                smallPaint
            )
        }
    }

    val left = 130f
    val right = 1510f
    val legendColumns = 4
    val legendColumnWidth = 350f
    var sectionTop = 225f + headerOffset

    sections.forEachIndexed { sectionIndex, (section, sectionBars) ->
        canvas.drawText(
            exportContext.getString(section.titleResource),
            90f,
            sectionTop + 30f,
            sectionTitlePaint
        )
        canvas.drawText(
            exportContext.getString(group.unitResource).withUvirIrradianceUnit(irradianceUnit),
            90f,
            sectionTop + 68f,
            textPaint
        )

        val top = sectionTop + 90f
        val bottom = top + 300f
        val maximum =
            sectionBars
                .filterNot { it.outOfRange }
                .maxOfOrNull { valueScale(it.value).coerceAtLeast(0.0) }
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
                y + 8f,
                smallPaint
            )
        }

        val slotWidth = (right - left) / sectionBars.size
        val barWidth = (slotWidth * 0.54f).coerceAtMost(150f)
        sectionBars.forEachIndexed { index, bar ->
            if (bar.outOfRange) return@forEachIndexed
            val displayValue = valueScale(bar.value)
            val normalized =
                (displayValue.coerceAtLeast(0.0) / maximum)
                    .toFloat()
                    .coerceIn(0f, 1f)
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
                12f,
                12f,
                barPaint
            )

            val labelWidth = smallPaint.measureText(bar.shortLabel)
            canvas.drawText(
                bar.shortLabel,
                barLeft + (barWidth - labelWidth) / 2f,
                bottom + 38f,
                smallPaint
            )
        }

        val legendStartY = bottom + 82f
        sectionBars.forEachIndexed { index, bar ->
            val column = index % legendColumns
            val row = index / legendColumns
            val x = left + column * legendColumnWidth
            val y = legendStartY + row * 58f
            barPaint.color = bar.color.toArgb()
            canvas.drawCircle(x, y - 8f, 10f, barPaint)
            canvas.drawText(
                "${bar.displayLabelResource?.let(exportContext::getString) ?: bar.exportLabel}: " +
                    if (bar.outOfRange) {
                        exportContext.getString(R.string.out_of_range_short)
                    } else {
                        formatUvirNumber(
                            valueScale(bar.value),
                            irradianceUnit.displayFractionDigits(3),
                            exportFormatting.numericFormat
                        )
                    },
                x + 20f,
                y,
                smallPaint
            )
        }

        val legendRows = (sectionBars.size + legendColumns - 1) / legendColumns
        sectionTop = legendStartY + legendRows * 58f + 28f

        if (sectionIndex < sections.lastIndex) {
            canvas.drawLine(
                90f,
                sectionTop - 16f,
                1510f,
                sectionTop - 16f,
                gridPaint
            )
        }
    }

    val sharedDirectory =
        File(context.cacheDir, "shared").apply {
            mkdirs()
        }
    val sectionFileLabel =
        when (section) {
            AcquisitionChartSection.UV -> "UV"
            AcquisitionChartSection.VISIBLE -> "Visible_Light"
            AcquisitionChartSection.FAR_RED_NIR -> "Infrared"
            AcquisitionChartSection.BIOLOGICAL -> "Biological_Effects"
        }
    val file =
        File(
            sharedDirectory,
            "${uvirAcquisitionExportBaseName(record)}_${sectionFileLabel}.png"
        )

    val exportBitmap =
        if (includeHeader) {
            bitmap
        } else {
            Bitmap.createBitmap(
                bitmap,
                0,
                (225f + headerOffset - 12f).toInt(),
                width,
                height - (225f + headerOffset - 12f).toInt()
            )
        }
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

internal fun shareAcquisitionCharts(
    context: Context,
    record: SavedRecordDetail,
    groups: List<AcquisitionChartGroup>,
    destination: UvirExportDestination = UvirExportDestination.SHARE
) {
    require(groups.isNotEmpty())

    val files =
        listOf(
            createAcquisitionCombinedChartFile(
                context = context,
                record = record,
                groups = groups
            )
        )
    if (destination == UvirExportDestination.SAVE) {
        requestUvirExportSave(context, files)
        return
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
                "Uvir acquisition chart"
            )
            if (uris.size == 1) {
                putExtra(Intent.EXTRA_STREAM, uris.first())
            } else {
                putParcelableArrayListExtra(
                    Intent.EXTRA_STREAM,
                    uris
                )
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
            context.getString(
                R.string.acquisition_chart_share
            )
        )
    )
}

internal fun shareAcquisitionChart(
    context: Context,
    record: SavedRecordDetail,
    group: AcquisitionChartGroup = AcquisitionChartGroup.IRRADIANCE
) {
    shareAcquisitionCharts(
        context = context,
        record = record,
        groups = listOf(group)
    )
}
