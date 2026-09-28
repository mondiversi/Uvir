package me.mondiversi.uvir

import org.junit.Assert.*
import org.junit.Test

class UvirConnectionNotificationTest {
    @Test fun leavingTheSessionViewDoesNotEraseItsRetainedActivity() {
        val retained = UvirConnectionSensorState("a", UvirConnectionState.CONNECTED, true)
        val uiFallback = retained.copy(activityInProgress = false)
        assertEquals(retained, uvirSelectedConnectionNotificationState(listOf(retained), uiFallback, false))
        val stopped = retained.copy(activityInProgress = false)
        assertEquals(stopped, uvirSelectedConnectionNotificationState(listOf(stopped), uiFallback, false))
        assertTrue(uvirSelectedConnectionNotificationState(listOf(stopped), uiFallback, true).activityInProgress)
    }

    @Test fun transientUiActivityDoesNotTurnOfflineOrSimulatedSensorsIntoLiveOnes() {
        for (state in listOf(UvirConnectionState.DISCONNECTED, UvirConnectionState.SIMULATED)) {
            val retained = UvirConnectionSensorState("a", state)
            assertEquals(retained, uvirSelectedConnectionNotificationState(listOf(retained),
                UvirConnectionSensorState("a", UvirConnectionState.CONNECTED, true), true))
        }
        val fallback = UvirConnectionSensorState("a", UvirConnectionState.CONNECTING)
        assertEquals(fallback, uvirSelectedConnectionNotificationState(emptyList(), fallback, false))
    }
    @Test fun acquisitionAndAlertTitlesUseTheSavedNameOrHardwareId() {
        val profiles = listOf(UvirSensorProfile(1, "A", "Portatile", 0, 0))
        assertEquals("Portatile", uvirNotificationSensorName(profiles, " a "))
        assertEquals("B", uvirNotificationSensorName(profiles, " B "))
        assertEquals("Portatile · Allerta in corso", uvirNamedNotificationTitle("Portatile", "Allerta in corso"))
        assertEquals("Allerta in corso", uvirNamedNotificationTitle("", "Allerta in corso"))
    }
    @Test fun selectingAnotherSensorKeepsOtherConnectionsAndNormalizesIds() {
        val first = UvirConnectionSensorState(" A ", UvirConnectionState.CONNECTED, true)
        val second = UvirConnectionSensorState("B", UvirConnectionState.CONNECTED)
        val selected = second.copy(deviceId = " b ")
        val states = uvirConnectionNotificationSensors(listOf(first, second), selected)
        assertEquals(listOf("a", "b"), states.map { it.deviceId })
        assertEquals(UvirConnectionCounts(2, 0, 1, 0), uvirConnectionCounts(states))
        assertEquals(states, uvirConnectionNotificationSensors(states, first.copy(deviceId = "a")))
    }

    @Test fun clearingTheSelectionDoesNotHideConnectedSensors() {
        val states = listOf(UvirConnectionSensorState("a", UvirConnectionState.CONNECTED))
        assertEquals(states, uvirConnectionNotificationSensors(states, null))
        assertEquals(states, uvirConnectionNotificationSensors(states,
            UvirConnectionSensorState("  ", UvirConnectionState.SIMULATED)))
    }

    @Test fun simulatedDataNeverCountsAsAConnectionOrRealActivity() {
        val sensor = uvirConnectionSensorState("A", true, true, true, true, true)
        assertEquals(UvirConnectionState.SIMULATED, sensor.state)
        assertEquals(UvirConnectionCounts(0, 0, 0, 1), uvirConnectionCounts(listOf(sensor)))
        assertEquals(UvirStatusDot.DEBUG, sensor.indicator.dot)
    }

    @Test fun disconnectedSessionIsOnlyLastKnownNotVerifiedActivity() {
        val sensor = UvirConnectionSensorState("a", UvirConnectionState.DISCONNECTED, true)
        assertEquals(UvirConnectionCounts(0, 0, 0, 0), uvirConnectionCounts(listOf(sensor)))
        assertEquals(UvirStatusDot.RED, sensor.indicator.dot)
    }

    @Test fun searchingIsDistinctFromAuthenticationAndLiveConnection() {
        assertEquals(UvirConnectionState.SEARCHING, uvirConnectionSensorState("a", false, false, false, true, false).state)
        assertEquals(UvirConnectionState.CONNECTING, uvirConnectionSensorState("a", false, false, true, true, false).state)
        assertEquals(UvirConnectionState.CONNECTED, uvirConnectionSensorState("a", false, true, true, true, false).state)
        assertEquals(UvirConnectionState.DISCONNECTED, uvirConnectionSensorState("a", false, false, false, false, false).state)
    }

    @Test fun aggregateCountsAllRealTransportsAndConnectionAttempts() {
        val states = listOf(
            UvirConnectionSensorState("usb", UvirConnectionState.CONNECTED, true),
            UvirConnectionSensorState("wifi", UvirConnectionState.CONNECTED),
            UvirConnectionSensorState("bt", UvirConnectionState.CONNECTING),
            UvirConnectionSensorState("mqtt", UvirConnectionState.SEARCHING),
            UvirConnectionSensorState("fake", UvirConnectionState.SIMULATED, true),
            UvirConnectionSensorState("offline", UvirConnectionState.DISCONNECTED, true)
        )
        assertEquals(UvirConnectionCounts(2, 2, 1, 1), uvirConnectionCounts(states))
    }
}
