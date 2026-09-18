package me.mondiversi.uvir

import android.content.Context
import android.content.ContextWrapper
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.UUID
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** All database tests use a private temporary database, never the user's uvir.db. */
class UvirInterruptedAutomaticSessionTest {
    private fun hello(active: Boolean = false, id: Long = 0) = JSONObject()
        .put("type", "hello").put("protocol", UVIR_SENSOR_PROTOCOL)
        .put("device_id", "A").put("offline_recording", active).put("offline_session_id", id)

    @Test fun onlyCompleteLiveHelloFramesProvideFreshAutomaticState() {
        val fresh = UvirSensorRuntimeInfo().updatedFrom(hello())
        assertNotNull(fresh.automaticJobReadback)
        val boundary = currentAutomaticJobReadbackRevision()
        val next = fresh.updatedFrom(hello())
        assertTrue(next.automaticJobReadback!!.revision > boundary)
        assertFalse(next.automaticJobReadback!!.active)
        assertEquals(0L, next.automaticJobReadback!!.sessionId)
        for (type in listOf("status", "diagnostic", "sample", "activity", "")) {
            assertNull(UvirSensorRuntimeInfo().updatedFrom(hello().put("type", type)).automaticJobReadback)
        }
        val stored = hello().apply { remove("type") }
        assertNull(UvirSensorRuntimeInfo().updatedFrom(stored).automaticJobReadback)
        for (field in listOf("offline_recording", "offline_session_id", "device_id")) {
            assertNull(fresh.updatedFrom(hello().apply { remove(field) }).automaticJobReadback)
        }
        assertNull(fresh.updatedFrom(hello().put("offline_recording", "false")).automaticJobReadback)
        assertNull(fresh.updatedFrom(hello().put("offline_session_id", 0.5)).automaticJobReadback)
        assertNull(fresh.updatedFrom(hello().put("protocol", "unrelated")).automaticJobReadback)
        assertNull(fresh.updatedFrom(hello(true, 0)).automaticJobReadback)
        assertNull(fresh.updatedFrom(hello(false, -1)).automaticJobReadback)
    }

    private inline fun isolatedDatabase(test: (UvirDatabaseHelper) -> Unit) {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(base.cacheDir, "interrupted-session-test-${UUID.randomUUID()}")
        assertTrue(directory.mkdir())
        val isolatedContext = object : ContextWrapper(base) {
            override fun getApplicationContext(): Context = this
            override fun getDatabasePath(name: String): File = File(directory, name)
            override fun getSharedPreferences(name: String, mode: Int): android.content.SharedPreferences =
                error("This test must not access app preferences")
            override fun openOrCreateDatabase(name: String, mode: Int, factory: SQLiteDatabase.CursorFactory?): SQLiteDatabase =
                SQLiteDatabase.openOrCreateDatabase(getDatabasePath(name), factory)
            override fun openOrCreateDatabase(name: String, mode: Int, factory: SQLiteDatabase.CursorFactory?,
                errorHandler: DatabaseErrorHandler?): SQLiteDatabase =
                SQLiteDatabase.openOrCreateDatabase(getDatabasePath(name).absolutePath, factory, errorHandler)
        }
        val database = UvirDatabaseHelper(isolatedContext)
        try {
            assertTrue(database.writableDatabase.path.startsWith(directory.absolutePath + File.separator))
            assertTrue(database.writableDatabase.path != base.getDatabasePath("uvir.db").absolutePath)
            test(database)
        } finally {
            database.close()
            directory.listFiles().orEmpty().forEach { assertTrue(it.delete()) }
            assertTrue(directory.delete())
        }
    }

    private fun end(database: UvirDatabaseHelper, id: Long): Long? =
        database.readableDatabase.rawQuery("SELECT ended_at FROM acquisition_sessions WHERE session_id = ?",
            arrayOf(id.toString())).use { cursor ->
            assertTrue(cursor.moveToFirst())
            if (cursor.isNull(0)) null else cursor.getLong(0)
        }

