package me.mondiversi.uvir

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    @Test fun firmwareUsesMatchingDefaultsOnlyAsStoredPreferenceFallbacks() {
        val firmware = listOf(File("../firmware/esp32/UvirSensor"),
            File("firmware/esp32/UvirSensor")).first { it.isDirectory }
        val defaults = File(firmware, "UvirFeedbackDefaults.h").readText()
        assertTrue(defaults.contains("kUvirDefaultStatusLedBrightness = $DEFAULT_SENSOR_STATUS_LED_BRIGHTNESS;"))
        assertTrue(defaults.contains("kUvirDefaultStatusBuzzerVolume = $DEFAULT_SENSOR_STATUS_BUZZER_VOLUME;"))
        val source = File(firmware, "UvirSensor.ino").readText()
        assertTrue(source.contains("preferences.getUChar(\"led_brightness\", kUvirDefaultStatusLedBrightness)"))
        assertTrue(source.contains("preferences.getUChar(\"buzzer_volume\", kUvirDefaultStatusBuzzerVolume)"))
    }
}
