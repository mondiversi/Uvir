package me.mondiversi.uvir

import org.junit.Assert.*
import org.junit.Test

class UvirSensorSimulationSettingsTest {
    @Test fun threeSensorsKeepIndependentSimulationAndSaturationChoices() {
        val preferences = memoryPreferences(mutableMapOf())
        UvirSensorSimulationSettingsStore.setEnabled(preferences, " Sensor-A ", true)
        UvirSensorSimulationSettingsStore.setOutOfRange(preferences, "sensor-a", true)
        UvirSensorSimulationSettingsStore.setEnabled(preferences, "sensor-b", true)
        assertEquals(UvirSensorSimulationSettings(true, true), UvirSensorSimulationSettingsStore.load(preferences, "SENSOR-A"))
        assertEquals(UvirSensorSimulationSettings(true, false), UvirSensorSimulationSettingsStore.load(preferences, "sensor-b"))
        assertEquals(UvirSensorSimulationSettings(), UvirSensorSimulationSettingsStore.load(preferences, "sensor-c"))
        UvirSensorSimulationSettingsStore.setEnabled(preferences, "sensor-b", false)
        assertTrue(UvirSensorSimulationSettingsStore.load(preferences, "sensor-a").enabled)
        assertFalse(UvirSensorSimulationSettingsStore.load(preferences, "sensor-b").enabled)
        saveSelectedSensorContext(preferences, "sensor-a")
        restoreSelectedSensorContext(preferences, "sensor-b")
        restoreSelectedSensorContext(preferences, "sensor-a")
        assertEquals(UvirSensorSimulationSettings(true, true), UvirSensorSimulationSettingsStore.load(preferences, "sensor-a"))
    }

    @Test fun simulatingAOnlyPausesAAndNeverChangesBsRunningSession() {
        val values = mutableMapOf<String, Any?>(
            "active_device_id" to "sensor-a",
            sensorContextPreferenceKey("sensor-b", KEY_AUTO_ENABLED) to true,
            sensorContextPreferenceKey("sensor-b", KEY_AUTO_SESSION_ID) to 42L,
            sensorContextPreferenceKey("sensor-b", KEY_AUTO_COMPLETED_COUNT) to 8
        )
        val preferences = memoryPreferences(values)
        val runningSession = values.toMap()
        UvirSensorSimulationSettingsStore.setEnabled(preferences, "sensor-a", true)
        assertFalse(uvirSensorRealTransportEnabled(preferences, "sensor-a", false))
        assertTrue(uvirSensorRealTransportEnabled(preferences, "sensor-b", false))
        assertTrue(uvirSensorRealTransportEnabled(preferences, "sensor-c", false))
        runningSession.forEach { (key, value) -> assertEquals(value, values[key]) }
        assertFalse(uvirSensorRealTransportEnabled(preferences, "sensor-a", true))
        assertFalse(uvirSensorRealTransportEnabled(preferences, "sensor-b", true))
    }

    @Test fun legacyGlobalSimulationMigratesOnlyToItsOwnerEvenWhenAnotherSensorIsReadFirst() {
        val values = mutableMapOf<String, Any?>(
            "active_device_id" to "SENSOR-A",
            KEY_USE_FAKE_SENSOR_DATA to true,
            KEY_FAKE_SENSOR_OUT_OF_RANGE to true
        )
        val preferences = memoryPreferences(values)
        assertEquals(UvirSensorSimulationSettings(), UvirSensorSimulationSettingsStore.load(preferences, "sensor-b"))
        assertEquals(UvirSensorSimulationSettings(true, true), UvirSensorSimulationSettingsStore.load(preferences, "sensor-a"))
        assertFalse(preferences.contains(KEY_USE_FAKE_SENSOR_DATA))
        assertFalse(preferences.contains(KEY_FAKE_SENSOR_OUT_OF_RANGE))
        assertEquals(UvirSensorSimulationSettings(), UvirSensorSimulationSettingsStore.load(preferences, "sensor-c"))
    }

