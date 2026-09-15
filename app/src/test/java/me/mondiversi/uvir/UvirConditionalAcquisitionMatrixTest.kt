package me.mondiversi.uvir

import org.junit.Assert.*
import org.junit.Test

class UvirConditionalAcquisitionMatrixTest {
    private val rules = listOf(
        ThresholdAlertRule(ThresholdAlertMetric.UVC, true, ThresholdAlertDirection.BELOW, 3f),
        ThresholdAlertRule(ThresholdAlertMetric.UVA, true, ThresholdAlertDirection.ABOVE, 3f))
    private fun plan(match: AcquisitionConditionMatch, action: AcquisitionConditionAction) =
        ConditionalAcquisitionPlan(match, action, rules)
    private fun expected(match: AcquisitionConditionMatch, first: Boolean, second: Boolean) =
        when (match) {
            AcquisitionConditionMatch.ANY -> first || second
            AcquisitionConditionMatch.ALL -> first && second
            AcquisitionConditionMatch.NONE -> !first && !second
        }

    @Test fun mixedDirectionMatrixKeepsMatchingAndActionsConsistent() {
        for (match in AcquisitionConditionMatch.entries) {
            for (action in AcquisitionConditionAction.entries) {
                for (pattern in 0..3) {
                    val first = pattern and 1 != 0
                    val second = pattern and 2 != 0
                    val sample = SensorSample(uvc = if (first) 1.0 else 4.0,
                        uva = if (second) 4.0 else 1.0)
                    val current = plan(match, action)
                    val met = expected(match, first, second)
                    assertEquals("$match/$action/$pattern", met, evaluateAcquisitionCondition(current, sample))
                    val decision = when (action) {
                        AcquisitionConditionAction.START -> if (met) ConditionalAcquisitionDecision.START_AND_RECORD
                            else ConditionalAcquisitionDecision.SKIP
                        AcquisitionConditionAction.STOP -> if (met) ConditionalAcquisitionDecision.STOP
                            else ConditionalAcquisitionDecision.RECORD
                        AcquisitionConditionAction.ACQUIRE -> if (met) ConditionalAcquisitionDecision.RECORD
                            else ConditionalAcquisitionDecision.SKIP
                    }
                    assertEquals(decision, conditionalAcquisitionDecision(current,
                        action != AcquisitionConditionAction.START, met))
                    val result = simulatedConditionalAutomaticAcquisitionStep(
                        2_500, 1_000, if (action == AcquisitionConditionAction.ACQUIRE) 1_000 else 6_000,
                        5, null, null, 0, true, current,
                        action != AcquisitionConditionAction.START) { evaluateAcquisitionCondition(current, sample) }
                    // STOP's false branch waits for the ordinary recording deadline.
                    val shouldRecord = met && action != AcquisitionConditionAction.STOP
                    if (action == AcquisitionConditionAction.STOP && met) {
                        assertEquals(SimulatedAutomaticAcquisitionStep.Stop, result)
                    } else if (shouldRecord) {
                        assertEquals(SimulatedAutomaticAcquisitionStep.Acquire(7_500,
                            action == AcquisitionConditionAction.START), result)
                    } else {
                        assertTrue(result is SimulatedAutomaticAcquisitionStep.Wait)
                    }
                }
            }
        }
    }

    @Test fun inclusiveBoundariesWorkForBothDirectionsAndEveryAction() {
        val sample = SensorSample(uvc = 3.0, uva = 3.0)
        for (match in AcquisitionConditionMatch.entries)
            for (action in AcquisitionConditionAction.entries)
                assertEquals(match != AcquisitionConditionMatch.NONE,
                    evaluateAcquisitionCondition(plan(match, action), sample))
    }

    @Test fun unknownReadingNeverSatisfiesAnyAllOrNone() {
        for (match in AcquisitionConditionMatch.entries) {
            for (action in AcquisitionConditionAction.entries) {
                val current = plan(match, action)
                for (sample in listOf(SensorSample(uvc = 1.0, uva = Double.NaN),
                    SensorSample(uvc = Double.POSITIVE_INFINITY, uva = 4.0))) {
                    assertNull(evaluateAcquisitionCondition(current, sample))
                    assertEquals(ConditionalAcquisitionDecision.SKIP,
                        conditionalAcquisitionDecision(current, action != AcquisitionConditionAction.START, null))
                }
                assertNull(evaluateAcquisitionCondition(current, SensorSample(uvc = 1.0, uva = 4.0),
                    uvAvailable = false))
            }
        }
    }
}
