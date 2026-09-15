package me.mondiversi.uvir

import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.FileProvider
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object UvirErrorLog {
    private const val LOG_FILE_NAME = "uvir_error_log.txt"
    private const val MAX_LOG_BYTES = 256 * 1024L
    private const val RETAINED_LOG_CHARACTERS = 128 * 1024
    private const val ROTATION_NOTICE =
        "[Older diagnostic entries were removed automatically.]\n"
    private val lock = Any()

    @Volatile
    private var installed = false

    @Volatile
    private var associatedSensorName = "—"

    // Updated with the already loaded profiles; error/crash handling must not query SQLite.
    internal fun updateAssociatedSensor(profile: UvirSensorProfile?, hardwareUid: String) {
        associatedSensorName = profile?.let(::detailSensorDisplayName)
            ?: hardwareUid.ifBlank { "—" }
    }

    fun install(context: Context) {
        if (installed) {
            return
        }

        synchronized(lock) {
            if (installed) {
                return
            }

            val applicationContext = context.applicationContext
            val previousHandler =
                Thread.getDefaultUncaughtExceptionHandler()

            Thread.setDefaultUncaughtExceptionHandler {
                    thread,
                    throwable ->
                record(
                    applicationContext,
                    "uncaught:${thread.name}",
                    throwable
                )
                previousHandler?.uncaughtException(
                    thread,
                    throwable
                )
            }
            installed = true
        }
    }

    fun record(
        context: Context,
        source: String,
        throwable: Throwable
    ) {
        val stackTrace =
            StringWriter().also { writer ->
                throwable.printStackTrace(
                    PrintWriter(writer)
                )
            }.toString()

        record(
            context = context,
            source = source,
            message = stackTrace
        )
    }

    fun record(
        context: Context,
        source: String,
        message: String
    ) {
        val sensorName = associatedSensorName
        runCatching {
            synchronized(lock) {
                val file = logFile(context)
                rotateIfNeeded(file)
                file.appendText(
                    buildString {
                        appendLine("────────────────────────────────")
                        appendLine("Time: ${timestamp()}")
                        appendLine("Source: $source")
                        appendLine("Associated sensor: $sensorName")
                        appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                        appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
                        appendLine(message.trim())
                        appendLine()
                    },
                    Charsets.UTF_8
                )
            }
        }
    }

    fun share(
        context: Context,
        chooserTitle: String,
        subject: String
    ): Boolean {
        val sourceFile =
            synchronized(lock) {
                logFile(context)
                    .takeIf {
                        it.isFile && it.length() > 0L
                    }
            } ?: return false

        val sharedDirectory =
            File(
                context.cacheDir,
                "shared"
            ).apply {
                mkdirs()
            }
        val sharedFile =
            File(
                sharedDirectory,
                uvirExportBaseName(
                    UvirExportContent.ERRORS
                ) + ".txt"
            )

        val diagnosticHeader =
            buildUvirDiagnosticReportHeader(context)

        synchronized(lock) {
            sharedFile.writeText(
                diagnosticHeader,
                Charsets.UTF_8
            )
            sharedFile.appendText(
                sourceFile.readText(Charsets.UTF_8),
                Charsets.UTF_8
            )
        }

        val uri =
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                sharedFile
            )
        val sendIntent =
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, subject)
                putExtra(Intent.EXTRA_STREAM, uri)
                clipData =
                    ClipData.newUri(
                        context.contentResolver,
                        sharedFile.name,
                        uri
                    )
                addFlags(
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
        val chooser =
            Intent.createChooser(
                sendIntent,
                chooserTitle
            )

        if (context !is Activity) {
            chooser.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK
            )
        }
        context.startActivity(chooser)
        return true
    }

    fun clear(context: Context): Boolean =
        runCatching {
            synchronized(lock) {
                val file = logFile(context)
                !file.exists() || file.delete()
            }
        }.getOrDefault(false)

    private fun logFile(context: Context): File =
        File(
            context.applicationContext.filesDir,
            LOG_FILE_NAME
        )

    private fun rotateIfNeeded(file: File) {
        if (!file.isFile || file.length() < MAX_LOG_BYTES) {
            return
        }

        val retained =
            file.readText(Charsets.UTF_8)
                .takeLast(RETAINED_LOG_CHARACTERS)
                .substringAfter('\n')

        file.writeText(
            ROTATION_NOTICE + retained,
            Charsets.UTF_8
        )
    }

    private fun timestamp(): String =
        SimpleDateFormat(
            "yyyy-MM-dd HH:mm:ss.SSS Z",
            Locale.US
        ).format(Date())

}
