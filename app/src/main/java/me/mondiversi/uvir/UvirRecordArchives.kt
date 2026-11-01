package me.mondiversi.uvir

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import org.json.JSONObject

private fun Cursor.archiveRow(columns: List<String>): JSONObject = JSONObject().also { row ->
    columns.forEach { name ->
        val index = getColumnIndexOrThrow(name)
        row.put(name, when (getType(index)) {
            Cursor.FIELD_TYPE_NULL -> JSONObject.NULL
            Cursor.FIELD_TYPE_INTEGER -> getLong(index)
            Cursor.FIELD_TYPE_FLOAT -> getDouble(index)
            Cursor.FIELD_TYPE_STRING -> getString(index)
            else -> error("Unsupported archive value")
        })
    }
}

/** A consistent snapshot, independent of display units, rounding, filters and hidden bands. */
internal fun exportUvirRecordArchive(helper: UvirDatabaseHelper, kind: UvirArchiveKind): UvirRecordArchive {
    val db = helper.writableDatabase
    db.beginTransaction()
    try {
        val sessions = linkedMapOf<Long, JSONObject>()
        val records = db.rawQuery("SELECT * FROM ${kind.recordsTable} ORDER BY timestamp, id", null).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    fun nullableLong(name: String): Long? = cursor.getColumnIndexOrThrow(name).let {
                        if (cursor.isNull(it)) null else cursor.getLong(it)
                    }
                    val id = cursor.getLong(cursor.getColumnIndexOrThrow("id"))
                    val sessionId = nullableLong("session_id")
                    if (sessionId != null && sessionId !in sessions) {
                        db.rawQuery("SELECT * FROM ${kind.sessionsTable} WHERE session_id = ?", arrayOf(sessionId.toString())).use { s ->
                            check(s.moveToFirst()) { "Missing stored session" }
                            sessions[sessionId] = s.archiveRow(archiveSessionColumns(kind))
                                .put("id", archiveTransferId(db, kind.sessionsTable, sessionId))
                        }
                    }
                    add(cursor.archiveRow(archiveRecordColumns(kind))
                        .put("id", archiveTransferId(db, kind.recordsTable, id))
                        .put("session", sessionId?.let { sessions.getValue(it).getString("id") } ?: JSONObject.NULL))
                }
            }
        }
        db.setTransactionSuccessful()
        return UvirRecordArchive(kind, sessions.values.toList(), records)
    } finally { db.endTransaction() }
}

internal data class UvirRecordImportResult(val imported: Int, val skipped: Int)

