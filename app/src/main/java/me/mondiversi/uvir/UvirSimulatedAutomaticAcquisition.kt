package me.mondiversi.uvir

internal const val KEY_AUTO_SIMULATED = "auto_simulated"

internal sealed interface SimulatedAutomaticAcquisitionStep {
    data object Stop : SimulatedAutomaticAcquisitionStep
    data class Wait(val delayMs: Long) : SimulatedAutomaticAcquisitionStep
    data class Acquire(val nextAtMs: Long, val startsSession: Boolean = false) : SimulatedAutomaticAcquisitionStep
}

/** Scheduling for debug data only. Real acquisitions remain owned by the ESP32. */
internal fun simulatedAutomaticAcquisitionStep(
    nowMs: Long,
    nextAtMs: Long,
    intervalSeconds: Long,
    endAtMs: Long?,
    maximumCount: Int?,
    completedCount: Int,
    sampleReady: Boolean
): SimulatedAutomaticAcquisitionStep {
    if (
        (endAtMs != null && nowMs >= endAtMs) ||
        (maximumCount != null && completedCount >= maximumCount)
    ) {
        return SimulatedAutomaticAcquisitionStep.Stop
    }
    if (!sampleReady) return SimulatedAutomaticAcquisitionStep.Wait(100L)
    if (nowMs < nextAtMs) {
        val remainingDuration = endAtMs?.minus(nowMs) ?: Long.MAX_VALUE
        return SimulatedAutomaticAcquisitionStep.Wait(
            minOf(nextAtMs - nowMs, remainingDuration, 1_000L)
        )
    }
    val intervalMs = intervalSeconds.coerceIn(1L, Long.MAX_VALUE / 1_000L) * 1_000L
    // After an app restart, acquire a current sample, not a burst of invented
    // historical samples for every interval that elapsed while Android was off.
    val baseMs = maxOf(nowMs, nextAtMs)
    return SimulatedAutomaticAcquisitionStep.Acquire(
        if (baseMs > Long.MAX_VALUE - intervalMs) Long.MAX_VALUE else baseMs + intervalMs
    )
}


/** Evaluate conditions only while monitoring is allowed, never during Acquire's
 * post-recording cooldown. This mirrors the autonomous firmware state machine. */
internal fun simulatedConditionalAutomaticAcquisitionStep(
    nowMs: Long, firstAllowedAtMs: Long, nextAtMs: Long, intervalSeconds: Long,
    endAtMs: Long?, maximumCount: Int?, completedCount: Int, sampleReady: Boolean,
    plan: ConditionalAcquisitionPlan?, started: Boolean, condition: () -> Boolean?
): SimulatedAutomaticAcquisitionStep {
    if ((endAtMs != null && nowMs >= endAtMs) ||
        (maximumCount != null && completedCount >= maximumCount))
        return SimulatedAutomaticAcquisitionStep.Stop
    if (plan == null || (plan.action == AcquisitionConditionAction.START && started))
        return simulatedAutomaticAcquisitionStep(nowMs, nextAtMs, intervalSeconds,
            endAtMs, maximumCount, completedCount, sampleReady)
    val eligibleAt = if (plan.action == AcquisitionConditionAction.ACQUIRE)
        maxOf(firstAllowedAtMs, nextAtMs) else firstAllowedAtMs
    val waitMs = minOf(150L, endAtMs?.minus(nowMs) ?: Long.MAX_VALUE).coerceAtLeast(1L)
    if (nowMs < eligibleAt || !sampleReady) return SimulatedAutomaticAcquisitionStep.Wait(waitMs)
    val decision = conditionalAcquisitionDecision(plan, started, condition())
    if (decision == ConditionalAcquisitionDecision.STOP) return SimulatedAutomaticAcquisitionStep.Stop
    if (decision == ConditionalAcquisitionDecision.SKIP ||
        (plan.action == AcquisitionConditionAction.STOP && nowMs < nextAtMs))
        return SimulatedAutomaticAcquisitionStep.Wait(waitMs)
    val acquire = simulatedAutomaticAcquisitionStep(nowMs, nowMs, intervalSeconds,
        endAtMs, maximumCount, completedCount, true) as SimulatedAutomaticAcquisitionStep.Acquire
    return acquire.copy(startsSession = decision == ConditionalAcquisitionDecision.START_AND_RECORD)
}
