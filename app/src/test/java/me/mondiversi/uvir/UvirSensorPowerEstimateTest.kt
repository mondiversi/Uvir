package me.mondiversi.uvir

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UvirSensorPowerEstimateTest {
    @Test
    fun wifiEstimateIsHigherThanUsbEstimate() {
        val info = UvirSensorRuntimeInfo(
            sensorAvailable = true,
            streaming = true,
            streamIntervalMs = 500,
            integrationMs = 100.0
        )

        val usb = estimateUvirSensorPower(SensorConnectionMode.USB, info)
        val wifi = estimateUvirSensorPower(SensorConnectionMode.WIFI, info)

        assertTrue(wifi.currentMilliAmps > usb.currentMilliAmps)
    }

    @Test
    fun activeTransportTakesPriorityOverSelectedSource() {
        val estimate = estimateUvirSensorPower(
            selectedConnectionMode = SensorConnectionMode.USB,
            sensorInfo = UvirSensorRuntimeInfo(activeTransport = "wifi")
        )

        assertTrue(estimate.currentMilliAmps >= 105.0)
    }

    @Test
    fun powerAndOneHourEnergyRemainDimensionallyConsistent() {
        val estimate = estimateUvirSensorPower(
            selectedConnectionMode = SensorConnectionMode.BLUETOOTH,
            sensorInfo = UvirSensorRuntimeInfo()
        )

        assertEquals(
            estimate.currentMilliAmps * estimate.referenceVoltageVolts,
            estimate.powerMilliWatts,
            0.0001
        )
        assertEquals(
            estimate.powerMilliWatts,
            estimate.energyForOneHourMilliWattHours,
            0.0001
        )
        assertTrue(estimate.minimumPowerMilliWatts < estimate.peakPowerMilliWatts)
    }
}
