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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Real SQLite coverage for keeping per-session variant metadata coherent. */
class UvirDatabaseVariantDeletionTest {
    private lateinit var directory: File
    private lateinit var database: UvirDatabaseHelper
    private lateinit var preferencesName: String
    private lateinit var baseContext: Context

    @Before
    fun preparePrivateDatabase() {
        baseContext = InstrumentationRegistry.getInstrumentation().targetContext
        directory = File(baseContext.cacheDir, "variant-deletion-test-" + UUID.randomUUID())
        assertTrue(directory.mkdir())
        preferencesName = "variant-deletion-prefs-" + UUID.randomUUID()
        val isolatedContext = object : ContextWrapper(baseContext) {
            override fun getApplicationContext(): Context = this
            override fun getDatabasePath(name: String) = File(directory, name)
            override fun getSharedPreferences(name: String, mode: Int) =
                baseContext.getSharedPreferences(preferencesName, mode)
            override fun openOrCreateDatabase(
                name: String,
                mode: Int,
                factory: SQLiteDatabase.CursorFactory?
            ): SQLiteDatabase = SQLiteDatabase.openOrCreateDatabase(getDatabasePath(name), factory)
            override fun openOrCreateDatabase(
                name: String,
                mode: Int,
                factory: SQLiteDatabase.CursorFactory?,
                errorHandler: DatabaseErrorHandler?
            ): SQLiteDatabase = SQLiteDatabase.openOrCreateDatabase(
                getDatabasePath(name).absolutePath,
                factory,
                errorHandler
            )
        }
        database = UvirDatabaseHelper(isolatedContext)
    }

    @After
    fun closePrivateDatabase() {
        database.close()
        baseContext.getSharedPreferences(preferencesName, Context.MODE_PRIVATE).edit().clear().commit()
        directory.listFiles().orEmpty().forEach { assertTrue(it.delete()) }
        assertTrue(directory.delete())
    }

    @Test
    fun deletingOneRecordDisablesVariantsAndClearsRemainingIndexes() {
        val records = createVariantSession(sessionId = 10L)

        assertEquals(1, database.deleteRecord(records.first().id))
        assertEquals(1, database.readAcquisitionSessionVariantsPerPosition(10L))
        val remaining = database.readSessionRecords(10L)
        assertEquals(3, remaining.size)
        remaining.forEach { record ->
            assertNull(record.positionIndex)
            assertNull(record.variantIndex)
        }
        assertEquals(1, sessionRowCount(10L))
    }

    @Test
    fun deletingEveryRecordAlsoDeletesTheSessionMetadataRow() {
        val records = createVariantSession(sessionId = 11L)

        assertEquals(4, database.deleteRecords(records.map { it.id }))
        assertTrue(database.readSessionRecords(11L).isEmpty())
        assertEquals(0, sessionRowCount(11L))
    }

    @Test
    fun resettingAcquisitionCountersDeletesVariantSessionsAndRestartsSessionIds() {
        createVariantSession(sessionId = 1L)

        assertEquals(4, database.resetAcquisitionCounters())
        assertTrue(database.readSavedRecords().isEmpty())
        assertEquals(0, sessionRowCount(1L))
        assertEquals(1L, database.nextSessionId())
    }

    private fun createVariantSession(sessionId: Long): List<SavedRecordSummary> {
        database.startAcquisitionSession(
            sessionId = sessionId,
            note = "Variants",
            startedAt = 1_000L,
            sensorDeviceId = "TEST-SENSOR"
        )
        repeat(4) { index ->
            database.saveAcquisition(
                sample = SensorSample(),
                note = "",
                automatic = true,
                sessionId = sessionId,
                sessionSequence = index + 1,
                sensorDeviceId = "TEST-SENSOR",
                timestamp = 1_000L + index
            )
        }
        assertTrue(database.updateAcquisitionSessionVariantsPerPosition(sessionId, 2))
        assertEquals(2, database.readAcquisitionSessionVariantsPerPosition(sessionId))
        return database.readSavedRecords().filter { it.sessionId == sessionId }
    }

    private fun sessionRowCount(sessionId: Long): Int = database.readableDatabase.rawQuery(
        "SELECT COUNT(*) FROM acquisition_sessions WHERE session_id = ?",
        arrayOf(sessionId.toString())
    ).use { cursor ->
        if (cursor.moveToFirst()) cursor.getInt(0) else 0
    }
}
