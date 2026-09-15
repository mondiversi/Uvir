package me.mondiversi.uvir

import android.database.sqlite.SQLiteDatabase

/** Repairs old records only; known identities take precedence over the default. */
internal fun backfillUnassociatedAlertSensors(database: SQLiteDatabase, defaultSensorId: Long): Int {
    if (defaultSensorId <= 0L) return 0
    database.beginTransaction()
    val changed: Int
    try {
        changed = database.compileStatement(
            """
            UPDATE OR IGNORE alerts
            SET sensor_id = COALESCE(
                (SELECT sensor.id FROM sensors AS sensor
                 WHERE sensor.hardware_uid = TRIM(alerts.sensor_device_id)),
                (SELECT session.sensor_id FROM alert_sessions AS session
                 WHERE session.session_id = alerts.session_id),
                ?
            )
            WHERE sensor_id IS NULL
            """.trimIndent()
        ).use { statement ->
            statement.bindLong(1, defaultSensorId)
            statement.executeUpdateDelete()
        }
        database.execSQL(
            """
            UPDATE alert_sessions
            SET sensor_id = (
                SELECT alert.sensor_id FROM alerts AS alert
                WHERE alert.session_id = alert_sessions.session_id
                  AND alert.sensor_id IS NOT NULL
                ORDER BY alert.timestamp, alert.id
                LIMIT 1
            )
            WHERE sensor_id IS NULL
              AND EXISTS (
                SELECT 1 FROM alerts AS alert
                WHERE alert.session_id = alert_sessions.session_id
                  AND alert.sensor_id IS NOT NULL
              )
            """.trimIndent()
        )
        database.setTransactionSuccessful()
    } finally {
        database.endTransaction()
    }
    return changed
}