    @Test fun legacyBackupDoesNotOverwriteAnExistingPerSensorChoice() {
        val preferences = memoryPreferences(mutableMapOf(
            "active_device_id" to "sensor-a",
            KEY_USE_FAKE_SENSOR_DATA to true,
            UvirSensorSimulationSettingsStore.preferenceKey("sensor-a", KEY_USE_FAKE_SENSOR_DATA) to false
        ))
        assertFalse(UvirSensorSimulationSettingsStore.load(preferences, "sensor-a").enabled)
    }

    @Test fun demoDoesNotLeakToFirstAssociatedHardwareOrExplicitNoSensorSelection() {
        val preferences = memoryPreferences(mutableMapOf())
        assertTrue(UvirSensorSimulationSettingsStore.load(preferences, "").enabled)
        assertFalse(UvirSensorSimulationSettingsStore.load(preferences, "new-sensor").enabled)
        preferences.edit().putStringSet(ASSOCIATED_SENSOR_IDS_KEY, setOf("new-sensor"))
            .putBoolean("sensor_selection_disabled", true).commit()
        assertFalse(UvirSensorSimulationSettingsStore.load(preferences, "").enabled)
    }

    @Test fun simulationSettingsAreAppBackupDataAndNeverFirmwareSettings() {
        val values = mutableMapOf<String, Any?>()
        val preferences = memoryPreferences(values)
        UvirSensorSimulationSettingsStore.setEnabled(preferences, "sensor-a", true)
        UvirSensorSimulationSettingsStore.setOutOfRange(preferences, "sensor-b", true)
        assertTrue(values.keys.all(::isTransferableAppPreference))
        assertTrue(values.keys.none { it in sensorConfigurationPreferenceKeys })
        val reopenedPreferences = memoryPreferences(values.toMutableMap())
        assertTrue(UvirSensorSimulationSettingsStore.load(reopenedPreferences, "sensor-a").enabled)
        assertFalse(UvirSensorSimulationSettingsStore.load(reopenedPreferences, "sensor-b").enabled)
        assertTrue(UvirSensorSimulationSettingsStore.load(reopenedPreferences, "sensor-b").outOfRange)
    }

    @Test fun changesDuringSessionsPreserveEverySensorsJobAndOnlyUpdateSelectedUid() {
        for (acquisitionActive in listOf(false, true)) for (alertsActive in listOf(false, true)) {
            val values = mutableMapOf<String, Any?>(
                "active_device_id" to "sensor-a",
                KEY_AUTO_ENABLED to acquisitionActive,
                KEY_AUTO_SESSION_ID to 12L,
                KEY_THRESHOLD_ALERT_ENABLED to alertsActive,
                KEY_THRESHOLD_ALERT_SESSION_ID to 14L,
                sensorContextPreferenceKey("sensor-b", KEY_AUTO_ENABLED) to true,
                sensorContextPreferenceKey("sensor-b", KEY_AUTO_SESSION_ID) to 42L
            )
            val preferences = memoryPreferences(values)
            val jobs = values.toMap()
            for (enabled in listOf(true, false, true)) {
                UvirSensorSimulationSettingsStore.setEnabled(preferences, "sensor-a", enabled)
                for (outOfRange in listOf(true, false)) {
                    UvirSensorSimulationSettingsStore.setOutOfRange(preferences, "sensor-a", outOfRange)
                    assertEquals(UvirSensorSimulationSettings(enabled, outOfRange),
                        UvirSensorSimulationSettingsStore.load(preferences, "sensor-a"))
                    assertEquals(UvirSensorSimulationSettings(),
                        UvirSensorSimulationSettingsStore.load(preferences, "sensor-b"))
                    jobs.forEach { (key, value) -> assertEquals(value, values[key]) }
                }
            }
        }
    }
}
