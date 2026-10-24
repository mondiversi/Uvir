package me.mondiversi.uvir

/** One fail-closed eligibility policy for the dialog and USB preflight. */
internal fun uvirFirmwareUpdateReady(
    usb: UvirUsbSensorState,
    expectedUid: String,
    release: UvirFirmwareRelease,
    appCode: Int = BuildConfig.VERSION_CODE
): Boolean {
    val info = usb.runtimeInfo
    return expectedUid.isNotBlank() &&
        appCode >= release.minAppCode &&
        usb.status == UsbSensorConnectionStatus.CONNECTED && usb.attached &&
        usb.appConnectionConfirmed && usb.offlineSyncComplete &&
        info.deviceId.equals(expectedUid, true) &&
        info.operationActive == false &&
        info.offlineAcquisitions == 0 && info.offlineAlerts == 0 && info.offlineErrors == 0 &&
        info.boardName == "ESP32 Dev Module" && info.chipModel.startsWith("ESP32-D0WD") &&
        info.appPartitionSizeBytes == UvirUpdatePolicy.APP_CAPACITY.toLong() &&
        compareFirmwareVersions(info.firmwareVersion, "0.5.102") >= 0 &&
        compareFirmwareVersions(release.version, info.firmwareVersion) > 0
}
