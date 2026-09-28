package me.mondiversi.uvir

import kotlin.math.abs
import kotlin.math.sqrt

private const val UVIR_UNCERTAINTY_MINIMUM_SAMPLES = 5
private const val UVIR_UNCERTAINTY_WINDOW_SAMPLES = 12

/**
 * Relative short-term dispersion of the latest live readings. This is a
 * stability estimate, not a replacement for the sensor's calibration
 * uncertainty or a certified accuracy specification.
 */
internal fun estimateLiveGroupUncertaintyPercent(
    history: List<LiveSamplePoint>,
    group: SensorGroup
): Double? {
    val samples =
        history
            .takeLast(UVIR_UNCERTAINTY_WINDOW_SAMPLES)
            .map { it.sample }
            .filterNot { it.isOutOfRange(group) }

    if (samples.size < UVIR_UNCERTAINTY_MINIMUM_SAMPLES) return null

    val estimates =
        when (group) {
            SensorGroup.UV ->
                listOf(
                    relativeDispersionPercent(
                        samples.map { it.uvc + it.uvb + it.uva }
                    )
                )

            SensorGroup.VISIBLE ->
                listOf(
                    relativeDispersionPercent(
                        samples.map {
                            it.violetto + it.blu + it.verde +
                                it.giallo + it.arancione + it.rosso
                        }
                    )
                )

            SensorGroup.NIR ->
                listOf(
                    relativeDispersionPercent(
                        samples.map { it.f8 + it.nir }
                    )
                )

            SensorGroup.BIOLOGICAL ->
                listOf(
                    relativeDispersionPercent(
                        samples.map { biologicalEffects(it).dnaUvProxy }
                    ),
                    relativeDispersionPercent(
                        samples.map { biologicalEffects(it).uvaPhotoagingProxy }
                    ),
                    relativeDispersionPercent(
                        samples.map { biologicalEffects(it).hevOxidativeProxy }
                    )
                )
        }

    return estimates.filterNotNull().maxOrNull()
}

private fun relativeDispersionPercent(values: List<Double>): Double? {
    val finiteValues = values.filter(Double::isFinite)
    if (finiteValues.size < UVIR_UNCERTAINTY_MINIMUM_SAMPLES) return null

    val mean = finiteValues.average()
    if (!mean.isFinite() || abs(mean) < 1e-12) return null

    val variance =
        finiteValues.sumOf { value ->
            val difference = value - mean
            difference * difference
        } / (finiteValues.size - 1)

    return (sqrt(variance) / abs(mean) * 100.0)
        .takeIf(Double::isFinite)
        ?.coerceIn(0.0, 99.9)
}
