package me.mondiversi.uvir

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UvirSensorConfigurationCommandTest {
    @Test
    fun sensorParametersIncludeAutonomousAndAutomaticShutdownSettings() {
        val command =
            sensorParametersCommand(
                SensorParameters(
                    autonomousRecordingEnabled = false,
                    automaticShutdownEnabled = true,
                    automaticShutdownSeconds = 3_661,
                    statusLedEnabled = true,
                    statusLedBrightness = 25,
                    statusBuzzerEnabled = false,
                    statusBuzzerVolume = 10
                )
            )

        assertEquals("SENSOR_CONFIG ON 25 OFF OFF 10 ON 3661", command)
    }

    @Test
    fun alertRepeatIntervalIsSentToSensor() {
        val commands =
            sensorAlertCommands(
                settings = ThresholdAlertSettings(
                    enabled = true,
                    rules =
                        listOf(
                            ThresholdAlertRule(
                                metric = ThresholdAlertMetric.UVA,
                                enabled = true,
                                direction = ThresholdAlertDirection.ABOVE,
                                threshold = 1f
                            )
                        ),
                    repeatSeconds = 30,
                    sound = ThresholdAlertSound.SILENT,
                    volume = 100
                ),
                sessionId = 12L
            )

        assertEquals("ALERT_CONFIG ON 30 12", commands.last())
    }

    @Test
    fun aConfiguredBellRemainsStoppedUntilTheSessionStarts() {
        val commands =
            sensorAlertCommands(
                settings = ThresholdAlertSettings(
                    enabled = false,
                    rules =
                        listOf(
                            ThresholdAlertRule(
                                metric = ThresholdAlertMetric.UVA,
                                enabled = true,
                                direction = ThresholdAlertDirection.ABOVE,
                                threshold = 1f
                            )
                        ),
                    repeatSeconds = 30,
                    sound = ThresholdAlertSound.SILENT,
                    volume = 100
                ),
                sessionId = 12L
            )

        assertEquals("ALERT_CONFIG OFF 30 12", commands.last())
    }

    @Test
    fun calibrationFactorsAreSentTogether() {
        val command =
            sensorCalibrationCommand(
                SensorCalibrationSettings(
                    visibleFactor = 1.25f,
                    uvFactor = 0.8f
                )
            )

        assertEquals("CALIBRATION_CONFIG 1.25 0.8", command)
    }

    @Test
    fun calibrationCommandConstrainsUnsafeFactors() {
        val command =
            sensorCalibrationCommand(
                SensorCalibrationSettings(
                    visibleFactor = 0.01f,
                    uvFactor = 50f
                )
            )

        assertTrue(command.endsWith("0.1 10.0"))
    }

    @Test
    fun calibrationRequiresCompatibleFirmware() {
        assertTrue(firmwareSupportsSensorCalibration("0.5.11"))
        assertTrue(firmwareSupportsSensorCalibration("0.6.0"))
        assertEquals(false, firmwareSupportsSensorCalibration("0.5.10"))
        assertEquals(false, firmwareSupportsSensorCalibration(""))
    }
}
