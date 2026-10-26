package me.mondiversi.uvir

import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

internal data class OpticalWindowMeasurementFrame(
    val connected: Boolean,
    val busy: Boolean,
    val context: OpticalWindowMeasurementContext?,
    val sequence: Long,
    val sample: SensorSample?
)

internal fun opticalWindowMeasurementFrame(deviceId: String?, mode: SensorConnectionMode, generation: Long,
    connected: Boolean, info: UvirSensorRuntimeInfo, sequence: Long, sample: SensorSample?,
    uvAvailable: Boolean): OpticalWindowMeasurementFrame {
    val window = sample?.opticalWindow
    val context = if (deviceId.isNullOrBlank() || window == null || info.opticalWindow != window) null else
        OpticalWindowMeasurementContext(normalizeSensorDeviceId(deviceId), mode, generation,
            info.firmwareVersion, info.settingsUpdatedAtMs, info.visibleCalibrationFactor,
            info.uvCalibrationFactor, info.calibrationOffsets, info.calibrationChannelFactors, window,
            info.samplingSamplesPerMeasurement, info.samplingSpacingMs, info.samplingDiscardExtremes,
            info.sensorAvailable != false, uvAvailable)
    return OpticalWindowMeasurementFrame(connected, info.calibrationChangeBlocked, context, sequence, sample)
}

/** Read-only observer: no calibration commands, recordings, stream changes or cancellation commands. */
internal suspend fun measureOpticalWindowReading(
    hardwareId: String,
    expected: OpticalWindowMeasurementContext?,
    frames: Flow<OpticalWindowMeasurementFrame>,
    current: () -> OpticalWindowMeasurementFrame,
    onProgress: (Int) -> Unit,
    durationMs: Long = OPTICAL_WINDOW_MEASUREMENT_MS,
    staleMs: Long = 6_000L,
    now: () -> Long = { System.nanoTime() / 1_000_000L }
): OpticalWindowReading = coroutineScope {
    val initial = current()
    val context = checkNotNull(initial.context) { "window_measurement_unavailable" }
    check(normalizeSensorDeviceId(context.deviceId) == normalizeSensorDeviceId(hardwareId) &&
        initial.connected && !initial.busy) {
        "window_measurement_connection_changed"
    }
    check(expected == null || context == expected) { "window_measurement_changed" }
    fun valid(): Boolean {
        val next = current()
        return next.connected && !next.busy && next.context == context
    }
    val accumulator = OpticalWindowMeasurementAccumulator(context)
    var sequence = initial.sequence
    val started = now()
    val acceptedAt = AtomicLong(started)
    val collector = launch(start = CoroutineStart.UNDISPATCHED) {
        frames.collect { frame ->
            check(frame.connected && !frame.busy && frame.context == context) { "window_measurement_changed" }
            check(frame.sequence >= sequence) { "window_measurement_sequence_changed" }
            if (frame.sequence > sequence) {
                sequence = frame.sequence
                frame.sample?.let {
                    accumulator.add(sequence, it)
                    acceptedAt.set(now())
                }
            }
        }
    }
    try {
        onProgress(0)
        while (now() - started < durationMs) {
            delay(minOf(200L, (durationMs - (now() - started)).coerceAtLeast(1L)))
            check(valid()) { "window_measurement_changed" }
            check(now() - acceptedAt.get() <= staleMs) { "window_measurement_stale" }
            onProgress((now() - started).coerceIn(0L, durationMs).toInt())
        }
    } finally { collector.cancelAndJoin() }
    check(valid()) { "window_measurement_changed" }
    accumulator.finish(::valid)
}
