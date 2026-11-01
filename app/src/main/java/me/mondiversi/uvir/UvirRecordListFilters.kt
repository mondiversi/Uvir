package me.mondiversi.uvir

import java.time.Instant
import java.time.ZoneId

internal enum class UvirRecordFilterMode { MANUAL, AUTOMATIC }

/** Alternatives within a field, conjunction between fields. Empty sets mean "all". */
internal data class UvirRecordListFilters(
    val fromInclusive: Long? = null,
    val untilInclusive: Long? = null,
    val notes: Set<String> = emptySet(),
    val recordIds: Set<String> = emptySet(),
    val sessionIds: Set<String> = emptySet(),
    /** 0 = no variants, -1 = any variant count, > 1 = exact count. */
    val variantCounts: Set<Int> = emptySet(),
    val modes: Set<UvirRecordFilterMode> = emptySet()
) {
    val isActive: Boolean get() =
        fromInclusive != null || untilInclusive != null || notes.isNotEmpty() ||
            recordIds.isNotEmpty() || sessionIds.isNotEmpty() || variantCounts.isNotEmpty() || modes.isNotEmpty()

    fun matches(
        id: Long, timestamp: Long, recordNote: String,
        sensorId: Long?, isAutomatic: Boolean, recordSessionId: Long?,
        isExternalCommand: Boolean = false,
        recordVariantCount: Int = 0
    ): Boolean =
        UvirRecordFilterMatcher(this).matches(id, timestamp, recordNote, sensorId,
            isAutomatic, recordSessionId, isExternalCommand, recordVariantCount)

    /** Length-prefixed lists preserve arbitrary Unicode, commas and newlines in notes. */
    fun savedValues(): List<String> = buildList {
        add("multi-v4")
        add(fromInclusive?.toString().orEmpty())
        add(untilInclusive?.toString().orEmpty())
        fun group(values: Collection<String>) { add(values.size.toString()); addAll(values) }
        group(notes)
        group(recordIds)
        group(sessionIds)
        group(variantCounts.map { it.toString() })
        group(modes.map { it.name })
    }

    companion object {
        fun fromSavedValues(values: List<String>): UvirRecordListFilters? = runCatching {
            if (values.firstOrNull() !in setOf("multi-v1", "multi-v2", "multi-v3", "multi-v4")) {
                // Upgrade the former single-choice activity state without discarding its filters.
                require(values.size in 7..9)
                UvirRecordListFilters(
                    fromInclusive = values[0].toLongOrNull(), untilInclusive = values[1].toLongOrNull(),
                    notes = values[2].takeIf(String::isNotBlank)?.let(::setOf).orEmpty(),
                    recordIds = values[5].takeIf(String::isNotEmpty)?.let(::setOf).orEmpty(),
                    sessionIds = values[6].takeIf(String::isNotEmpty)?.let(::setOf).orEmpty(),
                    variantCounts = values.getOrNull(8)?.toIntOrNull()?.let(::setOf).orEmpty()
                )
            } else {
                var index = 3
                fun group(): Set<String> {
                    val count = values[index++].toInt()
                    require(count >= 0 && count <= values.size - index)
                    return values.subList(index, index + count).toSet().also { index += count }
                }
                val notes = group()
                if (values[0] == "multi-v1") group() // Discard retired sensor filters.
                if (values[0] in setOf("multi-v1", "multi-v2")) group() // Retain the reset made during the Uvir redesign.
                val recordIds = group()
                val sessionIds = group()
                val variants = group().map { it.toInt() }.toSet()
                val modes = if (values[0] == "multi-v4") group().mapNotNull { saved ->
                    UvirRecordFilterMode.entries.find { it.name == saved }
                }.toSet() else emptySet()
                UvirRecordListFilters(values[1].toLongOrNull(), values[2].toLongOrNull(),
                    notes, recordIds, sessionIds, variants, modes)
                    .also { require(index == values.size) }
            }
        }.getOrNull()
    }

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
): List<SavedRecordSummary> {
    if (!filters.isActive) return records
    val matcher = UvirRecordFilterMatcher(filters)
    val variantCountsBySession = if (filters.variantCounts.isEmpty()) emptyMap()
        else uvirVariantCountsBySession(records)
    return records.filter {
        matcher.matches(
            it.id, it.timestamp, it.note, it.sensorId, it.automatic, it.sessionId,
            it.externalCommand,
            it.sessionId?.let(variantCountsBySession::get) ?: 0
        )
    }
}

internal fun uvirVariantCountsBySession(
    records: List<SavedRecordSummary>
): Map<Long, Int> = buildMap {
    // Keep only one maximum per session instead of allocating grouped record lists.
    for (record in records) {
        val sessionId = record.sessionId ?: continue
        val variant = record.variantIndex ?: continue
        if (variant > 1 && variant > (get(sessionId) ?: 1)) put(sessionId, variant)
    }
}

internal fun uvirAvailableVariantCounts(
    records: List<SavedRecordSummary>
): List<Int> = uvirVariantCountsBySession(records).values.distinct().sorted()

internal fun filterAlertEntries(
    entries: List<ThresholdAlertLogEntry>, filters: UvirRecordListFilters
): List<ThresholdAlertLogEntry> {
    if (!filters.isActive) return entries
    val matcher = UvirRecordFilterMatcher(filters.copy(variantCounts = emptySet(), modes = emptySet()))
    return entries.filter {
        matcher.matches(it.id, it.timestamp, it.note, it.sensorId, true, it.sessionId)
    }
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
