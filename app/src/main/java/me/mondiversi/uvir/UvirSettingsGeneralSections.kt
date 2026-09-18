package me.mondiversi.uvir

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Calendar
import java.util.Locale

private enum class UvirStandaloneExport {
    DATABASE,
    ERROR_LOG
}

private fun uvirLanguagesInMenuOrder(): List<AppLanguage> =
    listOf(AppLanguage.SYSTEM) +
        AppLanguage.entries
            .filterNot { it == AppLanguage.SYSTEM }
            .sortedBy { it.storedValue }

private fun resolvedUvirAppLanguage(
    selectedLanguage: AppLanguage,
    currentLocale: Locale
): AppLanguage {
    if (selectedLanguage != AppLanguage.SYSTEM) {
        return selectedLanguage
    }
    val currentCode =
        when (currentLocale.language.lowercase(Locale.ROOT)) {
            "iw" -> "he"
            else -> currentLocale.language.lowercase(Locale.ROOT)
        }
    return AppLanguage.entries.firstOrNull {
        it != AppLanguage.SYSTEM && it.storedValue == currentCode
    } ?: AppLanguage.ENGLISH
}

@Composable
private fun uvirLanguageLabel(language: AppLanguage): String =
    stringResource(
        when (language) {
            AppLanguage.SYSTEM -> R.string.language_system
            AppLanguage.ITALIAN -> R.string.language_italian
            AppLanguage.ENGLISH -> R.string.language_english
            AppLanguage.SPANISH -> R.string.language_spanish
            AppLanguage.FRENCH -> R.string.language_french
            AppLanguage.GERMAN -> R.string.language_german
            AppLanguage.GREEK -> R.string.language_greek
            AppLanguage.PORTUGUESE -> R.string.language_portuguese
            AppLanguage.PERSIAN -> R.string.language_persian
            AppLanguage.RUSSIAN -> R.string.language_russian
            AppLanguage.HEBREW -> R.string.language_hebrew
            AppLanguage.HINDI -> R.string.language_hindi
            AppLanguage.ARABIC -> R.string.language_arabic
            AppLanguage.CHINESE -> R.string.language_chinese
            AppLanguage.JAPANESE -> R.string.language_japanese
            AppLanguage.KOREAN -> R.string.language_korean
            AppLanguage.SWAHILI -> R.string.language_swahili
            AppLanguage.TURKISH -> R.string.language_turkish
        }
    )

@Composable
private fun uvirNumericFormatLabel(format: UvirNumericFormat): String =
    stringResource(
        when (format) {
            UvirNumericFormat.SYSTEM -> R.string.numeric_format_system
            UvirNumericFormat.INTERNATIONAL -> R.string.numeric_format_international
            UvirNumericFormat.EUROPEAN -> R.string.numeric_format_european
            UvirNumericFormat.AMERICAN -> R.string.date_format_american
        }
    )

@Composable
private fun uvirDateFormatLabel(format: UvirDateFormat): String =
    stringResource(
        when (format) {
            UvirDateFormat.SYSTEM -> R.string.date_format_system
            UvirDateFormat.INTERNATIONAL -> R.string.date_format_international
            UvirDateFormat.EUROPEAN -> R.string.date_format_european
            UvirDateFormat.AMERICAN -> R.string.date_format_american
        }
    )

@Composable
private fun uvirTimeFormatLabel(format: UvirTimeFormat): String =
    stringResource(
        when (format) {
            UvirTimeFormat.SYSTEM -> R.string.time_format_system
            UvirTimeFormat.H24 -> R.string.time_format_24_hours
            UvirTimeFormat.H12 -> R.string.time_format_12_hours
        }
    )

