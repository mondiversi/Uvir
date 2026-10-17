package me.mondiversi.uvir

import org.junit.Assert.*
import org.junit.Test
import java.time.ZoneId

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
        assertEquals(listOf(2L, 3L), ids(UvirRecordListFilters(notes = setOf("  GARD  "))))
    }
    @Test fun unicodeNoteSearchIsPreserved() {
        assertEquals(listOf(4L), ids(UvirRecordListFilters(notes = setOf("לילה"))))
        assertEquals(listOf(4L), ids(UvirRecordListFilters(notes = setOf("🌙"))))
    }
    @Test fun sensorsFilterByIdentityNotTheirRenameableName() {
        assertEquals(listOf(4L), ids(UvirRecordListFilters(sensorKeys = setOf("2"))))
        assertEquals(listOf(5L), ids(UvirRecordListFilters(sensorKeys = setOf("unknown"))))
    }
    @Test fun modesAreIndependentOfSessionMembership() {
        assertEquals(listOf(1L, 5L), ids(UvirRecordListFilters(modes = setOf(UvirRecordFilterMode.MANUAL))))
        assertEquals(listOf(2L, 3L, 4L), ids(UvirRecordListFilters(modes = setOf(UvirRecordFilterMode.AUTOMATIC))))
    }
    @Test fun externalModeIsDistinctFromManualAndAutomatic() {
        val external = SavedRecordSummary(
            id = 6, timestamp = 6000, note = "External", automatic = false,
            sensorId = 1, externalCommand = true
        )
        val withExternal = records + external
        assertEquals(listOf(6L), filterAcquisitionRecords(
            withExternal, UvirRecordListFilters(modes = setOf(UvirRecordFilterMode.EXTERNAL))
        ).map { it.id })
        assertEquals(listOf(1L, 5L), filterAcquisitionRecords(
            withExternal, UvirRecordListFilters(modes = setOf(UvirRecordFilterMode.MANUAL))
        ).map { it.id })
    }
    @Test fun idQueriesAreExactNotSubstringMatches() {
        assertEquals(listOf(2L), ids(UvirRecordListFilters(recordIds = setOf("2"))))
        assertEquals(listOf(2L, 3L), ids(UvirRecordListFilters(sessionIds = setOf("10"))))
        assertTrue(ids(UvirRecordListFilters(recordIds = setOf("20"))).isEmpty())
    }
    @Test fun sessionQueryDoesNotMatchMissingSessionOrZero() {
        assertTrue(ids(UvirRecordListFilters(sessionIds = setOf("0"))).isEmpty())
    }
    @Test fun invalidOrOverflowIdsDoNotAccidentallyMeanAll() {
        assertTrue(ids(UvirRecordListFilters(recordIds = setOf("abc"))).isEmpty())
        assertTrue(ids(UvirRecordListFilters(recordIds = setOf("9999999999999999999999"))).isEmpty())
    }
    @Test fun allCriteriaAreConjunctive() {
        assertEquals(listOf(3L), ids(UvirRecordListFilters(fromInclusive = 3000,
            notes = setOf("garden"), sensorKeys = setOf("1"),
            modes = setOf(UvirRecordFilterMode.AUTOMATIC), sessionIds = setOf("10"))))
    }
    @Test fun selectedDaysCoverTheWholeDayAndCannotRemainInverted() {
        val utc = ZoneId.of("UTC")
        val day = 86_400_000L
        val f = UvirRecordListFilters(day, day * 2 - 1)
        assertEquals(day * 4 - 1, f.withFromDay(day * 3, utc).untilInclusive)
        assertEquals(0L, f.withUntilDay(0L, utc).fromInclusive)
        assertEquals(day * 3, f.withFromDay(day * 3 + 1234, utc).fromInclusive)
        assertEquals(day * 2 - 1, f.withUntilDay(day + 1234, utc).untilInclusive)
    }
    @Test fun availableDateChoicesContainOnlyDistinctRecordedDays() {
        val utc = ZoneId.of("UTC")
        val day = 86_400_000L
        assertEquals(listOf(day * 2, day), uvirAvailableFilterDays(
            listOf(day * 2 + 1, day * 2 + 1000, day + 20), utc))
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
        assertEquals(1, ids(UvirRecordListFilters(recordIds = setOf("2"))).size)
        assertEquals(5, ids(UvirRecordListFilters()).size)
    }
    @Test fun variantFilterOffersAndMatchesOnlyCountsPresentInSavedSessions() {
        val withVariants = records.map { record ->
            when (record.id) {
                2L -> record.copy(positionIndex = 1, variantIndex = 1)
                3L -> record.copy(positionIndex = 1, variantIndex = 2)
                else -> record
            }
        }
        assertEquals(listOf(2), uvirAvailableVariantCounts(withVariants))
        assertEquals(listOf(2L, 3L), filterAcquisitionRecords(
            withVariants, UvirRecordListFilters(variantCounts = setOf(2))
        ).map { it.id })
        assertEquals(listOf(2L, 3L), filterAcquisitionRecords(
            withVariants, UvirRecordListFilters(variantCounts = setOf(-1))
        ).map { it.id })
        assertEquals(listOf(1L, 4L, 5L), filterAcquisitionRecords(
            withVariants, UvirRecordListFilters(variantCounts = setOf(0))
        ).map { it.id })
    }
    @Test fun alertsAreAlwaysAutomaticAndUseTheSamePredicate() {
        val entries = records.map { ThresholdAlertLogEntry(it.id, it.timestamp, "",
            it.sessionId, it.sessionSequence, it.note, it.sensorId) }
        assertEquals(listOf(2L, 3L), filterAlertEntries(entries,
            UvirRecordListFilters(notes = setOf("garden"), modes = setOf(UvirRecordFilterMode.AUTOMATIC))).map { it.id })
        assertTrue(filterAlertEntries(entries, UvirRecordListFilters(modes = setOf(UvirRecordFilterMode.MANUAL))).isEmpty())
    }
    @Test fun filteredAcquisitionSelectionCannotBecomeAnIncompleteFullSessionExport() {
        val all = records.map { SavedRecordDetail(it.id, it.timestamp, it.note,
            it.automatic, SensorSample(), it.sessionId, it.sessionSequence, it.sensorId) }
        val filteredIds = ids(UvirRecordListFilters(recordIds = setOf("2")))
        val plan = buildAcquisitionExportPlan(all.filter { it.id in filteredIds }, all)
        assertTrue(plan.hasPartialSessions)
        assertEquals(setOf(10L), plan.partialSessionIds)
        assertEquals(1, plan.items.size)
        assertTrue(plan.items.single() is AcquisitionExportItem.IndividualAcquisition)
    }
    @Test fun filteredAlertSelectionCannotBecomeAnIncompleteFullSessionExport() {
        val all = records.map { ThresholdAlertLogEntry(it.id, it.timestamp, "",
            it.sessionId, it.sessionSequence, it.note, it.sensorId) }
        val filtered = filterAlertEntries(all, UvirRecordListFilters(recordIds = setOf("2")))
        val plan = buildAlertExportPlan(filtered, all)
        assertTrue(plan.hasPartialSessions)
        assertEquals(1, plan.items.size)
    }
    @Test fun aSingleLetterOrSubstringMatchesAnywhereInTheNote() {
        assertEquals(listOf(1L, 2L, 3L), ids(UvirRecordListFilters(notes = setOf("r"))))
        assertEquals(listOf(1L), ids(UvirRecordListFilters(notes = setOf("ing u"))))
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

    @Test fun multipleSensorsAreAlternativesAndOtherFieldsRemainConjunctive() {
        assertEquals(listOf(1L, 2L, 3L, 4L), ids(UvirRecordListFilters(sensorKeys = setOf("1", "2"))))
        assertEquals(listOf(2L, 3L, 4L), ids(UvirRecordListFilters(sensorKeys = setOf("1", "2"),
            modes = setOf(UvirRecordFilterMode.AUTOMATIC))))
        assertEquals(listOf(4L, 5L), ids(UvirRecordListFilters(sensorKeys = setOf("2", "unknown"))))
    }

    @Test fun multipleNotesIdsSessionsAndVariantCountsUseOrWithinEachField() {
        assertEquals(listOf(2L, 3L, 4L), ids(UvirRecordListFilters(notes = setOf("Garden", "🌙"))))
        assertEquals(listOf(1L, 4L), ids(UvirRecordListFilters(recordIds = setOf("1", "4"))))
        assertEquals(listOf(2L, 3L, 4L), ids(UvirRecordListFilters(sessionIds = setOf("10", "20"))))
        val f = UvirRecordListFilters(variantCounts = setOf(2, 5))
        assertTrue(f.matches(1, 1, "", 1, true, 10, recordVariantCount = 2))
        assertTrue(f.matches(1, 1, "", 1, true, 10, recordVariantCount = 5))
        assertFalse(f.matches(1, 1, "", 1, true, 10, recordVariantCount = 4))
        assertFalse(f.matches(1, 1, "", 1, true, 10, recordVariantCount = 0))
    }

    @Test fun externalRecordsHaveOneModeEvenInsideAnotherSessionType() {
        val f = UvirRecordListFilters(modes = setOf(UvirRecordFilterMode.MANUAL, UvirRecordFilterMode.AUTOMATIC))
        assertFalse(f.matches(1, 1, "", 1, true, 10, isExternalCommand = true))
        assertTrue(f.copy(modes = f.modes + UvirRecordFilterMode.EXTERNAL)
            .matches(1, 1, "", 1, true, 10, isExternalCommand = true))
    }

    @Test fun multiChoiceStateRoundTripsWithoutLosingUnicodeOrDelimiters() {
        val f = UvirRecordListFilters(1000, 2000, setOf("مرحبا, 🌙\nא", "Garden"),
            setOf("1", "2", "unknown"), UvirRecordFilterMode.entries.toSet(),
            setOf("1", "2"), setOf("10", "20"), setOf(0, -1, 2, 5))
        assertEquals(f, UvirRecordListFilters.fromSavedValues(f.savedValues()))
        assertEquals(UvirRecordListFilters(), UvirRecordListFilters.fromSavedValues(UvirRecordListFilters().savedValues()))
    }

    @Test fun previousSingleChoiceStateIsUpgradedAndMalformedStateIsRejected() {
        val old = listOf("1000", "2000", "Garden", "1", "true", "2", "10", "false", "5")
        assertEquals(UvirRecordListFilters(1000, 2000, setOf("Garden"), setOf("1"),
            setOf(UvirRecordFilterMode.AUTOMATIC), setOf("2"), setOf("10"), setOf(5)),
            UvirRecordListFilters.fromSavedValues(old))
        assertNull(UvirRecordListFilters.fromSavedValues(listOf("multi-v1", "", "", "-1")))
        assertNull(UvirRecordListFilters.fromSavedValues(listOf("multi-v1", "", "", "99999")))
    }
}
