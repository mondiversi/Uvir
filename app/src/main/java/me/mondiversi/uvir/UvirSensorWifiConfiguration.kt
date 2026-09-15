package me.mondiversi.uvir

import java.nio.charset.StandardCharsets

internal data class SensorWifiConfiguration(
    val ssid: String,
    val password: String
) {
    val protocolCommand: String
        get() =
            "WIFI_CONFIG ${ssid.toHexUtf8()} ${password.toHexUtf8()}"
}

internal fun validatedSensorWifiConfiguration(
    ssid: String,
    password: String
): SensorWifiConfiguration? {
    val normalizedSsid = ssid.trim()
    val ssidBytes = normalizedSsid.toByteArray(StandardCharsets.UTF_8)
    val passwordBytes = password.toByteArray(StandardCharsets.UTF_8)

    return if (
        ssidBytes.size in 1..32 &&
        passwordBytes.size in 8..63
    ) {
        SensorWifiConfiguration(
            ssid = normalizedSsid,
            password = password
        )
    } else {
        null
    }
}

private fun String.toHexUtf8(): String =
    toByteArray(StandardCharsets.UTF_8).joinToString("") { byte ->
        "%02X".format(byte.toInt() and 0xFF)
    }
