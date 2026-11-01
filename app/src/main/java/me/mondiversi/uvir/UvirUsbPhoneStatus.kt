package me.mondiversi.uvir

internal val UsbPhoneState.activityInProgress: Boolean
    get() = automaticSession != null || alertSession != null || busy

internal fun simulatedUsbQualityFlags(state: UsbPhoneState, phase: Double): Int =
    if (state.simulation && state.saturationSimulation && phase.toLong() % 5L == 0L)
        UVIR_QUALITY_UV_OUT_OF_RANGE or UVIR_QUALITY_VISIBLE_NIR_OUT_OF_RANGE else 0

/** Keep connection errors and physical saturation visible even during recording. */
internal fun usbPhoneStatusTextRes(state: UsbPhoneState): Int = when {
    state.simulation -> R.string.sensor_status_simulated
    else -> when (state.status) {
        UsbPhoneStatus.DETACHED -> R.string.sensor_status_no_sensor
        UsbPhoneStatus.PERMISSION -> R.string.usb_permission
        UsbPhoneStatus.INITIALIZING -> R.string.usb_initializing
        UsbPhoneStatus.ERROR -> R.string.usb_error
        UsbPhoneStatus.READY -> when {
            state.overloadSample != null -> R.string.sensor_saturation
            state.activityInProgress -> R.string.sensor_info_activity
            state.ready -> R.string.usb_ready
            else -> R.string.usb_waiting
        }
    }
}
