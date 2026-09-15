package me.mondiversi.uvir

import android.content.Context
import android.os.Build
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal fun buildUvirDiagnosticReportHeader(
    context: Context
): String {
    val preferences =
        context.getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE
        )
    val language =
        AppLanguage.fromStoredValue(
            preferences.getString(
                KEY_APP_LANGUAGE,
                AppLanguage.SYSTEM.storedValue
            )
        )
    val numericFormat =
        UvirNumericFormat.fromStoredValue(
            preferences.getString(
                UVIR_NUMERIC_FORMAT_KEY,
                UvirNumericFormat.SYSTEM.storedValue
            )
        )
    val snapshot = UvirSensorRuntimeInfoStore.load(context)
    val sensorProfiles = runCatching {
        UvirDatabaseHelper(context).use { it.readSensorProfiles() }
    }.getOrDefault(emptyList())

    return buildString {
        appendLine("Uvir diagnostic report")
        appendLine("Generated: ${diagnosticTimestamp(System.currentTimeMillis())}")
        appendLine()
        appendLine("[Application]")
        appendDiagnosticField("Package", context.packageName)
        appendDiagnosticField(
            "Version",
            "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
        )
        appendDiagnosticField(
            "App language setting",
            language.storedValue
        )
        appendDiagnosticField(
            "Active locale",
            context.resources.configuration.locales[0].toLanguageTag()
        )
        appendDiagnosticField(
            "Numeric format setting",
            numericFormat.storedValue
        )
        appendLine()
        appendLine("[Phone]")
        appendDiagnosticField("Manufacturer", Build.MANUFACTURER)
        appendDiagnosticField("Model", Build.MODEL)
        appendDiagnosticField("Device", Build.DEVICE)
        appendDiagnosticField(
            "Android",
            "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
        )
        appendDiagnosticField(
            "Supported ABIs",
            Build.SUPPORTED_ABIS.joinToString(", ")
        )
        appendLine()
        appendLine("[Last known sensor]")
        appendDiagnosticField(
            "Sensor display name",
            detailSensorDisplayName(sensorProfiles.firstOrNull {
                it.hardwareUid.equals(snapshot?.info?.deviceId, ignoreCase = true)
            })
        )
        if (snapshot == null) {
            appendLine("No sensor information has been stored.")
        } else {
            appendSensorSnapshot(snapshot)
        }
        appendLine()
        appendLine("[Stored sensor profiles]")
        if (sensorProfiles.isEmpty()) {
            appendLine("—")
        } else {
            sensorProfiles.forEach { profile ->
                appendLine("Sensor ${profile.id}: ${detailSensorDisplayName(profile)} (${profile.hardwareUid})")
            }
        }
        appendLine()
        appendLine(
            "Network names, IP addresses, passwords, pairing codes and authentication credentials are omitted."
        )
        appendLine()
        appendLine("[Recorded errors]")
    }
}

