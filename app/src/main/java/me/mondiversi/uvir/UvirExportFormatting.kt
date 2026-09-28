package me.mondiversi.uvir

import android.content.Context
import android.content.res.Configuration
import androidx.core.content.edit
import java.util.Locale

internal const val UVIR_EXPORT_MODE_KEY = "export_mode"
private const val UVIR_EXPORT_CSV_SELECTED_KEY = "export_csv_selected"
private const val UVIR_EXPORT_READABLE_TABLE_SELECTED_KEY =
    "export_readable_table_selected"
private const val UVIR_EXPORT_CHARTS_SELECTED_KEY = "export_charts_selected"
private const val UVIR_EXPORT_CHART_MODE_KEY = "export_chart_mode"
private const val UVIR_EXPORT_READABLE_TABLE_GROUPING_KEY =
    "export_readable_table_grouping"
private const val UVIR_EXPORT_VARIANT_CHART_GROUPING_KEY =
    "export_variant_chart_grouping"
private const val UVIR_EXPORT_ACQUISITION_CHART_SCOPE_KEY =
    "export_acquisition_chart_scope"

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

internal data class UvirDataExportPreferences(
    val chartExportMode: UvirChartExportMode = UvirChartExportMode.COMBINED,
    val readableTableGrouping: UvirReadableTableGrouping =
        UvirReadableTableGrouping.BY_ACQUISITION,
    val variantChartGrouping: UvirVariantChartGrouping =
        UvirVariantChartGrouping.BY_VARIANT
)

private inline fun <reified T : Enum<T>> enumPreferenceOrDefault(
    storedValue: String?,
    defaultValue: T
): T =
    enumValues<T>().firstOrNull { value ->
        value.name == storedValue
    } ?: defaultValue

internal fun loadUvirDataExportPreferences(
    context: Context
): UvirDataExportPreferences {
    val preferences =
        context.getSharedPreferences(
            UVIR_PREFERENCES_NAME,
            Context.MODE_PRIVATE
        )
    return UvirDataExportPreferences(
        chartExportMode =
            enumPreferenceOrDefault(
                preferences.getString(UVIR_EXPORT_CHART_MODE_KEY, null),
                UvirChartExportMode.COMBINED
            ),
        readableTableGrouping =
            enumPreferenceOrDefault(
                preferences.getString(
                    UVIR_EXPORT_READABLE_TABLE_GROUPING_KEY,
                    null
                ),
                UvirReadableTableGrouping.BY_ACQUISITION
            ),
        variantChartGrouping =
            enumPreferenceOrDefault(
                preferences.getString(
                    UVIR_EXPORT_VARIANT_CHART_GROUPING_KEY,
                    null
                ),
                UvirVariantChartGrouping.BY_VARIANT
            )
    )
}

internal fun saveUvirDataExportPreferences(
    context: Context,
    preferences: UvirDataExportPreferences
) {
    context.getSharedPreferences(
        UVIR_PREFERENCES_NAME,
        Context.MODE_PRIVATE
    ).edit {
        // File types belong to the current export only. Remove legacy selections,
        // while retaining the radio choices shared by subsequent exports.
        remove(UVIR_EXPORT_CSV_SELECTED_KEY)
        remove(UVIR_EXPORT_READABLE_TABLE_SELECTED_KEY)
        remove(UVIR_EXPORT_CHARTS_SELECTED_KEY)
        putString(UVIR_EXPORT_CHART_MODE_KEY, preferences.chartExportMode.name)
        putString(
            UVIR_EXPORT_READABLE_TABLE_GROUPING_KEY,
            preferences.readableTableGrouping.name
        )
        putString(
            UVIR_EXPORT_VARIANT_CHART_GROUPING_KEY,
            preferences.variantChartGrouping.name
        )
    }
}

internal fun loadUvirAcquisitionChartShareScope(
    context: Context
): AcquisitionChartShareScope {
    val preferences =
        context.getSharedPreferences(
            UVIR_PREFERENCES_NAME,
            Context.MODE_PRIVATE
        )
    return enumPreferenceOrDefault(
        preferences.getString(UVIR_EXPORT_ACQUISITION_CHART_SCOPE_KEY, null),
        AcquisitionChartShareScope.CURRENT
    )
}

internal fun saveUvirAcquisitionChartShareScope(
    context: Context,
    scope: AcquisitionChartShareScope
) {
    context.getSharedPreferences(
        UVIR_PREFERENCES_NAME,
        Context.MODE_PRIVATE
    ).edit {
        putString(UVIR_EXPORT_ACQUISITION_CHART_SCOPE_KEY, scope.name)
    }
}

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
    uvirExportFormatting(
        context = context,
        mode = loadUvirExportMode(context)
    )

internal fun uvirExportFormatting(
    context: Context,
    mode: UvirExportMode
): UvirExportFormatting =
    when (mode) {
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
