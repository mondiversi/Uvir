package me.mondiversi.uvir

import android.content.Context
import android.content.ContextWrapper
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.UUID
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class UvirExternalAcquisitionAttributionTest {
    private lateinit var root: File
    private lateinit var helper: UvirDatabaseHelper

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        root = File(context.cacheDir, "external-attribution-${UUID.randomUUID()}")
        assertTrue(root.mkdir())
        helper = UvirDatabaseHelper(object : ContextWrapper(context) {
            override fun getApplicationContext(): Context = this
            override fun getDatabasePath(databaseName: String) = File(root, databaseName)
            override fun openOrCreateDatabase(
                databaseName: String,
                mode: Int,
                factory: SQLiteDatabase.CursorFactory?
            ): SQLiteDatabase = SQLiteDatabase.openOrCreateDatabase(getDatabasePath(databaseName), factory)
            override fun openOrCreateDatabase(
                databaseName: String,
                mode: Int,
                factory: SQLiteDatabase.CursorFactory?,
                errorHandler: DatabaseErrorHandler?
            ): SQLiteDatabase = SQLiteDatabase.openOrCreateDatabase(
                getDatabasePath(databaseName).absolutePath, factory, errorHandler
            )
        })
    }

    @After
    fun tearDown() {
        helper.close()
        root.deleteRecursively()
    }

    @Test
    fun externalPressMarksOnlyItsRecordInsideAutomaticSession() {
        assertTrue(helper.saveRecoveredAcquisition(
            timestamp = 1_000L, sample = SensorSample(), note = "session",
            sessionId = 17L, sequence = 1, sensorDeviceId = "SENSOR-A",
            sensorRecordId = 1L, externalCommand = false
        ))
        assertTrue(helper.saveRecoveredAcquisition(
            timestamp = 2_000L, sample = SensorSample(), note = "session",
            sessionId = 17L, sequence = 2, sensorDeviceId = "SENSOR-A",
            sensorRecordId = 2L, externalCommand = true
        ))
        assertEquals(listOf(0 to 1, 1 to 1), recordFlags(17L))
        assertFalse(helper.acquisitionSessionUsesExternalCommand(17L))
        helper.readableDatabase.rawQuery(
            "SELECT external_command FROM acquisition_sessions WHERE session_id = 17", null
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        }
    }

    @Test
    fun externalPressJoinsOnlyRememberedManualSessionOnSameSensor() {
        helper.saveAcquisition(
            sample = SensorSample(), note = "manual note", automatic = false,
            sessionId = 23L, sessionSequence = 1, sensorDeviceId = "SENSOR-A",
            timestamp = 1_000L
        )
        assertTrue(helper.manualSessionBelongsToSensor(23L, "SENSOR-A"))
        assertFalse(helper.manualSessionBelongsToSensor(23L, "SENSOR-B"))
        assertFalse(helper.manualSessionBelongsToSensor(23L, "SENSOR-A", 999L))
        assertTrue(helper.manualSessionBelongsToSensor(23L, "SENSOR-A", 1_001L))
        assertEquals("manual note", helper.manualSessionNote(23L))
        assertEquals(2, helper.nextSequenceForSession(23L))
        assertTrue(helper.saveRecoveredAcquisition(
            timestamp = 2_000L, sample = SensorSample(), note = helper.manualSessionNote(23L),
            sessionId = 23L, sequence = helper.nextSequenceForSession(23L),
            sensorDeviceId = "SENSOR-A", sensorRecordId = 9L,
            externalCommand = true, manualSession = true
        ))
        assertEquals(listOf(0 to 0, 1 to 0), recordFlags(23L))
        assertFalse(helper.acquisitionSessionUsesExternalCommand(23L))
        assertEquals(2, helper.acquisitionCountForSession(23L))
    }

    private fun recordFlags(sessionId: Long): List<Pair<Int, Int>> =
        helper.readableDatabase.rawQuery(
            "SELECT external_command, automatic FROM acquisitions WHERE session_id = ? ORDER BY session_sequence",
            arrayOf(sessionId.toString())
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(cursor.getInt(0) to cursor.getInt(1))
            }
        }
}
