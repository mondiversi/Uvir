package me.mondiversi.uvir

import org.junit.Assert.*
import org.junit.Test

class UvirSensorSimulationSettingsTest {
    @Test fun firstLaunchIsOptInAndHasNoHardwareIdentity() {
        val values = mutableMapOf<String, Any?>()
        val prefs = memoryPreferences(values)
        assertEquals(UvirSensorSimulationSettings(), UvirSensorSimulationSettingsStore.load(prefs))
        assertTrue(values.isEmpty())
        assertEquals("sensor-a", selectedUiSensorDeviceId(prefs, "sensor-a"))
        switchSimulationContext(prefs, "sensor-a", true)
        assertEquals("", selectedUiSensorDeviceId(prefs, "sensor-a"))
        assertEquals("—", homeSensorDisplayName("Portable", selectedUiSensorDeviceId(prefs, "sensor-a")))
    }

    @Test fun simulationNeverPausesAnyPhysicalTransport() {
        val prefs = memoryPreferences(mutableMapOf())
        switchSimulationContext(prefs, "sensor-a", true)
        for (uid in listOf("sensor-a", "sensor-b", "sensor-c")) {
            assertTrue(uvirSensorRealTransportEnabled(prefs, uid, false))
            assertFalse(uvirSensorRealTransportEnabled(prefs, uid, true))
        }
    }

    @Test fun realAndDemoJobsNotesAndDraftsStaySeparateAcrossRoundTrips() {
        val values = mutableMapOf<String, Any?>(
            KEY_AUTO_ENABLED to true, KEY_AUTO_SESSION_ID to 12L,
            KEY_AUTO_COMPLETED_COUNT to 3, KEY_AUTO_NOTE to "Real note",
            KEY_LAST_MANUAL_SESSION_ID to 22L,
            KEY_SENSOR_CONNECTION_MODE to "WIFI",
            sensorContextPreferenceKey("sensor-b", KEY_AUTO_ENABLED) to true,
            sensorContextPreferenceKey("sensor-b", KEY_AUTO_SESSION_ID) to 42L)
        val prefs = memoryPreferences(values)
        switchSimulationContext(prefs, "sensor-a", true)
        assertFalse(prefs.getBoolean(KEY_AUTO_ENABLED, false))
        assertEquals(0L, prefs.getLong(KEY_AUTO_SESSION_ID, 0L))
        prefs.edit().putBoolean(KEY_AUTO_ENABLED, true).putLong(KEY_AUTO_SESSION_ID, 55L)
            .putBoolean(KEY_AUTO_SIMULATED, true).putString(KEY_AUTO_NOTE, "Demo note")
            .putLong(KEY_LAST_MANUAL_SESSION_ID, 56L).commit()
        // Real reports continue while the demo screen is visible.
        prefs.edit().putInt(sensorContextPreferenceKey("sensor-a", KEY_AUTO_COMPLETED_COUNT), 4).commit()
        switchSimulationContext(prefs, "sensor-a", false)
        assertEquals(12L, prefs.getLong(KEY_AUTO_SESSION_ID, 0L))
        assertEquals(4, prefs.getInt(KEY_AUTO_COMPLETED_COUNT, 0))
        assertEquals("Real note", prefs.getString(KEY_AUTO_NOTE, ""))
        assertEquals("WIFI", prefs.getString(KEY_SENSOR_CONNECTION_MODE, ""))
        assertEquals(22L, prefs.getLong(KEY_LAST_MANUAL_SESSION_ID, 0L))
        assertFalse(prefs.getBoolean(KEY_AUTO_SIMULATED, false))
        assertTrue(prefs.getBoolean(sensorContextPreferenceKey("sensor-b", KEY_AUTO_ENABLED), false))
        assertEquals(42L, prefs.getLong(sensorContextPreferenceKey("sensor-b", KEY_AUTO_SESSION_ID), 0L))
        switchSimulationContext(prefs, "sensor-a", true)
        assertEquals(55L, prefs.getLong(KEY_AUTO_SESSION_ID, 0L))
        assertEquals(56L, prefs.getLong(KEY_LAST_MANUAL_SESSION_ID, 0L))
        assertEquals("Demo note", prefs.getString(KEY_AUTO_NOTE, ""))
        assertTrue(prefs.getBoolean(KEY_AUTO_SIMULATED, false))
    }

    @Test fun finalRealReadbackWinsOverOldUiStateWhenEnteringSimulation() {
        val prefs = memoryPreferences(mutableMapOf(
            KEY_AUTO_ENABLED to true, KEY_AUTO_SESSION_ID to 10L, KEY_AUTO_COMPLETED_COUNT to 1,
            sensorContextPreferenceKey("sensor-a", KEY_AUTO_ENABLED) to false,
            sensorContextPreferenceKey("sensor-a", KEY_AUTO_SESSION_ID) to 10L,
            sensorContextPreferenceKey("sensor-a", KEY_AUTO_COMPLETED_COUNT) to 2))
        switchSimulationContext(prefs, "sensor-a", true)
        switchSimulationContext(prefs, "sensor-a", false)
        assertFalse(prefs.getBoolean(KEY_AUTO_ENABLED, true))
        assertEquals(2, prefs.getInt(KEY_AUTO_COMPLETED_COUNT, 0))
    }

