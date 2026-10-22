package me.mondiversi.uvir

import org.junit.Assert.*
import org.junit.Test

class UvirOpticalWindowTest {
    @Test fun defaultsPreserveExistingCalibrationAndLegacyCommands() {
        val settings = SensorCalibrationSettings(1.2f, .9f)
        assertFalse(settings.opticalWindow.enabled)
        assertTrue(settings.opticalWindow.factors.all { it == 1f })
        assertEquals("CALIBRATION_CONFIG 1.2 0.9", sensorCalibrationCommand(settings))
        assertEquals(settings, parseSensorCalibrationCommand(sensorCalibrationCommand(settings)))
    }

    @Test fun everyBandAndDisabledRetainedFactorsRoundTripIndependently() {
        for (enabled in listOf(false, true)) {
            val window = OpticalWindowSettings(enabled, List(11) { 1f + it / 10f })
            val settings = SensorCalibrationSettings(1.25f, .75f, window)
            val command = sensorCalibrationCommand(settings)
            assertEquals(15, command.split(' ').size)
            assertEquals(settings, parseSensorCalibrationCommand(command))
        }
    }

    @Test fun invalidIncompleteOrNonFiniteFactorsAreRejected() {
        assertNull(parseSensorCalibrationCommand("CALIBRATION_CONFIG NaN 1"))
        assertNull(parseSensorCalibrationCommand("CALIBRATION_CONFIG 1 1 ON 1"))
        assertNull(parseSensorCalibrationCommand("CALIBRATION_CONFIG 1 1 extra"))
        assertNull(parseSensorCalibrationCommand("CALIBRATION_CONFIG 1 1 ON " + List(11) { "0.9" }.joinToString(" ")))
    }

    @Test fun oldSamplesRemainUnknownAndMixedContextsAreNotInvented() {
        val window = OpticalWindowSettings(true, List(11) { 1.1f })
        val old = SensorSample(uvc = 10.0)
        val known = old.copy(opticalWindow = window)
        assertNull(combineSamples(listOf(old, known), false).opticalWindow)
        assertEquals(window, combineSamples(listOf(known, known), false).opticalWindow)
        assertEquals(10.0, combineSamples(listOf(old, known), false).uvc, 0.0)
        assertEquals("—", opticalWindowSummary(null))
        assertEquals("×1", opticalWindowSummary(OpticalWindowSettings()))
    }

    @Test fun bandDescriptionsAndExportsDoNotClaimUnmeasuredUvc() {
        assertEquals("240–280 nm", UvirSensorBands.UVC)
        assertEquals("315–410 nm", UvirSensorBands.UVA)
        for (language in listOf("en", "it")) {
            val headers = measurementExportColumns(language)
            assertTrue(headers.any { it.startsWith("UVC_240_280_nm") })
            assertFalse(headers.any { it.contains("100_280") })
            assertTrue("Optical_window" in headers)
            assertEquals(hardwareCsvColumns(language), headers.takeLast(2))
        }
    }

    @Test fun unsupportedFirmwareCannotReceiveExtendedSettingsOrOutboxReplays() {
        val base = sensorCalibrationCommand(SensorCalibrationSettings())
        val extended = sensorCalibrationCommand(SensorCalibrationSettings(
            opticalWindow = OpticalWindowSettings(true, List(11) { 1.111f })))
        assertTrue(sensorCalibrationCommandsSupported(listOf(base), "0.5.104"))
        assertFalse(sensorCalibrationCommandsSupported(listOf(extended), "0.5.104"))
        assertFalse(sensorCalibrationCommandsSupported(listOf(extended), ""))
        assertFalse(sensorCalibrationCommandsSupported(listOf(extended), "0.5.105"))
        assertTrue(sensorCalibrationCommandsSupported(listOf(extended), "0.5.106"))
        assertFalse(sensorCalibrationCommandsSupported(listOf("CALIBRATION_CONFIG NaN 1"), "0.5.105"))
    }

    @Test fun rotationStatePreservesOpticsAndRestoresLegacySamples() {
        val scope = object : androidx.compose.runtime.saveable.SaverScope {
            override fun canBeSaved(value: Any): Boolean = true
        }
        val sample = SensorSample(uvc = 42.125,
            opticalWindow = OpticalWindowSettings(true, List(11) { 1.111f }))
        val saved = with(SensorSampleSaver) { scope.save(sample) }
        assertEquals(sample, SensorSampleSaver.restore(requireNotNull(saved)))
        val history = listOf(sample, sample.copy(opticalWindow = null))
        val savedHistory = with(SensorSampleListSaver) { scope.save(history) }
        assertEquals(history, SensorSampleListSaver.restore(requireNotNull(savedHistory)))
        val legacyValues = listOf(42.125) + List(11) { 0.0 }
        val legacy = requireNotNull(SensorSampleSaver.restore(legacyValues))
        assertEquals(42.125, legacy.uvc, 0.0)
        assertNull(legacy.opticalWindow)
        assertEquals(listOf(legacy, legacy), SensorSampleListSaver.restore(legacyValues + legacyValues))
    }

    @Test fun oldFirmwareCannotAdvertiseSupportForOpticalCorrection() {
        assertFalse(firmwareSupportsOpticalWindow("0.5.104"))
        assertFalse(firmwareSupportsOpticalWindow("0.5.105"))
        assertTrue(firmwareSupportsOpticalWindow("0.5.106"))
        assertTrue(firmwareSupportsOpticalWindow("0.6.0"))
    }
}
