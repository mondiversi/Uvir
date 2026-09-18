package me.mondiversi.uvir

import java.time.Instant
import java.time.ZoneId

/** Phone-only, conjunctive filters. Blank fields mean "all"; no database writes. */
internal data class UvirRecordListFilters(
    val fromInclusive: Long? = null,
    val untilInclusive: Long? = null,
    val note: String = "",
    val sensorKey: String = "",
    val automatic: Boolean? = null,
    val recordId: String = "",
    val sessionId: String = "",
    val externalCommand: Boolean? = null
) {
    val isActive: Boolean get() =
        fromInclusive != null || untilInclusive != null || note.isNotBlank() ||
            sensorKey.isNotEmpty() || automatic != null || externalCommand != null ||
            recordId.isNotEmpty() || sessionId.isNotEmpty()

    fun matches(
        id: Long, timestamp: Long, recordNote: String,
        sensorId: Long?, isAutomatic: Boolean, recordSessionId: Long?,
        isExternalCommand: Boolean = false
    ): Boolean =
        (fromInclusive == null || timestamp >= fromInclusive) &&
            (untilInclusive == null || timestamp <= untilInclusive) &&
            recordNote.contains(note.trim(), ignoreCase = true) &&
            (sensorKey.isEmpty() || sensorKey == sensorId.filterSensorKey()) &&
            (automatic == null || automatic == isAutomatic) &&
            (externalCommand == null || externalCommand == isExternalCommand) &&
            (recordId.isEmpty() || recordId.toLongOrNull() == id) &&
            (sessionId.isEmpty() || (recordSessionId != null &&
                sessionId.toLongOrNull() == recordSessionId))

    /** Selecting an inverted day moves the other bound instead of hiding everything. */
    fun withFromDay(value: Long, zoneId: ZoneId = ZoneId.systemDefault()): UvirRecordListFilters {
        val dayStart = uvirFilterDayStart(value, zoneId)
        return copy(
            fromInclusive = dayStart,
            untilInclusive = untilInclusive?.let {
                if (it < dayStart) uvirFilterDayEnd(dayStart, zoneId) else it
            }
        )
    }
    fun withUntilDay(value: Long, zoneId: ZoneId = ZoneId.systemDefault()): UvirRecordListFilters {
        val dayStart = uvirFilterDayStart(value, zoneId)
        val dayEnd = uvirFilterDayEnd(dayStart, zoneId)
        return copy(
            untilInclusive = dayEnd,
            fromInclusive = fromInclusive?.let {
                if (it > dayEnd) dayStart else it
            }
        )
    }
}

internal fun uvirFilterDayStart(timestamp: Long, zoneId: ZoneId = ZoneId.systemDefault()): Long =
    Instant.ofEpochMilli(timestamp).atZone(zoneId).toLocalDate()
        .atStartOfDay(zoneId).toInstant().toEpochMilli()

internal fun uvirFilterDayEnd(timestamp: Long, zoneId: ZoneId = ZoneId.systemDefault()): Long =
    Instant.ofEpochMilli(timestamp).atZone(zoneId).toLocalDate().plusDays(1)
        .atStartOfDay(zoneId).toInstant().toEpochMilli() - 1L

internal fun uvirAvailableFilterDays(
    timestamps: List<Long>, zoneId: ZoneId = ZoneId.systemDefault()
): List<Long> = timestamps.filter { it > 0L }
    .map { uvirFilterDayStart(it, zoneId) }.distinct()

internal fun Long?.filterSensorKey(): String = this?.toString() ?: "unknown"

internal fun filterAcquisitionRecords(
    records: List<SavedRecordSummary>, filters: UvirRecordListFilters
): List<SavedRecordSummary> =
    if (!filters.isActive) records else records.filter {
        filters.matches(
            it.id, it.timestamp, it.note, it.sensorId, it.automatic, it.sessionId,
            it.externalCommand
        )
    }

internal fun filterAlertEntries(
    entries: List<ThresholdAlertLogEntry>, filters: UvirRecordListFilters
): List<ThresholdAlertLogEntry> =
    if (!filters.isActive) entries else entries.filter {
        filters.matches(it.id, it.timestamp, it.note, it.sensorId, true, it.sessionId)
    }


/** Accept localized decimal digits too; identifiers remain exact canonical numbers. */
internal fun normalizeRecordFilterId(text: String): String =
    if (text.trimStart().startsWith("-")) "0" else buildString {
        for (character in text) {
            val digit = Character.digit(character, 10)
            if (digit >= 0) append(('0'.code + digit).toChar())
            if (length == 18) break
        }
    }
