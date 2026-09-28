package me.mondiversi.uvir

import android.content.Context
import android.content.res.Resources
import java.text.SimpleDateFormat
import java.text.DateFormat
import java.util.Date
import java.util.Locale

internal fun formatUvirSensorDiagnosticReport(
    resources: Resources,
    report: UvirSensorDiagnosticReport,
    technicalFormat: Boolean = false
): String {
    val locale = resources.configuration.locales[0]
    val unavailable = resources.getString(R.string.sensor_info_unavailable)
    fun millis(value: Double?) = value?.let {
        resources.getString(R.string.sensor_info_milliseconds, it)
    } ?: unavailable
    fun kilobytes(value: Long?) = value?.let {
        resources.getString(R.string.sensor_info_kilobytes, String.format(locale, "%.1f", it / 1024.0))
    } ?: unavailable
    fun bytes(value: Long?) = value?.let {
        when {
            it >= 1024L * 1024L * 1024L ->
                String.format(locale, "%.2f GB", it / (1024.0 * 1024.0 * 1024.0))
            it >= 1024L * 1024L ->
                String.format(locale, "%.2f MB", it / (1024.0 * 1024.0))
            else -> String.format(locale, "%.1f KB", it / 1024.0)
        }
    } ?: unavailable
    fun whole(value: Number?) = value?.let {
        String.format(locale, "%,d", it.toLong())
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
    fun passed(value: Boolean?) = resources.getString(when (value) {
        true -> R.string.sensor_info_test_passed
        false -> R.string.sensor_info_test_failed
        null -> R.string.sensor_info_unavailable
    })
    fun pin(value: Int?) = value?.let { "GPIO$it" } ?: unavailable
    fun i2cAddress(value: Int?) = value?.let {
        String.format(Locale.US, "0x%02X", it)
    } ?: unavailable
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
        field(
            R.string.diagnostic_generated,
            if (technicalFormat) {
                SimpleDateFormat(
                    "yyyy-MM-dd HH:mm:ss Z",
                    Locale.US
                ).format(Date(report.startedAtMs))
            } else {
                DateFormat.getDateTimeInstance(
                    DateFormat.MEDIUM,
                    DateFormat.MEDIUM,
                    locale
                ).format(Date(report.startedAtMs))
            }
        )
        appendLine("Uvir ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        field(R.string.sensor_info_sensor, report.sensorName)
        field(R.string.sensor_info_identifier, report.initialInfo.deviceId)
        field(R.string.sensor_info_source, source)
        field(R.string.sensor_info_board, info?.boardName ?: report.initialInfo.boardName)
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
                UvirDiagnosticOutcome.SUCCESS ->
                    "${millis(probe.responseMs)} · " +
                        passed(probe.info?.diagnosticHardwareHealthy())
                UvirDiagnosticOutcome.TIMEOUT -> resources.getString(R.string.diagnostic_timeout)
                UvirDiagnosticOutcome.WRITE_FAILED -> resources.getString(R.string.diagnostic_write_failed)
                UvirDiagnosticOutcome.CONNECTION_CHANGED -> resources.getString(R.string.diagnostic_connection_changed)
            }
            appendLine("${resources.getString(R.string.diagnostic_request)} ${index + 1}: $value")
        }
        appendLine()
        appendLine(resources.getString(R.string.sensor_info_components))
        appendLine(
            "${info?.visibleSensorModel?.ifBlank { "AS7343" } ?: "AS7343"} · " +
                "I²C ${i2cAddress(info?.visibleSensorI2cAddress)} · " +
                "SDA ${pin(info?.i2cSdaPin)} / SCL ${pin(info?.i2cSclPin)}: " +
                passed(
                    when {
                        info?.visibleSensorBusOk == false ||
                            info?.visibleSensorDriverOk == false ||
                            info?.visibleSensorReadOk == false -> false
                        info?.visibleSensorBusOk == true &&
                            info?.visibleSensorDriverOk == true &&
                            info?.visibleSensorReadOk == true -> true
                        else -> null
                    }
                )
        )
        appendLine(
            "${info?.uvSensorModel?.ifBlank { "AS7331" } ?: "AS7331"} · " +
                "I²C ${i2cAddress(info?.uvSensorI2cAddress)}: " +
                if (info?.uvAvailable == false && info?.uvSensorBusOk == false) {
                    detected(false)
                } else {
                    passed(
                        when {
                            info?.uvSensorBusOk == false ||
                                info?.uvSensorDriverOk == false ||
                                info?.uvSensorReadOk == false -> false
                            info?.uvSensorBusOk == true &&
                                info?.uvSensorDriverOk == true &&
                                info?.uvSensorReadOk == true -> true
                            else -> null
                        }
                    )
                }
        )
        field(
            R.string.diagnostic_sample_age,
            millis(info?.continuousSampleAgeMs?.toDouble())
        )
        appendLine(
            "${info?.rtcModel?.ifBlank { "DS3231" } ?: "DS3231"} · " +
                "I²C ${i2cAddress(info?.rtcI2cAddress)}: " +
                passed(
                    when {
                        info?.rtcBusOk == false || info?.rtcDriverOk == false || info?.rtcReadOk == false -> false
                        info?.rtcBusOk == true && info?.rtcDriverOk == true && info?.rtcReadOk == true -> true
                        else -> null
                    }
                )
        )
        appendLine(
            "${info?.sdAdapterModel?.ifBlank { "SN74HC125 + 1117C33" } ?: "SN74HC125 + 1117C33"} · " +
                "${info?.sdBus?.ifBlank { "SPI" } ?: "SPI"} " +
                "CS ${pin(info?.sdCsPin)} / SCK ${pin(info?.sdSckPin)} / " +
                "MISO ${pin(info?.sdMisoPin)} / MOSI ${pin(info?.sdMosiPin)}: " +
                passed(info?.sdReadWriteOk)
        )
        field(
            R.string.sensor_status_led,
            "R ${pin(info?.statusLedRedPin)} / G ${pin(info?.statusLedGreenPin)} / " +
                "B ${pin(info?.operationLedBluePin)} · ${passed(info?.statusLedControlAvailable)}"
        )
        field(
            R.string.sensor_led_brightness,
            info?.statusLedBrightness?.let { "$it%" } ?: unavailable
        )
        field(
            R.string.sensor_status_buzzer,
            "${pin(info?.statusBuzzerPin)} · ${passed(info?.statusBuzzerControlAvailable)}"
        )
        field(
            R.string.sensor_buzzer_volume,
            info?.statusBuzzerVolume?.let { "$it%" } ?: unavailable
        )
        field(
            R.string.external_command,
            "${pin(info?.externalInputPin)} · ${passed(info?.externalInputReadOk)} · " +
                when (info?.externalInputActive) {
                    true -> "LOW"
                    false -> "HIGH"
                    null -> unavailable
                }
        )
        appendLine()
        appendLine(resources.getString(R.string.connectivity))
        field(R.string.sensor_info_source, source)
        appendLine("App: ${passed(info?.appConnected)}")
        appendLine("Wi-Fi: ${detected(info?.wifiConnected)}")
        appendLine("Bluetooth: ${detected(info?.bluetoothConnected)}")
        appendLine("Internet: ${detected(info?.internetBrokerConnected)}")
        appendLine()
        field(R.string.sensor_info_ram_free, kilobytes(info?.freeHeapBytes))
        field(R.string.sensor_info_ram_capacity, kilobytes(info?.heapSizeBytes))
        field(R.string.sensor_info_flash_capacity, kilobytes(info?.flashSizeBytes))
        field(R.string.sensor_info_offline_storage_used,
            if (info?.offlineUsed != null && info.offlineCapacity != null) {
                "${info.offlineUsed}/${info.offlineCapacity}"
            } else unavailable)
        field(R.string.sensor_info_micro_sd_status, detected(info?.sdAvailable))
        appendLine("FRAM: ${detected(info?.framAvailable)}")
        appendLine("FRAM I²C: ${passed(info?.framBusOk)}")
        appendLine("FRAM read/write: ${passed(info?.framReadWriteOk)}")
        appendLine("FRAM queue: ${passed(info?.framQueueAvailable)}")
        appendLine(
            "FRAM records: ${whole(info?.framRecordsUsed)}/${whole(info?.framRecordCapacity)}"
        )
        field(R.string.sensor_info_rtc_status, detected(info?.rtcAvailable))
        field(
            R.string.sensor_info_clock_status,
            resources.getString(
                when (info?.rtcValid) {
                    true -> R.string.sensor_info_valid
                    false -> R.string.sensor_info_invalid
                    null -> R.string.sensor_info_unavailable
                }
            )
        )
        field(
            R.string.sensor_info_rtc_read_test,
            resources.getString(
                when (info?.rtcReadOk) {
                    true -> R.string.sensor_info_test_passed
                    false -> R.string.sensor_info_test_failed
                    null -> R.string.sensor_info_unavailable
                }
            )
        )
        field(R.string.sensor_info_micro_sd_type, info?.sdType ?: unavailable)
        field(R.string.sensor_info_offline_storage_capacity, bytes(info?.sdTotalBytes))
        field(R.string.sensor_info_offline_storage_free, bytes(info?.sdFreeBytes))
        field(R.string.sensor_info_record_capacity_total, whole(info?.sdRecordCapacityTotal))
        field(R.string.sensor_info_record_capacity_free, whole(info?.sdRecordCapacityFree))
        field(
            R.string.sensor_info_read_write_test,
            resources.getString(
                when (info?.sdReadWriteOk) {
                    true -> R.string.sensor_info_test_passed
                    false -> R.string.sensor_info_test_failed
                    null -> R.string.sensor_info_unavailable
                }
            )
        )
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
    deviceId: String,
    startedAtMs: Long,
    destination: UvirExportDestination = UvirExportDestination.SHARE
) {
    val file =
        prepareUvirTextExport(
            context = context,
            fileName =
                uvirSensorDiagnosticExportFileName(
                    deviceId,
                    startedAtMs
                ),
            text = text
        )
    deliverUvirExportFiles(
        context = context,
        files = listOf(file),
        destination = destination,
        chooserTitle = context.getString(R.string.share),
        subject = context.getString(R.string.diagnostic_title)
    )
}
