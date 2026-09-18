package me.mondiversi.uvir

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import androidx.core.content.FileProvider
import java.io.File
import java.text.DateFormat
import java.util.Date

internal fun formatUvirSensorDiagnosticReport(
    resources: Resources,
    report: UvirSensorDiagnosticReport
): String {
    val locale = resources.configuration.locales[0]
    val unavailable = resources.getString(R.string.sensor_info_unavailable)
    fun millis(value: Double?) = value?.let {
        resources.getString(R.string.sensor_info_milliseconds, it)
    } ?: unavailable
    fun kilobytes(value: Long?) = value?.let {
        resources.getString(R.string.sensor_info_kilobytes, String.format(locale, "%.1f", it / 1024.0))
    } ?: unavailable
    fun active(value: Boolean?) = resources.getString(when (value) {
        true -> R.string.diagnostic_active
        false -> R.string.diagnostic_inactive
        null -> R.string.sensor_info_unavailable
    })
    fun detected(value: Boolean?) = resources.getString(when (value) {
        true -> R.string.sensor_info_detected
        false -> R.string.sensor_info_not_detected
        null -> R.string.sensor_info_unavailable
    })
    val info = report.latestInfo
    val source = resources.getString(when (report.connectionMode) {
        SensorConnectionMode.USB -> R.string.sensor_connection_usb
        SensorConnectionMode.WIFI -> R.string.sensor_connection_wifi
        SensorConnectionMode.BLUETOOTH -> R.string.sensor_connection_bluetooth
        SensorConnectionMode.INTERNET -> R.string.sensor_connection_internet
    })
    return buildString {
        fun field(label: Int, value: String) {
            appendLine("${resources.getString(label)}: ${value.ifBlank { unavailable }}")
        }
        appendLine(resources.getString(R.string.diagnostic_title))
        field(R.string.diagnostic_generated, DateFormat.getDateTimeInstance(
            DateFormat.MEDIUM, DateFormat.MEDIUM, locale
        ).format(Date(report.startedAtMs)))
        appendLine("Uvir ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        field(R.string.sensor_info_sensor, report.sensorName)
        field(R.string.sensor_info_identifier, report.initialInfo.deviceId)
        field(R.string.sensor_info_source, source)
        field(R.string.sensor_info_board, report.initialInfo.boardName)
        field(R.string.sensor_info_firmware, info?.firmwareVersion ?: report.initialInfo.firmwareVersion)
        field(R.string.diagnostic_duration, millis(report.durationMs.toDouble()))
        appendLine()
        appendLine(resources.getString(
            R.string.diagnostic_summary, report.responses, UVIR_DIAGNOSTIC_REQUESTS
        ))
        appendLine(resources.getString(
            if (report.successful) R.string.diagnostic_success else R.string.diagnostic_warning
        ))
        if (report.responseTimes.isNotEmpty()) {
            field(R.string.diagnostic_latency, listOf(
                report.responseTimes.minOrNull(), report.responseTimes.average(), report.responseTimes.maxOrNull()
            ).joinToString(" / ") { millis(it) })
        }
        report.probes.forEachIndexed { index, probe ->
            val value = when (probe.outcome) {
                UvirDiagnosticOutcome.SUCCESS -> millis(probe.responseMs)
                UvirDiagnosticOutcome.TIMEOUT -> resources.getString(R.string.diagnostic_timeout)
                UvirDiagnosticOutcome.WRITE_FAILED -> resources.getString(R.string.diagnostic_write_failed)
                UvirDiagnosticOutcome.CONNECTION_CHANGED -> resources.getString(R.string.diagnostic_connection_changed)
            }
            appendLine("${resources.getString(R.string.diagnostic_request)} ${index + 1}: $value")
        }
        appendLine()
        field(R.string.sensor_info_ram_free, kilobytes(info?.freeHeapBytes))
        field(R.string.sensor_info_ram_capacity, kilobytes(info?.heapSizeBytes))
        field(R.string.sensor_info_flash_capacity, kilobytes(info?.flashSizeBytes))
        field(R.string.sensor_info_offline_storage_used,
            if (info?.offlineUsed != null && info.offlineCapacity != null) {
                "${info.offlineUsed}/${info.offlineCapacity}"
            } else unavailable)
        field(R.string.sensor_info_visible_sensor, detected(info?.sensorAvailable))
        field(R.string.sensor_info_uv_sensor, detected(info?.uvAvailable))
        field(R.string.diagnostic_auto_session, active(info?.offlineRecording))
        field(R.string.diagnostic_alert_session, active(info?.alertMonitoringEnabled))
        appendLine()
        appendLine(resources.getString(R.string.diagnostic_notes))
    }
}

internal fun shareUvirSensorDiagnosticReport(
    context: Context,
    text: String,
    startedAtMs: Long,
    destination: UvirExportDestination = UvirExportDestination.SHARE
) {
    val directory = File(context.cacheDir, "shared").apply { mkdirs() }
    val file = File(directory, "Uvir_Diagnostic_${uvirExportTimestamp(startedAtMs)}.txt")
    file.writeText(text, Charsets.UTF_8)
    if (destination == UvirExportDestination.SAVE) {
        requestUvirExportSave(context, listOf(file))
        return
    }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.diagnostic_title))
        putExtra(Intent.EXTRA_STREAM, uri)
        clipData = ClipData.newUri(context.contentResolver, file.name, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, context.getString(R.string.share)))
}
