package me.mondiversi.uvir

import org.junit.Assert.*
import org.junit.Test

class UvirCalibrationChannelFactorsTest {
    @Test fun legacyModuleGainsInitializeRowsWithoutChangingTheirMeaning() {
        val legacy = SensorCalibrationSettings(visibleFactor = 1.7f, uvFactor = .8f)
        val rows = CalibrationChannelFactors.fromModuleFactors(legacy.visibleFactor, legacy.uvFactor)
        assertEquals(List(3) { .8f } + List(8) { 1.7f }, rows.values)
        assertNull(legacy.channelFactors) // Merely reading/opening has no side effects.
        val changed = rows.withChannel(1, 1.2f)
        assertEquals(1.2f, changed.values[1], 0f)
        assertEquals(rows.values.filterIndexed { index, _ -> index != 1 },
            changed.values.filterIndexed { index, _ -> index != 1 })
    }

    @Test fun commandRoundTripsBothOffsetVersionsAndIndependentGainsAtomically() {
        for (offsets in listOf(null, CalibrationOffsets(), CalibrationOffsets(List(15) { it * .01f }))) {
            val value = SensorCalibrationSettings(1.2f, .8f,
                OpticalWindowSettings(true, List(11) { 1.1f }), offsets,
                CalibrationChannelFactors(List(11) { .5f + it * .1f }))
            val command = sensorCalibrationCommand(value)
            assertEquals(value, parseSensorCalibrationCommand(command))
            assertFalse(sensorCalibrationCommandsSupported(listOf(command), "0.5.108"))
            assertTrue(sensorCalibrationCommandsSupported(listOf(command), "0.5.109"))
            for (invalid in listOf(command + " 1", command.substringBeforeLast(' '),
                command.replace("CHANNEL_FACTORS", "CHANNEL_FACTOR"),
                command.substringBefore("CHANNEL_FACTORS") + "CHANNEL_FACTORS " + List(11) { "0" }.joinToString(" ")))
                assertNull(invalid, parseSensorCalibrationCommand(invalid))
        }
    }

    @Test fun invalidFactorsNeverReachAProfileOrFirmware() {
        assertEquals(List(11) { 1f }, CalibrationChannelFactors().values)
        for (invalid in listOf(Float.NaN, Float.POSITIVE_INFINITY, 0f, -.1f, .099f, 10.001f))
            assertThrows(IllegalArgumentException::class.java) {
                CalibrationChannelFactors().withChannel(3, invalid)
            }
        assertThrows(IllegalArgumentException::class.java) { CalibrationChannelFactors(List(10) { 1f }) }
        assertFalse(firmwareSupportsCalibrationChannels("0.5.108"))
        assertTrue(firmwareSupportsCalibrationChannels("0.5.109"))
    }
}
