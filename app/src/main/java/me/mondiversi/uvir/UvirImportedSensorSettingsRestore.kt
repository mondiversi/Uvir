package me.mondiversi.uvir

import android.content.Context
import kotlinx.coroutines.delay
import kotlin.math.abs

internal enum class UvirImportedSensorRestoreStatus {
    RESTORED,
    NOT_CONNECTED,
    FAILED
}

private interface UvirConnectedSensorTransport {
    val firmwareVersion: String
    fun send(commands: List<String>): Boolean
    fun configureInternet(configuration: SensorInternetConfiguration): Boolean
    fun configureWifi(ssid: String, password: String): Boolean
    fun configureRadios(settings: SensorRadioSettings): Boolean
    fun currentSettings(): UvirSensorSettingsSnapshot?
}

internal suspend fun restoreImportedSensorSettingsToConnectedSensor(
    imported: UvirImportedSensorSettings,
    usbSensorManager: UvirUsbSensorManager,
    wirelessSensorManager: UvirWirelessSensorManager
): UvirImportedSensorRestoreStatus {
    val transport = connectedTransportForImportedSensor(
        hardwareUid = imported.hardwareUid,
        usbSensorManager = usbSensorManager,
        wirelessSensorManager = wirelessSensorManager
    ) ?: return UvirImportedSensorRestoreStatus.NOT_CONNECTED

    if (
        !firmwareIsCurrentForApp(transport.firmwareVersion) ||
        !firmwareSupportsSensorSettingsSnapshot(transport.firmwareVersion) ||
        !firmwareSupportsSensorCalibration(transport.firmwareVersion)
    ) {
        return UvirImportedSensorRestoreStatus.FAILED
    }

    val activeSettings = transport.currentSettings()
        ?: return UvirImportedSensorRestoreStatus.FAILED
    val target = imported.settings.copy(
        firmwareVersion = activeSettings.firmwareVersion,
        alertMonitoringEnabled = activeSettings.alertMonitoringEnabled,
        alertSessionId = activeSettings.alertSessionId
    )
    val alertSettings = ThresholdAlertSettings(
        enabled = target.alertMonitoringEnabled,
        rules = target.alertRules,
        repeatSeconds = target.alertRepeatSeconds,
        recordEvents = target.alertRecordingEnabled ?: true,
        startDelaySeconds = target.alertStartDelaySeconds,
        durationSeconds = target.alertDurationSeconds,
        maxRegistrations = target.alertMaxRegistrations,
        sound = ThresholdAlertSound.SILENT,
        volume = 0
    )
    val commands = buildList {
        add(sensorTimeCommand())
        add(sensorParametersCommand(target.sensorParameters))
        add(sensorCalibrationCommand(target.calibrationSettings))
        add(sensorSamplingCommand(target.acquisitionParameters))
        addAll(sensorAlertCommands(alertSettings, target.alertSessionId))
        add("HELLO")
    }
    if (!transport.send(commands)) {
        return UvirImportedSensorRestoreStatus.FAILED
    }

    val confirmed = awaitImportedSensorSettings(transport, target)
    if (!confirmed) {
        return UvirImportedSensorRestoreStatus.FAILED
    }

    val internet = SensorInternetConfiguration(
        enabled = imported.credentials.internetEnabled,
        usePrimaryWifi = imported.credentials.internetUsePrimaryWifi,
        wifiSsid = imported.credentials.internetWifiSsid,
        wifiPassword = imported.credentials.internetWifiPassword,
        relayHost = imported.credentials.internetRelayHost,
        relayPort = imported.credentials.internetRelayPort,
        mqttUsername = imported.credentials.internetMqttUsername,
        mqttPassword = imported.credentials.internetMqttPassword
    )
    if (!internet.isComplete || !transport.configureInternet(internet)) {
        return UvirImportedSensorRestoreStatus.FAILED
    }

    if (imported.credentials.wifiSsid.isNotBlank()) {
        if (
            validatedSensorWifiConfiguration(
                imported.credentials.wifiSsid,
                imported.credentials.wifiPassword
            ) == null ||
            !transport.configureWifi(
                imported.credentials.wifiSsid,
                imported.credentials.wifiPassword
            )
        ) {
            return UvirImportedSensorRestoreStatus.FAILED
        }
    }

    if (
        !transport.configureRadios(
            SensorRadioSettings(
                wifiEnabled = target.wifiEnabled,
                bluetoothEnabled = target.bluetoothEnabled
            )
        )
    ) {
        return UvirImportedSensorRestoreStatus.FAILED
    }

    return UvirImportedSensorRestoreStatus.RESTORED
}

