package me.mondiversi.uvir

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UvirSensorWifiConfigurationTest {

    @Test
    fun validConfigurationBuildsUtf8HexProtocolCommand() {
        val configuration =
            validatedSensorWifiConfiguration(
                ssid = " Rete casa ",
                password = "password123"
            )

        assertEquals("Rete casa", configuration?.ssid)
        assertEquals(
            "WIFI_CONFIG 526574652063617361 70617373776F7264313233",
            configuration?.protocolCommand
        )
    }

    @Test
    fun invalidSsidAndPasswordLengthsAreRejected() {
        assertNull(validatedSensorWifiConfiguration("", "password123"))
        assertNull(validatedSensorWifiConfiguration("x".repeat(33), "password123"))
        assertNull(validatedSensorWifiConfiguration("Rete", "short"))
        assertNull(validatedSensorWifiConfiguration("Rete", "x".repeat(64)))
    }
}
