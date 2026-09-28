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

    @Test fun simulationsUseSelectedOfflineSensorAndLeaveOtherSensorsRunningSessionsUnchanged() {
        val acquisitionJob = database.nextSessionId()
        val alertJob = database.nextSessionId()
        database.startAcquisitionSession(acquisitionJob, "Running B", 1_000L, "B")
        database.startAlertSession(alertJob, "Running B", 1_000L, "B")
        val result = database.insertDebugSimulationEvents("A", "Simulation", now = 1_700_000_000_000L)
        assertEquals(15, result.acquisitions)
        assertEquals(6, result.alerts)
        assertEquals(15, count("acquisitions", "A"))
        assertEquals(6, count("alerts", "A"))
        assertEquals(0, count("acquisitions", "B"))
        assertEquals(0, count("alerts", "B"))
        for ((table, id) in listOf("acquisition_sessions" to acquisitionJob, "alert_sessions" to alertJob)) {
            database.readableDatabase.rawQuery("SELECT note, ended_at FROM $table WHERE session_id=?",
                arrayOf(id.toString())).use {
                assertTrue(it.moveToFirst())
                assertEquals("Running B", it.getString(0))
                assertTrue(it.isNull(1))
            }
        }
    }

    @Test fun noSelectionNeverFallsBackToMostRecentlySeenSensor() {
        try {
            database.insertDebugSimulationEvents("", "Simulation")
            fail("A selected sensor is required")
        } catch (_: IllegalArgumentException) { }
        for (table in listOf("acquisitions", "alerts", "acquisition_sessions", "alert_sessions")) {
            assertEquals(0, count(table, "A"))
            assertEquals(0, count(table, "B"))
        }
    }
}