/** Files only add archived measurements. Pairing, connection secrets and sensor settings are never imported. */
internal fun importUvirRecordArchives(helper: UvirDatabaseHelper, archives: List<UvirRecordArchive>): UvirRecordImportResult {
    val db = helper.writableDatabase
    var imported = 0
    var skipped = 0
    db.beginTransaction()
    try {
        archives.forEach { archive ->
            val kind = archive.kind
            fun existingRecord(row: JSONObject): Long? {
                val existing = archiveLocalId(db, kind.recordsTable, row.getString("id")) ?: return null
                db.rawQuery("SELECT * FROM ${kind.recordsTable} WHERE id=?", arrayOf(existing.toString())).use { c ->
                    check(c.moveToFirst()) { "Stale transfer identity" }
                    val sessionIndex = c.getColumnIndexOrThrow("session_id")
                    require(c.isNull(sessionIndex) == (row.archiveSession() == null)) { "Conflicting session membership" }
                    val stored = c.archiveRow(archiveRecordColumns(kind))
                    // Notes can be edited locally after an import. Measurements and timing cannot
                    // silently change under an existing portable identity.
                    (archiveRecordColumns(kind) - "note").forEach { key ->
                        val left = stored.get(key)
                        val right = row.get(key)
                        require(if (left is Number && right is Number) left.toDouble() == right.toDouble() else left == right) {
                            "Conflicting record identity"
                        }
                    }
                }
                return existing
            }
            val existingRecords = archive.records.associate { it.getString("id") to existingRecord(it) }
            val grouped = archive.records.filter { it.archiveSession() != null }.groupBy { it.getString("session") }
            val sessions = archive.sessions.associate { row ->
                val uid = row.getString("id")
                val duplicateSessions = grouped.getValue(uid).mapNotNull { record ->
                    existingRecords[record.getString("id")]?.let { id ->
                        db.rawQuery("SELECT session_id FROM ${kind.recordsTable} WHERE id = ?", arrayOf(id.toString())).use {
                            if (it.moveToFirst() && !it.isNull(0)) it.getLong(0) else null
                        }
                    }
                }.distinct()
                require(duplicateSessions.size <= 1) { "Records belong to different sessions" }
                val existing = archiveLocalId(db, kind.sessionsTable, uid) ?: duplicateSessions.singleOrNull()
                require(existing == null || duplicateSessions.isEmpty() || duplicateSessions.single() == existing) { "Conflicting session identity" }
                val id = if (existing != null) {
                    db.rawQuery("SELECT started_at FROM ${kind.sessionsTable} WHERE session_id=?", arrayOf(existing.toString())).use {
                        require(it.moveToFirst() && it.getLong(0) == row.getLong("started_at")) { "Conflicting session identity" }
                    }
                    existing
                } else {
                    // Allocate in the receiving phone's shared session counter inside this transaction.
                    db.execSQL("UPDATE uvir_counters SET value = MAX(value, (SELECT COALESCE(MAX(session_id), 0) FROM acquisition_sessions), (SELECT COALESCE(MAX(session_id), 0) FROM alert_sessions)) + 1 WHERE name = 'session_id'")
                    val newId = db.rawQuery("SELECT value FROM uvir_counters WHERE name = 'session_id'", null).use { check(it.moveToFirst()); it.getLong(0) }
                    val values = archiveValues(row, archiveSessionColumns(kind)).apply {
                        put("session_id", newId)
                        // An imported snapshot must not become an active recording on this phone.
                        if (row.isNull("ended_at")) put("ended_at", maxOf(row.getLong("started_at"), grouped.getValue(uid).maxOf { it.getLong("timestamp") }))
                    }
                    db.insertOrThrow(kind.sessionsTable, null, values)
                    replaceFreshArchiveId(db, kind.sessionsTable, newId, uid)
                    newId
                }
                bindArchiveId(db, kind.sessionsTable, id, uid)
                uid to id
            }
            archive.records.forEach { row ->
                val uid = row.getString("id")
                val existing = existingRecords[uid]
                val id = if (existing != null) { skipped++; existing } else {
                    val values = archiveValues(row, archiveRecordColumns(kind)).apply {
                        putNullableId("session_id", row.archiveSession()?.let { sessions.getValue(it) })
                    }
                    db.insertOrThrow(kind.recordsTable, null, values).also {
                        replaceFreshArchiveId(db, kind.recordsTable, it, uid)
                        imported++
                    }
                }
                bindArchiveId(db, kind.recordsTable, id, uid)
            }
            archive.sessions.forEach { row ->
                val uid = row.getString("id")
                val hasNewRecords = grouped.getValue(uid).any { existingRecords[it.getString("id")] == null }
                if (hasNewRecords) {
                    val end = row.archiveLong("ended_at", true)
                        ?: maxOf(row.getLong("started_at"), grouped.getValue(uid).maxOf { it.getLong("timestamp") })
                    // Extend a previously imported snapshot, while preserving locally active sessions.
                    db.execSQL("UPDATE ${kind.sessionsTable} SET ended_at = MAX(ended_at, ?) WHERE session_id = ? AND ended_at IS NOT NULL",
                        arrayOf<Any>(end, sessions.getValue(uid)))
                }
            }
        }
        db.setTransactionSuccessful()
    } finally { db.endTransaction() }
    if (imported > 0) helper.notifyDatabaseImported()
    return UvirRecordImportResult(imported, skipped)
}

private fun ContentValues.putNullableId(key: String, value: Long?) {
    if (value == null) putNull(key) else put(key, value)
}

private fun archiveValues(row: JSONObject, columns: List<String>): ContentValues = ContentValues().apply {
    columns.forEach { key ->
        when (val value = row.opt(key)) {
            null, JSONObject.NULL -> putNull(key)
            is String -> put(key, value)
            is Int -> put(key, value)
            is Long -> put(key, value)
            is Number -> put(key, value.toDouble())
            else -> error("Unsupported $key")
        }
    }
}
