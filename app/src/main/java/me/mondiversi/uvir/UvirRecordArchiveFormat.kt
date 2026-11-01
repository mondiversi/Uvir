package me.mondiversi.uvir

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

internal enum class UvirArchiveKind(val recordsTable: String, val sessionsTable: String) {
    ACQUISITIONS("acquisitions", "acquisition_sessions"),
    ALERTS("alerts", "alert_sessions")
}

internal val archiveSpectralColumns = listOf("uvc", "uvb", "uva", "violetto", "blu", "verde",
    "giallo", "arancione", "rosso", "f8", "nir")
internal fun archiveRecordColumns(kind: UvirArchiveKind): List<String> = listOf("timestamp", "session_sequence",
    "quality_flags", "optical_window") + when (kind) {
    UvirArchiveKind.ACQUISITIONS -> listOf("note", "automatic", "position_index", "variant_index") + archiveSpectralColumns
    UvirArchiveKind.ALERTS -> listOf("details")
}
internal fun archiveSessionColumns(kind: UvirArchiveKind): List<String> = listOf("note", "started_at", "ended_at") +
    if (kind == UvirArchiveKind.ACQUISITIONS) listOf("variants_per_position") else emptyList()

internal data class UvirRecordArchive(
    val kind: UvirArchiveKind,
    val sessions: List<JSONObject>,
    val records: List<JSONObject>
) {
    fun toJson(): JSONObject = JSONObject().put("format", "uvir-records").put("version", 2)
        .put("unit", "W/m2").put("kind", kind.recordsTable)
        .put("sessions", JSONArray(sessions)).put("records", JSONArray(records))
}

private fun JSONObject.objects(key: String, maximum: Int): List<JSONObject> {
    val array = getJSONArray(key)
    require(array.length() <= maximum) { "Too many $key" }
    return List(array.length()) { array.getJSONObject(it) }
}

internal fun JSONObject.archiveString(key: String, maximum: Int = 16_384): String {
    val value = get(key)
    require(value is String && value.length <= maximum) { "Invalid $key" }
    return value
}

internal fun JSONObject.archiveLong(key: String, nullable: Boolean = false): Long? {
    if (nullable && isNull(key)) return null
    val value = get(key)
    require(value is Int || value is Long) { "Invalid integer $key" }
    return (value as Number).toLong().also { require(it >= 0L) { "Negative $key" } }
}

internal fun JSONObject.archiveSession(): String? = if (isNull("session")) null else archiveString("session", 36)

/** Parse and validate the entire file before opening a write transaction. No SQL identifiers come from JSON. */
internal fun parseUvirRecordArchive(text: String, expectedKind: UvirArchiveKind): UvirRecordArchive {
    val root = JSONObject(text)
    require(root.archiveString("format") == "uvir-records" && root.archiveLong("version") in 1L..2L)
    require(root.archiveString("unit") == "W/m2" && root.archiveString("kind") == expectedKind.recordsTable)
    val sessions = root.objects("sessions", 100_000)
    val records = root.objects("records", 100_000)
    val allIds = (sessions + records).map { row -> row.archiveString("id", 36).also {
        require(UUID.fromString(it).toString() == it) { "Invalid transfer identity" }
    } }
    require(allIds.distinct().size == allIds.size)
    val sessionsById = sessions.associateBy { it.archiveString("id") }
    sessions.forEach { row ->
        row.archiveString("note")
        val start = row.archiveLong("started_at")!!
        row.archiveLong("ended_at", true)?.let { require(it >= start) }
        if (expectedKind == UvirArchiveKind.ACQUISITIONS) {
            require(row.archiveLong("variants_per_position") in 1L..Int.MAX_VALUE.toLong())
        }
    }
    records.forEach { row ->
        row.archiveLong("timestamp")
        row.archiveLong("session_sequence", true)?.let { require(it in 1..Int.MAX_VALUE.toLong()) }
        require(row.archiveLong("quality_flags") in 0L..Int.MAX_VALUE.toLong())
        if (!row.isNull("optical_window")) {
            val optics = row.archiveString("optical_window")
            require(OpticalWindowSettings.fromStoredOrNull(optics) != null) { "Invalid optical provenance" }
        }
        row.archiveSession()?.let { id ->
            requireNotNull(sessionsById[id]) { "Missing session" }
        }
        if (expectedKind == UvirArchiveKind.ACQUISITIONS) {
            row.archiveString("note")
            require(row.archiveLong("automatic") in 0L..1L)
            listOf("position_index", "variant_index").forEach { row.archiveLong(it, true)?.let { n -> require(n in 1..Int.MAX_VALUE.toLong()) } }
            archiveSpectralColumns.forEach { key ->
                val number = row.get(key)
                require(number is Number && number.toDouble().isFinite()) { "Invalid irradiance" }
            }
        } else {
            val details = row.archiveString("details")
            require(details.isNotBlank())
            details.split(';').forEach { encoded ->
                val parts = encoded.split('|')
                require(parts.size == 4)
                ThresholdAlertMetric.valueOf(parts[0])
                require(parts[1].toDouble().isFinite())
                ThresholdAlertDirection.valueOf(parts[2])
                require(parts[3].toDouble().isFinite())
            }
        }
    }
    val usedSessions = records.mapNotNull { it.archiveSession() }.toSet()
    require(sessions.all { it.archiveString("id") in usedSessions }) { "Empty session" }
    // Whitelist fields, including when importing version 1: retired hardware identities and
    // external-command metadata must never leak into a new export or the phone database.
    fun clean(rows: List<JSONObject>, columns: List<String>) = rows.map { source ->
        JSONObject().also { target -> columns.forEach { target.put(it, source.get(it)) } }
    }
    return UvirRecordArchive(expectedKind,
        clean(sessions, listOf("id") + archiveSessionColumns(expectedKind)),
        clean(records, listOf("id", "session") + archiveRecordColumns(expectedKind)))
}
