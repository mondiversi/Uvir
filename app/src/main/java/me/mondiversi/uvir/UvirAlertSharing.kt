package me.mondiversi.uvir

import android.content.Context
import java.io.File

internal fun thresholdAlertLogCsv(
    context: Context,
    entries: List<ThresholdAlertLogEntry>,
    numericFormat: UvirNumericFormat,
    dateFormat: UvirDateFormat = UvirDateFormat.INTERNATIONAL,
    timeFormat: UvirTimeFormat = UvirTimeFormat.H24,
    irradianceUnit: UvirIrradianceUnit = UvirIrradianceUnit.UW_CM2
): String {
    val rows =
        mutableListOf(
            listOf(
                context.getString(R.string.alert_chart_id_label),
                context.getString(R.string.share_session_id_label),
                context.getString(R.string.share_note_label),
                context.getString(R.string.share_date_label),
                "Timestamp_ms",
                context.getString(R.string.threshold_alerts_value),
                context.getString(R.string.threshold_alerts_condition),
                context.getString(R.string.threshold_use_current_value),
                context.getString(R.string.threshold_value_label)
                    .withUvirIrradianceUnit(irradianceUnit),
                context.getString(R.string.sensor_info_unit),
                context.getString(R.string.sensor_name_label),
                context.getString(R.string.out_of_range_csv_header)
            ).joinToString(";")
        )

    entries
        .sortedBy { it.id }
        .forEach { entry ->
            parseThresholdAlertLogDetails(
                entry.details
            ).forEach { violation ->
                rows +=
                    listOf(
                        entry.id.toString(),
                        entry.sessionId?.toString() ?: "—",
                        entry.note.ifBlank { context.getString(R.string.no_note) },
                        csvDateTime(
                            entry.timestamp,
                            dateFormat,
                            context.resources.configuration.locales[0],
                            timeFormat
                        ),
                        entry.timestamp.toString(),
                        context.getString(
                            thresholdAlertMetricLabelResource(
                                violation.rule.metric
                            )
                        ),
                        if (
                            violation.rule.direction ==
                            ThresholdAlertDirection.ABOVE
                        ) {
                            context.getString(R.string.threshold_direction_above)
                        } else {
                            context.getString(R.string.threshold_direction_below)
                        },
                        if (entry.qualityFlags.isOutOfRange(violation.rule.metric)) {
                            ""
                        } else {
                            formatUvirIrradianceExportNumber(
                                violation.value,
                                numericFormat,
                                irradianceUnit
                            )
                        },
                        formatUvirIrradianceExportNumber(
                            violation.rule.threshold.toDouble(),
                            numericFormat,
                            irradianceUnit
                        ),
                        irradianceUnit.unitLabel(
                            equivalent = violation.rule.metric.isBiologicalEffect()
                        ),
                        exportSensorName(entry.sensorDisplayName),
                        if (entry.qualityFlags.isOutOfRange(violation.rule.metric)) "1" else "0"
                    ).joinToString(";") {
                        csvCell(it)
                    }
            }
        }

    return rows.joinToString("\n")
}

internal fun readableThresholdAlertLog(
    context: Context,
    entries: List<ThresholdAlertLogEntry>,
    numericFormat: UvirNumericFormat,
    dateFormat: UvirDateFormat = UvirDateFormat.INTERNATIONAL,
    timeFormat: UvirTimeFormat = UvirTimeFormat.H24,
    irradianceUnit: UvirIrradianceUnit = UvirIrradianceUnit.UW_CM2
): String = buildString {
    appendLine("Uvir ${context.getString(R.string.threshold_alert_log_title)}")
    appendLine()

    entries
        .sortedBy { it.id }
        .forEachIndexed { index, entry ->
            appendLine(
                "${context.getString(R.string.sensor_selector_label)}: " +
                    exportSensorName(entry.sensorDisplayName)
            )
            appendLine("${context.getString(R.string.alert_chart_id_label)}: ${entry.id}")
            appendLine(
                "${context.getString(R.string.share_session_id_label)}: " +
                    (entry.sessionId ?: "—")
            )
            appendLine(
                "${context.getString(R.string.share_note_label)}: " +
                    entry.note.ifBlank { context.getString(R.string.no_note) }
            )
            appendLine(
                "${context.getString(R.string.share_date_label)}: " +
                    csvDateTime(
                        entry.timestamp,
                        dateFormat,
                        context.resources.configuration.locales[0],
                        timeFormat
                    )
            )

            parseThresholdAlertLogDetails(
                entry.details
            ).forEach { violation ->
                val metric =
                    context.getString(
                        thresholdAlertMetricLabelResource(
                            violation.rule.metric
                        )
                    )
                val symbol =
                    if (
                        violation.rule.direction ==
                        ThresholdAlertDirection.ABOVE
                    ) {
                        "≥"
                    } else {
                        "≤"
                    }
                val unit = irradianceUnit.unitLabel(
                    equivalent = violation.rule.metric.isBiologicalEffect()
                )

                appendLine(metric)
                if (entry.qualityFlags.isOutOfRange(violation.rule.metric)) {
                    appendLine(context.getString(R.string.out_of_range_short))
                } else {
                    appendLine(
                        "${formatUvirIrradianceNumber(violation.value, 3, numericFormat, irradianceUnit)} $symbol " +
                            "${formatUvirIrradianceNumber(violation.rule.threshold.toDouble(), 3, numericFormat, irradianceUnit)} $unit"
                    )
                }
            }

            if (index < entries.lastIndex) {
                appendLine()
                appendLine("────────────────────")
                appendLine()
            }
        }
}

internal fun shareThresholdAlertLog(
    context: Context,
    entries: List<ThresholdAlertLogEntry>,
    format: MeasurementShareFormat
) {
    if (entries.isEmpty()) {
        return
    }

    val exportFormatting = uvirExportFormatting(context)
    val exportContext = exportFormatting.context
    val numericFormat = exportFormatting.numericFormat
    val baseName =
        uvirExportBaseName(
            UvirExportContent.ALERTS
        )

    fun writeSharedFile(
        fileFormat: UvirExportFileFormat,
        content: String
    ): File =
        prepareUvirTextExport(
            context = context,
            fileName = fileFormat.fileName(baseName),
            text = content
        )

    val csvContent =
        thresholdAlertLogCsv(
            exportContext,
            entries,
            numericFormat,
            exportFormatting.dateFormat,
            exportFormatting.timeFormat
            , exportFormatting.irradianceUnit
        )
    val readableContent =
        readableThresholdAlertLog(
            exportContext,
            entries,
            numericFormat,
            exportFormatting.dateFormat,
            exportFormatting.timeFormat
            , exportFormatting.irradianceUnit
        )
    val subject =
        exportContext.getString(
            R.string.threshold_alert_export_subject
        )

    val files =
        when (format) {
            MeasurementShareFormat.CSV ->
                listOf(
                    writeSharedFile(
                        UvirExportFileFormat.CSV,
                        csvContent
                    )
                )

            MeasurementShareFormat.READABLE_TABLE ->
                listOf(
                    writeSharedFile(
                        UvirExportFileFormat.TXT,
                        readableContent
                    )
                )

            MeasurementShareFormat.BOTH ->
                listOf(
                    writeSharedFile(
                        UvirExportFileFormat.CSV,
                        csvContent
                    ),
                    writeSharedFile(
                        UvirExportFileFormat.TXT,
                        readableContent
                    )
                )
        }

    deliverUvirExportFiles(
        context = context,
        files = files,
        destination = UvirExportDestination.SHARE,
        chooserTitle = context.getString(R.string.threshold_alert_log_share),
        subject = subject
    )
}
