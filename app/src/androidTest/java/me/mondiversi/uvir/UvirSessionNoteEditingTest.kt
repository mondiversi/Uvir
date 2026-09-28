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

/** Private databases and preferences only; never creates records in the user's app. */
open class SessionNoteEditingFixture {
    protected lateinit var context: Context
    protected lateinit var database: UvirDatabaseHelper
    private lateinit var directory: File
    private val namespace = "note-edit-test-${UUID.randomUUID()}"
    private val preferenceNames = mutableSetOf<String>()
    protected val a = "NOTE-TEST-A"
    protected val b = "NOTE-TEST-B"

    @Before fun prepareNoteFixture() {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        directory = File(base.cacheDir, namespace).also { assertTrue(it.mkdir()) }
        context = object : ContextWrapper(base) {
            override fun getApplicationContext(): Context = this
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
                val isolated = "$namespace-$name"
                preferenceNames.add(isolated)
                return super.getSharedPreferences(isolated, mode)
            }
            override fun getDatabasePath(name: String): File = File(directory, name)
            override fun openOrCreateDatabase(name: String, mode: Int,
                factory: SQLiteDatabase.CursorFactory?) = SQLiteDatabase.openOrCreateDatabase(getDatabasePath(name), factory)
            override fun openOrCreateDatabase(name: String, mode: Int,
                factory: SQLiteDatabase.CursorFactory?, handler: DatabaseErrorHandler?) =
                SQLiteDatabase.openOrCreateDatabase(getDatabasePath(name).absolutePath, factory, handler)
        }
        database = UvirDatabaseHelper(context)
        assertTrue(database.writableDatabase.path.startsWith(directory.absolutePath + File.separator))
        assertEquals(20, database.writableDatabase.version)
        assertTrue(UvirSensorCredentialStore.save(context, UvirSensorCredentials(deviceId = a, authToken = "test-secret-a")))
        assertTrue(UvirSensorCredentialStore.restoreAssociatedSensor(context, UvirSensorCredentials(deviceId = b, authToken = "test-secret-b")))
    }

    @After fun disposeNoteFixture() {
        database.close()
        assertTrue(directory.deleteRecursively())
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        preferenceNames.forEach { base.deleteSharedPreferences(it) }
    }

    protected fun acquisition(session: Long? = 12L, automatic: Boolean = true,
        uid: String = a, external: Boolean = false): Long = database.saveAcquisition(
        sample = SensorSample(uva = 2.0), note = "original", automatic = automatic,
        sessionId = session, sessionSequence = session?.let { database.nextSequenceForSession(it) },
        sensorDeviceId = uid, externalCommand = external, timestamp = 1_000L)

    protected fun alert(session: Long = 13L, uid: String = a): ThresholdAlertLogEntry {
        assertTrue(database.insertRecoveredThresholdAlert(1_000L, "UVA|2.0|ABOVE|1.0", uid,
            sensorRecordId = session, sessionId = session))
        return database.readThresholdAlertLog().first { it.sessionId == session }
    }
}

class UvirSessionNoteEditingTest : SessionNoteEditingFixture() {
    @Test fun automaticSessionAndItsChildStayLockedUntilCompletion() {
        database.startAcquisitionSession(12L, "original", 900L, a)
        val record = acquisition()
        assertFalse(database.canEditAcquisitionSessionNote(12L))
        assertFalse(database.updateAcquisitionNote(record, ""))
        assertFalse(database.updateAcquisitionSessionNote(12L, "changed"))
        assertEquals("original", database.readRecord(record)!!.note)
        database.finishAcquisitionSession(12L, 2_000L)
        assertTrue(database.canEditAcquisitionSessionNote(12L))
        assertTrue(database.updateAcquisitionNote(record, "changed"))
        assertEquals("changed", database.readAcquisitionSessionNote(12L))
    }

    @Test fun externalSessionDoesNotUnlockWhenAnotherSensorOrNoSensorIsSelected() {
        database.startAcquisitionSession(12L, "original", 900L, a, externalCommand = true)
        val record = acquisition(external = true)
        assertTrue(UvirSensorCredentialStore.activateAssociatedSensor(context, b))
        assertFalse(database.updateAcquisitionNote(record, "changed"))
        assertTrue(UvirSensorCredentialStore.deactivateSensorSelection(context))
        assertFalse(database.canEditAcquisitionSessionNote(12L))
        assertFalse(database.updateAcquisitionSessionNote(12L, "changed"))
        database.finishAcquisitionSession(12L)
        assertTrue(database.updateAcquisitionSessionNote(12L, "changed"))
    }

    @Test fun alertSessionAndItsChildStayLockedOfflineUntilCompletion() {
        database.startAlertSession(13L, "original", 900L, a)
        val entry = alert()
        assertTrue(UvirSensorCredentialStore.deactivateSensorSelection(context))
        assertFalse(database.canEditAlertSessionNote(13L))
        assertFalse(database.updateAlertNote(entry.id, "changed"))
        assertFalse(database.updateAlertSessionNote(13L, ""))
        database.finishAlertSession(13L)
        assertTrue(database.canEditAlertSessionNote(13L))
        assertTrue(database.updateAlertNote(entry.id, "changed"))
        assertEquals("changed", database.readAlertSessionNote(13L))
    }

