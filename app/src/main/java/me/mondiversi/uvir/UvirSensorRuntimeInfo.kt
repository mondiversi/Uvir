package me.mondiversi.uvir

import org.json.JSONArray
import org.json.JSONObject

/** Activity frames cannot authenticate a sensor or replace its configuration. */
internal fun UvirSensorRuntimeInfo.updatedFromActivityFrame(
    json: JSONObject,
    appConnectionConfirmed: Boolean
): UvirSensorRuntimeInfo? {
    if (!appConnectionConfirmed ||
        json.optString("protocol") != UVIR_SENSOR_PROTOCOL ||
        json.optString("type") != "activity" ||
        deviceId.isBlank() ||
        normalizeSensorDeviceId(json.optString("device_id")) != normalizeSensorDeviceId(deviceId)
    ) return null
    val active = json.opt("operation_active") as? Boolean ?: return null
    return copy(operationActive = active)
}

data class UvirSensorRuntimeInfo(
    val sensorSettingsSchemaVersion: Int? = null,
    val settingsUpdatedAtMs: Long? = null,
    val protocol: String = "",
    val deviceId: String = "",
    val boardName: String = "",
    val chipModel: String = "",
    val chipCores: Int? = null,
    val cpuFrequencyMhz: Int? = null,
    val sensorName: String = "",
    val firmwareVersion: String = "",
    val unit: String = "",
    val calibrationKind: String = "",
    val visibleCalibrationFactor: Double? = null,
    val uvCalibrationFactor: Double? = null,
    val sensorAvailable: Boolean? = null,
    val uvAvailable: Boolean? = null,
    val i2cSdaPin: Int? = null,
    val i2cSclPin: Int? = null,
    val visibleSensorModel: String = "",
    val visibleSensorI2cAddress: Int? = null,
    val visibleSensorBusOk: Boolean? = null,
    val visibleSensorDriverOk: Boolean? = null,
    val visibleSensorReadOk: Boolean? = null,
    val continuousSampleAgeMs: Long? = null,
    val uvSensorModel: String = "",
    val uvSensorI2cAddress: Int? = null,
    val uvSensorBusOk: Boolean? = null,
    val uvSensorDriverOk: Boolean? = null,
    val uvSensorReadOk: Boolean? = null,
    val rtcModel: String = "",
    val rtcI2cAddress: Int? = null,
    val rtcBusOk: Boolean? = null,
    val rtcDriverOk: Boolean? = null,
    val sdAdapterModel: String = "",
    val sdBus: String = "",
    val sdCsPin: Int? = null,
    val sdSckPin: Int? = null,
    val sdMisoPin: Int? = null,
    val sdMosiPin: Int? = null,
    val statusLedControlAvailable: Boolean? = null,
    val statusLedRedPin: Int? = null,
    val statusLedGreenPin: Int? = null,
    val operationLedBluePin: Int? = null,
    val statusBuzzerControlAvailable: Boolean? = null,
    val statusBuzzerPin: Int? = null,
    val externalInputReadOk: Boolean? = null,
    val externalInputPin: Int? = null,
    val externalInputActive: Boolean? = null,
    val uptimeMs: Long? = null,
    val streaming: Boolean? = null,
    val appConnected: Boolean? = null,
    // Ephemeral activity from the connected device, never part of its saved settings.
    val operationActive: Boolean? = null,
    val streamIntervalMs: Long? = null,
    val samplingSamplesPerMeasurement: Int? = null,
    val samplingSpacingMs: Long? = null,
    val samplingDiscardExtremes: Boolean? = null,
    val activeTransport: String = "",
    val wifiConfigured: Boolean? = null,
    val wifiEnabled: Boolean? = null,
    val wifiConnected: Boolean? = null,
    val wifiNetwork: String = "",
    val wifiHostname: String = "",
    val wifiAddress: String = "",
    val wifiRssiDbm: Int? = null,
    val bluetoothEnabled: Boolean? = null,
    val bluetoothConnected: Boolean? = null,
    val bluetoothName: String = "",
    val bluetoothRssiDelta: Int? = null,
    val internetEnabled: Boolean? = null,
    val internetUsePrimaryWifi: Boolean? = null,
    val internetBrokerHost: String = "",
    val internetBrokerPort: Int? = null,
    val internetBrokerConnected: Boolean? = null,
    val integrationMs: Double? = null,
    val gain: Double? = null,
    val sampleSequence: Long? = null,
    val flashSizeBytes: Long? = null,
    val appPartitionSizeBytes: Long? = null,
    val firmwareSizeBytes: Long? = null,
    val freeAppPartitionBytes: Long? = null,
    val heapSizeBytes: Long? = null,
    val freeHeapBytes: Long? = null,
    val filesystemTotalBytes: Long? = null,
    val filesystemUsedBytes: Long? = null,
    val storageBackend: String = "",
    val storageRecordSizeBytes: Long? = null,
    val sdAvailable: Boolean? = null,
    val sdForeign: Boolean? = null,
    val sdType: String = "",
    val sdTotalBytes: Long? = null,
    val sdUsedBytes: Long? = null,
    val sdFreeBytes: Long? = null,
    val sdRecordCapacityTotal: Int? = null,
    val sdRecordCapacityFree: Int? = null,
    val sdInvalidRecords: Int? = null,
    val sdMountErrors: Int? = null,
    val sdWriteErrors: Int? = null,
    val sdReadWriteOk: Boolean? = null,
    val framAvailable: Boolean? = null,
    val framModel: String = "",
    val framI2cAddress: Int? = null,
    val framBusOk: Boolean? = null,
    val framReadWriteOk: Boolean? = null,
    val framQueueAvailable: Boolean? = null,
    val framCapacityBytes: Long? = null,
    val framRecordCapacity: Int? = null,
    val framRecordsUsed: Int? = null,
    val timeSynced: Boolean? = null,
    val rtcAvailable: Boolean? = null,
    val rtcValid: Boolean? = null,
    val rtcOscillatorStopped: Boolean? = null,
    val rtcReadOk: Boolean? = null,
    val rtcCurrentTimeMs: Long? = null,
    val timeSource: String = "",
    val rtcReadErrors: Int? = null,
    val rtcWriteErrors: Int? = null,
    val offlineStorageAvailable: Boolean? = null,
    val offlineCapacity: Int? = null,
    val offlineUsed: Int? = null,
    val offlineRemaining: Int? = null,
    val offlineStorageFull: Boolean? = null,
    val offlineAcquisitions: Int? = null,
    val offlineAlerts: Int? = null,
    val offlineAlertRepeatSeconds: Int? = null,
    val alertMonitoringEnabled: Boolean? = null,
    val alertRecordingEnabled: Boolean? = null,
    val alertStartDelaySeconds: Long? = null,
    val alertDurationSeconds: Long? = null,
    val alertMaxRegistrations: Int? = null,
    val alertCompletedRegistrations: Int? = null,
    val alertSessionId: Long? = null,
    val alertRules: List<ThresholdAlertRule>? = null,
    val offlineErrors: Int? = null,
    // Ephemeral proof of a complete report from this connection, not diagnostic cache data.
    val automaticJobReadback: UvirAutomaticJobReadback? = null,
    val offlineRecording: Boolean? = null,
    val offlineSessionId: Long? = null,
    val offlineCompleted: Int? = null,
    val offlineNextAtMs: Long? = null,
    val offlineExternalCommand: Boolean? = null,
    val offlineConditionPlan: ConditionalAcquisitionPlan? = null,
    val offlineConditionWaiting: Boolean? = null,
    val offlineEndAtMs: Long? = null,
    val offlineStartedAtMs: Long? = null,
    val autonomousRecordingEnabled: Boolean? = null,
    val automaticShutdownEnabled: Boolean? = null,
    val automaticShutdownSeconds: Int? = null,
    val statusLedEnabled: Boolean? = null,
    val statusLedBrightness: Int? = null,
    val statusBuzzerEnabled: Boolean? = null,
    val statusBuzzerVolume: Int? = null,
    val externalCommandEnabled: Boolean? = null
)

