package me.mondiversi.uvir

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

internal const val UVIR_CANONICAL_IRRADIANCE_UNIT = "W/m2"
internal const val UVIR_LEGACY_IRRADIANCE_UNIT = "uW/cm2"
internal const val KEY_IRRADIANCE_STORAGE_UNIT = "irradiance_storage_unit"
internal const val DEFAULT_IRRADIANCE_THRESHOLD_W_M2 = .01f

/** Legacy v1 wire numbers are deliberately isolated here, never used as stored units. */
internal fun legacyIrradianceToWm2(value: Double): Double = value / 100.0
internal fun legacyIrradianceToWm2(value: Float): Float = value / 100f
internal fun wm2ToLegacyIrradiance(value: Float): Float = value * 100f
internal fun irradianceLegacyCommandNumber(value: Float): String =
    java.math.BigDecimal(value.toString()).movePointRight(2).stripTrailingZeros().toPlainString()
internal fun irradianceUnitScaleToWm2(unit: String): Double = when (unit) {
    UVIR_CANONICAL_IRRADIANCE_UNIT -> 1.0
    UVIR_LEGACY_IRRADIANCE_UNIT -> .01
    else -> error("Unsupported irradiance unit: $unit")
}

/** Preserve unknown/malformed historical entries verbatim rather than dropping user data. */
internal fun scaleUvirAlertDetails(details: String, scale: Double): String =
    if (scale == 1.0) details else details.split(';').joinToString(";") { entry ->
        val fields = entry.split('|')
        val value = fields.getOrNull(1)?.toDoubleOrNull()
        val threshold = fields.getOrNull(3)?.toDoubleOrNull()
        if (fields.size != 4 || value == null || threshold == null || !value.isFinite() || !threshold.isFinite()) entry
        else listOf(fields[0], (value * scale).toString(), fields[2], (threshold * scale).toString()).joinToString("|")
    }

internal fun scaleUvirConditionPlan(text: String, scale: Double): String {
    if (text.isBlank() || scale == 1.0) return text
    val parts = text.split('|')
    require(parts.size == 3) { "Invalid conditional acquisition plan" }
    return parts.take(2).joinToString("|") + "|" + parts[2].split(';').joinToString(";") { rule ->
        val fields = rule.split(',')
        require(fields.size == 3)
        val threshold = fields[2].toDouble()
        require(threshold.isFinite())
        "${fields[0]},${fields[1]},${threshold * scale}"
    }
}

internal fun scaleUvirRuleArray(text: String, scale: Double): String = JSONArray(text).apply {
    for (i in 0 until length()) {
        val rule = getJSONObject(i)
        val threshold = rule.getDouble("threshold")
        require(threshold.isFinite())
        rule.put("threshold", threshold * scale)
    }
}.toString()

/** Only enumerated irradiance preferences, including inactive sensor profiles. */
internal fun migrateUvirIrradiancePreference(key: String, value: Any): Any {
    val base = if (key.startsWith("selected_sensor_context.")) key.substringAfter('.', "").substringAfter('.') else key
    return when {
        base == KEY_THRESHOLD_ALERT_VALUE ||
            (base.startsWith("threshold_alert_rule_") && base.endsWith("_value")) -> {
            require(value is Number && value.toDouble().isFinite())
            legacyIrradianceToWm2(value.toFloat())
        }
        base == KEY_AUTO_CONDITIONAL_RULES -> scaleUvirRuleArray(value as String, .01)
        base == KEY_AUTO_CONDITIONAL_PLAN -> scaleUvirConditionPlan(value as String, .01)
        else -> value
    }
}

/** One atomic preferences commit. A crash before commit leaves all legacy values intact. */
internal fun migrateUvirIrradiancePreferences(preferences: SharedPreferences) {
    when (preferences.getString(KEY_IRRADIANCE_STORAGE_UNIT, null)) {
        UVIR_CANONICAL_IRRADIANCE_UNIT -> return
        null, UVIR_LEGACY_IRRADIANCE_UNIT -> Unit
        else -> error("Unsupported preferences irradiance unit")
    }
    val changes = preferences.all.mapNotNull { (key, value) ->
        value ?: return@mapNotNull null
        migrateUvirIrradiancePreference(key, value).takeIf { it != value }?.let { key to it }
    }
    val editor = preferences.edit()
    changes.forEach { (key, value) -> when (value) {
        is Float -> editor.putFloat(key, value)
        is String -> editor.putString(key, value)
        else -> error("Unexpected irradiance preference type")
    } }
    check(editor.putString(KEY_IRRADIANCE_STORAGE_UNIT, UVIR_CANONICAL_IRRADIANCE_UNIT).commit()) {
        "Irradiance preferences migration could not be committed"
    }
}

internal fun backupUvirPreferencesBeforeSiMigration(context: Context) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    if (prefs.getString(KEY_IRRADIANCE_STORAGE_UNIT, null) == UVIR_CANONICAL_IRRADIANCE_UNIT) return
    val source = File(context.applicationInfo.dataDir, "shared_prefs/$PREFS_NAME.xml")
    if (source.isFile) {
        val target = File(context.noBackupFilesDir, "before-si/preferences.xml")
        check(target.parentFile!!.isDirectory || target.parentFile!!.mkdirs())
        atomicUvirPrivateBackup(source, target)
    }
}

/** A killed copy must never be mistaken for a completed pre-migration backup. */
@Synchronized
internal fun atomicUvirPrivateBackup(source: File, target: File) {
    if (target.exists()) return
    val temporary = File(target.parentFile, target.name + ".${System.nanoTime()}.tmp")
    try {
        source.inputStream().use { input ->
            java.io.FileOutputStream(temporary).use { output -> input.copyTo(output); output.fd.sync() }
        }
        check(temporary.length() == source.length()) { "Incomplete pre-SI backup" }
        check(temporary.renameTo(target)) { "Unable to finalize pre-SI backup" }
    } finally { temporary.delete() }
}
