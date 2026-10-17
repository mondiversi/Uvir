package me.mondiversi.uvir

import kotlin.random.Random
import org.junit.Assert.*
import org.junit.Test

class UvirRecordFilterEfficiencyTest {
    private class CountedSet(private val values: Set<String>) : AbstractSet<String>() {
        var traversals = 0
        override val size: Int get() = values.size
        override fun iterator(): Iterator<String> {
            traversals++
            return values.iterator()
        }
    }

    @Test fun identifiersAndNotesArePreparedOnceForAWholeList() {
        val ids = CountedSet(setOf("7", "8", "invalid"))
        val sessions = CountedSet(setOf("10", "invalid"))
        val notes = CountedSet(setOf(" garden ", "UV"))
        val records = List(2_000) { index ->
            SavedRecordSummary(index.toLong(), index.toLong(), "Garden", true, sessionId = 10L)
        }
        val filters = UvirRecordListFilters(recordIds = ids, sessionIds = sessions, notes = notes)
        assertEquals(listOf(7L, 8L), filterAcquisitionRecords(records, filters).map { it.id })
        assertEquals(1, ids.traversals)
        assertEquals(1, sessions.traversals)
        assertEquals(1, notes.traversals)
    }

    @Test fun optimizedFilteringKeepsEveryFormerPredicateRuleAndRecordOrder() {
        val random = Random(7343)
        val records = List(200) { index ->
            SavedRecordSummary(
                id = index.toLong(), timestamp = random.nextLong(1, 20_000),
                note = listOf("Garden", " UV ", "🌙 לילה", "")[index % 4],
                automatic = index % 2 == 0,
                sessionId = if (index % 5 == 0) null else (index % 12).toLong(),
                sensorId = if (index % 7 == 0) null else (index % 3).toLong(),
                externalCommand = index % 9 == 0,
                variantIndex = if (index % 3 == 0) null else index % 4 + 1
            )
        }
        val variantCounts = records.filter { it.sessionId != null && it.variantIndex != null }
            .groupBy { it.sessionId!! }
            .mapValues { (_, group) -> group.maxOf { it.variantIndex!! } }.filterValues { it > 1 }
        assertEquals(variantCounts, uvirVariantCountsBySession(records))
        fun <T> choices(values: List<T>) = values.filter { random.nextBoolean() }.toSet()
        repeat(250) {
            val filters = UvirRecordListFilters(
                fromInclusive = if (random.nextBoolean()) null else 3_000,
                untilInclusive = if (random.nextBoolean()) null else 17_000,
                notes = choices(listOf(" GARD ", "UV", "🌙", "")),
                sensorKeys = choices(listOf("0", "1", "unknown")),
                modes = choices(UvirRecordFilterMode.entries),
                recordIds = choices(listOf("1", "8", "21", "99", "invalid", "99999999999999999999")),
                sessionIds = choices(listOf("1", "8", "11", "invalid")),
                variantCounts = choices(listOf(-1, 0, 2, 4))
            )
            val expected = records.filter { record ->
                val count = variantCounts[record.sessionId] ?: 0
                (filters.fromInclusive == null || record.timestamp >= filters.fromInclusive) &&
                    (filters.untilInclusive == null || record.timestamp <= filters.untilInclusive) &&
                    (filters.notes.isEmpty() || filters.notes.any { record.note.contains(it.trim(), true) }) &&
                    (filters.sensorKeys.isEmpty() || record.sensorId.filterSensorKey() in filters.sensorKeys) &&
                    (filters.modes.isEmpty() || when {
                        record.externalCommand -> UvirRecordFilterMode.EXTERNAL
                        record.automatic -> UvirRecordFilterMode.AUTOMATIC
                        else -> UvirRecordFilterMode.MANUAL
                    } in filters.modes) &&
                    (filters.recordIds.isEmpty() || filters.recordIds.any { it.toLongOrNull() == record.id }) &&
                    (filters.sessionIds.isEmpty() || record.sessionId != null &&
                        filters.sessionIds.any { it.toLongOrNull() == record.sessionId }) &&
                    (filters.variantCounts.isEmpty() || filters.variantCounts.any {
                        when (it) { -1 -> count > 1; 0 -> count <= 1; else -> count == it }
                    })
            }
            assertEquals("Filter behavior changed: $filters", expected, filterAcquisitionRecords(records, filters))
        }
    }
}
