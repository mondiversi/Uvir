package me.mondiversi.uvir

import org.junit.Assert.*
import org.junit.Test

class UvirSavedValueDisplayTest {
    private val sample = SensorSample(uvc = 1.0, uvb = 1.0, uva = 2.0,
        violetto = 2.0, blu = 2.0, verde = 2.0, giallo = 2.0, arancione = 2.0, rosso = 2.0,
        f8 = 2.0, nir = 2.0)
    private val initial = UvirSavedValueDisplay()
    private fun share(state: UvirSavedValueDisplay, group: SessionChartGroup, metric: ThresholdAlertMetric,
        total: Boolean = false, source: SensorSample = sample): Float? =
        state.share(group, total, state.acquisitionValue(metric, source), source, 2, UvirIrradianceUnit.W_M2)

    @Test fun groupBasisAffectsOnlyChildrenAndNeverTheGroupTotalShare() {
        val grouped = initial.copy(spectrum = initial.spectrum.togglePercentageBase(SensorGroup.UV))
        assertEquals(.05f, share(initial, SessionChartGroup.UV, ThresholdAlertMetric.UVC)!!, 0f)
        assertEquals(.25f, share(grouped, SessionChartGroup.UV, ThresholdAlertMetric.UVC)!!, 0f)
        for (state in listOf(initial, grouped)) {
            assertEquals(.2f, share(state, SessionChartGroup.UV, ThresholdAlertMetric.UV_TOTAL, true)!!, 0f)
            assertEquals(.1f, share(state, SessionChartGroup.VISIBLE, ThresholdAlertMetric.BLUE)!!, 0f)
        }
    }

    @Test fun excludedChannelsRecalculateVisibleGroupAndGrandTotals() {
        val hidden = initial.toggleAcquisition(ThresholdAlertMetric.UVC)
        assertFalse(hidden.acquisitionVisible(ThresholdAlertMetric.UVC))
        assertEquals(3.0, hidden.acquisitionValue(ThresholdAlertMetric.UV_TOTAL, sample), 0.0)
        assertEquals(.158f, share(hidden, SessionChartGroup.UV, ThresholdAlertMetric.UV_TOTAL, true)!!, 0f)
        assertEquals(.105f, share(hidden, SessionChartGroup.UV, ThresholdAlertMetric.UVA)!!, 0f)
        val grouped = hidden.copy(spectrum = hidden.spectrum.togglePercentageBase(SensorGroup.UV))
        assertEquals(.667f, share(grouped, SessionChartGroup.UV, ThresholdAlertMetric.UVA)!!, 0f)
        assertEquals(initial, hidden.toggleAcquisition(ThresholdAlertMetric.UVC))
    }

    @Test fun sessionSharesUseEachRecordsOwnSpectrumRatherThanAnAccumulatedSession() {
        val other = sample.copy(uvb = 4.0, uva = 5.0)
        assertEquals(.038f, share(initial, SessionChartGroup.UV, ThresholdAlertMetric.UVC, source = other)!!, 0f)
        assertEquals(.385f, share(initial, SessionChartGroup.UV, ThresholdAlertMetric.UV_TOTAL, true, other)!!, 0f)
        assertEquals(.05f, share(initial, SessionChartGroup.UV, ThresholdAlertMetric.UVC)!!, 0f)
        val grouped = initial.copy(spectrum = initial.spectrum.togglePercentageBase(SensorGroup.UV))
        assertEquals(.1f, share(grouped, SessionChartGroup.UV, ThresholdAlertMetric.UVC, source = other)!!, 0f)
        assertEquals(.25f, share(grouped, SessionChartGroup.UV, ThresholdAlertMetric.UVC)!!, 0f)
    }

    @Test fun overlappingHevIsNeverCountedTwiceAndItsComponentsStayConsistent() {
        val hidden = initial.toggleAcquisition(ThresholdAlertMetric.HEV)
        for (metric in listOf(ThresholdAlertMetric.HEV, ThresholdAlertMetric.VIOLET, ThresholdAlertMetric.BLUE)) {
            assertFalse(hidden.acquisitionVisible(metric))
        }
        assertEquals(8.0, hidden.acquisitionValue(ThresholdAlertMetric.VISIBLE_TOTAL, sample), 0.0)
        assertEquals(.5f, share(hidden, SessionChartGroup.VISIBLE, ThresholdAlertMetric.VISIBLE_TOTAL, true)!!, 0f)
        val partial = hidden.toggleAcquisition(ThresholdAlertMetric.BLUE)
        assertTrue(partial.acquisitionVisible(ThresholdAlertMetric.HEV))
        assertEquals(2.0, partial.acquisitionValue(ThresholdAlertMetric.HEV, sample), 0.0)
    }