internal fun persistImportedSensorAssociation(
    context: Context,
    imported: UvirImportedSensorSettings
): Boolean =
    if (imported.wasActive) {
        UvirSensorCredentialStore.save(context, imported.credentials)
    } else {
        UvirSensorCredentialStore.restoreAssociatedSensor(
            context,
            imported.credentials
        )
    }

private fun connectedTransportForImportedSensor(
    hardwareUid: String,
    usbSensorManager: UvirUsbSensorManager,
    wirelessSensorManager: UvirWirelessSensorManager
): UvirConnectedSensorTransport? {
    val usbState = usbSensorManager.state.value
    val usbHardwareUid = usbState.credentials.deviceId
        .ifBlank { usbState.deviceId.orEmpty() }
    if (
        usbState.status == UsbSensorConnectionStatus.CONNECTED &&
        usbState.appConnectionConfirmed &&
        usbHardwareUid.equals(hardwareUid, ignoreCase = true)
    ) {
        return object : UvirConnectedSensorTransport {
            override val firmwareVersion: String =
                usbState.runtimeInfo.firmwareVersion.ifBlank {
                    usbState.firmwareVersion.orEmpty()
                }

            override fun send(commands: List<String>): Boolean =
                usbSensorManager.sendSensorControlCommands(commands)

            override fun configureInternet(
                configuration: SensorInternetConfiguration
            ): Boolean = usbSensorManager.configureInternet(configuration)

            override fun configureWifi(ssid: String, password: String): Boolean =
                usbSensorManager.configureWifiNetwork(ssid, password)

            override fun configureRadios(settings: SensorRadioSettings): Boolean =
                usbSensorManager.configureWirelessTransportsEnabled(settings)

            override fun currentSettings(): UvirSensorSettingsSnapshot? =
                usbSensorManager.state.value.runtimeInfo
                    .toSensorSettingsSnapshotOrNull()
        }
    }

    val wirelessState = wirelessSensorManager.state.value
    if (
        wirelessState.status == WirelessSensorConnectionStatus.CONNECTED &&
        wirelessState.appConnectionConfirmed &&
        wirelessState.deviceId.orEmpty()
            .equals(hardwareUid, ignoreCase = true)
    ) {
        return object : UvirConnectedSensorTransport {
            override val firmwareVersion: String =
                wirelessState.runtimeInfo.firmwareVersion.ifBlank {
                    wirelessState.firmwareVersion.orEmpty()
                }

            override fun send(commands: List<String>): Boolean =
                wirelessSensorManager.sendSensorControlCommands(commands)

            override fun configureInternet(
                configuration: SensorInternetConfiguration
            ): Boolean = wirelessSensorManager.configureInternet(configuration)

            override fun configureWifi(ssid: String, password: String): Boolean =
                wirelessSensorManager.configureWifiNetwork(ssid, password)

            override fun configureRadios(settings: SensorRadioSettings): Boolean =
                wirelessSensorManager.configureWirelessTransportsEnabled(settings)

            override fun currentSettings(): UvirSensorSettingsSnapshot? =
                wirelessSensorManager.state.value.runtimeInfo
                    .toSensorSettingsSnapshotOrNull()
        }
    }
    return null
}

private suspend fun awaitImportedSensorSettings(
    transport: UvirConnectedSensorTransport,
    target: UvirSensorSettingsSnapshot,
    timeoutMs: Long = 5_000L
): Boolean {
    val attempts = (timeoutMs / 100L).toInt().coerceAtLeast(1)
    repeat(attempts) {
        val current = transport.currentSettings()
        if (current != null && sensorBackedSettingsMatch(current, target)) {
            return true
        }
        delay(100L)
    }
    return false
}

internal fun sensorBackedSettingsMatch(
    current: UvirSensorSettingsSnapshot,
    target: UvirSensorSettingsSnapshot
): Boolean =
    current.sensorParameters == target.sensorParameters &&
        current.acquisitionParameters == target.acquisitionParameters &&
        abs(
            current.calibrationSettings.visibleFactor -
                target.calibrationSettings.visibleFactor
        ) < 0.0001f &&
        abs(
            current.calibrationSettings.uvFactor -
                target.calibrationSettings.uvFactor
        ) < 0.0001f &&
        current.alertRepeatSeconds == target.alertRepeatSeconds &&
        current.alertRecordingEnabled == target.alertRecordingEnabled &&
        current.alertStartDelaySeconds == target.alertStartDelaySeconds &&
        current.alertDurationSeconds == target.alertDurationSeconds &&
        current.alertMaxRegistrations == target.alertMaxRegistrations &&
        current.alertRules.enabledRulesForRestore() ==
            target.alertRules.enabledRulesForRestore()

private fun List<ThresholdAlertRule>.enabledRulesForRestore(): List<ThresholdAlertRule> =
    filter(ThresholdAlertRule::enabled)
        .sortedBy { it.metric.ordinal }
