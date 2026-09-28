package me.mondiversi.uvir

import org.junit.Assert.*
import org.junit.Test

class UvirSensorSettingsSyncTest {
    @Test fun sensorWinsEqualNewerAndUnknownDates() {
        assertFalse(appSensorSettingsAreNewer(0, 0))
        assertFalse(appSensorSettingsAreNewer(100, 100))
        assertFalse(appSensorSettingsAreNewer(100, 101))
        assertTrue(appSensorSettingsAreNewer(101, 100))
    }
    @Test fun clockGoingBackwardsDoesNotLoseANewEdit() {
        assertEquals(101L, nextSensorSettingsUpdate(20, 100, 99))
        assertEquals(102L, nextSensorSettingsUpdate(20, 100, 101))
        assertEquals(500L, nextSensorSettingsUpdate(500, 100, 101))
    }
    @Test fun latestGroupEditKeepsOtherUnconfirmedGroupsAndPutsRadiosLast() {
        assertEquals(listOf("SENSOR_CONFIG OFF 50 ON", "SAMPLING_CONFIG 5 200 1", "RADIO WIFI OFF"),
            mergePendingSensorSettings(listOf("SENSOR_CONFIG ON 50 ON", "RADIO WIFI OFF", "SAMPLING_CONFIG 5 200 1"),
                listOf("SENSOR_CONFIG OFF 50 ON")))
    }
    @Test fun operationalCommandsAreNeverReplayed() {
        assertTrue(mergePendingSensorSettings(emptyList(), listOf("SAMPLE", "TIME 123", "HELLO",
            "OFFLINE_STOP", "FACTORY_RESET", "OFFLINE_EXTERNAL_JOB 999", "ALERT_CONFIG ON 30 88")).isEmpty())
    }
    @Test fun alertConfigurationExcludesEnabledStateAndSessionIdentity() {
        val edit = alertSettingsEdit(listOf("ALERTS_CLEAR", "ALERT_RULE UVA ABOVE 4.0",
            "ALERT_CONFIG ON 30 999 SAVE 2 60 5"))!!
        assertTrue(edit.startsWith("ALERT_SETTINGS 30 SAVE 2 60 5 "))
        assertFalse(edit.contains("999"))
        assertFalse(edit.contains(" ON "))
        assertEquals(null, alertSettingsEdit(listOf("ALERT_CONFIG OFF 30 999")))
    }
    @Test fun noEnabledAlertRulesUsesDashNotAnEmptyProtocolToken() {
        assertEquals("ALERT_SETTINGS 30 SAVE 0 0 0 -", alertSettingsEdit(listOf("ALERTS_CLEAR", "ALERT_CONFIG OFF 30 0")))
    }
    @Test fun encodedUpdateIsOneBoundedLineWithoutPlaintextCredentials() {
        val line = UvirPendingSensorSettings(123, listOf("WIFI_CONFIG secret pass", "RADIO WIFI ON")).protocolCommand()
        assertTrue(line.startsWith("SETTINGS_APPLY 123 "))
        assertFalse(line.contains("\n"))
        assertFalse(line.contains("secret")) // encoding is NOT encryption; transport auth/TLS remain required.
    }
}
