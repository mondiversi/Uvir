package me.mondiversi.uvir

import org.junit.Assert.*
import org.junit.Test

class UvirCompactSpectralBandsTest {
    @Test fun liveHardwareCaptionsUseTheReportedModelsForTheCorrectGroups() {
        val info = UvirSensorRuntimeInfo(uvSensorModel = " UV-TEST ", visibleSensorModel = " VIS-TEST ")
        assertEquals("UV-TEST", liveSpectralSensorModel(SensorGroup.UV, info))
        assertEquals("VIS-TEST", liveSpectralSensorModel(SensorGroup.VISIBLE, info))
        assertEquals("VIS-TEST", liveSpectralSensorModel(SensorGroup.NIR, info))
        assertEquals("", liveSpectralSensorModel(SensorGroup.BIOLOGICAL, info))
    }

    @Test fun legacyOrEmptyRuntimeInfoFallsBackToTheCurrentHardwareWithoutChangingChannelReferences() {
        for (info in listOf(null, UvirSensorRuntimeInfo(), UvirSensorRuntimeInfo(uvSensorModel = " ", visibleSensorModel = " "))) {
            assertEquals("AS7331", liveSpectralSensorModel(SensorGroup.UV, info))
            assertEquals("AS7343", liveSpectralSensorModel(SensorGroup.VISIBLE, info))
            assertEquals("AS7343", liveSpectralSensorModel(SensorGroup.NIR, info))
        }
        assertNull(sensorChannelReference(R.string.uv_radiation))
        assertNull(sensorChannelReference(R.string.visible_light))
        assertNull(sensorChannelReference(R.string.far_red_nir))
        assertEquals("405 / 425 nm", sensorChannelReference(R.string.violet))
    }

    @Test fun uvLabelsRetainCorrectedNominalChannelLimits() {
        assertEquals("240–280 nm", UvirSensorBands.UVC)
        assertEquals("280–315 nm", UvirSensorBands.UVB)
        assertEquals("315–410 nm", UvirSensorBands.UVA)
        assertEquals("240–410 nm", UvirSensorBands.UV_CHANNELS)
    }

    @Test fun visibleReferencesAreActualChannelPeaksRatherThanColourIntervals() {
        val references = listOf(UvirSensorBands.VIOLET_CHANNELS, UvirSensorBands.BLUE_CHANNELS,
            UvirSensorBands.GREEN_CHANNEL, UvirSensorBands.GREEN_YELLOW_CHANNELS,
            UvirSensorBands.ORANGE_CHANNEL, UvirSensorBands.RED_CHANNEL)
        assertEquals(listOf("405 / 425 nm", "450 / 475 nm", "515 nm",
            "550 / 555 nm", "600 nm", "640 nm"), references)
        assertTrue(references.all { '≈' !in it && '–' !in it && '\n' !in it })
    }

    @Test fun sharedLabelsNeverInventAPassbandForTotalsOrBiologicalEffects() {
        assertEquals("Green-yellow · 550 / 555 nm", sensorChannelLabel("Green-yellow", R.string.yellow))
        assertEquals(UvirSensorBands.BLUE_CHANNELS, sensorChannelReference(R.string.threshold_channel_heb))
        assertEquals("UVC · 240–280 nm", sensorChannelLabel("UVC", null, "UVC"))
        for (label in listOf(R.string.visible_light, R.string.uv_radiation, R.string.far_red_nir,
            R.string.threshold_channel_uv_total, R.string.threshold_channel_visible_total,
            R.string.threshold_channel_nir_total, R.string.threshold_channel_hev, R.string.dna_uv_proxy)) {
            assertNull(sensorChannelReference(label))
            assertEquals("Total", sensorChannelLabel("Total", label))
        }
    }

    @Test fun chartAndExportRenamingDoesNotChangeStoredReadingsOrColumnOrder() {
        val sample = SensorSample(giallo = 7.25)
        val bar = acquisitionChartBars(sample).single { it.displayLabelResource == R.string.yellow }
        assertEquals("Green-yellow", bar.exportLabel)
        assertEquals(7.25, bar.value, 0.0)
        val record = SavedRecordDetail(1L, 1L, "", false, sample)
        val series = sessionChartSeries(listOf(record), SessionChartGroup.VISIBLE)
            .single { it.displayLabelResource == R.string.yellow }
        assertEquals("Green-yellow", series.label)
        assertEquals(listOf(7.25), series.values)
        assertEquals(25, MEASUREMENT_EXPORT_COLUMNS_EN.indexOf("Green_yellow_peaks_550_555_nm_uW_cm2"))
        assertEquals(25, MEASUREMENT_EXPORT_COLUMNS_IT.indexOf("Verde_giallo_peaks_550_555_nm_uW_cm2"))
        assertEquals(17, MEASUREMENT_EXPORT_COLUMNS_EN.indexOf("HEV_uW_cm2"))
        assertFalse(MEASUREMENT_EXPORT_COLUMNS_EN.any { "400_500" in it || "570_590" in it })
    }

    @Test fun hevAggregateKeepsItsValueButNeverAddsAWavelengthList() {
        assertNull(sensorChannelReference(R.string.threshold_channel_hev))
        assertNull(sensorChannelReference(null, "HEV"))
        assertEquals("HEV", sensorChannelLabel("HEV", R.string.threshold_channel_hev, "HEV"))
        assertEquals("HEV", sensorChannelLabel("HEV", null, "HEV"))
        val sample = SensorSample(violetto = 1.25, blu = 2.75)
        assertEquals(4.0, sample.thresholdMetricValue(ThresholdAlertMetric.HEV), 0.0)
        assertTrue(MEASUREMENT_EXPORT_COLUMNS_EN.none { it.startsWith("HEV_") && "nm" in it })
        assertTrue(MEASUREMENT_EXPORT_COLUMNS_IT.none { it.startsWith("HEV_") && "nm" in it })
    }

    @Test fun infraredListsBothFarRedContributorsWithoutInventingContinuousCoverage() {
        assertEquals("690 / 745 / 855 nm", UvirSensorBands.INFRARED_CHANNELS)
        assertEquals("690 / 745 nm", UvirSensorBands.FAR_RED_CHANNELS)
        assertEquals("855 nm", UvirSensorBands.NIR_CHANNEL)
        assertFalse(UvirSensorBands.INFRARED_CHANNELS.contains("–"))
    }
}
