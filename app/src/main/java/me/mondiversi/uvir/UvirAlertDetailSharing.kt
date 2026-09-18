package me.mondiversi.uvir

import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import java.util.Locale

internal fun shareAlertDetail(
    context: Context,
    entry: ThresholdAlertLogEntry,
    selection: MeasurementDetailShareSelection,
    destination: UvirExportDestination = UvirExportDestination.SHARE
) {
    shareAlertDetails(
        context = context,
        entries = listOf(entry),
        baseName = uvirAlertExportBaseName(entry),
        selection = selection,
        destination = destination,
        chartFiles = { mode ->
            when (mode) {
                UvirChartExportMode.COMBINED ->
                    listOf(createAlertChartFile(context, entry))
                UvirChartExportMode.SEPARATE ->
                    createAlertSeparateChartFiles(context, entry)
            }
        }
    )
}

internal fun shareAlertSessionDetail(
    context: Context,
    sessionId: Long,
    entries: List<ThresholdAlertLogEntry>,
    selection: MeasurementDetailShareSelection,
    destination: UvirExportDestination = UvirExportDestination.SHARE
) {
    require(entries.isNotEmpty())
    shareAlertDetails(
        context = context,
        entries = entries,
        baseName = uvirAlertSessionExportBaseName(sessionId, entries),
        selection = selection,
        destination = destination,
        chartFiles = { mode ->
            when (mode) {
                UvirChartExportMode.COMBINED ->
                    listOf(
                        createAlertSessionCombinedChartFile(
                            context = context,
                            sessionId = sessionId,
                            entries = entries
                        )
                    )
                UvirChartExportMode.SEPARATE ->
                    createAlertSessionSeparateChartFiles(
                        context = context,
                        sessionId = sessionId,
                        entries = entries
                    )
            }
        }
    )
}

private fun shareAlertDetails(
    context: Context,
    entries: List<ThresholdAlertLogEntry>,
    baseName: String,
    selection: MeasurementDetailShareSelection,
    destination: UvirExportDestination,
    chartFiles: (UvirChartExportMode) -> List<File>
) {
    require(entries.isNotEmpty())
    require(selection.dataFormat != null || selection.includeCharts)

    val exportFormatting = uvirExportFormatting(context)
    val exportContext = exportFormatting.context
    val sharedDirectory =
        File(context.cacheDir, "shared").apply {
            mkdirs()
        }
    val files = mutableListOf<File>()

    fun writeSharedFile(
        extension: String,
        content: String
    ): File =
        File(sharedDirectory, "$baseName.$extension").apply {
            writeText(content, Charsets.UTF_8)
        }

    when (selection.dataFormat) {
        MeasurementShareFormat.CSV -> {
            files +=
                writeSharedFile(
                    extension = "csv",
                    content =
                        thresholdAlertLogCsv(
                            exportContext,
                            entries,
                            exportFormatting.numericFormat,
                            exportFormatting.dateFormat,
                            exportFormatting.timeFormat,
                            exportFormatting.irradianceUnit
                        )
                )
        }

        MeasurementShareFormat.READABLE_TABLE -> {
            files +=
                writeSharedFile(
                    extension = "txt",
                    content =
                        readableThresholdAlertLog(
                            exportContext,
                            entries,
                            exportFormatting.numericFormat,
                            exportFormatting.dateFormat,
                            exportFormatting.timeFormat,
                            exportFormatting.irradianceUnit
                        )
                )
        }

        MeasurementShareFormat.BOTH -> {
            files +=
                writeSharedFile(
                    extension = "csv",
                    content =
                        thresholdAlertLogCsv(
                            exportContext,
                            entries,
                            exportFormatting.numericFormat,
                            exportFormatting.dateFormat,
                            exportFormatting.timeFormat,
                            exportFormatting.irradianceUnit
                        )
                )
            files +=
                writeSharedFile(
                    extension = "txt",
                    content =
                        readableThresholdAlertLog(
                            exportContext,
                            entries,
                            exportFormatting.numericFormat,
                            exportFormatting.dateFormat,
                            exportFormatting.timeFormat,
                            exportFormatting.irradianceUnit
                        )
                )
        }

        null -> Unit
    }

    if (selection.includeCharts) {
        files += chartFiles(selection.chartExportMode)
    }

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
    val sendIntent =
        Intent(
            if (files.size == 1) {
                Intent.ACTION_SEND
            } else {
                Intent.ACTION_SEND_MULTIPLE
            }
        ).apply {
            type =
                if (files.size > 1) {
                    "*/*"
                } else {
                    when (files.first().extension.lowercase(Locale.US)) {
                        "csv" -> "text/csv"
                        "txt" -> "text/plain"
                        else -> "image/png"
                    }
                }
            putExtra(
                Intent.EXTRA_SUBJECT,
                exportContext.getString(R.string.threshold_alert_export_subject)
            )
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
    val chooser =
        Intent.createChooser(
            sendIntent,
            context.getString(R.string.threshold_alert_log_share)
        )

    if (context !is Activity) {
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(chooser)
}
