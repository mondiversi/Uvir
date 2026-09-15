package me.mondiversi.uvir

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UvirAlertExportPlanTest {
    private fun alert(
        id: Long,
        timestamp: Long = id,
        sessionId: Long? = null
    ) =
        ThresholdAlertLogEntry(
            id = id,
            timestamp = timestamp,
            details = "UVA|12.0|ABOVE|10.0",
            sessionId = sessionId
        )

    @Test
    fun completeSessionBecomesOneSessionItem() {
        val all =
            listOf(
                alert(1, sessionId = 10),
                alert(2, sessionId = 10),
                alert(3)
            )

        val plan = buildAlertExportPlan(all, all)

        assertFalse(plan.hasPartialSessions)
        assertEquals(2, plan.items.size)
        assertEquals(
            listOf(1L, 2L),
            (plan.items[1] as AlertExportItem.CompleteSession)
                .entries
                .map { it.id }
        )
    }

    @Test
    fun partialSessionBecomesIndividualAlerts() {
        val all =
            listOf(
                alert(1, sessionId = 10),
                alert(2, sessionId = 10),
                alert(3, sessionId = 20),
                alert(4, sessionId = 20)
            )
        val selected = listOf(all[0], all[2], all[3])

        val plan = buildAlertExportPlan(selected, all)

        assertTrue(plan.hasPartialSessions)
        assertEquals(setOf(10L), plan.partialSessionIds)
        assertEquals(2, plan.items.size)
        assertTrue(
            plan.items.any {
                it is AlertExportItem.IndividualAlert &&
                    it.entry.id == 1L &&
                    it.fromPartialSession
            }
        )
        assertTrue(
            plan.items.any {
                it is AlertExportItem.CompleteSession &&
                    it.sessionId == 20L
            }
        )
    }
}
