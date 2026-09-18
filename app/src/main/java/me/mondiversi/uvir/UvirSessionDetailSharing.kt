package me.mondiversi.uvir

import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import java.util.Locale

internal fun shareAcquisitionSessionDetail(
    context: Context,
    sessionId: Long,
    records: List<SavedRecordDetail>,
    selection: MeasurementDetailShareSelection,
    destination: UvirExportDestination = UvirExportDestination.SHARE
) {
    require(records.isNotEmpty())
    require(selection.dataFormat != null || selection.includeCharts)

    val exportFormatting = uvirExportFormatting(context)
    val exportContext = exportFormatting.context
    val sharedDirectory =
        File(context.cacheDir, "shared").apply {
            mkdirs()
        }
    val sharedBaseName =
        uvirSessionAcquisitionsExportBaseName(
            sessionId,
            records
        )
    val files = mutableListOf<File>()

    fun writeSharedFile(
        extension: String,
        content: String
    ): File =
        File(
            sharedDirectory,
            "$sharedBaseName.$extension"
        ).apply {
            writeText(content, Charsets.UTF_8)
        }

    when (selection.dataFormat) {
        MeasurementShareFormat.CSV -> {
            files +=
                writeSharedFile(
                    extension = "csv",
                    content =
                        measurementCsv(
                            recordsInVariantExportOrder(records),
                            exportFormatting.numericFormat,
                            exportFormatting.dateFormat,
                            exportFormatting.language,
                            exportContext.resources.configuration.locales[0],
                            exportContext,
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
                        readableMeasurementTable(
                            exportContext,
                            records,
                            exportFormatting.numericFormat,
                            exportFormatting.dateFormat,
                            exportFormatting.timeFormat,
                            exportFormatting.irradianceUnit,
                            groupByVariant = true
                        )
                )
        }

        MeasurementShareFormat.BOTH -> {
            files +=
                writeSharedFile(
                    extension = "csv",
                    content =
                        measurementCsv(
                            recordsInVariantExportOrder(records),
                            exportFormatting.numericFormat,
                            exportFormatting.dateFormat,
                            exportFormatting.language,
                            exportContext.resources.configuration.locales[0],
                            exportContext,
                            exportFormatting.timeFormat,
                            exportFormatting.irradianceUnit
                        )
                )
            files +=
                writeSharedFile(
                    extension = "txt",
                    content =
                        readableMeasurementTable(
                            exportContext,
                            records,
                            exportFormatting.numericFormat,
                            exportFormatting.dateFormat,
                            exportFormatting.timeFormat,
                            exportFormatting.irradianceUnit,
                            groupByVariant = true
                        )
                )
        }

        null -> Unit
    }

    if (selection.includeCharts) {
        files +=
            createSessionVariantChartFiles(
                context = context,
                sessionId = sessionId,
                records = records,
                mode = selection.chartExportMode
            )
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
                    when (
                        files.first().extension.lowercase(Locale.US)
                    ) {
                        "csv" -> "text/csv"
                        "txt" -> "text/plain"
                        else -> "image/png"
                    }
                }
            putExtra(
                Intent.EXTRA_SUBJECT,
                exportContext.getString(R.string.share_subject)
            )
            if (files.size == 1) {
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
    val chooser =
        Intent.createChooser(
            sendIntent,
            context.getString(R.string.share_measurements)
        )

    if (context !is Activity) {
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(chooser)
}
