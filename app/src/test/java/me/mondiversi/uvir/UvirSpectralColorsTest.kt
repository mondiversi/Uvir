package me.mondiversi.uvir

import androidx.compose.ui.graphics.Color
import java.io.File
import org.junit.Assert.*
import org.junit.Test

class UvirSpectralColorsTest {
    private val expected = linkedMapOf(
        ThresholdAlertMetric.UV_TOTAL to 0xFF6A1B9A, ThresholdAlertMetric.UVC to 0xFF9C27B0,
        ThresholdAlertMetric.UVB to 0xFF673AB7, ThresholdAlertMetric.UVA to 0xFF3F51B5,
        ThresholdAlertMetric.VISIBLE_TOTAL to 0xFF00897B, ThresholdAlertMetric.HEV to 0xFF3949AB,
        ThresholdAlertMetric.VIOLET to 0xFF8E24AA, ThresholdAlertMetric.BLUE to 0xFF1E88E5,
        ThresholdAlertMetric.GREEN to 0xFF43A047, ThresholdAlertMetric.YELLOW to 0xFFFDD835,
        ThresholdAlertMetric.ORANGE to 0xFFFB8C00, ThresholdAlertMetric.RED to 0xFFE53935,
        ThresholdAlertMetric.NIR_TOTAL to 0xFF6D4C41, ThresholdAlertMetric.FAR_RED to 0xFFD32F2F,
        ThresholdAlertMetric.NIR to 0xFF8D6E63,
        ThresholdAlertMetric.BIO_DNA_UV to 0xFF673AB7,
        ThresholdAlertMetric.BIO_UVA_PHOTOAGING to 0xFF3F51B5,
        ThresholdAlertMetric.BIO_HEV_OXIDATIVE to 0xFF1E88E5
    )

    @Test fun palettePreservesTheOriginalMainViewColorsIncludingTotalsAndBiologicalEffects() {
        expected.forEach { (metric, argb) ->
            assertEquals(metric.name, Color(argb), uvirSpectralColor(metric))
            assertEquals(metric.name, Color(argb), thresholdAlertMetricDisplayColor(metric))
        }
        assertEquals(uvirSpectralColor(ThresholdAlertMetric.VIOLET), uvirSpectralColor(ThresholdAlertMetric.HEB))
    }

    @Test fun allSavedAndAlertChartProducersUseTheSamePaletteForEachMetric() {
        val sample = SensorSample(uvc = 1.0, uvb = 2.0, uva = 3.0, violetto = 4.0,
            blu = 5.0, verde = 6.0, giallo = 7.0, arancione = 8.0, rosso = 9.0, f8 = 10.0, nir = 11.0)
        val record = SavedRecordDetail(1, 1_000, "Fixture", false, sample)
        for (group in SessionChartGroup.entries) {
            val metrics = savedAcquisitionMetrics(group)
            val session = sessionChartSeries(listOf(record), group)
            assertEquals(metrics.size, session.size)
            metrics.zip(session).forEach { (metric, item) ->
                assertEquals("Session $metric", Color(expected.getValue(metric)), item.color)
            }
        }
        for (group in AcquisitionChartGroup.entries) {
            val metrics = if (group == AcquisitionChartGroup.BIOLOGICAL) savedAcquisitionMetrics(SessionChartGroup.BIOLOGICAL)
                else SessionChartGroup.entries.filterNot { it.biological }.flatMap(::savedAcquisitionMetrics)
            val bars = acquisitionChartBars(sample, group)
            assertEquals(metrics.size, bars.size)
            metrics.zip(bars).forEach { (metric, bar) ->
                assertEquals("Acquisition $metric", Color(expected.getValue(metric)), bar.color)
            }
        }
        val entry = ThresholdAlertLogEntry(1, 1_000,
            expected.keys.joinToString(";") { "${it.name}|2.0|ABOVE|1.0" }, sessionId = 1)
        alertChartBars(entry).forEach { assertEquals("Alert ${it.metric}", Color(expected.getValue(it.metric)), it.color) }
        alertSessionChartSeries(listOf(entry)).forEach {
            assertEquals("Alert session ${it.metric}", Color(expected.getValue(it.metric)), it.color)
        }
    }

    @Test fun renderersCannotIntroduceAnotherLocalPaletteOrSavedLabelTint() {
        val root = File("src/main/java/me/mondiversi/uvir")
        for (name in listOf("UvirSensorSpectrum.kt", "UvirAcquisitionChartData.kt", "UvirSessionChartData.kt")) {
            val source = File(root, name).readText()
            assertFalse("Local palette in $name", Regex("Color\\(0x").containsMatchIn(source))
            assertTrue("Shared palette in $name", source.contains("uvirSpectralColor("))
        }
        for (name in listOf("UvirSessionAcquisitionValues.kt", "UvirAlertSessionValues.kt")) {
            val source = File(root, name).readText()
            assertFalse(name, source.contains("uvirSessionValueLabelColor"))
            assertFalse(name, source.contains("lerp("))
        }
    }
}
