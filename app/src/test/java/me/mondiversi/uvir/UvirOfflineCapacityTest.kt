package me.mondiversi.uvir

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UvirOfflineCapacityTest {
    @Test
    fun availablePercentageIsRoundedAndClamped() {
        assertEquals(100, offlineCapacityPercentage(1_800, 1_800))
        assertEquals(50, offlineCapacityPercentage(900, 1_800))
        assertEquals(33, offlineCapacityPercentage(599, 1_800))
        assertEquals(0, offlineCapacityPercentage(-1, 1_800))
        assertEquals(100, offlineCapacityPercentage(2_000, 1_800))
    }

    @Test
    fun automaticOnlyUsesEveryAvailableRecord() {
        val estimate = estimateOfflineAutonomy(
            runtimeInfo = UvirSensorRuntimeInfo(
                offlineCapacity = 1_800,
                offlineRemaining = 1_800
            ),
            automaticIntervalSeconds = 60L,
            alertsEnabled = false,
            alertRepeatSeconds = 30
        )!!

        assertEquals(1_800, estimate.automaticAcquisitions)
        assertEquals(108_000L, estimate.durationSeconds)
    }

    @Test
    fun continuousAlertsShareCapacityWithAutomaticAcquisitions() {
        val estimate = estimateOfflineAutonomy(
            runtimeInfo = UvirSensorRuntimeInfo(
                offlineCapacity = 1_800,
                offlineRemaining = 1_800
            ),
            automaticIntervalSeconds = 60L,
            alertsEnabled = true,
            alertRepeatSeconds = 30
        )!!

        assertEquals(600, estimate.automaticAcquisitions)
        assertEquals(36_000L, estimate.durationSeconds)
        assertTrue(estimate.includesAlerts)
    }

    @Test
    fun fullFlagPreventsPositiveAutonomy() {
        val estimate = estimateOfflineAutonomy(
            runtimeInfo = UvirSensorRuntimeInfo(
                offlineCapacity = 1_800,
                offlineRemaining = 0,
                offlineStorageFull = true
            ),
            automaticIntervalSeconds = 60L,
            alertsEnabled = false,
            alertRepeatSeconds = 30
        )!!

        assertTrue(estimate.storageFull)
        assertEquals(null, estimate.durationSeconds)
    }

    @Test
    fun scheduleAndMaximumCapTheAutomaticEstimate() {
        val estimate = estimateOfflineAutonomy(
            runtimeInfo = UvirSensorRuntimeInfo(
                offlineCapacity = 1_800,
                offlineRemaining = 1_800
            ),
            automaticIntervalSeconds = 60L,
            alertsEnabled = false,
            alertRepeatSeconds = 30,
            startDelaySeconds = 300L,
            automaticDurationSeconds = 3_600L,
            maximumAutomaticAcquisitions = 10
        )!!

        assertEquals(10, estimate.automaticAcquisitions)
        assertEquals(900L, estimate.durationSeconds)
    }

    @Test
    fun alertsDuringStartDelayUseTheSharedCapacity() {
        val estimate = estimateOfflineAutonomy(
            runtimeInfo = UvirSensorRuntimeInfo(
                offlineCapacity = 10,
                offlineRemaining = 10
            ),
            automaticIntervalSeconds = 60L,
            alertsEnabled = true,
            alertRepeatSeconds = 30,
            startDelaySeconds = 60L
        )!!

        assertEquals(3, estimate.automaticAcquisitions)
        assertEquals(220L, estimate.durationSeconds)
    }

    @Test
    fun exhaustedAutomaticLimitDoesNotPromiseAnotherAcquisition() {
        val estimate = estimateOfflineAutonomy(
            runtimeInfo = UvirSensorRuntimeInfo(
                offlineCapacity = 1_800,
                offlineRemaining = 1_800
            ),
            automaticIntervalSeconds = 60L,
            alertsEnabled = false,
            alertRepeatSeconds = 30,
            maximumAutomaticAcquisitions = 0
        )!!

        assertEquals(0, estimate.automaticAcquisitions)
        assertEquals(0L, estimate.durationSeconds)
    }

    @Test
    fun offlineAlertEstimateIncludesTheTimeNeededToBuildEachMeasurement() {
        val estimate = estimateOfflineAutonomy(
            runtimeInfo = UvirSensorRuntimeInfo(
                offlineCapacity = 10,
                offlineRemaining = 10,
                integrationMs = 0.0
            ),
            automaticIntervalSeconds = null,
            alertsEnabled = true,
            alertRepeatSeconds = 30,
            samplesPerMeasurement = 5,
            sampleSpacingMs = 500L
        )!!

        assertEquals(320L, estimate.durationSeconds)
    }
}
