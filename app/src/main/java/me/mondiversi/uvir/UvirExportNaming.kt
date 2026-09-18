package me.mondiversi.uvir

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal enum class UvirExportContent(
    val fileLabel: String
) {
    ACQUISITIONS("Acquisitions"),
    ALERTS("Alerts"),
    ERRORS("Error_Log"),
    DATABASE("Database")
}

internal fun uvirExportTimestamp(
    timestamp: Long
): String =
    if (timestamp <= 0L) {
        "unknown_time"
    } else {
        SimpleDateFormat(
            "yyyyMMdd_HHmmss",
            Locale.US
        ).format(Date(timestamp))
    }

internal fun uvirAcquisitionExportBaseName(
    record: SavedRecordDetail
): String =
    "Uvir_Acquisition_ID${record.id}_${uvirExportTimestamp(record.timestamp)}"

internal fun uvirSessionAcquisitionsExportBaseName(
    sessionId: Long,
    records: List<SavedRecordDetail>
): String {
    require(records.isNotEmpty())
    val startedAt = records.minOf { it.timestamp }
    return "Uvir_Session_Acquisitions_ID${sessionId}_${uvirExportTimestamp(startedAt)}"
}

internal fun uvirAlertExportBaseName(
    entry: ThresholdAlertLogEntry
): String =
    "Uvir_Alert_ID${entry.id}_${uvirExportTimestamp(entry.timestamp)}"

internal fun uvirAlertSessionExportBaseName(
    sessionId: Long,
    entries: List<ThresholdAlertLogEntry>
): String {
    require(entries.isNotEmpty())
    val startedAt = entries.minOf { it.timestamp }
    return "Uvir_Session_Alerts_ID${sessionId}_${uvirExportTimestamp(startedAt)}"
}

internal fun thresholdAlertMetricExportFileLabel(
    metric: ThresholdAlertMetric
): String =
    when (metric) {
        ThresholdAlertMetric.UV_TOTAL -> "UV"
        ThresholdAlertMetric.UVC -> "UVC"
        ThresholdAlertMetric.UVB -> "UVB"
        ThresholdAlertMetric.UVA -> "UVA"
        ThresholdAlertMetric.HEV -> "HEV"
        ThresholdAlertMetric.HEB -> "HEB"
        ThresholdAlertMetric.VISIBLE_TOTAL -> "Visible_Light"
        ThresholdAlertMetric.VIOLET -> "Violet"
        ThresholdAlertMetric.BLUE -> "Blue"
        ThresholdAlertMetric.GREEN -> "Green"
        ThresholdAlertMetric.YELLOW -> "Yellow"
        ThresholdAlertMetric.ORANGE -> "Orange"
        ThresholdAlertMetric.RED -> "Red"
        ThresholdAlertMetric.NIR_TOTAL -> "Infrared"
        ThresholdAlertMetric.FAR_RED -> "Far_Red"
        ThresholdAlertMetric.NIR -> "NIR"
        ThresholdAlertMetric.BIO_DNA_UV -> "Biological_DNA_UV"
        ThresholdAlertMetric.BIO_UVA_PHOTOAGING -> "Biological_UVA_Photoaging"
        ThresholdAlertMetric.BIO_HEV_OXIDATIVE -> "Biological_HEV_Oxidative"
    }

/**
 * Gives every user-facing export the same portable naming structure.
 * The timestamp deliberately avoids spaces and filesystem-sensitive symbols.
 */
internal fun uvirExportBaseName(
    content: UvirExportContent,
    date: Date = Date()
): String {
    val timestamp = uvirExportTimestamp(date.time)

    return "Uvir_${content.fileLabel}_$timestamp"
}