    @Test fun biologicalVisibilityIsIndependentFromSpectralExclusionsAndNeverChangesTheProxy() {
        val hidden = initial.toggleAcquisition(ThresholdAlertMetric.BIO_DNA_UV)
        assertFalse(hidden.acquisitionVisible(ThresholdAlertMetric.BIO_DNA_UV))
        assertTrue(hidden.acquisitionVisible(ThresholdAlertMetric.UVC))
        assertTrue(hidden.acquisitionVisible(ThresholdAlertMetric.BIO_UVA_PHOTOAGING))
        assertEquals(sample.thresholdMetricValue(ThresholdAlertMetric.BIO_DNA_UV),
            hidden.acquisitionValue(ThresholdAlertMetric.BIO_DNA_UV, sample), 0.0)
        assertTrue(initial.toggleAcquisition(ThresholdAlertMetric.UV_TOTAL)
            .acquisitionVisible(ThresholdAlertMetric.BIO_DNA_UV))
    }

    @Test fun independentBiologicalAcquisitionValuesCanBeRestoredWithoutAffectingOtherValues() {
        val hidden = initial.toggleAcquisition(ThresholdAlertMetric.BIO_DNA_UV)
            .toggleAcquisition(ThresholdAlertMetric.BIO_UVA_PHOTOAGING)
        assertFalse(hidden.acquisitionVisible(ThresholdAlertMetric.BIO_DNA_UV))
        assertFalse(hidden.acquisitionVisible(ThresholdAlertMetric.BIO_UVA_PHOTOAGING))
        assertTrue(hidden.acquisitionVisible(ThresholdAlertMetric.BIO_HEV_OXIDATIVE))
        assertTrue(hidden.acquisitionVisible(ThresholdAlertMetric.UVC))
        val restored = hidden.toggleAcquisition(ThresholdAlertMetric.BIO_DNA_UV)
        assertTrue(restored.acquisitionVisible(ThresholdAlertMetric.BIO_DNA_UV))
        assertFalse(restored.acquisitionVisible(ThresholdAlertMetric.BIO_UVA_PHOTOAGING))
        assertEquals(initial, restored.toggleAcquisition(ThresholdAlertMetric.BIO_UVA_PHOTOAGING))
    }

    @Test fun chartSelectionsAgreeWithTheTablesWithoutMutatingRecordsOrRawChartData() {
        val original = sample.copy()
        val records = listOf(SavedRecordDetail(1, 1, "", false, sample),
            SavedRecordDetail(2, 2, "", false, sample.copy(uva = 5.0)))
        val before = records.map { it.copy() }
        val hidden = initial.toggleAcquisition(ThresholdAlertMetric.UVC).toggleAcquisition(ThresholdAlertMetric.BLUE)
        val bars = savedAcquisitionChartBars(sample, AcquisitionChartGroup.IRRADIANCE, hidden)
        assertFalse(bars.any { it.shortLabel == "UVC" || it.shortLabel == "B" })
        assertEquals(3.0, bars.single { it.shortLabel == "UV" }.value, 0.0)
        val series = savedSessionChartSeries(records, SessionChartGroup.UV, hidden)
        assertFalse(series.any { it.label == "UVC" })
        assertEquals(listOf(3.0, 6.0), series.first().values)
        assertEquals(listOf(4.0, 7.0), sessionChartSeries(records, SessionChartGroup.UV).first().values)
        assertEquals(before, records)
        assertEquals(original, sample)
    }

    @Test fun everyMappedTableChannelHasTheOriginalReadingAndSeriesPosition() {
        val records = listOf(SavedRecordDetail(1, 1, "", false, sample))
        for (group in SessionChartGroup.entries) {
            val metrics = savedAcquisitionMetrics(group)
            val series = sessionChartSeries(records, group)
            assertEquals(metrics.size, series.size)
            metrics.forEachIndexed { index, metric ->
                assertEquals(series[index].values.single(), initial.acquisitionValue(metric, sample), 0.0)
            }
        }
    }

    @Test fun allExcludedReadingsProduceFiniteZeroTotalsAndEmptyBiologicalPlots() {
        val hidden = SessionChartGroup.entries.flatMap(::savedAcquisitionMetrics).filterNot {
            it in setOf(ThresholdAlertMetric.UV_TOTAL, ThresholdAlertMetric.VISIBLE_TOTAL,
                ThresholdAlertMetric.NIR_TOTAL, ThresholdAlertMetric.HEV)
        }.fold(initial) { state, metric -> state.toggleAcquisition(metric) }
        val bars = savedAcquisitionChartBars(sample, AcquisitionChartGroup.IRRADIANCE, hidden)
        assertEquals(3, bars.size)
        assertTrue(bars.all { it.value == 0.0 && !it.outOfRange })
        assertTrue(savedAcquisitionChartBars(sample, AcquisitionChartGroup.BIOLOGICAL, hidden).isEmpty())
        for (group in listOf(SessionChartGroup.UV, SessionChartGroup.VISIBLE, SessionChartGroup.FAR_RED_NIR)) {
            assertEquals(0f, share(hidden, group, savedAcquisitionMetrics(group).first(), true)!!, 0f)
        }
    }
}
