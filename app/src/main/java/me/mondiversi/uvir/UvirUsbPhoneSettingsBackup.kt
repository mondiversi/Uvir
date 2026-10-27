package me.mondiversi.uvir

import android.content.Context
import me.mondiversi.uvir.usb.PhoneCalibration
import org.json.JSONObject

private const val USB_PHONE_BACKUP = "usb_phone_v1"
internal const val USB_PHONE_PREFS = "uvir_usb_phone_v1"
internal const val USB_PHONE_CALIBRATION = "calibration"

/** Include durable USB settings in the existing encrypted APP backup, never jobs or mock state. */
internal fun addUsbPhoneSettingsBackup(context: Context, payload: JSONObject) {
    if (!BuildConfig.USB_ONLY) return
    val source = context.getSharedPreferences(USB_PHONE_PREFS, Context.MODE_PRIVATE)
    val config = JSONObject().put("version", 2)
    source.all.forEach { (key, value) ->
        if (key in setOf("samples", "spacing", "trim") || stableUsbCalibrationKey(key)) config.put(key, value)
    }
    config.put(USB_PHONE_CALIBRATION, source.getString(USB_PHONE_CALIBRATION, null)
        ?: encodeUsbPhoneCalibration(PhoneCalibration()))
    // A string stays a harmless opaque app preference if opened by the older format-v4 reader.
    payload.put(USB_PHONE_BACKUP, config.toString())
}

internal fun restoreUsbPhoneSettingsBackup(context: Context, payload: JSONObject) {
    if (!BuildConfig.USB_ONLY || !payload.has(USB_PHONE_BACKUP)) return
    val config = JSONObject(payload.getString(USB_PHONE_BACKUP))
    require(config.getInt("version") in 1..2)
    val samples = config.optInt("samples", 5).also { require(it in 1..100) }
    val spacing = config.optLong("spacing", 100).also { require(it in 0..60000) }
    val trim = config.optBoolean("trim", false)
    val profiles = config.keys().asSequence().filter(::stableUsbCalibrationKey).associateWith { key ->
        config.getString(key).also { decodeUsbPhoneCalibration(it) }
    }
    val target = context.getSharedPreferences(USB_PHONE_PREFS, Context.MODE_PRIVATE)
    // Old preview backups have per-adapter profiles only. Preserve them without
    // silently turning any of them into the new phone-owned calibration.
    val calibration = if (config.has(USB_PHONE_CALIBRATION)) config.getString(USB_PHONE_CALIBRATION)
        .also { decodeUsbPhoneCalibration(it) } else target.getString(USB_PHONE_CALIBRATION, null)
    check(target.edit().clear().putInt("samples", samples).putLong("spacing", spacing).putBoolean("trim", trim)
        .apply {
            profiles.forEach { (key, value) -> putString(key, value) }
            calibration?.let { putString(USB_PHONE_CALIBRATION, it) }
        }.commit())
}

internal fun isUsbPhoneBackupPreference(key: String): Boolean = key == USB_PHONE_BACKUP
private fun stableUsbCalibrationKey(key: String): Boolean = key.matches(Regex("calibration:FT232H:[A-Za-z0-9_-]{4,64}"))

internal fun decodeUsbPhoneCalibration(encoded: String): PhoneCalibration {
    val json = JSONObject(encoded)
    fun list(key: String) = json.getJSONArray(key).let { array -> List(array.length()) { array.getDouble(it) } }
    return PhoneCalibration(list("factors"), list("offsets"), json.getDouble("master"), list("window"))
}

internal fun encodeUsbPhoneCalibration(value: PhoneCalibration): String = JSONObject()
    .put("factors", org.json.JSONArray(value.factors)).put("offsets", org.json.JSONArray(value.offsets))
    .put("master", value.windowMaster).put("window", org.json.JSONArray(value.window)).toString()
