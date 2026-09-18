package me.mondiversi.uvir

import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.core.content.edit

internal const val UVIR_IRRADIANCE_UNIT_KEY = "irradiance_unit"

/**
 * Irradiance is always stored and exchanged with the sensor in µW/cm².
 * This preference only controls presentation and exports.
 */
enum class UvirIrradianceUnit(
    val storedValue: String,
    val symbol: String,
    val csvSymbol: String
) {
    W_M2("w_m2", "W/m²", "W_m2"),
    MW_CM2("mw_cm2", "mW/cm²", "mW_cm2"),
    UW_CM2("uw_cm2", "µW/cm²", "uW_cm2");

    fun fromCanonicalUwCm2(value: Double): Double =
        when (this) {
            W_M2 -> value / 100.0
            MW_CM2 -> value / 1_000.0
            UW_CM2 -> value
        }

    fun toCanonicalUwCm2(value: Double): Double =
        when (this) {
            W_M2 -> value * 100.0
            MW_CM2 -> value * 1_000.0
            UW_CM2 -> value
        }

    fun unitLabel(equivalent: Boolean = false): String =
        if (equivalent) "$symbol eq." else symbol

    fun csvUnitLabel(equivalent: Boolean = false): String =
        if (equivalent) "${csvSymbol}_eq" else csvSymbol

    fun displayFractionDigits(canonicalFractionDigits: Int): Int =
        (canonicalFractionDigits +
            when (this) {
                W_M2 -> 2
                MW_CM2 -> 3
                UW_CM2 -> 0
            }).coerceAtMost(9)

    companion object {
        fun fromStoredValue(value: String?): UvirIrradianceUnit =
            entries.firstOrNull { it.storedValue == value } ?: MW_CM2
    }
}

internal val LocalUvirIrradianceUnit =
    staticCompositionLocalOf { UvirIrradianceUnit.MW_CM2 }

internal fun loadUvirIrradianceUnit(context: Context): UvirIrradianceUnit =
    UvirIrradianceUnit.fromStoredValue(
        context.getSharedPreferences(
            UVIR_PREFERENCES_NAME,
            Context.MODE_PRIVATE
        ).getString(
            UVIR_IRRADIANCE_UNIT_KEY,
            UvirIrradianceUnit.MW_CM2.storedValue
        )
    )

internal fun saveUvirIrradianceUnit(
    context: Context,
    unit: UvirIrradianceUnit
) {
    context.getSharedPreferences(
        UVIR_PREFERENCES_NAME,
        Context.MODE_PRIVATE
    ).edit {
        putString(UVIR_IRRADIANCE_UNIT_KEY, unit.storedValue)
    }
}

internal fun formatUvirIrradianceNumber(
    canonicalUwCm2: Double,
    fractionDigits: Int,
    numericFormat: UvirNumericFormat,
    unit: UvirIrradianceUnit,
    grouping: Boolean = true
): String =
    formatUvirNumber(
        value = unit.fromCanonicalUwCm2(canonicalUwCm2),
        fractionDigits = unit.displayFractionDigits(fractionDigits),
        format = numericFormat,
        grouping = grouping
    )

internal fun formatUvirIrradianceExportNumber(
    canonicalUwCm2: Double,
    numericFormat: UvirNumericFormat,
    unit: UvirIrradianceUnit
): String =
    formatUvirExportNumber(
        unit.fromCanonicalUwCm2(canonicalUwCm2),
        numericFormat
    )

internal fun String.withUvirIrradianceUnit(
    unit: UvirIrradianceUnit
): String =
    replace("µW/cm²", unit.symbol)
        .replace("uW/cm2", unit.symbol)
