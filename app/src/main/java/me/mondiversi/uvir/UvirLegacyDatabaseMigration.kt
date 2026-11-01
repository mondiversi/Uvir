package me.mondiversi.uvir

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase

/** Read compatibility for archives created by the retired ESP32 edition. Never used for new records. */
internal class UvirLegacyDatabaseMigration(private val appContext: Context) {
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
                quality_flags INTEGER NOT NULL DEFAULT 0,
                optical_window TEXT
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
                optical_window TEXT,
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
        if (db.version == 0 || db.version >= UVIR_DATABASE_VERSION) {
            createUvirPhoneSessionTables(db)
            return
        }
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
                optical_window TEXT,
                calibration_offsets TEXT,
                calibration_channel_factors TEXT,
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
                external_command_enabled INTEGER NOT NULL DEFAULT 1,
                alert_start_delay_seconds INTEGER NOT NULL DEFAULT 0,
                alert_duration_seconds INTEGER NOT NULL DEFAULT 0,
                alert_max_registrations INTEGER NOT NULL DEFAULT 0,
                settings_updated_at_ms INTEGER NOT NULL DEFAULT 0,
                alert_recording_enabled INTEGER
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

    fun upgrade(
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
        if (oldVersion < 19) {
            addColumnIfMissing(db, "sensor_settings", "alert_start_delay_seconds", "INTEGER NOT NULL DEFAULT 0")
            addColumnIfMissing(db, "sensor_settings", "alert_duration_seconds", "INTEGER NOT NULL DEFAULT 0")
            addColumnIfMissing(db, "sensor_settings", "alert_max_registrations", "INTEGER NOT NULL DEFAULT 0")
        }
        if (oldVersion < 22) {
            addColumnIfMissing(db, "acquisitions", "optical_window", "TEXT")
            addColumnIfMissing(db, "alerts", "optical_window", "TEXT")
            addColumnIfMissing(db, "sensor_settings", "optical_window", "TEXT")
        }
        if (oldVersion < 23) addColumnIfMissing(db, "sensor_settings", "calibration_offsets", "TEXT")
        if (oldVersion < 24) addColumnIfMissing(db, "sensor_settings", "calibration_channel_factors", "TEXT")
        if (oldVersion < 21) UvirLegacyCameraArchive.createTable(db)
        if (oldVersion < 20) {
            addColumnIfMissing(db, "sensor_settings", "settings_updated_at_ms", "INTEGER NOT NULL DEFAULT 0")
            addColumnIfMissing(db, "sensor_settings", "alert_recording_enabled", "INTEGER")
        }
        if (oldVersion < 25 && newVersion >= 25) migrateUvirDatabaseToSi(db)
        if (oldVersion < 26) createUvirRecordArchiveSchema(db)
        if (oldVersion < 27) migrateUvirDatabaseToPhoneArchive(db)
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
        return sensorId
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
}
