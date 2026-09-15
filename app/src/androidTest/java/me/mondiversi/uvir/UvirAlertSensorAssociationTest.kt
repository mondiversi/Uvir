package me.mondiversi.uvir

import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UvirAlertSensorAssociationTest {
    private fun database(): SQLiteDatabase = SQLiteDatabase.create(null).apply {
        execSQL("CREATE TABLE sensors(id INTEGER PRIMARY KEY, hardware_uid TEXT COLLATE NOCASE)")
        execSQL("CREATE TABLE alert_sessions(session_id INTEGER PRIMARY KEY, sensor_id INTEGER)")
        execSQL("CREATE TABLE alerts(id INTEGER PRIMARY KEY, timestamp INTEGER, session_id INTEGER, sensor_id INTEGER, sensor_device_id TEXT, sensor_record_id INTEGER)")
        execSQL("CREATE UNIQUE INDEX sensor_record ON alerts(sensor_id, sensor_record_id)")
        execSQL("INSERT INTO sensors VALUES(1, 'UVIR-DEFAULT'), (2, 'UVIR-OTHER')")
    }

    private fun sensorId(db: SQLiteDatabase, table: String, idColumn: String, id: Long): Long? =
        db.rawQuery("SELECT sensor_id FROM $table WHERE $idColumn = ?", arrayOf(id.toString())).use {
            check(it.moveToFirst())
            if (it.isNull(0)) null else it.getLong(0)
        }

    @Test
    fun fillsOnlyMissingIdsAndKeepsKnownSensorAndSessionIdentities() {
        database().use { db ->
            db.execSQL("INSERT INTO alert_sessions VALUES(10, NULL), (20, 2)")
            db.execSQL("INSERT INTO alerts VALUES(1, 100, 10, NULL, NULL, NULL), (2, 200, 20, NULL, NULL, NULL), (3, 300, NULL, NULL, 'uvir-other', NULL), (4, 400, NULL, 2, NULL, NULL)")
            assertEquals(3, backfillUnassociatedAlertSensors(db, 1))
            assertEquals(1L, sensorId(db, "alerts", "id", 1))
            assertEquals(2L, sensorId(db, "alerts", "id", 2))
            assertEquals(2L, sensorId(db, "alerts", "id", 3))
            assertEquals(2L, sensorId(db, "alerts", "id", 4))
            assertEquals(1L, sensorId(db, "alert_sessions", "session_id", 10))
            assertEquals(2L, sensorId(db, "alert_sessions", "session_id", 20))
            assertEquals(0, backfillUnassociatedAlertSensors(db, 2))
        }
    }

    @Test
    fun ignoresInvalidDefaultAndPreservesRecordsWhenSourceIdsWouldCollide() {
        database().use { db ->
            db.execSQL("INSERT INTO alerts VALUES(1, 100, NULL, 1, NULL, 7), (2, 200, NULL, NULL, NULL, 7)")
            assertEquals(0, backfillUnassociatedAlertSensors(db, 0))
            assertEquals(0, backfillUnassociatedAlertSensors(db, 1))
            assertEquals(1L, sensorId(db, "alerts", "id", 1))
            assertEquals(null, sensorId(db, "alerts", "id", 2))
            db.rawQuery("SELECT COUNT(*) FROM alerts", null).use {
                check(it.moveToFirst())
                assertEquals(2, it.getInt(0))
            }
        }
    }
}
