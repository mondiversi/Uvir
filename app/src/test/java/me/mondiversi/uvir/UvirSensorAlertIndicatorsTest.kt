package me.mondiversi.uvir

import org.junit.Assert.*
import org.junit.Test

class UvirSensorAlertIndicatorsTest {
    @Test fun matchingSensorExplicitlyStartsAndStopsItsAlerts() {
        assertTrue(uvirSensorAlertMonitoringActive(" A ", UvirSensorRuntimeInfo(
            deviceId = "a", alertMonitoringEnabled = true), false))
        assertFalse(uvirSensorAlertMonitoringActive("A", UvirSensorRuntimeInfo(
            deviceId = "A", alertMonitoringEnabled = false), true))
    }

    @Test fun acquisitionsAndTransientActivityNeverCountAsAlerts() {
        assertFalse(uvirSensorAlertMonitoringActive("A", UvirSensorRuntimeInfo(
            deviceId = "A", offlineRecording = true, operationActive = true), false))
    }

    @Test fun anotherSensorCannotReplaceTheLastKnownState() {
        assertFalse(uvirSensorAlertMonitoringActive("A", UvirSensorRuntimeInfo(
            deviceId = "B", alertMonitoringEnabled = true), false))
        assertTrue(uvirSensorAlertMonitoringActive("A", UvirSensorRuntimeInfo(
            deviceId = "B", alertMonitoringEnabled = false), true))
    }

    @Test fun missingDisconnectedSnapshotRetainsTheLastKnownState() {
        assertTrue(uvirSensorAlertMonitoringActive("A", UvirSensorRuntimeInfo(), true))
        assertFalse(uvirSensorAlertMonitoringActive("A", UvirSensorRuntimeInfo(), false))
        assertTrue(uvirSensorAlertMonitoringActive("A", UvirSensorRuntimeInfo(deviceId = "A"), true))
    }

    @Test fun selectedUiOverridesOnlyItsOwnStateWithoutMutatingTheBackgroundSet() {
        val background = setOf(" a ", "B", " ")
        assertEquals(setOf("b"), uvirSensorAlertDeviceIds(background, "A", false))
        assertEquals(setOf("a", "b"), uvirSensorAlertDeviceIds(setOf("B"), " A ", true))
        assertEquals(setOf(" a ", "B", " "), background)
        assertEquals(setOf("b"), uvirSensorAlertDeviceIds(setOf("B"), "", true))
    }

    @Test fun headerWarnsOnlyAboutOtherSensorsWithKnownAlerts() {
        assertFalse(uvirOtherSensorHasAlerts("A", setOf(" a ", "")))
        assertFalse(uvirOtherSensorHasAlerts("A", emptySet()))
        assertFalse(uvirOtherSensorHasAlerts("", setOf("B")))
        assertTrue(uvirOtherSensorHasAlerts("A", setOf(" B ")))
        assertTrue(uvirOtherSensorHasAlerts("A", setOf("A", "B", "C")))
    }
}
