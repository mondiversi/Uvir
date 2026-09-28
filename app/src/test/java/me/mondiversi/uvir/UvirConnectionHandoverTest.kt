package me.mondiversi.uvir

import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test

class UvirConnectionHandoverTest {
    private fun route(
        previous: SensorConnectionMode = SensorConnectionMode.WIFI,
        target: SensorConnectionMode = SensorConnectionMode.BLUETOOTH,
        usbId: String = "",
        usbReady: Boolean = false,
        wirelessId: String = "A",
        wirelessReady: Boolean = true,
        selected: String = "A"
    ) = uvirConnectionHandoverRoute(selected, previous, target,
        usbId, usbReady, wirelessId, wirelessReady)

    @Test fun connectedWirelessPeerReceivesTheHandoverBeforeSelectionChanges() {
        assertEquals(UvirConnectionHandoverRoute.WIRELESS, route())
    }

    @Test fun currentUsbSourceReceivesTheHandoverOverItsOwnCable() {
        assertEquals(UvirConnectionHandoverRoute.USB,
            route(previous = SensorConnectionMode.USB, usbId = "A", usbReady = true))
    }

    @Test fun anotherSensorsUsbCableCannotReceiveTheHandover() {
        assertEquals(UvirConnectionHandoverRoute.WIRELESS, route(usbId = "B", usbReady = true))
        assertEquals(UvirConnectionHandoverRoute.LOCAL_SELECTION,
            route(usbId = "B", usbReady = true, wirelessReady = false))
    }

    @Test fun anotherWirelessPeerCannotReceiveTheHandover() {
        assertEquals(UvirConnectionHandoverRoute.USB,
            route(usbId = "A", usbReady = true, wirelessId = "B"))
        assertEquals(UvirConnectionHandoverRoute.LOCAL_SELECTION, route(wirelessId = "B"))
    }

    @Test fun offlineSensorStillAllowsSourceSelectionWithoutAnUnconfirmedCommand() {
        assertEquals(UvirConnectionHandoverRoute.LOCAL_SELECTION, route(wirelessReady = false))
        assertEquals(UvirConnectionHandoverRoute.LOCAL_SELECTION, route(selected = ""))
    }

    @Test fun selectingUsbPreservesWirelessRecoveryAndNeedsNoWirelessOff() {
        assertEquals(UvirConnectionHandoverRoute.LOCAL_SELECTION,
            route(target = SensorConnectionMode.USB, usbId = "A", usbReady = true))
        assertNull(SensorConnectionMode.USB.wirelessHandoverCommand())
    }

    @Test fun deviceIdsAreComparedUsingTheExistingNormalization() {
        assertEquals(UvirConnectionHandoverRoute.WIRELESS, route(wirelessId = " a "))
    }

    @Test fun protocolCommandsIncludeAllThreeWirelessSources() {
        assertEquals("WIRELESS WIFI", SensorConnectionMode.WIFI.wirelessHandoverCommand())
        assertEquals("WIRELESS BLUETOOTH", SensorConnectionMode.BLUETOOTH.wirelessHandoverCommand())
        assertEquals("WIRELESS INTERNET", SensorConnectionMode.INTERNET.wirelessHandoverCommand())
    }

    @Test fun onlyTheExpectedModeFromTheExpectedSensorConfirmsTheRequest() {
        val ack = UvirConnectionModeAcknowledgement(" A ", SensorConnectionMode.BLUETOOTH)
        assertFalse(ack.confirm("B", "bluetooth"))
        assertFalse(ack.confirm("A", "wifi"))
        assertTrue(ack.confirm("a", "BLUETOOTH"))
        assertTrue(ack.await(0))
    }

    @Test fun closingOrRejectingARequestNeverConfirmsIt() {
        val ack = UvirConnectionModeAcknowledgement("A", SensorConnectionMode.BLUETOOTH)
        assertTrue(ack.reject())
        assertFalse(ack.await(0))
        assertFalse(ack.confirm("A", "bluetooth"))
    }

    @Test fun aLateResponseCannotReverseATimeout() {
        val ack = UvirConnectionModeAcknowledgement("A", SensorConnectionMode.BLUETOOTH)
        assertFalse(ack.await(1))
        assertFalse(ack.confirm("A", "bluetooth"))
        assertFalse(ack.await(0))
    }

