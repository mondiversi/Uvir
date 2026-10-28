package me.mondiversi.uvir

import android.app.Activity
import android.content.Context
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import me.mondiversi.uvir.usb.*

@Composable
internal fun UsbPhoneSettings(runtime: UvirUsbPhoneRuntime, state: UsbPhoneState, page: UvirSettingsPage,
    background: Color, card: Color, text: Color, secondary: Color,
    unit: UvirIrradianceUnit, onUnit: (UvirIrradianceUnit) -> Unit,
    numeric: UvirNumericFormat, onNumeric: (UvirNumericFormat) -> Unit,
    date: UvirDateFormat, onDate: (UvirDateFormat) -> Unit,
    time: UvirTimeFormat, onTime: (UvirTimeFormat) -> Unit, onBack: () -> Unit,
    onSettingsSaved: () -> Unit = {}) {
    val context = LocalContext.current
    val preferences = remember { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }
    val scope = rememberCoroutineScope()
    val editing = !state.measuringReference
    val canMeasure = state.status == UsbPhoneStatus.READY && state.ready && !state.simulation && !state.measuringReference
    // Earlier preview page IDs can survive Activity restoration; both now open General.
    val generalPage = page in setOf(UvirSettingsPage.GENERAL, UvirSettingsPage.APPEARANCE, UvirSettingsPage.ALERTS)
    val title = stringResource(usbSettingsTitle(page))
    val scroll = rememberScrollState()
    var showSettingsExport by remember { mutableStateOf(false) }
    var showCounterReset by remember { mutableStateOf(false) }
    var showAppReset by remember { mutableStateOf(false) }
    var appLanguage by remember { mutableStateOf(AppLanguage.fromStoredValue(preferences.getString(KEY_APP_LANGUAGE, "system"))) }
    var showSimulationConfirmation by remember { mutableStateOf(false) }
    var simulatingEvents by remember { mutableStateOf(false) }
    if (showSimulationConfirmation) UvirAlertDialog(onDismissRequest = { if (!simulatingEvents) showSimulationConfirmation = false },
        title = { Text(stringResource(R.string.debug_simulate_events_question)) },
        text = { Text(stringResource(R.string.debug_simulate_events_confirmation)) }, containerColor = card, textContentColor = text,
        confirmButton = {
            TextButton(enabled = !simulatingEvents, onClick = { scope.launch {
                simulatingEvents = true
                try {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { runtime.database.insertDebugSimulationEvents("") }
                    showSimulationConfirmation = false
                } catch (error: Exception) { UvirErrorLog.record(context, "simulate_events", error) }
                finally { simulatingEvents = false }
            } }) { Text(stringResource(R.string.debug_simulate_events)) }
        }, dismissButton = { UvirDialogCancelButton(onClick = { showSimulationConfirmation = false }, enabled = !simulatingEvents) })
    if (showCounterReset) UvirCounterResetConfirmationDialog(card, text, secondary,
        onResetAllCounters = { runtime.database.resetAllCounters() }, onDismissRequest = { showCounterReset = false })
    if (showAppReset) UvirRestoreDefaultsConfirmationDialog(card, text, secondary,
        onRestoreDefaults = {
            context.getSharedPreferences(USB_PHONE_PREFS, Context.MODE_PRIVATE).edit().clear().commit()
            runtime.reloadPhoneSettings()
            restoreUvirApplicationDefaults(context, runtime.database)
        },
        onRestored = { showAppReset = false; (context as? Activity)?.recreate() }, onDismissRequest = { showAppReset = false })
    if (showSettingsExport) UvirPhoneBackupExportDialog(R.string.export_settings_title,
        R.string.usb_settings_export_description, UvirExportFileFormat.JSON, card, text, secondary,
        onDismiss = { showSettingsExport = false }, onExport = { destination ->
            val file = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                createUvirAppSettingsBackup(context, null)
            }
            deliverUvirExportFiles(context, listOf(file), destination, context.getString(R.string.export_settings_title))
            showSettingsExport = false
        })
    UvirFullScreenPage(onDismissRequest = onBack, title = { UvirMenuTitle(title) },
        containerColor = background, contentColor = text, scrollState = scroll, text = {
            CompositionLocalProvider(LocalUvirSettingsActionButtons provides true, LocalUvirSettingsNeutralIcons provides true,
                LocalUvirSettingsNavigation provides UvirSettingsNavigation(page, {})) {
                UvirIslandColumns(Modifier.fillMaxSize().verticalScroll(scroll), spacing = UvirIslandSpacing,
                    twoColumnsEnabled = !generalPage) {
                    when (page) {
                        UvirSettingsPage.SENSOR_MEASUREMENT -> {
                            SettingsStaticIsland(title = stringResource(R.string.settings_section_acquisition), titleIcon = ConnectivityIconType.SAMPLING, containerColor = card, titleColor = text) {
                                SettingsPageDescription(stringResource(R.string.usb_phone_processing), secondary)
                                UsbPhoneIntField(R.string.usb_sampling_count, state.samples.toString(), 1L..100L) {
                                    if (it.toInt() != state.samples) {
                                        runtime.setSampling(it.toInt(), state.spacingMs, state.trim); onSettingsSaved()
                                    }
                                }
                                SettingsCheckboxWithDescription(state.trim,
                                    { runtime.setSampling(state.samples, state.spacingMs, it); onSettingsSaved() },
                                    title = stringResource(R.string.discard_extremes),
                                    description = stringResource(R.string.discard_extremes_description),
                                    primaryText = text, secondaryText = secondary)
                                SettingsGroupDivider(secondary)
                                UsbPhoneIntField(R.string.usb_sampling_spacing, state.spacingMs.toString(), 0L..60000L) {
                                    if (it != state.spacingMs) {
                                        runtime.setSampling(state.samples, it, state.trim); onSettingsSaved()
                                    }
                                }
                            }
                            UvirSensorCalibrationSettings(context, editing, true, "1.0", {}, "1.0", {},
                                card, text, secondary,
                                offsets = CalibrationOffsets(state.calibration.offsets.map(Double::toFloat)), offsetsSupported = true,
                                onOffsetsChange = { offsets -> runtime.updateCalibration { it.copy(offsets = offsets.values.map(Float::toDouble)) }; onSettingsSaved() },
                                channelFactors = CalibrationChannelFactors(state.calibration.factors.map(Double::toFloat)), channelFactorsSupported = true,
                                onChannelFactorsChange = { factors -> runtime.updateCalibration { it.copy(factors = factors.values.map(Float::toDouble)) }; onSettingsSaved() },
                                sensorDeviceId = "phone-usb", darkMeasurementSupported = true,
                                onMeasureDarkOffsets = if (canMeasure) runtime::measureOffsets else null)
                            UvirOpticalWindowSettings(
                                OpticalWindowSettings(true, state.calibration.window.map(Double::toFloat), state.calibration.windowMaster.toFloat()),
                                onChange = { window -> runtime.updateCalibration { it.copy(windowMaster = window.masterMultiplier.toDouble(), window = window.factors.map(Float::toDouble)) }; onSettingsSaved() },
                                enabled = editing, supported = true, cardColor = card, primaryText = text, secondaryText = secondary,
                                sensorDeviceId = "phone-usb", onMeasureWindow = if (canMeasure) runtime::measureWindow else null)
                        }
                        UvirSettingsPage.DEBUG -> {
                            SettingsStaticIsland(title = stringResource(R.string.usb_diagnostics), titleIcon = ConnectivityIconType.DEBUG, containerColor = card, titleColor = text) {
                                SettingsPageDescription("FT232H: ${state.status}", secondary)
                                SettingsPageDescription("AS7331 · 0x74: ${if (state.uvAvailable) "✓" else "—"} · ${state.uvGain ?: "—"}×", secondary)
                                SettingsPageDescription("AS7343 · 0x39: ${if (state.visibleAvailable) "✓" else "—"} · ${state.visibleGain ?: "—"}×", secondary)
                                state.error?.let { Text(it, color = secondary, fontSize = 12.sp) }
                                SettingsPageDescription(stringResource(R.string.usb_hardware_pending), secondary)
                                UsbPhoneAction(R.string.usb_retry, !state.simulation, onClick = runtime::retry)
                            }
                            SettingsStaticIsland(title = stringResource(R.string.usb_simulation), titleIcon = ConnectivityIconType.DEBUG, containerColor = card, titleColor = text) {
                                UvirSimulateEventsButton(simulatingEvents, { showSimulationConfirmation = true }, text, secondary)
                                SettingsCheckboxWithDescription(state.simulation,
                                    { runtime.setSimulation(it); onSettingsSaved() },
                                    title = stringResource(R.string.debug_fake_data),
                                    description = stringResource(R.string.debug_fake_data_description),
                                    enabled = state.automaticSession == null && state.alertSession == null && !state.busy,
                                    primaryText = text, secondaryText = secondary,
                                    modifier = Modifier.testTag("usb_simulation_toggle"))
                                SettingsCheckboxWithDescription(state.outOfRangeSimulation,
                                    { runtime.setOutOfRangeSimulation(it); onSettingsSaved() },
                                    title = stringResource(R.string.debug_fake_out_of_range),
                                    description = stringResource(R.string.debug_fake_out_of_range_description),
                                    enabled = state.simulation, primaryText = text, secondaryText = secondary)
                            }
                        }
                        UvirSettingsPage.GENERAL, UvirSettingsPage.APPEARANCE, UvirSettingsPage.ALERTS ->
                            UsbPhoneGeneralSettings(page, card, text, secondary, onSettingsSaved)
                        else -> UvirSettingsGeneralSections(
                            database = runtime.database, autoEnabled = state.automaticSession != null || state.alertSession != null,
                            sensorRestoreEnabled = false,
                            numericFormatValue = numeric.storedValue, onNumericFormatValueChange = { onNumeric(UvirNumericFormat.fromStoredValue(it)); onSettingsSaved() },
                            dateFormatValue = date.storedValue, onDateFormatValueChange = { onDate(UvirDateFormat.fromStoredValue(it)); onSettingsSaved() },
                            timeFormatValue = time.storedValue, onTimeFormatValueChange = { onTime(UvirTimeFormat.fromStoredValue(it)); onSettingsSaved() },
                            exportModeValue = loadUvirExportMode(context).storedValue, onExportModeValueChange = { saveUvirExportMode(context, UvirExportMode.fromStoredValue(it)); onSettingsSaved() },
                            irradianceUnitValue = unit.storedValue, onIrradianceUnitValueChange = { onUnit(UvirIrradianceUnit.fromStoredValue(it)); onSettingsSaved() },
                            numericFormatSectionExpanded = true, onNumericFormatSectionExpandedChange = {},
                            appLanguage = appLanguage, onAppLanguageChanged = { language ->
                                appLanguage = language
                                preferences.edit().putString(KEY_APP_LANGUAGE, language.storedValue).apply()
                                onSettingsSaved()
                                (context as? Activity)?.recreate()
                            }, languageSectionExpanded = true, onLanguageSectionExpandedChange = {},
                            countersSectionExpanded = true, onCountersSectionExpandedChange = {},
                            onResetAllRequested = { showCounterReset = true }, onRestoreDefaultsRequested = { showAppReset = true },
                            onSensorRestoreRequested = {}, onSettingsExportRequested = { showSettingsExport = true },
                            onSettingsImportRequested = { requestUvirSettingsImport(context) }, onDatabaseImportRequested = { requestUvirDatabaseImport(context) },
                            debugSectionExpanded = false, onDebugSectionExpandedChange = {}, fakeSensorDataEnabled = state.simulation,
                            onFakeSensorDataEnabledChange = runtime::setSimulation, fakeSensorOutOfRangeEnabled = false, onFakeSensorOutOfRangeEnabledChange = {},
                            debugPerformanceEnabled = false, debugPerformanceSensorConnected = false, debugPerformanceFirmwareSupported = false,
                            debugPerformanceCompletionToken = 0, onStartDebugPerformance = { false }, onStopDebugPerformance = { false },
                            diagnosticSensorConnected = false, diagnosticSensorDeviceId = "", diagnosticSensorInfo = UvirSensorRuntimeInfo(),
                            diagnosticConnectionMode = SensorConnectionMode.USB, diagnosticSensorName = "",
                            onDiagnosticProbe = { _, _ -> UvirDiagnosticProbe(UvirDiagnosticOutcome.CONNECTION_CHANGED) },
                            cardColor = card, primaryText = text, secondaryText = secondary,
                            liveIrradianceDecimals = currentUvirLiveIrradianceDecimals(),
                            onLiveIrradianceDecimalsChange = { saveUvirLiveIrradianceDecimals(context, unit, it); onSettingsSaved() })
                    }
                }
            }
        })
}

