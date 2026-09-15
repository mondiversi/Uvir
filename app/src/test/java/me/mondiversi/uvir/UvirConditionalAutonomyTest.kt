package me.mondiversi.uvir
import org.junit.Assert.*
import org.junit.Test

class UvirConditionalAutonomyTest {
    private val estimate=UvirOfflineAutonomyEstimate(3200,2800,100,500,true,false)
    private fun plan(action:AcquisitionConditionAction)=ConditionalAcquisitionPlan(AcquisitionConditionMatch.ANY,action,
        listOf(ThresholdAlertRule(ThresholdAlertMetric.HEV,true,ThresholdAlertDirection.ABOVE,1f)))
    @Test fun gatedAcquisitionKeepsExactCapacityWithoutInventingDuration() {
        val actual=estimate.withConditionalSchedule(plan(AcquisitionConditionAction.ACQUIRE),false)
        assertEquals(2800,actual.remaining); assertNull(actual.durationSeconds); assertFalse(actual.durationKnown)
    }
    @Test fun waitingStartHasUnknownWaitingTime() {
        assertFalse(estimate.withConditionalSchedule(plan(AcquisitionConditionAction.START),true).durationKnown)
    }
    @Test fun triggeredStartUsesNormalSchedule() {
        assertEquals(estimate,estimate.withConditionalSchedule(plan(AcquisitionConditionAction.START),false))
    }
    @Test fun conditionalStopHasUnknownFutureStopTime() {
        assertFalse(estimate.withConditionalSchedule(plan(AcquisitionConditionAction.STOP),false).durationKnown)
    }
    @Test fun ordinaryScheduleIsUnchanged() { assertEquals(estimate,estimate.withConditionalSchedule(null,false)) }
}
