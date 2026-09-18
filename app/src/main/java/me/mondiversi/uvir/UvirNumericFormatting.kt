package me.mondiversi.uvir

import android.content.Context
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

internal const val UVIR_PREFERENCES_NAME = "uvir_preferences"
internal const val UVIR_NUMERIC_FORMAT_KEY = "numeric_format"
internal const val UVIR_NUMERIC_FORMAT_EXPANDED_KEY =
    "settings_numeric_format_expanded"

enum class UvirNumericFormat(
    val storedValue: String
) {
    SYSTEM("system"),
    INTERNATIONAL("international"),
    EUROPEAN("european"),
    AMERICAN("american");

    companion object {
        fun fromStoredValue(value: String?): UvirNumericFormat =
            entries.firstOrNull {
                it.storedValue == value
            } ?: SYSTEM
    }
}

internal val LocalUvirNumericFormat =
    staticCompositionLocalOf {
        UvirNumericFormat.SYSTEM
    }

internal fun loadUvirNumericFormat(
    context: Context
): UvirNumericFormat =
    UvirNumericFormat.fromStoredValue(
        context.getSharedPreferences(
            UVIR_PREFERENCES_NAME,
            Context.MODE_PRIVATE
        ).getString(
            UVIR_NUMERIC_FORMAT_KEY,
            UvirNumericFormat.SYSTEM.storedValue
        )
    )

internal fun saveUvirNumericFormat(
    context: Context,
    format: UvirNumericFormat
) {
    context.getSharedPreferences(
        UVIR_PREFERENCES_NAME,
        Context.MODE_PRIVATE
    ).edit {
        putString(
            UVIR_NUMERIC_FORMAT_KEY,
            format.storedValue
        )
    }
}

internal fun formatUvirNumber(
    value: Double,
    fractionDigits: Int,
    format: UvirNumericFormat,
    grouping: Boolean = true
): String {
    if (!value.isFinite()) {
        return value.toString()
    }

    val symbols = numericFormatSymbols(format)
    val pattern =
        buildString {
            append(if (grouping) "#,##0" else "0")
            if (fractionDigits > 0) {
                append('.')
                repeat(fractionDigits) {
                    append('0')
                }
            }
        }

    return DecimalFormat(
        pattern,
        symbols
    ).apply {
        isGroupingUsed = grouping
        minimumFractionDigits = fractionDigits
        maximumFractionDigits = fractionDigits
        roundingMode = RoundingMode.HALF_UP
    }.format(value)
}

internal fun formatUvirExportNumber(
    value: Double,
    format: UvirNumericFormat
): String {
    if (!value.isFinite()) {
        return value.toString()
    }

    return DecimalFormat(
        "0.#########",
        numericFormatSymbols(format)
    ).apply {
        isGroupingUsed = false
        roundingMode = RoundingMode.HALF_UP
    }.format(value)
}

private fun numericFormatSymbols(
    format: UvirNumericFormat
): DecimalFormatSymbols =
    when (format) {
        UvirNumericFormat.SYSTEM ->
            DecimalFormatSymbols.getInstance(Locale.getDefault())

        UvirNumericFormat.INTERNATIONAL ->
            DecimalFormatSymbols.getInstance(Locale.US).apply {
                groupingSeparator = '\u202F'
            }

        UvirNumericFormat.EUROPEAN ->
            DecimalFormatSymbols.getInstance(Locale.GERMANY)

        UvirNumericFormat.AMERICAN ->
            DecimalFormatSymbols.getInstance(Locale.US)
    }

@Composable
internal fun NumericFormatOptionRow(
    selected: Boolean,
    label: String,
    example: String,
    primaryText: Color,
    secondaryText: Color,
    onClick: () -> Unit
) {
    SettingsRadioChoiceRow(
        selected = selected,
        primaryText = primaryText,
        secondaryText = secondaryText,
        onClick = onClick
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            color = primaryText,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )

        Text(
            text = example,
            color = secondaryText,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
