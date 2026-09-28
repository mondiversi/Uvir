package me.mondiversi.uvir

import android.content.Context
import java.io.File

internal data class MeasurementDetailShareSelection(
    val dataFormat: MeasurementShareFormat?,
    val includeCharts: Boolean,
    val chartExportMode: UvirChartExportMode = UvirChartExportMode.COMBINED,
    val variantChartGrouping: UvirVariantChartGrouping =
        UvirVariantChartGrouping.BY_VARIANT,
    val readableTableGrouping: UvirReadableTableGrouping =
        UvirReadableTableGrouping.BY_ACQUISITION
)

internal enum class UvirChartExportMode {
    COMBINED,
    SEPARATE
}

internal enum class UvirVariantChartGrouping {
    BY_VARIANT,
    BY_GROUP
}

internal enum class UvirReadableTableGrouping {
    BY_ACQUISITION,
    BY_SPECTRAL_AREA
}

internal fun shareAcquisitionDetail(
    context: Context,
    record: SavedRecordDetail,
    selection: MeasurementDetailShareSelection,
    variantCount: Int? = null,
    destination: UvirExportDestination = UvirExportDestination.SHARE
) {
    require(selection.dataFormat != null || selection.includeCharts)

    val exportFormatting = uvirExportFormatting(context)
    val exportContext = exportFormatting.context
    val sharedBaseName =
        uvirAcquisitionExportBaseName(record)
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

    when (selection.dataFormat) {
        MeasurementShareFormat.CSV -> {
            files +=
                writeSharedFile(
                    UvirExportFileFormat.CSV,
                    measurementCsv(
                        listOf(record),
                        exportFormatting.numericFormat,
                        exportFormatting.dateFormat,
                        exportFormatting.language,
                        exportContext.resources.configuration.locales[0],
                        exportContext,
                        exportFormatting.timeFormat,
                        exportFormatting.irradianceUnit,
                        variantCountsBySession =
                            record.sessionId
                                ?.takeIf { variantCount != null }
                                ?.let { mapOf(it to requireNotNull(variantCount)) }
                                .orEmpty()
                    )
                )
        }

        MeasurementShareFormat.READABLE_TABLE -> {
            files +=
                writeSharedFile(
                    UvirExportFileFormat.TXT,
                    readableMeasurementTable(
                        exportContext,
                        listOf(record),
                        exportFormatting.numericFormat,
                        exportFormatting.dateFormat,
                        exportFormatting.timeFormat,
                        exportFormatting.irradianceUnit,
                        variantCountOverride = variantCount
                    )
                )
        }

        MeasurementShareFormat.BOTH -> {
            files +=
                writeSharedFile(
                    UvirExportFileFormat.CSV,
                    measurementCsv(
                        listOf(record),
                        exportFormatting.numericFormat,
                        exportFormatting.dateFormat,
                        exportFormatting.language,
                        exportContext.resources.configuration.locales[0],
                        exportContext,
                        exportFormatting.timeFormat,
                        exportFormatting.irradianceUnit,
                        variantCountsBySession =
                            record.sessionId
                                ?.takeIf { variantCount != null }
                                ?.let { mapOf(it to requireNotNull(variantCount)) }
                                .orEmpty()
                    )
                )
            files +=
                writeSharedFile(
                    UvirExportFileFormat.TXT,
                    readableMeasurementTable(
                        exportContext,
                        listOf(record),
                        exportFormatting.numericFormat,
                        exportFormatting.dateFormat,
                        exportFormatting.timeFormat,
                        exportFormatting.irradianceUnit,
                        variantCountOverride = variantCount
                    )
                )
        }

        null -> Unit
    }

    if (selection.includeCharts) {
        files +=
            when (selection.chartExportMode) {
                UvirChartExportMode.COMBINED ->
                    listOf(
                        createAcquisitionCombinedChartFile(
                            context,
                            record,
                            variantCount = variantCount
                        )
                    )
                UvirChartExportMode.SEPARATE ->
                    createAcquisitionSeparateChartFiles(
                        context,
                        record,
                        variantCount = variantCount
                    )
            }
    }

    val subject = exportContext.getString(R.string.share_subject)
    deliverUvirExportFiles(
        context = context,
        files = files,
        destination = destination,
        chooserTitle = context.getString(R.string.share_measurements),
        subject = subject
    )
}
