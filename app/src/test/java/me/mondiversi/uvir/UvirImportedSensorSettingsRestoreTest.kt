package me.mondiversi.uvir

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UvirImportedSensorSettingsRestoreTest {
    @Test
    fun confirmedSettingsIgnoreRuntimeActivityAndDisabledAlertRules() {
        val enabledRule =
            ThresholdAlertRule(
                metric = ThresholdAlertMetric.UVA,
                enabled = true,
                direction = ThresholdAlertDirection.ABOVE,
                threshold = 2.5f
            )
        val target = snapshot(
            alertMonitoringEnabled = false,
            alertSessionId = 0L,
            alertRules =
                listOf(
                    enabledRule,
                    ThresholdAlertRule(
                        metric = ThresholdAlertMetric.UVB,
                        enabled = false,
                        direction = ThresholdAlertDirection.BELOW,
                        threshold = 1f
                    )
                )
        )
        val current = target.copy(
            alertMonitoringEnabled = true,
            alertSessionId = 817L,
            alertRules =
                listOf(
                    ThresholdAlertRule(
                        metric = ThresholdAlertMetric.UVC,
                        enabled = false,
                        direction = ThresholdAlertDirection.ABOVE,
                        threshold = 999f
                    ),
                    enabledRule
                )
        )

        assertTrue(sensorBackedSettingsMatch(current, target))
    }

    @Test
    fun changedSensorParameterPreventsFalseConfirmation() {
        val target = snapshot()
        val current = target.copy(
            sensorParameters =
                target.sensorParameters.copy(statusLedBrightness = 75)
        )

        assertFalse(sensorBackedSettingsMatch(current, target))
    }

    @Test
    fun changedEnabledAlertThresholdPreventsFalseConfirmation() {
        val targetRule =
            ThresholdAlertRule(
                metric = ThresholdAlertMetric.UV_TOTAL,
                enabled = true,
                direction = ThresholdAlertDirection.ABOVE,
                threshold = 5f
            )
        val target = snapshot(alertRules = listOf(targetRule))
        val current = target.copy(
            alertRules = listOf(targetRule.copy(threshold = 6f))
        )

        assertFalse(sensorBackedSettingsMatch(current, target))
    }

    private fun snapshot(
        alertMonitoringEnabled: Boolean = false,
        alertSessionId: Long = 0L,
        alertRules: List<ThresholdAlertRule> = emptyList()
    ): UvirSensorSettingsSnapshot =
        UvirSensorSettingsSnapshot(
            schemaVersion = SENSOR_SETTINGS_SCHEMA_VERSION,
            firmwareVersion = "0.5.99",
            sensorParameters = SensorParameters(),
            acquisitionParameters =
                AcquisitionParameters(
                    samplesPerMeasurement = 5,
                    sampleSpacingMs = 150L,
                    discardExtremes = true
                ),
            calibrationSettings = SensorCalibrationSettings(),
            alertMonitoringEnabled = alertMonitoringEnabled,
            alertRepeatSeconds = 30,
            alertSessionId = alertSessionId,
            alertRules = alertRules,
            wifiEnabled = true,
            bluetoothEnabled = true,
            internetEnabled = false,
            internetUsePrimaryWifi = true,
            wifiSsid = "UVIR",
            internetRelayHost = "",
            internetRelayPort = DEFAULT_UVIR_RELAY_PORT,
            lastSyncedAt = 1L
        )
}
