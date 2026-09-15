package me.mondiversi.uvir

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UvirSensorRuntimeInfoTest {
    @Test
    fun signalLevelsUseStableFourStepScale() {
        assertEquals(4, wifiSignalLevel(-45))
        assertEquals(3, wifiSignalLevel(-60))
        assertEquals(2, wifiSignalLevel(-70))
        assertEquals(1, wifiSignalLevel(-82))

        assertEquals(4, bluetoothSignalLevel(10))
        assertEquals(3, bluetoothSignalLevel(0))
        assertEquals(2, bluetoothSignalLevel(-5))
        assertEquals(1, bluetoothSignalLevel(-12))
    }

    @Test
    fun signalPercentagesAreClampedAndMonotonic() {
        assertEquals(0, wifiSignalPercentage(-110))
        assertEquals(60, wifiSignalPercentage(-70))
        assertEquals(100, wifiSignalPercentage(-40))

        assertEquals(0, bluetoothSignalPercentage(-30))
        assertEquals(66, bluetoothSignalPercentage(0))
        assertEquals(100, bluetoothSignalPercentage(20))
    }

    @Test
    fun completeFirmwareSnapshotBecomesPerSensorSettings() {
        val info =
            UvirSensorRuntimeInfo(
                sensorSettingsSchemaVersion = 1,
                firmwareVersion = "0.5.65",
                visibleCalibrationFactor = 1.25,
                uvCalibrationFactor = 0.8,
                samplingSamplesPerMeasurement = 7,
                samplingSpacingMs = 750,
                samplingDiscardExtremes = false,
                autonomousRecordingEnabled = true,
                automaticShutdownEnabled = true,
                automaticShutdownSeconds = 3600,
                statusLedEnabled = true,
                statusLedBrightness = 12,
                statusBuzzerEnabled = false,
                statusBuzzerVolume = 9,
                alertMonitoringEnabled = true,
                alertSessionId = 42,
                offlineAlertRepeatSeconds = 30,
                alertRules =
                    listOf(
                        ThresholdAlertRule(
                            ThresholdAlertMetric.UVA,
                            true,
                            ThresholdAlertDirection.ABOVE,
                            1.5f
                        ),
                        ThresholdAlertRule(
                            ThresholdAlertMetric.BIO_DNA_UV,
                            true,
                            ThresholdAlertDirection.BELOW,
                            0.4f
                        )
                    ),
                wifiEnabled = true,
                bluetoothEnabled = false,
                internetEnabled = true,
                internetUsePrimaryWifi = true,
                wifiNetwork = "Lab",
                internetBrokerHost = "broker.example",
                internetBrokerPort = 8883
            )

        val snapshot = requireNotNull(info.toSensorSettingsSnapshotOrNull())
        assertEquals(7, snapshot.acquisitionParameters.samplesPerMeasurement)
        assertEquals(750L, snapshot.acquisitionParameters.sampleSpacingMs)
        assertFalse(snapshot.acquisitionParameters.discardExtremes)
        assertEquals(42L, snapshot.alertSessionId)
        assertEquals(2, snapshot.alertRules.size)
        assertEquals(ThresholdAlertMetric.UVA, snapshot.alertRules.first().metric)
        assertEquals("broker.example", snapshot.internetRelayHost)
    }

    @Test
    fun legacyHelloCannotOverwriteLocalSensorSettings() {
        val info =
            UvirSensorRuntimeInfo(
                firmwareVersion = "0.5.64",
                samplingSamplesPerMeasurement = 5
            )

        assertNull(info.toSensorSettingsSnapshotOrNull())
        assertFalse(firmwareSupportsSensorSettingsSnapshot("0.5.64"))
        assertTrue(firmwareSupportsSensorSettingsSnapshot("0.5.65"))
    }

    @Test
    fun sensorRulesAreExpandedForEveryAlertEditor() {
        val snapshot =
            UvirSensorSettingsSnapshot(
                schemaVersion = 1,
                firmwareVersion = "0.5.65",
                sensorParameters = SensorParameters(),
                acquisitionParameters = AcquisitionParameters(5, 500, true),
                calibrationSettings = SensorCalibrationSettings(),
                alertMonitoringEnabled = false,
                alertRepeatSeconds = 30,
                alertSessionId = 0,
                alertRules =
                    listOf(
                        ThresholdAlertRule(
                            metric = ThresholdAlertMetric.UVA,
                            enabled = true,
                            direction = ThresholdAlertDirection.ABOVE,
                            threshold = 2f
                        )
                    ),
                wifiEnabled = true,
                bluetoothEnabled = true,
                internetEnabled = false,
                internetUsePrimaryWifi = true,
                wifiSsid = "",
                internetRelayHost = "",
                internetRelayPort = 8883
            )
        val previous =
            ThresholdAlertSettings(
                enabled = false,
                rules =
                    ThresholdAlertMetric.entries.map { metric ->
                        ThresholdAlertRule(
                            metric,
                            false,
                            ThresholdAlertDirection.BELOW,
                            1f
                        )
                    },
                repeatSeconds = 60,
                sound = ThresholdAlertSound.DOUBLE_BEEP,
                volume = 70
            )

        val merged = snapshot.sensorBackedAlertSettings(previous)
        assertEquals(ThresholdAlertMetric.entries.size, merged.rules.size)
        assertTrue(merged.rules.single { it.metric == ThresholdAlertMetric.UVA }.enabled)
        assertFalse(merged.enabled)
        assertEquals(ThresholdAlertSound.DOUBLE_BEEP, merged.sound)
    }

}
