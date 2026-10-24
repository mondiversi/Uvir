package me.mondiversi.uvir

import org.junit.Assert.*
import org.junit.Test

class UvirFirmwareUpdateReadinessTest {
    private val release = UvirFirmwareRelease("0.5.112", BuildConfig.VERSION_CODE,
        UvirUpdateAsset("", 1, ""), "a".repeat(32))
    private val ready = UvirUsbSensorState(status = UsbSensorConnectionStatus.CONNECTED,
        attached = true, appConnectionConfirmed = true, offlineSyncComplete = true,
        runtimeInfo = UvirSensorRuntimeInfo(deviceId = "TEST-SENSOR", operationActive = false,
            boardName = "ESP32 Dev Module", chipModel = "ESP32-D0WDQ6", firmwareVersion = "0.5.111",
            appPartitionSizeBytes = UvirUpdatePolicy.APP_CAPACITY.toLong(),
            offlineAcquisitions = 0, offlineAlerts = 0, offlineErrors = 0))

    @Test fun onlyTheCorrectAuthenticatedIdleAndSynchronizedUsbSensorIsReady() {
        assertTrue(uvirFirmwareUpdateReady(ready, "test-sensor", release))
        assertFalse(uvirFirmwareUpdateReady(ready, "OTHER", release))
        assertFalse(uvirFirmwareUpdateReady(ready.copy(runtimeInfo = ready.runtimeInfo.copy(deviceId = "")), "", release))
        for (status in UsbSensorConnectionStatus.entries.filter { it != UsbSensorConnectionStatus.CONNECTED })
            assertFalse(status.name, uvirFirmwareUpdateReady(ready.copy(status = status), "TEST-SENSOR", release))
        for (state in listOf(ready.copy(attached = false), ready.copy(appConnectionConfirmed = false),
            ready.copy(offlineSyncComplete = false)))
            assertFalse(uvirFirmwareUpdateReady(state, "TEST-SENSOR", release))
    }

    @Test fun activeUnknownOrUnsynchronizedStatesFailClosed() {
        val info = ready.runtimeInfo
        for (blocked in listOf(info.copy(operationActive = true), info.copy(operationActive = null),
            info.copy(offlineAcquisitions = 1), info.copy(offlineAcquisitions = null),
            info.copy(offlineAlerts = 1), info.copy(offlineAlerts = null),
            info.copy(offlineErrors = 1), info.copy(offlineErrors = null)))
            assertFalse(blocked.toString(), uvirFirmwareUpdateReady(ready.copy(runtimeInfo = blocked), "TEST-SENSOR", release))
    }

    @Test fun unsupportedHardwareVersionsAndAppRequirementsNeverEnableFlashing() {
        for (blocked in listOf(ready.runtimeInfo.copy(boardName = "Other"),
            ready.runtimeInfo.copy(chipModel = "ESP32-S3"), ready.runtimeInfo.copy(appPartitionSizeBytes = 0),
            ready.runtimeInfo.copy(firmwareVersion = "0.5.101"), ready.runtimeInfo.copy(firmwareVersion = ""),
            ready.runtimeInfo.copy(firmwareVersion = "0.5.112"), ready.runtimeInfo.copy(firmwareVersion = "0.5.113")))
            assertFalse(uvirFirmwareUpdateReady(ready.copy(runtimeInfo = blocked), "TEST-SENSOR", release))
        assertFalse(uvirFirmwareUpdateReady(ready, "TEST-SENSOR", release, appCode = release.minAppCode - 1))
    }

    @Test fun appAndFirmwareFailuresUseDifferentMessages() {
        assertEquals(R.string.update_app_failed, uvirUpdateFailureMessage(UvirUpdateOperation.APP))
        assertEquals(R.string.update_failed, uvirUpdateFailureMessage(UvirUpdateOperation.FIRMWARE))
        assertNotEquals(uvirUpdateFailureMessage(UvirUpdateOperation.APP),
            uvirUpdateFailureMessage(UvirUpdateOperation.FIRMWARE))
    }
}
