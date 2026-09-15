package me.mondiversi.uvir

/**
 * Conservative software estimate for the complete sensor assembly.
 *
 * The ESP32 cannot measure its own supply current. These values combine the
 * ESP32 datasheet operating modes with the detected sensors and the configured
 * indicators. They are intentionally presented as estimates in the UI.
 */
internal data class UvirSensorPowerEstimate(
    val referenceVoltageVolts: Double,
    val currentMilliAmps: Double,
    val powerMilliWatts: Double,
    val energyForOneHourMilliWattHours: Double,
    val minimumCurrentMilliAmps: Double,
    val minimumPowerMilliWatts: Double,
    val peakCurrentMilliAmps: Double,
    val peakPowerMilliWatts: Double
)

internal fun estimateUvirSensorPower(
    selectedConnectionMode: SensorConnectionMode,
    sensorInfo: UvirSensorRuntimeInfo
): UvirSensorPowerEstimate {
    val referenceVoltageVolts = 5.0
    val activeConnectionMode =
        when (sensorInfo.activeTransport.lowercase()) {
            "wifi" -> SensorConnectionMode.WIFI
            "bluetooth" -> SensorConnectionMode.BLUETOOTH
            "internet" -> SensorConnectionMode.INTERNET
            "usb" -> SensorConnectionMode.USB
            else -> selectedConnectionMode
        }

    // Includes the ESP32 Dev Module and its normal board overhead. Wi-Fi and
    // Bluetooth values model an established link, not every short RF peak.
    val controllerCurrentMilliAmps =
        when (activeConnectionMode) {
            SensorConnectionMode.USB -> 35.0
            SensorConnectionMode.WIFI -> 105.0
            SensorConnectionMode.BLUETOOTH -> 95.0
            SensorConnectionMode.INTERNET -> 115.0
        }

    val samplingActive =
        sensorInfo.streaming == true ||
            sensorInfo.offlineRecording == true
    val samplingDutyCycle =
        if (samplingActive) {
            val integration = sensorInfo.integrationMs ?: 100.0
            val interval = sensorInfo.streamIntervalMs?.toDouble() ?: 500.0
            (integration / interval).coerceIn(0.05, 1.0)
        } else {
            0.0
        }
    val visibleSensorCurrentMilliAmps =
        when {
            sensorInfo.sensorAvailable == false -> 0.0
            samplingActive -> 5.0 * samplingDutyCycle
            else -> 0.05
        }
    val uvSensorCurrentMilliAmps =
        when {
            sensorInfo.uvAvailable != true -> 0.0
            samplingActive -> 1.6 * samplingDutyCycle
            else -> 0.01
        }

    val ledBrightness =
        (sensorInfo.statusLedBrightness ?: 10)
            .coerceIn(1, 100) / 100.0
    val statusLedCurrentMilliAmps =
        if (sensorInfo.statusLedEnabled == true) {
            // One steady RGB channel; an active operation can also light the
            // separate blue LED.
            4.0 * ledBrightness +
                if (sensorInfo.offlineRecording == true) {
                    4.0 * ledBrightness
                } else {
                    0.0
                }
        } else {
            0.0
        }

    val currentMilliAmps =
        controllerCurrentMilliAmps +
            visibleSensorCurrentMilliAmps +
            uvSensorCurrentMilliAmps +
            statusLedCurrentMilliAmps
    val minimumCurrentMilliAmps = 25.0
    // Covers an ESP32 Wi-Fi transmit peak plus both sensors, indicators and a
    // short buzzer event. Board and module tolerances can still vary.
    val peakCurrentMilliAmps = 300.0

    return UvirSensorPowerEstimate(
        referenceVoltageVolts = referenceVoltageVolts,
        currentMilliAmps = currentMilliAmps,
        powerMilliWatts = currentMilliAmps * referenceVoltageVolts,
        energyForOneHourMilliWattHours =
            currentMilliAmps * referenceVoltageVolts,
        minimumCurrentMilliAmps = minimumCurrentMilliAmps,
        minimumPowerMilliWatts =
            minimumCurrentMilliAmps * referenceVoltageVolts,
        peakCurrentMilliAmps = peakCurrentMilliAmps,
        peakPowerMilliWatts =
            peakCurrentMilliAmps * referenceVoltageVolts
    )
}
