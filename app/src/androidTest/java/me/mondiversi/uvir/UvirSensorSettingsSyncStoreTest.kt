package me.mondiversi.uvir

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

/** All data is in isolated test preferences/database, never the user's records or associations. */
class UvirSensorSettingsSyncStoreTest {
    private val prefix = "settings-sync-trial-"
    private val base = InstrumentationRegistry.getInstrumentation().targetContext
    private val context = object : ContextWrapper(base) {
        override fun getApplicationContext(): Context = this
        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences = base.getSharedPreferences(prefix + name, mode)
        override fun getDatabasePath(name: String): File = File(base.cacheDir, prefix + name)
        override fun openOrCreateDatabase(name: String, mode: Int, factory: SQLiteDatabase.CursorFactory?): SQLiteDatabase =
            SQLiteDatabase.openOrCreateDatabase(getDatabasePath(name), factory)
        override fun openOrCreateDatabase(name: String, mode: Int, factory: SQLiteDatabase.CursorFactory?, handler: DatabaseErrorHandler?): SQLiteDatabase =
            SQLiteDatabase.openOrCreateDatabase(getDatabasePath(name).path, factory, handler)
    }

    @After fun cleanOnlyFixtures() {
        SQLiteDatabase.deleteDatabase(context.getDatabasePath("uvir.db"))
        listOf(SENSOR_SETTINGS_PENDING_PREFS, "uvir_sensor_credentials").forEach { base.deleteSharedPreferences(prefix + it) }
    }

    private fun snapshot(date: Long = 100) = UvirSensorSettingsSnapshot(
        schemaVersion = 2, firmwareVersion = SENSOR_SETTINGS_SYNC_MIN_FIRMWARE, sensorParameters = SensorParameters(),
        acquisitionParameters = AcquisitionParameters(5, 150, true), calibrationSettings = SensorCalibrationSettings(),
        alertMonitoringEnabled = false, alertRepeatSeconds = 30, alertSessionId = 0, alertRules = emptyList(),
        wifiEnabled = true, bluetoothEnabled = true, internetEnabled = false, internetUsePrimaryWifi = true,
        wifiSsid = "", internetRelayHost = "", internetRelayPort = 8883, lastSyncedAt = 50, updatedAtMs = date)

    @Test fun offsetsStayPerDeviceAndSurviveSettingsBackupAndLegacyPendingEdits() {
        val offsets = CalibrationOffsets(List(15) { it * .125f })
        val a = snapshot().copy(firmwareVersion = "0.5.108",
            calibrationSettings = SensorCalibrationSettings(offsets = offsets))
        val b = snapshot().copy(firmwareVersion = "0.5.108",
            calibrationSettings = SensorCalibrationSettings(offsets = CalibrationOffsets()))
        UvirDatabaseHelper(context).use { database ->
            assertTrue(database.upsertSensorSettings("A", a))
            assertTrue(database.upsertSensorSettings("B", b))
            assertEquals(offsets, database.readSensorSettings("A")!!.calibrationSettings.offsets)
            assertEquals(CalibrationOffsets(), database.readSensorSettings("B")!!.calibrationSettings.offsets)
            val restored = a.toTransferJson().toSensorSettingsSnapshot(b)
            assertEquals(offsets, restored.calibrationSettings.offsets)
            val legacy = UvirPendingSensorSettings(101, listOf("CALIBRATION_CONFIG 1.2 0.8")).applyTo(a)
            assertEquals(offsets, legacy.calibrationSettings.offsets)
            val clear = UvirPendingSensorSettings(102, listOf(sensorCalibrationCommand(b.calibrationSettings))).applyTo(a)
            assertEquals(CalibrationOffsets(), clear.calibrationSettings.offsets)
            assertFalse(sensorBackedSettingsMatch(b, a))
            assertTrue(sensorBackedSettingsMatch(a, a))
        }
    }

    @Test fun channelGainsStayPerSensorAndRoundTripThroughExportsAndTheOutbox() {
        val gains = CalibrationChannelFactors(List(11) { .5f + it * .1f })
        val a = snapshot().copy(firmwareVersion = "0.5.109",
            calibrationSettings = SensorCalibrationSettings(1.2f, .8f,
                offsets = CalibrationOffsets(), channelFactors = gains))
        val b = snapshot().copy(firmwareVersion = "0.5.109")
        UvirDatabaseHelper(context).use { database ->
            assertTrue(database.upsertSensorSettings("A", a))
            assertTrue(database.upsertSensorSettings("B", b))
            assertEquals(gains, database.readSensorSettings("A")!!.calibrationSettings.channelFactors)
            assertNull(database.readSensorSettings("B")!!.calibrationSettings.channelFactors)
            assertEquals(a.calibrationSettings, a.toTransferJson().toSensorSettingsSnapshot(b).calibrationSettings)
            val legacyBackup = a.toTransferJson().apply { remove("calibration_channel_factors") }
            assertEquals(CalibrationChannelFactors.fromModuleFactors(1.2f, .8f),
                legacyBackup.toSensorSettingsSnapshot(a).calibrationSettings.channelFactors)
            val pending = UvirPendingSensorSettings(101, listOf(sensorCalibrationCommand(a.calibrationSettings)))
            assertEquals(gains, pending.applyTo(b).calibrationSettings.channelFactors)
            val olderWindowEdit = UvirPendingSensorSettings(102, listOf("CALIBRATION_CONFIG 1.2 0.8"))
            assertEquals(gains, olderWindowEdit.applyTo(a).calibrationSettings.channelFactors)
            assertFalse(sensorBackedSettingsMatch(a.copy(calibrationSettings = a.calibrationSettings.copy(channelFactors = null)), a))
        }
    }

