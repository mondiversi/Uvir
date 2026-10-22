package me.mondiversi.uvir

import android.content.res.Resources

/** Nominal UV ranges and AS7343 sensitivity peaks, never invented colour passbands. */
internal object UvirSensorBands {
    const val UVC = "240–280 nm"
    const val UVB = "280–315 nm"
    const val UVA = "315–410 nm"
    const val UV_CHANNELS = "240–410 nm"

    // A slash separates contributing channel peaks, not a continuous interval.
    const val VIOLET_CHANNELS = "405 / 425 nm"
    const val BLUE_CHANNELS = "450 / 475 nm"
    const val GREEN_CHANNEL = "515 nm"
    const val GREEN_YELLOW_CHANNELS = "550 / 555 nm"
    const val ORANGE_CHANNEL = "600 nm"
    const val RED_CHANNEL = "640 nm"

    // Discrete characteristic wavelengths; do not turn them into a continuous
    // infrared range or omit F7, which contributes to the far-red estimate.
    const val INFRARED_CHANNELS = "690 / 745 / 855 nm"
    const val FAR_RED_CHANNELS = "690 / 745 nm"
    const val NIR_CHANNEL = "855 nm"
}

/** Main-view hardware caption only; never a spectral range or a stored-value label. */
internal fun liveSpectralSensorModel(group: SensorGroup, info: UvirSensorRuntimeInfo? = null): String = when (group) {
    SensorGroup.UV -> info?.uvSensorModel.orEmpty().trim().ifBlank { "AS7331" }
    SensorGroup.VISIBLE, SensorGroup.NIR -> info?.visibleSensorModel.orEmpty().trim().ifBlank { "AS7343" }
    SensorGroup.BIOLOGICAL -> ""
}

/** Shared by saved values, threshold editors and human-readable exports. */
internal fun sensorChannelReference(labelResource: Int?, code: String = ""): String? = when (labelResource) {
    R.string.violet -> UvirSensorBands.VIOLET_CHANNELS
    R.string.blue, R.string.threshold_channel_heb -> UvirSensorBands.BLUE_CHANNELS
    R.string.green -> UvirSensorBands.GREEN_CHANNEL
    R.string.yellow -> UvirSensorBands.GREEN_YELLOW_CHANNELS
    R.string.orange -> UvirSensorBands.ORANGE_CHANNEL
    R.string.red -> UvirSensorBands.RED_CHANNEL
    R.string.threshold_channel_uvc -> UvirSensorBands.UVC
    R.string.threshold_channel_uvb -> UvirSensorBands.UVB
    R.string.threshold_channel_uva -> UvirSensorBands.UVA
    R.string.threshold_channel_hev -> null // HEV is an aggregate, like the group totals.
    R.string.session_chart_series_far_red, R.string.threshold_channel_far_red -> UvirSensorBands.FAR_RED_CHANNELS
    R.string.threshold_channel_nir -> UvirSensorBands.NIR_CHANNEL
    else -> when (code) {
        "UVC" -> UvirSensorBands.UVC
        "UVB" -> UvirSensorBands.UVB
        "UVA" -> UvirSensorBands.UVA
        "NIR" -> UvirSensorBands.NIR_CHANNEL
        "HEV" -> null
        "HEB" -> UvirSensorBands.BLUE_CHANNELS
        else -> null // Totals and biological effects have no single channel passband.
    }
}

internal fun sensorChannelLabel(label: String, labelResource: Int?, code: String = ""): String =
    sensorChannelReference(labelResource, code)?.let { "$label · $it" } ?: label

internal fun Resources.sensorChannelLabel(labelResource: Int): String =
    sensorChannelLabel(getString(labelResource), labelResource)