@Composable
private fun UvirSettingsRadioOption(
    selected: Boolean,
    title: String,
    example: String? = null,
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
            text = title,
            modifier = Modifier.weight(1f),
            color = primaryText,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
        example?.let {
            Text(
                text = it,
                color = secondaryText,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun UvirFormatDescription(
    text: String,
    secondaryText: Color
) {
    Text(
        text = text,
        color = secondaryText,
        fontSize = 12.sp,
        lineHeight = 16.sp
    )
}

@Composable
internal fun UvirSettingsGeneralSections(
    database: UvirDatabaseHelper,
    autoEnabled: Boolean,
    numericFormatValue: String,
    onNumericFormatValueChange: (String) -> Unit,
    dateFormatValue: String,
    onDateFormatValueChange: (String) -> Unit,
    timeFormatValue: String,
    onTimeFormatValueChange: (String) -> Unit,
    exportModeValue: String,
    onExportModeValueChange: (String) -> Unit,
    irradianceUnitValue: String,
    onIrradianceUnitValueChange: (String) -> Unit,
    numericFormatSectionExpanded: Boolean,
    onNumericFormatSectionExpandedChange: (Boolean) -> Unit,
    appLanguage: AppLanguage,
    onAppLanguageChanged: (AppLanguage) -> Unit,
    languageSectionExpanded: Boolean,
    onLanguageSectionExpandedChange: (Boolean) -> Unit,
    countersSectionExpanded: Boolean,
    onCountersSectionExpandedChange: (Boolean) -> Unit,
    onResetAllRequested: () -> Unit,
    onRestoreDefaultsRequested: () -> Unit,
    debugSectionExpanded: Boolean,
    onDebugSectionExpandedChange: (Boolean) -> Unit,
    fakeSensorDataEnabled: Boolean,
    onFakeSensorDataEnabledChange: (Boolean) -> Unit,
    fakeSensorOutOfRangeEnabled: Boolean,
    onFakeSensorOutOfRangeEnabledChange: (Boolean) -> Unit,
    debugPerformanceEnabled: Boolean,
    debugPerformanceSensorConnected: Boolean,
    debugPerformanceFirmwareSupported: Boolean,
    debugPerformanceCompletionToken: Long,
    onStartDebugPerformance:
        suspend (String) -> Boolean,
    onStopDebugPerformance:
        suspend () -> Boolean,
    diagnosticSensorConnected: Boolean,
    diagnosticSensorInfo: UvirSensorRuntimeInfo,
    diagnosticConnectionMode: SensorConnectionMode,
    diagnosticSensorName: String,
    onDiagnosticProbe: suspend (String, SensorConnectionMode) -> UvirDiagnosticProbe,
    cardColor: Color,
    primaryText: Color,
    secondaryText: Color
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    var standaloneExport by remember {
        mutableStateOf<UvirStandaloneExport?>(null)
    }
    val dateExampleTimestamp =
        remember {
            Calendar.getInstance().apply {
                clear()
                set(
                    2026,
                    Calendar.SEPTEMBER,
                    15,
                    18,
                    35,
                    42
                )
            }.timeInMillis
        }

    standaloneExport?.let { export ->
        UvirSaveOrShareDialog(
            cardColor = cardColor,
            primaryText = primaryText,
            secondaryText = secondaryText,
            title =
                stringResource(
                    when (export) {
                        UvirStandaloneExport.DATABASE ->
                            R.string.export_database_dialog_title
                        UvirStandaloneExport.ERROR_LOG ->
                            R.string.export_error_log_dialog_title
                    }
                ),
            description =
                stringResource(
                    when (export) {
                        UvirStandaloneExport.DATABASE ->
                            R.string.export_database_dialog_description
                        UvirStandaloneExport.ERROR_LOG ->
                            R.string.export_error_log_dialog_description
                    }
                ),
            onDismiss = { standaloneExport = null },
            onExport = { destination ->
                runCatching {
                    val exported = when (export) {
                        UvirStandaloneExport.DATABASE -> {
                            shareDatabase(context, database, destination)
                            true
                        }
                        UvirStandaloneExport.ERROR_LOG ->
                            UvirErrorLog.share(
                                context = context,
                                chooserTitle = resources.getString(R.string.debug_error_log),
                                subject = resources.getString(R.string.debug_error_log_subject),
                                destination = destination
                            )
                    }
                    if (!exported) {
                        showUvirBottomMessage(
                            context,
                            resources.getString(R.string.debug_error_log_empty),
                            longDuration = false
                        )
                    }
                }.onFailure { error ->
                    UvirErrorLog.record(context, "export_settings_content", error)
                    showUvirBottomMessage(
                        context,
                        resources.getString(R.string.export_save_failed)
                    )
                }
                standaloneExport = null
            }
        )
    }

    SettingsSection(
        settingsPage = UvirSettingsPage.LANGUAGE,
        title = stringResource(R.string.settings_section_language),
        titleIcon = ConnectivityIconType.LANGUAGE,
        expanded = languageSectionExpanded,
        onExpandedChange = { expanded ->
            onLanguageSectionExpandedChange(expanded)
            saveSettingsSectionExpanded(
                context,
                KEY_SETTINGS_LANGUAGE_EXPANDED,
                expanded
            )
        },
        containerColor = cardColor,
        titleColor = primaryText,
        chevronColor = secondaryText,
        dividerColor = secondaryText.copy(alpha = 0.28f)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(UvirSettingsGroupGap)
        ) {
            SettingsPageDescription(
                text = stringResource(R.string.language_description),
                color = secondaryText
            )
            Column(
                verticalArrangement = Arrangement.spacedBy(UvirSettingsChoiceSpacing)
            ) {
                val systemLanguageCode =
                    Locale.getDefault().language.uppercase(Locale.ROOT)
                uvirLanguagesInMenuOrder().forEach { language ->
                    UvirSettingsRadioOption(
                        selected = appLanguage == language,
                        title = uvirLanguageLabel(language),
                        example =
                            if (language == AppLanguage.SYSTEM) {
                                systemLanguageCode
                            } else {
                                language.storedValue.uppercase(Locale.ROOT)
                            },
                        primaryText = primaryText,
                        secondaryText = secondaryText,
                        onClick = {
                            if (appLanguage != language) {
                                onAppLanguageChanged(language)
                            }
                        }
                    )
                }
            }
        }
    }

    SettingsSection(
        settingsPage = UvirSettingsPage.LANGUAGE_AND_FORMATS,
        title = stringResource(R.string.settings_section_language_formats),
        titleIcon = ConnectivityIconType.NUMERIC_FORMAT,
        expanded = numericFormatSectionExpanded,
        onExpandedChange = { expanded ->
            onNumericFormatSectionExpandedChange(expanded)
            saveSettingsSectionExpanded(
                context,
                UVIR_NUMERIC_FORMAT_EXPANDED_KEY,
                expanded
            )
        },
        containerColor = cardColor,
        titleColor = primaryText,
        chevronColor = secondaryText,
        dividerColor = secondaryText.copy(alpha = 0.28f)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(UvirSettingsGroupGap)
        ) {
            SettingsPageDescription(
                text = stringResource(R.string.language_formats_description),
                color = secondaryText
            )

            Column(
                verticalArrangement = Arrangement.spacedBy(UvirSettingsChoiceSpacing)
            ) {
                Text(
                    text = stringResource(R.string.irradiance_view),
                    color = primaryText,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                UvirFormatDescription(
                    text = stringResource(R.string.irradiance_measurement_description),
                    secondaryText = secondaryText
                )
                Spacer(Modifier.height(4.dp))
                UvirIrradianceUnit.entries.forEach { unit ->
                    UvirSettingsRadioOption(
                        selected = irradianceUnitValue == unit.storedValue,
                        title = unit.symbol,
                        primaryText = primaryText,
                        secondaryText = secondaryText,
                        onClick = {
                            if (irradianceUnitValue != unit.storedValue) {
                                onIrradianceUnitValueChange(unit.storedValue)
                            }
                        }
                    )
                }
            }

            SettingsGroupDivider(secondaryText)

            Column(
                verticalArrangement = Arrangement.spacedBy(UvirSettingsChoiceSpacing)
            ) {
                Text(
                    text = stringResource(R.string.format_numbers_heading),
                    color = primaryText,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                UvirFormatDescription(
                    text = stringResource(R.string.numeric_format_description),
                    secondaryText = secondaryText
                )
                Spacer(Modifier.height(4.dp))
                UvirNumericFormat.entries.forEach { format ->
                    UvirSettingsRadioOption(
                        selected = numericFormatValue == format.storedValue,
                        title = uvirNumericFormatLabel(format),
                        example =
                            formatUvirNumber(1000.23, 2, format),
                        primaryText = primaryText,
                        secondaryText = secondaryText,
                        onClick = {
                            if (numericFormatValue != format.storedValue) {
                                onNumericFormatValueChange(format.storedValue)
                            }
                        }
                    )
                }
            }

            SettingsGroupDivider(secondaryText)

            Column(
                verticalArrangement = Arrangement.spacedBy(UvirSettingsChoiceSpacing)
            ) {
                Text(
                    text = stringResource(R.string.format_date_time_heading),
                    color = primaryText,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                UvirFormatDescription(
                    text = stringResource(R.string.date_format_description),
                    secondaryText = secondaryText
                )
                Spacer(Modifier.height(4.dp))
                UvirDateFormat.entries.forEach { format ->
                    UvirSettingsRadioOption(
                        selected = dateFormatValue == format.storedValue,
                        title = uvirDateFormatLabel(format),
                        example = formatUvirDateOnly(dateExampleTimestamp, format),
                        primaryText = primaryText,
                        secondaryText = secondaryText,
                        onClick = {
                            if (dateFormatValue != format.storedValue) {
                                onDateFormatValueChange(format.storedValue)
                            }
                        }
                    )
                }
            }

            SettingsGroupDivider(secondaryText)

            Column(
                verticalArrangement = Arrangement.spacedBy(UvirSettingsChoiceSpacing)
            ) {
                Text(
                    text = stringResource(R.string.format_time_heading),
                    color = primaryText,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                UvirFormatDescription(
                    text = stringResource(R.string.time_format_description),
                    secondaryText = secondaryText
                )
                Spacer(Modifier.height(4.dp))
                UvirTimeFormat.entries.forEach { format ->
                    val effectiveFormat =
                        resolveUvirTimeFormat(context, format)
                    UvirSettingsRadioOption(
                        selected = timeFormatValue == format.storedValue,
                        title = uvirTimeFormatLabel(format),
                        example =
                            formatUvirTimeOnly(
                                timestamp = dateExampleTimestamp,
                                format = effectiveFormat
                            ),
                        primaryText = primaryText,
                        secondaryText = secondaryText,
                        onClick = {
                            if (timeFormatValue != format.storedValue) {
                                onTimeFormatValueChange(format.storedValue)
                            }
                        }
                    )
                }
            }

        }
    }

    SettingsSection(
        settingsPage = UvirSettingsPage.SHARING_AND_EXPORT,
        title = stringResource(R.string.settings_section_sharing_export),
        titleIconContent = { tint ->
            UvirMenuIcon(
                type = MenuIconType.EXPORT,
                modifier = Modifier.size(20.dp),
                tint = tint
            )
        },
        containerColor = cardColor,
        titleColor = primaryText,
        chevronColor = secondaryText,
        dividerColor = secondaryText.copy(alpha = 0.28f)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(UvirSettingsGroupGap)
        ) {
            SettingsPageDescription(
                text = stringResource(R.string.export_format_description),
                color = secondaryText
            )
            Column(
                verticalArrangement = Arrangement.spacedBy(UvirSettingsChoiceSpacing)
            ) {
                UvirExportMode.entries.forEach { mode ->
                    UvirSettingsRadioOption(
                        selected = exportModeValue == mode.storedValue,
                        title =
                            stringResource(
                                when (mode) {
                                    UvirExportMode.SELECTED ->
                                        R.string.export_format_selected
                                    UvirExportMode.INTERNATIONAL ->
                                        R.string.export_format_international
                                }
                            ),
                        primaryText = primaryText,
                        secondaryText = secondaryText,
                        onClick = {
                            if (exportModeValue != mode.storedValue) {
                                onExportModeValueChange(mode.storedValue)
                            }
                        }
                    )
                }
            }

            val selectedExportMode =
                UvirExportMode.fromStoredValue(exportModeValue)
            val previewNumericFormat =
                if (selectedExportMode == UvirExportMode.INTERNATIONAL) {
                    UvirNumericFormat.INTERNATIONAL
                } else {
                    UvirNumericFormat.fromStoredValue(numericFormatValue)
                }
            val previewDateFormat =
                if (selectedExportMode == UvirExportMode.INTERNATIONAL) {
                    UvirDateFormat.INTERNATIONAL
                } else {
                    UvirDateFormat.fromStoredValue(dateFormatValue)
                }
            val previewTimeFormat =
                if (selectedExportMode == UvirExportMode.INTERNATIONAL) {
                    UvirTimeFormat.H24
                } else {
                    resolveUvirTimeFormat(
                        context,
                        UvirTimeFormat.fromStoredValue(timeFormatValue)
                    )
                }
            val previewIrradianceUnit =
                if (selectedExportMode == UvirExportMode.INTERNATIONAL) {
                    UvirIrradianceUnit.W_M2
                } else {
                    UvirIrradianceUnit.fromStoredValue(irradianceUnitValue)
                }
            val previewContext =
                if (selectedExportMode == UvirExportMode.INTERNATIONAL) {
                    val configuration =
                        android.content.res.Configuration(
                            androidx.compose.ui.platform.LocalConfiguration.current
                        ).apply {
                            setLocale(Locale.ENGLISH)
                            setLayoutDirection(Locale.ENGLISH)
                        }
                    remember(context, selectedExportMode) {
                        context.createConfigurationContext(configuration)
                    }
                } else {
                    context
                }
            val previewDateTime =
                "${formatUvirDateOnly(dateExampleTimestamp, previewDateFormat)} · " +
                    formatUvirTimeOnly(dateExampleTimestamp, previewTimeFormat)
            val previewTotal =
                formatUvirIrradianceNumber(
                    canonicalUwCm2 = 123456.78,
                    fractionDigits = 3,
                    numericFormat = previewNumericFormat,
                    unit = previewIrradianceUnit
                )
            val previewUva =
                formatUvirIrradianceNumber(
                    canonicalUwCm2 = 89214.32,
                    fractionDigits = 3,
                    numericFormat = previewNumericFormat,
                    unit = previewIrradianceUnit
                )

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                color = secondaryText.copy(alpha = 0.08f),
                contentColor = primaryText
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text =
                            "${previewContext.getString(R.string.share_acquisition_label)} #12",
                        color = primaryText,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = previewDateTime,
                        color = secondaryText,
                        fontSize = 11.sp,
                        lineHeight = 14.sp
                    )
                    HorizontalDivider(color = secondaryText.copy(alpha = 0.20f))
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = previewContext.getString(R.string.uv_radiation),
                            modifier = Modifier.weight(1f),
                            color = primaryText,
                            fontSize = 12.sp
                        )
                        Text(
                            text = "$previewTotal ${previewIrradianceUnit.symbol}",
                            color = primaryText,
                            fontSize = 12.sp
                        )
                    }
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "UVA",
                            modifier = Modifier.weight(1f),
                            color = primaryText,
                            fontSize = 12.sp
                        )
                        Text(
                            text = "$previewUva ${previewIrradianceUnit.symbol}",
                            color = primaryText,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }

    SettingsSection(
        settingsPage = UvirSettingsPage.DATA_RESTORE,
        title =
            stringResource(
                R.string.data_and_restore_title
            ),
        titleIcon =
            ConnectivityIconType.COUNTERS,
        expanded =
            countersSectionExpanded,
        onExpandedChange = { expanded ->
            onCountersSectionExpandedChange(expanded)
            saveSettingsSectionExpanded(
                context,
                KEY_SETTINGS_COUNTERS_EXPANDED,
                expanded
            )
        },
        containerColor = cardColor,
        titleColor = primaryText,
        chevronColor = secondaryText,
        dividerColor =
            secondaryText.copy(
                alpha = 0.28f
            )
    ) {
        SettingsPageDescription(
            text = stringResource(R.string.data_and_restore_description_split),
            color = secondaryText
        )

        Column(
            verticalArrangement =
                Arrangement.spacedBy(UvirSettingsRelatedGap)
        ) {
            OutlinedButton(
                onClick = {
                    standaloneExport = UvirStandaloneExport.DATABASE
                },
                modifier = Modifier.fillMaxWidth(),
                colors =
                    ButtonDefaults.outlinedButtonColors(
                        contentColor = primaryText
                    ),
                border =
                    BorderStroke(
                        1.dp,
                        secondaryText.copy(alpha = 0.72f)
                    )
            ) {
                UvirLabeledButtonContent(
                    text = stringResource(R.string.export_database)
                ) {
                    UvirMenuIcon(
                        type = MenuIconType.EXPORT,
                        modifier = Modifier.size(20.dp),
                        tint = LocalContentColor.current
                    )
                }
            }

            Text(
                text = stringResource(R.string.export_database_description),
                color = secondaryText,
                fontSize = 12.sp
            )
        }


        SettingsGroupDivider(secondaryText)

        UvirDataAndRestoreContent(
            autoEnabled = autoEnabled,
            secondaryText = secondaryText,
            onResetRequested = {
                onResetAllRequested()
            },
            onRestoreDefaultsRequested = {
                onRestoreDefaultsRequested()
            }
        )
    }

    SettingsSection(
        settingsPage = UvirSettingsPage.DEBUG,
        title =
            stringResource(
                R.string.settings_section_debug
            ),
        titleIcon = ConnectivityIconType.DEBUG,
        expanded = debugSectionExpanded,
        onExpandedChange = { expanded ->
            onDebugSectionExpandedChange(expanded)
            saveSettingsSectionExpanded(
                context,
                KEY_SETTINGS_DEBUG_EXPANDED,
                expanded
            )
        },
        containerColor = cardColor,
        titleColor = primaryText,
        chevronColor = secondaryText,
        dividerColor =
            secondaryText.copy(alpha = 0.28f)
    ) {
        SettingsPageDescription(
            text = stringResource(R.string.debug_description),
            color = secondaryText
        )

        Column(
            verticalArrangement =
                Arrangement.spacedBy(UvirSettingsRelatedGap)
        ) {
            OutlinedButton(
                onClick = {
                    standaloneExport = UvirStandaloneExport.ERROR_LOG
                },
                modifier = Modifier.fillMaxWidth(),
                colors =
                    ButtonDefaults.outlinedButtonColors(
                        contentColor = primaryText
                    ),
                border =
                    BorderStroke(
                        1.dp,
                        secondaryText.copy(
                            alpha = 0.72f
                        )
                    )
            ) {
                UvirLabeledButtonContent(
                    text = stringResource(R.string.debug_error_log)
                ) {
                    UvirMenuIcon(
                        type = MenuIconType.EXPORT,
                        modifier = Modifier.size(20.dp),
                        tint = LocalContentColor.current
                    )
                }
            }

            Text(
                text =
                    stringResource(
                        R.string.debug_error_log_description
                    ),
                color = secondaryText,
                fontSize = 11.sp,
                lineHeight = 14.sp
            )
        }


        SettingsGroupDivider(secondaryText)

        UvirSensorDiagnosticsContent(
            sensorConnected = diagnosticSensorConnected,
            sensorInfo = diagnosticSensorInfo,
            connectionMode = diagnosticConnectionMode,
            sensorName = diagnosticSensorName,
            cardColor = cardColor,
            primaryText = primaryText,
            secondaryText = secondaryText,
            onProbe = onDiagnosticProbe
        )

        SettingsGroupDivider(secondaryText)

        SettingsCheckboxWithDescription(
            checked = fakeSensorDataEnabled,
            onCheckedChange =
                { enabled ->
                    onFakeSensorDataEnabledChange(enabled)
                },
            title =
                stringResource(
                    R.string.debug_fake_data
                ),
            description =
                stringResource(
                    R.string.debug_fake_data_description
                ),
            primaryText = primaryText,
            secondaryText = secondaryText
        )

        SettingsCheckboxWithDescription(
            checked = fakeSensorOutOfRangeEnabled,
            onCheckedChange = onFakeSensorOutOfRangeEnabledChange,
            enabled = fakeSensorDataEnabled,
            title = stringResource(R.string.debug_fake_out_of_range),
            description = stringResource(R.string.debug_fake_out_of_range_description),
            primaryText = primaryText,
            secondaryText = secondaryText
        )

        SettingsGroupDivider(secondaryText)

        UvirDebugPerformanceContent(
            context = context,
            sensorConnected = debugPerformanceSensorConnected,
            enabled = debugPerformanceEnabled,
            firmwareSupported = debugPerformanceFirmwareSupported,
            completionToken = debugPerformanceCompletionToken,
            primaryText = primaryText,
            secondaryText = secondaryText,
            onStart = onStartDebugPerformance,
            onStop = onStopDebugPerformance
        )
    }
}
