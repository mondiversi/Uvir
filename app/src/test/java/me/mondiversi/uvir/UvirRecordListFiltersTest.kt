package me.mondiversi.uvir

import org.junit.Assert.*
import org.junit.Test

class UvirRecordListFiltersTest {
    private val records = listOf(
        SavedRecordSummary(1, 1000, "Morning UV", false, sensorId = 1),
        SavedRecordSummary(2, 2000, "Garden", true, 10, 1, 1),
        SavedRecordSummary(3, 3000, "Garden", true, 10, 2, 1),
        SavedRecordSummary(4, 4000, "לילה 🌙", true, 20, 1, 2),
        SavedRecordSummary(5, 5000, "", false)
    )
    private fun ids(f: UvirRecordListFilters) = filterAcquisitionRecords(records, f).map { it.id }

    @Test fun inactiveFiltersReturnTheOriginalList() {
        assertFalse(UvirRecordListFilters().isActive)
        assertSame(records, filterAcquisitionRecords(records, UvirRecordListFilters()))
    }
    @Test fun dateRangeIncludesBothBoundaries() {
        assertEquals(listOf(2L, 3L), ids(UvirRecordListFilters(2000, 3000)))
    }
    @Test fun openDateBoundsWorkIndependently() {
        assertEquals(listOf(4L, 5L), ids(UvirRecordListFilters(fromInclusive = 4000)))
        assertEquals(listOf(1L, 2L), ids(UvirRecordListFilters(untilInclusive = 2000)))
    }
    @Test fun noteSearchIsCaseInsensitiveAndTrimsQuery() {
        assertEquals(listOf(2L, 3L), ids(UvirRecordListFilters(note = "  GARD  ")))
    }
    @Test fun unicodeNoteSearchIsPreserved() {
        assertEquals(listOf(4L), ids(UvirRecordListFilters(note = "לילה")))
        assertEquals(listOf(4L), ids(UvirRecordListFilters(note = "🌙")))
    }
    @Test fun sensorsFilterByIdentityNotTheirRenameableName() {
        assertEquals(listOf(4L), ids(UvirRecordListFilters(sensorKey = "2")))
        assertEquals(listOf(5L), ids(UvirRecordListFilters(sensorKey = "unknown")))
    }
    @Test fun modesAreIndependentOfSessionMembership() {
        assertEquals(listOf(1L, 5L), ids(UvirRecordListFilters(automatic = false)))
        assertEquals(listOf(2L, 3L, 4L), ids(UvirRecordListFilters(automatic = true)))
    }
    @Test fun idQueriesAreExactNotSubstringMatches() {
        assertEquals(listOf(2L), ids(UvirRecordListFilters(recordId = "2")))
        assertEquals(listOf(2L, 3L), ids(UvirRecordListFilters(sessionId = "10")))
        assertTrue(ids(UvirRecordListFilters(recordId = "20")).isEmpty())
    }
    @Test fun sessionQueryDoesNotMatchMissingSessionOrZero() {
        assertTrue(ids(UvirRecordListFilters(sessionId = "0")).isEmpty())
    }
    @Test fun invalidOrOverflowIdsDoNotAccidentallyMeanAll() {
        assertTrue(ids(UvirRecordListFilters(recordId = "abc")).isEmpty())
        assertTrue(ids(UvirRecordListFilters(recordId = "9999999999999999999999")).isEmpty())
    }
    @Test fun allCriteriaAreConjunctive() {
        assertEquals(listOf(3L), ids(UvirRecordListFilters(fromInclusive = 3000,
            note = "garden", sensorKey = "1", automatic = true, sessionId = "10")))
    }
    @Test fun changedRangeCannotRemainInverted() {
        val f = UvirRecordListFilters(120_000, 179_999)
        assertEquals(299_999L, f.withFrom(240_000).untilInclusive)
        assertEquals(60_000L, f.withUntil(119_999).fromInclusive)
        assertEquals(59_999L, f.withFrom(240_000).untilInclusive!! - 240_000L)
        assertEquals(59_999L, 119_999L - f.withUntil(119_999).fromInclusive!!)
    }
    @Test fun localizedDecimalDigitsAreCanonicalized() {
        assertEquals("123", normalizeRecordFilterId("١٢٣"))
        assertEquals("123", normalizeRecordFilterId("۱۲۳"))
        assertEquals("123", normalizeRecordFilterId("１２３"))
    }
    @Test fun negativeIdentifierDoesNotBecomeAPositiveRecordSelection() {
        assertEquals("0", normalizeRecordFilterId("-12"))
    }
    @Test fun identifierLengthIsBoundedAndNonDigitsAreIgnored() {
        assertEquals("123", normalizeRecordFilterId("x123"))
        assertEquals(18, normalizeRecordFilterId("9".repeat(50)).length)
    }
    @Test fun resetRestoresAllRecords() {
        assertEquals(1, ids(UvirRecordListFilters(recordId = "2")).size)
        assertEquals(5, ids(UvirRecordListFilters()).size)
    }
    @Test fun alertsAreAlwaysAutomaticAndUseTheSamePredicate() {
        val entries = records.map { ThresholdAlertLogEntry(it.id, it.timestamp, "",
            it.sessionId, it.sessionSequence, it.note, it.sensorId) }
        assertEquals(listOf(2L, 3L), filterAlertEntries(entries,
            UvirRecordListFilters(note = "garden", automatic = true)).map { it.id })
        assertTrue(filterAlertEntries(entries, UvirRecordListFilters(automatic = false)).isEmpty())
    }
    @Test fun filteredAcquisitionSelectionCannotBecomeAnIncompleteFullSessionExport() {
        val all = records.map { SavedRecordDetail(it.id, it.timestamp, it.note,
            it.automatic, SensorSample(), it.sessionId, it.sessionSequence, it.sensorId) }
        val filteredIds = ids(UvirRecordListFilters(recordId = "2"))
        val plan = buildAcquisitionExportPlan(all.filter { it.id in filteredIds }, all)
        assertTrue(plan.hasPartialSessions)
        assertEquals(setOf(10L), plan.partialSessionIds)
        assertEquals(1, plan.items.size)
        assertTrue(plan.items.single() is AcquisitionExportItem.IndividualAcquisition)
    }
    @Test fun filteredAlertSelectionCannotBecomeAnIncompleteFullSessionExport() {
        val all = records.map { ThresholdAlertLogEntry(it.id, it.timestamp, "",
            it.sessionId, it.sessionSequence, it.note, it.sensorId) }
        val filtered = filterAlertEntries(all, UvirRecordListFilters(recordId = "2"))
        val plan = buildAlertExportPlan(filtered, all)
        assertTrue(plan.hasPartialSessions)
        assertEquals(1, plan.items.size)
    }
    @Test fun aSingleLetterOrSubstringMatchesAnywhereInTheNote() {
        assertEquals(listOf(1L, 2L, 3L), ids(UvirRecordListFilters(note = "r")))
        assertEquals(listOf(1L), ids(UvirRecordListFilters(note = "ing u")))
    }

    @Test fun oneDayNeedsBothBoundsWhileFromOnlyIncludesFollowingDays() {
        val day = 86_400_000L
        val list = listOf(
            records[0].copy(timestamp = day - 1),
            records[1].copy(timestamp = day),
            records[2].copy(timestamp = day * 2 - 1),
            records[3].copy(timestamp = day * 2)
        )
        assertEquals(listOf(2L, 3L, 4L), filterAcquisitionRecords(list,
            UvirRecordListFilters(fromInclusive = day)).map { it.id })
        assertEquals(listOf(2L, 3L), filterAcquisitionRecords(list,
            UvirRecordListFilters(fromInclusive = day, untilInclusive = day * 2 - 1)).map { it.id })
    }

}
