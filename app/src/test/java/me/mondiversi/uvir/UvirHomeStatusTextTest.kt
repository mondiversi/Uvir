package me.mondiversi.uvir

import org.junit.Assert.assertEquals
import org.junit.Test

class UvirHomeStatusTextTest {
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
