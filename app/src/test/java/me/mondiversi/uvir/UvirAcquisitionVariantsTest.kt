package me.mondiversi.uvir

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UvirAcquisitionVariantsTest {
    private fun record(
        sequence: Int,
        position: Int? = null,
        variant: Int? = null
    ) =
        SavedRecordDetail(
            id = sequence.toLong(),
            timestamp = sequence.toLong(),
            note = "",
            automatic = true,
            sample = SensorSample(),
            sessionId = 9L,
            sessionSequence = sequence,
            positionIndex = position,
            variantIndex = variant
        )

    @Test
    fun groupsACompleteSessionByVariantAndPosition() {
        val records =
            listOf(
                record(1, position = 1, variant = 1),
                record(2, position = 1, variant = 2),
                record(3, position = 2, variant = 1),
                record(4, position = 2, variant = 2),
                record(5, position = 3, variant = 1),
                record(6, position = 3, variant = 2)
            )

        val groups = acquisitionVariantGroups(records.reversed())

        assertEquals(listOf(1, 2), groups.map { it.index })
        assertEquals(listOf(1L, 3L, 5L), groups[0].records.map { it.id })
        assertEquals(listOf(2L, 4L, 6L), groups[1].records.map { it.id })
        assertEquals(
            listOf(1L, 3L, 5L, 2L, 4L, 6L),
            recordsInVariantExportOrder(records).map { it.id }
        )
    }

    @Test
    fun incompleteVariantMetadataFallsBackToSequenceOrder() {
        val records =
            listOf(
                record(2, position = 1, variant = 2),
                record(1, position = 1, variant = null)
            )

        assertTrue(acquisitionVariantGroups(records).isEmpty())
        assertEquals(listOf(1L, 2L), recordsInVariantExportOrder(records).map { it.id })
    }

    @Test
    fun currentVariantConfigurationIsImmediatelyReflectedInOpenRecords() {
        val records = (1..6).map { sequence -> record(sequence) }

        val configured = recordsWithAcquisitionVariantMetadata(records, 3)

        assertEquals(listOf(1, 2, 3, 1, 2, 3), configured.map { it.variantIndex })
        assertEquals(listOf(1, 1, 1, 2, 2, 2), configured.map { it.positionIndex })
        assertEquals(3, acquisitionVariantGroups(configured).size)
    }

    @Test
    fun disablingVariantsImmediatelyClearsOpenRecordMetadata() {
        val configured =
            recordsWithAcquisitionVariantMetadata(
                records = (1..4).map { sequence -> record(sequence) },
                variantsPerPosition = 2
            )

        val disabled = recordsWithAcquisitionVariantMetadata(configured, 1)

        assertTrue(disabled.all { it.variantIndex == null && it.positionIndex == null })
    }

    @Test
    fun chartFileCountMultipliesGroupsByConfiguredVariants() {
        val configured =
            recordsWithAcquisitionVariantMetadata(
                records = (1..6).map { sequence -> record(sequence) },
                variantsPerPosition = 2
            )

        assertEquals(
            2,
            acquisitionSessionChartFileCount(
                configured,
                UvirChartExportMode.COMBINED
            )
        )
        assertEquals(
            8,
            acquisitionSessionChartFileCount(
                configured,
                UvirChartExportMode.SEPARATE
            )
        )
    }

    @Test
    fun singleAcquisitionExportsOnlyItsFourPopulatedGroups() {
        assertEquals(4, acquisitionChartGroupCount(record(sequence = 1)))
    }
}
