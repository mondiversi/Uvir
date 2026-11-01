package me.mondiversi.uvir

import org.junit.Assert.assertEquals
import org.junit.Test

class UvirSensorFeedbackDefaultsTest {
    @Test fun newSensorDefaultsToTwentyPercentForLedAndBuzzer() {
        val defaults = SensorParameters()
        assertEquals(20, defaults.statusLedBrightness)
        assertEquals(20, defaults.statusBuzzerVolume)
    }

    @Test fun explicitTenPercentAndOtherUserValuesAreNotReplacedByDefaults() {
        for (value in listOf(1, 10, 35, 100)) {
            val stored = SensorParameters(statusLedBrightness = value, statusBuzzerVolume = value)
            assertEquals(value, stored.statusLedBrightness)
            assertEquals(value, stored.statusBuzzerVolume)
            assertEquals(stored, stored.copy())
        }
    }

}
