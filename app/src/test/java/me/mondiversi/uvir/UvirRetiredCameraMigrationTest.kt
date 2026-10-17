package me.mondiversi.uvir

import org.junit.Assert.*
import org.junit.Test

class UvirRetiredCameraMigrationTest {
    @Test fun cleanInstallDoesNotCreateAVirtualSourceOrEnableSimulation() {
        val values = mutableMapOf<String, Any?>()
        val prefs = memoryPreferences(values)
        removeRetiredCameraSelection(prefs, "")
        assertTrue(values.isEmpty())
        assertFalse(UvirSensorSimulationSettingsStore.load(prefs).enabled)
        assertEquals("", selectedUiSensorDeviceId(prefs, ""))
    }

    @Test fun restoresPhysicalNotesSettingsAndLatestSessionProgressWithoutChangingCredentials() {
        val values = mutableMapOf<String, Any?>(
            "camera_source_active" to true, "camera_context_prepared" to true,
            "camera_source_id" to "camera:fixture", "camera_network_consent" to true,
            KEY_AUTO_NOTE to "Camera draft", KEY_AUTO_SESSION_ID to 0L,
            "credential.fixture" to "unchanged",
            sensorContextPreferenceKey("sensor-a", KEY_AUTO_NOTE) to "Real note",
            sensorContextPreferenceKey("sensor-a", KEY_AUTO_SESSION_ID) to 12L,
            sensorContextPreferenceKey("sensor-a", KEY_AUTO_COMPLETED_COUNT) to 5,
            sensorContextPreferenceKey("sensor-a", KEY_SENSOR_CONNECTION_MODE) to "BLUETOOTH",
            sensorContextPreferenceKey("__camera_source__", KEY_AUTO_NOTE) to "Retired draft"
        )
        val prefs = memoryPreferences(values)
        removeRetiredCameraSelection(prefs, "sensor-a")
        assertEquals("Real note", prefs.getString(KEY_AUTO_NOTE, ""))
        assertEquals(12L, prefs.getLong(KEY_AUTO_SESSION_ID, 0L))
        assertEquals(5, prefs.getInt(KEY_AUTO_COMPLETED_COUNT, 0))
        assertEquals("BLUETOOTH", prefs.getString(KEY_SENSOR_CONNECTION_MODE, ""))
        assertEquals("unchanged", values["credential.fixture"])
        assertEquals("sensor-a", selectedUiSensorDeviceId(prefs, "sensor-a"))
        assertFalse(values.keys.any(::isRetiredCameraPreference))
        val after = values.toMap()
        removeRetiredCameraSelection(prefs, "sensor-a")
        assertEquals(after, values)
    }

    @Test fun removalWithNoHardwareClearsOnlyTheRetiredUiContext() {
        val values = mutableMapOf<String, Any?>(
            "camera_context_prepared" to true, KEY_AUTO_NOTE to "Camera",
            KEY_AUTO_ENABLED to true, "app.preference" to "keep"
        )
        val prefs = memoryPreferences(values)
        removeRetiredCameraSelection(prefs, "")
        assertFalse(prefs.contains(KEY_AUTO_NOTE))
        assertFalse(prefs.getBoolean(KEY_AUTO_ENABLED, false))
        assertEquals("keep", values["app.preference"])
        assertEquals("—", homeSensorDisplayName("", selectedUiSensorDeviceId(prefs, "")))
    }

    @Test fun activeSimulationKeepsItsDraftAndReturnsToPhysicalContextWhenDisabled() {
        val prefs = memoryPreferences(mutableMapOf(
            "camera_source_active" to true, "camera_context_prepared" to true,
            KEY_SIMULATION_CONTEXT_ACTIVE to true, KEY_SIMULATION_CONTEXT_PREPARED to true,
            KEY_AUTO_NOTE to "Demo note", KEY_AUTO_SESSION_ID to 99L,
            sensorContextPreferenceKey("sensor-a", KEY_AUTO_NOTE) to "Real note",
            sensorContextPreferenceKey("sensor-a", KEY_AUTO_SESSION_ID) to 12L
        ))
        removeRetiredCameraSelection(prefs, "sensor-a")
        assertEquals("Demo note", prefs.getString(KEY_AUTO_NOTE, ""))
        assertEquals(99L, prefs.getLong(KEY_AUTO_SESSION_ID, 0L))
        assertEquals("", selectedUiSensorDeviceId(prefs, "sensor-a"))
        switchSimulationContext(prefs, "sensor-a", false)
        assertEquals("Real note", prefs.getString(KEY_AUTO_NOTE, ""))
        assertEquals(12L, prefs.getLong(KEY_AUTO_SESSION_ID, 0L))
        assertEquals("sensor-a", selectedUiSensorDeviceId(prefs, "sensor-a"))
    }

    @Test fun anUnpreparedCameraFlagDoesNotOverwriteExistingPhysicalSettings() {
        val prefs = memoryPreferences(mutableMapOf(
            "camera_source_active" to true, KEY_AUTO_NOTE to "Physical note"
        ))
        removeRetiredCameraSelection(prefs, "sensor-a")
        assertEquals("Physical note", prefs.getString(KEY_AUTO_NOTE, ""))
        assertFalse(prefs.contains("camera_source_active"))
    }

    @Test fun backupNeverImportsRetiredCameraOwnershipOrDrafts() {
        for (key in listOf("camera_source_active", "camera_context_prepared", "camera_source_id",
            "camera_network_consent", sensorContextPreferenceKey("__camera_source__", KEY_AUTO_NOTE))) {
            assertFalse(key, isTransferableAppPreference(key))
        }
    }
}
