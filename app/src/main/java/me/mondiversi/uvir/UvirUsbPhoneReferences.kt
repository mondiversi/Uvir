package me.mondiversi.uvir

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.withTimeout
import me.mondiversi.uvir.usb.PhoneOpticalFrame

/** No replay, old attachment or pre-edit frame can become a calibration reference.
 * Each module may contribute independently. Missing channels remain missing, never zero. */
internal suspend fun collectPhoneOpticalReference(
    frames: Flow<PhoneOpticalFrame>, generation: Long, revision: Long,
    isCurrent: () -> Boolean, progress: suspend (Int) -> Unit,
    durationMs: Long = OPTICAL_WINDOW_MEASUREMENT_MS
): List<PhoneOpticalFrame> {
    val rows = mutableListOf<PhoneOpticalFrame>()
    var firstAt: Long? = null
    withTimeout(durationMs + 10_000) {
        frames.takeWhile { frame ->
            check(isCurrent() && frame.generation == generation && frame.revision == revision) { "window_measurement_changed" }
            if (firstAt == null) firstAt = frame.capturedAtMs
            val elapsed = frame.capturedAtMs - firstAt!!
            check(elapsed >= 0) { "window_measurement_sequence_changed" }
            if (rows.size < 2_000) rows.add(frame)
            progress(elapsed.coerceAtMost(durationMs).toInt())
            elapsed < durationMs
        }.collect { }
    }
    check(isCurrent()) { "window_measurement_changed" }
    check(rows.isNotEmpty() && rows.last().capturedAtMs - rows.first().capturedAtMs >= durationMs) {
        "Not enough fresh readings"
    }
    return rows
}
