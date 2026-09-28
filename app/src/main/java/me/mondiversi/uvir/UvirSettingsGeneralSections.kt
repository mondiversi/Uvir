package me.mondiversi.uvir

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
private fun UvirFormatGroupHeader(
    title: String,
    icon: ConnectivityIconType,
    primaryText: Color
) {
    SettingsIslandHeader(title, primaryText, icon)
}

@Composable
internal fun UvirSettingsGeneralSections(
    database: UvirDatabaseHelper,
    autoEnabled: Boolean,
    sensorRestoreEnabled: Boolean,
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
    onSensorRestoreRequested: () -> Unit,
    onSettingsExportRequested: () -> Unit,
    onSettingsImportRequested: () -> Unit,
    onDatabaseImportRequested: () -> Unit,
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
    diagnosticSensorDeviceId: String,
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
    val coroutineScope = rememberCoroutineScope()
    var standaloneExport by remember {
        mutableStateOf<UvirStandaloneExport?>(null)
    }
    var showSimulateEventsConfirmation by remember {
        mutableStateOf(false)
    }
    var simulatingEvents by remember {
        mutableStateOf(false)
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
        if (export == UvirStandaloneExport.DATABASE) {
            UvirEncryptedDatabaseExportDialog(
                cardColor = cardColor,
                primaryText = primaryText,
                secondaryText = secondaryText,
                onDismiss = { standaloneExport = null },
                onExport = { password, destination ->
                    runCatching {
                        shareDatabase(context, database, password, destination)
                    }.onFailure { error ->
                        UvirErrorLog.record(context, "export_database", error)
                        showUvirBottomMessage(
                            context,
                            resources.getString(R.string.export_save_failed)
                        )
                    }
                    standaloneExport = null
                }
            )
        } else UvirSaveOrShareDialog(
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
            fileFormat =
                when (export) {
                    UvirStandaloneExport.DATABASE ->
                        UvirExportFileFormat.DATABASE
                    UvirStandaloneExport.ERROR_LOG ->
                        UvirExportFileFormat.TXT
                },
            onDismiss = { standaloneExport = null },
            onExport = { destination ->
                runCatching {
                    val exported = when (export) {
                        UvirStandaloneExport.DATABASE -> error("Handled above")
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

    if (showSimulateEventsConfirmation) {
        UvirAlertDialog(
            onDismissRequest = {
                if (!simulatingEvents) {
                    showSimulateEventsConfirmation = false
                }
            },
            title = {
                Text(stringResource(R.string.debug_simulate_events_question))
            },
            text = {
                Text(stringResource(R.string.debug_simulate_events_confirmation))
            },
            dismissButton = {
                TextButton(
                    enabled = !simulatingEvents,
                    onClick = {
                        simulatingEvents = true
                        coroutineScope.launch {
                            val result =
                                runCatching {
                                    withContext(Dispatchers.IO) {
                                        database.insertDebugSimulationEvents(
                                            selectedSensorDeviceId = diagnosticSensorDeviceId,
                                            note =
                                                resources.getString(
                                                    R.string.debug_simulation_note
                                                )
                                        )
                                    }
                                }
                            simulatingEvents = false
                            showSimulateEventsConfirmation = false
                            result.onSuccess { inserted ->
                                showUvirBottomMessage(
                                    context,
                                    resources.getString(
                                        R.string.debug_simulate_events_completed,
                                        inserted.acquisitions,
                                        inserted.alerts
                                    ),
                                    longDuration = false
                                )
                            }.onFailure { error ->
                                UvirErrorLog.record(
                                    context,
                                    "simulate_debug_events",
                                    error
                                )
                                showUvirBottomMessage(
                                    context,
                                    resources.getString(R.string.save_error),
                                    longDuration = false
                                )
                            }
                        }
                    }
                ) {
                    Text(stringResource(R.string.confirm))
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !simulatingEvents,
                    onClick = {
                        showSimulateEventsConfirmation = false
                    },
                    colors =
                        ButtonDefaults.textButtonColors(
                            contentColor = UvirDestructiveActionColor
                        )
                ) {
                    Text(stringResource(R.string.cancel))
                }
            },
            containerColor = cardColor,
            titleContentColor = primaryText,
            textContentColor = secondaryText
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
        dividerColor = secondaryText.copy(alpha = 0.28f),
        contentSpacing = UvirIslandSpacing,
        wrapDetailContent = false
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(UvirIslandSpacing)
        ) {
            SettingsIsland(
                containerColor = cardColor,
                contentColor = primaryText
            ) {
                UvirFormatGroupHeader(
                    title = stringResource(R.string.irradiance_view),
                    icon = ConnectivityIconType.IRRADIANCE,
                    primaryText = primaryText
                )
                Column(verticalArrangement = Arrangement.spacedBy(UvirSettingsChoiceSpacing)) {
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
            }

            SettingsIsland(
                containerColor = cardColor,
                contentColor = primaryText
            ) {
                UvirFormatGroupHeader(
                    title = stringResource(R.string.format_numbers_heading),
                    icon = ConnectivityIconType.NUMERIC_FORMAT,
                    primaryText = primaryText
                )
                Column(verticalArrangement = Arrangement.spacedBy(UvirSettingsChoiceSpacing)) {
                    UvirFormatDescription(
                        text = stringResource(R.string.numeric_format_description),
                        secondaryText = secondaryText
                    )
                    Spacer(Modifier.height(4.dp))
                    UvirNumericFormat.entries.forEach { format ->
                        UvirSettingsRadioOption(
                            selected = numericFormatValue == format.storedValue,
                            title = uvirNumericFormatLabel(format),
                            example = formatUvirNumber(1000.23, 2, format),
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
            }

            SettingsIsland(
                containerColor = cardColor,
                contentColor = primaryText
            ) {
                UvirFormatGroupHeader(
                    title = stringResource(R.string.format_date_time_heading),
                    icon = ConnectivityIconType.DATE,
                    primaryText = primaryText
                )
                Column(verticalArrangement = Arrangement.spacedBy(UvirSettingsChoiceSpacing)) {
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
            }

            SettingsIsland(
                containerColor = cardColor,
                contentColor = primaryText
            ) {
                UvirFormatGroupHeader(
                    title = stringResource(R.string.format_time_heading),
                    icon = ConnectivityIconType.TIME,
                    primaryText = primaryText
                )
                Column(verticalArrangement = Arrangement.spacedBy(UvirSettingsChoiceSpacing)) {
                    UvirFormatDescription(
                        text = stringResource(R.string.time_format_description),
                        secondaryText = secondaryText
                    )
                    Spacer(Modifier.height(4.dp))
                    UvirTimeFormat.entries.forEach { format ->
                        val effectiveFormat = resolveUvirTimeFormat(context, format)
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
    }

    val settingsNavigation = LocalUvirSettingsNavigation.current
    if (settingsNavigation != null && settingsNavigation.selectedPage == null) {
        HorizontalDivider(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 3.dp),
            color = secondaryText.copy(alpha = 0.24f)
        )
    }

    SettingsSection(
        settingsPage = UvirSettingsPage.DATA_RESTORE,
        title =
            stringResource(
                R.string.data_and_restore_title
            ),
        titleIcon =
            ConnectivityIconType.DATA,
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
            ),
        contentSpacing = UvirIslandSpacing,
        wrapDetailContent = false
    ) {
        SettingsIsland(containerColor = cardColor, contentColor = primaryText) {
            val exportDescription = stringResource(R.string.export_settings_title)
            val importDescription = stringResource(R.string.import_settings)
            UvirFormatGroupHeader(
                title = stringResource(R.string.data_management_settings_group),
                icon = ConnectivityIconType.GENERAL,
                primaryText = primaryText
            )
            SettingsPageDescription(
                text = stringResource(R.string.settings_transfer_description),
                color = secondaryText
            )
            Row(horizontalArrangement = Arrangement.spacedBy(UvirActionButtonGap)) {
                OutlinedButton(
                    onClick = onSettingsExportRequested,
                    modifier = Modifier.weight(1f).semantics {
                        contentDescription = exportDescription
                    },
                    colors = uvirOutlinedActionColors(primaryText),
                    border = uvirOutlinedActionBorder(true, secondaryText)
                ) {
                    UvirLabeledButtonContent(
                        text = stringResource(R.string.export)
                            .uppercase(resources.configuration.locales[0])
                    ) {
                        UvirMenuIcon(
                            type = MenuIconType.EXPORT,
                            modifier = Modifier.size(20.dp),
                            tint = LocalContentColor.current
                        )
                    }
                }

                OutlinedButton(
                    onClick = onSettingsImportRequested,
                    modifier = Modifier.weight(1f).semantics {
                        contentDescription = importDescription
                    },
                    colors = uvirOutlinedActionColors(primaryText),
                    border = uvirOutlinedActionBorder(true, secondaryText)
                ) {
                    UvirLabeledButtonContent(
                        text = stringResource(R.string.data_management_import_button)
                            .uppercase(resources.configuration.locales[0])
                    ) {
                        UvirMenuIcon(
                            type = MenuIconType.IMPORT,
                            modifier = Modifier.size(20.dp),
                            tint = LocalContentColor.current
                        )
                    }
                }
            }
        }

        SettingsIsland(containerColor = cardColor, contentColor = primaryText) {
            val exportDescription = stringResource(R.string.export_database)
            val importDescription = stringResource(R.string.import_database)
            UvirFormatGroupHeader(
                title = stringResource(R.string.data_management_database_group),
                icon = ConnectivityIconType.DATABASE,
                primaryText = primaryText
            )
            SettingsPageDescription(
                text = stringResource(R.string.database_transfer_description),
                color = secondaryText
            )
            Row(horizontalArrangement = Arrangement.spacedBy(UvirActionButtonGap)) {
                OutlinedButton(
                    onClick = {
                        standaloneExport = UvirStandaloneExport.DATABASE
                    },
                    modifier = Modifier.weight(1f).semantics {
                        contentDescription = exportDescription
                    },
                    colors = uvirOutlinedActionColors(primaryText),
                    border = uvirOutlinedActionBorder(true, secondaryText)
                ) {
                    UvirLabeledButtonContent(
                        text = stringResource(R.string.export)
                            .uppercase(resources.configuration.locales[0])
                    ) {
                        UvirMenuIcon(
                            type = MenuIconType.EXPORT,
                            modifier = Modifier.size(20.dp),
                            tint = LocalContentColor.current
                        )
                    }
                }

                OutlinedButton(
                    onClick = onDatabaseImportRequested,
                    modifier = Modifier.weight(1f).semantics {
                        contentDescription = importDescription
                    },
                    colors = uvirOutlinedActionColors(primaryText),
                    border = uvirOutlinedActionBorder(true, secondaryText)
                ) {
                    UvirLabeledButtonContent(
                        text = stringResource(R.string.data_management_import_button)
                            .uppercase(resources.configuration.locales[0])
                    ) {
                        UvirMenuIcon(
                            type = MenuIconType.IMPORT,
                            modifier = Modifier.size(20.dp),
                            tint = LocalContentColor.current
                        )
                    }
                }
            }
        }

        UvirErrorLogSettingsIsland(
            cardColor = cardColor,
            primaryText = primaryText,
            secondaryText = secondaryText,
            onExport = { standaloneExport = UvirStandaloneExport.ERROR_LOG }
        )

        SettingsIsland(containerColor = cardColor, contentColor = primaryText) {
            UvirFormatGroupHeader(
                title = stringResource(R.string.data_management_reset_group),
                icon = ConnectivityIconType.COUNTERS,
                primaryText = primaryText
            )
            SettingsPageDescription(
                text = stringResource(R.string.data_management_reset_description),
                color = secondaryText
            )
            UvirDataAndRestoreContent(
                autoEnabled = autoEnabled,
                sensorRestoreEnabled = sensorRestoreEnabled,
                secondaryText = secondaryText,
                onResetRequested = onResetAllRequested,
                onRestoreDefaultsRequested = onRestoreDefaultsRequested,
                onSensorRestoreRequested = onSensorRestoreRequested
            )
        }
    }

    // The menu entry lives at the end of Sensor; keep its existing detail content here.
    if (settingsNavigation != null && settingsNavigation.selectedPage == null) return

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
            secondaryText.copy(alpha = 0.28f),
        contentSpacing = UvirIslandSpacing,
        wrapDetailContent = false
    ) {
        UvirDiagnosticsIslands(
            cardColor = cardColor,
            primaryText = primaryText,
            secondaryText = secondaryText,
            diagnosticTest = {
                UvirSensorDiagnosticsContent(
                    sensorConnected = diagnosticSensorConnected && uvirDiagnosticPeerMatches(
                        diagnosticSensorDeviceId, diagnosticSensorInfo.deviceId,
                        connected = true, confirmed = true
                    ),
                    sensorInfo = diagnosticSensorInfo,
                    connectionMode = diagnosticConnectionMode,
                    sensorName = diagnosticSensorName,
                    cardColor = cardColor,
                    primaryText = primaryText,
                    secondaryText = secondaryText,
                    onProbe = onDiagnosticProbe,
                    showDescription = false
                )
            },
            simulation = {
                Column(
                    verticalArrangement =
                        Arrangement.spacedBy(UvirSettingsRelatedGap)
                ) {
                    OutlinedButton(
                        onClick = {
                            showSimulateEventsConfirmation = true
                        },
                        enabled = !simulatingEvents && diagnosticSensorDeviceId.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                        colors = uvirOutlinedActionColors(primaryText),
                        border =
                            uvirOutlinedActionBorder(
                                enabled = !simulatingEvents && diagnosticSensorDeviceId.isNotBlank(),
                                secondaryText = secondaryText
                            )
                    ) {
                        UvirLabeledButtonContent(
                            text = stringResource(R.string.debug_simulate_events)
                        ) {
                            UvirMenuIcon(
                                type = MenuIconType.SAVED_MEASUREMENTS,
                                modifier = Modifier.size(20.dp),
                                tint = LocalContentColor.current
                            )
                        }
                    }

                    Text(
                        text = stringResource(R.string.debug_simulate_events_description),
                        color = secondaryText,
                        fontSize = 11.sp,
                        lineHeight = 14.sp
                    )
                }

                UvirSensorSimulationSettingsContent(
                    enabled = fakeSensorDataEnabled,
                    outOfRange = fakeSensorOutOfRangeEnabled,
                    onEnabledChange = onFakeSensorDataEnabledChange,
                    onOutOfRangeChange = onFakeSensorOutOfRangeEnabledChange,
                    primaryText = primaryText,
                    secondaryText = secondaryText
                )
            },
            concert = {
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
        )
    }
}

@Composable
internal fun UvirDiagnosticsIslands(
    cardColor: Color,
    primaryText: Color,
    diagnosticTest: @Composable () -> Unit,
    simulation: @Composable () -> Unit,
    concert: @Composable () -> Unit,
    secondaryText: Color = primaryText
) {
    SettingsIsland(containerColor = cardColor, contentColor = primaryText) {
        UvirFormatGroupHeader(
            stringResource(R.string.diagnostics_test_group), ConnectivityIconType.DEBUG, primaryText
        )
        SettingsPageDescription(stringResource(R.string.diagnostic_description), secondaryText)
        diagnosticTest()
    }
    SettingsIsland(containerColor = cardColor, contentColor = primaryText) {
        UvirFormatGroupHeader(
            stringResource(R.string.diagnostics_simulation_group), ConnectivityIconType.SIMULATION, primaryText
        )
        SettingsPageDescription(stringResource(R.string.diagnostics_simulation_description), secondaryText)
        simulation()
    }
    SettingsIsland(containerColor = cardColor, contentColor = primaryText) {
        concert()
    }
}

@Composable
internal fun UvirSensorSimulationSettingsContent(
    enabled: Boolean,
    outOfRange: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    onOutOfRangeChange: (Boolean) -> Unit,
    primaryText: Color,
    secondaryText: Color
) {
    // These phone-only switches belong to the selected UID, not to its running job.
    SettingsCheckboxWithDescription(
        checked = enabled,
        onCheckedChange = onEnabledChange,
        title = stringResource(R.string.debug_fake_data),
        description = stringResource(R.string.debug_fake_data_description),
        primaryText = primaryText,
        secondaryText = secondaryText
    )
    SettingsCheckboxWithDescription(
        checked = outOfRange,
        onCheckedChange = onOutOfRangeChange,
        enabled = enabled,
        title = stringResource(R.string.debug_fake_out_of_range),
        description = stringResource(R.string.debug_fake_out_of_range_description),
        primaryText = primaryText,
        secondaryText = secondaryText
    )
}

@Composable
internal fun UvirErrorLogSettingsIsland(
    cardColor: Color,
    primaryText: Color,
    secondaryText: Color,
    onExport: () -> Unit
) {
    val resources = LocalResources.current
    val exportDescription = stringResource(R.string.debug_error_log)
    SettingsIsland(containerColor = cardColor, contentColor = primaryText) {
        UvirFormatGroupHeader(
            title = stringResource(R.string.data_management_error_log_group),
            icon = ConnectivityIconType.DEBUG,
            primaryText = primaryText
        )
        SettingsPageDescription(
            text = stringResource(R.string.data_management_error_log_description),
            color = secondaryText
        )
        OutlinedButton(
            onClick = onExport,
            modifier = Modifier.fillMaxWidth().semantics {
                contentDescription = exportDescription
            },
            colors = uvirOutlinedActionColors(primaryText),
            border = uvirOutlinedActionBorder(true, secondaryText)
        ) {
            UvirLabeledButtonContent(
                text = stringResource(R.string.export)
                    .uppercase(resources.configuration.locales[0])
            ) {
                UvirMenuIcon(
                    type = MenuIconType.EXPORT,
                    modifier = Modifier.size(20.dp),
                    tint = LocalContentColor.current
                )
            }
        }
    }
}

@Composable
internal fun UvirSettingsDiagnosticsListEntry(
    cardColor: Color,
    primaryText: Color,
    secondaryText: Color
) {
    SettingsSection(
        settingsPage = UvirSettingsPage.DEBUG,
        title = stringResource(R.string.settings_section_debug),
        titleIcon = ConnectivityIconType.DEBUG,
        containerColor = cardColor,
        titleColor = primaryText,
        chevronColor = secondaryText,
        dividerColor = secondaryText.copy(alpha = 0.28f)
    ) { /* The existing diagnostics page is rendered by UvirSettingsGeneralSections. */ }
}
