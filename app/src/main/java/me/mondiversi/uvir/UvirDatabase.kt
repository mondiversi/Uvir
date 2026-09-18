package me.mondiversi.uvir

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class UvirDatabaseHelper(
    context: Context
) : SQLiteOpenHelper(
    context.applicationContext,
    "uvir.db",
    null,
    18
) {

    private val appContext =
        context.applicationContext

    private val mutableAcquisitionRevision = MutableStateFlow(0L)
    private val mutableAlertRevision = MutableStateFlow(0L)
    private val mutableSensorProfileRevision = MutableStateFlow(0L)

    internal val acquisitionRevision: StateFlow<Long> =
        mutableAcquisitionRevision.asStateFlow()
    internal val alertRevision: StateFlow<Long> =
        mutableAlertRevision.asStateFlow()
    internal val sensorProfileRevision: StateFlow<Long> =
        mutableSensorProfileRevision.asStateFlow()

    private fun notifyAcquisitionsChanged() {
        mutableAcquisitionRevision.update { it + 1L }
    }

    private fun notifyAlertsChanged() {
        mutableAlertRevision.update { it + 1L }
    }

    private fun notifySensorProfilesChanged() {
        mutableSensorProfileRevision.update { it + 1L }
    }

    private fun createSensorsTable(
        db: SQLiteDatabase
    ) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS sensors (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                hardware_uid TEXT NOT NULL COLLATE NOCASE UNIQUE,
                display_name TEXT NOT NULL,
                first_seen_at INTEGER NOT NULL,
                last_seen_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }

    private fun createCountersTable(
        db: SQLiteDatabase
    ) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS uvir_counters (
                name TEXT PRIMARY KEY,
                value INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT OR IGNORE INTO uvir_counters(name, value)
            VALUES ('session_id', 0)
            """.trimIndent()
        )
    }

    private fun createAcquisitionsTable(
        db: SQLiteDatabase
    ) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS acquisitions (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                timestamp INTEGER NOT NULL,
                note TEXT NOT NULL,
                automatic INTEGER NOT NULL DEFAULT 0,
                external_command INTEGER NOT NULL DEFAULT 0,
                session_id INTEGER,
                session_sequence INTEGER,
                position_index INTEGER,
                variant_index INTEGER,
                sensor_id INTEGER REFERENCES sensors(id) ON DELETE SET NULL,
                sensor_device_id TEXT,
                sensor_record_id INTEGER,

                uvc REAL NOT NULL,
                uvb REAL NOT NULL,
                uva REAL NOT NULL,

                violetto REAL NOT NULL,
                blu REAL NOT NULL,
                verde REAL NOT NULL,
                giallo REAL NOT NULL,
                arancione REAL NOT NULL,
                rosso REAL NOT NULL,

                f8 REAL NOT NULL,
                nir REAL NOT NULL,
                quality_flags INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE UNIQUE INDEX IF NOT EXISTS
                idx_acquisitions_sensor_record
            ON acquisitions(sensor_id, sensor_record_id)
            """.trimIndent()
        )
    }

    private fun createAlertsTable(
        db: SQLiteDatabase
    ) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS alerts (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                timestamp INTEGER NOT NULL,
                details TEXT NOT NULL,
                quality_flags INTEGER NOT NULL DEFAULT 0,
                session_id INTEGER,
                session_sequence INTEGER,
                sensor_id INTEGER REFERENCES sensors(id) ON DELETE SET NULL,
                sensor_device_id TEXT,
                sensor_record_id INTEGER
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE UNIQUE INDEX IF NOT EXISTS
                idx_alerts_sensor_record
            ON alerts(sensor_id, sensor_record_id)
            """.trimIndent()
        )
    }

    private fun createSessionTables(
        db: SQLiteDatabase
    ) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS acquisition_sessions (
                session_id INTEGER PRIMARY KEY,
                sensor_id INTEGER REFERENCES sensors(id) ON DELETE SET NULL,
                sensor_origin_session_id INTEGER,
                note TEXT NOT NULL DEFAULT '',
                external_command INTEGER NOT NULL DEFAULT 0,
                variants_per_position INTEGER NOT NULL DEFAULT 1,
                started_at INTEGER NOT NULL,
                ended_at INTEGER
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE UNIQUE INDEX IF NOT EXISTS
                idx_acquisition_sessions_sensor_origin
            ON acquisition_sessions(sensor_id, sensor_origin_session_id)
            WHERE sensor_origin_session_id IS NOT NULL
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS alert_sessions (
                session_id INTEGER PRIMARY KEY,
                sensor_id INTEGER REFERENCES sensors(id) ON DELETE SET NULL,
                note TEXT NOT NULL DEFAULT '',
                started_at INTEGER NOT NULL,
                ended_at INTEGER
            )
            """.trimIndent()
        )
    }

    private fun createSensorSettingsTables(
        db: SQLiteDatabase
    ) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS sensor_settings (
                sensor_id INTEGER PRIMARY KEY
                    REFERENCES sensors(id) ON DELETE CASCADE,
                settings_schema_version INTEGER NOT NULL,
                firmware_version TEXT NOT NULL,
                autonomous_recording_enabled INTEGER NOT NULL,
                automatic_shutdown_enabled INTEGER NOT NULL,
                automatic_shutdown_seconds INTEGER NOT NULL,
                status_led_enabled INTEGER NOT NULL,
                status_led_brightness INTEGER NOT NULL,
                status_buzzer_enabled INTEGER NOT NULL,
                status_buzzer_volume INTEGER NOT NULL,
                samples_per_measurement INTEGER NOT NULL,
                sample_spacing_ms INTEGER NOT NULL,
                discard_extremes INTEGER NOT NULL,
                visible_calibration_factor REAL NOT NULL,
                uv_calibration_factor REAL NOT NULL,
                alert_monitoring_enabled INTEGER NOT NULL,
                alert_repeat_seconds INTEGER NOT NULL,
                alert_session_id INTEGER NOT NULL,
                wifi_enabled INTEGER NOT NULL,
                bluetooth_enabled INTEGER NOT NULL,
                internet_enabled INTEGER NOT NULL,
                internet_use_primary_wifi INTEGER NOT NULL,
                wifi_ssid TEXT NOT NULL,
                internet_relay_host TEXT NOT NULL,
                internet_relay_port INTEGER NOT NULL,
                last_synced_at INTEGER NOT NULL,
                external_command_enabled INTEGER NOT NULL DEFAULT 1
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS sensor_alert_rules (
                sensor_id INTEGER NOT NULL
                    REFERENCES sensors(id) ON DELETE CASCADE,
                metric TEXT NOT NULL,
                direction TEXT NOT NULL,
                threshold REAL NOT NULL,
                PRIMARY KEY(sensor_id, metric)
            )
            """.trimIndent()
        )
    }

    override fun onCreate(db: SQLiteDatabase) {
        createSensorsTable(db)
        createAcquisitionsTable(db)
        createCountersTable(db)
        createAlertsTable(db)
        createSessionTables(db)
        createSensorSettingsTables(db)
    }

    override fun onUpgrade(
        db: SQLiteDatabase,
        oldVersion: Int,
        newVersion: Int
    ) {
        // Newer acquisition/session schemas reference the local sensor table.
        // Create it first so upgrades are safe from every supported version.
        createSensorsTable(db)

        if (oldVersion < 5) {
            db.execSQL("DROP TABLE IF EXISTS measurements")
            db.execSQL("DROP TABLE IF EXISTS acquisitions")
            db.execSQL(
                "DELETE FROM sqlite_sequence " +
                    "WHERE name IN ('measurements', 'acquisitions')"
            )
            appContext
                .getSharedPreferences(
                    PREFS_NAME,
                    Context.MODE_PRIVATE
                )
                .edit()
                .putBoolean(KEY_AUTO_ENABLED, false)
                .putLong(KEY_AUTO_SESSION_ID, 0L)
                .putInt(KEY_AUTO_COMPLETED_COUNT, 0)
                .apply()
        }

        if (oldVersion < 9) {
            migrateAcquisitionsToVersion9(db)
            migrateAlertsToVersion9(db)
            migrateSessionCounterToVersion9(db)
        }


        if (oldVersion < 10) {
            migrateSessionsToVersion10(db)
        }

        if (oldVersion < 11) {
            migrateSensorProfilesToVersion11(db)
        }

        if (oldVersion < 12) {
            createSensorSettingsTables(db)
        }

        if (oldVersion < 13) {
            addColumnIfMissing(
                db,
                "acquisitions",
                "quality_flags",
                "INTEGER NOT NULL DEFAULT 0"
            )
            addColumnIfMissing(
                db,
                "alerts",
                "quality_flags",
                "INTEGER NOT NULL DEFAULT 0"
            )
        }

        if (oldVersion < 14) {
            addColumnIfMissing(
                db,
                "acquisitions",
                "external_command",
                "INTEGER NOT NULL DEFAULT 0"
            )
            addColumnIfMissing(
                db,
                "acquisition_sessions",
                "external_command",
                "INTEGER NOT NULL DEFAULT 0"
            )

            // A sensor-originated record without a session can only have been
            // created by the external input. Preserve that distinction for
            // records captured before this column existed.
            db.execSQL(
                """
                UPDATE acquisitions
                SET external_command = 1
                WHERE sensor_record_id IS NOT NULL
                  AND session_id IS NULL
                """.trimIndent()
            )

            val preferences = appContext.getSharedPreferences(
                PREFS_NAME,
                Context.MODE_PRIVATE
            )
            if (preferences.getBoolean(KEY_AUTO_EXTERNAL_COMMAND, false)) {
                val sessionId = preferences.getLong(KEY_AUTO_SESSION_ID, 0L)
                if (sessionId > 0L) {
                    db.execSQL(
                        "UPDATE acquisition_sessions SET external_command = 1 WHERE session_id = ?",
                        arrayOf(sessionId)
                    )
                    db.execSQL(
                        "UPDATE acquisitions SET external_command = 1 WHERE session_id = ?",
                        arrayOf(sessionId)
                    )
                }
            }
        }

        if (oldVersion < 15) {
            addColumnIfMissing(
                db,
                "acquisition_sessions",
                "sensor_origin_session_id",
                "INTEGER"
            )
            db.execSQL(
                """
                CREATE UNIQUE INDEX IF NOT EXISTS
                    idx_acquisition_sessions_sensor_origin
                ON acquisition_sessions(sensor_id, sensor_origin_session_id)
                WHERE sensor_origin_session_id IS NOT NULL
                """.trimIndent()
            )
        }

        if (oldVersion < 16) {
            addColumnIfMissing(
                db,
                "sensor_settings",
                "external_command_enabled",
                "INTEGER NOT NULL DEFAULT 1"
            )
        }

        if (oldVersion < 17) {
            addColumnIfMissing(
                db,
                "acquisition_sessions",
                "acquisitions_per_cycle",
                "INTEGER NOT NULL DEFAULT 1"
            )
        }

        if (oldVersion < 18) {
            migrateAcquisitionVariantsToVersion18(db)
        }
    }

    private fun migrateAcquisitionVariantsToVersion18(
        db: SQLiteDatabase
    ) {
        addColumnIfMissing(db, "acquisitions", "position_index", "INTEGER")
        addColumnIfMissing(db, "acquisitions", "variant_index", "INTEGER")

        val sessionColumns = tableColumns(db, "acquisition_sessions")
        val variantsSource =
            when {
                "variants_per_position" in sessionColumns ->
                    "variants_per_position"
                "acquisitions_per_cycle" in sessionColumns ->
                    "acquisitions_per_cycle"
                else -> "1"
            }

        db.execSQL("DROP TABLE IF EXISTS acquisition_sessions_v18")
        db.execSQL(
            """
            CREATE TABLE acquisition_sessions_v18 (
                session_id INTEGER PRIMARY KEY,
                sensor_id INTEGER REFERENCES sensors(id) ON DELETE SET NULL,
                sensor_origin_session_id INTEGER,
                note TEXT NOT NULL DEFAULT '',
                external_command INTEGER NOT NULL DEFAULT 0,
                variants_per_position INTEGER NOT NULL DEFAULT 1,
                started_at INTEGER NOT NULL,
                ended_at INTEGER
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO acquisition_sessions_v18 (
                session_id, sensor_id, sensor_origin_session_id, note,
                external_command, variants_per_position, started_at, ended_at
            )
            SELECT
                session_id, sensor_id, sensor_origin_session_id, note,
                external_command, MAX(1, $variantsSource), started_at, ended_at
            FROM acquisition_sessions
            """.trimIndent()
        )
        db.execSQL("DROP TABLE acquisition_sessions")
        db.execSQL(
            "ALTER TABLE acquisition_sessions_v18 RENAME TO acquisition_sessions"
        )
        db.execSQL(
            """
            CREATE UNIQUE INDEX IF NOT EXISTS
                idx_acquisition_sessions_sensor_origin
            ON acquisition_sessions(sensor_id, sensor_origin_session_id)
            WHERE sensor_origin_session_id IS NOT NULL
            """.trimIndent()
        )
        db.execSQL(
            """
            UPDATE acquisitions
            SET
                variant_index = CASE
                    WHEN session_sequence IS NOT NULL AND (
                        SELECT variants_per_position
                        FROM acquisition_sessions
                        WHERE session_id = acquisitions.session_id
                    ) > 1
                    THEN ((session_sequence - 1) % (
                        SELECT variants_per_position
                        FROM acquisition_sessions
                        WHERE session_id = acquisitions.session_id
                    )) + 1
                    ELSE NULL
                END,
                position_index = CASE
                    WHEN session_sequence IS NOT NULL AND (
                        SELECT variants_per_position
                        FROM acquisition_sessions
                        WHERE session_id = acquisitions.session_id
                    ) > 1
                    THEN CAST((session_sequence - 1) / (
                        SELECT variants_per_position
                        FROM acquisition_sessions
                        WHERE session_id = acquisitions.session_id
                    ) AS INTEGER) + 1
                    ELSE NULL
                END
            """.trimIndent()
        )
    }

    private fun addColumnIfMissing(
        db: SQLiteDatabase,
        table: String,
        column: String,
        definition: String
    ) {
        if (column !in tableColumns(db, table)) {
            db.execSQL("ALTER TABLE $table ADD COLUMN $column $definition")
        }
    }

    private fun migrateSensorProfilesToVersion11(
        db: SQLiteDatabase
    ) {
        createSensorsTable(db)
        addColumnIfMissing(db, "acquisitions", "sensor_id", "INTEGER")
        addColumnIfMissing(db, "alerts", "sensor_id", "INTEGER")
        addColumnIfMissing(db, "acquisition_sessions", "sensor_id", "INTEGER")
        addColumnIfMissing(db, "alert_sessions", "sensor_id", "INTEGER")

        listOf("acquisitions", "alerts").forEach { table ->
            db.execSQL(
                """
                INSERT OR IGNORE INTO sensors(
                    hardware_uid, display_name, first_seen_at, last_seen_at
                )
                SELECT
                    TRIM(sensor_device_id),
                    TRIM(sensor_device_id),
                    MIN(timestamp),
                    MAX(timestamp)
                FROM $table
                WHERE sensor_device_id IS NOT NULL
                  AND TRIM(sensor_device_id) <> ''
                GROUP BY TRIM(sensor_device_id)
                """.trimIndent()
            )
            db.execSQL(
                """
                UPDATE $table
                SET sensor_id = (
                    SELECT sensor.id
                    FROM sensors AS sensor
                    WHERE sensor.hardware_uid = TRIM($table.sensor_device_id)
                )
                WHERE sensor_id IS NULL
                  AND sensor_device_id IS NOT NULL
                  AND TRIM(sensor_device_id) <> ''
                """.trimIndent()
            )
        }

        db.execSQL(
            """
            UPDATE acquisition_sessions
            SET sensor_id = (
                SELECT acquisition.sensor_id
                FROM acquisitions AS acquisition
                WHERE acquisition.session_id = acquisition_sessions.session_id
                  AND acquisition.sensor_id IS NOT NULL
                ORDER BY acquisition.timestamp, acquisition.id
                LIMIT 1
            )
            WHERE sensor_id IS NULL
            """.trimIndent()
        )
        db.execSQL(
            """
            UPDATE alert_sessions
            SET sensor_id = (
                SELECT alert.sensor_id
                FROM alerts AS alert
                WHERE alert.session_id = alert_sessions.session_id
                  AND alert.sensor_id IS NOT NULL
                ORDER BY alert.timestamp, alert.id
                LIMIT 1
            )
            WHERE sensor_id IS NULL
            """.trimIndent()
        )

        db.execSQL("DROP INDEX IF EXISTS idx_acquisitions_sensor_record")
        db.execSQL("DROP INDEX IF EXISTS idx_alerts_sensor_record")
        db.execSQL(
            """
            CREATE UNIQUE INDEX idx_acquisitions_sensor_record
            ON acquisitions(sensor_id, sensor_record_id)
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE UNIQUE INDEX idx_alerts_sensor_record
            ON alerts(sensor_id, sensor_record_id)
            """.trimIndent()
        )
    }

    private fun migrateSessionsToVersion10(
        db: SQLiteDatabase
    ) {
        createSessionTables(db)
        db.execSQL(
            """
            INSERT OR IGNORE INTO acquisition_sessions(
                session_id, note, started_at, ended_at
            )
            SELECT
                source.session_id,
                COALESCE(
                    (
                        SELECT candidate.note
                        FROM acquisitions AS candidate
                        WHERE candidate.session_id = source.session_id
                          AND TRIM(candidate.note) <> ''
                        ORDER BY candidate.timestamp, candidate.id
                        LIMIT 1
                    ),
                    ''
                ),
                MIN(source.timestamp),
                MAX(source.timestamp)
            FROM acquisitions AS source
            WHERE source.session_id IS NOT NULL
            GROUP BY source.session_id
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT OR IGNORE INTO alert_sessions(
                session_id, note, started_at, ended_at
            )
            SELECT
                session_id,
                '',
                MIN(timestamp),
                MAX(timestamp)
            FROM alerts
            WHERE session_id IS NOT NULL
            GROUP BY session_id
            """.trimIndent()
        )
    }

    private fun tableExists(
        db: SQLiteDatabase,
        table: String
    ): Boolean =
        db.rawQuery(
            "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?",
            arrayOf(table)
        ).use { it.moveToFirst() }

    private fun tableColumns(
        db: SQLiteDatabase,
        table: String
    ): Set<String> =
        db.rawQuery(
            "PRAGMA table_info($table)",
            null
        ).use { cursor ->
            buildSet {
                val nameIndex = cursor.getColumnIndexOrThrow("name")
                while (cursor.moveToNext()) {
                    add(cursor.getString(nameIndex))
                }
            }
        }

    private fun normalizedHardwareUid(hardwareUid: String): String =
        hardwareUid.trim()

    private fun ensureSensorRow(
        database: SQLiteDatabase,
        hardwareUid: String,
        seenAt: Long = System.currentTimeMillis()
    ): Long? {
        val normalizedUid = normalizedHardwareUid(hardwareUid)
        if (normalizedUid.isBlank()) return null

        createSensorsTable(database)
        val normalizedSeenAt = seenAt.coerceAtLeast(0L)
        val inserted =
            database.insertWithOnConflict(
                "sensors",
                null,
                ContentValues().apply {
                    put("hardware_uid", normalizedUid)
                    put("display_name", normalizedUid)
                    put("first_seen_at", normalizedSeenAt)
                    put("last_seen_at", normalizedSeenAt)
                },
                SQLiteDatabase.CONFLICT_IGNORE
            )

        val sensorId =
            database.query(
                "sensors",
                arrayOf("id"),
                "hardware_uid = ?",
                arrayOf(normalizedUid),
                null,
                null,
                null,
                "1"
            ).use { cursor ->
                if (cursor.moveToFirst()) cursor.getLong(0) else null
            }

        if (sensorId != null && inserted == -1L) {
            database.update(
                "sensors",
                ContentValues().apply {
                    put("last_seen_at", normalizedSeenAt)
                },
                "id = ? AND last_seen_at < ?",
                arrayOf(sensorId.toString(), normalizedSeenAt.toString())
            )
        }
        if (inserted != -1L) notifySensorProfilesChanged()
        return sensorId
    }

    fun ensureSensorProfile(
        hardwareUid: String,
        seenAt: Long = System.currentTimeMillis()
    ): UvirSensorProfile? {
        val database = writableDatabase
        val sensorId = ensureSensorRow(database, hardwareUid, seenAt) ?: return null
        return readSensorProfile(database, sensorId)
    }

    fun renameSensor(
        hardwareUid: String,
        displayName: String
    ): UvirSensorProfile? {
        val database = writableDatabase
        val normalizedUid = normalizedHardwareUid(hardwareUid)
        // A phone-only rename is not sensor activity: preserve its last-seen timestamp.
        val sensorId = findSensorProfile(normalizedUid)?.id
            ?: ensureSensorRow(database, normalizedUid, seenAt = 0L)
            ?: return null
        val normalizedName = normalizeSensorDisplayName(displayName, normalizedUid)
        val changed =
            database.update(
                "sensors",
                ContentValues().apply {
                    put("display_name", normalizedName)
                },
                "id = ? AND display_name <> ?",
                arrayOf(sensorId.toString(), normalizedName)
            ) > 0
        if (changed) notifySensorProfilesChanged()
        return readSensorProfile(database, sensorId)
    }

    fun readSensorProfiles(): List<UvirSensorProfile> {
        val database = readableDatabase
        createSensorsTable(database)
        return database.query(
            "sensors",
            arrayOf(
                "id",
                "hardware_uid",
                "display_name",
                "first_seen_at",
                "last_seen_at"
            ),
            null,
            null,
            null,
            null,
            "display_name COLLATE NOCASE, id"
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        UvirSensorProfile(
                            id = cursor.getLong(0),
                            hardwareUid = cursor.getString(1),
                            displayName = cursor.getString(2),
                            firstSeenAt = cursor.getLong(3),
                            lastSeenAt = cursor.getLong(4)
                        )
                    )
                }
            }
        }
    }

    fun assignDefaultSensorToUnassociatedAlerts(defaultHardwareUid: String): Int {
        val defaultProfile =
            findSensorProfile(defaultHardwareUid)
                ?: readSensorProfiles().singleOrNull()
                ?: return 0
        val changed = backfillUnassociatedAlertSensors(writableDatabase, defaultProfile.id)
        if (changed > 0) notifyAlertsChanged()
        return changed
    }

    fun findSensorProfile(sensorId: Long): UvirSensorProfile? {
        if (sensorId <= 0L) return null
        return readSensorProfile(readableDatabase, sensorId)
    }

    fun findSensorProfile(hardwareUid: String): UvirSensorProfile? {
        val normalizedUid = normalizedHardwareUid(hardwareUid)
        if (normalizedUid.isBlank()) return null
        val database = readableDatabase
        createSensorsTable(database)
        val sensorId =
            database.query(
                "sensors",
                arrayOf("id"),
                "hardware_uid = ?",
                arrayOf(normalizedUid),
                null,
                null,
                null,
                "1"
            ).use { cursor ->
                if (cursor.moveToFirst()) cursor.getLong(0) else null
            }
        return sensorId?.let { readSensorProfile(database, it) }
    }

    fun upsertSensorSettings(
        hardwareUid: String,
        settings: UvirSensorSettingsSnapshot,
        syncedAt: Long = System.currentTimeMillis()
    ): Boolean {
        val database = writableDatabase
        database.beginTransaction()
        return try {
            createSensorSettingsTables(database)
            val sensorId =
                ensureSensorRow(database, hardwareUid, syncedAt)
                    ?: return false
            val values =
                ContentValues().apply {
                    put("sensor_id", sensorId)
                    put("settings_schema_version", settings.schemaVersion)
                    put("firmware_version", settings.firmwareVersion)
                    putBoolean("autonomous_recording_enabled", settings.sensorParameters.autonomousRecordingEnabled)
                    putBoolean("automatic_shutdown_enabled", settings.sensorParameters.automaticShutdownEnabled)
                    put("automatic_shutdown_seconds", settings.sensorParameters.automaticShutdownSeconds)
                    putBoolean("status_led_enabled", settings.sensorParameters.statusLedEnabled)
                    put("status_led_brightness", settings.sensorParameters.statusLedBrightness)
                    putBoolean("status_buzzer_enabled", settings.sensorParameters.statusBuzzerEnabled)
                    put("status_buzzer_volume", settings.sensorParameters.statusBuzzerVolume)
                    put("samples_per_measurement", settings.acquisitionParameters.samplesPerMeasurement)
                    put("sample_spacing_ms", settings.acquisitionParameters.sampleSpacingMs)
                    putBoolean("discard_extremes", settings.acquisitionParameters.discardExtremes)
                    put("visible_calibration_factor", settings.calibrationSettings.visibleFactor)
                    put("uv_calibration_factor", settings.calibrationSettings.uvFactor)
                    putBoolean("alert_monitoring_enabled", settings.alertMonitoringEnabled)
                    put("alert_repeat_seconds", settings.alertRepeatSeconds)
                    put("alert_session_id", settings.alertSessionId)
                    putBoolean("wifi_enabled", settings.wifiEnabled)
                    putBoolean("bluetooth_enabled", settings.bluetoothEnabled)
                    putBoolean("internet_enabled", settings.internetEnabled)
                    putBoolean("internet_use_primary_wifi", settings.internetUsePrimaryWifi)
                    put("wifi_ssid", settings.wifiSsid)
                    put("internet_relay_host", settings.internetRelayHost)
                    put("internet_relay_port", settings.internetRelayPort)
                    put("last_synced_at", syncedAt.coerceAtLeast(0L))
                    putBoolean(
                        "external_command_enabled",
                        settings.sensorParameters.externalCommandEnabled
                    )
                }
            database.insertWithOnConflict(
                "sensor_settings",
                null,
                values,
                SQLiteDatabase.CONFLICT_REPLACE
            )
            database.delete(
                "sensor_alert_rules",
                "sensor_id = ?",
                arrayOf(sensorId.toString())
            )
            settings.alertRules
                .filter { it.enabled }
                .forEach { rule ->
                    database.insertOrThrow(
                        "sensor_alert_rules",
                        null,
                        ContentValues().apply {
                            put("sensor_id", sensorId)
                            put("metric", rule.metric.name)
                            put("direction", rule.direction.name)
                            put("threshold", rule.threshold)
                        }
                    )
                }
            database.setTransactionSuccessful()
            true
        } finally {
            database.endTransaction()
        }
    }

    fun readSensorSettings(
        hardwareUid: String
    ): UvirSensorSettingsSnapshot? {
        val database = readableDatabase
        createSensorSettingsTables(database)
        val sensorId =
            database.query(
                "sensors",
                arrayOf("id"),
                "hardware_uid = ?",
                arrayOf(normalizedHardwareUid(hardwareUid)),
                null,
                null,
                null,
                "1"
            ).use { cursor ->
                if (cursor.moveToFirst()) cursor.getLong(0) else null
            } ?: return null
        val rules = readSensorAlertRules(database, sensorId)
        return database.query(
            "sensor_settings",
            SENSOR_SETTINGS_COLUMNS,
            "sensor_id = ?",
            arrayOf(sensorId.toString()),
            null,
            null,
            null,
            "1"
        ).use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            UvirSensorSettingsSnapshot(
                schemaVersion = cursor.getInt(0),
                firmwareVersion = cursor.getString(1),
                sensorParameters =
                    SensorParameters(
                        autonomousRecordingEnabled = cursor.getInt(2) != 0,
                        automaticShutdownEnabled = cursor.getInt(3) != 0,
                        automaticShutdownSeconds = cursor.getInt(4),
                        statusLedEnabled = cursor.getInt(5) != 0,
                        statusLedBrightness = cursor.getInt(6),
                        statusBuzzerEnabled = cursor.getInt(7) != 0,
                        statusBuzzerVolume = cursor.getInt(8),
                        externalCommandEnabled = cursor.getInt(25) != 0
                    ),
                acquisitionParameters =
                    AcquisitionParameters(
                        samplesPerMeasurement = cursor.getInt(9),
                        sampleSpacingMs = cursor.getLong(10),
                        discardExtremes = cursor.getInt(11) != 0
                    ),
                calibrationSettings =
                    SensorCalibrationSettings(
                        visibleFactor = cursor.getFloat(12),
                        uvFactor = cursor.getFloat(13)
                    ),
                alertMonitoringEnabled = cursor.getInt(14) != 0,
                alertRepeatSeconds = cursor.getInt(15),
                alertSessionId = cursor.getLong(16),
                alertRules = rules,
                wifiEnabled = cursor.getInt(17) != 0,
                bluetoothEnabled = cursor.getInt(18) != 0,
                internetEnabled = cursor.getInt(19) != 0,
                internetUsePrimaryWifi = cursor.getInt(20) != 0,
                wifiSsid = cursor.getString(21),
                internetRelayHost = cursor.getString(22),
                internetRelayPort = cursor.getInt(23),
                lastSyncedAt = cursor.getLong(24)
            )
        }
    }

    private fun readSensorAlertRules(
        database: SQLiteDatabase,
        sensorId: Long
    ): List<ThresholdAlertRule> =
        database.query(
            "sensor_alert_rules",
            arrayOf("metric", "direction", "threshold"),
            "sensor_id = ?",
            arrayOf(sensorId.toString()),
            null,
            null,
            "rowid"
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    val metric =
                        runCatching {
                            ThresholdAlertMetric.valueOf(cursor.getString(0))
                        }.getOrNull() ?: continue
                    val direction =
                        runCatching {
                            ThresholdAlertDirection.valueOf(cursor.getString(1))
                        }.getOrNull() ?: continue
                    add(
                        ThresholdAlertRule(
                            metric = metric,
                            enabled = true,
                            direction = direction,
                            threshold = cursor.getFloat(2)
                        )
                    )
                }
            }
        }

    private fun ContentValues.putBoolean(
        key: String,
        value: Boolean
    ) {
        put(key, if (value) 1 else 0)
    }

    private fun readSensorProfile(
        database: SQLiteDatabase,
        sensorId: Long
    ): UvirSensorProfile? =
        database.query(
            "sensors",
            arrayOf(
                "id",
                "hardware_uid",
                "display_name",
                "first_seen_at",
                "last_seen_at"
            ),
            "id = ?",
            arrayOf(sensorId.toString()),
            null,
            null,
            null,
            "1"
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                UvirSensorProfile(
                    id = cursor.getLong(0),
                    hardwareUid = cursor.getString(1),
                    displayName = cursor.getString(2),
                    firstSeenAt = cursor.getLong(3),
                    lastSeenAt = cursor.getLong(4)
                )
            } else {
                null
            }
        }

    private companion object {
        val SENSOR_SETTINGS_COLUMNS =
            arrayOf(
                "settings_schema_version",
                "firmware_version",
                "autonomous_recording_enabled",
                "automatic_shutdown_enabled",
                "automatic_shutdown_seconds",
                "status_led_enabled",
                "status_led_brightness",
                "status_buzzer_enabled",
                "status_buzzer_volume",
                "samples_per_measurement",
                "sample_spacing_ms",
                "discard_extremes",
                "visible_calibration_factor",
                "uv_calibration_factor",
                "alert_monitoring_enabled",
                "alert_repeat_seconds",
                "alert_session_id",
                "wifi_enabled",
                "bluetooth_enabled",
                "internet_enabled",
                "internet_use_primary_wifi",
                "wifi_ssid",
                "internet_relay_host",
                "internet_relay_port",
                "last_synced_at",
                "external_command_enabled"
            )
    }

    private fun migrateAcquisitionsToVersion9(
        db: SQLiteDatabase
    ) {
        if (!tableExists(db, "acquisitions")) {
            createAcquisitionsTable(db)
            return
        }

        val columns = tableColumns(db, "acquisitions")
        if (
            "session_id" in columns &&
            "session_sequence" in columns
        ) {
            createAcquisitionsTable(db)
            return
        }

        val sessionIdColumn =
            if ("automatic_session_id" in columns) {
                "automatic_session_id"
            } else {
                "NULL"
            }
        val sessionSequenceColumn =
            if ("automatic_sequence" in columns) {
                "automatic_sequence"
            } else {
                "NULL"
            }
        val sensorDeviceColumn =
            if ("sensor_device_id" in columns) {
                "sensor_device_id"
            } else {
                "NULL"
            }
        val sensorRecordColumn =
            if ("sensor_record_id" in columns) {
                "sensor_record_id"
            } else {
                "NULL"
            }

        db.execSQL("DROP INDEX IF EXISTS idx_acquisitions_sensor_record")
        db.execSQL("ALTER TABLE acquisitions RENAME TO acquisitions_legacy_v8")
        createAcquisitionsTable(db)
        db.execSQL(
            """
            INSERT INTO acquisitions (
                id, timestamp, note, automatic,
                session_id, session_sequence,
                sensor_device_id, sensor_record_id,
                uvc, uvb, uva,
                violetto, blu, verde, giallo, arancione, rosso,
                f8, nir
            )
            SELECT
                id, timestamp, note, automatic,
                $sessionIdColumn, $sessionSequenceColumn,
                $sensorDeviceColumn, $sensorRecordColumn,
                uvc, uvb, uva,
                violetto, blu, verde, giallo, arancione, rosso,
                f8, nir
            FROM acquisitions_legacy_v8
            """.trimIndent()
        )
        db.execSQL("DROP TABLE acquisitions_legacy_v8")
    }

    private fun migrateAlertsToVersion9(
        db: SQLiteDatabase
    ) {
        val sourceTable =
            when {
                tableExists(db, "alerts") -> "alerts"
                tableExists(db, "threshold_alert_log") -> "threshold_alert_log"
                else -> null
            }

        if (sourceTable == null) {
            createAlertsTable(db)
            return
        }

        val columns = tableColumns(db, sourceTable)
        if (
            sourceTable == "alerts" &&
            "session_sequence" in columns
        ) {
            createAlertsTable(db)
            return
        }

        val sessionIdColumn =
            if ("session_id" in columns) "session_id" else "NULL"
        val sessionSequenceColumn =
            if ("session_sequence" in columns) {
                "session_sequence"
            } else {
                "NULL"
            }
        val sensorDeviceColumn =
            if ("sensor_device_id" in columns) {
                "sensor_device_id"
            } else {
                "NULL"
            }
        val sensorRecordColumn =
            if ("sensor_record_id" in columns) {
                "sensor_record_id"
            } else {
                "NULL"
            }

        db.execSQL("DROP INDEX IF EXISTS idx_alerts_sensor_record")
        db.execSQL("ALTER TABLE $sourceTable RENAME TO alerts_legacy_v8")
        createAlertsTable(db)
        db.execSQL(
            """
            INSERT INTO alerts (
                id, timestamp, details,
                session_id, session_sequence,
                sensor_device_id, sensor_record_id
            )
            SELECT
                id, timestamp, details,
                $sessionIdColumn, $sessionSequenceColumn,
                $sensorDeviceColumn, $sensorRecordColumn
            FROM alerts_legacy_v8
            """.trimIndent()
        )
        db.execSQL(
            """
            UPDATE alerts
            SET session_sequence = (
                SELECT COUNT(*)
                FROM alerts AS earlier
                WHERE earlier.session_id = alerts.session_id
                  AND (
                    earlier.timestamp < alerts.timestamp OR
                    (earlier.timestamp = alerts.timestamp AND earlier.id <= alerts.id)
                  )
            )
            WHERE session_id IS NOT NULL
              AND session_sequence IS NULL
            """.trimIndent()
        )
        db.execSQL("DROP TABLE alerts_legacy_v8")
    }

    private fun migrateSessionCounterToVersion9(
        db: SQLiteDatabase
    ) {
        createCountersTable(db)
        val legacyCounter =
            db.rawQuery(
                "SELECT value FROM uvir_counters WHERE name = 'automatic_session_id'",
                null
            ).use { cursor ->
                if (cursor.moveToFirst()) cursor.getLong(0) else 0L
            }
        val largestStoredSession =
            db.rawQuery(
                """
                SELECT MAX(session_id)
                FROM (
                    SELECT session_id FROM acquisitions
                    UNION ALL
                    SELECT session_id FROM alerts
                )
                """.trimIndent(),
                null
            ).use { cursor ->
                if (cursor.moveToFirst() && !cursor.isNull(0)) {
                    cursor.getLong(0)
                } else {
                    0L
                }
            }
        db.execSQL(
            """
            UPDATE uvir_counters
            SET value = MAX(value, ?)
            WHERE name = 'session_id'
            """.trimIndent(),
            arrayOf(maxOf(legacyCounter, largestStoredSession))
        )
        db.execSQL(
            "DELETE FROM uvir_counters WHERE name = 'automatic_session_id'"
        )
    }

    private fun nextSessionId(database: SQLiteDatabase): Long {
        var nextId = 0L
        createCountersTable(database)
        database.execSQL(
            """
            UPDATE uvir_counters
            SET value = value + 1
            WHERE name = 'session_id'
            """.trimIndent()
        )
        database.rawQuery(
            """
            SELECT value
            FROM uvir_counters
            WHERE name = 'session_id'
            """.trimIndent(),
            null
        ).use {
            if (it.moveToFirst()) {
                nextId = it.getLong(0)
            }
        }
        return nextId
    }

    fun nextSessionId(): Long {
        val database = writableDatabase
        var nextId = 0L
        database.beginTransaction()
        try {
            nextId = nextSessionId(database)
            database.setTransactionSuccessful()
        } finally {
            database.endTransaction()
        }

        return nextId
    }

    /**
     * Translates a per-sensor autonomous session token into Uvir's global,
     * progressive session ID. The mapping lives beside the session itself so
     * live delivery, later offline recovery and app restarts all converge on
     * the same row without exposing the large remote token in the UI.
     */
    fun resolveSensorOriginatedAcquisitionSession(
        sensorDeviceId: String,
        sensorSessionId: Long,
        startedAt: Long
    ): Long {
        if (!isSensorOriginatedSessionId(sensorSessionId) ||
            sensorDeviceId.isBlank()
        ) {
            return sensorSessionId
        }

        val database = writableDatabase
        var localSessionId = 0L
        var created = false
        database.beginTransaction()
        try {
            createSessionTables(database)
            val sensorId = ensureSensorRow(
                database,
                sensorDeviceId,
                startedAt.coerceAtLeast(0L)
            ) ?: return 0L
            localSessionId = database.query(
                "acquisition_sessions",
                arrayOf("session_id"),
                "sensor_id = ? AND sensor_origin_session_id = ?",
                arrayOf(sensorId.toString(), sensorSessionId.toString()),
                null,
                null,
                null,
                "1"
            ).use { cursor ->
                if (cursor.moveToFirst()) cursor.getLong(0) else 0L
            }
            if (localSessionId == 0L) {
                localSessionId = nextSessionId(database)
                database.insertOrThrow(
                    "acquisition_sessions",
                    null,
                    ContentValues().apply {
                        put("session_id", localSessionId)
                        put("sensor_id", sensorId)
                        put("sensor_origin_session_id", sensorSessionId)
                        put("note", "")
                        put("external_command", 1)
                        put("started_at", startedAt.coerceAtLeast(0L))
                        putNull("ended_at")
                    }
                )
                created = true
            }
            database.setTransactionSuccessful()
        } finally {
            database.endTransaction()
        }
        if (created) notifyAcquisitionsChanged()
        return localSessionId
    }

    private fun upsertSession(
        database: SQLiteDatabase,
        table: String,
        sessionId: Long,
        note: String,
        startedAt: Long,
        reopen: Boolean,
        sensorDeviceId: String = "",
        externalCommand: Boolean = false
    ) {
        require(
            table == "acquisition_sessions" || table == "alert_sessions"
        )
        if (sessionId <= 0L) return

        createSessionTables(database)
        val sensorId = ensureSensorRow(database, sensorDeviceId, startedAt)
        val existingNote =
            database.query(
                table,
                arrayOf("note"),
                "session_id = ?",
                arrayOf(sessionId.toString()),
                null,
                null,
                null,
                "1"
            ).use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        val normalizedNote = limitUvirNote(note).trim()
        if (existingNote == null) {
            database.insertOrThrow(
                table,
                null,
                ContentValues().apply {
                    put("session_id", sessionId)
                    sensorId?.let { put("sensor_id", it) }
                    put("note", normalizedNote)
                    if (table == "acquisition_sessions") {
                        put("external_command", if (externalCommand) 1 else 0)
                    }
                    put("started_at", startedAt.coerceAtLeast(0L))
                    putNull("ended_at")
                }
            )
        } else {
            val values = ContentValues()
            if (existingNote.isBlank() && normalizedNote.isNotBlank()) {
                values.put("note", normalizedNote)
            }
            if (reopen) {
                values.putNull("ended_at")
            }
            sensorId?.let {
                values.put("sensor_id", it)
            }
            if (table == "acquisition_sessions" && externalCommand) {
                values.put("external_command", 1)
            }
            if (values.size() > 0) {
                database.update(
                    table,
                    values,
                    "session_id = ?",
                    arrayOf(sessionId.toString())
                )
            }
        }
    }

    fun startAcquisitionSession(
        sessionId: Long,
        note: String,
        startedAt: Long = System.currentTimeMillis(),
        sensorDeviceId: String = "",
        externalCommand: Boolean = false
    ) {
        upsertSession(
            database = writableDatabase,
            table = "acquisition_sessions",
            sessionId = sessionId,
            note = note,
            startedAt = startedAt,
            reopen = true,
            sensorDeviceId = sensorDeviceId,
            externalCommand = externalCommand
        )
    }

    fun ensureAcquisitionSession(
        sessionId: Long,
        note: String,
        startedAt: Long,
        sensorDeviceId: String = "",
        externalCommand: Boolean = false
    ) {
        upsertSession(
            database = writableDatabase,
            table = "acquisition_sessions",
            sessionId = sessionId,
            note = note,
            startedAt = startedAt,
            reopen = false,
            sensorDeviceId = sensorDeviceId,
            externalCommand = externalCommand
        )
    }

    fun finishAcquisitionSession(
        sessionId: Long,
        endedAt: Long = System.currentTimeMillis()
    ) {
        if (sessionId <= 0L) return
        writableDatabase.update(
            "acquisition_sessions",
            ContentValues().apply {
                put("ended_at", endedAt.coerceAtLeast(0L))
            },
            "session_id = ? AND ended_at IS NULL",
            arrayOf(sessionId.toString())
        )
    }

    /** A lost RAM job has no shutdown timestamp: use only the last recorded event. */
    fun finishInterruptedAcquisitionSession(sessionId: Long, sensorDeviceId: String): Boolean {
        if (sessionId <= 0L || sensorDeviceId.isBlank()) return false
        val closed = writableDatabase.compileStatement(
            """
            UPDATE acquisition_sessions
            SET ended_at = COALESCE(
                (SELECT MAX(timestamp) FROM acquisitions
                 WHERE session_id = acquisition_sessions.session_id
                   AND sensor_id = acquisition_sessions.sensor_id),
                started_at
            )
            WHERE session_id = ? AND sensor_id =
                (SELECT id FROM sensors WHERE hardware_uid = ?)
            """.trimIndent()
        ).use { statement ->
            statement.bindLong(1, sessionId)
            statement.bindString(2, normalizeSensorDeviceId(sensorDeviceId))
            statement.executeUpdateDelete() > 0
        }
        if (closed) notifyAcquisitionsChanged()
        return closed
    }

    fun startAlertSession(
        sessionId: Long,
        note: String,
        startedAt: Long = System.currentTimeMillis(),
        sensorDeviceId: String = ""
    ) {
        upsertSession(
            database = writableDatabase,
            table = "alert_sessions",
            sessionId = sessionId,
            note = note,
            startedAt = startedAt,
            reopen = true,
            sensorDeviceId = sensorDeviceId
        )
    }

    fun readAcquisitionSessionNote(sessionId: Long): String =
        readSessionNote("acquisition_sessions", sessionId)

    fun updateAcquisitionNote(recordId: Long, note: String): Boolean {
        if (recordId <= 0L) return false
        val database = writableDatabase
        val recordContext =
            database.query(
                "acquisitions",
                arrayOf("automatic", "session_id"),
                "id = ?",
                arrayOf(recordId.toString()),
                null,
                null,
                null,
                "1"
            ).use { cursor ->
                if (!cursor.moveToFirst()) {
                    null
                } else {
                    val automatic = cursor.getInt(0) != 0
                    val sessionId = if (cursor.isNull(1)) null else cursor.getLong(1)
                    automatic to sessionId
                }
            } ?: return false

        val updated =
            if (recordContext.first && recordContext.second != null) {
                updateAcquisitionSessionNote(requireNotNull(recordContext.second), note)
            } else {
                database.update(
                    "acquisitions",
                    ContentValues().apply { put("note", limitUvirNote(note).trim()) },
                    "id = ?",
                    arrayOf(recordId.toString())
                ) > 0
            }
        if (updated && !(recordContext.first && recordContext.second != null)) {
            notifyAcquisitionsChanged()
        }
        return updated
    }

    fun updateAcquisitionSessionNote(sessionId: Long, note: String): Boolean {
        if (sessionId <= 0L) return false
        val database = writableDatabase
        val normalizedNote = limitUvirNote(note).trim()
        val automaticSession =
            database.rawQuery(
                "SELECT EXISTS(SELECT 1 FROM acquisitions WHERE session_id = ? AND automatic = 1)",
                arrayOf(sessionId.toString())
            ).use { cursor ->
                cursor.moveToFirst() && cursor.getInt(0) != 0
            }
        var updated = false
        database.beginTransaction()
        try {
            updated =
                if (automaticSession) {
                    createSessionTables(database)
                    database.update(
                        "acquisition_sessions",
                        ContentValues().apply { put("note", normalizedNote) },
                        "session_id = ?",
                        arrayOf(sessionId.toString())
                    ) > 0
                } else {
                    database.update(
                        "acquisitions",
                        ContentValues().apply { put("note", normalizedNote) },
                        "session_id = ?",
                        arrayOf(sessionId.toString())
                    ) > 0
                }
            if (updated && automaticSession) {
                database.update(
                    "acquisitions",
                    ContentValues().apply { put("note", "") },
                    "session_id = ?",
                    arrayOf(sessionId.toString())
                )
            }
            database.setTransactionSuccessful()
        } finally {
            database.endTransaction()
        }
        if (updated) notifyAcquisitionsChanged()
        return updated
    }

    fun readAcquisitionSessionVariantsPerPosition(sessionId: Long): Int {
        if (sessionId <= 0L) return 1
        createSessionTables(readableDatabase)
        return readableDatabase.query(
            "acquisition_sessions",
            arrayOf("variants_per_position"),
            "session_id = ?",
            arrayOf(sessionId.toString()),
            null,
            null,
            null,
            "1"
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.getInt(0).coerceAtLeast(1) else 1
        }
    }

    fun updateAcquisitionSessionVariantsPerPosition(
        sessionId: Long,
        variantsPerPosition: Int
    ): Boolean {
        if (sessionId <= 0L) return false
        val database = writableDatabase
        createSessionTables(database)
        val normalizedVariants = variantsPerPosition.coerceAtLeast(1)
        val acquisitionCount =
            database.rawQuery(
                "SELECT COUNT(*) FROM acquisitions WHERE session_id = ?",
                arrayOf(sessionId.toString())
            ).use { cursor ->
                if (cursor.moveToFirst()) cursor.getInt(0) else 0
            }
        if (
            normalizedVariants > 1 &&
            (acquisitionCount <= 0 || acquisitionCount % normalizedVariants != 0)
        ) {
            return false
        }

        var updated = false
        database.beginTransaction()
        try {
            updated = database.update(
                "acquisition_sessions",
                ContentValues().apply {
                    put("variants_per_position", normalizedVariants)
                },
                "session_id = ?",
                arrayOf(sessionId.toString())
            ) > 0
            if (updated) {
                if (normalizedVariants > 1) {
                    database.execSQL(
                        """
                        UPDATE acquisitions
                        SET
                            variant_index = ((session_sequence - 1) % ?) + 1,
                            position_index = CAST((session_sequence - 1) / ? AS INTEGER) + 1
                        WHERE session_id = ? AND session_sequence IS NOT NULL
                        """.trimIndent(),
                        arrayOf<Any>(
                            normalizedVariants,
                            normalizedVariants,
                            sessionId
                        )
                    )
                } else {
                    database.execSQL(
                        """
                        UPDATE acquisitions
                        SET variant_index = NULL, position_index = NULL
                        WHERE session_id = ?
                        """.trimIndent(),
                        arrayOf(sessionId)
                    )
                }
            }
            database.setTransactionSuccessful()
        } finally {
            database.endTransaction()
        }
        if (updated) notifyAcquisitionsChanged()
        return updated
    }

    private fun acquisitionVariantMetadata(
        database: SQLiteDatabase,
        sessionId: Long?,
        sessionSequence: Int?
    ): Pair<Int?, Int?> {
        if (sessionId == null || sessionSequence == null || sessionSequence <= 0) {
            return null to null
        }
        val variantsPerPosition =
            database.query(
                "acquisition_sessions",
                arrayOf("variants_per_position"),
                "session_id = ?",
                arrayOf(sessionId.toString()),
                null,
                null,
                null,
                "1"
            ).use { cursor ->
                if (cursor.moveToFirst()) cursor.getInt(0).coerceAtLeast(1) else 1
            }
        if (variantsPerPosition <= 1) return null to null

        val variantIndex = ((sessionSequence - 1) % variantsPerPosition) + 1
        val positionIndex = ((sessionSequence - 1) / variantsPerPosition) + 1
        return positionIndex to variantIndex
    }

    fun readAlertSessionNote(sessionId: Long): String =
        readSessionNote("alert_sessions", sessionId)

    fun updateAlertNote(alertId: Long, note: String): Boolean {
        if (alertId <= 0L) return false
        val sessionId =
            readableDatabase.query(
                "alerts",
                arrayOf("session_id"),
                "id = ?",
                arrayOf(alertId.toString()),
                null,
                null,
                null,
                "1"
            ).use { cursor ->
                if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else null
            } ?: return false
        return updateAlertSessionNote(sessionId, note)
    }

    fun updateAlertSessionNote(sessionId: Long, note: String): Boolean {
        if (sessionId <= 0L) return false
        createSessionTables(writableDatabase)
        val updated =
            writableDatabase.update(
                "alert_sessions",
                ContentValues().apply { put("note", limitUvirNote(note).trim()) },
                "session_id = ?",
                arrayOf(sessionId.toString())
            ) > 0
        if (updated) notifyAlertsChanged()
        return updated
    }

    private fun readSessionNote(table: String, sessionId: Long): String {
        if (sessionId <= 0L) return ""
        return readableDatabase.query(
            table,
            arrayOf("note"),
            "session_id = ?",
            arrayOf(sessionId.toString()),
            null,
            null,
            null,
            "1"
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0).orEmpty() else ""
        }
    }

    fun ensureAlertSession(
        sessionId: Long,
        note: String = "",
        startedAt: Long,
        sensorDeviceId: String = ""
    ) {
        upsertSession(
            database = writableDatabase,
            table = "alert_sessions",
            sessionId = sessionId,
            note = note,
            startedAt = startedAt,
            reopen = false,
            sensorDeviceId = sensorDeviceId
        )
    }

    fun finishAlertSession(
        sessionId: Long,
        endedAt: Long = System.currentTimeMillis()
    ) {
        if (sessionId <= 0L) return
        writableDatabase.update(
            "alert_sessions",
            ContentValues().apply {
                put("ended_at", endedAt.coerceAtLeast(0L))
            },
            "session_id = ? AND ended_at IS NULL",
            arrayOf(sessionId.toString())
        )
    }

    fun currentSessionCounter(): Long {
        val database = readableDatabase
        createCountersTable(database)
        return database.rawQuery(
            """
            SELECT value
            FROM uvir_counters
            WHERE name = 'session_id'
            """.trimIndent(),
            null
        ).use {
            if (it.moveToFirst()) {
                it.getLong(0)
            } else {
                0L
            }
        }
    }

    private fun updateSessionCounterFromStoredData(
        database: SQLiteDatabase
    ) {
        createCountersTable(database)
        val largestStoredSession =
            database.rawQuery(
                """
                SELECT MAX(session_id)
                FROM (
                    SELECT session_id FROM acquisitions
                    UNION ALL
                    SELECT session_id FROM alerts
                )
                """.trimIndent(),
                null
            ).use { cursor ->
                if (cursor.moveToFirst() && !cursor.isNull(0)) {
                    cursor.getLong(0)
                } else {
                    0L
                }
            }
        database.execSQL(
            """
            UPDATE uvir_counters
            SET value = ?
            WHERE name = 'session_id'
            """.trimIndent(),
            arrayOf(largestStoredSession)
        )
    }

    private fun acquisitionCounter(
        database: SQLiteDatabase
    ): Long {
        return database.rawQuery(
            """
            SELECT seq
            FROM sqlite_sequence
            WHERE name = 'acquisitions'
            """.trimIndent(),
            null
        ).use {
            if (it.moveToFirst()) {
                it.getLong(0)
            } else {
                0L
            }
        }
    }

    fun currentAcquisitionCounter(): Long =
        acquisitionCounter(readableDatabase)

    fun nextSequenceForSession(
        sessionId: Long
    ): Int =
        readableDatabase.rawQuery(
            """
            SELECT COALESCE(MAX(session_sequence), 0) + 1
            FROM acquisitions
            WHERE session_id = ?
            """.trimIndent(),
            arrayOf(sessionId.toString())
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                cursor.getInt(0).coerceAtLeast(1)
            } else {
                1
            }
        }

    fun acquisitionCountForSession(
        sessionId: Long
    ): Int =
        readableDatabase.rawQuery(
            """
            SELECT COUNT(*)
            FROM acquisitions
            WHERE session_id = ?
            """.trimIndent(),
            arrayOf(sessionId.toString())
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                cursor.getInt(0).coerceAtLeast(0)
            } else {
                0
            }
        }

    fun saveAcquisition(
        sample: SensorSample,
        note: String,
        automatic: Boolean = false,
        sessionId: Long? = null,
        sessionSequence: Int? = null,
        sensorDeviceId: String = "",
        externalCommand: Boolean = false
    ): Long {

        val normalizedNote = limitUvirNote(note).trim()
        val database = writableDatabase
        val sensorId = ensureSensorRow(database, sensorDeviceId)
        val (positionIndex, variantIndex) =
            acquisitionVariantMetadata(database, sessionId, sessionSequence)
        val values = ContentValues().apply {
            put("timestamp", System.currentTimeMillis())
            put("note", normalizedNote)
            put("automatic", if (automatic) 1 else 0)
            put("external_command", if (externalCommand) 1 else 0)

            if (sessionId != null) {
                put(
                    "session_id",
                    sessionId
                )
            }

            if (sessionSequence != null) {
                put(
                    "session_sequence",
                    sessionSequence
                )
            }
            positionIndex?.let { put("position_index", it) }
            variantIndex?.let { put("variant_index", it) }

            sensorId?.let { put("sensor_id", it) }

            put("uvc", sample.uvc)
            put("uvb", sample.uvb)
            put("uva", sample.uva)

            put("violetto", sample.violetto)
            put("blu", sample.blu)
            put("verde", sample.verde)
            put("giallo", sample.giallo)
            put("arancione", sample.arancione)
            put("rosso", sample.rosso)

            put("f8", sample.f8)
            put("nir", sample.nir)
            put("quality_flags", sample.qualityFlags)
        }

        val result = database.insert(
            "acquisitions",
            null,
            values
        )
        if (result != -1L) {
            if (sessionId != null && sensorId != null) {
                database.update(
                    "acquisition_sessions",
                    ContentValues().apply { put("sensor_id", sensorId) },
                    "session_id = ? AND sensor_id IS NULL",
                    arrayOf(sessionId.toString())
                )
            }
            notifyAcquisitionsChanged()
        }
        return result
    }

    fun saveRecoveredAcquisition(
        timestamp: Long,
        sample: SensorSample,
        note: String,
        sessionId: Long,
        sequence: Int,
        sensorDeviceId: String,
        sensorRecordId: Long,
        externalCommand: Boolean? = null
    ): Boolean {
        val database = writableDatabase
        var stored = false
        var inserted = false
        database.beginTransaction()
        try {
            createCountersTable(database)
            val sensorId = ensureSensorRow(database, sensorDeviceId, timestamp)
            val recoveredSessionId = sessionId.takeIf { it > 0L }
            recoveredSessionId?.let {
                upsertSession(
                    database = database,
                    table = "acquisition_sessions",
                    sessionId = it,
                    note = note,
                    startedAt = timestamp,
                    reopen = false,
                    sensorDeviceId = sensorDeviceId,
                    externalCommand = externalCommand == true
                )
            }
            val sessionUsesExternalCommand =
                recoveredSessionId?.let { recoveredId ->
                    database.query(
                        "acquisition_sessions",
                        arrayOf("external_command"),
                        "session_id = ?",
                        arrayOf(recoveredId.toString()),
                        null,
                        null,
                        null,
                        "1"
                    ).use { cursor ->
                        cursor.moveToFirst() && cursor.getInt(0) != 0
                    }
                } ?: true
            val recoveredThroughExternalCommand =
                recoveredSessionId == null ||
                    externalCommand == true ||
                    sessionUsesExternalCommand
            val (positionIndex, variantIndex) =
                acquisitionVariantMetadata(database, recoveredSessionId, sequence)
            val result = database.insertWithOnConflict(
                "acquisitions",
                null,
                ContentValues().apply {
                    put("timestamp", timestamp)
                    // Automatic-session notes live once in acquisition_sessions.
                    put("note", if (recoveredSessionId != null) "" else note)
                    put("automatic", if (recoveredSessionId != null) 1 else 0)
                    put("external_command", if (recoveredThroughExternalCommand) 1 else 0)
                    if (recoveredSessionId != null) {
                        put("session_id", recoveredSessionId)
                        put("session_sequence", sequence)
                    }
                    positionIndex?.let { put("position_index", it) }
                    variantIndex?.let { put("variant_index", it) }
                    sensorId?.let { put("sensor_id", it) }
                    put("sensor_record_id", sensorRecordId)
                    put("uvc", sample.uvc)
                    put("uvb", sample.uvb)
                    put("uva", sample.uva)
                    put("violetto", sample.violetto)
                    put("blu", sample.blu)
                    put("verde", sample.verde)
                    put("giallo", sample.giallo)
                    put("arancione", sample.arancione)
                    put("rosso", sample.rosso)
                    put("f8", sample.f8)
                    put("nir", sample.nir)
                    put("quality_flags", sample.qualityFlags)
                },
                SQLiteDatabase.CONFLICT_IGNORE
            )
            inserted = result != -1L
            stored =
                inserted ||
                    sensorRecordExists(
                        database = database,
                        table = "acquisitions",
                        sensorDeviceId = sensorDeviceId,
                        sensorRecordId = sensorRecordId
                    )
            if (stored && recoveredSessionId != null && sensorId != null) {
                // Recovery may arrive after an interrupted session was closed.
                // Extend its last-known end, without reopening its execution state.
                database.execSQL(
                    """
                    UPDATE acquisition_sessions SET ended_at = ?
                    WHERE session_id = ? AND sensor_id = ?
                      AND ended_at IS NOT NULL AND ended_at < ?
                    """.trimIndent(),
                    arrayOf(timestamp, recoveredSessionId, sensorId, timestamp)
                )
            }
            recoveredSessionId?.let {
                database.execSQL(
                    """
                    UPDATE uvir_counters
                    SET value = MAX(value, ?)
                    WHERE name = 'session_id'
                    """.trimIndent(),
                    arrayOf(it)
                )
            }
            database.setTransactionSuccessful()
        } finally {
            database.endTransaction()
        }
        if (inserted) {
            notifyAcquisitionsChanged()
        }
        return stored
    }

    fun hasSensorAcquisition(
        sensorDeviceId: String,
        sensorRecordId: Long
    ): Boolean =
        sensorRecordExists(
            database = readableDatabase,
            table = "acquisitions",
            sensorDeviceId = sensorDeviceId,
            sensorRecordId = sensorRecordId
        )

    fun readSavedRecords(
        sensorId: Long? = null
    ): List<SavedRecordSummary> {

        val list = mutableListOf<SavedRecordSummary>()

        val cursor = readableDatabase.rawQuery(
            """
            SELECT
                acquisition.id,
                acquisition.timestamp,
                CASE
                    WHEN acquisition.automatic = 1
                    THEN COALESCE(NULLIF(session.note, ''), acquisition.note)
                    ELSE acquisition.note
                END AS resolved_note,
                acquisition.automatic,
                CASE
                    WHEN acquisition.external_command = 1
                      OR COALESCE(session.external_command, 0) = 1
                    THEN 1 ELSE 0
                END AS resolved_external_command,
                acquisition.session_id,
                acquisition.session_sequence,
                acquisition.position_index,
                acquisition.variant_index,
                acquisition.sensor_id
            FROM acquisitions AS acquisition
            LEFT JOIN acquisition_sessions AS session
              ON session.session_id = acquisition.session_id
            ${if (sensorId != null) "WHERE acquisition.sensor_id = ?" else ""}
            ORDER BY acquisition.timestamp DESC
            """.trimIndent(),
            sensorId?.let { arrayOf(it.toString()) }
        )

        cursor.use {
            val idIndex = it.getColumnIndexOrThrow("id")
            val timestampIndex = it.getColumnIndexOrThrow("timestamp")
            val noteIndex = it.getColumnIndexOrThrow("resolved_note")
            val automaticIndex = it.getColumnIndexOrThrow("automatic")
            val externalCommandIndex =
                it.getColumnIndexOrThrow("resolved_external_command")
            val automaticSessionIndex =
                it.getColumnIndexOrThrow(
                    "session_id"
                )
            val sessionSequenceIndex =
                it.getColumnIndexOrThrow(
                    "session_sequence"
                )
            val positionIndexIndex =
                it.getColumnIndexOrThrow("position_index")
            val variantIndexIndex =
                it.getColumnIndexOrThrow("variant_index")
            val sensorIdIndex =
                it.getColumnIndexOrThrow("sensor_id")

            while (it.moveToNext()) {
                list.add(
                    SavedRecordSummary(
                        id = it.getLong(idIndex),
                        timestamp = it.getLong(timestampIndex),
                        note = it.getString(noteIndex),
                        automatic = it.getInt(automaticIndex) != 0,
                        externalCommand = it.getInt(externalCommandIndex) != 0,
                        sessionId =
                            if (
                                it.isNull(
                                    automaticSessionIndex
                                )
                            ) {
                                null
                            } else {
                                it.getLong(
                                    automaticSessionIndex
                                )
                            },
                        sessionSequence =
                            if (
                                it.isNull(
                                    sessionSequenceIndex
                                )
                            ) {
                                null
                            } else {
                                it.getInt(
                                    sessionSequenceIndex
                                )
                            },
                        positionIndex =
                            if (it.isNull(positionIndexIndex)) {
                                null
                            } else {
                                it.getInt(positionIndexIndex)
                            },
                        variantIndex =
                            if (it.isNull(variantIndexIndex)) {
                                null
                            } else {
                                it.getInt(variantIndexIndex)
                            },
                        sensorId =
                            if (it.isNull(sensorIdIndex)) {
                                null
                            } else {
                                it.getLong(sensorIdIndex)
                            }
                    )
                )
            }
        }

        return list
    }

    fun readRecord(id: Long): SavedRecordDetail? {

        val cursor = readableDatabase.rawQuery(
            """
            SELECT
                acquisition.*,
                CASE
                    WHEN acquisition.automatic = 1
                    THEN COALESCE(NULLIF(session.note, ''), acquisition.note)
                    ELSE acquisition.note
                END AS resolved_note,
                CASE
                    WHEN acquisition.external_command = 1
                      OR COALESCE(session.external_command, 0) = 1
                    THEN 1 ELSE 0
                END AS resolved_external_command,
                COALESCE(NULLIF(sensor.display_name, ''), sensor.hardware_uid, '—') AS sensor_display_name
            FROM acquisitions AS acquisition
            LEFT JOIN acquisition_sessions AS session
              ON session.session_id = acquisition.session_id
            LEFT JOIN sensors AS sensor
              ON sensor.id = acquisition.sensor_id
            WHERE acquisition.id = ?
            """.trimIndent(),
            arrayOf(id.toString())
        )

        cursor.use {
            if (!it.moveToFirst()) {
                return null
            }

            fun d(name: String): Double =
                it.getDouble(it.getColumnIndexOrThrow(name))

            return SavedRecordDetail(
                id = id,
                timestamp = it.getLong(
                    it.getColumnIndexOrThrow("timestamp")
                ),
                note = it.getString(
                    it.getColumnIndexOrThrow("resolved_note")
                ),
                automatic = it.getInt(
                    it.getColumnIndexOrThrow("automatic")
                ) != 0,
                externalCommand = it.getInt(
                    it.getColumnIndexOrThrow("resolved_external_command")
                ) != 0,
                sample = SensorSample(
                    uvc = d("uvc"),
                    uvb = d("uvb"),
                    uva = d("uva"),

                    violetto = d("violetto"),
                    blu = d("blu"),
                    verde = d("verde"),
                    giallo = d("giallo"),
                    arancione = d("arancione"),
                    rosso = d("rosso"),

                    f8 = d("f8"),
                    nir = d("nir"),
                    qualityFlags = d("quality_flags").toInt()
                ),
                sessionId =
                    it.getColumnIndexOrThrow(
                        "session_id"
                    ).let { index ->
                        if (it.isNull(index)) {
                            null
                        } else {
                            it.getLong(index)
                        }
                    },
                sessionSequence =
                    it.getColumnIndexOrThrow(
                        "session_sequence"
                    ).let { index ->
                        if (it.isNull(index)) {
                            null
                        } else {
                            it.getInt(index)
                        }
                    },
                positionIndex =
                    it.getColumnIndexOrThrow("position_index").let { index ->
                        if (it.isNull(index)) null else it.getInt(index)
                    },
                variantIndex =
                    it.getColumnIndexOrThrow("variant_index").let { index ->
                        if (it.isNull(index)) null else it.getInt(index)
                    },
                sensorId =
                    it.getColumnIndexOrThrow("sensor_id").let { index ->
                        if (it.isNull(index)) null else it.getLong(index)
                    },
                sensorDisplayName = it.getString(it.getColumnIndexOrThrow("sensor_display_name"))
            )
        }
    }

    fun deleteRecord(id: Long): Int {
        return writableDatabase.delete(
            "acquisitions",
            "id = ?",
            arrayOf(id.toString())
        )
    }

    fun deleteRecords(ids: Collection<Long>): Int {
        if (ids.isEmpty()) {
            return 0
        }

        val placeholders =
            ids.joinToString(",") { "?" }

        return writableDatabase.delete(
            "acquisitions",
            "id IN ($placeholders)",
            ids.map { it.toString() }
                .toTypedArray()
        )
    }

    fun deleteAllAcquisitions(): Int {
        val database = writableDatabase
        var deletedRows = 0

        database.beginTransaction()
        try {
            deletedRows = database.delete(
                "acquisitions",
                null,
                null
            )
            database.delete(
                "acquisition_sessions",
                null,
                null
            )

            database.setTransactionSuccessful()
        } finally {
            database.endTransaction()
        }

        clearRememberedManualSession(
            appContext.getSharedPreferences(
                PREFS_NAME,
                Context.MODE_PRIVATE
            )
        )

        return deletedRows
    }

    fun resetAllCounters(): Int {
        clearCachedSensorOperationalContexts(
            appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        )
        val database = writableDatabase
        var deletedRows = 0

        database.beginTransaction()
        try {
            deletedRows = database.delete(
                "acquisitions",
                null,
                null
            )
            database.delete("acquisition_sessions", null, null)
            database.execSQL(
                "DELETE FROM sqlite_sequence " +
                    "WHERE name IN ('acquisitions', 'alerts')"
            )
            deletedRows += database.delete(
                "alerts",
                null,
                null
            )
            database.delete("alert_sessions", null, null)
            createCountersTable(database)
            database.execSQL(
                """
                UPDATE uvir_counters
                SET value = 0
                WHERE name = 'session_id'
                """.trimIndent()
            )
            database.setTransactionSuccessful()
        } finally {
            database.endTransaction()
        }


        clearRememberedManualSession(
            appContext.getSharedPreferences(
                PREFS_NAME,
                Context.MODE_PRIVATE
            )
        )

        return deletedRows
    }

    fun deleteAllSensorProfiles(): Int {
        val database = writableDatabase
        var deletedRows = 0
        database.beginTransaction()
        try {
            createSensorSettingsTables(database)
            database.delete("sensor_alert_rules", null, null)
            database.delete("sensor_settings", null, null)
            deletedRows = database.delete("sensors", null, null)
            database.setTransactionSuccessful()
        } finally {
            database.endTransaction()
        }
        if (deletedRows > 0) {
            notifySensorProfilesChanged()
        }
        return deletedRows
    }

    fun resetAcquisitionCounters(): Int {
        val database = writableDatabase
        var deletedRows = 0

        database.beginTransaction()
        try {
            deletedRows = database.delete(
                "acquisitions",
                null,
                null
            )
            database.delete("acquisition_sessions", null, null)
            database.execSQL(
                "DELETE FROM sqlite_sequence " +
                    "WHERE name = 'acquisitions'"
            )
            updateSessionCounterFromStoredData(database)
            database.setTransactionSuccessful()
        } finally {
            database.endTransaction()
        }

        clearRememberedManualSession(
            appContext.getSharedPreferences(
                PREFS_NAME,
                Context.MODE_PRIVATE
            )
        )

        return deletedRows
    }

    fun resetAlertCounters(): Int {
        val database = writableDatabase
        var deletedRows = 0

        database.beginTransaction()
        try {
            deletedRows = database.delete(
                "alerts",
                null,
                null
            )
            database.delete("alert_sessions", null, null)
            database.execSQL(
                "DELETE FROM sqlite_sequence " +
                    "WHERE name = 'alerts'"
            )
            updateSessionCounterFromStoredData(database)
            database.setTransactionSuccessful()
        } finally {
            database.endTransaction()
        }

        return deletedRows
    }

    fun readAllRecords():
            List<SavedRecordDetail> {
        return readSavedRecords()
            .mapNotNull {
                readRecord(it.id)
            }
    }

    fun readSessionRecords(
        sessionId: Long
    ): List<SavedRecordDetail> {
        return readSavedRecords()
            .asSequence()
            .filter {
                it.sessionId == sessionId
            }
            .mapNotNull {
                readRecord(it.id)
            }
            .sortedBy {
                it.timestamp
            }
            .toList()
    }

    fun insertThresholdAlertLog(
        violations: List<ThresholdAlertViolation>,
        timestamp: Long = System.currentTimeMillis(),
        sessionId: Long? = null,
        sensorDeviceId: String = "",
        qualityFlags: Int = 0
    ): Long {
        if (violations.isEmpty()) {
            return -1L
        }

        val details =
            violations.joinToString(";") { violation ->
                listOf(
                    violation.rule.metric.name,
                    violation.value.toString(),
                    violation.rule.direction.name,
                    violation.rule.threshold.toString()
                ).joinToString("|")
            }

        val database = writableDatabase
        val sensorId = ensureSensorRow(database, sensorDeviceId, timestamp)
        val normalizedSessionId = sessionId?.takeIf { it > 0L }
        var result = -1L
        database.beginTransaction()
        try {
            normalizedSessionId?.let {
                upsertSession(
                    database = database,
                    table = "alert_sessions",
                    sessionId = it,
                    note = "",
                    startedAt = timestamp,
                    reopen = false,
                    sensorDeviceId = sensorDeviceId
                )
            }
            result = database.insert(
                "alerts",
                null,
                ContentValues().apply {
                    put("timestamp", timestamp)
                    put("details", details)
                    put("quality_flags", qualityFlags)
                    sensorId?.let { put("sensor_id", it) }
                    normalizedSessionId?.let {
                        put("session_id", it)
                        put(
                            "session_sequence",
                            nextAlertSequenceForSession(database, it)
                        )
                    }
                }
            )
            database.setTransactionSuccessful()
        } finally {
            database.endTransaction()
        }
        if (result != -1L) {
            notifyAlertsChanged()
        }
        return result
    }

    fun insertRecoveredThresholdAlert(
        timestamp: Long,
        details: String,
        sensorDeviceId: String,
        sensorRecordId: Long,
        sessionId: Long? = null,
        qualityFlags: Int = 0
    ): Boolean {
        if (details.isBlank()) return false
        val database = writableDatabase
        val normalizedSessionId = sessionId?.takeIf { it > 0L }
        var result = -1L
        database.beginTransaction()
        try {
            val sensorId = ensureSensorRow(database, sensorDeviceId, timestamp)
            normalizedSessionId?.let {
                upsertSession(
                    database = database,
                    table = "alert_sessions",
                    sessionId = it,
                    note = "",
                    startedAt = timestamp,
                    reopen = false,
                    sensorDeviceId = sensorDeviceId
                )
            }
            result = database.insertWithOnConflict(
                "alerts",
                null,
                ContentValues().apply {
                    put("timestamp", timestamp)
                    put("details", details)
                    put("quality_flags", qualityFlags)
                    sensorId?.let { put("sensor_id", it) }
                    put("sensor_record_id", sensorRecordId)
                    normalizedSessionId?.let {
                        put("session_id", it)
                        put(
                            "session_sequence",
                            nextAlertSequenceForSession(database, it)
                        )
                    }
                },
                SQLiteDatabase.CONFLICT_IGNORE
            )
            database.setTransactionSuccessful()
        } finally {
            database.endTransaction()
        }
        val inserted = result != -1L
        val stored = inserted ||
            sensorRecordExists(
                database = database,
                table = "alerts",
                sensorDeviceId = sensorDeviceId,
                sensorRecordId = sensorRecordId
            )
        if (inserted) {
            notifyAlertsChanged()
        }
        return stored
    }

    private fun nextAlertSequenceForSession(
        database: SQLiteDatabase,
        sessionId: Long
    ): Int =
        database.rawQuery(
            """
            SELECT COALESCE(MAX(session_sequence), 0) + 1
            FROM alerts
            WHERE session_id = ?
            """.trimIndent(),
            arrayOf(sessionId.toString())
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                cursor.getInt(0).coerceAtLeast(1)
            } else {
                1
            }
        }

    private fun sensorRecordExists(
        database: SQLiteDatabase,
        table: String,
        sensorDeviceId: String,
        sensorRecordId: Long
    ): Boolean {
        val normalizedUid = normalizedHardwareUid(sensorDeviceId)
        if (normalizedUid.isBlank()) return false
        val sensorId =
            database.query(
                "sensors",
                arrayOf("id"),
                "hardware_uid = ?",
                arrayOf(normalizedUid),
                null,
                null,
                null,
                "1"
            ).use { cursor ->
                if (cursor.moveToFirst()) cursor.getLong(0) else null
            } ?: return false
        return database.query(
            table,
            arrayOf("sensor_record_id"),
            "sensor_id = ? AND sensor_record_id = ?",
            arrayOf(sensorId.toString(), sensorRecordId.toString()),
            null,
            null,
            null,
            "1"
        ).use { cursor ->
            cursor.moveToFirst()
        }
    }

    fun readThresholdAlertLog(
        limit: Int = 10_000,
        sensorId: Long? = null
    ): List<ThresholdAlertLogEntry> {
        val entries =
            mutableListOf<ThresholdAlertLogEntry>()

        readableDatabase.rawQuery(
            """
            SELECT
                alert.id,
                alert.timestamp,
                alert.details,
                alert.session_id,
                alert.session_sequence,
                COALESCE(session.note, '') AS session_note,
                alert.sensor_id,
                COALESCE(NULLIF(sensor.display_name, ''), sensor.hardware_uid, '—') AS sensor_display_name
                , alert.quality_flags
            FROM alerts AS alert
            LEFT JOIN alert_sessions AS session
              ON session.session_id = alert.session_id
            LEFT JOIN sensors AS sensor
              ON sensor.id = alert.sensor_id
            ${if (sensorId != null) "WHERE alert.sensor_id = ?" else ""}
            ORDER BY alert.timestamp DESC, alert.id DESC
            LIMIT ?
            """.trimIndent(),
            buildList {
                sensorId?.let { add(it.toString()) }
                add(limit.coerceIn(1, 100_000).toString())
            }.toTypedArray()
        ).use { cursor ->
            while (cursor.moveToNext()) {
                entries +=
                    ThresholdAlertLogEntry(
                        id = cursor.getLong(0),
                        timestamp = cursor.getLong(1),
                        details = cursor.getString(2),
                        sessionId =
                            if (cursor.isNull(3)) {
                                null
                            } else {
                                cursor.getLong(3).takeIf { it > 0L }
                            },
                        sessionSequence =
                            if (cursor.isNull(4)) {
                                null
                            } else {
                                cursor.getInt(4).takeIf { it > 0 }
                            },
                        note = cursor.getString(5).orEmpty(),
                        sensorId =
                            if (cursor.isNull(6)) null else cursor.getLong(6),
                        sensorDisplayName = cursor.getString(7),
                        qualityFlags = cursor.getInt(8)
                    )
            }
        }

        return entries
    }

    fun deleteAllThresholdAlertLogs(): Int {
        val database = writableDatabase
        var deleted = 0
        database.beginTransaction()
        try {
            deleted = database.delete("alerts", null, null)
            database.delete("alert_sessions", null, null)
            database.setTransactionSuccessful()
        } finally {
            database.endTransaction()
        }
        if (deleted > 0) notifyAlertsChanged()
        return deleted
    }

    fun deleteThresholdAlertLogs(
        ids: Collection<Long>
    ): Int {
        if (ids.isEmpty()) {
            return 0
        }

        val placeholders =
            ids.joinToString(",") { "?" }

        return writableDatabase.delete(
            "alerts",
            "id IN ($placeholders)",
            ids.map { it.toString() }
                .toTypedArray()
        )
    }

    fun updateAcquisition(
        record: SavedRecordDetail
    ): Int {
        val values =
            ContentValues().apply {
                put("timestamp", record.timestamp)
                put("note", record.note)
                put(
                    "automatic",
                    if (record.automatic) 1 else 0
                )
                put("external_command", if (record.externalCommand) 1 else 0)

                record.sessionId?.let {
                    put("session_id", it)
                } ?: putNull("session_id")

                record.sessionSequence?.let {
                    put("session_sequence", it)
                } ?: putNull("session_sequence")

                put("uvc", record.sample.uvc)
                put("uvb", record.sample.uvb)
                put("uva", record.sample.uva)
                put("violetto", record.sample.violetto)
                put("blu", record.sample.blu)
                put("verde", record.sample.verde)
                put("giallo", record.sample.giallo)
                put("arancione", record.sample.arancione)
                put("rosso", record.sample.rosso)
                put("f8", record.sample.f8)
                put("nir", record.sample.nir)
                put("quality_flags", record.sample.qualityFlags)
            }

        return writableDatabase.update(
            "acquisitions",
            values,
            "id = ?",
            arrayOf(record.id.toString())
        )
    }

    fun replaceAllAcquisitions(
        records: List<SavedRecordDetail>,
        acquisitionCounter: Long? = null,
        sessionCounter: Long? = null
    ): Int {
        val database = writableDatabase

        database.beginTransaction()
        try {
            database.delete(
                "acquisitions",
                null,
                null
            )
            database.delete(
                "acquisition_sessions",
                null,
                null
            )

            records.forEach { record ->
                val values =
                    ContentValues().apply {
                        put("id", record.id)
                        put("timestamp", record.timestamp)
                        put("note", record.note)
                        put(
                            "automatic",
                            if (record.automatic) 1 else 0
                        )
                        put("external_command", if (record.externalCommand) 1 else 0)

                        record.sessionId?.let {
                            put("session_id", it)
                        } ?: putNull("session_id")

                        record.sessionSequence?.let {
                            put("session_sequence", it)
                        } ?: putNull("session_sequence")

                        put("uvc", record.sample.uvc)
                        put("uvb", record.sample.uvb)
                        put("uva", record.sample.uva)
                        put("violetto", record.sample.violetto)
                        put("blu", record.sample.blu)
                        put("verde", record.sample.verde)
                        put("giallo", record.sample.giallo)
                        put("arancione", record.sample.arancione)
                        put("rosso", record.sample.rosso)
                        put("f8", record.sample.f8)
                        put("nir", record.sample.nir)
                        put("quality_flags", record.sample.qualityFlags)
                    }

                database.insertOrThrow(
                    "acquisitions",
                    null,
                    values
                )
            }

            records
                .filter { it.automatic && it.sessionId != null }
                .groupBy { requireNotNull(it.sessionId) }
                .forEach { (sessionId, sessionRecords) ->
                    val ordered = sessionRecords.sortedBy { it.timestamp }
                    upsertSession(
                        database = database,
                        table = "acquisition_sessions",
                        sessionId = sessionId,
                        note =
                            ordered.firstOrNull { it.note.isNotBlank() }
                                ?.note
                                .orEmpty(),
                        startedAt = ordered.first().timestamp,
                        reopen = false,
                        externalCommand = ordered.any { it.externalCommand }
                    )
                    database.update(
                        "acquisition_sessions",
                        ContentValues().apply {
                            put("ended_at", ordered.last().timestamp)
                        },
                        "session_id = ?",
                        arrayOf(sessionId.toString())
                    )
                }

            val importedSessionCounter =
                maxOf(
                    sessionCounter ?: 0L,
                    records
                        .mapNotNull {
                            it.sessionId
                        }
                        .maxOrNull()
                        ?: 0L
                )
            createCountersTable(database)
            database.execSQL(
                """
                UPDATE uvir_counters
                SET value = MAX(value, ?)
                WHERE name = 'session_id'
                """.trimIndent(),
                arrayOf(importedSessionCounter)
            )

            val importedAcquisitionCounter =
                maxOf(
                    acquisitionCounter ?: 0L,
                    records.maxOfOrNull {
                        it.id
                    } ?: 0L
                )
            if (
                importedAcquisitionCounter >
                acquisitionCounter(database)
            ) {
                val sequenceValues =
                    ContentValues().apply {
                        put(
                            "seq",
                            importedAcquisitionCounter
                        )
                    }
                val updated =
                    database.update(
                        "sqlite_sequence",
                        sequenceValues,
                        "name = ?",
                        arrayOf("acquisitions")
                    )
                if (updated == 0) {
                    sequenceValues.put(
                        "name",
                        "acquisitions"
                    )
                    database.insertOrThrow(
                        "sqlite_sequence",
                        null,
                        sequenceValues
                    )
                }
            }

            database.setTransactionSuccessful()
        } finally {
            database.endTransaction()
        }

        clearRememberedManualSession(
            appContext.getSharedPreferences(
                PREFS_NAME,
                Context.MODE_PRIVATE
            )
        )

        return records.size
    }
}
