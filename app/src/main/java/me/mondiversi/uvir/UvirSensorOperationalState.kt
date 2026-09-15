package me.mondiversi.uvir

import android.content.SharedPreferences

/** Only the active app context: no records, sensor profiles or user values. */
internal val sensorOperationalPreferenceKeys: List<String> =
    listOf(
        KEY_AUTO_ENABLED,
        KEY_AUTO_SIMULATED,
        KEY_AUTO_SESSION_ID,
        KEY_AUTO_COMPLETED_COUNT,
        KEY_AUTO_NEXT_SAVE_MS,
        KEY_AUTO_END_MS,
        KEY_AUTO_SCHEDULE_KNOWN,
        KEY_AUTO_CONDITIONAL_PLAN, KEY_AUTO_CONDITIONAL_WAITING, KEY_AUTO_FIRST_ALLOWED_MS,
        KEY_THRESHOLD_ALERT_ENABLED,
        KEY_THRESHOLD_ALERT_SESSION_ID,
        KEY_LAST_MANUAL_SESSION_ID,
        LEGACY_KEY_LAST_MANUAL_SESSION_ACTIVITY_MS,
        KEY_OFFLINE_REOPEN_NOTICE_PENDING,
        KEY_OFFLINE_REOPEN_AUTO_ACTIVE,
        KEY_OFFLINE_REOPEN_ALERTS_ACTIVE,
        KEY_OFFLINE_REOPEN_NEXT_SAVE_MS,
        KEY_OFFLINE_REOPEN_END_MS,
        KEY_OFFLINE_REOPEN_COMPLETED_COUNT
    ) + persistedOfflineNoticeKeys + ThresholdAlertMetric.entries.map {
        thresholdRulePreferenceKey(it, "enabled")
    }

internal fun clearSensorOperationalState(
    preferences: SharedPreferences
): Boolean {
    val editor = preferences.edit()
    sensorOperationalPreferenceKeys.forEach(editor::remove)
    return editor
        .putString(KEY_MANUAL_SAVE_MODE, ManualSaveMode.SINGLE.storedValue)
        .commit()
}

internal fun ThresholdAlertSettings.withoutAssociatedSensor():
    ThresholdAlertSettings =
    copy(enabled = false, rules = rules.map { it.copy(enabled = false) })

internal fun UvirSensorRuntimeInfo.activeAutomaticSessionIdOrNull(): Long? =
    offlineSessionId?.takeIf { offlineRecording == true && it > 0L }

/** An idle sensor has no session note to replace the app's next-alert note. */
internal fun restoreAlertSessionNote(
    activeSessionId: Long,
    currentNote: String,
    readSessionNote: (Long) -> String
): String =
    if (activeSessionId > 0L) readSessionNote(activeSessionId) else currentNote
