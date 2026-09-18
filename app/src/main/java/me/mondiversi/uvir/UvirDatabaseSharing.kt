package me.mondiversi.uvir

import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

internal fun shareDatabase(
    context: Context,
    database: UvirDatabaseHelper,
    destination: UvirExportDestination = UvirExportDestination.SHARE
) {
    val sqliteDatabase =
        database.writableDatabase

    sqliteDatabase.rawQuery(
        "PRAGMA wal_checkpoint(FULL)",
        null
    ).use { cursor ->
        cursor.moveToFirst()
    }

    val sourceFile =
        File(sqliteDatabase.path)

    check(sourceFile.isFile) {
        "Database file not found"
    }

    val sharedDirectory =
        File(
            context.cacheDir,
            "shared"
        ).apply {
            mkdirs()
        }

    val exportedFile =
        File(
            sharedDirectory,
            uvirExportBaseName(
                UvirExportContent.DATABASE
            ) + ".db"
        )

    sourceFile.copyTo(
        exportedFile,
        overwrite = true
    )

    if (destination == UvirExportDestination.SAVE) {
        requestUvirExportSave(context, listOf(exportedFile))
        return
    }

    val uri =
        FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            exportedFile
        )

    val sendIntent =
        Intent(Intent.ACTION_SEND).apply {
            type = "application/vnd.sqlite3"
            putExtra(
                Intent.EXTRA_SUBJECT,
                context.getString(
                    R.string.export_database_subject
                )
            )
            putExtra(
                Intent.EXTRA_STREAM,
                uri
            )
            clipData =
                ClipData.newUri(
                    context.contentResolver,
                    exportedFile.name,
                    uri
                )
            addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }

    val chooser =
        Intent.createChooser(
            sendIntent,
            context.getString(
                R.string.export_database
            )
        )

    if (context !is Activity) {
        chooser.addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK
        )
    }

    context.startActivity(chooser)
}
