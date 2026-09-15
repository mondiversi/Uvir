package me.mondiversi.uvir

import org.junit.Assert.assertEquals
import org.junit.Test

class UvirBiologicalChartDataTest {
    private val sample =
        SensorSample(
            uvc = 10.0,
            uvb = 20.0,
            uva = 30.0,
            violetto = 40.0,
            blu = 50.0
        )

    @Test
    fun acquisitionChartContainsAllBiologicalEffects() {
        val expected = biologicalEffects(sample)
        val bars =
            acquisitionChartBars(
                sample,
                AcquisitionChartGroup.BIOLOGICAL
            )

        assertEquals(listOf("DNA", "UVA", "HEV"), bars.map { it.shortLabel })
        assertEquals(expected.dnaUvProxy, bars[0].value, 0.0)
        assertEquals(expected.uvaPhotoagingProxy, bars[1].value, 0.0)
        assertEquals(expected.hevOxidativeProxy, bars[2].value, 0.0)
    }

    @Test
    fun irradianceChartContainsDerivedBandsAndSectionValues() {
        val bars =
            acquisitionChartBars(
                sample,
                AcquisitionChartGroup.IRRADIANCE
            )
        val byLabel = bars.associateBy { it.shortLabel }

        assertEquals(60.0, byLabel.getValue("UV").value, 0.0)
        assertEquals(90.0, byLabel.getValue("HEV").value, 0.0)
        assertEquals(90.0, byLabel.getValue("VIS").value, 0.0)
        assertEquals(0.0, byLabel.getValue("IR").value, 0.0)
        assertEquals(
            AcquisitionChartSection.VISIBLE,
            byLabel.getValue("HEV").section
        )
        assertEquals(
            listOf("UV", "UVC", "UVB", "UVA"),
            bars.filter { it.section == AcquisitionChartSection.UV }
                .map { it.shortLabel }
        )
        assertEquals(
            listOf("VIS", "HEV", "V", "B", "G", "Y", "O", "R"),
            bars.filter { it.section == AcquisitionChartSection.VISIBLE }
                .map { it.shortLabel }
        )
        assertEquals(
            listOf("IR", "FR", "NIR"),
            bars.filter { it.section == AcquisitionChartSection.FAR_RED_NIR }
                .map { it.shortLabel }
        )
    }

    @Test
    fun sessionChartContainsAllBiologicalEffectsForEveryRecord() {
        val records =
            listOf(
                savedRecord(id = 1L, sample = sample),
                savedRecord(
                    id = 2L,
                    sample = sample.copy(uva = 60.0)
                )
            )
        val series =
            sessionChartSeries(
                records,
                SessionChartGroup.BIOLOGICAL
            )

        assertEquals(3, series.size)
        assertEquals(2, series[0].values.size)
        assertEquals(
            biologicalEffects(records[1].sample).uvaPhotoagingProxy,
            series[1].values[1],
            0.0
        )
    }

    @Test
    fun sessionIrradianceChartsStartWithSectionTotals() {
        val records = listOf(savedRecord(id = 1L, sample = sample))

        val uv = sessionChartSeries(records, SessionChartGroup.UV)
        val visible = sessionChartSeries(records, SessionChartGroup.VISIBLE)
        val farRed = sessionChartSeries(records, SessionChartGroup.FAR_RED_NIR)

        assertEquals("Ultraviolet", uv.first().label)
        assertEquals(60.0, uv.first().values.single(), 0.0)
        assertEquals("Visible light", visible.first().label)
        assertEquals(90.0, visible.first().values.single(), 0.0)
        assertEquals("Infrared", farRed.first().label)
        assertEquals(0.0, farRed.first().values.single(), 0.0)
    }

    private fun savedRecord(
        id: Long,
        sample: SensorSample
    ) =
        SavedRecordDetail(
            id = id,
            timestamp = id * 1_000L,
            note = "",
            automatic = true,
            sample = sample
        )
}
