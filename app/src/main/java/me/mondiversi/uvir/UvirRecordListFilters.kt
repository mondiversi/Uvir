package me.mondiversi.uvir

/** Phone-only, conjunctive filters. Blank fields mean "all"; no database writes. */
internal data class UvirRecordListFilters(
    val fromInclusive: Long? = null,
    val untilInclusive: Long? = null,
    val note: String = "",
    val sensorKey: String = "",
    val automatic: Boolean? = null,
    val recordId: String = "",
    val sessionId: String = ""
) {
    val isActive: Boolean get() =
        fromInclusive != null || untilInclusive != null || note.isNotBlank() ||
            sensorKey.isNotEmpty() || automatic != null ||
            recordId.isNotEmpty() || sessionId.isNotEmpty()

    fun matches(
        id: Long, timestamp: Long, recordNote: String,
        sensorId: Long?, isAutomatic: Boolean, recordSessionId: Long?
    ): Boolean =
        (fromInclusive == null || timestamp >= fromInclusive) &&
            (untilInclusive == null || timestamp <= untilInclusive) &&
            recordNote.contains(note.trim(), ignoreCase = true) &&
            (sensorKey.isEmpty() || sensorKey == sensorId.filterSensorKey()) &&
            (automatic == null || automatic == isAutomatic) &&
            (recordId.isEmpty() || recordId.toLongOrNull() == id) &&
            (sessionId.isEmpty() || (recordSessionId != null &&
                sessionId.toLongOrNull() == recordSessionId))

    /** Picking an inverted range moves the other bound instead of hiding everything. */
    fun withFrom(value: Long): UvirRecordListFilters = copy(
        fromInclusive = value,
        untilInclusive = untilInclusive?.let {
            if (it < value) Math.floorDiv(value, 60_000L) * 60_000L + 59_999L else it
        }
    )
    fun withUntil(value: Long): UvirRecordListFilters = copy(
        untilInclusive = value,
        fromInclusive = fromInclusive?.let {
            if (it > value) Math.floorDiv(value, 60_000L) * 60_000L else it
        }
    )
}

internal fun Long?.filterSensorKey(): String = this?.toString() ?: "unknown"

internal fun filterAcquisitionRecords(
    records: List<SavedRecordSummary>, filters: UvirRecordListFilters
): List<SavedRecordSummary> =
    if (!filters.isActive) records else records.filter {
        filters.matches(it.id, it.timestamp, it.note, it.sensorId, it.automatic, it.sessionId)
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