    @Test fun restartKeepsDemoContextAndDoesNotRestoreRealJobsIntoIt() {
        val values = mutableMapOf<String, Any?>(KEY_AUTO_SESSION_ID to 10L)
        val prefs = memoryPreferences(values)
        switchSimulationContext(prefs, "sensor-a", true)
        prefs.edit().putLong(KEY_AUTO_SESSION_ID, 20L).commit()
        val reopened = memoryPreferences(values.toMutableMap())
        switchSimulationContext(reopened, "sensor-a", UvirSensorSimulationSettingsStore.load(reopened).enabled)
        assertEquals(20L, reopened.getLong(KEY_AUTO_SESSION_ID, 0L))
        switchSimulationContext(reopened, "sensor-a", false)
        assertEquals(10L, reopened.getLong(KEY_AUTO_SESSION_ID, 0L))
    }

    @Test fun noHardwareDemoAndLaterAssociationNeverBecomeTheSameContext() {
        val prefs = memoryPreferences(mutableMapOf())
        switchSimulationContext(prefs, "", true)
        prefs.edit().putLong(KEY_LAST_MANUAL_SESSION_ID, 88L).commit()
        switchSimulationContext(prefs, "new-sensor", false)
        assertEquals(0L, prefs.getLong(KEY_LAST_MANUAL_SESSION_ID, 0L))
        assertEquals("new-sensor", selectedUiSensorDeviceId(prefs, "new-sensor"))
        switchSimulationContext(prefs, "new-sensor", true)
        assertEquals(88L, prefs.getLong(KEY_LAST_MANUAL_SESSION_ID, 0L))
    }

    @Test fun repeatedSameSwitchIsIdempotent() {
        val values = mutableMapOf<String, Any?>(KEY_AUTO_NOTE to "Real")
        val prefs = memoryPreferences(values)
        switchSimulationContext(prefs, "sensor-a", true)
        prefs.edit().putString(KEY_AUTO_NOTE, "Demo").commit()
        val before = values.toMap()
        switchSimulationContext(prefs, "sensor-a", true)
        assertEquals(before, values)
    }

    @Test fun saturationIsAnIndependentDemoChoiceNotASensorSetting() {
        val prefs = memoryPreferences(mutableMapOf())
        UvirSensorSimulationSettingsStore.setOutOfRange(prefs, "", true)
        assertEquals(UvirSensorSimulationSettings(false, true), UvirSensorSimulationSettingsStore.load(prefs))
        switchSimulationContext(prefs, "sensor-a", true)
        assertEquals(UvirSensorSimulationSettings(true, true), UvirSensorSimulationSettingsStore.load(prefs))
        switchSimulationContext(prefs, "sensor-a", false)
        assertTrue(UvirSensorSimulationSettingsStore.load(prefs).outOfRange)
    }

    @Test fun migrateOnlySelectedLegacyChoiceNotAnInactiveSensorsChoice() {
        val prefs = memoryPreferences(mutableMapOf(
            UvirSensorSimulationSettingsStore.preferenceKey("sensor-a", KEY_USE_FAKE_SENSOR_DATA) to false,
            UvirSensorSimulationSettingsStore.preferenceKey("sensor-b", KEY_USE_FAKE_SENSOR_DATA) to true))
        UvirSensorSimulationSettingsStore.migrateLegacy(prefs, "sensor-a")
        assertFalse(UvirSensorSimulationSettingsStore.load(prefs).enabled)
    }

    @Test fun selectedLegacySimulationEntersAnEmptySeparateContext() {
        val prefs = memoryPreferences(mutableMapOf(
            KEY_AUTO_SESSION_ID to 11L,
            UvirSensorSimulationSettingsStore.preferenceKey("sensor-a", KEY_USE_FAKE_SENSOR_DATA) to true,
            UvirSensorSimulationSettingsStore.preferenceKey("sensor-a", KEY_FAKE_SENSOR_OUT_OF_RANGE) to true))
        UvirSensorSimulationSettingsStore.migrateLegacy(prefs, "sensor-a")
        switchSimulationContext(prefs, "sensor-a", UvirSensorSimulationSettingsStore.load(prefs).enabled)
        assertEquals(UvirSensorSimulationSettings(true, true), UvirSensorSimulationSettingsStore.load(prefs))
        assertEquals(0L, prefs.getLong(KEY_AUTO_SESSION_ID, 0L))
        assertEquals(11L, prefs.getLong(sensorContextPreferenceKey("sensor-a", KEY_AUTO_SESSION_ID), 0L))
    }

    @Test fun oldNoHardwareAndGlobalChoicesMigrateWithoutReenablingStaleFlagsLater() {
        for (legacyKey in listOf(KEY_USE_FAKE_SENSOR_DATA,
            UvirSensorSimulationSettingsStore.preferenceKey("", KEY_USE_FAKE_SENSOR_DATA))) {
            val prefs = memoryPreferences(mutableMapOf(legacyKey to true))
            UvirSensorSimulationSettingsStore.migrateLegacy(prefs, "")
            assertTrue(UvirSensorSimulationSettingsStore.load(prefs).enabled)
            UvirSensorSimulationSettingsStore.setEnabled(prefs, "", false)
            UvirSensorSimulationSettingsStore.migrateLegacy(prefs, "")
            assertFalse(UvirSensorSimulationSettingsStore.load(prefs).enabled)
        }
    }

    @Test fun backupTransfersChoicesButNeverContextOwnershipOrJobIds() {
        assertTrue(isTransferableAppPreference(KEY_SIMULATION_CONTEXT_ACTIVE))
        assertFalse(isTransferableAppPreference(KEY_SIMULATION_CONTEXT_PREPARED))
        assertFalse(isTransferableAppPreference(sensorContextPreferenceKey(SIMULATION_SENSOR_CONTEXT, KEY_AUTO_SESSION_ID)))
        assertFalse(KEY_SIMULATION_CONTEXT_ACTIVE in sensorConfigurationPreferenceKeys)
    }
}
