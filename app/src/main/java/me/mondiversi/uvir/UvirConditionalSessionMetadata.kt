package me.mondiversi.uvir

import android.content.ContentValues

/** Only a confirmed START trigger can move a reserved session's start forward. */
internal fun UvirDatabaseHelper.confirmConditionalSessionStart(
    sessionId: Long,
    sensorDeviceId: String,
    startedAtMs: Long
): Boolean {
    if (sessionId <= 0L || sensorDeviceId.isBlank() || startedAtMs <= 0L) return false
    return writableDatabase.update(
        "acquisition_sessions",
        ContentValues().apply { put("started_at", startedAtMs) },
        """session_id = ? AND sensor_id = (SELECT id FROM sensors WHERE hardware_uid = ?)
           AND started_at < ? AND NOT EXISTS (
               SELECT 1 FROM acquisitions WHERE session_id = ? AND timestamp < ?
           )""",
        arrayOf(sessionId.toString(), sensorDeviceId, startedAtMs.toString(),
            sessionId.toString(), startedAtMs.toString())
    ) > 0
}
