package me.mondiversi.uvir

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.UUID
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class UvirDebugSimulationTargetTest {
    private lateinit var database: UvirDatabaseHelper
    private lateinit var directory: File
    private lateinit var isolatedContext: Context
    private val preferences = mutableSetOf<String>()
    private val namespace = "simulation-target-${UUID.randomUUID()}"

    @Before fun setUp() {
        val original = InstrumentationRegistry.getInstrumentation().targetContext
        directory = File(original.cacheDir, namespace).also { assertTrue(it.mkdir()) }
        val isolated = object : ContextWrapper(original) {
            override fun getApplicationContext(): Context = this
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
                val uniqueName = "$namespace-$name"
                preferences.add(uniqueName)
                return super.getSharedPreferences(uniqueName, mode)
            }
            override fun getDatabasePath(name: String) = File(directory, name)
            override fun openOrCreateDatabase(name: String, mode: Int, factory: SQLiteDatabase.CursorFactory?) =
                SQLiteDatabase.openOrCreateDatabase(getDatabasePath(name), factory)
            override fun openOrCreateDatabase(name: String, mode: Int, factory: SQLiteDatabase.CursorFactory?,
                handler: DatabaseErrorHandler?) =
                SQLiteDatabase.openOrCreateDatabase(getDatabasePath(name).absolutePath, factory, handler)
        }
        isolatedContext = isolated
        database = UvirDatabaseHelper(isolated).also { it.writableDatabase }
        database.ensureSensorProfile("A", 10L)
        database.ensureSensorProfile("B", 20L)
    }

    @After fun tearDown() {
        database.close()
        directory.deleteRecursively()
        val original = InstrumentationRegistry.getInstrumentation().targetContext
        preferences.forEach { original.deleteSharedPreferences(it) }
    }

    private fun count(table: String, uid: String): Int = database.readableDatabase.rawQuery(
        "SELECT COUNT(*) FROM $table t JOIN sensors s ON t.sensor_id=s.id WHERE s.hardware_uid=?",
        arrayOf(uid)
    ).use { it.moveToFirst(); it.getInt(0) }

    @Test fun simulationsNeverUseRealSensorsAndLeaveTheirRunningSessionsUnchanged() {
        val acquisitionJob = database.nextSessionId()
        val alertJob = database.nextSessionId()
        database.startAcquisitionSession(acquisitionJob, "Running B", 1_000L, "B")
        database.startAlertSession(alertJob, "Running B", 1_000L, "B")
        val result = database.insertDebugSimulationEvents("Simulation", now = 1_700_000_000_000L)
        assertEquals(15, result.acquisitions)
        assertEquals(6, result.alerts)
        assertEquals(0, count("acquisitions", "A"))
        assertEquals(0, count("alerts", "A"))
        assertEquals(0, count("acquisitions", "B"))
        assertEquals(0, count("alerts", "B"))
        assertUnattributedMockHistory()
        for ((table, id) in listOf("acquisition_sessions" to acquisitionJob, "alert_sessions" to alertJob)) {
            database.readableDatabase.rawQuery("SELECT note, ended_at FROM $table WHERE session_id=?",
                arrayOf(id.toString())).use {
                assertTrue(it.moveToFirst())
                assertEquals("Running B", it.getString(0))
                assertTrue(it.isNull(1))
            }
        }
    }

    @Test fun eventsWorkWithoutAnyAssociatedSensorAndDoNotCreateAProfile() {
        database.writableDatabase.delete("sensors", null, null)
        assertTrue(database.readSensorProfiles().isEmpty())
        val result = database.insertDebugSimulationEvents("Simulation")
        assertEquals(15, result.acquisitions)
        assertEquals(6, result.alerts)
        assertUnattributedMockHistory()
        assertTrue(database.readSensorProfiles().isEmpty())
    }

    @Test fun mockEventsHaveNoSensorAndStayUnattributedAfterPairingAnotherSensor() {
        val result = database.insertDebugSimulationEvents("Simulation", now = 1_700_000_000_000L)
        assertEquals(15, result.acquisitions)
        assertEquals(6, result.alerts)
        assertUnattributedMockHistory()
        database.ensureSensorProfile("C", 1_800_000_000_000L)
        assertUnattributedMockHistory()
    }

    @Test fun mockAndOutOfRangeChoicesNeverChangeEventAttributionOrSelectedHardware() {
        val prefs = isolatedContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        var batches = 0
        for (uid in listOf("", "A", "B")) for (mock in listOf(false, true)) {
            prefs.edit().putString("active_device_id", uid).commit()
            UvirSensorSimulationSettingsStore.setEnabled(prefs, uid, mock)
            UvirSensorSimulationSettingsStore.setOutOfRange(prefs, uid, !mock)
            database.insertDebugSimulationEvents("Simulation", now = 1_700_000_000_000L + batches * 1_000L)
            batches++
            assertUnattributedMockHistory(batches)
            assertEquals(uid, prefs.getString("active_device_id", ""))
            assertEquals(mock, UvirSensorSimulationSettingsStore.load(prefs).enabled)
            assertEquals(!mock, UvirSensorSimulationSettingsStore.load(prefs).outOfRange)
        }
    }

    private fun assertUnattributedMockHistory(batches: Int = 1) {
        for ((table, expected) in mapOf("acquisitions" to 15, "alerts" to 6,
            "acquisition_sessions" to 3, "alert_sessions" to 2)) {
            database.readableDatabase.rawQuery("SELECT COUNT(*) FROM $table WHERE sensor_id IS NULL", null).use {
                assertTrue(it.moveToFirst())
                assertEquals(expected * batches, it.getInt(0))
            }
        }
        for (uid in listOf("A", "B", "C")) for (table in listOf("acquisitions", "alerts")) {
            assertEquals(0, count(table, uid))
        }
        database.readThresholdAlertLog().forEach {
            assertNull(it.sensorId)
            assertEquals("—", it.sensorDisplayName)
        }
        database.readSavedRecords().forEach {
            val detail = requireNotNull(database.readRecord(it.id))
            assertNull(detail.sensorId)
            assertEquals("—", detail.sensorDisplayName)
        }
    }
}
