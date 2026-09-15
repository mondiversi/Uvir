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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal fun createAcquisitionChartFiles(
    context: Context,
    record: SavedRecordDetail,
    group: AcquisitionChartGroup
): List<File> {
    val bars = acquisitionChartBars(record.sample, group)
    return AcquisitionChartSection.entries.mapNotNull { section ->
        bars.filter { it.section == section }
            .takeIf { it.isNotEmpty() }
            ?.let { sectionBars ->
                createAcquisitionChartFile(
                    context = context,
                    record = record,
                    group = group,
                    section = section,
                    bars = sectionBars
                )
            }
    }
}

private fun createAcquisitionChartFile(
    context: Context,
    record: SavedRecordDetail,
    group: AcquisitionChartGroup,
    section: AcquisitionChartSection,
    bars: List<AcquisitionChartBar>
): File {
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
    val barPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    val noteLines =
        wrapChartExportText(
            text = chartExportContextText(
                note = acquisitionDisplayNote(
                    note = record.note,
                    automatic = record.automatic,
                    sessionSequence = record.sessionSequence,
                    emptyNote = "—"
                ),
                sensorName = record.sensorDisplayName
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
            455 + legendRows * 58
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

    canvas.drawText("Uvir acquisition chart", 90f, 85f, titlePaint)
    canvas.drawText(
        chartExportIdentifierLine(
            recordLabel = "Acquisition",
            recordId = record.id,
            sessionId = record.sessionId
        ) + " · " +
            SimpleDateFormat(
                "yyyy-MM-dd HH:mm:ss",
                Locale.US
            ).format(Date(record.timestamp)),
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
    canvas.drawText(
        "${group.exportTitle} (${group.exportUnit})",
        90f,
        180f + headerOffset,
        textPaint
    )

    val left = 130f
    val right = 1510f
    val legendColumns = 4
    val legendColumnWidth = 350f
    var sectionTop = 225f + headerOffset

    sections.forEachIndexed { sectionIndex, (section, sectionBars) ->
        canvas.drawText(
            section.exportTitle,
            90f,
            sectionTop + 25f,
            textPaint
        )

        val top = sectionTop + 45f
        val bottom = top + 300f
        val maximum =
            sectionBars
                .maxOfOrNull { it.value.coerceAtLeast(0.0) }
                ?.coerceAtLeast(1.0)
                ?: 1.0

        repeat(5) { index ->
            val y = top + (bottom - top) * index / 4f
            canvas.drawLine(left, y, right, y, gridPaint)
            val value = maximum * (4 - index) / 4.0
            canvas.drawText(
                String.format(Locale.US, "%.2f", value),
                24f,
                y + 8f,
                smallPaint
            )
        }

        val slotWidth = (right - left) / sectionBars.size
        val barWidth = (slotWidth * 0.54f).coerceAtMost(150f)
        sectionBars.forEachIndexed { index, bar ->
            val normalized =
                (bar.value.coerceAtLeast(0.0) / maximum)
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
                "${bar.exportLabel}: ${String.format(Locale.US, "%.3f", bar.value)}",
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

internal fun shareAcquisitionCharts(
    context: Context,
    record: SavedRecordDetail,
    groups: List<AcquisitionChartGroup>
) {
    require(groups.isNotEmpty())

    val files =
        groups.flatMap { group ->
            createAcquisitionChartFiles(
                context = context,
                record = record,
                group = group
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
