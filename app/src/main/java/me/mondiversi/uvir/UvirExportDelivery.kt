package me.mondiversi.uvir

import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

enum class UvirExportDestination {
    SAVE,
    SHARE
}

internal enum class UvirExportFileFormat(
    val displayName: String,
    val extension: String,
    val mimeType: String
) {
    CSV("CSV", "csv", "text/csv"),
    TXT("TXT", "txt", "text/plain"),
    PNG("PNG", "png", "image/png"),
    DATABASE("DB", "db", "application/vnd.sqlite3"),
    ZIP("ZIP", "zip", "application/zip"),
    SETTINGS("UVIRSETTINGS", "uvirsettings", "application/json"),
    JSON("JSON", "json", "application/json"),
    LOG("LOG", "log", "text/plain"),
    SQLITE("SQLITE", "sqlite", "application/vnd.sqlite3"),
    SQLITE3("SQLITE3", "sqlite3", "application/vnd.sqlite3"),
    OTHER("FILE", "", "application/octet-stream");

    fun fileName(baseName: String): String =
        if (extension.isBlank()) baseName else "$baseName.$extension"

    companion object {
        fun fromExtension(extension: String): UvirExportFileFormat =
            entries.firstOrNull {
                it.extension.equals(extension.trimStart('.'), ignoreCase = true)
            } ?: OTHER

        fun fromFile(file: File): UvirExportFileFormat =
            fromExtension(file.extension)
    }
}

internal data class UvirExportFileSummary(
    val format: UvirExportFileFormat,
    val count: Int
) {
    init {
        require(count >= 0)
    }
}

internal data class UvirPreparedExport(
    val files: List<File>
) {
    init {
        require(files.isNotEmpty())
        require(files.all(File::isFile))
    }

    val summaries: List<UvirExportFileSummary> =
        files
            .groupingBy { file ->
                UvirExportFileFormat.fromFile(file)
            }
            .eachCount()
            .map { (format, count) ->
                UvirExportFileSummary(format, count)
            }
            .sortedBy { it.format.ordinal }
}

internal interface UvirExportSaveHost {
    fun saveUvirExportFiles(files: List<File>)
}

internal interface UvirSettingsImportHost {
    fun selectUvirSettingsFiles()
}

internal interface UvirDatabaseImportHost {
    fun selectUvirDatabaseArchive()
}

internal fun requestUvirDatabaseImport(context: Context) {
    var current: Context? = context
    while (current != null) {
        if (current is UvirDatabaseImportHost) {
            current.selectUvirDatabaseArchive()
            return
        }
        current = if (current is ContextWrapper) current.baseContext else null
    }
    error("The current screen cannot open the database picker.")
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
    UvirExportFileFormat.fromFile(file).mimeType

internal fun uvirSharedExportDirectory(context: Context): File =
    File(context.cacheDir, "shared").apply { mkdirs() }

internal fun prepareUvirTextExport(
    context: Context,
    fileName: String,
    text: String
): File =
    File(uvirSharedExportDirectory(context), fileName).apply {
        writeText(text, Charsets.UTF_8)
    }

internal fun prepareUvirCopiedExport(
    context: Context,
    source: File,
    fileName: String
): File {
    require(source.isFile)
    return source.copyTo(
        File(uvirSharedExportDirectory(context), fileName),
        overwrite = true
    )
}

internal fun deliverUvirExportFiles(
    context: Context,
    files: List<File>,
    destination: UvirExportDestination,
    chooserTitle: String,
    subject: String = chooserTitle
) {
    val prepared = UvirPreparedExport(files)
    when (destination) {
        UvirExportDestination.SAVE ->
            requestUvirExportSave(context, prepared.files)

        UvirExportDestination.SHARE ->
            shareUvirExportFiles(
                context = context,
                files = prepared.files,
                chooserTitle = chooserTitle,
                subject = subject
            )
    }
}

internal fun shareUvirExportFiles(
    context: Context,
    files: List<File>,
    chooserTitle: String,
    subject: String = chooserTitle
) {
    require(files.isNotEmpty())

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
    val mimeTypes = files.map(::uvirExportMimeType).distinct()
    val sendIntent =
        Intent(
            if (files.size == 1) {
                Intent.ACTION_SEND
            } else {
                Intent.ACTION_SEND_MULTIPLE
            }
        ).apply {
            type = mimeTypes.singleOrNull() ?: "*/*"
            putExtra(Intent.EXTRA_SUBJECT, subject)
            if (files.size == 1) {
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
    val chooser = Intent.createChooser(sendIntent, chooserTitle)
    if (context !is Activity) {
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(chooser)
}