    private fun recover(database: UvirDatabaseHelper, time: Long, sequence: Int, session: Long = 12,
        sensor: String = "A", record: Long = sequence.toLong()) = database.saveRecoveredAcquisition(
        timestamp = time, sample = SensorSample(uva = 2.0), note = "", sessionId = session,
        sequence = sequence, sensorDeviceId = sensor, sensorRecordId = record
    )

    @Test fun lostJobEndsAtLastSavedRecordAndRecoveryExtendsItWithoutReopening() = isolatedDatabase { database ->
        database.startAcquisitionSession(12, "Session note", 1_000, "A")
        assertTrue(recover(database, 2_000, 1))
        assertTrue(database.finishInterruptedAcquisitionSession(12, "A"))
        assertEquals(2_000L, end(database, 12))
        database.ensureAcquisitionSession(12, "", 9_000, "A")
        assertEquals(2_000L, end(database, 12)) // Reopening the app cannot reopen a lost job.
        assertTrue(recover(database, 3_000, 2))
        assertEquals(3_000L, end(database, 12))
        assertTrue(recover(database, 2_500, 3))
        assertEquals(3_000L, end(database, 12))
        assertTrue(recover(database, 3_000, 2)) // Repeated record is acknowledged, not duplicated.
        assertEquals(3, database.acquisitionCountForSession(12))
        assertEquals("Session note", database.readAcquisitionSessionNote(12))
        assertEquals(listOf(1, 2, 3), database.readSessionRecords(12).map { it.sessionSequence }.sortedBy { it })
    }

    @Test fun otherSensorsAndNormallyFinishedOrActiveSessionsArePreserved() = isolatedDatabase { database ->
        database.startAcquisitionSession(12, "A note", 1_000, "A")
        database.startAcquisitionSession(13, "B note", 1_100, "B")
        database.startAlertSession(14, "Alert note", 1_200, "A")
        assertFalse(database.finishInterruptedAcquisitionSession(12, "B"))
        assertNull(end(database, 12))
        assertTrue(database.finishInterruptedAcquisitionSession(12, "A"))
        assertEquals(1_000L, end(database, 12)) // No events: known start, not invented power-loss time.
        assertNull(end(database, 13))
        assertTrue(recover(database, 2_000, 1, session = 13, sensor = "B"))
        assertNull(end(database, 13))
        database.finishAcquisitionSession(13, 4_000)
        assertTrue(recover(database, 3_000, 2, session = 13, sensor = "B"))
        assertEquals(4_000L, end(database, 13))
        assertEquals("B note", database.readAcquisitionSessionNote(13))
        assertEquals("Alert note", database.readAlertSessionNote(14))
        database.readableDatabase.rawQuery("SELECT ended_at FROM alert_sessions WHERE session_id = 14", null).use {
            assertTrue(it.moveToFirst()); assertTrue(it.isNull(0))
        }
    }

    @Test fun sensorOriginatedSessionTokensMapStablyPerSensor() = isolatedDatabase { database ->
        val remoteToken = (1L shl 62) or 1_789_000_000_000L
        val first = database.resolveSensorOriginatedAcquisitionSession(
            sensorDeviceId = "A",
            sensorSessionId = remoteToken,
            startedAt = 1_000L
        )
        val repeated = database.resolveSensorOriginatedAcquisitionSession(
            sensorDeviceId = "A",
            sensorSessionId = remoteToken,
            startedAt = 9_000L
        )
        val otherSensor = database.resolveSensorOriginatedAcquisitionSession(
            sensorDeviceId = "B",
            sensorSessionId = remoteToken,
            startedAt = 2_000L
        )

        assertTrue(first > 0L)
        assertEquals(first, repeated)
        assertTrue(otherSensor > 0L)
        assertTrue(first != otherSensor)
        assertTrue(first < (1L shl 62))
        assertTrue(otherSensor < (1L shl 62))
    }
}
