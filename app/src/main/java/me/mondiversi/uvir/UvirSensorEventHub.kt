package me.mondiversi.uvir

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.receiveAsFlow
import org.json.JSONObject

/** Identity is captured by the authenticated transport, not inferred from the selected UI. */
internal data class UvirStoredDeviceEvent<T>(
    val deviceId: String,
    val event: T,
    val stored: Boolean = true,
    val newRecord: Boolean = false,
    val sessionId: Long = 0L
)

internal sealed interface UvirDeviceFrame {
    val deviceId: String
    data class Runtime(override val deviceId: String, val event: SensorRuntimeEvent) : UvirDeviceFrame
    data class Sync(override val deviceId: String, val event: SensorSyncEvent) : UvirDeviceFrame
    data class Alert(override val deviceId: String, val source: SensorSyncSource,
        val event: SensorLiveAlertEvent, val qualityFlags: Int) : UvirDeviceFrame
}

internal object UvirSensorEventHub {
    // One retained consumer persists all devices in arrival order, including UI transitions.
    private val incoming = Channel<UvirDeviceFrame>(Channel.UNLIMITED)
    val frames = incoming.receiveAsFlow()
    private val liveAlerts = MutableSharedFlow<UvirStoredDeviceEvent<SensorLiveAlertEvent>>(extraBufferCapacity = 128)
    val alerts = liveAlerts.asSharedFlow()
    fun emitRuntime(deviceId: String, event: SensorRuntimeEvent) {
        if (deviceId.isNotBlank()) incoming.trySend(UvirDeviceFrame.Runtime(deviceId, event))
    }
    fun emitSync(deviceId: String, event: SensorSyncEvent) {
        if (deviceId.isNotBlank()) incoming.trySend(UvirDeviceFrame.Sync(deviceId, event))
    }
    fun emitLiveAlert(deviceId: String, source: SensorSyncSource, event: SensorLiveAlertEvent, qualityFlags: Int) {
        if (deviceId.isNotBlank()) incoming.trySend(UvirDeviceFrame.Alert(deviceId, source, event, qualityFlags))
    }
    suspend fun publishAlert(event: UvirStoredDeviceEvent<SensorLiveAlertEvent>) { liveAlerts.emit(event) }
}

internal fun JSONObject.toUvirLiveAlert(sequence: Long) = SensorLiveAlertEvent(
    receiptSequence = sequence,
    timestampMs = optLong("timestamp_ms").takeIf { it > 0L } ?: System.currentTimeMillis(),
    details = optString("details"),
    sessionId = optLong("session_id", 0L),
    recorded = optBoolean("recorded", true)
)