    @Test fun pendingSurvivesStoreReloadAndOlderReadbackButHardwareWinsTies() {
        UvirDatabaseHelper(context).use { database ->
            database.upsertSensorSettings("A", snapshot(), syncedAt = 50)
            val edit = UvirSensorSettingsSyncStore.stage(context, "A", 100, listOf("SAMPLING_CONFIG 9 200 0"))
            assertEquals(edit, UvirSensorSettingsSyncStore.pending(context, "a"))
            assertNull(UvirSensorSettingsSyncStore.pending(context, "B"))
            val cached = database.readSensorSettings("A")!!
            assertEquals(9, cached.acquisitionParameters.samplesPerMeasurement)
            assertEquals(50L, cached.lastSyncedAt)
            assertEquals(edit.updatedAt, cached.updatedAtMs)
            assertEquals(edit, UvirSensorSettingsSyncStore.reconcile(context, "A", snapshot(), database))
            assertEquals(9, database.readSensorSettings("A")!!.acquisitionParameters.samplesPerMeasurement)
            assertNull(UvirSensorSettingsSyncStore.reconcile(context, "A", snapshot(edit.updatedAt), database))
            assertNull(UvirSensorSettingsSyncStore.pending(context, "A"))
            assertEquals(5, database.readSensorSettings("A")!!.acquisitionParameters.samplesPerMeasurement)
            // A late frame must not reverse an update that was already acknowledged.
            val retry = UvirSensorSettingsSyncStore.reconcile(context, "A", snapshot(100), database)!!
            assertEquals(edit.updatedAt, retry.updatedAt)
            assertTrue(retry.commands.all { sensorSettingsCommandKey(it) != null })
            assertEquals(edit.updatedAt, database.readSensorSettings("A")!!.updatedAtMs)
        }
    }

    @Test fun successfulTransportWriteDoesNotAcknowledgeAndNewAssociationInvalidatesPending() {
        val credentials = UvirSensorCredentials(deviceId = "A", authToken = "old-token")
        UvirSensorCredentialStore.restoreAssociatedSensor(context, credentials)
        val runtime = UvirSensorRuntimeInfo(firmwareVersion = SENSOR_SETTINGS_SYNC_MIN_FIRMWARE, settingsUpdatedAtMs = 100)
        assertTrue(sendDatedSensorSettings(context, "A", runtime, listOf("SAMPLING_CONFIG 7 300 1")) { true })
        assertNotNull(UvirSensorSettingsSyncStore.pending(context, "A"))
        assertFalse(sendDatedSensorSettings(context, "A", runtime, listOf("SAMPLING_CONFIG 9 500 0")) { false })
        assertEquals(listOf("SAMPLING_CONFIG 9 500 0"), UvirSensorSettingsSyncStore.pending(context, "A")!!.commands)
        UvirSensorCredentialStore.restoreAssociatedSensor(context, credentials.copy(authToken = "new-token"))
        assertNull(UvirSensorSettingsSyncStore.pending(context, "A"))
    }

    @Test fun exactHardwareAcknowledgementPersistsPendingNetworkCredentialsForThatSensorOnly() {
        val a = UvirSensorCredentials(deviceId = "A", authToken = "A-token")
        val b = UvirSensorCredentials(deviceId = "B", authToken = "B-token", wifiPassword = "B-password")
        UvirSensorCredentialStore.restoreAssociatedSensor(context, a)
        UvirSensorCredentialStore.restoreAssociatedSensor(context, b)
        val edit = UvirSensorSettingsSyncStore.stage(context, "A", 100,
            listOf("WIFI_CONFIG ${"New-network".sensorSettingsHex()} ${"New-password".sensorSettingsHex()}"))
        UvirDatabaseHelper(context).use { database ->
            UvirSensorSettingsSyncStore.reconcile(context, "A", snapshot(edit.updatedAt), database)
        }
        assertEquals("New-password", UvirSensorCredentialStore.loadForDevice(context, "A")!!.wifiPassword)
        assertEquals("B-password", UvirSensorCredentialStore.loadForDevice(context, "B")!!.wifiPassword)
    }

    @Test fun version19MigrationKeepsSettingsAndInitializesRevisionWithoutInventingAnEditDate() {
        UvirDatabaseHelper(context).use { it.upsertSensorSettings("A", snapshot()) }
        SQLiteDatabase.openDatabase(context.getDatabasePath("uvir.db").path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            val columns = db.rawQuery("PRAGMA table_info(sensor_settings)", null).use { c ->
                buildList { while (c.moveToNext()) { val n = c.getString(1); if (n !in listOf("settings_updated_at_ms", "alert_recording_enabled")) add(n) } }
            }
            db.execSQL("CREATE TABLE old_settings AS SELECT ${columns.joinToString(",")} FROM sensor_settings")
            db.execSQL("DROP TABLE sensor_settings")
            db.execSQL("ALTER TABLE old_settings RENAME TO sensor_settings")
            db.version = 19
        }
        UvirDatabaseHelper(context).use { database ->
            val migrated = database.readSensorSettings("A")!!
            assertEquals(0L, migrated.updatedAtMs)
            assertEquals(snapshot().sensorParameters, migrated.sensorParameters)
            assertNull(migrated.alertRecordingEnabled)
            assertEquals(25, database.readableDatabase.version)
        }
    }
}
