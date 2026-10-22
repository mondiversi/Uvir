package me.mondiversi.uvir

import android.content.Context
import android.content.ContextWrapper
import android.database.sqlite.SQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** Isolated archives/preferences only. Never opens the installed user's database. */
class UvirSiMigrationTest {
    private val base = InstrumentationRegistry.getInstrumentation().targetContext

    private fun legacyDatabase(): SQLiteDatabase = SQLiteDatabase.create(null).apply {
        execSQL("CREATE TABLE acquisitions(id INTEGER PRIMARY KEY,uvc REAL,uvb REAL,uva REAL,violetto REAL,blu REAL,verde REAL,giallo REAL,arancione REAL,rosso REAL,f8 REAL,nir REAL,note TEXT,quality_flags INTEGER,timestamp INTEGER)")
        execSQL("INSERT INTO acquisitions VALUES(73,100,200,0.003,400,500,600,700,800,900,1000,1100,'preserve',3,123456789)")
        execSQL("CREATE TABLE alerts(id INTEGER PRIMARY KEY,details TEXT,note TEXT)")
        execSQL("INSERT INTO alerts VALUES(91,'UVA|0.003|ABOVE|0.002;BLUE|500|BELOW|600','keep')")
        execSQL("CREATE TABLE sensor_alert_rules(threshold REAL,metric TEXT)")
        execSQL("INSERT INTO sensor_alert_rules VALUES(2,'UVA')")
        execSQL("CREATE TABLE sensor_settings(calibration_offsets TEXT,visible_calibration_factor REAL,settings_updated_at_ms INTEGER)")
        execSQL("INSERT INTO sensor_settings VALUES(?,1.5,1234)", arrayOf(JSONObject().put("unit", "uW/cm2")
            .put("version", 1).put("values", JSONArray(List(15) { it + .003 })).toString()))
        version = 24
    }

