package me.mondiversi.uvir

import android.content.Context
import android.content.res.Configuration
import androidx.core.content.edit
import java.util.Locale

internal const val UVIR_EXPORT_MODE_KEY = "export_mode"

enum class UvirExportMode(
    val storedValue: String
) {
    SELECTED("selected"),
    INTERNATIONAL("international");

    companion object {
        fun fromStoredValue(value: String?): UvirExportMode =
            entries.firstOrNull {
                it.storedValue == value
            } ?: INTERNATIONAL
    }
}

internal data class UvirExportFormatting(
    val context: Context,
    val language: String,
    val numericFormat: UvirNumericFormat,
    val dateFormat: UvirDateFormat,
    val timeFormat: UvirTimeFormat,
    val irradianceUnit: UvirIrradianceUnit
)

internal fun loadUvirExportMode(
    context: Context
): UvirExportMode =
    UvirExportMode.fromStoredValue(
        context.getSharedPreferences(
            UVIR_PREFERENCES_NAME,
            Context.MODE_PRIVATE
        ).getString(
            UVIR_EXPORT_MODE_KEY,
            UvirExportMode.INTERNATIONAL.storedValue
        )
    )

internal fun saveUvirExportMode(
    context: Context,
    mode: UvirExportMode
) {
    context.getSharedPreferences(
        UVIR_PREFERENCES_NAME,
        Context.MODE_PRIVATE
    ).edit {
        putString(UVIR_EXPORT_MODE_KEY, mode.storedValue)
    }
}

internal fun uvirExportFormatting(
    context: Context
): UvirExportFormatting =
    when (loadUvirExportMode(context)) {
        UvirExportMode.SELECTED ->
            UvirExportFormatting(
                context = context,
                language =
                    context.resources.configuration.locales[0].language,
                numericFormat = loadUvirNumericFormat(context),
                dateFormat = loadUvirDateFormat(context),
                timeFormat =
                    resolveUvirTimeFormat(
                        context,
                        loadUvirTimeFormat(context)
                    ),
                irradianceUnit = loadUvirIrradianceUnit(context)
            )

        UvirExportMode.INTERNATIONAL -> {
            val configuration =
                Configuration(context.resources.configuration).apply {
                    setLocale(Locale.ENGLISH)
                    setLayoutDirection(Locale.ENGLISH)
                }
            UvirExportFormatting(
                context = context.createConfigurationContext(configuration),
                language = DATA_EXPORT_LANGUAGE,
                numericFormat = UvirNumericFormat.INTERNATIONAL,
                dateFormat = UvirDateFormat.INTERNATIONAL,
                timeFormat = UvirTimeFormat.H24,
                irradianceUnit = UvirIrradianceUnit.W_M2
            )
        }
    }