@Composable
internal fun UsbPhoneIntField(label: Int, stored: String, range: LongRange, onValue: (Long) -> Unit) {
    var draft by remember(stored) { mutableStateOf(stored) }
    NumberField(value = draft, onValueChange = { value ->
        if (value.length <= 10 && value.all(Char::isDigit)) { draft = value; value.toLongOrNull()?.takeIf { it in range }?.let(onValue) }
    }, label = stringResource(label), modifier = Modifier.fillMaxWidth().testTag("usb_setting_$label"),
        maxChars = range.last.toString().length,
        onEditingComplete = {
            val normalized = normalizeBoundedLong(draft, range.first, range.last).text
            draft = normalized
            normalized.toLongOrNull()?.let(onValue)
        })
}

private fun usbSettingsTitle(page: UvirSettingsPage): Int = when (page) {
    UvirSettingsPage.SENSOR_MEASUREMENT -> R.string.sensor_info_measurement
    UvirSettingsPage.DEBUG -> R.string.settings_section_debug
    UvirSettingsPage.GENERAL, UvirSettingsPage.APPEARANCE, UvirSettingsPage.ALERTS -> R.string.settings_section_sensor_parameters
    UvirSettingsPage.LANGUAGE -> R.string.settings_section_language
    UvirSettingsPage.LANGUAGE_AND_FORMATS -> R.string.settings_section_language_formats
    else -> R.string.data_and_restore_title
}

