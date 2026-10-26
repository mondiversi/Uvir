package me.mondiversi.uvir

import org.json.JSONObject

internal const val UVIR_SENSOR_PROTOCOL = "uvir-sensor-v1"
internal const val UVIR_SENSOR_UNIT = "uW/cm2"

/**
 * Single parser shared by USB, Wi-Fi and Bluetooth. Keeping the band mapping
 * here prevents one transport from silently exposing a different spectrum.
 */
internal fun JSONObject.toUvirSensorSampleOrNull(): SensorSample? {
    if (!liveSampleIsFresh()) return null
    val unit = optString("unit")
    if (unit !in setOf(UVIR_LEGACY_IRRADIANCE_UNIT, UVIR_CANONICAL_IRRADIANCE_UNIT)) return null
    val bands = optJSONObject("bands") ?: return null
    return bands.toUvirBandSample(unit).copy(qualityFlags = uvirQualityFlags(), opticalWindow = opticalWindowOrNull())
}

internal fun JSONObject.liveSampleIsFresh(): Boolean = uvirLiveFrameFresh(
    optBoolean("sample_valid", true),
    if (has("sample_age_ms")) optLong("sample_age_ms", -1) else null,
    if (has("sample_max_age_ms")) optLong("sample_max_age_ms", -1) else null)

internal fun uvirValidLiveProtocolFrame(line: String, expectedDeviceId: String): Boolean = runCatching {
    val frame = JSONObject(line)
    frame.optString("protocol") == UVIR_SENSOR_PROTOCOL && frame.optString("type").isNotBlank() &&
        (frame.optString("device_id").isBlank() || frame.optString("device_id").equals(expectedDeviceId, true))
}.getOrDefault(false)

internal fun JSONObject.uvirQualityFlags(): Int {
    var flags = optInt("quality_flags", 0)
    if (optBoolean("saturated", false)) {
        flags = flags or UVIR_QUALITY_VISIBLE_NIR_OUT_OF_RANGE
    }
    if (optBoolean("uv_saturated", false)) {
        flags = flags or UVIR_QUALITY_UV_OUT_OF_RANGE
    }
    return flags
}

internal fun JSONObject.toUvirBandSample(unit: String = UVIR_LEGACY_IRRADIANCE_UNIT): SensorSample =
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
    ).scaledIrradiance(irradianceUnitScaleToWm2(unit))

internal fun JSONObject.sensorIrradianceScaleToWm2(): Double =
    irradianceUnitScaleToWm2(optString(KEY_IRRADIANCE_STORAGE_UNIT,
        optString("unit", UVIR_LEGACY_IRRADIANCE_UNIT)))

internal fun JSONObject.sensorAlertDetailsWm2(): String =
    scaleUvirAlertDetails(optString("details"), sensorIrradianceScaleToWm2())

private fun JSONObject.nonNegativeFiniteDouble(key: String): Double =
    optDouble(key, 0.0)
        .takeIf { it.isFinite() && it >= 0.0 }
        ?: 0.0
