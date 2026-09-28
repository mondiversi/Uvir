package me.mondiversi.uvir

import android.content.Context
import java.io.File

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
    val sharedBaseName =
        uvirSessionAcquisitionsExportBaseName(
            sessionId,
            records
        )
    val files = mutableListOf<File>()

    fun writeSharedFile(
        format: UvirExportFileFormat,
        content: String
    ): File =
        prepareUvirTextExport(
            context = context,
            fileName = format.fileName(sharedBaseName),
            text = content
        )

    fun writeReadableFiles(): List<File> {
        val variantGroups = acquisitionVariantGroups(records)
        if (variantGroups.isEmpty()) {
            return listOf(
                writeSharedFile(
                    format = UvirExportFileFormat.TXT,
                    content =
                        readableMeasurementTable(
                            exportContext,
                            records,
                            exportFormatting.numericFormat,
                            exportFormatting.dateFormat,
                            exportFormatting.timeFormat,
                            exportFormatting.irradianceUnit,
                            grouping = selection.readableTableGrouping
                        )
                )
            )
        }

        return variantGroups.map { variant ->
            prepareUvirTextExport(
                context = context,
                fileName =
                    UvirExportFileFormat.TXT.fileName(
                        "$sharedBaseName" +
                            "_${uvirVariantFileToken(variant.index)}"
                    ),
                text =
                    readableMeasurementTable(
                        exportContext,
                        variant.records,
                        exportFormatting.numericFormat,
                        exportFormatting.dateFormat,
                        exportFormatting.timeFormat,
                        exportFormatting.irradianceUnit,
                        variantCountOverride = variant.count,
                        grouping = selection.readableTableGrouping
                    )
            )
        }
    }

    when (selection.dataFormat) {
        MeasurementShareFormat.CSV -> {
            files +=
                writeSharedFile(
                    format = UvirExportFileFormat.CSV,
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
            files += writeReadableFiles()
        }

        MeasurementShareFormat.BOTH -> {
            files +=
                writeSharedFile(
                    format = UvirExportFileFormat.CSV,
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
            files += writeReadableFiles()
        }

        null -> Unit
    }

    if (selection.includeCharts) {
        files +=
            createSessionVariantChartFiles(
                context = context,
                sessionId = sessionId,
                records = records,
                mode = selection.chartExportMode,
                variantGrouping = selection.variantChartGrouping
            )
    }

    deliverUvirExportFiles(
        context = context,
        files = files,
        destination = destination,
        chooserTitle = context.getString(R.string.share_measurements),
        subject = exportContext.getString(R.string.share_subject)
    )
}
