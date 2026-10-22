package me.mondiversi.uvir

import org.junit.Assert.*
import org.junit.Test

class UvirOpticalWindowEditorTest {
    @Test fun masterMultipliesWithoutOverwritingOrCompoundingChannelFactors() {
        val before = OpticalWindowSettings(true, List(11) { 1f + it / 10f })
        val first = before.withMasterFactor(1.2f)
        assertEquals(before.factors, first.factors)
        assertEquals(1.8f, first.effectiveFactors[5], .000001f)
        val second = first.withMasterFactor(1.5f)
        assertEquals(before.factors, second.factors)
        assertEquals(2.25f, second.effectiveFactors[5], 0f)
        val edited = second.withChannelFactor(5, 2f)
        assertEquals(1.5f, edited.masterMultiplier, 0f)
        assertEquals(3f, edited.effectiveFactors[5], 0f)
        assertEquals(List(11) { 1f }, edited.resetMultipliers().factors)
        assertEquals(1f, edited.resetMultipliers().masterMultiplier, 0f)
    }

    @Test fun masterWireRoundTripIsVersionGatedAndPreservesAllOtherSettings() {
        val window = OpticalWindowSettings(true, List(11) { 1.5f }).withMasterFactor(1.2f)
        val settings = SensorCalibrationSettings(1.1f, .9f, window, CalibrationOffsets(), CalibrationChannelFactors())
        val command = sensorCalibrationCommand(settings)
        assertTrue(command.endsWith(" WINDOW_MASTER 1.2"))
        assertEquals(settings, parseSensorCalibrationCommand(command))
        assertFalse(sensorCalibrationCommandsSupported(listOf(command), "0.5.110"))
        assertTrue(sensorCalibrationCommandsSupported(listOf(command), "0.5.111"))
        assertNull(parseSensorCalibrationCommand(command + " extra"))
        for (invalid in listOf("NaN", "Infinity", "0.9", "10.1"))
            assertNull(parseSensorCalibrationCommand(command.substringBeforeLast(' ') + " " + invalid))
        assertEquals(1.007813f, normalizeOpticalMasterFactor(1.0078125f), 0f)
        assertEquals(window.withMasterFactor(1f), parseSensorCalibrationCommand(
            sensorCalibrationCommand(settings.copy(opticalWindow = window.withMasterFactor(1f))))!!.opticalWindow)
    }

    @Test fun legacyDisabledFactorsOpenAsNeutralWithoutActivatingStoredCorrections() {
        val legacy = OpticalWindowSettings(false, List(11) { 1.5f })
        val editable = legacy.forWindowEditor()
        assertTrue(editable.enabled)
        assertEquals(List(11) { 1f }, editable.factors)
        assertFalse(legacy.enabled)
        assertEquals(List(11) { 1.5f }, legacy.factors)
        val applied = editable.withCommonFactor(1.25f)
        assertTrue(applied.enabled)
        assertEquals(List(11) { 1.25f }, applied.factors)
        assertEquals(applied, applied.forWindowEditor())
    }

    @Test fun oneCommonEditUpdatesAllElevenFactorsWithoutMultiplyingPreviousOnes() {
        val previous = OpticalWindowSettings(true, List(11) { 1f + it / 10f })
        val common = previous.withCommonFactor(1.25f)
        assertEquals(List(11) { 1.25f }, common.factors)
        assertEquals(1.25f, common.commonFactorOrNull)
        assertTrue(common.enabled)
        assertEquals(common, common.withCommonFactor(1.25f))
        assertEquals(15, sensorCalibrationCommand(SensorCalibrationSettings(opticalWindow = common)).split(' ').size)
    }

    @Test fun individualFactorsStartFromTheCommonValueAndOnlyOneChannelChanges() {
        val common = OpticalWindowSettings(enabled = true).withCommonFactor(1.111f)
        val individual = common.withChannelFactor(4, 1.5f)
        assertNull(individual.commonFactorOrNull)
        individual.factors.forEachIndexed { index, factor ->
            assertEquals(if (index == 4) 1.5f else 1.111f, factor, 0f)
        }
        assertEquals(List(11) { 1.111f }, individual.withCommonFactor(1.111f).factors)
        assertEquals(List(11) { 1.111f }, common.factors)
    }

    @Test fun referencesFollowExactlyTheStoredChannelOrder() {
        assertEquals(listOf("240–280 nm", "280–315 nm", "315–410 nm",
            "405 / 425 nm", "450 / 475 nm", "515 nm", "550 / 555 nm", "600 nm",
            "640 nm", "690 / 745 nm", "855 nm"), opticalWindowChannelReferences)
    }

    @Test fun invalidCommonOrIndividualValuesCannotReachTheWire() {
        for (factor in listOf(Float.NaN, Float.POSITIVE_INFINITY, 0f, .9f, 10.1f)) {
            assertTrue(runCatching { OpticalWindowSettings().withCommonFactor(factor) }.isFailure)
            assertTrue(runCatching { OpticalWindowSettings().withChannelFactor(5, factor) }.isFailure)
        }
    }

    @Test fun aCalibrationRejectionDoesNotMeanTheTransportIsDisconnected() {
        for (code in listOf("calibration_busy", "calibration_invalid", "calibration_save_failed"))
            assertTrue(isCalibrationCommandRejection(code))
        for (code in listOf("auth_failed", "sensor_read", "sensor_identity_mismatch", "unknown"))
            assertFalse(isCalibrationCommandRejection(code))
    }

    @Test fun aVisualActivityPulseDoesNotDisableCalibrationButRealWorkDoes() {
        assertFalse(UvirSensorRuntimeInfo(operationActive = true, calibrationLocked = false).calibrationChangeBlocked)
        assertTrue(UvirSensorRuntimeInfo(operationActive = true, calibrationLocked = true).calibrationChangeBlocked)
        assertTrue(UvirSensorRuntimeInfo(operationActive = false, calibrationLocked = true).calibrationChangeBlocked)
        assertTrue(UvirSensorRuntimeInfo(operationActive = true).calibrationChangeBlocked)
        assertFalse(UvirSensorRuntimeInfo().calibrationChangeBlocked)
    }
}
