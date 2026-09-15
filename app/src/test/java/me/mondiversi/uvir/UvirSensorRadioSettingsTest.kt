package me.mondiversi.uvir

import org.junit.Assert.assertEquals
import org.junit.Test

class UvirSensorRadioSettingsTest {
    @Test
    fun replacementRadioIsEnabledBeforeActiveRadioIsDisabled() {
        val changes =
            orderedSensorRadioChanges(
                current =
                    SensorRadioSettings(
                        wifiEnabled = true,
                        bluetoothEnabled = false
                    ),
                target =
                    SensorRadioSettings(
                        wifiEnabled = false,
                        bluetoothEnabled = true
                    ),
                activeMode = SensorConnectionMode.WIFI
            )

        assertEquals(
            listOf(
                SensorRadioChange(
                    SensorConnectionMode.BLUETOOTH,
                    true
                ),
                SensorRadioChange(
                    SensorConnectionMode.WIFI,
                    false
                )
            ),
            changes
        )
    }

    @Test
    fun unchangedSettingsProduceNoCommands() {
        val settings =
            SensorRadioSettings(
                wifiEnabled = true,
                bluetoothEnabled = true
            )

        assertEquals(
            emptyList<SensorRadioChange>(),
            orderedSensorRadioChanges(
                current = settings,
                target = settings,
                activeMode = SensorConnectionMode.BLUETOOTH
            )
        )
    }
}
