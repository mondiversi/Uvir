package me.mondiversi.uvir

import android.content.Context
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

object UvirErrorLog {
    private const val LOG_FILE_NAME = "uvir_error_log.txt"
    private const val MAX_LOG_BYTES = 256 * 1024L
    private const val RETAINED_LOG_CHARACTERS = 128 * 1024
    private const val ROTATION_NOTICE =
        "[Older diagnostic entries were removed automatically.]\n"
    private val lock = Any()
    private val changes = MutableStateFlow(0L)
    internal val revision = changes.asStateFlow()

    internal fun read(context: Context): String = synchronized(lock) {
        logFile(context).takeIf { it.isFile }?.readText(Charsets.UTF_8).orEmpty()
    }

    /** Stored UTF-8 log only; does not load its contents or generate an export. */
    internal fun sizeBytes(context: Context): Long = synchronized(lock) {
        logFile(context).takeIf { it.isFile }?.length() ?: 0L
    }

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
                changes.update { it + 1 }
            }
        }
    }

    fun share(
        context: Context,
        chooserTitle: String,
        subject: String,
        destination: UvirExportDestination = UvirExportDestination.SHARE
    ): Boolean {
        val sharedFile = createExport(context) ?: return false
        deliverUvirExportFiles(context, listOf(sharedFile), destination, chooserTitle, subject)
        return true
    }

    internal fun createExport(context: Context): File? {
        val log = read(context).takeIf { it.isNotBlank() } ?: return null
        return prepareUvirTextExport(
                context = context,
                fileName =
                    UvirExportFileFormat.TXT.fileName(
                        uvirExportBaseName(UvirExportContent.ERRORS)
                    ),
                text = buildUvirDiagnosticReportHeader(context) + log
            )
    }

    fun clear(context: Context): Boolean =
        runCatching {
            synchronized(lock) {
                val file = logFile(context)
                !file.exists() || file.delete()
            }
        }.getOrDefault(false).also { cleared -> if (cleared) changes.update { it + 1 } }

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
