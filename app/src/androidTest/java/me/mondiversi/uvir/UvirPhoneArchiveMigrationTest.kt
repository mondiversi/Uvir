package me.mondiversi.uvir

import android.content.ContentValues
import android.content.Context
import android.content.ContextWrapper
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/** Only synthetic private databases. No access to the user's archive or attached hardware. */
class UvirPhoneArchiveMigrationTest {
    private val base = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var root: File
    private val helpers = mutableListOf<UvirDatabaseHelper>()
    @Before fun setup() { root = File(base.cacheDir, "phone-schema-${UUID.randomUUID()}").apply { check(mkdir()) } }
    @After fun cleanup() { helpers.forEach { it.close() }; root.deleteRecursively() }

    private fun isolated(name: String): Context = object : ContextWrapper(base) {
        private val directory = File(root, name).apply { mkdirs() }
        override fun getApplicationContext(): Context = this
        override fun getNoBackupFilesDir(): File = File(directory, "private-backups").apply { mkdirs() }
        override fun getDatabasePath(name: String): File = File(directory, name)
        override fun openOrCreateDatabase(name: String, mode: Int, factory: SQLiteDatabase.CursorFactory?): SQLiteDatabase =
            SQLiteDatabase.openOrCreateDatabase(getDatabasePath(name), factory)
        override fun openOrCreateDatabase(name: String, mode: Int, factory: SQLiteDatabase.CursorFactory?, errorHandler: DatabaseErrorHandler?): SQLiteDatabase =
            SQLiteDatabase.openOrCreateDatabase(getDatabasePath(name).path, factory, errorHandler)
    }
    private fun helper(context: Context) = UvirDatabaseHelper(context).also { helpers += it }
    private fun rows(db: SQLiteDatabase, sql: String): List<List<String?>> = db.rawQuery(sql, null).use { c ->
        buildList { while(c.moveToNext()) add((0 until c.columnCount).map { if(c.isNull(it)) null else c.getString(it) }) }
    }
    private fun retained(db: SQLiteDatabase) = uvirPhoneArchiveColumns.map { (table, columns) ->
        rows(db, "SELECT ${columns.keys.joinToString(",")} FROM $table ORDER BY 1")
    } + listOf(rows(db,"SELECT * FROM uvir_counters ORDER BY name"), rows(db,"SELECT * FROM record_transfer_ids ORDER BY uid"))

