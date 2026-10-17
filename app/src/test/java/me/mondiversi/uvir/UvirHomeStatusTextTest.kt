package me.mondiversi.uvir

import org.junit.Assert.assertEquals
import org.junit.Test

class UvirHomeStatusTextTest {
    @Test fun noAssociatedSensorHasItsOwnMessageEvenWithStaleConnectionState() {
        for (busy in listOf(false, true)) for (live in listOf(false, true))
            for (initializing in listOf(false, true)) for (searching in listOf(false, true)) {
                assertEquals(R.string.sensor_status_no_associated_sensor,
                    uvirHomeStatusTextRes(false, busy, live, initializing, searching, false))
                assertEquals(R.string.sensor_status_simulated,
                    uvirHomeStatusTextRes(true, busy, live, initializing, searching, false))
            }
    }

    @Test fun simulatedStatusTakesPrecedenceOverEveryTransportAndActivityState() {
        for (busy in listOf(false, true)) for (live in listOf(false, true))
            for (initializing in listOf(false, true)) for (searching in listOf(false, true)) {
                assertEquals(R.string.sensor_status_simulated,
                    uvirHomeStatusTextRes(true, busy, live, initializing, searching))
            }
    }

    @Test fun otherSensorsKeepTheirActivityAndConnectionStateWhenSelectionChanges() {
        val busy = UvirConnectionSensorState("B", UvirConnectionState.CONNECTED, true)
        val offline = busy.copy(state = UvirConnectionState.DISCONNECTED)
        val idle = UvirStatusIndicator(UvirStatusDot.GREEN, false)
        for (other in listOf(busy, offline)) {
            val result = uvirSensorSelectionIndicators("A", idle, mapOf("B" to other.indicator))
            assertEquals(other.indicator, result.getValue("b"))
            assertEquals(true, result.getValue("b").pulses)
        }
        assertEquals(UvirStatusIndicator(UvirStatusDot.RED, true), offline.indicator)
    }

    @Test fun simulationUsesTheBugIconInsteadOfEveryPhysicalConnectionIcon() {
        for (mode in SensorConnectionMode.entries) {
            assertEquals(ConnectivityIconType.DEBUG, uvirHomeStatusIconType(true, mode))
            assertEquals(uvirSensorConnectionIconType(mode), uvirHomeStatusIconType(false, mode))
        }
    }

    @Test fun selectionStatesAreKeyedByNormalizedSensorIdentity() {
        val live = UvirStatusIndicator(UvirStatusDot.YELLOW, true)
        val other = UvirStatusIndicator(UvirStatusDot.GREEN, false)
        val states = uvirSensorSelectionIndicators(" A ", live, mapOf(
            "A" to UvirStatusIndicator(UvirStatusDot.RED, false), " B " to other))
        assertEquals(mapOf("a" to live, "b" to other), states)
    }

    @Test fun noSelectionDoesNotAssignTheHomeStateToAnotherSensor() {
        val other = UvirStatusIndicator(UvirStatusDot.RED, false)
        assertEquals(mapOf("b" to other), uvirSensorSelectionIndicators("",
            UvirStatusIndicator(UvirStatusDot.DEBUG, false), mapOf("B" to other)))
    }

    @Test
    fun searchingAndHandshakeHaveDifferentMessages() {
        assertEquals(
            R.string.sensor_info_searching,
            uvirHomeStatusTextRes(false, false, false, false, true)
        )
        assertEquals(
            R.string.sensor_info_connecting,
            uvirHomeStatusTextRes(false, false, false, true, false)
        )
    }

    @Test
    fun currentActivityTakesPrecedenceEvenWhenDisconnected() {
        assertEquals(
            R.string.sensor_info_activity,
            uvirHomeStatusTextRes(false, true, false, false, true)
        )
        assertEquals(
            R.string.sensor_info_activity,
            uvirHomeStatusTextRes(false, true, true, false, false)
        )
    }

    @Test
    fun idleStateMatchesConnection() {
        assertEquals(
            R.string.sensor_status_connected,
            uvirHomeStatusTextRes(false, false, true, false, false)
        )
        assertEquals(
            R.string.sensor_status_no_sensor,
            uvirHomeStatusTextRes(false, false, false, false, false)
        )
    }

    @Test
    fun notificationIndicatorMatchesHomeDot() {
        assertEquals(
            UvirStatusIndicator(UvirStatusDot.RED, false),
            uvirStatusIndicator(false, false, false, false)
        )
        assertEquals(
            UvirStatusIndicator(UvirStatusDot.RED, true),
            uvirStatusIndicator(false, false, false, true)
        )
        assertEquals(
            UvirStatusIndicator(UvirStatusDot.YELLOW, true),
            uvirStatusIndicator(false, false, true, false)
        )
        assertEquals(
            UvirStatusIndicator(UvirStatusDot.GREEN, true),
            uvirStatusIndicator(false, true, false, true)
        )
        assertEquals(
            UvirStatusIndicator(UvirStatusDot.GREEN, false),
            uvirStatusIndicator(false, true, false, false)
        )
    }
}
