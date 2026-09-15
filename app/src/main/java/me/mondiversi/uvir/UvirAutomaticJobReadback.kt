package me.mondiversi.uvir

import java.util.concurrent.atomic.AtomicLong
import org.json.JSONObject

/** A complete live report, never restored from the phone's cached diagnostics. */
data class UvirAutomaticJobReadback(
    val deviceId: String,
    val sessionId: Long,
    val active: Boolean,
    val revision: Long
)

private val automaticJobReadbackRevision = AtomicLong()

internal fun currentAutomaticJobReadbackRevision(): Long = automaticJobReadbackRevision.get()

internal fun parseAutomaticJobReadback(json: JSONObject): UvirAutomaticJobReadback? {
    if (json.optString("type") != "hello" ||
        json.optString("protocol") != UVIR_SENSOR_PROTOCOL
    ) return null
    val deviceId = normalizeSensorDeviceId(json.optString("device_id"))
    val active = json.opt("offline_recording") as? Boolean ?: return null
    val rawSessionId = json.opt("offline_session_id")
    val sessionId = when (rawSessionId) {
        is Long -> rawSessionId
        is Int -> rawSessionId.toLong()
        else -> return null
    }
    if (deviceId.isBlank() || sessionId < 0L || (active && sessionId == 0L)) return null
    return UvirAutomaticJobReadback(deviceId, sessionId, active, automaticJobReadbackRevision.incrementAndGet())
}

/** Ignore pre-start reports until the sensor confirms the new job or the link is lost. */
internal data class UvirAutomaticJobReadbackGuard(
    val minimumRevision: Long = 0L,
    val mayResolveMissingJob: Boolean = true
) {
    fun afterStart() = UvirAutomaticJobReadbackGuard(currentAutomaticJobReadbackRevision(), false)
    fun afterDisconnection() = UvirAutomaticJobReadbackGuard(currentAutomaticJobReadbackRevision(), true)
    fun afterActiveConfirmation() = if (mayResolveMissingJob) this else
        UvirAutomaticJobReadbackGuard(currentAutomaticJobReadbackRevision(), true)
}

internal fun shouldResolveMissingAutomaticJob(
    readback: UvirAutomaticJobReadback?,
    guard: UvirAutomaticJobReadbackGuard,
    associatedDeviceId: String,
    connectionConfirmed: Boolean,
    simulated: Boolean,
    appJobActive: Boolean,
    appSessionId: Long
): Boolean = connectionConfirmed && !simulated && appJobActive && appSessionId > 0L &&
    guard.mayResolveMissingJob && readback != null && readback.revision > guard.minimumRevision &&
    readback.deviceId.isNotBlank() &&
    normalizeSensorDeviceId(associatedDeviceId) == normalizeSensorDeviceId(readback.deviceId) &&
    !readback.active && readback.sessionId == 0L
