package me.mondiversi.uvir

internal const val KEY_AUTO_CONDITIONAL_ENABLED = "auto_conditional_enabled"
internal const val KEY_AUTO_CONDITIONAL_MATCH = "auto_conditional_match"
internal const val KEY_AUTO_CONDITIONAL_ACTION = "auto_conditional_action"
internal const val KEY_AUTO_CONDITIONAL_PLAN = "auto_conditional_plan"
internal const val KEY_AUTO_CONDITIONAL_WAITING = "auto_conditional_waiting"
internal const val KEY_AUTO_FIRST_ALLOWED_MS = "auto_first_allowed_ms"

enum class AcquisitionConditionMatch { ANY, ALL, NONE }
enum class AcquisitionConditionAction { START, STOP, ACQUIRE }

data class ConditionalAcquisitionPlan(
    val match: AcquisitionConditionMatch,
    val action: AcquisitionConditionAction,
    val rules: List<ThresholdAlertRule>
) {
    fun isValid(): Boolean = rules.isNotEmpty() && rules.size <= 24 &&
        rules.map { it.metric }.distinct().size == rules.size &&
        rules.all { it.enabled && it.threshold.isFinite() && it.threshold >= 0f }

    /** Enum names and numbers only: no notes, credentials or localized text. */
    fun encode(): String = match.name + "|" + action.name + "|" +
        rules.joinToString(";") { "${it.metric.name},${it.direction.name},${it.threshold}" }
}

internal fun conditionalPlanFromRules(
    rules: List<ThresholdAlertRule>,
    match: AcquisitionConditionMatch,
    action: AcquisitionConditionAction
): ConditionalAcquisitionPlan? =
    ConditionalAcquisitionPlan(match, action, rules.filter { it.enabled }.map { it.copy() })
        .takeIf { it.isValid() }

internal fun decodeConditionalAcquisitionPlan(text: String?): ConditionalAcquisitionPlan? =
    runCatching {
        val parts = text.orEmpty().split('|')
        require(parts.size == 3)
        ConditionalAcquisitionPlan(
            AcquisitionConditionMatch.valueOf(parts[0]),
            AcquisitionConditionAction.valueOf(parts[1]),
            parts[2].split(';').map {
                val fields = it.split(',')
                require(fields.size == 3)
                ThresholdAlertRule(ThresholdAlertMetric.valueOf(fields[0]), true,
                    ThresholdAlertDirection.valueOf(fields[1]), fields[2].toFloat())
            }
        ).takeIf { it.isValid() }
    }.getOrNull()

/** Old snapshots remain readable; new starts require sample-level monitoring. */
internal fun firmwareSupportsConditionalAcquisition(version: String): Boolean =
    compareFirmwareVersions(version, "0.5.73") >= 0

internal fun firmwareSupportsImmediateConditionalAcquisition(version: String): Boolean =
    compareFirmwareVersions(version, "0.5.75") >= 0

internal fun evaluateAcquisitionCondition(
    plan: ConditionalAcquisitionPlan,
    sample: SensorSample,
    uvAvailable: Boolean = true
): Boolean? {
    if (!plan.isValid()) return null
    val matches = plan.rules.map { rule ->
        val needsUv = rule.metric in listOf(ThresholdAlertMetric.UV_TOTAL,
            ThresholdAlertMetric.UVC, ThresholdAlertMetric.UVB, ThresholdAlertMetric.UVA,
            ThresholdAlertMetric.BIO_DNA_UV, ThresholdAlertMetric.BIO_UVA_PHOTOAGING)
        if (needsUv && !uvAvailable) return null
        val value = sample.thresholdMetricValue(rule.metric)
        if (!value.isFinite()) return null
        if (rule.direction == ThresholdAlertDirection.ABOVE) value >= rule.threshold
        else value <= rule.threshold
    }
    return when (plan.match) {
        AcquisitionConditionMatch.ANY -> matches.any { it }
        AcquisitionConditionMatch.ALL -> matches.all { it }
        AcquisitionConditionMatch.NONE -> matches.none { it }
    }
}

internal enum class ConditionalAcquisitionDecision { RECORD, SKIP, START_AND_RECORD, STOP }

internal fun conditionalAcquisitionDecision(
    plan: ConditionalAcquisitionPlan?,
    started: Boolean,
    condition: Boolean?
): ConditionalAcquisitionDecision {
    if (plan == null || (plan.action == AcquisitionConditionAction.START && started))
        return ConditionalAcquisitionDecision.RECORD
    if (condition == null) return ConditionalAcquisitionDecision.SKIP
    return when (plan.action) {
        AcquisitionConditionAction.START -> if (condition) ConditionalAcquisitionDecision.START_AND_RECORD
            else ConditionalAcquisitionDecision.SKIP
        AcquisitionConditionAction.STOP -> if (condition) ConditionalAcquisitionDecision.STOP
            else ConditionalAcquisitionDecision.RECORD
        AcquisitionConditionAction.ACQUIRE -> if (condition) ConditionalAcquisitionDecision.RECORD
            else ConditionalAcquisitionDecision.SKIP
    }
}
