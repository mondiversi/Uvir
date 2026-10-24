package me.mondiversi.uvir

import android.content.Context
import android.content.ContextWrapper
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import kotlinx.coroutines.runBlocking
import android.net.Uri
import java.io.File
import java.util.UUID

class UvirRecordArchiveTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var root: File
    private val helpers = mutableListOf<UvirDatabaseHelper>()
    private lateinit var source: UvirDatabaseHelper
    private lateinit var target: UvirDatabaseHelper
    private val kind = UvirArchiveKind.ACQUISITIONS

    @Before fun setup() {
        root = File(context.cacheDir, "record-archive-${UUID.randomUUID()}").apply { check(mkdir()) }
        source = database("source"); target = database("target")
    }
    @After fun cleanup() { helpers.forEach { it.close() }; root.deleteRecursively() }

    @Test fun differentPhonesWithTheSameLocalSensorIdKeepTheCorrectHardwareAndChronology() {
        source.renameSensor("ESP-SOURCE", "Private source alias")
        source.saveAcquisition(SensorSample(uvc = .012345678901234), "old source", sensorDeviceId = "ESP-SOURCE", timestamp = 1000)
        target.renameSensor("ESP-TARGET", "My sensor")
        target.saveAcquisition(SensorSample(), "recent target", sensorDeviceId = "ESP-TARGET", timestamp = 9000)
        assertEquals(source.findSensorProfile("ESP-SOURCE")!!.id, target.findSensorProfile("ESP-TARGET")!!.id)
        val result = importUvirRecordArchives(target, listOf(roundTrip()))
        assertEquals(UvirRecordImportResult(1, 0), result)
        val records = target.readSavedRecords()
        assertEquals(listOf("recent target", "old source"), records.map { it.note })
        val imported = target.readRecord(records.last().id)!!
        assertEquals(.012345678901234, imported.sample.uvc, 0.0)
        assertEquals("ESP-SOURCE", imported.sensorDisplayName)
        assertNotEquals(records.first().sensorId, imported.sensorId)
        assertEquals(0L, target.findSensorProfile(imported.sensorId!!)!!.lastSeenAt)
        assertFalse(UvirSensorCredentialStore.associatedDeviceIds(context).contains("ESP-SOURCE"))
    }

    @Test fun knownHardwareReusesItsLocalIdWithoutRenamingAndLaterPairingResolvesOldRecords() {
        source.saveAcquisition(SensorSample(), "same", sensorDeviceId = "esp-same", timestamp = 1000)
        target.ensureSensorProfile("OTHER")
        val known = target.renameSensor("ESP-SAME", "Local name")!!
        importUvirRecordArchives(target, listOf(roundTrip()))
        val recordId = target.readSavedRecords().single().id
        assertEquals(known.id, target.readRecord(recordId)!!.sensorId)
        assertEquals("Local name", target.readRecord(recordId)!!.sensorDisplayName)
        target.renameSensor("ESP-SAME", "After pairing")
        assertEquals("After pairing", target.readRecord(recordId)!!.sensorDisplayName)
        assertEquals(2, target.readSensorProfiles().size)
    }

    @Test fun repeatedImportAndReexportToAnotherPhoneKeepStableIdentities() {
        source.saveAcquisition(SensorSample(), "one", timestamp = 1000)
        val file = roundTrip()
        assertEquals(UvirRecordImportResult(1, 0), importUvirRecordArchives(target, listOf(file)))
        assertEquals(UvirRecordImportResult(0, 1), importUvirRecordArchives(target, listOf(file)))
        val third = database("third")
        val reexport = exportUvirRecordArchive(target, kind)
        assertEquals(file.records.single().getString("id"), reexport.records.single().getString("id"))
        importUvirRecordArchives(third, listOf(parse(reexport)))
        assertEquals(UvirRecordImportResult(0, 1), importUvirRecordArchives(source, listOf(parse(exportUvirRecordArchive(third, kind)))))
        assertEquals(1, source.readSavedRecords().size)
    }

    @Test fun sessionsGetNewIdsAndPreserveVariantsNotesAndOpticalProvenance() {
        val sourceSession = source.nextSessionId()
        source.startAcquisitionSession(sourceSession, "session note", 1000, "SOURCE")
        val sample = SensorSample(uvc = .123456789, violetto = 2.23456789, nir = 9.87654321,
            qualityFlags = 3, opticalWindow = OpticalWindowSettings(true, List(11) { 1.2f }))
        source.saveAcquisition(sample, "sample", true, sourceSession, 1, "SOURCE", timestamp = 2000)
        source.saveAcquisition(sample, "sample", true, sourceSession, 2, "SOURCE", timestamp = 3000)
        assertTrue(source.updateAcquisitionSessionVariantsPerPosition(sourceSession, 2))
        source.finishAcquisitionSession(sourceSession, 4000)
        val occupied = target.nextSessionId()
        target.startAcquisitionSession(occupied, "keep", 5000, "TARGET")
        importUvirRecordArchives(target, listOf(roundTrip()))
        val records = target.readSavedRecords().map { target.readRecord(it.id)!! }
        val session = records.first().sessionId!!
        assertNotEquals(sourceSession, session)
        assertEquals("keep", target.readAcquisitionSessionNote(occupied))
        assertEquals("session note", target.readAcquisitionSessionNote(session))
        assertEquals(2, target.readAcquisitionSessionVariantsPerPosition(session))
        assertEquals(listOf(2, 1), records.map { it.variantIndex })
        assertEquals(sample, records.first().sample)
        assertTrue(target.nextSessionId() > session)
    }

    @Test fun alertsPreserveSelectiveThresholdsAndSessionMetadata() {
        val session = source.nextSessionId()
        source.startAlertSession(session, "alert note", 1000, "ALERT-SOURCE")
        val violations = listOf(ThresholdAlertViolation(ThresholdAlertRule(ThresholdAlertMetric.UVB, true,
            ThresholdAlertDirection.ABOVE, .125f), .234567891))
        source.insertThresholdAlertLog(violations, 2000, session, "ALERT-SOURCE", 3)
        val archive = parse(exportUvirRecordArchive(source, UvirArchiveKind.ALERTS))
        importUvirRecordArchives(target, listOf(archive))
        val entry = target.readThresholdAlertLog().single()
        assertEquals(violations, parseThresholdAlertLogDetails(entry.details))
        assertEquals("alert note", entry.note)
        assertEquals("ALERT-SOURCE", entry.sensorDisplayName)
        assertEquals(3, entry.qualityFlags)
        assertTrue(target.canEditAlertSessionNote(entry.sessionId))
        assertEquals(UvirRecordImportResult(0, 1), importUvirRecordArchives(target, listOf(archive)))
    }

    @Test fun syntheticSourceRemainsUnassociatedAndImportedSnapshotsAreClosed() {
        val session = source.nextSessionId()
        source.startAcquisitionSession(session, "synthetic", 1000)
        source.saveAcquisition(SensorSample(), "sample", true, session, 1, timestamp = 2000)
        importUvirRecordArchives(target, listOf(roundTrip()))
        val record = target.readRecord(target.readSavedRecords().single().id)!!
        assertNull(record.sensorId)
        assertEquals("—", record.sensorDisplayName)
        assertTrue(target.readSensorProfiles().isEmpty())
        assertTrue(target.canEditAcquisitionSessionNote(record.sessionId))
    }

    @Test fun nativeSensorRecordsAlreadySyncedOnTheReceivingPhoneAreNotDuplicated() {
        source.saveRecoveredAcquisition(1000, SensorSample(uvb = .25), "note", 0, 0, "ESP", 42)
        target.saveRecoveredAcquisition(1000, SensorSample(uvb = .25), "local", 0, 0, "ESP", 42)
        assertEquals(UvirRecordImportResult(0, 1), importUvirRecordArchives(target, listOf(roundTrip())))
        assertEquals(1, target.readSavedRecords().size)
    }

    @Test fun aLaterConflictingFileRollsBackAllEarlierFilesAndNewSensorRows() {
        source.saveAcquisition(SensorSample(), "source", sensorDeviceId = "SOURCE", timestamp = 1000)
        val first = roundTrip()
        val bad = roundTrip()
        bad.records.single().put("timestamp", 9999)
        val failed = runCatching { importUvirRecordArchives(target, listOf(first, bad)) }
        assertTrue(failed.isFailure)
        assertTrue(target.readSavedRecords().isEmpty())
        assertTrue(target.readSensorProfiles().isEmpty())
        assertEquals(0L, target.currentSessionCounter())
    }

    @Test fun malformedFilesAndOtherUnitsAreRejectedBeforeImport() {
        source.saveAcquisition(SensorSample(), "source", timestamp = 1000)
        val original = exportUvirRecordArchive(source, kind).toJson()
        listOf("unit" to "uW/cm2", "version" to 999, "kind" to "alerts").forEach { (key, value) ->
            val changed = JSONObject(original.toString()).put(key, value)
            assertTrue(runCatching { parseUvirRecordArchive(changed.toString(), kind) }.isFailure)
        }
        val broken = JSONObject(original.toString())
        broken.getJSONArray("records").getJSONObject(0).put("sensor", "missing-hardware")
        assertTrue(runCatching { parseUvirRecordArchive(broken.toString(), kind) }.isFailure)
        assertTrue(target.readSavedRecords().isEmpty())
    }

    @Test fun resetClearsTransferIdsEvenWhenLocalIdsAreReused() {
        source.saveAcquisition(SensorSample(), "source", timestamp = 1000)
        val archive = roundTrip()
        importUvirRecordArchives(target, listOf(archive))
        target.resetAllCounters()
        target.saveAcquisition(SensorSample(), "other", timestamp = 2000)
        assertEquals(UvirRecordImportResult(1, 0), importUvirRecordArchives(target, listOf(archive)))
        assertEquals(2, target.readSavedRecords().size)
    }

    @Test fun encryptedDatabaseBackupPreservesTransferIdentities() = runBlocking {
        source.saveAcquisition(SensorSample(), "source", timestamp = 1000)
        source.writableDatabase.rawQuery("PRAGMA wal_checkpoint(FULL)", null).close()
        val backup = createEncryptedUvirDatabaseArchive(File(source.writableDatabase.path),
            File(root, "backup.zip"), "test-password".toCharArray())
        val records = roundTrip() // The backup deliberately predates the first portable export.
        importEncryptedUvirDatabaseArchive(context, target, Uri.fromFile(backup), "test-password".toCharArray())
        assertEquals(UvirRecordImportResult(0, 1), importUvirRecordArchives(target, listOf(records)))
        assertEquals(1, target.readSavedRecords().size)
    }

    @Test fun laterSnapshotExtendsTheSameImportedSessionWithoutReopeningIt() {
        val session = source.nextSessionId()
        source.startAcquisitionSession(session, "snapshot", 1000, "ESP")
        source.saveAcquisition(SensorSample(), "one", true, session, 1, "ESP", timestamp = 2000)
        importUvirRecordArchives(target, listOf(roundTrip()))
        source.saveAcquisition(SensorSample(), "two", true, session, 2, "ESP", timestamp = 3000)
        assertEquals(UvirRecordImportResult(1, 1), importUvirRecordArchives(target, listOf(roundTrip())))
        val importedSession = target.readSavedRecords().first().sessionId!!
        assertEquals(1, target.readSavedRecords().map { it.sessionId }.distinct().size)
        target.readableDatabase.rawQuery("SELECT ended_at FROM acquisition_sessions WHERE session_id = ?",
            arrayOf(importedSession.toString())).use { assertTrue(it.moveToFirst()); assertEquals(3000L, it.getLong(0)) }
    }

    @Test fun upgradeFromPreviousSchemaPreservesAllStoredMeasurements() {
        val id = source.saveAcquisition(SensorSample(uva = .123456789), "before", timestamp = 1000)
        source.writableDatabase.execSQL("DROP TABLE record_transfer_ids")
        source.writableDatabase.version = 25
        source.close()
        source = database("source", existing = true)
        assertEquals(26, source.readableDatabase.version)
        assertEquals(.123456789, source.readRecord(id)!!.sample.uva, 0.0)
        assertEquals("before", source.readRecord(id)!!.note)
        assertEquals(1, roundTrip().records.size)
    }

    private fun roundTrip() = parse(exportUvirRecordArchive(source, kind))
    private fun parse(archive: UvirRecordArchive) = parseUvirRecordArchive(archive.toJson().toString(), archive.kind)
    private fun database(name: String, existing: Boolean = false): UvirDatabaseHelper {
        val directory = File(root, name).apply { if (!existing) check(mkdir()) }
        val isolated = object : ContextWrapper(context) {
            override fun getApplicationContext(): Context = this
            override fun getDatabasePath(name: String) = File(directory, name)
            override fun openOrCreateDatabase(name: String, mode: Int, factory: SQLiteDatabase.CursorFactory?) =
                SQLiteDatabase.openOrCreateDatabase(getDatabasePath(name), factory)
            override fun openOrCreateDatabase(name: String, mode: Int, factory: SQLiteDatabase.CursorFactory?, errorHandler: DatabaseErrorHandler?) =
                SQLiteDatabase.openOrCreateDatabase(getDatabasePath(name).absolutePath, factory, errorHandler)
        }
        return UvirDatabaseHelper(isolated).also { helpers += it }
    }
}