    @Test fun sqliteConversionPreservesEveryNonIrradianceFieldAndLegacyChannelLayout() {
        legacyDatabase().use { db ->
            migrateUvirDatabaseToSi(db)
            db.rawQuery("SELECT * FROM acquisitions", null).use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(73, c.getInt(0)); assertEquals(1.0, c.getDouble(1), 0.0)
                assertEquals(.00003, c.getDouble(3), 1e-15)
                assertEquals(11.0, c.getDouble(11), 0.0)
                assertEquals("preserve", c.getString(12)); assertEquals(3, c.getInt(13))
                assertEquals(123456789, c.getLong(14))
            }
            db.rawQuery("SELECT details,note FROM alerts", null).use { c ->
                assertTrue(c.moveToFirst())
                val entries = parseThresholdAlertLogDetails(c.getString(0))
                assertEquals(.00003, entries[0].value, 1e-15)
                assertEquals(.00002f, entries[0].rule.threshold, 1e-10f)
                assertEquals(5.0, entries[1].value, 0.0)
                assertEquals("keep", c.getString(1))
            }
            db.rawQuery("SELECT * FROM sensor_settings", null).use { c ->
                assertTrue(c.moveToFirst())
                val json = JSONObject(c.getString(0))
                assertEquals("W/m2", json.getString("unit"))
                val offsets = CalibrationOffsets.fromJsonOrNull(json)!!
                assertTrue(offsets.isLegacyPhysical); assertEquals(15, offsets.values.size)
                assertEquals(.14003f, offsets.values[14], 1e-7f)
                assertEquals(1.5, c.getDouble(1), 0.0); assertEquals(1234L, c.getLong(2))
            }
            db.rawQuery("SELECT threshold FROM sensor_alert_rules", null).use { c ->
                assertTrue(c.moveToFirst()); assertEquals(.02, c.getDouble(0), 0.0)
            }
        }
    }

    @Test fun malformedOffsetRollsBackTheWholeDatabaseTransaction() {
        legacyDatabase().use { db ->
            db.execSQL("UPDATE sensor_settings SET calibration_offsets='not-json'")
            db.beginTransaction()
            try {
                assertTrue(runCatching { migrateUvirDatabaseToSi(db) }.isFailure)
            } finally { db.endTransaction() }
            db.rawQuery("SELECT uvc FROM acquisitions", null).use { c ->
                assertTrue(c.moveToFirst()); assertEquals(100.0, c.getDouble(0), 0.0)
            }
            assertEquals(24, db.version)
        }
    }

    @Test fun sqliteOpenHelperBacksUpBeforeUpgradeAndDoesNotConvertOnReopen() {
        val root = File(base.cacheDir, "si-test-${UUID.randomUUID()}").apply { mkdirs() }
        val isolated = object : ContextWrapper(base) {
            override fun getApplicationContext(): Context = this
            override fun getDatabasePath(name: String) = File(root, name)
            override fun getNoBackupFilesDir() = File(root, "no-backup").apply { mkdirs() }
            override fun openOrCreateDatabase(name: String, mode: Int, factory: SQLiteDatabase.CursorFactory?): SQLiteDatabase =
                SQLiteDatabase.openOrCreateDatabase(getDatabasePath(name), factory)
            override fun openOrCreateDatabase(name: String, mode: Int, factory: SQLiteDatabase.CursorFactory?,
                errorHandler: android.database.DatabaseErrorHandler?): SQLiteDatabase =
                SQLiteDatabase.openOrCreateDatabase(getDatabasePath(name).absolutePath, factory, errorHandler)
        }
        try {
            // Make a real v24 file; backup is checkpointed before the upgrade begins.
            SQLiteDatabase.openOrCreateDatabase(isolated.getDatabasePath("uvir.db"), null).use { db ->
                db.execSQL("CREATE TABLE acquisitions(id INTEGER PRIMARY KEY,uvc REAL)")
                db.execSQL("INSERT INTO acquisitions VALUES(7,200)"); db.version = 24
            }
            repeat(2) {
                UvirDatabaseHelper(isolated).use { helper ->
                    val db = helper.writableDatabase
                    assertEquals(25, db.version)
                    db.rawQuery("SELECT uvc FROM acquisitions", null).use { c ->
                        assertTrue(c.moveToFirst()); assertEquals(2.0, c.getDouble(0), 0.0)
                    }
                }
            }
            val backup = File(isolated.noBackupFilesDir, "before-si/uvir-v24.db")
            SQLiteDatabase.openDatabase(backup.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                assertEquals(24, db.version)
                db.rawQuery("SELECT uvc FROM acquisitions", null).use { c ->
                    assertTrue(c.moveToFirst()); assertEquals(200.0, c.getDouble(0), 0.0)
                }
            }
        } finally { root.deleteRecursively() }
    }

    @Test fun preferencesAreAtomicVersionedAndIncludeInactiveSensors() {
        val name = "si-preferences-${UUID.randomUUID()}"
        val prefs = base.getSharedPreferences(name, Context.MODE_PRIVATE)
        try {
            val key = thresholdRulePreferenceKey(ThresholdAlertMetric.UVA, "value")
            val other = sensorContextPreferenceKey("OTHER", key)
            prefs.edit().putFloat(key, 100f).putFloat(other, 300f)
                .putFloat(KEY_SENSOR_UV_CALIBRATION_FACTOR, 1.7f)
                .putString(KEY_AUTO_CONDITIONAL_RULES, "[{\"metric\":\"UVA\",\"enabled\":true,\"direction\":\"ABOVE\",\"threshold\":200}]")
                .putString(KEY_AUTO_CONDITIONAL_PLAN, "ALL|ACQUIRE|UVA,ABOVE,200")
                .putString(UVIR_IRRADIANCE_UNIT_KEY, "uw_cm2").putLong("settings_date", 77).commit()
            repeat(2) { migrateUvirIrradiancePreferences(prefs) }
            assertEquals(1f, prefs.getFloat(key, 0f), 0f)
            assertEquals(3f, prefs.getFloat(other, 0f), 0f)
            assertEquals(1.7f, prefs.getFloat(KEY_SENSOR_UV_CALIBRATION_FACTOR, 0f), 0f)
            assertEquals(2f, loadAcquisitionConditions(prefs).first { it.metric == ThresholdAlertMetric.UVA }.threshold, 0f)
            assertEquals(2f, decodeConditionalAcquisitionPlan(prefs.getString(KEY_AUTO_CONDITIONAL_PLAN, null))!!.rules.single().threshold, 0f)
            assertEquals("uw_cm2", prefs.getString(UVIR_IRRADIANCE_UNIT_KEY, null))
            assertEquals(77, prefs.getLong("settings_date", 0))
            assertFalse(isTransferableAppPreference(KEY_IRRADIANCE_STORAGE_UNIT))
        } finally { base.deleteSharedPreferences(name) }
    }

    @Test fun invalidPreferencesCannotLeavePartiallyConvertedThresholds() {
        val name = "si-invalid-${UUID.randomUUID()}"
        val prefs = base.getSharedPreferences(name, Context.MODE_PRIVATE)
        try {
            prefs.edit().putFloat(KEY_THRESHOLD_ALERT_VALUE, 100f).putString(KEY_AUTO_CONDITIONAL_RULES, "broken").commit()
            assertTrue(runCatching { migrateUvirIrradiancePreferences(prefs) }.isFailure)
            assertEquals(100f, prefs.getFloat(KEY_THRESHOLD_ALERT_VALUE, 0f), 0f)
            assertFalse(prefs.contains(KEY_IRRADIANCE_STORAGE_UNIT))
        } finally { base.deleteSharedPreferences(name) }
    }

    @Test fun liveLegacyAndSiFramesRepresentTheSamePhysicalSample() {
        fun frame(unit: String, value: Double) = JSONObject().put("unit", unit)
            .put("bands", JSONObject().put("uva", value))
        val legacy = frame("uW/cm2", 200.0).toUvirSensorSampleOrNull()!!
        val si = frame("W/m2", 2.0).toUvirSensorSampleOrNull()!!
        assertEquals(si, legacy); assertEquals(2.0, si.uva, 0.0)
        assertNull(frame("unknown", 2.0).toUvirSensorSampleOrNull())
        assertEquals(si, sensorSampleToJson(si).toSensorSample())
        assertEquals(si, JSONObject().put("uva", 200.0).toSensorSample())
    }

    @Test fun offsetJsonUnitsAreExplicitAndNeverConvertedTwice() {
        for (count in listOf(11, 15)) {
            val old = JSONObject().put("version", if (count == 15) 1 else 2).put("unit", "uW/cm2")
                .put("values", JSONArray(List(count) { 200.0 }))
            val offsets = CalibrationOffsets.fromJsonOrNull(old)!!
            assertEquals(List(count) { 2f }, offsets.values)
            assertEquals(offsets, CalibrationOffsets.fromJsonOrNull(offsets.toJson()))
        }
    }

    @Test fun transferSnapshotsAndWireOutboxKeepTheirDistinctUnitsAndAuthority() {
        val settings = UvirSensorSettingsSnapshot(schemaVersion = 2, firmwareVersion = "0.5.111",
            sensorParameters = SensorParameters(), acquisitionParameters = AcquisitionParameters(5, 150, true),
            calibrationSettings = SensorCalibrationSettings(offsets = CalibrationOffsets(List(11) { .03f })),
            alertMonitoringEnabled = false, alertRepeatSeconds = 30, alertSessionId = 0,
            alertRules = listOf(ThresholdAlertRule(ThresholdAlertMetric.UVA, true, ThresholdAlertDirection.ABOVE, 2f)),
            wifiEnabled = true, bluetoothEnabled = true, internetEnabled = false, internetUsePrimaryWifi = true,
            wifiSsid = "", internetRelayHost = "", internetRelayPort = 8883, updatedAtMs = 12345)
        val si = settings.toTransferJson()
        assertEquals(2f, si.toSensorSettingsSnapshot(settings).alertRules.single().threshold, 0f)
        assertEquals(settings.calibrationSettings, si.toSensorSettingsSnapshot(settings).calibrationSettings)
        val old = JSONObject(si.toString()).apply {
            remove("irradiance_unit")
            getJSONArray("alert_rules").getJSONObject(0).put("threshold", 200)
            put("calibration_offsets", JSONObject().put("version", 2).put("unit", "uW/cm2").put("values", JSONArray(List(11) { 3 })))
        }.toSensorSettingsSnapshot(settings)
        assertEquals(settings.alertRules, old.alertRules)
        assertEquals(settings.calibrationSettings, old.calibrationSettings)
        val commands = cachedSensorSettingsCommands(settings, UvirSensorCredentials(deviceId = "test"))
        val outbox = UvirPendingSensorSettings(12345, commands).applyTo(settings)
        assertEquals(settings.alertRules, outbox.alertRules)
        assertEquals(settings.calibrationSettings, outbox.calibrationSettings)
        assertEquals(12345, outbox.updatedAtMs)
        assertTrue(commands.first { it.startsWith("CALIBRATION_CONFIG") }.contains("COLOR_OFFSETS 3 3 3"))
    }
}
