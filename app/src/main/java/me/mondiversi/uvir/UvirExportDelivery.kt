package me.mondiversi.uvir

import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import java.io.File

enum class UvirExportDestination {
    SAVE,
    SHARE
}

internal interface UvirExportSaveHost {
    fun saveUvirExportFiles(files: List<File>)
}

internal interface UvirSettingsImportHost {
    fun selectUvirSettingsFiles()
}

internal fun requestUvirSettingsImport(context: Context) {
    var current: Context? = context
    while (current != null) {
        if (current is UvirSettingsImportHost) {
            current.selectUvirSettingsFiles()
            return
        }
        current = if (current is ContextWrapper) current.baseContext else null
    }
    error("The current screen cannot open the settings picker.")
}

internal fun requestUvirExportSave(
    context: Context,
    files: List<File>
) {
    require(files.isNotEmpty())

    var current: Context? = context
    while (current != null) {
        if (current is UvirExportSaveHost) {
            current.saveUvirExportFiles(files)
            return
        }
        current =
            if (current is ContextWrapper) {
                current.baseContext
            } else {
                null
            }
    }

    error("The current screen cannot open the export destination picker.")
}

internal fun uvirExportMimeType(file: File): String =
    when (file.extension.lowercase()) {
        "csv" -> "text/csv"
        "txt", "log" -> "text/plain"
        "png" -> "image/png"
        "json", "uvirsettings" -> "application/json"
        "db", "sqlite", "sqlite3" -> "application/vnd.sqlite3"
        else -> "application/octet-stream"
    }
