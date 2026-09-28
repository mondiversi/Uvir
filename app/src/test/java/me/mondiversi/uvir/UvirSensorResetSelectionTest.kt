package me.mondiversi.uvir

import org.junit.Assert.*
import org.junit.Test

class UvirSensorResetSelectionTest {
    @Test fun onlyLiveIdleAuthenticatedIndicatorsAllowSelection() {
        assertTrue(uvirSensorCanReset(UvirStatusIndicator(UvirStatusDot.GREEN, false)))
        assertFalse(uvirSensorCanReset(null))
        assertFalse(uvirSensorCanReset(UvirStatusIndicator(UvirStatusDot.GREEN, true)))
        for (dot in listOf(UvirStatusDot.RED, UvirStatusDot.YELLOW, UvirStatusDot.DEBUG)) {
            assertFalse(uvirSensorCanReset(UvirStatusIndicator(dot, false)))
        }
    }

    @Test fun selectionNeverIncludesUnrequestedOfflineBusyOrUnknownSensors() {
        val profiles = listOf("A", "B", "C", "D").mapIndexed { index, id ->
            UvirSensorProfile(index.toLong(), id, id, 0L, 0L)
        }
        val indicators = mapOf(
            normalizeSensorDeviceId("A") to UvirStatusIndicator(UvirStatusDot.GREEN, false),
            normalizeSensorDeviceId("B") to UvirStatusIndicator(UvirStatusDot.GREEN, false),
            normalizeSensorDeviceId("C") to UvirStatusIndicator(UvirStatusDot.RED, false),
            normalizeSensorDeviceId("D") to UvirStatusIndicator(UvirStatusDot.GREEN, true)
        )
        assertEquals(emptyList<String>(), uvirSensorResetTargets(emptyList(), profiles, indicators))
        assertEquals(listOf("B"), uvirSensorResetTargets(listOf("b", "B", "C", "D", "other"), profiles, indicators))
        assertEquals(listOf("A", "B"), uvirSensorResetTargets(listOf("B", "A"), profiles, indicators))
        assertEquals(emptyList<String>(), uvirSensorResetTargets(listOf("A"), profiles, emptyMap()))
    }

    @Test fun runtimeBusyCheckBlocksBothKindsOfSessions() {
        assertTrue(uvirSensorRuntimeIdle(UvirSensorRuntimeInfo(offlineRecording = false, alertMonitoringEnabled = false)))
        assertFalse(uvirSensorRuntimeIdle(UvirSensorRuntimeInfo(offlineRecording = true)))
        assertFalse(uvirSensorRuntimeIdle(UvirSensorRuntimeInfo(alertMonitoringEnabled = true)))
    }
}
