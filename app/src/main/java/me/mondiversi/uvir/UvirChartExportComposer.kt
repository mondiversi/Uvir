package me.mondiversi.uvir

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import java.io.File
import java.io.FileOutputStream

/** Joins already-rendered chart panels into one vertically scrollable PNG. */
internal fun combineChartExportFiles(
    outputFile: File,
    panelFiles: List<File>
): File {
    require(panelFiles.isNotEmpty())
    val panels = panelFiles.map { file ->
        requireNotNull(BitmapFactory.decodeFile(file.absolutePath)) {
            "Unable to decode chart panel ${file.name}"
        }
    }
    val gap = 28
    val width = panels.maxOf { it.width }
    val height = panels.sumOf { it.height } + gap * (panels.size - 1)
    val combined = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(combined)
    canvas.drawColor(Color.WHITE)
    val separatorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(222, 222, 228)
        strokeWidth = 2f
    }
    var top = 0f
    panels.forEachIndexed { index, panel ->
        canvas.drawBitmap(panel, (width - panel.width) / 2f, top, null)
        top += panel.height
        if (index < panels.lastIndex) {
            canvas.drawLine(90f, top + gap / 2f, width - 90f, top + gap / 2f, separatorPaint)
            top += gap
        }
    }

    outputFile.parentFile?.mkdirs()
    FileOutputStream(outputFile).use { output ->
        check(combined.compress(Bitmap.CompressFormat.PNG, 100, output))
    }
    combined.recycle()
    panels.forEach(Bitmap::recycle)
    panelFiles
        .filterNot { it.absolutePath == outputFile.absolutePath }
        .forEach(File::delete)
    return outputFile
}
