package me.mondiversi.uvir

internal const val SENSOR_SETTINGS_SCHEMA_VERSION = 1
private const val SENSOR_SETTINGS_SNAPSHOT_MINIMUM_FIRMWARE = "0.5.65"

/** A simulation must restore phone preferences, never the real sensor's cache.
 * This only selects initial UI state; confirmed real-sensor hydration is unchanged. */
internal fun readInitialSensorSettings(
    useFakeSensorData: Boolean,
    readAssociatedSensorSettings: () -> UvirSensorSettingsSnapshot?
): UvirSensorSettingsSnapshot? =
    if (useFakeSensorData) null else readAssociatedSensorSettings()

data class UvirSensorSettingsSnapshot(
    val schemaVersion: Int,
    val firmwareVersion: String,
    val sensorParameters: SensorParameters,
    val acquisitionParameters: AcquisitionParameters,
    val calibrationSettings: SensorCalibrationSettings,
    val alertMonitoringEnabled: Boolean,
    val alertRepeatSeconds: Int,
    val alertSessionId: Long,
    val alertRules: List<ThresholdAlertRule>,
    val wifiEnabled: Boolean,
    val bluetoothEnabled: Boolean,
    val internetEnabled: Boolean,
    val internetUsePrimaryWifi: Boolean,
    val wifiSsid: String,
    val internetRelayHost: String,
    val internetRelayPort: Int,
    val lastSyncedAt: Long = 0L
)

internal fun firmwareSupportsSensorSettingsSnapshot(
    version: String
): Boolean =
    compareFirmwareVersions(
        version,
        SENSOR_SETTINGS_SNAPSHOT_MINIMUM_FIRMWARE
    ) >= 0

internal fun UvirSensorRuntimeInfo.toSensorSettingsSnapshotOrNull():
    UvirSensorSettingsSnapshot? {
    val schemaVersion = sensorSettingsSchemaVersion ?: return null
    if (schemaVersion < SENSOR_SETTINGS_SCHEMA_VERSION) return null

    return UvirSensorSettingsSnapshot(
        schemaVersion = schemaVersion,
        firmwareVersion = firmwareVersion,
        sensorParameters =
            SensorParameters(
                autonomousRecordingEnabled =
                    autonomousRecordingEnabled ?: return null,
                automaticShutdownEnabled =
                    automaticShutdownEnabled ?: return null,
                automaticShutdownSeconds =
                    (automaticShutdownSeconds ?: return null)
                        .coerceIn(60, 86_400),
                statusLedEnabled = statusLedEnabled ?: return null,
                statusLedBrightness =
                    (statusLedBrightness ?: return null).coerceIn(1, 100),
                statusBuzzerEnabled = statusBuzzerEnabled ?: return null,
                statusBuzzerVolume =
                    (statusBuzzerVolume ?: return null).coerceIn(1, 100)
            ),
        acquisitionParameters =
            AcquisitionParameters(
                samplesPerMeasurement =
                    (samplingSamplesPerMeasurement ?: return null)
                        .coerceIn(1, 21),
                sampleSpacingMs =
                    (samplingSpacingMs ?: return null)
                        .coerceIn(150L, 5_000L),
                discardExtremes =
                    samplingDiscardExtremes ?: return null
            ),
        calibrationSettings =
            SensorCalibrationSettings(
                visibleFactor =
                    (visibleCalibrationFactor ?: return null)
                        .toFloat()
                        .coerceIn(
                            MIN_SENSOR_CALIBRATION_FACTOR,
                            MAX_SENSOR_CALIBRATION_FACTOR
                        ),
                uvFactor =
                    (uvCalibrationFactor ?: return null)
                        .toFloat()
                        .coerceIn(
                            MIN_SENSOR_CALIBRATION_FACTOR,
                            MAX_SENSOR_CALIBRATION_FACTOR
                        )
            ),
        alertMonitoringEnabled = alertMonitoringEnabled ?: return null,
        alertRepeatSeconds =
            (offlineAlertRepeatSeconds ?: return null).coerceIn(1, 86_400),
        alertSessionId =
            (alertSessionId ?: return null).coerceAtLeast(0L),
        alertRules = alertRules ?: return null,
        wifiEnabled = wifiEnabled ?: return null,
        bluetoothEnabled = bluetoothEnabled ?: return null,
        internetEnabled = internetEnabled ?: return null,
        internetUsePrimaryWifi = internetUsePrimaryWifi ?: return null,
        wifiSsid = wifiNetwork,
        internetRelayHost = internetBrokerHost,
        internetRelayPort =
            (internetBrokerPort ?: return null).coerceIn(1, 65_535)
    )
}

internal fun UvirSensorSettingsSnapshot.sensorBackedAlertSettings(
    appSettings: ThresholdAlertSettings
): ThresholdAlertSettings {
    val sensorRulesByMetric = alertRules.associateBy { it.metric }
    val appRulesByMetric = appSettings.rules.associateBy { it.metric }
    val completeRules =
        ThresholdAlertMetric.entries.map { metric ->
            sensorRulesByMetric[metric]
                ?: appRulesByMetric[metric]?.copy(enabled = false)
                ?: ThresholdAlertRule(
                    metric = metric,
                    enabled = false,
                    direction = ThresholdAlertDirection.ABOVE,
                    threshold = 1f
                )
        }
    return appSettings.copy(
        enabled = alertMonitoringEnabled && alertRules.isNotEmpty(),
        rules = completeRules,
        repeatSeconds = alertRepeatSeconds
    )
}
