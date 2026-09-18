package me.mondiversi.uvir

internal data class AcquisitionVariantGroup(
    val index: Int,
    val count: Int,
    val records: List<SavedRecordDetail>
)

internal fun recordsWithAcquisitionVariantMetadata(
    records: List<SavedRecordDetail>,
    variantsPerPosition: Int
): List<SavedRecordDetail> {
    if (variantsPerPosition < 2) {
        return records.map { record ->
            record.copy(
                positionIndex = null,
                variantIndex = null
            )
        }
    }

    return records.mapIndexed { index, record ->
        val sequence =
            record.sessionSequence
                ?.takeIf { it > 0 }
                ?: (index + 1)
        record.copy(
            positionIndex = ((sequence - 1) / variantsPerPosition) + 1,
            variantIndex = ((sequence - 1) % variantsPerPosition) + 1
        )
    }
}

internal fun acquisitionVariantGroups(
    records: List<SavedRecordDetail>
): List<AcquisitionVariantGroup> {
    if (records.isEmpty()) return emptyList()

    val variantCount = records.mapNotNull { it.variantIndex }.maxOrNull() ?: 1
    if (
        variantCount < 2 ||
        records.any { it.variantIndex !in 1..variantCount }
    ) {
        return emptyList()
    }

    return (1..variantCount).mapNotNull { variantIndex ->
        records
            .filter { it.variantIndex == variantIndex }
            .sortedWith(
                compareBy<SavedRecordDetail> { it.positionIndex ?: Int.MAX_VALUE }
                    .thenBy { it.sessionSequence ?: Int.MAX_VALUE }
                    .thenBy { it.timestamp }
                    .thenBy { it.id }
            )
            .takeIf { it.isNotEmpty() }
            ?.let { groupedRecords ->
                AcquisitionVariantGroup(
                    index = variantIndex,
                    count = variantCount,
                    records = groupedRecords
                )
            }
    }
}

internal fun recordsInVariantExportOrder(
    records: List<SavedRecordDetail>
): List<SavedRecordDetail> =
    acquisitionVariantGroups(records)
        .takeIf { it.isNotEmpty() }
        ?.flatMap { it.records }
        ?: records.sortedWith(
            compareBy<SavedRecordDetail> { it.sessionSequence ?: Int.MAX_VALUE }
                .thenBy { it.timestamp }
                .thenBy { it.id }
        )