internal fun UvirSensorRuntimeInfo.updatedFrom(
    json: JSONObject
): UvirSensorRuntimeInfo =
    copy(
        settingsUpdatedAtMs = json.longOrPrevious("settings_updated_at_ms", settingsUpdatedAtMs),
        sensorSettingsSchemaVersion =
            json.intOrPrevious(
                "sensor_settings_schema",
                sensorSettingsSchemaVersion
            ),
        protocol = json.stringOrPrevious("protocol", protocol),
        deviceId = json.stringOrPrevious("device_id", deviceId),
        boardName = json.stringOrPrevious("board", boardName),
        chipModel = json.stringOrPrevious("chip_model", chipModel),
        chipCores = json.intOrPrevious("chip_cores", chipCores),
        cpuFrequencyMhz =
            json.intOrPrevious("cpu_frequency_mhz", cpuFrequencyMhz),
        sensorName = json.stringOrPrevious("sensor", sensorName),
        firmwareVersion =
            json.stringOrPrevious("firmware", firmwareVersion),
        unit = json.stringOrPrevious("unit", unit),
        calibrationKind =
            json.stringOrPrevious("calibration", calibrationKind),
        visibleCalibrationFactor =
            json.doubleOrPrevious(
                "visible_calibration_factor",
                visibleCalibrationFactor
            ),
        uvCalibrationFactor =
            json.doubleOrPrevious(
                "uv_calibration_factor",
                uvCalibrationFactor
            ),
        sensorAvailable =
            json.booleanOrPrevious("sensor_available", sensorAvailable),
        uvAvailable =
            json.booleanOrPrevious("uv_available", uvAvailable),
        i2cSdaPin = json.intOrPrevious("i2c_sda_pin", i2cSdaPin),
        i2cSclPin = json.intOrPrevious("i2c_scl_pin", i2cSclPin),
        visibleSensorModel =
            json.stringOrPrevious("visible_sensor_model", visibleSensorModel),
        visibleSensorI2cAddress =
            json.intOrPrevious("visible_sensor_i2c_address", visibleSensorI2cAddress),
        visibleSensorBusOk =
            json.booleanOrPrevious("visible_sensor_bus_ok", visibleSensorBusOk),
        visibleSensorDriverOk =
            json.booleanOrPrevious("visible_sensor_driver_ok", visibleSensorDriverOk),
        visibleSensorReadOk =
            json.booleanOrPrevious("visible_sensor_read_ok", visibleSensorReadOk),
        continuousSampleAgeMs =
            json.longOrPrevious("continuous_sample_age_ms", continuousSampleAgeMs),
        uvSensorModel =
            json.stringOrPrevious("uv_sensor_model", uvSensorModel),
        uvSensorI2cAddress =
            json.intOrPrevious("uv_sensor_i2c_address", uvSensorI2cAddress),
        uvSensorBusOk =
            json.booleanOrPrevious("uv_sensor_bus_ok", uvSensorBusOk),
        uvSensorDriverOk =
            json.booleanOrPrevious("uv_sensor_driver_ok", uvSensorDriverOk),
        uvSensorReadOk =
            json.booleanOrPrevious("uv_sensor_read_ok", uvSensorReadOk),
        rtcModel = json.stringOrPrevious("rtc_model", rtcModel),
        rtcI2cAddress =
            json.intOrPrevious("rtc_i2c_address", rtcI2cAddress),
        rtcBusOk = json.booleanOrPrevious("rtc_bus_ok", rtcBusOk),
        rtcDriverOk =
            json.booleanOrPrevious("rtc_driver_ok", rtcDriverOk),
        sdAdapterModel =
            json.stringOrPrevious("sd_adapter_model", sdAdapterModel),
        sdBus = json.stringOrPrevious("sd_bus", sdBus),
        sdCsPin = json.intOrPrevious("sd_cs_pin", sdCsPin),
        sdSckPin = json.intOrPrevious("sd_sck_pin", sdSckPin),
        sdMisoPin = json.intOrPrevious("sd_miso_pin", sdMisoPin),
        sdMosiPin = json.intOrPrevious("sd_mosi_pin", sdMosiPin),
        statusLedControlAvailable =
            json.booleanOrPrevious(
                "status_led_control_available",
                statusLedControlAvailable
            ),
        statusLedRedPin =
            json.intOrPrevious("status_led_red_pin", statusLedRedPin),
        statusLedGreenPin =
            json.intOrPrevious("status_led_green_pin", statusLedGreenPin),
        operationLedBluePin =
            json.intOrPrevious("operation_led_blue_pin", operationLedBluePin),
        statusBuzzerControlAvailable =
            json.booleanOrPrevious(
                "status_buzzer_control_available",
                statusBuzzerControlAvailable
            ),
        statusBuzzerPin =
            json.intOrPrevious("status_buzzer_pin", statusBuzzerPin),
        externalInputReadOk =
            json.booleanOrPrevious("external_input_read_ok", externalInputReadOk),
        externalInputPin =
            json.intOrPrevious("external_input_pin", externalInputPin),
        externalInputActive =
            json.booleanOrPrevious("external_input_active", externalInputActive),
        uptimeMs = json.longOrPrevious("uptime_ms", uptimeMs),
        streaming = json.booleanOrPrevious("streaming", streaming),
        appConnected = json.booleanOrPrevious("app_connected", appConnected),
        operationActive = json.booleanOrPrevious("operation_active", operationActive),
        streamIntervalMs =
            json.longOrPrevious("stream_interval_ms", streamIntervalMs),
        samplingSamplesPerMeasurement =
            json.intOrPrevious(
                "samples_per_result",
                samplingSamplesPerMeasurement
            ),
        samplingSpacingMs =
            json.longOrPrevious(
                "sample_spacing_ms",
                samplingSpacingMs
            ),
        samplingDiscardExtremes =
            json.booleanOrPrevious(
                "extremes_discarded",
                samplingDiscardExtremes
            ),
        activeTransport =
            json.stringOrPrevious("active_transport", activeTransport),
        wifiConfigured =
            json.booleanOrPrevious("wifi_configured", wifiConfigured),
        wifiEnabled =
            json.booleanOrPrevious("wifi_enabled", wifiEnabled),
        wifiConnected =
            json.booleanOrPrevious("wifi_connected", wifiConnected),
        wifiNetwork =
            json.stringOrPrevious("wifi_network", wifiNetwork),
        wifiHostname =
            json.stringOrPrevious("wifi_hostname", wifiHostname),
        wifiAddress =
            json.stringOrPrevious("wifi_ip", wifiAddress),
        wifiRssiDbm =
            json.intOrPrevious("wifi_rssi_dbm", wifiRssiDbm),
        bluetoothEnabled =
            json.booleanOrPrevious("bluetooth_enabled", bluetoothEnabled),
        bluetoothConnected =
            json.booleanOrPrevious(
                "bluetooth_connected",
                bluetoothConnected
            ),
        bluetoothName =
            json.stringOrPrevious("bluetooth_name", bluetoothName),
        bluetoothRssiDelta =
            json.intOrPrevious(
                "bluetooth_rssi_delta",
                bluetoothRssiDelta
            ),
        internetEnabled =
            json.booleanOrPrevious("internet_enabled", internetEnabled),
        internetUsePrimaryWifi =
            json.booleanOrPrevious(
                "internet_use_primary_wifi",
                internetUsePrimaryWifi
            ),
        internetBrokerHost =
            json.stringOrPrevious("internet_relay_host", internetBrokerHost),
        internetBrokerPort =
            json.intOrPrevious("internet_relay_port", internetBrokerPort),
        internetBrokerConnected =
            json.booleanOrPrevious(
                "internet_relay_connected",
                internetBrokerConnected
            ),
        integrationMs =
            json.doubleOrPrevious("integration_ms", integrationMs),
        gain = json.doubleOrPrevious("gain", gain),
        sampleSequence =
            json.longOrPrevious("seq", sampleSequence),
        flashSizeBytes =
            json.longOrPrevious("flash_size_bytes", flashSizeBytes),
        appPartitionSizeBytes =
            json.longOrPrevious(
                "app_partition_size_bytes",
                appPartitionSizeBytes
            ),
        firmwareSizeBytes =
            json.longOrPrevious("firmware_size_bytes", firmwareSizeBytes),
        freeAppPartitionBytes =
            json.longOrPrevious(
                "free_app_partition_bytes",
                freeAppPartitionBytes
            ),
        heapSizeBytes =
            json.longOrPrevious("heap_size_bytes", heapSizeBytes),
        freeHeapBytes =
            json.longOrPrevious("free_heap_bytes", freeHeapBytes),
        filesystemTotalBytes =
            json.longOrPrevious(
                "filesystem_total_bytes",
                filesystemTotalBytes
            ),
        filesystemUsedBytes =
            json.longOrPrevious(
                "filesystem_used_bytes",
                filesystemUsedBytes
            ),
        storageBackend =
            json.stringOrPrevious("storage_backend", storageBackend),
        storageRecordSizeBytes =
            json.longOrPrevious(
                "storage_record_size_bytes",
                storageRecordSizeBytes
            ),
        sdAvailable =
            json.booleanOrPrevious("sd_available", sdAvailable),
        sdForeign =
            json.booleanOrPrevious("sd_foreign", sdForeign),
        sdType = json.stringOrPrevious("sd_type", sdType),
        sdTotalBytes =
            json.longOrPrevious("sd_total_bytes", sdTotalBytes),
        sdUsedBytes =
            json.longOrPrevious("sd_used_bytes", sdUsedBytes),
        sdFreeBytes =
            json.longOrPrevious("sd_free_bytes", sdFreeBytes),
        sdRecordCapacityTotal =
            json.intOrPrevious(
                "sd_record_capacity_total",
                sdRecordCapacityTotal
            ),
        sdRecordCapacityFree =
            json.intOrPrevious(
                "sd_record_capacity_free",
                sdRecordCapacityFree
            ),
        sdInvalidRecords =
            json.intOrPrevious("sd_invalid_records", sdInvalidRecords),
        sdMountErrors =
            json.intOrPrevious("sd_mount_errors", sdMountErrors),
        sdWriteErrors =
            json.intOrPrevious("sd_write_errors", sdWriteErrors),
        sdReadWriteOk =
            json.booleanOrPrevious("sd_read_write_ok", sdReadWriteOk),
        framAvailable =
            json.booleanOrPrevious("fram_available", framAvailable),
        framModel = json.stringOrPrevious("fram_model", framModel),
        framI2cAddress =
            json.intOrPrevious("fram_i2c_address", framI2cAddress),
        framBusOk = json.booleanOrPrevious("fram_bus_ok", framBusOk),
        framReadWriteOk =
            json.booleanOrPrevious("fram_read_write_ok", framReadWriteOk),
        framQueueAvailable =
            json.booleanOrPrevious("fram_queue_available", framQueueAvailable),
        framCapacityBytes =
            json.longOrPrevious("fram_capacity_bytes", framCapacityBytes),
        framRecordCapacity =
            json.intOrPrevious("fram_record_capacity", framRecordCapacity),
        framRecordsUsed =
            json.intOrPrevious("fram_records_used", framRecordsUsed),
        timeSynced =
            json.booleanOrPrevious("time_synced", timeSynced),
        rtcAvailable =
            json.booleanOrPrevious("rtc_available", rtcAvailable),
        rtcValid =
            json.booleanOrPrevious("rtc_valid", rtcValid),
        rtcOscillatorStopped =
            json.booleanOrPrevious(
                "rtc_oscillator_stopped",
                rtcOscillatorStopped
            ),
        rtcReadOk =
            json.booleanOrPrevious("rtc_read_ok", rtcReadOk),
        rtcCurrentTimeMs =
            json.longOrPrevious("rtc_current_time_ms", rtcCurrentTimeMs),
        timeSource =
            json.stringOrPrevious("time_source", timeSource),
        rtcReadErrors =
            json.intOrPrevious("rtc_read_errors", rtcReadErrors),
        rtcWriteErrors =
            json.intOrPrevious("rtc_write_errors", rtcWriteErrors),
        offlineStorageAvailable =
            json.booleanOrPrevious(
                "offline_storage_available",
                offlineStorageAvailable
            ),
        offlineCapacity =
            json.intOrPrevious("offline_capacity", offlineCapacity),
        offlineUsed =
            json.intOrPrevious("offline_used", offlineUsed),
        offlineRemaining =
            json.intOrPrevious("offline_remaining", offlineRemaining),
        offlineStorageFull =
            json.booleanOrPrevious(
                "offline_storage_full",
                offlineStorageFull
            ),
        offlineAcquisitions =
            json.intOrPrevious("offline_acquisitions", offlineAcquisitions),
        offlineAlerts =
            json.intOrPrevious("offline_alerts", offlineAlerts),
        offlineAlertRepeatSeconds =
            json.intOrPrevious(
                "offline_alert_repeat_seconds",
                offlineAlertRepeatSeconds
            ),
        alertMonitoringEnabled =
            json.booleanOrPrevious(
                "alert_monitoring_enabled",
                alertMonitoringEnabled
            ),
        alertRecordingEnabled =
            json.booleanOrPrevious("alert_recording_enabled", alertRecordingEnabled),
        alertStartDelaySeconds =
            json.longOrPrevious("alert_start_delay_seconds", alertStartDelaySeconds),
        alertDurationSeconds =
            json.longOrPrevious("alert_duration_seconds", alertDurationSeconds),
        alertMaxRegistrations =
            json.intOrPrevious("alert_max_registrations", alertMaxRegistrations),
        alertCompletedRegistrations =
            json.intOrPrevious("alert_completed_registrations", alertCompletedRegistrations),
        alertSessionId =
            json.longOrPrevious("alert_session_id", alertSessionId),
        alertRules =
            json.alertRulesOrPrevious("alert_rules", alertRules),
        offlineErrors =
            json.intOrPrevious("offline_errors", offlineErrors),
        automaticJobReadback =
            if (json.optString("type") == "hello") parseAutomaticJobReadback(json) else automaticJobReadback,
        offlineRecording =
            json.booleanOrPrevious("offline_recording", offlineRecording),
        offlineSessionId =
            json.longOrPrevious("offline_session_id", offlineSessionId),
        offlineCompleted =
            json.intOrPrevious("offline_completed", offlineCompleted),
        offlineNextAtMs =
            json.longOrPrevious("offline_next_ms", offlineNextAtMs),
        offlineExternalCommand =
            json.booleanOrPrevious(
                "offline_external_command",
                offlineExternalCommand
            ),
        offlineConditionPlan = if (json.has("offline_condition_plan"))
            decodeConditionalAcquisitionPlan(json.optString("offline_condition_plan")) else offlineConditionPlan,
        offlineConditionWaiting = json.booleanOrPrevious("offline_condition_waiting", offlineConditionWaiting),
        offlineEndAtMs = json.longOrPrevious("offline_end_ms", offlineEndAtMs),
        offlineStartedAtMs = json.longOrPrevious("offline_started_ms", offlineStartedAtMs),
        autonomousRecordingEnabled =
            json.booleanOrPrevious(
                "autonomous_recording_enabled",
                autonomousRecordingEnabled
            ),
        automaticShutdownEnabled =
            json.booleanOrPrevious(
                "automatic_shutdown_enabled",
                automaticShutdownEnabled
            ),
        automaticShutdownSeconds =
            json.intOrPrevious(
                "automatic_shutdown_seconds",
                automaticShutdownSeconds
            ),
        statusLedEnabled =
            json.booleanOrPrevious("status_led_enabled", statusLedEnabled),
        statusLedBrightness =
            json.intOrPrevious(
                "status_led_brightness",
                statusLedBrightness
            ),
        statusBuzzerEnabled =
            json.booleanOrPrevious(
                "status_buzzer_enabled",
                statusBuzzerEnabled
            ),
        statusBuzzerVolume =
            json.intOrPrevious(
                "status_buzzer_volume",
                statusBuzzerVolume
            ),
        externalCommandEnabled =
            json.booleanOrPrevious(
                "external_command_enabled",
                externalCommandEnabled
            )
    )

