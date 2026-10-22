package me.mondiversi.uvir

import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

internal const val DARK_OFFSETS_MINIMUM_FIRMWARE = "0.5.110"
internal enum class CalibrationGroup(val protocolName: String, val indices: IntRange) {
    UV("UV", 0..2), VISIBLE("VIS", 3..10)
}

internal data class DarkOffsetReading(val group: CalibrationGroup, val values: List<Float>, val samples: Int) {
    init {
        require(samples >= 20 && values.size == group.indices.count())
        require(values.all { it.isFinite() && it in 0f..MAX_CALIBRATION_OFFSET_W_M2 })
    }
}

internal data class DarkOffsetFrame(
    val requestId: String, val deviceId: String, val group: String, val state: String,
    val elapsedMs: Int = 0, val samples: Int = 0, val unit: String = "",
    val values: List<Float> = emptyList(), val error: String = "cancelled"
)

/** Preserve the other group, including lossless legacy physical-channel offsets. */
internal fun CalibrationOffsets.withDarkReading(reading: DarkOffsetReading): CalibrationOffsets {
    val base = if (isLegacyPhysical && reading.group == CalibrationGroup.VISIBLE) forColorEditor() else this
    return base.copy(values = base.values.toMutableList().apply {
        reading.group.indices.forEachIndexed { i, channel -> set(channel, reading.values[i]) }
    })
}

internal fun SensorCalibrationSettings.resetGroup(group: CalibrationGroup): SensorCalibrationSettings {
    val baseOffsets = offsets ?: CalibrationOffsets()
    val offsetIndices = if (baseOffsets.isLegacyPhysical && group == CalibrationGroup.VISIBLE) 3..14 else group.indices
    val gains = channelFactors ?: CalibrationChannelFactors.fromModuleFactors(visibleFactor, uvFactor)
    return copy(
        visibleFactor = if (group == CalibrationGroup.VISIBLE) 1f else visibleFactor,
        uvFactor = if (group == CalibrationGroup.UV) 1f else uvFactor,
        offsets = baseOffsets.copy(values = baseOffsets.values.mapIndexed { i, v -> if (i in offsetIndices) 0f else v }),
        channelFactors = gains.copy(values = gains.values.mapIndexed { i, v -> if (i in group.indices) 1f else v })
    )
}

/** One request per transport instance; unrelated/late/device-mismatched frames cannot be applied. */
internal class UvirDarkOffsetClient {
    private data class Pending(val id: String, val deviceId: String, val group: CalibrationGroup,
        val frames: Channel<DarkOffsetFrame> = Channel(Channel.CONFLATED))
    private val lock = Any()
    private var pending: Pending? = null

    suspend fun measure(deviceId: String, group: CalibrationGroup, connectionValid: () -> Boolean,
        send: (String) -> Boolean, onProgress: (Int) -> Unit): DarkOffsetReading {
        check(deviceId.isNotBlank() && connectionValid()) { "dark_offsets_connection_changed" }
        val request = Pending(UUID.randomUUID().toString().replace("-", "").uppercase(), deviceId, group)
        synchronized(lock) { check(pending == null) { "dark_offsets_busy" }; pending = request }
        var ready = false
        try {
            check(withContext(Dispatchers.IO) {
                connectionValid() && send("DARK_OFFSETS START ${group.protocolName} ${request.id}")
            }) { "dark_offsets_write_failed" }
            return withTimeout(40_000L) {
                while (true) {
                    val frame = withTimeout(6_000L) { request.frames.receive() }
                    check(connectionValid()) { "dark_offsets_connection_changed" }
                    when (frame.state) {
                        "progress" -> onProgress(frame.elapsedMs.coerceIn(0, 30_000))
                        "ready" -> {
                            check(frame.unit in setOf(UVIR_CANONICAL_IRRADIANCE_UNIT, UVIR_LEGACY_IRRADIANCE_UNIT)) { "dark_offsets_invalid_unit" }
                            val scale = irradianceUnitScaleToWm2(frame.unit)
                            val reading = DarkOffsetReading(group, frame.values.map { (it * scale).toFloat() }, frame.samples)
                            ready = true
                            return@withTimeout reading
                        }
                        else -> error("dark_offsets_${frame.error}")
                    }
                }
                @Suppress("UNREACHABLE_CODE") error("unreachable")
            }
        } finally {
            // Best effort only to the SAME transport generation, even on popup cancellation.
            try {
                if (!ready) withContext(NonCancellable + Dispatchers.IO) {
                    if (connectionValid()) runCatching { send("DARK_OFFSETS CANCEL ${request.id}") }
                }
            } finally {
                // Returning from IO can rethrow cancellation: cleanup must still release the slot.
                synchronized(lock) { if (pending === request) pending = null }
                request.frames.close()
            }
        }
    }

    fun receive(frame: JSONObject) {
        val parsed = runCatching {
            val values = frame.optJSONArray("values")
            DarkOffsetFrame(frame.getString("request_id"), frame.getString("device_id"),
                frame.getString("group"), frame.getString("state"), frame.optInt("elapsed_ms"),
                frame.optInt("samples"), frame.optString("unit"),
                if (values == null) emptyList() else List(values.length()) { values.getDouble(it).toFloat() },
                frame.optString("error", "cancelled"))
        }.getOrNull() ?: return
        receive(parsed)
    }

    fun receive(frame: DarkOffsetFrame) {
        val request = synchronized(lock) { pending } ?: return
        if (frame.requestId != request.id || !frame.deviceId.equals(request.deviceId, true) ||
            frame.group != request.group.protocolName) return
        request.frames.trySend(frame)
    }
}
