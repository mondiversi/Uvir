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
    "sensor_record_id", "quality_flags", "optical_window") + when (kind) {
    UvirArchiveKind.ACQUISITIONS -> listOf("note", "automatic", "external_command", "position_index", "variant_index") + archiveSpectralColumns
    UvirArchiveKind.ALERTS -> listOf("details")
}
internal fun archiveSessionColumns(kind: UvirArchiveKind): List<String> = listOf("note", "started_at", "ended_at") +
    if (kind == UvirArchiveKind.ACQUISITIONS) listOf("external_command", "variants_per_position", "sensor_origin_session_id") else emptyList()

internal data class UvirRecordArchive(
    val kind: UvirArchiveKind,
    val sensors: List<JSONObject>,
    val sessions: List<JSONObject>,
    val records: List<JSONObject>
) {
    fun toJson(): JSONObject = JSONObject().put("format", "uvir-records").put("version", 1)
        .put("unit", "W/m2").put("kind", kind.recordsTable)
        .put("sensors", JSONArray(sensors)).put("sessions", JSONArray(sessions)).put("records", JSONArray(records))
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

internal fun JSONObject.archiveSensor(): String? = if (isNull("sensor")) null else archiveString("sensor", 128)
internal fun JSONObject.archiveSession(): String? = if (isNull("session")) null else archiveString("session", 36)

/** Parse and validate the entire file before opening a write transaction. No SQL identifiers come from JSON. */
internal fun parseUvirRecordArchive(text: String, expectedKind: UvirArchiveKind): UvirRecordArchive {
    val root = JSONObject(text)
    require(root.archiveString("format") == "uvir-records" && root.archiveLong("version") == 1L)
    require(root.archiveString("unit") == "W/m2" && root.archiveString("kind") == expectedKind.recordsTable)
    val sensors = root.objects("sensors", 10_000)
    val sensorIds = sensors.map { sensor ->
        sensor.archiveString("hardware_id", 128).also { require(it.isNotBlank() && it == it.trim() && it.none(Char::isISOControl)) }
            .lowercase(java.util.Locale.ROOT)
    }
    require(sensorIds.distinct().size == sensorIds.size)
    val knownSensors = sensorIds.toSet()
    sensors.forEach { it.archiveString("name", 128) }
    val sessions = root.objects("sessions", 100_000)
    val records = root.objects("records", 100_000)
    val allIds = (sessions + records).map { row -> row.archiveString("id", 36).also {
        require(UUID.fromString(it).toString() == it) { "Invalid transfer identity" }
    } }
    require(allIds.distinct().size == allIds.size)
    fun checkSensor(row: JSONObject) {
        row.archiveSensor()?.let { require(it.lowercase(java.util.Locale.ROOT) in knownSensors) }
    }
    val sessionsById = sessions.associateBy { it.archiveString("id") }
    sessions.forEach { row ->
        checkSensor(row)
        row.archiveString("note")
        val start = row.archiveLong("started_at")!!
        row.archiveLong("ended_at", true)?.let { require(it >= start) }
        if (expectedKind == UvirArchiveKind.ACQUISITIONS) {
            require(row.archiveLong("external_command") in 0L..1L)
            require(row.archiveLong("variants_per_position") in 1L..Int.MAX_VALUE.toLong())
            row.archiveLong("sensor_origin_session_id", true)
        }
    }
    records.forEach { row ->
        checkSensor(row)
        row.archiveLong("timestamp")
        listOf("session_sequence", "sensor_record_id").forEach { row.archiveLong(it, true)?.let { n -> require(n > 0) } }
        require(row.archiveLong("quality_flags") in 0L..Int.MAX_VALUE.toLong())
        if (!row.isNull("optical_window")) {
            val optics = row.archiveString("optical_window")
            require(OpticalWindowSettings.fromStoredOrNull(optics) != null) { "Invalid optical provenance" }
        }
        row.archiveSession()?.let { id ->
            val session = requireNotNull(sessionsById[id]) { "Missing session" }
            require(row.archiveSensor().equals(session.archiveSensor(), true)) { "Session has another sensor" }
        }
        if (expectedKind == UvirArchiveKind.ACQUISITIONS) {
            row.archiveString("note")
            listOf("automatic", "external_command").forEach { require(row.archiveLong(it) in 0L..1L) }
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
    return UvirRecordArchive(expectedKind, sensors, sessions, records)
}
