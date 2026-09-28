package me.mondiversi.uvir

/** Live authenticated connection required; simulated and busy devices are not reset targets. */
internal fun uvirSensorCanReset(indicator: UvirStatusIndicator?): Boolean =
    indicator?.dot == UvirStatusDot.GREEN && !indicator.pulses

internal fun uvirSensorRuntimeIdle(info: UvirSensorRuntimeInfo): Boolean =
    info.offlineRecording != true && info.alertMonitoringEnabled != true

/** Normalize once, retain profile order and never infer a target from the selected device. */
internal fun uvirSensorResetTargets(
    requested: Collection<String>,
    profiles: List<UvirSensorProfile>,
    indicators: Map<String, UvirStatusIndicator>
): List<String> {
    val requestedIds = requested.map(::normalizeSensorDeviceId).toSet()
    return profiles.filter {
        normalizeSensorDeviceId(it.hardwareUid) in requestedIds &&
            uvirSensorCanReset(indicators[normalizeSensorDeviceId(it.hardwareUid)])
    }.distinctBy { normalizeSensorDeviceId(it.hardwareUid) }.map { it.hardwareUid }
}

/** One UID, one verified transport. No fallback to an unrelated connected sensor. */
internal fun restoreConnectedUvirSensor(
    deviceId: String,
    usb: UvirUsbSensorManager,
    wireless: UvirWirelessSensorManager?,
    timeoutMs: Long = 8_000L
): Boolean {
    val usbState = usb.state.value
    if (uvirDiagnosticPeerMatches(deviceId, usbState.deviceId.orEmpty(),
            usbState.status == UsbSensorConnectionStatus.CONNECTED, usbState.appConnectionConfirmed)) {
        return uvirSensorRuntimeIdle(usbState.runtimeInfo) &&
            usb.restoreDefaultsAndPowerOffAwait(timeoutMs, expectedHardwareUid = deviceId)
    }
    val worker = wireless ?: return false
    val state = worker.state.value
    return uvirDiagnosticPeerMatches(deviceId, state.deviceId.orEmpty(),
        state.status == WirelessSensorConnectionStatus.CONNECTED, state.appConnectionConfirmed) &&
        uvirSensorRuntimeIdle(state.runtimeInfo) &&
        worker.restoreDefaultsAndPowerOffAwait(timeoutMs, expectedHardwareUid = deviceId)
}
