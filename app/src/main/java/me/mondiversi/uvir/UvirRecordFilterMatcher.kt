package me.mondiversi.uvir

/** Normalize a filter once per list pass, not once per stored record. */
internal class UvirRecordFilterMatcher(private val filters: UvirRecordListFilters) {
    private val notes = filters.notes.map(String::trim)
    private val recordIds = filters.recordIds.mapNotNull(String::toLongOrNull).toHashSet()
    private val sessionIds = filters.sessionIds.mapNotNull(String::toLongOrNull).toHashSet()

    fun matches(
        id: Long,
        timestamp: Long,
        recordNote: String,
        sensorId: Long?,
        isAutomatic: Boolean,
        recordSessionId: Long?,
        isExternalCommand: Boolean = false,
        recordVariantCount: Int = 0
    ): Boolean =
        (filters.fromInclusive == null || timestamp >= filters.fromInclusive) &&
            (filters.untilInclusive == null || timestamp <= filters.untilInclusive) &&
            (notes.isEmpty() || notes.any { recordNote.contains(it, ignoreCase = true) }) &&
            (filters.sensorKeys.isEmpty() || sensorId.filterSensorKey() in filters.sensorKeys) &&
            (filters.modes.isEmpty() || when {
                isExternalCommand -> UvirRecordFilterMode.EXTERNAL
                isAutomatic -> UvirRecordFilterMode.AUTOMATIC
                else -> UvirRecordFilterMode.MANUAL
            } in filters.modes) &&
            // Invalid nonempty ID selections must match nothing, not mean "all".
            (filters.recordIds.isEmpty() || id in recordIds) &&
            (filters.sessionIds.isEmpty() || recordSessionId != null && recordSessionId in sessionIds) &&
            (filters.variantCounts.isEmpty() || filters.variantCounts.any {
                when (it) {
                    -1 -> recordVariantCount > 1
                    0 -> recordVariantCount <= 1
                    else -> recordVariantCount == it
                }
            })
}
