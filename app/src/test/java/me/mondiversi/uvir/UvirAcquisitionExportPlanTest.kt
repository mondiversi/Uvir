package me.mondiversi.uvir

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UvirAcquisitionExportPlanTest {
    private fun record(
        id: Long,
        timestamp: Long = id,
        sessionId: Long? = null,
        sessionSequence: Int? = sessionId?.let { id.toInt() },
        positionIndex: Int? = null,
        variantIndex: Int? = null
    ) =
        SavedRecordDetail(
            id = id,
            timestamp = timestamp,
            note = "",
            automatic = sessionId != null,
            sample = SensorSample(),
            sessionId = sessionId,
            sessionSequence = sessionSequence,
            positionIndex = positionIndex,
            variantIndex = variantIndex
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

    @Test
    fun chartCountCombinesVariantSessionAndStandaloneAcquisition() {
        val sessionRecords =
            (1..6).map { sequence ->
                record(
                    id = sequence.toLong(),
                    sessionId = 10,
                    sessionSequence = sequence,
                    positionIndex = ((sequence - 1) / 2) + 1,
                    variantIndex = ((sequence - 1) % 2) + 1
                )
            }
        val standalone = record(id = 20)
        val plan =
            buildAcquisitionExportPlan(
                selectedRecords = sessionRecords + standalone,
                allRecords = sessionRecords + standalone
            )

        val combined =
            acquisitionExportChartFileBreakdown(
                plan,
                UvirChartExportMode.COMBINED
            )
        val separate =
            acquisitionExportChartFileBreakdown(
                plan,
                UvirChartExportMode.SEPARATE
            )

        assertEquals(2, combined.completeSessionFiles)
        assertEquals(1, combined.individualAcquisitionFiles)
        assertEquals(3, combined.totalFiles)
        assertEquals(8, separate.completeSessionFiles)
        assertEquals(4, separate.individualAcquisitionFiles)
        assertEquals(12, separate.totalFiles)
    }

    @Test
    fun partialVariantSessionCountsEverySelectedRecordAsIndividual() {
        val all =
            (1..6).map { sequence ->
                record(
                    id = sequence.toLong(),
                    sessionId = 10,
                    sessionSequence = sequence,
                    positionIndex = ((sequence - 1) / 2) + 1,
                    variantIndex = ((sequence - 1) % 2) + 1
                )
            }
        val plan = buildAcquisitionExportPlan(all.take(3), all)

        assertTrue(plan.hasPartialSessions)
        assertEquals(
            3,
            acquisitionExportChartFileCount(
                plan,
                UvirChartExportMode.COMBINED
            )
        )
        assertEquals(
            12,
            acquisitionExportChartFileCount(
                plan,
                UvirChartExportMode.SEPARATE
            )
        )
    }
}
