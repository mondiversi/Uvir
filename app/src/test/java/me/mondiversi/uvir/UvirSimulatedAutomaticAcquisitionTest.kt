package me.mondiversi.uvir

import org.junit.Assert.assertEquals
import org.junit.Test

class UvirSimulatedAutomaticAcquisitionTest {
    private fun step(
        now: Long = 1_000L,
        next: Long = 1_000L,
        interval: Long = 5L,
        end: Long? = null,
        maximum: Int? = null,
        completed: Int = 0,
        ready: Boolean = true
    ) = simulatedAutomaticAcquisitionStep(now, next, interval, end, maximum, completed, ready)

    @Test fun firstAcquisitionIsImmediateWhenReady() {
        assertEquals(SimulatedAutomaticAcquisitionStep.Acquire(6_000L), step())
    }

    @Test fun intervalDoesNotAcquireEarly() {
        assertEquals(SimulatedAutomaticAcquisitionStep.Wait(1_000L), step(next = 6_000L))
        assertEquals(SimulatedAutomaticAcquisitionStep.Wait(100L), step(now = 5_900L, next = 6_000L))
    }

    @Test fun subsequentAcquisitionIsDueAtTheConfiguredInterval() {
        assertEquals(SimulatedAutomaticAcquisitionStep.Acquire(11_000L), step(now = 6_000L, next = 6_000L))
    }

    @Test fun initialDelayDefersTheFirstAcquisition() {
        assertEquals(SimulatedAutomaticAcquisitionStep.Wait(1_000L), step(next = 11_000L))
        assertEquals(SimulatedAutomaticAcquisitionStep.Acquire(16_000L), step(now = 11_000L, next = 11_000L))
    }

    @Test fun initialSamplingMustBeReady() {
        assertEquals(SimulatedAutomaticAcquisitionStep.Wait(100L), step(ready = false))
    }

    @Test fun durationEndsWithoutAnotherAcquisitionAtTheDeadline() {
        assertEquals(SimulatedAutomaticAcquisitionStep.Stop, step(end = 1_000L))
        assertEquals(SimulatedAutomaticAcquisitionStep.Stop, step(end = 999L))
    }

    @Test fun durationCanEndWhileWaitingForTheNextInterval() {
        assertEquals(SimulatedAutomaticAcquisitionStep.Wait(400L), step(next = 6_000L, end = 1_400L))
    }

    @Test fun durationCanEndBeforeTheInitialSampleIsReady() {
        assertEquals(SimulatedAutomaticAcquisitionStep.Stop, step(end = 1_000L, ready = false))
    }

    @Test fun maximumCountStopsImmediatelyAfterTheLastSavedAcquisition() {
        assertEquals(SimulatedAutomaticAcquisitionStep.Stop, step(maximum = 3, completed = 3))
        assertEquals(SimulatedAutomaticAcquisitionStep.Acquire(6_000L), step(maximum = 3, completed = 2))
    }

    @Test fun disabledLimitsDoNotEndTheSession() {
        assertEquals(SimulatedAutomaticAcquisitionStep.Acquire(6_000L), step(completed = 100))
    }

    @Test fun appRestartDoesNotInventMissedHistoricalAcquisitions() {
        assertEquals(SimulatedAutomaticAcquisitionStep.Acquire(65_000L), step(now = 60_000L))
    }

    @Test fun intervalIsPositiveAndDoesNotOverflow() {
        assertEquals(SimulatedAutomaticAcquisitionStep.Acquire(2_000L), step(interval = 0L))
        assertEquals(SimulatedAutomaticAcquisitionStep.Acquire(Long.MAX_VALUE), step(now = Long.MAX_VALUE - 100L, interval = Long.MAX_VALUE))
    }
}
