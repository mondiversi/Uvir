package me.mondiversi.uvir

import org.junit.Assert.*
import org.junit.Test

class UvirUsbPhoneStatusTest {
    @Test fun simulatedSaturationOnlyFlagsSyntheticReadingsAndKeepsTheOrangeStatus() {
        val base = UsbPhoneState(simulation = true)
        val saturated = base.copy(saturationSimulation = true)
        assertEquals(3, simulatedUsbQualityFlags(saturated, 5.0))
        assertEquals(0, simulatedUsbQualityFlags(saturated, 6.0))
        assertEquals(0, simulatedUsbQualityFlags(base, 5.0))
        assertEquals(0, simulatedUsbQualityFlags(saturated.copy(simulation = false), 5.0))
        assertEquals(R.string.sensor_status_simulated, usbPhoneStatusTextRes(saturated))
    }
    @Test fun actualWorkShowsActivityButACompletedManualSessionDoesNot() {
        val idle = UsbPhoneState(status = UsbPhoneStatus.READY, sample = SensorSample())
        for (active in listOf(idle.copy(automaticSession = 1), idle.copy(alertSession = 2), idle.copy(busy = true))) {
            assertTrue(active.activityInProgress)
            assertEquals(R.string.sensor_info_activity, usbPhoneStatusTextRes(active))
            assertEquals(R.string.sensor_info_activity, usbPhoneStatusTextRes(active.copy(sample = null)))
        }
        assertFalse(idle.copy(manualSession = 3, manualCount = 10).activityInProgress)
        assertEquals(R.string.usb_ready, usbPhoneStatusTextRes(idle.copy(manualSession = 3)))
    }

    @Test fun saturationAndConnectionProblemsCannotBeHiddenByActivity() {
        val active = UsbPhoneState(status = UsbPhoneStatus.READY, busy = true,
            overloadSample = SensorSample(qualityFlags = UVIR_QUALITY_VISIBLE_NIR_OUT_OF_RANGE))
        assertEquals(R.string.sensor_saturation, usbPhoneStatusTextRes(active))
        assertEquals(R.string.usb_error, usbPhoneStatusTextRes(active.copy(status = UsbPhoneStatus.ERROR)))
        assertEquals(R.string.sensor_status_no_sensor, usbPhoneStatusTextRes(active.copy(status = UsbPhoneStatus.DETACHED)))
        for (status in UsbPhoneStatus.entries) {
            assertEquals(R.string.sensor_status_simulated, usbPhoneStatusTextRes(active.copy(status = status, simulation = true)))
        }
    }
}
