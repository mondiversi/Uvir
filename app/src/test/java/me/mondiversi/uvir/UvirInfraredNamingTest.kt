package me.mondiversi.uvir

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UvirInfraredNamingTest {
    @Test fun acquisitionGroupUsesInfraredWithoutChangingItsComponents() {
        val bars = acquisitionChartBars(SensorSample(f8 = 4.0, nir = 6.0))
            .filter { it.section == AcquisitionChartSection.FAR_RED_NIR }
        assertEquals("Infrared", AcquisitionChartSection.FAR_RED_NIR.exportTitle)
        assertEquals(listOf("IR", "FR", "NIR"), bars.map { it.shortLabel })
        assertEquals(listOf("Infrared", "Far-red", "NIR"), bars.map { it.exportLabel })
        assertEquals(listOf(10.0, 4.0, 6.0), bars.map { it.value })
        assertEquals(R.string.far_red_nir, bars.first().displayLabelResource)
    }

    @Test fun sessionSeriesUsesTheSameNameAndKeepsEveryReading() {
        val records = listOf(
            SavedRecordDetail(1L, 1L, "", false, SensorSample(f8 = 4.0, nir = 6.0)),
            SavedRecordDetail(2L, 2L, "", false, SensorSample(f8 = 17.0, nir = 9.0))
        )
        val series = sessionChartSeries(records, SessionChartGroup.FAR_RED_NIR)
        assertEquals("Infrared", SessionChartGroup.FAR_RED_NIR.exportTitle)
        assertEquals(listOf("Infrared", "Far-red", "NIR"), series.map { it.label })
        assertEquals(listOf(10.0, 26.0), series.first().values)
        assertEquals(R.string.far_red_nir, series.first().displayLabelResource)
    }

    @Test fun alertExportNameDistinguishesTheGroupFromItsIndividualChannels() {
        assertEquals("Infrared", thresholdAlertMetricExportFileLabel(ThresholdAlertMetric.NIR_TOTAL))
        assertEquals("Far_Red", thresholdAlertMetricExportFileLabel(ThresholdAlertMetric.FAR_RED))
        assertEquals("NIR", thresholdAlertMetricExportFileLabel(ThresholdAlertMetric.NIR))
    }

    @Test fun csvChangesOnlyTheGroupHeaderAndKeepsColumnOrder() {
        assertTrue("Infrarosso_uW_cm2" in MEASUREMENT_EXPORT_COLUMNS_IT)
        assertTrue("Infrared_uW_cm2" in MEASUREMENT_EXPORT_COLUMNS_EN)
        assertFalse("FarRed_NIR_uW_cm2" in MEASUREMENT_EXPORT_COLUMNS_IT)
        assertFalse("FarRed_NIR_uW_cm2" in MEASUREMENT_EXPORT_COLUMNS_EN)
        assertEquals(22, MEASUREMENT_EXPORT_COLUMNS_IT.indexOf("Infrarosso_uW_cm2"))
        assertEquals(22, MEASUREMENT_EXPORT_COLUMNS_EN.indexOf("Infrared_uW_cm2"))
        assertEquals(MEASUREMENT_EXPORT_COLUMNS_IT.size, MEASUREMENT_EXPORT_COLUMNS_EN.size)
    }
}
