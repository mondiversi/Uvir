package me.mondiversi.uvir

import org.json.JSONObject

internal data class UvirRtcClockObservation(
    val deviceId: String,
    val oscillatorStopped: Boolean,
    val receivedAtMs: Long
)

/** Read the accepted HELLO itself, never a saved or already resynchronized snapshot. */
internal fun JSONObject.toUvirRtcClockObservation(
    expectedDeviceId: String,
    receivedAtMs: Long
): UvirRtcClockObservation? {
    val id = optString("device_id").trim()
    if (optString("protocol") != UVIR_SENSOR_PROTOCOL || optString("type") != "hello" ||
        id.isBlank() || normalizeSensorDeviceId(id) != normalizeSensorDeviceId(expectedDeviceId) ||
        opt("rtc_available") != true
    ) return null
    // Older firmware, a missing module, and I2C errors are not evidence of a flat battery.
    val stopped = opt("rtc_oscillator_stopped") as? Boolean ?: return null
    if (!stopped && opt("rtc_read_ok") == false) return null
    return UvirRtcClockObservation(id, stopped, receivedAtMs)
}

/** One fresh check per device and app launch; survives Activity/theme/locale recreation. */
internal class UvirRtcStartupCheck(private val startedAtMs: Long) {
    private val checked = mutableSetOf<String>()
    private val pending = linkedMapOf<String, String>()

    // The retained runtime serializes all accesses, including dismissal and batch delivery.
    fun observe(observation: UvirRtcClockObservation): Boolean {
        val id = normalizeSensorDeviceId(observation.deviceId)
        if (id.isBlank() || observation.receivedAtMs < startedAtMs || !checked.add(id)) return false
        if (!observation.oscillatorStopped) return false
        pending[id] = observation.deviceId.trim()
        return true
    }

    fun pendingDeviceIds(): List<String> = pending.values.toList()

    fun dismiss(deviceIds: Collection<String>) {
        deviceIds.forEach { pending.remove(normalizeSensorDeviceId(it)) }
    }
}
