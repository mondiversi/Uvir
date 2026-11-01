package me.mondiversi.uvir

import org.json.JSONArray
import org.json.JSONObject

/** Keep both record families in one portable file; never include app settings. */
internal fun combinedUvirRecordArchiveJson(archives: List<UvirRecordArchive>): JSONObject {
    require(archives.map { it.kind }.toSet() == UvirArchiveKind.entries.toSet() && archives.size == 2)
    return JSONObject().put("format", "uvir-records-bundle").put("version", 1).put("unit", "W/m2")
        .put("archives", JSONArray(archives.map { it.toJson() }))
}

/** Older separate archives remain importable without asking the user for a type. */
internal fun parseCombinedUvirRecordArchive(text: String): List<UvirRecordArchive> {
    val root = JSONObject(text)
    fun parsePart(part: JSONObject): UvirRecordArchive {
        val kind = UvirArchiveKind.entries.single { it.recordsTable == part.archiveString("kind") }
        return parseUvirRecordArchive(part.toString(), kind)
    }
    if (root.archiveString("format") == "uvir-records") return listOf(parsePart(root))
    require(root.archiveString("format") == "uvir-records-bundle" && root.archiveLong("version") == 1L)
    require(root.archiveString("unit") == "W/m2")
    val parts = root.getJSONArray("archives")
    require(parts.length() == 2)
    return List(parts.length()) { parsePart(parts.getJSONObject(it)) }.also { archives ->
        require(archives.map { it.kind }.toSet() == UvirArchiveKind.entries.toSet())
        require(archives.sumOf { it.records.size } <= 100_000 && archives.sumOf { it.sessions.size } <= 100_000)
        val ids = archives.flatMap { it.records + it.sessions }.map { it.getString("id") }
        require(ids.distinct().size == ids.size) { "Duplicate transfer identity across record types" }
    }
}

/** An outer transaction keeps both families and their transfer IDs in one snapshot. */
internal fun exportCombinedUvirRecordArchive(helper: UvirDatabaseHelper): List<UvirRecordArchive> {
    val db = helper.writableDatabase
    db.beginTransaction()
    try {
        return UvirArchiveKind.entries.map { exportUvirRecordArchive(helper, it) }.also { db.setTransactionSuccessful() }
    } finally { db.endTransaction() }
}
