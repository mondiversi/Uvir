package me.mondiversi.uvir

import kotlin.math.abs
import kotlin.math.sqrt

internal const val OPTICAL_WINDOW_MEASUREMENT_MS = 30_000L
internal const val OPTICAL_WINDOW_MIN_SAMPLES = 20
internal const val OPTICAL_WINDOW_MIN_SIGNAL_W_M2 = 0.0001
private const val MAX_RELATIVE_VARIATION = 0.05

/** Identity and configuration must stay fixed throughout both reference intervals. */
internal data class OpticalWindowMeasurementContext(
    val deviceId: String,
    val mode: SensorConnectionMode,
    val generation: Long,
    val firmware: String,
    val settingsRevision: Long?,
    val visibleFactor: Double?,
    val uvFactor: Double?,
    val offsets: CalibrationOffsets?,
    val channelFactors: CalibrationChannelFactors?,
    val window: OpticalWindowSettings,
    val samplingCount: Int?,
    val samplingSpacing: Long?,
    val samplingTrim: Boolean?,
    val visibleAvailable: Boolean,
    val uvAvailable: Boolean
)

internal enum class OpticalWindowChannelIssue {
    UNAVAILABLE, INSUFFICIENT, UNSTABLE, SATURATED, BRIGHTER, OUT_OF_RANGE
}

internal data class OpticalWindowChannelReading(
    val mean: Double,
    val deviation: Double,
    val samples: Int,
    val issue: OpticalWindowChannelIssue? = null
)

internal data class OpticalWindowReading(
    val context: OpticalWindowMeasurementContext,
    val channels: List<OpticalWindowChannelReading>,
    val isCurrent: () -> Boolean = { true }
) {
    init { require(channels.size == 11) }
}

internal data class OpticalWindowChannelProposal(
    val factor: Float? = null,
    val issue: OpticalWindowChannelIssue? = null
)

internal data class OpticalWindowProposal(
    val reference: OpticalWindowReading,
    val filtered: OpticalWindowReading,
    val channels: List<OpticalWindowChannelProposal>
) {
    val validChannels: Int get() = channels.count { it.factor != null }

    fun canApplyTo(window: OpticalWindowSettings): Boolean =
        validChannels > 0 && reference.isCurrent() && filtered.isCurrent() &&
            window.forWindowEditor() == reference.context.window.forWindowEditor()

    /** Replace only usable channel factors; never multiply by old factors or change the master. */
    fun applyTo(window: OpticalWindowSettings): OpticalWindowSettings {
        check(canApplyTo(window)) { "window_measurement_changed" }
        return window.forWindowEditor().copy(factors = channels.mapIndexed { index, proposal ->
            proposal.factor ?: window.forWindowEditor().factors[index]
        })
    }
}

internal fun opticalWindowProposal(reference: OpticalWindowReading, filtered: OpticalWindowReading): OpticalWindowProposal {
    check(reference.context == filtered.context) { "window_measurement_changed" }
    return OpticalWindowProposal(reference, filtered, reference.channels.zip(filtered.channels).map { (before, after) ->
        val issue = before.issue ?: after.issue
        when {
            issue != null -> OpticalWindowChannelProposal(issue = issue)
            !before.mean.isFinite() || !after.mean.isFinite() ||
                before.mean < OPTICAL_WINDOW_MIN_SIGNAL_W_M2 || after.mean < OPTICAL_WINDOW_MIN_SIGNAL_W_M2 ->
                OpticalWindowChannelProposal(issue = OpticalWindowChannelIssue.INSUFFICIENT)
            before.mean / after.mean < 1.0 -> OpticalWindowChannelProposal(issue = OpticalWindowChannelIssue.BRIGHTER)
            before.mean / after.mean > 10.0 -> OpticalWindowChannelProposal(issue = OpticalWindowChannelIssue.OUT_OF_RANGE)
            else -> OpticalWindowChannelProposal((before.mean / after.mean).toFloat())
        }
    })
}

/** Bounded Welford statistics. Already-applied window factors are removed, not saved/reset. */
internal class OpticalWindowMeasurementAccumulator(private val context: OpticalWindowMeasurementContext) {
    private val values = List(11) { mutableListOf<Double>() }
    private val saturated = BooleanArray(11)
    private var previousSequence = Long.MIN_VALUE

    fun add(sequence: Long, sample: SensorSample) {
        if (sequence == previousSequence) return
        check(sequence > previousSequence) { "window_measurement_sequence_changed" }
        previousSequence = sequence
        check(sample.opticalWindow == context.window) { "window_measurement_changed" }
        val bands = listOf(sample.uvc, sample.uvb, sample.uva, sample.violetto, sample.blu,
            sample.verde, sample.giallo, sample.arancione, sample.rosso, sample.f8, sample.nir)
        val factors = if (context.window.enabled) context.window.effectiveFactors else List(11) { 1f }
        bands.forEachIndexed { index, corrected ->
            val available = if (index < 3) context.uvAvailable else context.visibleAvailable
            val flag = if (index < 3) UVIR_QUALITY_UV_OUT_OF_RANGE else UVIR_QUALITY_VISIBLE_NIR_OUT_OF_RANGE
            if (sample.qualityFlags and flag != 0) saturated[index] = true
            if (available && corrected.isFinite() && corrected >= 0 && values[index].size < 2_000) {
                values[index].add(corrected / factors[index])
            }
        }
    }

    fun finish(isCurrent: () -> Boolean = { true }): OpticalWindowReading = OpticalWindowReading(context,
        values.mapIndexed { index, samples ->
            var mean = 0.0
            var m2 = 0.0
            samples.forEachIndexed { i, value ->
                val delta = value - mean
                mean += delta / (i + 1)
                m2 += delta * (value - mean)
            }
            val deviation = if (samples.size > 1) sqrt((m2 / (samples.size - 1)).coerceAtLeast(0.0)) else 0.0
            val half = samples.size / 2
            val drift = if (half > 0) abs(samples.take(half).average() - samples.drop(half).average()) else 0.0
            val available = if (index < 3) context.uvAvailable else context.visibleAvailable
            val issue = when {
                !available -> OpticalWindowChannelIssue.UNAVAILABLE
                saturated[index] -> OpticalWindowChannelIssue.SATURATED
                samples.size < OPTICAL_WINDOW_MIN_SAMPLES || mean < OPTICAL_WINDOW_MIN_SIGNAL_W_M2 ->
                    OpticalWindowChannelIssue.INSUFFICIENT
                deviation / mean > MAX_RELATIVE_VARIATION || drift / mean > MAX_RELATIVE_VARIATION ->
                    OpticalWindowChannelIssue.UNSTABLE
                else -> null
            }
            OpticalWindowChannelReading(mean, deviation, samples.size, issue)
        }, isCurrent)
}
