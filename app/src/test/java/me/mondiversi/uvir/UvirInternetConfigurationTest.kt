package me.mondiversi.uvir

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UvirInternetConfigurationTest {

    @Test
    fun primaryWifiConfigurationBuildsProtocolCommand() {
        val configuration =
            validatedSensorInternetConfiguration(
                enabled = true,
                usePrimaryWifi = true,
                wifiSsid = "",
                wifiPassword = "",
                relayHost = "relay.uvir.example",
                relayPortText = "8883",
                mqttUsername = "uvir-user",
                mqttPassword = "secret-password"
            )

        assertEquals(
            "INTERNET_CONFIG ON PRIMARY - - " +
                "72656C61792E757669722E6578616D706C65 8883 " +
                "757669722D75736572 7365637265742D70617373776F7264",
            configuration?.protocolCommand
        )
    }

    @Test
    fun secondaryWifiSupportsUtf8AndRequiresValidCredentials() {
        val configuration =
            validatedSensorInternetConfiguration(
                enabled = true,
                usePrimaryWifi = false,
                wifiSsid = "Rete mobile",
                wifiPassword = "password123",
                relayHost = "relay.example.org",
                relayPortText = "8443",
                mqttUsername = "user",
                mqttPassword = "password"
            )

        assertEquals("Rete mobile", configuration?.wifiSsid)
        assertEquals(8443, configuration?.relayPort)
        assertNull(
            validatedSensorInternetConfiguration(
                enabled = true,
                usePrimaryWifi = false,
                wifiSsid = "Rete mobile",
                wifiPassword = "short",
                relayHost = "relay.example.org",
                relayPortText = "8883",
                mqttUsername = "user",
                mqttPassword = "password"
            )
        )
    }

    @Test
    fun enabledConfigurationRejectsSchemesAndInvalidPorts() {
        assertNull(
            validatedSensorInternetConfiguration(
                true,
                true,
                "",
                "",
                "https://relay.example.org",
                "8883",
                "user",
                "password"
            )
        )
        assertNull(
            validatedSensorInternetConfiguration(
                true,
                true,
                "",
                "",
                "relay.example.org",
                "70000",
                "user",
                "password"
            )
        )
    }
}
