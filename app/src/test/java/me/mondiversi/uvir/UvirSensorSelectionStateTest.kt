package me.mondiversi.uvir

import org.junit.Assert.*
import org.junit.Test

class UvirSensorSelectionStateTest {
    @Test fun migrationEnrollsOnlyCurrentSensorNotHistoricalProfiles() {
        val values = mutableMapOf<String, Any?>(
            "active_device_id" to " SENSOR-A ",
            "sensor_profile.sensor-a" to "saved profile",
            "sensor_profile.sensor-b" to "historical profile"
        )
        assertEquals(setOf("sensor-a"), associatedSensorDeviceIds(memoryPreferences(values)))
    }

    @Test fun explicitEmptyListDoesNotReassociateLegacySensor() {
        val values = mutableMapOf<String, Any?>(
            "device_id" to "sensor-a", ASSOCIATED_SENSOR_IDS_KEY to emptySet<String>()
        )
        assertTrue(associatedSensorDeviceIds(memoryPreferences(values)).isEmpty())
    }

    @Test fun associatedIdsAreCaseInsensitiveAndIgnoreEmptyEntries() {
        val values = mutableMapOf<String, Any?>(
            ASSOCIATED_SENSOR_IDS_KEY to setOf(" SENSOR-A ", "sensor-a", "", "Sensor-B")
        )
        assertEquals(setOf("sensor-a", "sensor-b"), associatedSensorDeviceIds(memoryPreferences(values)))
    }

    @Test fun switchingRestoresThreeIndependentNotesAndSensorParameters() {
        val values = mutableMapOf<String, Any?>(
            KEY_MANUAL_ACQUISITION_NOTE to "manual A",
            KEY_AUTO_NOTE to "automatic A",
            KEY_THRESHOLD_ALERT_NOTE to "alert A",
            KEY_SAMPLE_SPACING_MS to 150L,
            KEY_SENSOR_STATUS_LED_BRIGHTNESS to 10,
            KEY_SENSOR_VISIBLE_CALIBRATION_FACTOR to 2f,
            KEY_SENSOR_CONNECTION_MODE to "BLUETOOTH"
        )
        val preferences = memoryPreferences(values)
        assertTrue(saveSelectedSensorContext(preferences, "sensor-A"))
        assertTrue(restoreSelectedSensorContext(preferences, "sensor-B"))
        preferences.edit().putString(KEY_MANUAL_ACQUISITION_NOTE, "manual B")
            .putString(KEY_AUTO_NOTE, "automatic B").putString(KEY_THRESHOLD_ALERT_NOTE, "alert B")
            .putLong(KEY_SAMPLE_SPACING_MS, 500L).putFloat(KEY_SENSOR_VISIBLE_CALIBRATION_FACTOR, 5f).commit()
        assertTrue(saveSelectedSensorContext(preferences, "sensor-B"))
        assertTrue(restoreSelectedSensorContext(preferences, " SENSOR-A "))
        assertEquals("manual A", preferences.getString(KEY_MANUAL_ACQUISITION_NOTE, ""))
        assertEquals("automatic A", preferences.getString(KEY_AUTO_NOTE, ""))
        assertEquals("alert A", preferences.getString(KEY_THRESHOLD_ALERT_NOTE, ""))
        assertEquals(150L, preferences.getLong(KEY_SAMPLE_SPACING_MS, 0))
        assertEquals(2f, preferences.getFloat(KEY_SENSOR_VISIBLE_CALIBRATION_FACTOR, 0f))
        assertEquals("BLUETOOTH", preferences.getString(KEY_SENSOR_CONNECTION_MODE, ""))
        restoreSelectedSensorContext(preferences, "sensor-B")
        assertEquals("alert B", preferences.getString(KEY_THRESHOLD_ALERT_NOTE, ""))
        assertEquals(500L, preferences.getLong(KEY_SAMPLE_SPACING_MS, 0))
    }

    @Test fun sessionStateAndOfflineEstimateBelongToTheSelectedSensor() {
        val values = mutableMapOf<String, Any?>(KEY_AUTO_ENABLED to true,
            KEY_AUTO_SESSION_ID to 42L, KEY_AUTO_COMPLETED_COUNT to 8,
            KEY_THRESHOLD_ALERT_SESSION_ID to 43L)
        val preferences = memoryPreferences(values)
        persistOfflineDisconnectionNotice(preferences, UvirOfflineDisconnectionNotice(null, false))
        saveSelectedSensorContext(preferences, "a")
        restoreSelectedSensorContext(preferences, "b")
        assertFalse(preferences.getBoolean(KEY_AUTO_ENABLED, false))
        assertNull(loadPersistedOfflineDisconnectionNotice(preferences))
        restoreSelectedSensorContext(preferences, "a")
        assertTrue(preferences.getBoolean(KEY_AUTO_ENABLED, false))
        assertEquals(42L, preferences.getLong(KEY_AUTO_SESSION_ID, 0))
        assertEquals(8, preferences.getInt(KEY_AUTO_COMPLETED_COUNT, 0))
        assertEquals(43L, preferences.getLong(KEY_THRESHOLD_ALERT_SESSION_ID, 0))
        assertFalse(loadPersistedOfflineDisconnectionNotice(preferences)!!.autonomousRecordingEnabled)
    }

