package me.mondiversi.uvir

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

internal enum class UvirConnectionHandoverRoute { LOCAL_SELECTION, USB, WIRELESS }

/** Never send a handover to another sensor just because its USB cable is present. */
internal fun uvirConnectionHandoverRoute(
    deviceId: String,
    previousMode: SensorConnectionMode,
    targetMode: SensorConnectionMode,
    usbDeviceId: String,
    usbReady: Boolean,
    wirelessDeviceId: String,
    wirelessReady: Boolean
): UvirConnectionHandoverRoute {
    if (targetMode == SensorConnectionMode.USB || deviceId.isBlank()) {
        return UvirConnectionHandoverRoute.LOCAL_SELECTION
    }
    val usbMatches = usbReady && normalizeSensorDeviceId(deviceId) == normalizeSensorDeviceId(usbDeviceId)
    val wirelessMatches = wirelessReady && normalizeSensorDeviceId(deviceId) == normalizeSensorDeviceId(wirelessDeviceId)
    return when {
        previousMode == SensorConnectionMode.USB && usbMatches -> UvirConnectionHandoverRoute.USB
        wirelessMatches -> UvirConnectionHandoverRoute.WIRELESS
        usbMatches -> UvirConnectionHandoverRoute.USB
        else -> UvirConnectionHandoverRoute.LOCAL_SELECTION
    }
}

internal fun SensorConnectionMode.wirelessHandoverCommand(): String? = when (this) {
    SensorConnectionMode.USB -> null // USB selection must not turn off wireless recovery.
    SensorConnectionMode.WIFI -> "WIRELESS WIFI"
    SensorConnectionMode.BLUETOOTH -> "WIRELESS BLUETOOTH"
    SensorConnectionMode.INTERNET -> "WIRELESS INTERNET"
}

internal fun wirelessHandoverTimeoutMs(timeoutMs: Long): Long = timeoutMs.coerceIn(1_500L, 120_000L)

internal fun isWirelessHandoverRejection(code: String): Boolean = code in setOf(
    "wifi_disabled", "wifi_not_configured", "bluetooth_disabled", "internet_not_configured"
)

/** A timeout, a rejection or a closed transport can never masquerade as an ACK. */
internal class UvirConnectionModeAcknowledgement(
    private val deviceId: String,
    private val mode: SensorConnectionMode
) {
    private val result = AtomicReference<Boolean?>(null)
    private val completed = CountDownLatch(1)

    fun confirm(reportedDeviceId: String, reportedMode: String): Boolean {
        val expectedMode = mode.name.lowercase(java.util.Locale.ROOT)
        if (deviceId.isBlank() ||
            normalizeSensorDeviceId(deviceId) != normalizeSensorDeviceId(reportedDeviceId) ||
            !reportedMode.equals(expectedMode, ignoreCase = true)
        ) return false
        return finish(true)
    }

    fun reject() = finish(false)

    private fun finish(confirmed: Boolean): Boolean {
        if (!result.compareAndSet(null, confirmed)) return false
        completed.countDown()
        return true
    }

    fun await(timeoutMs: Long): Boolean {
        if (!completed.await(timeoutMs.coerceAtLeast(0L), TimeUnit.MILLISECONDS)) reject()
        return result.get() == true
    }
}
