package me.mondiversi.uvir.usb

import kotlinx.coroutines.delay

/** Only conversion/adaptation failures are retried. A broken USB/I²C transport propagates. */
internal suspend fun <T> retryOpticalSample(read: () -> T): T {
    repeat(3) { attempt ->
        try { return read() }
        catch (error: InvalidSensorReading) {
            // Minimum/locked gain cannot improve within this frame. Retry on the
            // next live cycle instead, so the other module is not blocked by nine
            // identical saturated conversions.
            if (!error.retryable || error.rangeExhausted || attempt == 2) throw error
            delay(50)
        }
    }
    error("Unreachable")
}

internal data class PhoneOpticalFrame(
    val generation: Long,
    val revision: Long,
    val capturedAtMs: Long,
    val uv: RawChannels?,
    val visible: RawChannels?,
    val uvError: InvalidSensorReading? = null,
    val visibleError: InvalidSensorReading? = null
) {
    val complete: DoubleArray? get() = if (uv != null && visible != null) uv.wm2 + visible.wm2 else null
    fun referenceValues(): DoubleArray = (uv?.wm2 ?: DoubleArray(3) { Double.NaN }) +
        (visible?.wm2 ?: DoubleArray(8) { Double.NaN })
}

/** Pending UV integration overlaps VIS/IR. Never run I²C transactions in parallel. */
internal suspend fun readPhoneOpticalFrame(uv: As7331, visible: As7343,
    generation: Long, revision: Long, lockUv: Boolean, lockVisible: Boolean,
    now: () -> Long): PhoneOpticalFrame {
    uv.startCapture()
    var visibleError: InvalidSensorReading? = null
    val v = try { retryOpticalSample { visible.capture(lockVisible) } }
        catch (error: InvalidSensorReading) { if (!error.retryable) throw error; visibleError = error; null }
    var pending = true
    var uvError: InvalidSensorReading? = null
    val u = try { retryOpticalSample {
        if (pending) { pending = false; uv.finishCapture(lockUv) } else uv.capture(lockUv)
    } } catch (error: InvalidSensorReading) { if (!error.retryable) throw error; uvError = error; null }
    return PhoneOpticalFrame(generation, revision, now(), u, v, uvError, visibleError)
}

/** USB round trips can exceed the chip's integration time. Bound freshness against the
 * observed capture duration as well as configured spacing, never against wall-clock time. */
internal fun opticalFreshnessMs(spacingMs: Long, captureDurationMs: Long): Long =
    maxOf(500L, 3 * maxOf(spacingMs, captureDurationMs.coerceIn(1, 10_000)))

/** Includes bounded gain adaptation/retries over USB, not only one integration.
 * A new attempt cannot renew an already expired or failed reading. */
internal const val OPTICAL_CAPTURE_GRACE_MS = 10_000L

internal fun opticalCaptureProtectsGap(lastAtMs: Long, freshnessMs: Long,
    startedAtMs: Long, finishedAtMs: Long): Boolean =
    startedAtMs >= lastAtMs && startedAtMs - lastAtMs <= freshnessMs &&
        finishedAtMs >= startedAtMs && finishedAtMs - startedAtMs <= OPTICAL_CAPTURE_GRACE_MS

/** A failed conversion does not destroy valid samples. Long gaps/config changes do. */
internal class PhoneSamplingWindow {
    private val frames = ArrayDeque<DoubleArray>()
    private var version = Long.MIN_VALUE
    private var lastAt = Long.MIN_VALUE
    private var lastFreshnessMs = 500L
    fun reset() { frames.clear(); lastAt = Long.MIN_VALUE }
    fun expire(nowMs: Long) {
        if (lastAt != Long.MIN_VALUE && nowMs - lastAt > lastFreshnessMs) reset()
    }
    fun add(values: DoubleArray, revision: Long, samples: Int, nowMs: Long, freshnessMs: Long,
        captureStartedAtMs: Long = nowMs): List<DoubleArray>? {
        require(samples in 1..100 && values.size == 11 && values.all { it.isFinite() && it >= 0 })
        if (version != revision || lastAt != Long.MIN_VALUE && nowMs - lastAt > lastFreshnessMs &&
            !opticalCaptureProtectsGap(lastAt, lastFreshnessMs, captureStartedAtMs, nowMs)) reset()
        version = revision
        lastAt = nowMs
        lastFreshnessMs = freshnessMs
        frames.addLast(values.copyOf())
        while (frames.size > samples) frames.removeFirst()
        return if (frames.size == samples) frames.toList() else null
    }
}