    private fun legacy(context: Context, version: Int = 26): File {
        val file = context.getDatabasePath("uvir.db")
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            db.execSQL("CREATE TABLE sensors(id INTEGER PRIMARY KEY AUTOINCREMENT,hardware_uid TEXT NOT NULL COLLATE NOCASE UNIQUE,display_name TEXT NOT NULL,first_seen_at INTEGER NOT NULL,last_seen_at INTEGER NOT NULL)")
            db.execSQL("INSERT INTO sensors VALUES(1,'ESP-LEGACY','Private alias',1,2)")
            uvirPhoneArchiveColumns.forEach { (table, columns) ->
                val extras = buildList {
                    add("sensor_id INTEGER REFERENCES sensors(id)")
                    if (table == "acquisitions" || table == "alerts") {
                        add("sensor_device_id TEXT"); add("sensor_record_id INTEGER")
                    }
                    if (table == "acquisitions" || table == "acquisition_sessions") add("external_command INTEGER NOT NULL DEFAULT 0")
                    if (table == "acquisition_sessions") add("sensor_origin_session_id INTEGER")
                }
                db.execSQL("CREATE TABLE $table (${(columns.map { "${it.key} ${it.value}" } + extras).joinToString(",")})")
            }
            db.execSQL("CREATE TABLE uvir_counters(name TEXT PRIMARY KEY,value INTEGER NOT NULL)")
            db.execSQL("INSERT INTO uvir_counters VALUES('session_id',900)")
            db.execSQL("CREATE TABLE sensor_settings(sensor_id INTEGER PRIMARY KEY,calibration_offsets TEXT)")
            db.execSQL("CREATE TABLE sensor_alert_rules(sensor_id INTEGER,threshold REAL)")
            db.execSQL("CREATE TABLE camera_estimates(id INTEGER PRIMARY KEY AUTOINCREMENT,payload TEXT)")
            db.execSQL("INSERT INTO camera_estimates(payload) VALUES('retired')")
            db.execSQL("INSERT INTO acquisition_sessions VALUES(42,'session 🌙',2,1000,4000,1,1,99)")
            db.execSQL("INSERT INTO alert_sessions VALUES(43,'alert note',1000,4000,1)")
            db.insertOrThrow("acquisitions",null,ContentValues().apply {
                put("id",7);put("timestamp",2000);put("note","original record");put("automatic",1)
                put("external_command",1);put("session_id",42);put("session_sequence",2)
                put("position_index",1);put("variant_index",2);put("sensor_id",1)
                put("sensor_device_id","ESP-LEGACY");put("sensor_record_id",88)
                archiveSpectralColumns.forEachIndexed { i, key -> put(key,(i+1)*.123456789012345) }
                put("quality_flags",3);put("optical_window",OpticalWindowSettings(true,List(11) { 1.2f }).toJson().toString())
            })
            db.execSQL("INSERT INTO alerts(id,timestamp,details,quality_flags,session_id,session_sequence,sensor_id) VALUES(8,3000,'UVA|0.7|ABOVE|0.4',1,43,1,1)")
            db.execSQL("UPDATE sqlite_sequence SET seq=400 WHERE name='acquisitions'")
            db.execSQL("UPDATE sqlite_sequence SET seq=80 WHERE name='alerts'")
            createUvirRecordArchiveSchema(db)
            db.version=version
        }
        return file
    }

    @Test fun upgradeRemovesOnlyRetiredMetadataAndPreservesEveryRetainedValueAndIdentity() {
        val context=isolated("upgrade");val original=legacy(context)
        val before=SQLiteDatabase.openDatabase(original.path,null,SQLiteDatabase.OPEN_READONLY).use(::retained)
        helper(context).use { helper ->
            val db=helper.writableDatabase
            assertEquals(27,db.version);assertEquals(before,retained(db))
            val tables=rows(db,"SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' AND name!='android_metadata'").flatten().toSet()
            assertEquals(uvirPhoneArchiveColumns.keys + setOf("uvir_counters","record_transfer_ids"),tables)
            uvirPhoneArchiveColumns.forEach { (table,columns) ->
                assertEquals(columns.keys.toList(),rows(db,"PRAGMA table_info($table)").map { it[1] })
            }
            assertEquals(401L,helper.saveAcquisition(SensorSample(),"next"))
            assertEquals(901L,helper.nextSessionId())
            val backup=File(context.noBackupFilesDir,"before-phone-archive/uvir-v26.db")
            assertTrue(backup.isFile)
            SQLiteDatabase.openDatabase(backup.path,null,SQLiteDatabase.OPEN_READONLY).use { assertEquals(26,it.version);assertEquals(before,retained(it)) }
        }
        helper(context).use { assertEquals(2,it.readSavedRecords().size);assertEquals(27,it.readableDatabase.version) }
    }

    @Test fun oldDatabaseImportsMigrateAPrivateCopyAndPreserveTargetOnFailure() = runBlocking {
        val context=isolated("source");val file=legacy(context)
        val original=file.readBytes()
        val target=helper(isolated("target"));target.saveAcquisition(SensorSample(),"replace")
        importUvirDatabaseArchive(base,target,Uri.fromFile(file))
        assertArrayEquals(original,file.readBytes());assertEquals(7L,target.readSavedRecords().single().id)
        assertEquals(2,target.readAcquisitionSessionVariantsPerPosition(42))
        val kept=retained(target.readableDatabase)
        SQLiteDatabase.openDatabase(file.path,null,SQLiteDatabase.OPEN_READWRITE).use { it.execSQL("ALTER TABLE acquisitions RENAME COLUMN uvc TO missing_band") }
        assertTrue(runCatching { importUvirDatabaseArchive(base,target,Uri.fromFile(file)) }.isFailure)
        assertEquals(kept,retained(target.readableDatabase))
    }

    @Test fun legacySiConversionHappensOnceBeforeMetadataRemoval() = runBlocking {
        val context=isolated("legacy-units");val file=legacy(context,24)
        val target=helper(isolated("target"))
        importUvirDatabaseArchive(base,target,Uri.fromFile(file))
        assertEquals(.00123456789012345,target.readRecord(7)!!.sample.uvc,1e-18)
        assertEquals(.007,parseThresholdAlertLogDetails(target.readThresholdAlertLog().single().details).single().value,1e-15)
        val exported=createUvirDatabaseBackup(base,target,null)
        try { importUvirDatabaseArchive(base,target,Uri.fromFile(exported));assertEquals(.00123456789012345,target.readRecord(7)!!.sample.uvc,1e-18) }
        finally { exported.delete() }
    }

    @Test fun portableVersionTwoHasNoSensorOrExternalFieldsAndVersionOneIsNormalized() {
        val source=helper(isolated("portable"));source.saveAcquisition(SensorSample(uva=.123456789),"keep")
        val current=exportUvirRecordArchive(source,UvirArchiveKind.ACQUISITIONS).toJson()
        assertEquals(2,current.getInt("version"));assertFalse(current.has("sensors"))
        assertFalse(current.toString().contains("external_command"));assertFalse(current.toString().contains("sensor"))
        val old=JSONObject(current.toString()).put("version",1).put("sensors",org.json.JSONArray())
        old.getJSONArray("records").getJSONObject(0).put("sensor","OLD").put("sensor_record_id",123).put("external_command",1)
        val normalized=parseUvirRecordArchive(old.toString(),UvirArchiveKind.ACQUISITIONS)
        assertEquals(parseUvirRecordArchive(current.toString(),UvirArchiveKind.ACQUISITIONS).toJson().toString(),normalized.toJson().toString())
        val target=helper(isolated("receiving"))
        assertEquals(UvirRecordImportResult(1,0),importUvirRecordArchives(target,listOf(normalized)))
        assertEquals(UvirRecordImportResult(0,1),importUvirRecordArchives(target,listOf(normalized)))
        assertEquals(.123456789,target.readRecord(target.readSavedRecords().single().id)!!.sample.uva,0.0)
    }
}
