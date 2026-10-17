package me.mondiversi.uvir

import android.content.Context
import android.content.ContextWrapper
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class UvirDatabaseImportTest {
    private lateinit var context: Context
    private lateinit var root: File
    private lateinit var source: UvirDatabaseHelper
    private lateinit var target: UvirDatabaseHelper

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        root = File(context.cacheDir, "database-import-test-${UUID.randomUUID()}")
        assertTrue(root.mkdir())
        source = UvirDatabaseHelper(isolatedContext("source"))
        target = UvirDatabaseHelper(isolatedContext("target"))
    }

    @After
    fun tearDown() {
        source.close()
        target.close()
        root.deleteRecursively()
    }

    @Test
    fun encryptedArchiveRestoresRecordsAndWrongPasswordKeepsExistingData() = runBlocking {
        source.saveAcquisition(
            sample = SensorSample(),
            note = "restored record",
            automatic = false,
            sensorDeviceId = "SOURCE-SENSOR",
            timestamp = 1_000L
        )
        target.saveAcquisition(
            sample = SensorSample(),
            note = "old record",
            automatic = false,
            sensorDeviceId = "TARGET-SENSOR",
            timestamp = 2_000L
        )
        source.writableDatabase.rawQuery("PRAGMA wal_checkpoint(FULL)", null).close()
        val archive = createEncryptedUvirDatabaseArchive(
            File(source.writableDatabase.path),
            File(root, "backup.zip"),
            "correct-password".toCharArray()
        )

        val failed = runCatching {
            importEncryptedUvirDatabaseArchive(
                context, target, Uri.fromFile(archive), "wrong-password".toCharArray()
            )
        }
        assertTrue(failed.isFailure)
        assertEquals("old record", target.readSavedRecords().single().note)

        importEncryptedUvirDatabaseArchive(
            context, target, Uri.fromFile(archive), "correct-password".toCharArray()
        )
        assertEquals("restored record", target.readSavedRecords().single().note)
    }

    @Test
    fun incompatibleDatabaseVersionDoesNotReplaceExistingRecords() = runBlocking {
        target.saveAcquisition(
            sample = SensorSample(),
            note = "keep this record",
            automatic = false,
            sensorDeviceId = "TARGET-SENSOR",
            timestamp = 3_000L
        )
        source.writableDatabase.version = target.writableDatabase.version + 1
        val archive = createEncryptedUvirDatabaseArchive(
            File(source.writableDatabase.path),
            File(root, "newer-backup.zip"),
            "correct-password".toCharArray()
        )

        val failed = runCatching {
            importEncryptedUvirDatabaseArchive(
                context, target, Uri.fromFile(archive), "correct-password".toCharArray()
            )
        }
        assertTrue(failed.isFailure)
        assertEquals("keep this record", target.readSavedRecords().single().note)
    }

    @Test fun retiredCameraArchiveAndCounterRoundTripWithoutMixingSpectralRecords() = runBlocking {
        assertEquals(1L, insertLegacyCameraRow(source, "camera:source"))
        insertLegacyCameraRow(target, "camera:target")
        val archive = createEncryptedUvirDatabaseArchive(File(source.writableDatabase.path), File(root, "camera-backup.zip"), "correct-password".toCharArray())
        importEncryptedUvirDatabaseArchive(context, target, Uri.fromFile(archive), "correct-password".toCharArray())
        assertEquals(legacyCameraRows(source), legacyCameraRows(target))
        assertEquals(2L, insertLegacyCameraRow(target, "camera:source"))
        assertTrue(target.readSavedRecords().isEmpty())
        target.resetAllCounters()
        assertTrue(legacyCameraRows(target).isEmpty())
        assertEquals(1L, insertLegacyCameraRow(target, "camera:source"))
    }

    @Test fun previousVersionTwentyBackupStillImportsWithoutCameraTable() = runBlocking {
        source.saveAcquisition(SensorSample(), "previous-version-record", false, sensorDeviceId = "fixture", timestamp = 1_000L)
        source.writableDatabase.execSQL("DROP TABLE camera_estimates")
        source.writableDatabase.version = 20
        val archive = createEncryptedUvirDatabaseArchive(File(source.writableDatabase.path), File(root, "version-twenty.zip"), "correct-password".toCharArray())
        importEncryptedUvirDatabaseArchive(context, target, Uri.fromFile(archive), "correct-password".toCharArray())
        assertEquals("previous-version-record", target.readSavedRecords().single().note)
        assertTrue(legacyCameraRows(target).isEmpty())
        assertEquals(20, source.writableDatabase.version)
    }

    // Opaque legacy data: no runtime Camera model or recording API is retained.
    private fun insertLegacyCameraRow(helper: UvirDatabaseHelper, sourceId: String): Long =
        helper.writableDatabase.insertOrThrow("camera_estimates", null, android.content.ContentValues().apply {
            put("source_id", sourceId)
            put("timestamp", 1_800_000_000_000L)
            put("data_origin", "estimated")
            put("model_version", "retired-prototype")
            put("estimate_csv", "Source,Data_Origin\nCamera,Estimated\n")
        })

    private fun legacyCameraRows(helper: UvirDatabaseHelper): List<List<String>> =
        helper.readableDatabase.rawQuery("SELECT * FROM camera_estimates ORDER BY id", null).use { cursor ->
            buildList { while (cursor.moveToNext()) add((0 until cursor.columnCount).map { cursor.getString(it) }) }
        }

    private fun isolatedContext(name: String): Context {
        val directory = File(root, name).apply { assertTrue(mkdir()) }
        return object : ContextWrapper(context) {
            override fun getApplicationContext(): Context = this
            override fun getDatabasePath(databaseName: String) = File(directory, databaseName)
            override fun openOrCreateDatabase(
                databaseName: String,
                mode: Int,
                factory: SQLiteDatabase.CursorFactory?
            ): SQLiteDatabase = SQLiteDatabase.openOrCreateDatabase(
                getDatabasePath(databaseName), factory
            )
            override fun openOrCreateDatabase(
                databaseName: String,
                mode: Int,
                factory: SQLiteDatabase.CursorFactory?,
                errorHandler: DatabaseErrorHandler?
            ): SQLiteDatabase = SQLiteDatabase.openOrCreateDatabase(
                getDatabasePath(databaseName).absolutePath, factory, errorHandler
            )
        }
    }
}
