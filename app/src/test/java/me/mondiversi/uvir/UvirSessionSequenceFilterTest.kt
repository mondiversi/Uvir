package me.mondiversi.uvir

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class UvirSessionSequenceFilterTest {
    @Test
    fun selectsTheRequestedPositionFromEveryCycle() {
        val records = (1..15).map(::record)

        val filtered = filterSessionRecordsBySequence(
            records = records,
            cycleSize = 5,
            cyclePosition = 2
        )

        assertEquals(listOf(2L, 7L, 12L), filtered.map { it.id })
    }

    @Test
    fun usesListOrderWhenLegacyRecordsHaveNoSessionSequence() {
        val records = (1..9).map { record(it, sessionSequence = null) }

        val filtered = filterSessionRecordsBySequence(
            records = records,
            cycleSize = 3,
            cyclePosition = 3
        )

        assertEquals(listOf(3L, 6L, 9L), filtered.map { it.id })
    }

    @Test
    fun inactiveOrInvalidFilterLeavesTheSessionUntouched() {
        val records = (1..4).map(::record)

        assertSame(records, filterSessionRecordsBySequence(records, 1, 1))
        assertSame(records, filterSessionRecordsBySequence(records, 3, 1))
        assertSame(records, filterSessionRecordsBySequence(records, 4, 5))
    }

    @Test
    fun offersOnlyExactDivisorsOfTheSessionSize() {
        assertEquals(listOf(1, 2, 5, 10), sessionSequenceCycleSizes(10))
        assertEquals(listOf(1, 11), sessionSequenceCycleSizes(11))
    }

    private fun record(
        sequence: Int,
        sessionSequence: Int? = sequence
    ): SavedRecordDetail =
        SavedRecordDetail(
            id = sequence.toLong(),
            timestamp = sequence.toLong(),
            note = "",
            automatic = true,
            sample = SensorSample(),
            sessionId = 1L,
            sessionSequence = sessionSequence
        )
}