    @Test fun newSensorNeverInheritsSettingsButGlobalUiPreferencesRemain() {
        val values = mutableMapOf<String, Any?>(KEY_AUTO_NOTE to "old note",
            KEY_SENSOR_UV_CALIBRATION_FACTOR to 5f, KEY_APP_LANGUAGE to "it", "global_record_counter" to 100L)
        val preferences = memoryPreferences(values)
        saveSelectedSensorContext(preferences, "a")
        restoreSelectedSensorContext(preferences, "new sensor")
        assertFalse(preferences.contains(KEY_AUTO_NOTE))
        assertFalse(preferences.contains(KEY_SENSOR_UV_CALIBRATION_FACTOR))
        assertEquals("it", preferences.getString(KEY_APP_LANGUAGE, ""))
        assertEquals(100L, preferences.getLong("global_record_counter", 0))
    }

    @Test fun dissociationForgetsOperationalStateButKeepsDraftsAndOtherSensor() {
        val values = mutableMapOf<String, Any?>(KEY_AUTO_ENABLED to true,
            KEY_AUTO_SESSION_ID to 20L, KEY_AUTO_NOTE to "A note")
        val preferences = memoryPreferences(values)
        saveSelectedSensorContext(preferences, "a")
        preferences.edit().putLong(KEY_AUTO_SESSION_ID, 30L).putString(KEY_AUTO_NOTE, "B note").commit()
        saveSelectedSensorContext(preferences, "b")
        forgetSelectedSensorOperationalContext(preferences, "a")
        restoreSelectedSensorContext(preferences, "a")
        assertFalse(preferences.getBoolean(KEY_AUTO_ENABLED, false))
        assertEquals(0L, preferences.getLong(KEY_AUTO_SESSION_ID, 0))
        assertEquals("A note", preferences.getString(KEY_AUTO_NOTE, ""))
        restoreSelectedSensorContext(preferences, "b")
        assertEquals(30L, preferences.getLong(KEY_AUTO_SESSION_ID, 0))
        assertEquals("B note", preferences.getString(KEY_AUTO_NOTE, ""))
    }

    @Test fun configuredAlertRulesSurviveSwitchWithoutStartingMonitoring() {
        val enabled = thresholdRulePreferenceKey(ThresholdAlertMetric.UVA, "enabled")
        val value = thresholdRulePreferenceKey(ThresholdAlertMetric.UVA, "value")
        val direction = thresholdRulePreferenceKey(ThresholdAlertMetric.UVA, "direction")
        val values = mutableMapOf<String, Any?>(enabled to true, value to 3f,
            direction to "BELOW", KEY_THRESHOLD_ALERT_ENABLED to false)
        val preferences = memoryPreferences(values)
        saveSelectedSensorContext(preferences, "a")
        restoreSelectedSensorContext(preferences, "b")
        assertFalse(preferences.contains(enabled))
        restoreSelectedSensorContext(preferences, "a")
        assertTrue(preferences.getBoolean(enabled, false))
        assertEquals(3f, preferences.getFloat(value, 0f))
        assertEquals("BELOW", preferences.getString(direction, ""))
        assertFalse(preferences.getBoolean(KEY_THRESHOLD_ALERT_ENABLED, false))
    }

    @Test fun resettingCountersRemovesCachedSessionIdsWithoutRemovingNotes() {
        val values = mutableMapOf<String, Any?>(KEY_AUTO_SESSION_ID to 20L, KEY_AUTO_NOTE to "A note")
        val preferences = memoryPreferences(values)
        saveSelectedSensorContext(preferences, "a")
        preferences.edit().putLong(KEY_AUTO_SESSION_ID, 30L).putString(KEY_AUTO_NOTE, "B note").commit()
        saveSelectedSensorContext(preferences, "b")
        clearCachedSensorOperationalContexts(preferences)
        restoreSelectedSensorContext(preferences, "a")
        assertEquals(0L, preferences.getLong(KEY_AUTO_SESSION_ID, 0))
        assertEquals("A note", preferences.getString(KEY_AUTO_NOTE, ""))
        restoreSelectedSensorContext(preferences, "b")
        assertEquals(0L, preferences.getLong(KEY_AUTO_SESSION_ID, 0))
        assertEquals("B note", preferences.getString(KEY_AUTO_NOTE, ""))
    }
}