@Composable
private fun UsbPhoneGeneralSettings(page: UvirSettingsPage, card: Color, text: Color,
    secondary: Color, onSettingsSaved: () -> Unit) {
    val context = LocalContext.current
    val preferences = remember { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }
    val scope = rememberCoroutineScope()
    var alerts by remember { mutableStateOf(loadThresholdAlertSettings(preferences).withMonitoringStopped()) }
    var feedback by remember { mutableStateOf(loadAcquisitionFeedbackSettings(preferences)) }
    UvirSamplingAndAlertSettings(context, false, true, false, "1", {}, "100", {}, false, {}, null,
        true, {}, alerts.sound.name, { raw ->
            alerts = alerts.copy(sound = ThresholdAlertSound.valueOf(raw)); saveThresholdAlertSettings(preferences, alerts); onSettingsSaved()
        }, alerts.volume.toFloat(), { volume -> alerts = alerts.copy(volume = volume.toInt()); saveThresholdAlertSettings(preferences, alerts); onSettingsSaved() },
        onPreviewThresholdAlertSound = { sound, volume -> scope.launch { playThresholdAlertTone(context, sound, volume) } },
        acquisitionFeedbackSettings = feedback, onAcquisitionFeedbackSettingsChange = { feedback = it; saveAcquisitionFeedbackSettings(preferences, it); onSettingsSaved() },
        onPreviewAcquisitionFeedback = { sound, volume -> scope.launch { playThresholdAlertTone(context, sound, volume, toneProfile = UvirFeedbackToneProfile.ACQUISITION) } },
        cardColor = card, primaryText = text, secondaryText = secondary,
        alertsPage = page, alertsLeadingContent = { UvirThemeSettingsIsland(card, text, secondary) })
}
