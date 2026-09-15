package me.mondiversi.uvir

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun UvirSettingsGeneralSections(
    database: UvirDatabaseHelper,
    autoEnabled: Boolean,
    numericFormatValue: String,
    onNumericFormatValueChange: (String) -> Unit,
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

    SettingsSection(
        settingsPage = UvirSettingsPage.NUMERIC_FORMAT,
        title =
            stringResource(
                R.string.settings_section_numeric_format
            ),
        titleIcon =
            ConnectivityIconType.NUMERIC_FORMAT,
        expanded =
            numericFormatSectionExpanded,
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
        dividerColor =
            secondaryText.copy(
                alpha = 0.28f
            )
    ) {
        Column(
            verticalArrangement =
                Arrangement.spacedBy(UvirSettingsControlGap)
        ) {
            Text(
                text =
                    stringResource(
                        R.string.numeric_format_description
                    ),
                color = secondaryText,
                fontSize = 12.sp
            )

            SettingsChoiceGroup {
                UvirNumericFormat.entries
                    .forEach { format ->
                        NumericFormatOptionRow(
                        selected =
                            numericFormatValue ==
                                    format.storedValue,
                        label =
                            stringResource(
                                when (format) {
                                    UvirNumericFormat.SYSTEM ->
                                        R.string.numeric_format_system

                                    UvirNumericFormat.INTERNATIONAL ->
                                        R.string.numeric_format_international

                                    UvirNumericFormat.EUROPEAN ->
                                        R.string.numeric_format_european
                                }
                            ),
                        example =
                            when (format) {
                                UvirNumericFormat.SYSTEM ->
                                    formatUvirNumber(
                                        value = 1000.23,
                                        fractionDigits = 2,
                                        format = UvirNumericFormat.SYSTEM
                                    )

                                UvirNumericFormat.INTERNATIONAL ->
                                    stringResource(
                                        R.string.numeric_format_international_example
                                    )

                                UvirNumericFormat.EUROPEAN ->
                                    stringResource(
                                        R.string.numeric_format_european_example
                                    )
                            },
                        primaryText = primaryText,
                        secondaryText = secondaryText,
                        onClick = {
                            onNumericFormatValueChange(
                                format.storedValue
                            )
                        }
                        )
                    }
            }
        }
    }

    SettingsSection(
        settingsPage = UvirSettingsPage.LANGUAGE,
        title =
            stringResource(
                R.string.settings_section_language
            ),
        titleIcon =
            ConnectivityIconType.LANGUAGE,
        expanded =
            languageSectionExpanded,
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
        dividerColor =
            secondaryText.copy(
                alpha = 0.28f
            )
    ) {
        Column(
            verticalArrangement =
                Arrangement.spacedBy(UvirSettingsControlGap)
        ) {
            Text(
                text =
                    stringResource(
                        R.string.language_description
                    ),
                color = secondaryText,
                fontSize = 12.sp
            )

            SettingsChoiceGroup {
                listOf(
                    AppLanguage.SYSTEM,
                    AppLanguage.ARABIC,
                    AppLanguage.CHINESE,
                    AppLanguage.ENGLISH,
                    AppLanguage.FRENCH,
                    AppLanguage.GERMAN,
                    AppLanguage.GREEK,
                    AppLanguage.HEBREW,
                    AppLanguage.HINDI,
                    AppLanguage.ITALIAN,
                    AppLanguage.JAPANESE,
                    AppLanguage.KOREAN,
                    AppLanguage.PERSIAN,
                    AppLanguage.PORTUGUESE,
                    AppLanguage.RUSSIAN,
                    AppLanguage.SPANISH,
                    AppLanguage.SWAHILI,
                    AppLanguage.TURKISH
                ).forEach { language ->
                    LanguageOptionRow(
                    language = language,
                    selected =
                        appLanguage == language,
                    label =
                        stringResource(
                            when (language) {
                                AppLanguage.SYSTEM ->
                                    R.string.language_system

                                AppLanguage.ITALIAN ->
                                    R.string.language_italian

                                AppLanguage.ENGLISH ->
                                    R.string.language_english

                                AppLanguage.SPANISH ->
                                    R.string.language_spanish

                                AppLanguage.FRENCH ->
                                    R.string.language_french

                                AppLanguage.GERMAN ->
                                    R.string.language_german

                                AppLanguage.GREEK ->
                                    R.string.language_greek

                                AppLanguage.PORTUGUESE ->
                                    R.string.language_portuguese

                                AppLanguage.PERSIAN ->
                                    R.string.language_persian

                                AppLanguage.RUSSIAN ->
                                    R.string.language_russian

                                AppLanguage.HEBREW ->
                                    R.string.language_hebrew

                                AppLanguage.HINDI ->
                                    R.string.language_hindi

                                AppLanguage.ARABIC ->
                                    R.string.language_arabic

                                AppLanguage.CHINESE ->
                                    R.string.language_chinese

                                AppLanguage.JAPANESE ->
                                    R.string.language_japanese

                                AppLanguage.KOREAN ->
                                    R.string.language_korean

                                AppLanguage.SWAHILI ->
                                    R.string.language_swahili

                                AppLanguage.TURKISH ->
                                    R.string.language_turkish

                            }
                        ),
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
        Text(
            text = stringResource(R.string.data_and_restore_description_split),
            color = secondaryText,
            fontSize = 12.sp
        )

        Column(
            verticalArrangement =
                Arrangement.spacedBy(UvirSettingsRelatedGap)
        ) {
            OutlinedButton(
                onClick = {
                    runCatching {
                        shareDatabase(context, database)
                    }.onFailure { error ->
                        UvirErrorLog.record(
                            context,
                            "export_database",
                            error
                        )
                        showUvirBottomMessage(
                            context,
                            resources.getString(R.string.export_database_error)
                        )
                    }
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
                UvirMenuIcon(
                    type = MenuIconType.SHARE,
                    modifier = Modifier.size(20.dp),
                    tint = LocalContentColor.current
                )
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.export_database))
            }

            Text(
                text = stringResource(R.string.export_database_description),
                color = secondaryText,
                fontSize = 12.sp
            )
        }

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
        Text(
            text = stringResource(R.string.debug_description),
            color = secondaryText,
            fontSize = 12.sp,
            lineHeight = 16.sp
        )

        Column(
            verticalArrangement =
                Arrangement.spacedBy(UvirSettingsRelatedGap)
        ) {
            OutlinedButton(
                onClick = {
                    runCatching {
                        UvirErrorLog.share(
                            context = context,
                            chooserTitle =
                                resources.getString(
                                    R.string.debug_error_log
                                ),
                            subject =
                                resources.getString(
                                    R.string.debug_error_log_subject
                                )
                        )
                    }.onSuccess { shared ->
                        if (!shared) {
                            showUvirBottomMessage(
                                context,
                                resources.getString(
                                    R.string.debug_error_log_empty
                                ),
                                longDuration = false
                            )
                        }
                    }.onFailure { error ->
                        UvirErrorLog.record(
                            context,
                            "share_error_log",
                            error
                        )
                        showUvirBottomMessage(
                            context,
                            resources.getString(
                                R.string.debug_error_log_share_error
                            ),
                            longDuration = false
                        )
                    }
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
                UvirMenuIcon(
                    type = MenuIconType.SHARE,
                    modifier = Modifier.size(20.dp),
                    tint = LocalContentColor.current
                )

                Spacer(Modifier.width(8.dp))

                AdaptiveSingleLineButtonText(
                    text =
                        stringResource(
                            R.string.debug_error_log
                        )
                )
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
