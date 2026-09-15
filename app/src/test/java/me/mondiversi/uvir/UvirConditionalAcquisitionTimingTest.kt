package me.mondiversi.uvir

import org.junit.Assert.*
import org.junit.Test

class UvirConditionalAcquisitionTimingTest {
    private fun plan(action: AcquisitionConditionAction) = ConditionalAcquisitionPlan(
        AcquisitionConditionMatch.ANY, action,
        listOf(ThresholdAlertRule(ThresholdAlertMetric.UVA, true, ThresholdAlertDirection.BELOW, 3f)))
    private fun step(action: AcquisitionConditionAction, now: Long = 1_000L,
        first: Long = 1_000L, next: Long = 1_000L, started: Boolean = true,
        met: Boolean? = true, end: Long? = null, max: Int? = null, count: Int = 0,
        ready: Boolean = true, evaluate: (() -> Boolean?)? = null
    ) = simulatedConditionalAutomaticAcquisitionStep(now, first, next, 5L, end, max,
        count, ready, plan(action), started, evaluate ?: { met })

    @Test fun startRespondsToCrossingBetweenFiveSecondTicks() {
        assertEquals(SimulatedAutomaticAcquisitionStep.Wait(150L),
            step(AcquisitionConditionAction.START, started = false, met = false))
        assertEquals(SimulatedAutomaticAcquisitionStep.Acquire(7_500L, true),
            step(AcquisitionConditionAction.START, now = 2_500L, started = false))
    }
    @Test fun startDelayHasPriorityAndDoesNotEvaluateEarly() {
        var calls = 0
        assertEquals(SimulatedAutomaticAcquisitionStep.Wait(150L),
            step(AcquisitionConditionAction.START, first = 10_000L, started = false,
                evaluate = { calls++; true }))
        assertEquals(0, calls)
        assertEquals(SimulatedAutomaticAcquisitionStep.Acquire(15_000L, true),
            step(AcquisitionConditionAction.START, now = 10_000L, first = 10_000L, started = false))
    }
    @Test fun startIntervalIsAnchoredAtTriggerNotAtEarlierFailedCheck() {
        val triggered = step(AcquisitionConditionAction.START, now = 3_100L, started = false)
        assertEquals(SimulatedAutomaticAcquisitionStep.Acquire(8_100L, true), triggered)
    }
    @Test fun startedStartJobDoesNotRecheckConditions() {
        var calls = 0
        assertEquals(SimulatedAutomaticAcquisitionStep.Wait(1_000L),
            step(AcquisitionConditionAction.START, now = 2_000L, next = 6_000L,
                evaluate = { calls++; false }))
        assertEquals(SimulatedAutomaticAcquisitionStep.Acquire(11_000L),
            step(AcquisitionConditionAction.START, now = 6_000L, next = 6_000L,
                evaluate = { calls++; null }))
        assertEquals(0, calls)
    }
    @Test fun stopRespondsBeforeNextRecordingDeadline() {
        assertEquals(SimulatedAutomaticAcquisitionStep.Stop,
            step(AcquisitionConditionAction.STOP, now = 2_000L, next = 6_000L))
    }
    @Test fun stopFalseConditionDoesNotCreateExtraAcquisitions() {
        assertEquals(SimulatedAutomaticAcquisitionStep.Wait(150L),
            step(AcquisitionConditionAction.STOP, now = 2_000L, next = 6_000L, met = false))
        assertEquals(SimulatedAutomaticAcquisitionStep.Acquire(11_000L),
            step(AcquisitionConditionAction.STOP, now = 6_000L, next = 6_000L, met = false))
    }
    @Test fun stopHonorsInitialDelay() {
        var calls = 0
        step(AcquisitionConditionAction.STOP, first = 8_000L, evaluate = { calls++; true })
        assertEquals(0, calls)
    }
    @Test fun acquireFalseConditionRetriesWithoutWaitingFiveSeconds() {
        assertEquals(SimulatedAutomaticAcquisitionStep.Wait(150L),
            step(AcquisitionConditionAction.ACQUIRE, now = 2_000L, met = false))
        assertEquals(SimulatedAutomaticAcquisitionStep.Acquire(7_150L),
            step(AcquisitionConditionAction.ACQUIRE, now = 2_150L))
    }
    @Test fun acquireNeverChecksDuringCooldownEvenAtLastMillisecond() {
        var calls = 0
        for (now in listOf(1_001L, 2_000L, 5_999L))
            assertEquals(SimulatedAutomaticAcquisitionStep.Wait(150L),
                step(AcquisitionConditionAction.ACQUIRE, now = now, next = 6_000L,
                    evaluate = { calls++; true }))
        assertEquals(0, calls)
        assertEquals(SimulatedAutomaticAcquisitionStep.Acquire(11_000L),
            step(AcquisitionConditionAction.ACQUIRE, now = 6_000L, next = 6_000L,
                evaluate = { calls++; true }))
        assertEquals(1, calls)
    }
    @Test fun unchangedTrueConditionCanAcquireAgainAfterCooldown() {
        assertEquals(SimulatedAutomaticAcquisitionStep.Acquire(6_000L),
            step(AcquisitionConditionAction.ACQUIRE))
        assertEquals(SimulatedAutomaticAcquisitionStep.Acquire(11_000L),
            step(AcquisitionConditionAction.ACQUIRE, now = 6_000L, next = 6_000L))
    }
    @Test fun invalidSampleCannotStartAcquireOrStop() {
        for (action in AcquisitionConditionAction.entries)
            assertEquals(SimulatedAutomaticAcquisitionStep.Wait(150L),
                step(action, started = action != AcquisitionConditionAction.START, met = null))
    }
    @Test fun durationAndCountTakePriorityWithoutEvaluating() {
        var calls = 0
        for (action in AcquisitionConditionAction.entries) {
            assertEquals(SimulatedAutomaticAcquisitionStep.Stop,
                step(action, end = 1_000L, evaluate = { calls++; true }))
            assertEquals(SimulatedAutomaticAcquisitionStep.Stop,
                step(action, max = 3, count = 3, evaluate = { calls++; true }))
        }
        assertEquals(0, calls)
    }
    @Test fun durationCanStopDuringAcquireCooldown() {
        assertEquals(SimulatedAutomaticAcquisitionStep.Stop,
            step(AcquisitionConditionAction.ACQUIRE, now = 3_000L, next = 6_000L, end = 3_000L))
    }
    @Test fun missingSampleDoesNotEvaluate() {
        var calls = 0
        step(AcquisitionConditionAction.STOP, ready = false, evaluate = { calls++; true })
        assertEquals(0, calls)
    }
    @Test fun oldFirmwareReadbackRemainsSupportedButNewStartIsBlocked() {
        assertTrue(firmwareSupportsConditionalAcquisition("0.5.73"))
        assertTrue(firmwareSupportsConditionalAcquisition("0.5.74"))
        assertFalse(firmwareSupportsImmediateConditionalAcquisition("0.5.74"))
        assertTrue(firmwareSupportsImmediateConditionalAcquisition("0.5.75"))
    }
    @Test fun initialEligibilityIsScopedToSensorProfile() {
        assertTrue(KEY_AUTO_FIRST_ALLOWED_MS in sensorSelectionPreferenceKeys)
    }
}
