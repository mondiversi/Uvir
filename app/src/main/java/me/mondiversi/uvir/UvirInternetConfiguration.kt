package me.mondiversi.uvir

import java.nio.charset.StandardCharsets

internal const val DEFAULT_UVIR_RELAY_PORT = 8883

internal data class SensorInternetConfiguration(
    val enabled: Boolean = false,
    val usePrimaryWifi: Boolean = true,
    val wifiSsid: String = "",
    val wifiPassword: String = "",
    val relayHost: String = "",
    val relayPort: Int = DEFAULT_UVIR_RELAY_PORT,
    val mqttUsername: String = "",
    val mqttPassword: String = ""
) {
    val isComplete: Boolean
        get() =
            !enabled ||
                (
                    relayHost.isValidRelayHost() &&
                        relayPort in 1..65_535 &&
                        mqttUsername.isNotBlank() &&
                        mqttUsername.toByteArray(StandardCharsets.UTF_8).size <= 128 &&
                        mqttPassword.isNotBlank() &&
                        mqttPassword.toByteArray(StandardCharsets.UTF_8).size <= 256 &&
                        (
                            usePrimaryWifi ||
                                validatedSensorWifiConfiguration(
                                    wifiSsid,
                                    wifiPassword
                                ) != null
                            )
                    )

    val protocolCommand: String
        get() =
            listOf(
                "INTERNET_CONFIG",
                if (enabled) "ON" else "OFF",
                if (usePrimaryWifi) "PRIMARY" else "SECONDARY",
                wifiSsid.toHexUtf8OrDash(),
                wifiPassword.toHexUtf8OrDash(),
                relayHost.trim().toHexUtf8OrDash(),
                relayPort.toString(),
                mqttUsername.toHexUtf8OrDash(),
                mqttPassword.toHexUtf8OrDash()
            ).joinToString(" ")
}

internal fun validatedSensorInternetConfiguration(
    enabled: Boolean,
    usePrimaryWifi: Boolean,
    wifiSsid: String,
    wifiPassword: String,
    relayHost: String,
    relayPortText: String,
    mqttUsername: String,
    mqttPassword: String
): SensorInternetConfiguration? {
    val normalizedHost = relayHost.trim()
    val normalizedPort = relayPortText.trim().toIntOrNull()
        ?: return null
    val normalizedWifi =
        if (!enabled || usePrimaryWifi) {
            null
        } else {
            validatedSensorWifiConfiguration(wifiSsid, wifiPassword)
                ?: return null
        }

    return SensorInternetConfiguration(
        enabled = enabled,
        usePrimaryWifi = usePrimaryWifi,
        wifiSsid = normalizedWifi?.ssid.orEmpty(),
        wifiPassword = normalizedWifi?.password.orEmpty(),
        relayHost = normalizedHost,
        relayPort = normalizedPort,
        mqttUsername = mqttUsername.trim(),
        mqttPassword = mqttPassword
    ).takeIf { it.isComplete }
}

internal fun UvirSensorCredentials.withInternetConfiguration(
    configuration: SensorInternetConfiguration
): UvirSensorCredentials =
    copy(
        internetEnabled = configuration.enabled,
        internetUsePrimaryWifi = configuration.usePrimaryWifi,
        internetWifiSsid = configuration.wifiSsid,
        internetWifiPassword = configuration.wifiPassword,
        internetRelayHost = configuration.relayHost,
        internetRelayPort = configuration.relayPort,
        internetMqttUsername = configuration.mqttUsername,
        internetMqttPassword = configuration.mqttPassword
    )

private fun String.isValidRelayHost(): Boolean {
    if (isBlank() || length > 253 || contains(Regex("[\\s/:]"))) {
        return false
    }
    return split('.').all { label ->
        label.isNotBlank() &&
            label.length <= 63 &&
            label.first() != '-' &&
            label.last() != '-' &&
            label.all { it.isLetterOrDigit() || it == '-' }
    }
}

private fun String.toHexUtf8OrDash(): String {
    if (isEmpty()) return "-"
    return toByteArray(StandardCharsets.UTF_8).joinToString("") { byte ->
        "%02X".format(byte.toInt() and 0xFF)
    }
}
