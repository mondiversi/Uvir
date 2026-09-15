package me.mondiversi.uvir

import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import java.util.Locale

internal fun thresholdAlertLogCsv(
    context: Context,
    entries: List<ThresholdAlertLogEntry>,
    numericFormat: UvirNumericFormat
): String {
    val rows =
        mutableListOf(
            listOf(
                "Alert ID",
                "Session ID",
                "Session note",
                "Date_Time",
                "Timestamp_ms",
                "Metric",
                "Direction",
                "Current_value",
                "Threshold",
                "Unit",
                "Sensor_name"
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
                        entry.note,
                        csvDateTime(
                            entry.timestamp,
                            DATA_EXPORT_LANGUAGE
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
                            "At or above"
                        } else {
                            "At or below"
                        },
                        csvNumber(
                            violation.value,
                            numericFormat
                        ),
                        csvNumber(
                            violation.rule.threshold.toDouble(),
                            numericFormat
                        ),
                        if (
                            violation.rule.metric
                                .isBiologicalEffect()
                        ) {
                            "uW/cm2 eq."
                        } else {
                            "uW/cm2"
                        },
                        exportSensorName(entry.sensorDisplayName)
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
    numericFormat: UvirNumericFormat
): String = buildString {
    appendLine("Uvir value alert log")
    appendLine()

    entries
        .sortedBy { it.id }
        .forEachIndexed { index, entry ->
            appendLine("Sensor: ${exportSensorName(entry.sensorDisplayName)}")
            appendLine("Alert ID: ${entry.id}")
            appendLine("Session ID: ${entry.sessionId ?: "—"}")
            appendLine("Session note: ${entry.note.ifBlank { "—" }}")
            appendLine(
                "Date/time: " +
                    csvDateTime(
                        entry.timestamp,
                        DATA_EXPORT_LANGUAGE
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
                val unit =
                    if (
                        violation.rule.metric
                            .isBiologicalEffect()
                    ) {
                        "µW/cm² eq."
                    } else {
                        "µW/cm²"
                    }

                appendLine(metric)
                appendLine(
                    "${formatUvirNumber(violation.value, 3, numericFormat)} $symbol " +
                        "${formatUvirNumber(violation.rule.threshold.toDouble(), 3, numericFormat)} $unit"
                )
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

    // Exports are intentionally stable and internationally exchangeable:
    // the user's display preference must never alter shared data files.
    val numericFormat =
        UvirNumericFormat.INTERNATIONAL

    val exportConfiguration =
        android.content.res.Configuration(
            context.resources.configuration
        ).apply {
            setLocale(Locale.ENGLISH)
            setLayoutDirection(Locale.ENGLISH)
        }
    val exportContext =
        context.createConfigurationContext(
            exportConfiguration
        )
    val sharedDirectory =
        File(
            context.cacheDir,
            "shared"
        ).apply {
            mkdirs()
        }
    val baseName =
        uvirExportBaseName(
            UvirExportContent.ALERTS
        )

    fun writeSharedFile(
        extension: String,
        content: String
    ): File =
        File(
            sharedDirectory,
            "$baseName.$extension"
        ).apply {
            writeText(content, Charsets.UTF_8)
        }

    fun sharedUri(file: File) =
        FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

    val csvContent =
        thresholdAlertLogCsv(
            exportContext,
            entries,
            numericFormat
        )
    val readableContent =
        readableThresholdAlertLog(
            exportContext,
            entries,
            numericFormat
        )
    val subject =
        exportContext.getString(
            R.string.threshold_alert_export_subject
        )

    val sendIntent =
        when (format) {
            MeasurementShareFormat.CSV -> {
                val file =
                    writeSharedFile(
                        "csv",
                        csvContent
                    )
                val uri = sharedUri(file)

                Intent(Intent.ACTION_SEND).apply {
                    type = "text/csv"
                    putExtra(Intent.EXTRA_SUBJECT, subject)
                    putExtra(Intent.EXTRA_STREAM, uri)
                    clipData =
                        ClipData.newUri(
                            context.contentResolver,
                            file.name,
                            uri
                        )
                    addFlags(
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                }
            }

            MeasurementShareFormat.READABLE_TABLE -> {
                val file =
                    writeSharedFile(
                        "txt",
                        readableContent
                    )
                val uri = sharedUri(file)

                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, subject)
                    putExtra(Intent.EXTRA_STREAM, uri)
                    clipData =
                        ClipData.newUri(
                            context.contentResolver,
                            file.name,
                            uri
                        )
                    addFlags(
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                }
            }

            MeasurementShareFormat.BOTH -> {
                val csvFile =
                    writeSharedFile(
                        "csv",
                        csvContent
                    )
                val readableFile =
                    writeSharedFile(
                        "txt",
                        readableContent
                    )
                val csvUri = sharedUri(csvFile)
                val readableUri = sharedUri(readableFile)

                Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                    type = "text/*"
                    putExtra(Intent.EXTRA_SUBJECT, subject)
                    putParcelableArrayListExtra(
                        Intent.EXTRA_STREAM,
                        arrayListOf(
                            csvUri,
                            readableUri
                        )
                    )
                    clipData =
                        ClipData.newUri(
                            context.contentResolver,
                            csvFile.name,
                            csvUri
                        ).apply {
                            addItem(
                                ClipData.Item(readableUri)
                            )
                        }
                    addFlags(
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                }
            }
        }

    val chooser =
        Intent.createChooser(
            sendIntent,
            context.getString(
                R.string.threshold_alert_log_share
            )
        )

    if (context !is Activity) {
        chooser.addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK
        )
    }

    context.startActivity(chooser)
}