internal fun wifiSignalLevel(rssiDbm: Int): Int =
    when {
        rssiDbm >= -50 -> 4
        rssiDbm >= -65 -> 3
        rssiDbm >= -75 -> 2
        else -> 1
    }

internal fun bluetoothSignalLevel(rssiDelta: Int): Int =
    when {
        rssiDelta >= 8 -> 4
        rssiDelta >= 0 -> 3
        rssiDelta >= -8 -> 2
        else -> 1
    }

internal fun wifiSignalPercentage(rssiDbm: Int): Int =
    ((rssiDbm.coerceIn(-100, -50) + 100) * 2)

internal fun bluetoothSignalPercentage(rssiDelta: Int): Int =
    (((rssiDelta.coerceIn(-16, 8) + 16) * 100) / 24)

private fun JSONObject.stringOrPrevious(
    key: String,
    previous: String
): String =
    if (has(key) && !isNull(key)) {
        optString(key)
    } else {
        previous
    }

private fun JSONObject.booleanOrPrevious(
    key: String,
    previous: Boolean?
): Boolean? =
    if (!has(key)) previous else if (isNull(key)) null else optBoolean(key)

private fun JSONObject.intOrPrevious(
    key: String,
    previous: Int?
): Int? =
    if (!has(key)) previous else if (isNull(key)) null else optInt(key)

