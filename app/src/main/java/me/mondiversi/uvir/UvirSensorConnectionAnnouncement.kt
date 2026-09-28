package me.mondiversi.uvir

// A disconnected transport can already have cleared its ID, or the selection
// can have moved on. Announce the sensor that was connected, not the next one.
internal fun uvirConnectionAnnouncementDeviceId(
    connected: Boolean,
    currentDeviceId: String,
    lastConnectedDeviceId: String
): String =
    if (connected) currentDeviceId.trim()
    else lastConnectedDeviceId.trim().ifBlank { currentDeviceId.trim() }

internal fun uvirConnectionAnnouncementSensorName(
    deviceId: String,
    profiles: List<UvirSensorProfile>
): String {
    val identity = deviceId.trim()
    if (identity.isBlank()) return ""
    return profiles.firstOrNull {
        it.hardwareUid.trim().equals(identity, ignoreCase = true)
    }?.displayName?.trim()?.takeIf { it.isNotBlank() } ?: identity
}
