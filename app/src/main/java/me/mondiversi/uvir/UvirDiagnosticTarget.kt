package me.mondiversi.uvir

/** A diagnostic action must never fall back to another connected peer. */
internal fun uvirDiagnosticPeerMatches(
    selectedDeviceId: String,
    connectedDeviceId: String,
    connected: Boolean,
    confirmed: Boolean
): Boolean = selectedDeviceId.isNotBlank() && connected && confirmed &&
    normalizeSensorDeviceId(selectedDeviceId) == normalizeSensorDeviceId(connectedDeviceId)