private fun JSONObject.longOrPrevious(
    key: String,
    previous: Long?
): Long? =
    if (!has(key)) previous else if (isNull(key)) null else optLong(key)

private fun JSONObject.doubleOrPrevious(
    key: String,
    previous: Double?
): Double? =
    if (!has(key)) {
        previous
    } else if (isNull(key)) {
        null
    } else {
        optDouble(key).takeIf { it.isFinite() }
    }

private fun JSONObject.alertRulesOrPrevious(
    key: String,
    previous: List<ThresholdAlertRule>?
): List<ThresholdAlertRule>? {
    if (!has(key)) return previous
    if (isNull(key)) return null
    val values = optJSONArray(key) ?: return previous
    return values.toThresholdAlertRules()
}

private fun JSONArray.toThresholdAlertRules(): List<ThresholdAlertRule> =
    buildList {
        for (index in 0 until length()) {
            val value = optJSONObject(index) ?: continue
            val metric =
                runCatching {
                    ThresholdAlertMetric.valueOf(
                        value.optString("metric").trim()
                    )
                }.getOrNull() ?: continue
            val direction =
                runCatching {
                    ThresholdAlertDirection.valueOf(
                        value.optString("direction").trim()
                    )
                }.getOrNull() ?: continue
            val threshold =
                value.optDouble("threshold", Double.NaN)
                    .takeIf { it.isFinite() && it >= 0.0 }
                    ?.toFloat()
                    ?: continue
            add(
                ThresholdAlertRule(
                    metric = metric,
                    enabled = true,
                    direction = direction,
                    threshold = threshold
                )
            )
        }
    }
