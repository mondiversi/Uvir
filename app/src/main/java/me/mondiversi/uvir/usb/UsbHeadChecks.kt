package me.mondiversi.uvir.usb

import java.util.concurrent.CancellationException

/** Diagnostic evidence only; never a calibrated measurement or an archived acquisition. */
internal data class UsbOpticalCheck(
    val recognized: Boolean? = null,
    val initialized: Boolean? = null,
    val readable: Boolean? = null,
    val chipId: Int? = null,
    val error: String? = null
) {
    val passed: Boolean get() = recognized == true && initialized == true && readable == true
}

internal data class UsbHeadChecks(
    val generation: Long,
    val adapterName: String,
    val usbOpened: Boolean? = null,
    val mpsseReady: Boolean? = null,
    val i2cReady: Boolean? = null,
    val uv: UsbOpticalCheck = UsbOpticalCheck(),
    val visible: UsbOpticalCheck = UsbOpticalCheck(),
    val complete: Boolean = false,
    val error: String? = null
) {
    val adapterPassed: Boolean get() = usbOpened == true && mpsseReady == true && i2cReady == true
    val passed: Boolean get() = complete && adapterPassed && uv.passed && visible.passed
}

internal fun checkUsbOpticalComponent(
    initialize: () -> Unit,
    chipId: () -> Int?,
    identityMatches: (Int) -> Boolean,
    capture: () -> DoubleArray,
    expectedChannels: Int,
    isCurrent: () -> Boolean,
    onError: (Exception) -> Unit
): UsbOpticalCheck {
    fun current() { if (!isCurrent()) throw CancellationException("USB connection changed") }
    current()
    try {
        initialize()
        current()
        val id = chipId()
        require(id != null && identityMatches(id)) { "Optical chip identity was not verified" }
    } catch (error: Exception) {
        if (error is CancellationException) throw error
        current()
        onError(error)
        val id = chipId()
        return UsbOpticalCheck(
            recognized = id?.let(identityMatches) ?: false.takeIf { error is I2cNack },
            initialized = false, chipId = id, error = error.message ?: error.javaClass.simpleName)
    }
    val id = chipId()
    try {
        val values = capture()
        current()
        require(values.size == expectedChannels && values.all { it.isFinite() && it >= 0.0 }) {
            "Invalid optical channel frame"
        }
        return UsbOpticalCheck(true, true, true, id)
    } catch (error: Exception) {
        if (error is CancellationException) throw error
        current()
        onError(error)
        return UsbOpticalCheck(true, true, false, id, error.message ?: error.javaClass.simpleName)
    }
}

/** One module failing must not suppress recognition/testing of the other one. */
internal fun checkUsbOpticalModules(
    uv: As7331, visible: As7343,
    isCurrent: () -> Boolean,
    onError: (String, Exception) -> Unit
): Pair<UsbOpticalCheck, UsbOpticalCheck> {
    val ultraviolet = checkUsbOpticalComponent(uv::initialize, { uv.deviceId }, { it and 0xf0 == 0x20 },
        { uv.capture().wm2 }, 3, isCurrent) { onError("usb_diagnostic_uv", it) }
    val visibleInfrared = checkUsbOpticalComponent(visible::initialize, { visible.deviceId }, { it == 0x81 },
        { visible.capture().wm2 }, 8, isCurrent) { onError("usb_diagnostic_vis_ir", it) }
    return ultraviolet to visibleInfrared
}
