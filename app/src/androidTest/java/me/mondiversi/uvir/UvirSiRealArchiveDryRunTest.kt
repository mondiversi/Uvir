package me.mondiversi.uvir

import android.content.Context
import android.content.ContextWrapper
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Requires an explicitly uploaded COPY in the disposable package. No connections or UI. */
class UvirSiRealArchiveDryRunTest {
    private fun rows(db: SQLiteDatabase, table: String): List<Map<String, Any?>> =
        db.rawQuery("SELECT * FROM $table ORDER BY rowid", null).use { c ->
            buildList { while (c.moveToNext()) add(c.columnNames.mapIndexed { i, key -> key to when (c.getType(i)) {
                Cursor.FIELD_TYPE_NULL -> null
                Cursor.FIELD_TYPE_INTEGER -> c.getLong(i)
                Cursor.FIELD_TYPE_FLOAT -> c.getDouble(i)
                Cursor.FIELD_TYPE_BLOB -> c.getBlob(i).toList()
                else -> c.getString(i)
            } }.toMap()) }
        }

    @Test fun convertsACopyOfTheActualArchiveWithoutChangingItsOtherContents() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("realArchiveDryRun") == "true")
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        check(base.packageName == "me.mondiversi.uvir.verification")
        val root = File(base.filesDir, "si-real-dryrun")
        check(File(root, "uvir.db").isFile)
        val isolated = object : ContextWrapper(base) {
            override fun getApplicationContext(): Context = this
            override fun getDatabasePath(name: String) = File(root, name)
            override fun getNoBackupFilesDir() = File(root, "no-backup").apply { mkdirs() }
            override fun openOrCreateDatabase(name: String, mode: Int, factory: SQLiteDatabase.CursorFactory?): SQLiteDatabase =
                SQLiteDatabase.openOrCreateDatabase(getDatabasePath(name), factory)
            override fun openOrCreateDatabase(name: String, mode: Int, factory: SQLiteDatabase.CursorFactory?,
                errorHandler: android.database.DatabaseErrorHandler?): SQLiteDatabase =
                SQLiteDatabase.openOrCreateDatabase(getDatabasePath(name).path, factory, errorHandler)
        }
        val tables = listOf("sensors", "uvir_counters", "acquisition_sessions", "alert_sessions", "sensor_settings",
            "sensor_alert_rules", "acquisitions", "alerts", "camera_estimates")
        val before = SQLiteDatabase.openDatabase(isolated.getDatabasePath("uvir.db").path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            check(db.version == 24)
            tables.associateWith { rows(db, it) }
        }
        val irradianceColumns = setOf("uvc", "uvb", "uva", "violetto", "blu", "verde", "giallo", "arancione", "rosso", "f8", "nir")
        repeat(2) {
            UvirDatabaseHelper(isolated).use { helper ->
                val db = helper.writableDatabase
                assertEquals(26, db.version)
                tables.forEach { table ->
                    val after = rows(db, table)
                    assertEquals(table, before.getValue(table).size, after.size)
                    before.getValue(table).zip(after).forEach { (old, current) ->
                        old.forEach { (key, value) -> when {
                            (table == "acquisitions" && key in irradianceColumns) || (table == "sensor_alert_rules" && key == "threshold") ->
                                assertEquals("$table/$key", (value as Number).toDouble() / 100,
                                    (current.getValue(key) as Number).toDouble(), 1e-12)
                            table == "alerts" && key == "details" ->
                                assertEquals(scaleUvirAlertDetails(value as String, .01), current[key])
                            table == "sensor_settings" && key == "calibration_offsets" ->
                                assertEquals(CalibrationOffsets.fromStoredOrNull(value as? String), CalibrationOffsets.fromStoredOrNull(current[key] as? String))
                            else -> assertEquals("$table/$key", value, current[key])
                        } }
                    }
                }
                db.rawQuery("PRAGMA quick_check", null).use { c -> assertTrue(c.moveToFirst()); assertEquals("ok", c.getString(0)) }
            }
        }
        val prefs = base.getSharedPreferences("si-real-dryrun", Context.MODE_PRIVATE)
        val oldPrefs = prefs.all.toMap()
        check(!prefs.contains(KEY_IRRADIANCE_STORAGE_UNIT))
        migrateUvirIrradiancePreferences(prefs)
        val once = prefs.all.toMap()
        migrateUvirIrradiancePreferences(prefs)
        assertEquals(once, prefs.all)
        oldPrefs.forEach { (key, value) ->
            if (key.endsWith(KEY_THRESHOLD_ALERT_VALUE) || (key.contains("threshold_alert_rule_") && key.endsWith("_value")))
                assertEquals((value as Number).toDouble() / 100, (once.getValue(key) as Number).toDouble(), 1e-5)
            else if (!key.endsWith(KEY_AUTO_CONDITIONAL_RULES) && !key.endsWith(KEY_AUTO_CONDITIONAL_PLAN))
                assertEquals(key, value, once[key])
        }
        InstrumentationRegistry.getInstrumentation().sendStatus(0, android.os.Bundle().apply {
            putString("stream", "Real-archive COPY verified: ${before.getValue("acquisitions").size} acquisitions, ${before.getValue("alerts").size} alerts; non-irradiance data unchanged; reopen and preferences idempotent.\n")
        })
    }
}
