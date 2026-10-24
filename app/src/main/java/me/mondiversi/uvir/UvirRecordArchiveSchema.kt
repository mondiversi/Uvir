package me.mondiversi.uvir

import android.database.sqlite.SQLiteDatabase
import java.util.UUID

/** Transfer identities survive re-export, while local counters remain private to each phone. */
internal fun createUvirRecordArchiveSchema(db: SQLiteDatabase) {
    db.execSQL("""CREATE TABLE IF NOT EXISTS record_transfer_ids (
        uid TEXT PRIMARY KEY NOT NULL,
        kind TEXT NOT NULL,
        local_id INTEGER NOT NULL
    )""")
    db.execSQL("CREATE INDEX IF NOT EXISTS idx_record_transfer_local ON record_transfer_ids(kind, local_id)")
    UvirArchiveKind.entries.forEach { kind ->
        listOf(kind.recordsTable to "id", kind.sessionsTable to "session_id").forEach { (table, key) ->
            db.execSQL("""CREATE TRIGGER IF NOT EXISTS archive_delete_$table AFTER DELETE ON $table
                BEGIN DELETE FROM record_transfer_ids WHERE kind = '$table' AND local_id = OLD.$key; END""")
            // Assign identity at creation, so even a backup made before the first export has it.
            val uuid = """lower(hex(randomblob(4)) || '-' || hex(randomblob(2)) || '-4' ||
                substr(hex(randomblob(2)), 2) || '-' || substr('89ab', (random() & 3) + 1, 1) ||
                substr(hex(randomblob(2)), 2) || '-' || hex(randomblob(6)))"""
            db.execSQL("""CREATE TRIGGER IF NOT EXISTS archive_insert_$table AFTER INSERT ON $table
                BEGIN INSERT INTO record_transfer_ids(uid, kind, local_id)
                SELECT $uuid, '$table', NEW.$key WHERE NOT EXISTS
                (SELECT 1 FROM record_transfer_ids WHERE kind = '$table' AND local_id = NEW.$key); END""")
            db.execSQL("""INSERT INTO record_transfer_ids(uid, kind, local_id)
                SELECT $uuid, '$table', $key FROM $table WHERE NOT EXISTS
                (SELECT 1 FROM record_transfer_ids WHERE kind = '$table' AND local_id = $table.$key)""")
        }
    }
}

/** Replace only the fresh identity assigned by the insert trigger, preserving the portable original. */
internal fun replaceFreshArchiveId(db: SQLiteDatabase, table: String, localId: Long, uid: String) {
    db.delete("record_transfer_ids", "kind = ? AND local_id = ?", arrayOf(table, localId.toString()))
    bindArchiveId(db, table, localId, uid)
}

internal fun archiveLocalId(db: SQLiteDatabase, table: String, uid: String): Long? =
    db.rawQuery("SELECT local_id, kind FROM record_transfer_ids WHERE uid = ?", arrayOf(uid)).use {
        if (!it.moveToFirst()) null else {
            require(it.getString(1) == table) { "Transfer identity belongs to another record type" }
            it.getLong(0)
        }
    }

internal fun bindArchiveId(db: SQLiteDatabase, table: String, localId: Long, uid: String) {
    val existing = archiveLocalId(db, table, uid)
    require(existing == null || existing == localId) { "Conflicting transfer identity" }
    db.execSQL("INSERT OR IGNORE INTO record_transfer_ids(uid, kind, local_id) VALUES (?, ?, ?)",
        arrayOf<Any>(uid, table, localId))
}

internal fun archiveTransferId(db: SQLiteDatabase, table: String, localId: Long): String {
    db.rawQuery("SELECT uid FROM record_transfer_ids WHERE kind = ? AND local_id = ? ORDER BY rowid LIMIT 1",
        arrayOf(table, localId.toString())).use { if (it.moveToFirst()) return it.getString(0) }
    return UUID.randomUUID().toString().also { bindArchiveId(db, table, localId, it) }
}
