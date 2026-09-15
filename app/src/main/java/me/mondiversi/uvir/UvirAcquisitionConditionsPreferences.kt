package me.mondiversi.uvir

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

internal const val KEY_AUTO_CONDITIONAL_RULES = "auto_conditional_rules"

/** Dedicated phone-only criteria. Never reads or writes the value-alert preferences. */
internal fun acquisitionConditionMetrics(): List<ThresholdAlertMetric> =
    SensorGroup.entries.flatMap(::thresholdAlertMetricsForGroup).distinct()

internal fun loadAcquisitionConditions(preferences: SharedPreferences): List<ThresholdAlertRule> {
    val stored = runCatching {
        val array = JSONArray(preferences.getString(KEY_AUTO_CONDITIONAL_RULES, "[]") ?: "[]")
        (0 until array.length()).mapNotNull { index ->
            runCatching {
                val rule = array.getJSONObject(index)
                ThresholdAlertRule(
                    ThresholdAlertMetric.valueOf(rule.getString("metric")),
                    rule.getBoolean("enabled"),
                    ThresholdAlertDirection.valueOf(rule.getString("direction")),
                    rule.getDouble("threshold").toFloat()
                ).takeIf { it.threshold.isFinite() && it.threshold >= 0f }
            }.getOrNull()
        }.associateBy { it.metric }
    }.getOrDefault(emptyMap())
    return acquisitionConditionMetrics().map { metric ->
        stored[metric] ?: ThresholdAlertRule(metric, false, ThresholdAlertDirection.ABOVE, 1f)
    }
}
internal fun saveAcquisitionConditions(
    preferences: SharedPreferences,
    rules: List<ThresholdAlertRule>
): Boolean {
    val allowed = acquisitionConditionMetrics().toSet()
    if (rules.any { it.metric !in allowed || !it.threshold.isFinite() || it.threshold < 0f } ||
        rules.map { it.metric }.distinct().size != rules.size) return false
    val array = JSONArray()
    rules.forEach { rule ->
        array.put(JSONObject().put("metric", rule.metric.name).put("enabled", rule.enabled)
            .put("direction", rule.direction.name).put("threshold", rule.threshold.toDouble()))
    }
    return preferences.edit().putString(KEY_AUTO_CONDITIONAL_RULES, array.toString()).commit()
}
