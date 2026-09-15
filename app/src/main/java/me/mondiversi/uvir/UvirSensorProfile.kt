package me.mondiversi.uvir

internal const val MAX_SENSOR_DISPLAY_NAME_LENGTH = 48

data class UvirSensorProfile(
    val id: Long,
    val hardwareUid: String,
    val displayName: String,
    val firstSeenAt: Long,
    val lastSeenAt: Long
)

internal fun normalizeSensorDisplayName(
    value: String,
    fallbackHardwareUid: String
): String =
    value
        .replace(Regex("\\s+"), " ")
        .trim()
        .take(MAX_SENSOR_DISPLAY_NAME_LENGTH)
        .ifBlank { fallbackHardwareUid.trim() }
