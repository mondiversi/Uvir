package me.mondiversi.uvir

import android.database.sqlite.SQLiteDatabase

internal const val UVIR_DATABASE_VERSION = 27

/** The phone owns records and settings. USB attachment identities are deliberately not persisted. */
internal val uvirPhoneArchiveColumns = linkedMapOf(
    "acquisitions" to linkedMapOf(
        "id" to "INTEGER PRIMARY KEY AUTOINCREMENT", "timestamp" to "INTEGER NOT NULL",
        "note" to "TEXT NOT NULL", "automatic" to "INTEGER NOT NULL DEFAULT 0",
        "session_id" to "INTEGER", "session_sequence" to "INTEGER",
        "position_index" to "INTEGER", "variant_index" to "INTEGER",
        *archiveSpectralColumns.map { it to "REAL NOT NULL" }.toTypedArray(),
        "quality_flags" to "INTEGER NOT NULL DEFAULT 0", "optical_window" to "TEXT"
    ),
    "alerts" to linkedMapOf(
        "id" to "INTEGER PRIMARY KEY AUTOINCREMENT", "timestamp" to "INTEGER NOT NULL",
        "details" to "TEXT NOT NULL", "optical_window" to "TEXT",
        "quality_flags" to "INTEGER NOT NULL DEFAULT 0", "session_id" to "INTEGER",
        "session_sequence" to "INTEGER"
    ),
    "acquisition_sessions" to linkedMapOf(
        "session_id" to "INTEGER PRIMARY KEY", "note" to "TEXT NOT NULL DEFAULT ''",
        "variants_per_position" to "INTEGER NOT NULL DEFAULT 1",
        "started_at" to "INTEGER NOT NULL", "ended_at" to "INTEGER"
    ),
    "alert_sessions" to linkedMapOf(
        "session_id" to "INTEGER PRIMARY KEY", "note" to "TEXT NOT NULL DEFAULT ''",
        "started_at" to "INTEGER NOT NULL", "ended_at" to "INTEGER"
    )
)

private fun createPhoneTable(db: SQLiteDatabase, table: String, name: String = table) {
    val columns = uvirPhoneArchiveColumns.getValue(table).entries.joinToString(",") { (column, type) -> "$column $type" }
    db.execSQL("CREATE TABLE IF NOT EXISTS $name ($columns)")
}

internal fun createUvirPhoneSessionTables(db: SQLiteDatabase) {
    listOf("acquisition_sessions", "alert_sessions").forEach { createPhoneTable(db, it) }
}

internal fun createUvirPhoneArchiveSchema(db: SQLiteDatabase) {
    uvirPhoneArchiveColumns.keys.forEach { createPhoneTable(db, it) }
    db.execSQL("CREATE TABLE IF NOT EXISTS uvir_counters (name TEXT PRIMARY KEY, value INTEGER NOT NULL)")
    db.execSQL("INSERT OR IGNORE INTO uvir_counters(name,value) VALUES ('session_id',0)")
    listOf("acquisitions", "alerts").forEach { table ->
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_${table}_session ON $table(session_id,session_sequence)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_${table}_time ON $table(timestamp DESC,id DESC)")
    }
    createUvirRecordArchiveSchema(db)
}

/** Called in an upgrade transaction, also for the private copy of an imported legacy database. */
internal fun migrateUvirDatabaseToPhoneArchive(db: SQLiteDatabase) {
    val highWatermarks = db.rawQuery("SELECT name,seq FROM sqlite_sequence WHERE name IN ('acquisitions','alerts')", null).use { c ->
        buildMap { while (c.moveToNext()) put(c.getString(0), c.getLong(1)) }
    }
    // Drop transfer triggers first: rebuilding tables must not delete their portable UUIDs.
    val triggers = db.rawQuery("SELECT name FROM sqlite_master WHERE type='trigger'", null).use { c ->
        buildList { while (c.moveToNext()) add(c.getString(0)) }
    }
    triggers.forEach { name -> db.execSQL("DROP TRIGGER \"${name.replace("\"", "\"\"")}\"") }
    uvirPhoneArchiveColumns.forEach { (table, columns) ->
        val temporary = "${table}_phone"
        createPhoneTable(db, table, temporary)
        val names = columns.keys.joinToString(",")
        db.execSQL("INSERT INTO $temporary ($names) SELECT $names FROM $table")
        db.execSQL("DROP TABLE $table")
        db.execSQL("ALTER TABLE $temporary RENAME TO $table")
    }
    listOf("sensor_alert_rules", "sensor_settings", "camera_estimates", "sensors").forEach {
        db.execSQL("DROP TABLE IF EXISTS $it")
    }
    highWatermarks.forEach { (table, value) ->
        db.execSQL("UPDATE sqlite_sequence SET seq=MAX(seq,?) WHERE name=?", arrayOf<Any>(value, table))
        db.execSQL("INSERT INTO sqlite_sequence(name,seq) SELECT ?,? WHERE NOT EXISTS (SELECT 1 FROM sqlite_sequence WHERE name=?)",
            arrayOf<Any>(table, value, table))
    }
    createUvirPhoneArchiveSchema(db)
}
