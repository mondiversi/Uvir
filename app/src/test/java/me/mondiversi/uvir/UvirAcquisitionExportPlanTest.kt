package me.mondiversi.uvir

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UvirAcquisitionExportPlanTest {
    private fun record(
        id: Long,
        timestamp: Long = id,
        sessionId: Long? = null
    ) =
        SavedRecordDetail(
            id = id,
            timestamp = timestamp,
            note = "",
            automatic = sessionId != null,
            sample = SensorSample(),
            sessionId = sessionId,
            sessionSequence = sessionId?.let { id.toInt() }
        )

    @Test
    fun completeSessionBecomesOneSessionItem() {
        val all =
            listOf(
                record(1, sessionId = 10),
                record(2, sessionId = 10),
                record(3)
            )

        val plan = buildAcquisitionExportPlan(all, all)

        assertFalse(plan.hasPartialSessions)
        assertEquals(2, plan.items.size)
        assertEquals(
            listOf(1L, 2L),
            (plan.items[1] as AcquisitionExportItem.CompleteSession)
                .records
                .map { it.id }
        )
    }

    @Test
    fun partialSessionBecomesIndividualAcquisitions() {
        val all =
            listOf(
                record(1, sessionId = 10),
                record(2, sessionId = 10),
                record(3, sessionId = 20),
                record(4, sessionId = 20)
            )
        val selected = listOf(all[0], all[2], all[3])

        val plan = buildAcquisitionExportPlan(selected, all)

        assertTrue(plan.hasPartialSessions)
        assertEquals(setOf(10L), plan.partialSessionIds)
        assertEquals(2, plan.items.size)
        assertTrue(
            plan.items.any {
                it is AcquisitionExportItem.IndividualAcquisition &&
                    it.record.id == 1L &&
                    it.fromPartialSession
            }
        )
        assertTrue(
            plan.items.any {
                it is AcquisitionExportItem.CompleteSession &&
                    it.sessionId == 20L
            }
        )
    }
}
