package me.mondiversi.uvir

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.core.content.edit
import java.math.BigDecimal
import java.math.RoundingMode

internal const val UVIR_LIVE_IRRADIANCE_MAX_DECIMALS = 6

internal fun defaultUvirLiveIrradianceDecimals(unit: UvirIrradianceUnit): Int = when (unit) {
    UvirIrradianceUnit.W_M2 -> 2
    UvirIrradianceUnit.MW_CM2 -> 3
    UvirIrradianceUnit.UW_CM2 -> 1
}

internal fun uvirLiveIrradianceDecimalsKey(unit: UvirIrradianceUnit): String =
    "live_irradiance_decimals_${unit.storedValue}"

internal fun loadUvirLiveIrradianceDecimals(context: Context, unit: UvirIrradianceUnit): Int =
    context.getSharedPreferences(UVIR_PREFERENCES_NAME, Context.MODE_PRIVATE)
        .getInt(uvirLiveIrradianceDecimalsKey(unit), defaultUvirLiveIrradianceDecimals(unit))
        .coerceIn(0, UVIR_LIVE_IRRADIANCE_MAX_DECIMALS)

internal fun saveUvirLiveIrradianceDecimals(context: Context, unit: UvirIrradianceUnit, digits: Int) {
    context.getSharedPreferences(UVIR_PREFERENCES_NAME, Context.MODE_PRIVATE).edit {
        putInt(uvirLiveIrradianceDecimalsKey(unit), digits.coerceIn(0, UVIR_LIVE_IRRADIANCE_MAX_DECIMALS))
    }
}

// Legacy preference names are retained; the choice now applies to live and saved displays.
internal val LocalUvirLiveIrradianceDecimals = staticCompositionLocalOf<Int?> { null }

@Composable
internal fun rememberUvirIrradianceDisplayDecimals(context: Context): Map<UvirIrradianceUnit, Int> {
    val preferences = remember(context) { context.getSharedPreferences(UVIR_PREFERENCES_NAME, Context.MODE_PRIVATE) }
    val values = remember(context) {
        mutableStateMapOf(*UvirIrradianceUnit.entries.map { it to loadUvirLiveIrradianceDecimals(context, it) }.toTypedArray())
    }
    DisposableEffect(context, preferences) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == null || UvirIrradianceUnit.entries.any { uvirLiveIrradianceDecimalsKey(it) == key }) {
                UvirIrradianceUnit.entries.forEach { values[it] = loadUvirLiveIrradianceDecimals(context, it) }
            }
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        onDispose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return values
}

internal fun resolveUvirLiveIrradianceDecimals(digits: Int?, unit: UvirIrradianceUnit): Int =
    (digits ?: defaultUvirLiveIrradianceDecimals(unit)).coerceIn(0, UVIR_LIVE_IRRADIANCE_MAX_DECIMALS)

@Composable
internal fun currentUvirLiveIrradianceDecimals(): Int = resolveUvirLiveIrradianceDecimals(
    LocalUvirLiveIrradianceDecimals.current,
    LocalUvirIrradianceUnit.current
)

internal fun formatUvirLiveIrradianceNumber(
    canonicalWm2: Double,
    digits: Int,
    numericFormat: UvirNumericFormat,
    unit: UvirIrradianceUnit
): String = formatUvirLiveDisplayNumber(unit.fromCanonicalWm2(canonicalWm2), digits, numericFormat)

internal fun formatUvirLiveDisplayNumber(
    value: Double,
    digits: Int,
    numericFormat: UvirNumericFormat,
    grouping: Boolean = true
): String {
    val fixedDigits = digits.coerceIn(0, UVIR_LIVE_IRRADIANCE_MAX_DECIMALS)
    val rounded = roundUvirLiveDisplayValue(value, fixedDigits)
    return formatUvirNumber(rounded, fixedDigits, numericFormat, grouping = grouping)
}

internal fun roundUvirLiveDisplayValue(value: Double, digits: Int): Double =
    if (value.isFinite()) {
        BigDecimal.valueOf(value).setScale(digits.coerceIn(0, UVIR_LIVE_IRRADIANCE_MAX_DECIMALS),
            RoundingMode.HALF_UP).toDouble()
    } else value

@Composable
internal fun displayedUvirIrradianceNumber(canonicalWm2: Double): String {
    val digits = currentUvirLiveIrradianceDecimals()
    val numericFormat = LocalUvirNumericFormat.current
    val unit = LocalUvirIrradianceUnit.current
    return formatUvirLiveIrradianceNumber(canonicalWm2, digits, numericFormat, unit)
}

@Composable
internal fun mainViewIrradianceNumber(canonicalWm2: Double): String =
    displayedUvirIrradianceNumber(canonicalWm2)
