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
