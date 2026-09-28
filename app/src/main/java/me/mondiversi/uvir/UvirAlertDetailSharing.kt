package me.mondiversi.uvir

import android.content.Context
import java.io.File

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
    val files = mutableListOf<File>()

    fun writeSharedFile(
        format: UvirExportFileFormat,
        content: String
    ): File =
        prepareUvirTextExport(
            context = context,
            fileName = format.fileName(baseName),
            text = content
        )

    when (selection.dataFormat) {
        MeasurementShareFormat.CSV -> {
            files +=
                writeSharedFile(
                    format = UvirExportFileFormat.CSV,
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
                    format = UvirExportFileFormat.TXT,
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
                    format = UvirExportFileFormat.CSV,
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
                    format = UvirExportFileFormat.TXT,
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

    deliverUvirExportFiles(
        context = context,
        files = files,
        destination = destination,
        chooserTitle = context.getString(R.string.threshold_alert_log_share),
        subject =
            exportContext.getString(R.string.threshold_alert_export_subject)
    )
}
