package me.mondiversi.uvir

import org.junit.Assert.*
import org.junit.Test

class UvirConditionalAcquisitionTest {
    private val sample = SensorSample(uvc = 1.0, uvb = 2.0, uva = 3.0, violetto = 4.0, blu = 5.0, f8 = 2.0, nir = 3.0)
    private fun plan(match: AcquisitionConditionMatch = AcquisitionConditionMatch.ANY,
        action: AcquisitionConditionAction = AcquisitionConditionAction.ACQUIRE,
        rules: List<ThresholdAlertRule> = listOf(
            ThresholdAlertRule(ThresholdAlertMetric.UV_TOTAL,true,ThresholdAlertDirection.ABOVE,6f),
            ThresholdAlertRule(ThresholdAlertMetric.NIR_TOTAL,true,ThresholdAlertDirection.BELOW,4f)
        )) = ConditionalAcquisitionPlan(match,action,rules)

    @Test fun anyIncludesEqualityAndMixedDirections() { assertEquals(true,evaluateAcquisitionCondition(plan(),sample)) }
    @Test fun allMustBeTrueAtTheSameTime() { assertEquals(false,evaluateAcquisitionCondition(plan(AcquisitionConditionMatch.ALL),sample)) }
    @Test fun noneNegatesAnyNotAll() { assertEquals(false,evaluateAcquisitionCondition(plan(AcquisitionConditionMatch.NONE),sample)) }
    @Test fun noneCanBeTrue() { assertEquals(true,evaluateAcquisitionCondition(plan(AcquisitionConditionMatch.NONE),SensorSample(nir=10.0))) }
    @Test fun belowIncludesEquality() { assertEquals(true,evaluateAcquisitionCondition(plan(rules=listOf(
        ThresholdAlertRule(ThresholdAlertMetric.NIR_TOTAL,true,ThresholdAlertDirection.BELOW,5f))),sample)) }
    @Test fun unavailableUvIsUnknownEvenForNone() { assertNull(evaluateAcquisitionCondition(plan(AcquisitionConditionMatch.NONE),sample,false)) }
    @Test fun unavailableUvDoesNotInvalidateVisibleOnlyRules() { assertEquals(true,evaluateAcquisitionCondition(plan(rules=listOf(
        ThresholdAlertRule(ThresholdAlertMetric.HEV,true,ThresholdAlertDirection.ABOVE,9f))),sample,false)) }
    @Test fun nonFiniteSamplesAreNotFalse() { assertNull(evaluateAcquisitionCondition(plan(),sample.copy(uvc=Double.NaN))) }
    @Test fun emptyRulesAreInvalid() { assertFalse(plan(rules=emptyList()).isValid()) }
    @Test fun duplicatesAreInvalid() { assertFalse(plan(rules=listOf(plan().rules.first(),plan().rules.first())).isValid()) }
    @Test fun negativeAndNonFiniteThresholdsAreInvalid() { for (t in listOf(-1f,Float.NaN,Float.POSITIVE_INFINITY))
        assertFalse(plan(rules=listOf(plan().rules.first().copy(threshold=t))).isValid()) }
    @Test fun criteriaSnapshotDoesNotDependOnAlertMonitoring() {
        val settings=ThresholdAlertSettings(enabled=false,rules=plan().rules,repeatSeconds=30,sound=ThresholdAlertSound.SINGLE_BEEP,volume=10)
        assertNotNull(conditionalPlanFromRules(settings.rules,AcquisitionConditionMatch.ALL,AcquisitionConditionAction.START))
    }
    @Test fun disabledIndividualRulesAreExcluded() {
        val settings=ThresholdAlertSettings(enabled=false,rules=plan().rules.map { it.copy(enabled=false) },repeatSeconds=30,sound=ThresholdAlertSound.SINGLE_BEEP,volume=10)
        assertNull(conditionalPlanFromRules(settings.rules,AcquisitionConditionMatch.ANY,AcquisitionConditionAction.ACQUIRE))
    }
    @Test fun snapshotIsIndependentOfLaterEdits() {
        val mutableRules=plan().rules.toMutableList()
        val snapshot=conditionalPlanFromRules(mutableRules,
            AcquisitionConditionMatch.ANY,AcquisitionConditionAction.ACQUIRE)!!
        mutableRules[0]=mutableRules[0].copy(threshold=100f)
        assertEquals(6f,snapshot.rules[0].threshold)
    }
    @Test fun planRoundTripsWithoutLocalizedNames() { for(m in AcquisitionConditionMatch.entries)
        for(a in AcquisitionConditionAction.entries) assertEquals(plan(m,a),decodeConditionalAcquisitionPlan(plan(m,a).encode())) }
    @Test fun malformedReadbackCannotBecomeActiveConditions() { for(text in listOf("", "ANY|START|", "ANY|UNKNOWN|UV_TOTAL,ABOVE,1",
        "ANY|START|UV_TOTAL,ABOVE,NaN","ANY|START|UNSUPPORTED,ABOVE,1","ANY|START|UV_TOTAL,ABOVE,-1"))
        assertNull(decodeConditionalAcquisitionPlan(text)) }
    @Test fun startWaitsThenLatches() {
        val p=plan(action=AcquisitionConditionAction.START)
        assertEquals(ConditionalAcquisitionDecision.SKIP,conditionalAcquisitionDecision(p,false,false))
        assertEquals(ConditionalAcquisitionDecision.START_AND_RECORD,conditionalAcquisitionDecision(p,false,true))
        assertEquals(ConditionalAcquisitionDecision.RECORD,conditionalAcquisitionDecision(p,true,false))
        assertEquals(ConditionalAcquisitionDecision.RECORD,conditionalAcquisitionDecision(p,true,null))
    }
    @Test fun stopTerminatesOnlyOnValidTrueCondition() {
        val p=plan(action=AcquisitionConditionAction.STOP)
        assertEquals(ConditionalAcquisitionDecision.STOP,conditionalAcquisitionDecision(p,true,true))
        assertEquals(ConditionalAcquisitionDecision.RECORD,conditionalAcquisitionDecision(p,true,false))
        assertEquals(ConditionalAcquisitionDecision.SKIP,conditionalAcquisitionDecision(p,true,null))
    }
    @Test fun acquireSkipsWithoutAdvancingStoredCount() {
        val p=plan(); var stored=0
        for (result in listOf(false,null,true,false,true))
            if(conditionalAcquisitionDecision(p,true,result)==ConditionalAcquisitionDecision.RECORD) stored++
        assertEquals(2,stored)
    }
    @Test fun normalAcquisitionDoesNotRequireConditions() {
        assertEquals(ConditionalAcquisitionDecision.RECORD,conditionalAcquisitionDecision(null,true,null))
    }
    @Test fun conditionalProtocolIsAtomicAndOrdinaryProtocolUnchanged() {
        val request=AutomaticAcquisitionRequest(5,"note",false,0,true,30,true,3)
        val params=AcquisitionParameters(5,150,true)
        val normal=sensorOfflineJobCommand(request,1,1000,31000,0,params)
        assertEquals("OFFLINE_JOB 1 1000 5 31000 3 0 5 150 1 -",normal)
        val conditional=sensorOfflineJobCommand(request.copy(conditionalPlan=plan(action=AcquisitionConditionAction.START)),1,1000,0,0,params)
        assertEquals("OFFLINE_CONDITIONAL_JOB 1 1000 5 0 3 0 5 150 1 - START ANY 30 2 UV_TOTAL ABOVE 6.0 NIR_TOTAL BELOW 4.0",conditional)
    }
    @Test fun oldFirmwareCannotSilentlyIgnoreConditions() {
        assertFalse(firmwareSupportsConditionalAcquisition("0.5.72"))
        assertTrue(firmwareSupportsConditionalAcquisition("0.5.73"))
    }
}
