package me.mondiversi.uvir

import org.json.JSONObject

internal const val UVIR_SENSOR_PROTOCOL = "uvir-sensor-v1"
internal const val UVIR_SENSOR_UNIT = "uW/cm2"

/**
 * Single parser shared by USB, Wi-Fi and Bluetooth. Keeping the band mapping
 * here prevents one transport from silently exposing a different spectrum.
 */
internal fun JSONObject.toUvirSensorSampleOrNull(): SensorSample? {
    if (optString("unit") != UVIR_SENSOR_UNIT) return null
    val bands = optJSONObject("bands") ?: return null
    return bands.toUvirBandSample()
}

internal fun JSONObject.toUvirBandSample(): SensorSample =
    SensorSample(
        uvc = nonNegativeFiniteDouble("uvc"),
        uvb = nonNegativeFiniteDouble("uvb"),
        uva = nonNegativeFiniteDouble("uva"),
        violetto = nonNegativeFiniteDouble("violet"),
        blu = nonNegativeFiniteDouble("blue"),
        verde = nonNegativeFiniteDouble("green"),
        giallo = nonNegativeFiniteDouble("yellow"),
        arancione = nonNegativeFiniteDouble("orange"),
        rosso = nonNegativeFiniteDouble("red"),
        f8 = nonNegativeFiniteDouble("far_red"),
        nir = nonNegativeFiniteDouble("nir")
    )

private fun JSONObject.nonNegativeFiniteDouble(key: String): Double =
    optDouble(key, 0.0)
        .takeIf { it.isFinite() && it >= 0.0 }
        ?: 0.0