private fun StringBuilder.appendSensorSnapshot(
    snapshot: UvirStoredSensorRuntimeInfo
) {
    val info = snapshot.info
    val sensorNames =
        buildList {
            if (info.sensorName.isNotBlank()) {
                add(info.sensorName)
            }
            if (
                info.uvAvailable == true &&
                !info.sensorName.contains("AS7331", ignoreCase = true)
            ) {
                add("AS7331")
            }
        }.joinToString(" + ")

    appendDiagnosticField(
        "Last update",
        diagnosticTimestamp(snapshot.capturedAtMs)
    )
    appendDiagnosticField("Connection source", snapshot.connectionMode.name)
    appendDiagnosticField("Protocol", info.protocol)
    appendDiagnosticField("Sensor ID", info.deviceId)
    appendDiagnosticField("Board", info.boardName)
    appendDiagnosticField("Chip model", info.chipModel)
    appendDiagnosticField("CPU cores", info.chipCores?.toString().orEmpty())
    appendDiagnosticField(
        "CPU frequency",
        info.cpuFrequencyMhz?.let { "$it MHz" }.orEmpty()
    )
    appendDiagnosticField("Sensors", sensorNames)
    appendDiagnosticField("Firmware", info.firmwareVersion)
    appendDiagnosticField("Unit", info.unit)
    appendDiagnosticField("Calibration", info.calibrationKind)
    appendDiagnosticField(
        "Visible calibration factor",
        info.visibleCalibrationFactor?.toString().orEmpty()
    )
    appendDiagnosticField(
        "UV calibration factor",
        info.uvCalibrationFactor?.toString().orEmpty()
    )
    appendDiagnosticField(
        "Offline alert interval",
        info.offlineAlertRepeatSeconds?.let { "$it s" }.orEmpty()
    )
    appendDiagnosticField(
        "VIS/NIR sensor detected",
        info.sensorAvailable.diagnosticValue()
    )
    appendDiagnosticField(
        "UV sensor detected",
        info.uvAvailable.diagnosticValue()
    )
    appendDiagnosticField("Streaming", info.streaming.diagnosticValue())
    appendDiagnosticField(
        "Transmission interval",
        info.streamIntervalMs?.let { "$it ms" }.orEmpty()
    )
    appendDiagnosticField(
        "Integration time",
        info.integrationMs?.let {
            String.format(Locale.US, "%.1f ms", it)
        }.orEmpty()
    )
    appendDiagnosticField(
        "Gain",
        info.gain?.let {
            String.format(Locale.US, "%.2f x", it)
        }.orEmpty()
    )
    appendDiagnosticField("Wi-Fi enabled", info.wifiEnabled.diagnosticValue())
    appendDiagnosticField(
        "Wi-Fi connected",
        info.wifiConnected.diagnosticValue()
    )
    appendDiagnosticField(
        "Wi-Fi signal",
        info.wifiRssiDbm?.let {
            "${wifiSignalPercentage(it)}% ($it dBm)"
        }.orEmpty()
    )
    appendDiagnosticField(
        "Bluetooth enabled",
        info.bluetoothEnabled.diagnosticValue()
    )
    appendDiagnosticField(
        "Bluetooth connected",
        info.bluetoothConnected.diagnosticValue()
    )
    appendDiagnosticField(
        "Bluetooth signal",
        info.bluetoothRssiDelta?.let {
            "${bluetoothSignalPercentage(it)}% (relative $it)"
        }.orEmpty()
    )
    appendDiagnosticField(
        "Samples sent",
        info.sampleSequence?.toString().orEmpty()
    )
    appendDiagnosticField(
        "Sensor flash total",
        info.flashSizeBytes.diagnosticBytes()
    )
    appendDiagnosticField(
        "Uvir firmware size",
        info.firmwareSizeBytes.diagnosticBytes()
    )
    appendDiagnosticField(
        "Program partition size",
        info.appPartitionSizeBytes.diagnosticBytes()
    )
    appendDiagnosticField(
        "Program space free",
        info.freeAppPartitionBytes.diagnosticBytes()
    )
    appendDiagnosticField(
        "Sensor RAM total",
        info.heapSizeBytes.diagnosticBytes()
    )
    appendDiagnosticField(
        "Sensor RAM free",
        info.freeHeapBytes.diagnosticBytes()
    )
    appendDiagnosticField(
        "Offline storage total",
        info.filesystemTotalBytes.diagnosticBytes()
    )
    appendDiagnosticField(
        "Offline storage used",
        info.filesystemUsedBytes.diagnosticBytes()
    )
}

private fun Long?.diagnosticBytes(): String =
    this?.let { bytes ->
        if (bytes >= 1024L * 1024L) {
            String.format(Locale.US, "%.2f MB", bytes / (1024.0 * 1024.0))
        } else {
            String.format(Locale.US, "%.1f KB", bytes / 1024.0)
        }
    }.orEmpty()

private fun StringBuilder.appendDiagnosticField(
    label: String,
    value: String
) {
    append(label)
    append(": ")
    appendLine(
        value
            .replace('\r', ' ')
            .replace('\n', ' ')
            .ifBlank { "Not available" }
    )
}

private fun Boolean?.diagnosticValue(): String =
    when (this) {
        true -> "Yes"
        false -> "No"
        null -> "Not available"
    }

private fun diagnosticTimestamp(timestampMs: Long): String =
    SimpleDateFormat(
        "yyyy-MM-dd HH:mm:ss.SSS Z",
        Locale.US
    ).format(Date(timestampMs))
