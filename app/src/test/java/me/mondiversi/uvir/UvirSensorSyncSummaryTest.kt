package me.mondiversi.uvir

import org.junit.Assert.assertEquals
import org.junit.Test

class UvirSensorSyncSummaryTest {
    private val profiles = listOf(
        UvirSensorProfile(1, "UVIR-B", "Esterno", 0, 0),
        UvirSensorProfile(2, "UVIR-A", "Portatile", 0, 0)
    )

    @Test fun summaryUsesTheEventOriginRatherThanTheFirstOrSelectedProfile() {
        val summary = SensorSyncSummary(12, 3, 2, true).withSensorOrigin(" uvir-a ", profiles)
        assertEquals("uvir-a", summary.sensorDeviceId)
        assertEquals("Portatile", summary.sensorName)
        assertEquals(12, summary.acquisitions)
        assertEquals(3, summary.alerts)
        assertEquals(2, summary.errors)
        assertEquals(true, summary.storageWasFull)
    }

    @Test fun unknownOrUnnamedSensorFallsBackToItsOwnHardwareId() {
        assertEquals("UVIR-C", SensorSyncSummary(1, 0, 0)
            .withSensorOrigin(" UVIR-C ", profiles).sensorName)
        assertEquals("UVIR-A", SensorSyncSummary(0, 1, 0)
            .withSensorOrigin("UVIR-A", listOf(profiles[1].copy(displayName = " "))).sensorName)
    }

    @Test fun capturedOriginDoesNotChangeWhenAnotherSensorIsChosenOrRenamed() {
        val summary = SensorSyncSummary(1, 0, 0).withSensorOrigin("UVIR-A", profiles)
        summary.withSensorOrigin("UVIR-B", profiles)
        assertEquals("UVIR-A", summary.sensorDeviceId)
        assertEquals("Portatile", summary.sensorName)
    }
}