    @Test fun aDifferentOpenSessionDoesNotLockFinishedRecordsOrStandaloneAcquisitions() {
        database.startAcquisitionSession(12L, "original", 900L, a)
        val record = acquisition()
        database.finishAcquisitionSession(12L)
        database.startAcquisitionSession(22L, "other", 900L, b)
        database.startAlertSession(23L, "other", 900L, b)
        assertTrue(database.updateAcquisitionNote(record, "finished"))
        val standalone = acquisition(session = null, automatic = false)
        assertTrue(database.canEditAcquisitionSessionNote(null))
        assertTrue(database.updateAcquisitionNote(standalone, "independent"))
        assertEquals("independent", database.readRecord(standalone)!!.note)
    }

    @Test fun rememberedManualSessionLocksSessionAndExternalChildWithoutMetadataRow() {
        val record = acquisition(automatic = false, external = true)
        val prefs = context.getSharedPreferences(PREFS_NAME, 0)
        prefs.edit().putLong(KEY_LAST_MANUAL_SESSION_ID, 12L).commit()
        assertFalse(database.canEditAcquisitionSessionNote(12L))
        assertFalse(database.updateAcquisitionNote(record, "changed"))
        assertFalse(database.updateAcquisitionSessionNote(12L, "changed"))
        prefs.edit().remove(KEY_LAST_MANUAL_SESSION_ID).commit()
        assertTrue(database.updateAcquisitionNote(record, "changed"))
    }

    @Test fun backgroundManualSessionUsesItsOwnSensorContextNotSelectedGlobals() {
        val record = acquisition(automatic = false)
        assertTrue(UvirSensorCredentialStore.activateAssociatedSensor(context, b))
        val prefs = context.getSharedPreferences(PREFS_NAME, 0)
        prefs.edit().putLong(sensorContextPreferenceKey(a, KEY_LAST_MANUAL_SESSION_ID), 12L)
            .putLong(KEY_LAST_MANUAL_SESSION_ID, 22L).commit()
        assertFalse(database.updateAcquisitionNote(record, "changed"))
        prefs.edit().remove(sensorContextPreferenceKey(a, KEY_LAST_MANUAL_SESSION_ID)).commit()
        assertTrue(database.updateAcquisitionNote(record, "changed"))
    }

    @Test fun inactiveManualVariantsMetadataDoesNotImplyAnOngoingAutomaticJob() {
        acquisition(automatic = false)
        acquisition(automatic = false)
        database.ensureAcquisitionSession(12L, "original", 900L, a)
        assertTrue(database.updateAcquisitionSessionVariantsPerPosition(12L, 2))
        assertTrue(database.canEditAcquisitionSessionNote(12L))
        assertTrue(database.updateAcquisitionSessionNote(12L, "changed"))
        assertTrue(database.readSessionRecords(12L).all { it.note == "changed" })
    }

    @Test fun clearedFinishedNoteCannotBeRestoredByRecoveredRecordsOrLegacyChildNotes() {
        database.startAcquisitionSession(12L, "original", 900L, a)
        val first = acquisition()
        database.finishAcquisitionSession(12L, 2_000L)
        assertTrue(database.updateAcquisitionSessionNote(12L, ""))
        database.writableDatabase.execSQL("UPDATE acquisitions SET note = 'legacy' WHERE id = ?", arrayOf(first))
        assertEquals("", database.readRecord(first)!!.note)
        assertTrue(database.saveRecoveredAcquisition(3_000L, SensorSample(uva = 5.0), "original",
            12L, 2, a, 102L))
        database.ensureAcquisitionSession(12L, "original", 900L, a)
        assertEquals("", database.readAcquisitionSessionNote(12L))
        assertTrue(database.readSessionRecords(12L).all { it.note.isEmpty() })
        assertTrue(database.readSavedRecords().all { it.note.isEmpty() })
        assertTrue(database.canEditAcquisitionSessionNote(12L))
    }

    @Test fun recoveryCanFillAnInitialUnknownNoteOnlyWhileSessionIsOpen() {
        database.ensureAcquisitionSession(12L, "", 900L, a)
        assertFalse(database.canEditAcquisitionSessionNote(12L))
        database.ensureAcquisitionSession(12L, "original", 900L, a)
        assertEquals("original", database.readAcquisitionSessionNote(12L))
        database.finishAcquisitionSession(12L)
        val record = acquisition()
        assertTrue(database.updateAcquisitionNote(record, "new"))
        database.ensureAcquisitionSession(12L, "old", 900L, a)
        assertEquals("new", database.readRecord(record)!!.note)
    }

    @Test fun aSessionStartingAfterEditorOpenedIsCheckedAgainAtSaveTime() {
        val record = acquisition(automatic = false)
        assertTrue(database.canEditAcquisitionSessionNote(12L))
        context.getSharedPreferences(PREFS_NAME, 0).edit().putLong(KEY_LAST_MANUAL_SESSION_ID, 12L).commit()
        assertFalse(database.updateAcquisitionNote(record, "stale dialog"))
        assertEquals("original", database.readRecord(record)!!.note)
        database.startAlertSession(13L, "original", 900L, a)
        alert()
        database.finishAlertSession(13L)
        assertTrue(database.canEditAlertSessionNote(13L))
        database.startAlertSession(13L, "original", 900L, a)
        assertFalse(database.updateAlertSessionNote(13L, "stale dialog"))
    }
}