    @Test fun theExpectedDisconnectAfterConfirmationCannotEraseSuccess() {
        val ack = UvirConnectionModeAcknowledgement("A", SensorConnectionMode.INTERNET)
        assertTrue(ack.confirm("A", "internet"))
        assertFalse(ack.reject())
        assertTrue(ack.await(0))
    }

    @Test fun asynchronousReaderCanConfirmWithoutWaitingForAManagerLock() {
        val worker = Executors.newSingleThreadExecutor()
        try {
            val ack = UvirConnectionModeAcknowledgement("A", SensorConnectionMode.WIFI)
            val result = worker.submit<Boolean> { ack.await(1_000) }
            assertTrue(ack.confirm("A", "wifi"))
            assertTrue(result.get(1, TimeUnit.SECONDS))
        } finally { worker.shutdownNow() }
    }

    @Test fun knownRadioRejectionsAreNotPhysicalTransportFailures() {
        for (code in listOf("wifi_disabled", "wifi_not_configured",
            "bluetooth_disabled", "internet_not_configured")) {
            assertTrue(code, isWirelessHandoverRejection(code))
        }
        assertFalse(isWirelessHandoverRejection("sensor_read"))
        assertFalse(isWirelessHandoverRejection("auth_required"))
    }

    @Test fun acknowledgementWaitIsBounded() {
        assertEquals(1_500L, wirelessHandoverTimeoutMs(0))
        assertEquals(6_000L, wirelessHandoverTimeoutMs(6_000))
        assertEquals(120_000L, wirelessHandoverTimeoutMs(Long.MAX_VALUE))
    }

    private fun source(name: String): String {
        val root = listOf(File("src/main/java/me/mondiversi/uvir"),
            File("app/src/main/java/me/mondiversi/uvir")).first { it.isDirectory }
        return File(root, name).readText()
    }

    @Test fun uiAbortsAnUnconfirmedHandoverBeforeSavingTheSelection() {
        val root = source("UvirAppRoot.kt")
        val start = root.indexOf("onSensorConnectionModeChanged = { mode ->")
        val end = root.indexOf("onRequestBluetoothPermission =", start)
        val callback = root.substring(start, end)
        val failure = callback.indexOf("if (!handoverConfirmed)")
        assertTrue(failure >= 0)
        assertTrue(callback.indexOf("return@launch", failure) < callback.indexOf("completeModeChange()"))
        assertFalse(callback.contains("configureWirelessMode("))
        assertTrue(callback.contains("sensorConnectionSwitchJob = null"))
        assertTrue(callback.contains("isSensorSelectionDisabled(context)"))
    }

    @Test fun neitherTransportWaitsForAnAckWhileHoldingItsManagerMonitor() {
        for (name in listOf("UvirWirelessSensorManager.kt", "UvirUsbSensorManager.kt")) {
            val text = source(name)
            assertFalse(name, Regex("@Synchronized\\s+fun requestWirelessModeAndAwait").containsMatchIn(text))
            assertTrue(name, text.contains("UvirConnectionModeAcknowledgement(deviceId, mode)"))
            assertTrue(name, text.contains("wirelessModeAcknowledgement.get()?.reject()"))
        }
    }

    @Test fun firmwareRepliesBeforeChangingRadioAndPreservesRepeatedRequestFallback() {
        val file = listOf(File("../firmware/esp32/UvirSensor/UvirSensor.ino"),
            File("firmware/esp32/UvirSensor/UvirSensor.ino")).first { it.isFile }
        val firmware = file.readText()
        val start = firmware.indexOf("if (command.startsWith(\"WIRELESS \"))")
        val end = firmware.indexOf("if (command.startsWith(\"RADIO \"))", start)
        val command = firmware.substring(start, end)
        assertTrue(command.indexOf("output.flush();") < command.indexOf("applyWirelessMode(newMode, true);"))
        assertFalse(command.contains("stopWirelessFallback(false)"))
        assertTrue(command.contains("wirelessModeStartedAtMs = millis();"))
        assertTrue(command.contains("alternateWirelessMode(wirelessMode) != WirelessMode::Off"))
        assertTrue(firmware.contains("kWirelessFallbackIntervalMs = 20000"))
    }
}
