package me.mondiversi.uvir

import org.junit.Assert.*
import org.junit.Test

class UvirInitialSensorSettingsTest {
    private val configured = ThresholdAlertSettings(
        enabled = false,
        rules = listOf(
            ThresholdAlertRule(ThresholdAlertMetric.UV_TOTAL, true, ThresholdAlertDirection.BELOW, 0.8f),
            ThresholdAlertRule(ThresholdAlertMetric.UVA, true, ThresholdAlertDirection.ABOVE, 3f),
            ThresholdAlertRule(ThresholdAlertMetric.BIO_DNA_UV, true, ThresholdAlertDirection.ABOVE, 4f)
        ),
        repeatSeconds = 60,
        sound = ThresholdAlertSound.DOUBLE_BEEP,
        volume = 70
    )

    private fun snapshot(monitoring: Boolean = false, sessionId: Long = 0L,
        rules: List<ThresholdAlertRule> = emptyList()) = UvirSensorSettingsSnapshot(
        schemaVersion = 1, firmwareVersion = "0.5.75",
        sensorParameters = SensorParameters(),
        acquisitionParameters = AcquisitionParameters(5, 150, true),
        calibrationSettings = SensorCalibrationSettings(),
        alertMonitoringEnabled = monitoring, alertRepeatSeconds = 30,
        alertSessionId = sessionId, alertRules = rules,
        wifiEnabled = true, bluetoothEnabled = true, internetEnabled = false,
        internetUsePrimaryWifi = true, wifiSsid = "", internetRelayHost = "",
        internetRelayPort = 8883
    )

    @Test fun simulationNeverReadsTheRealSensorCache() {
        assertNull(readInitialSensorSettings(true) { error("Real sensor cache must not be read") })
    }

    @Test fun fakeReopenKeepsConfiguredBellsWithoutStartingMonitoring() {
        val initial = readInitialSensorSettings(true) { snapshot() }
        val restored = initial?.sensorBackedAlertSettings(configured) ?: configured
        assertEquals(configured, restored)
        assertTrue(restored.hasConfiguredRules())
        assertFalse(restored.hasActiveMonitoring())
        assertFalse(shouldStartNewThresholdAlertSession(configured, restored, 0L))
    }

    @Test fun fakeReopenNeverBorrowsARealRunningSessionOrItsRules() {
        val cached = snapshot(true, 99L, listOf(
            ThresholdAlertRule(ThresholdAlertMetric.BLUE, true, ThresholdAlertDirection.ABOVE, 25f)))
        val initial = readInitialSensorSettings(true) { cached }
        val restored = initial?.sensorBackedAlertSettings(configured) ?: configured
        val sessionId = initial?.alertSessionId ?: 7L
        assertEquals(configured, restored)
        assertEquals(7L, sessionId)
        assertFalse(restored.hasActiveMonitoring())
    }

    @Test fun fakeRunningSessionIsNotStoppedByAnIdleRealSensorCache() {
        val running = configured.copy(enabled = true)
        val initial = readInitialSensorSettings(true) { snapshot() }
        val restored = initial?.sensorBackedAlertSettings(running) ?: running
        assertEquals(running, restored)
        assertTrue(restored.hasActiveMonitoring())
    }

    @Test fun realSensorStillUsesItsOwnCachedProfile() {
        val cached = snapshot(true, 42L, configured.rules)
        var reads = 0
        val initial = readInitialSensorSettings(false) { reads++; cached }
        assertSame(cached, initial)
        assertEquals(1, reads)
        assertEquals(42L, initial!!.alertSessionId)
        assertTrue(initial.sensorBackedAlertSettings(configured).hasActiveMonitoring())
    }

    @Test fun emptyRealSensorCacheStillDisablesForeignPhoneRules() {
        val initial = readInitialSensorSettings(false) { snapshot() }!!
        val restored = initial.sensorBackedAlertSettings(configured)
        assertFalse(restored.hasConfiguredRules())
        assertFalse(restored.hasActiveMonitoring())
        assertEquals(configured.sound, restored.sound)
        assertEquals(configured.volume, restored.volume)
    }

    @Test fun noRealProfileFallsBackToPhoneSettingsAsBefore() {
        val initial = readInitialSensorSettings(false) { null }
        assertEquals(configured, initial?.sensorBackedAlertSettings(configured) ?: configured)
    }
}
