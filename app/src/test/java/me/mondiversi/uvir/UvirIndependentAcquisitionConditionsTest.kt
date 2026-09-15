package me.mondiversi.uvir

import org.junit.Assert.*
import org.junit.Test

class UvirIndependentAcquisitionConditionsTest {
    private fun rule(enabled: Boolean = true) =
        ThresholdAlertRule(ThresholdAlertMetric.UVC, enabled, ThresholdAlertDirection.ABOVE, 7f)

    @Test fun criteriaCreateAPlanWithoutAnyAlertSettings() {
        val plan = conditionalPlanFromRules(listOf(rule()), AcquisitionConditionMatch.ALL, AcquisitionConditionAction.START)
        assertNotNull(plan)
        assertEquals(7f, plan!!.rules.single().threshold)
    }
    @Test fun unselectedCriteriaCannotEnableConditionalAcquisition() {
        assertNull(conditionalPlanFromRules(listOf(rule(false)), AcquisitionConditionMatch.ANY, AcquisitionConditionAction.ACQUIRE))
    }
    @Test fun copiedCriteriaDoNotChangeWhenThePhoneDraftChanges() {
        val draft = mutableListOf(rule())
        val plan = conditionalPlanFromRules(draft, AcquisitionConditionMatch.NONE, AcquisitionConditionAction.STOP)!!
        draft[0] = rule().copy(threshold = 99f, direction = ThresholdAlertDirection.BELOW)
        assertEquals(rule(), plan.rules.single())
    }
    @Test fun allGroupsKeepTheSameOrderAndHaveNoDuplicateLegacyViolet() {
        val metrics = acquisitionConditionMetrics()
        assertEquals(18, metrics.size)
        assertEquals(ThresholdAlertMetric.UV_TOTAL, metrics.first())
        assertFalse(metrics.contains(ThresholdAlertMetric.HEB))
        assertEquals(ThresholdAlertMetric.BIO_HEV_OXIDATIVE, metrics.last())
    }
    @Test fun invalidThresholdsCannotReachTheSensor() {
        for (value in listOf(-1f, Float.NaN, Float.POSITIVE_INFINITY)) {
            assertNull(conditionalPlanFromRules(listOf(rule().copy(threshold = value)),
                AcquisitionConditionMatch.ANY, AcquisitionConditionAction.ACQUIRE))
        }
    }
}
