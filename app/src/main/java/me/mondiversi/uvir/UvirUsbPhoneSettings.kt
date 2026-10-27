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
    time: UvirTimeFormat, onTime: (UvirTimeFormat) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val preferences = remember { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }
    val scope = rememberCoroutineScope()
    val editing = state.status == UsbPhoneStatus.READY && !state.simulation && !state.measuringReference
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
    if (showSettingsExport) UvirSettingsExportDialog(card, text, secondary, runtime.database.readSensorProfiles()
        .filterNot { it.hardwareUid.startsWith("FT232H", ignoreCase = true) },
        onDismiss = { showSettingsExport = false }, onExport = { includeApp, ids, password, destination ->
            try {
                val files = buildList {
                    if (includeApp) add(createUvirAppSettingsBackup(context, password))
                    ids.forEach { add(createUvirSensorSettingsBackup(context, runtime.database, it, true, password)) }
                }
                deliverUvirExportFiles(context, files, destination, context.getString(R.string.export_settings_title))
                showSettingsExport = false
            } catch (error: Exception) { UvirErrorLog.record(context, "export_settings", error) }
            finally { password.fill('\u0000') }
        })
    UvirFullScreenPage(onDismissRequest = onBack, title = { UvirMenuTitle(title) },
        containerColor = background, contentColor = text, scrollState = scroll, text = {
            CompositionLocalProvider(LocalUvirSettingsActionButtons provides true, LocalUvirSettingsNeutralIcons provides true,
                LocalUvirSettingsNavigation provides UvirSettingsNavigation(page, {})) {
                UvirIslandColumns(Modifier.fillMaxSize().verticalScroll(scroll), spacing = UvirIslandSpacing) {
                    when (page) {
                        UvirSettingsPage.SENSOR_MEASUREMENT -> {
                            SettingsStaticIsland(title = stringResource(R.string.settings_section_acquisition), titleIcon = ConnectivityIconType.SAMPLING, containerColor = card, titleColor = text) {
                                Text(stringResource(R.string.usb_phone_processing), color = secondary, fontSize = 13.sp)
                                UsbPhoneIntField(R.string.usb_sampling_count, state.samples.toString(), 1L..100L) {
                                    runtime.setSampling(it.toInt(), state.spacingMs, state.trim)
                                }
                                UsbPhoneIntField(R.string.usb_sampling_spacing, state.spacingMs.toString(), 0L..60000L) {
                                    runtime.setSampling(state.samples, it, state.trim)
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(state.trim, { runtime.setSampling(state.samples, state.spacingMs, it) }, colors = uvirCheckboxColors())
                                    Text(stringResource(R.string.usb_sampling_trim), fontSize = 13.sp)
                                }
                            }
                            UvirSensorCalibrationSettings(context, editing, state.uvAvailable, "1.0", {}, "1.0", {},
                                card, text, secondary,
                                offsets = CalibrationOffsets(state.calibration.offsets.map(Double::toFloat)), offsetsSupported = true,
                                onOffsetsChange = { runtime.setCalibration(state.calibration.copy(offsets = it.values.map(Float::toDouble))) },
                                channelFactors = CalibrationChannelFactors(state.calibration.factors.map(Double::toFloat)), channelFactorsSupported = true,
                                onChannelFactorsChange = { runtime.setCalibration(state.calibration.copy(factors = it.values.map(Float::toDouble))) },
                                sensorDeviceId = state.identity, darkMeasurementSupported = editing,
                                onMeasureDarkOffsets = runtime::measureOffsets)
                            UvirOpticalWindowSettings(
                                OpticalWindowSettings(true, state.calibration.window.map(Double::toFloat), state.calibration.windowMaster.toFloat()),
                                onChange = { runtime.setCalibration(state.calibration.copy(windowMaster = it.masterMultiplier.toDouble(), window = it.factors.map(Float::toDouble))) },
                                enabled = editing, supported = true, cardColor = card, primaryText = text, secondaryText = secondary,
                                sensorDeviceId = state.identity, onMeasureWindow = runtime::measureWindow)
                            if (!editing) Text(stringResource(R.string.usb_probe_required), color = secondary, fontSize = 12.sp)
                            if (editing && !state.persistentIdentity) Text(stringResource(R.string.usb_identity_temporary), color = secondary, fontSize = 12.sp)
                        }
                        UvirSettingsPage.DEBUG -> {
                            SettingsStaticIsland(title = stringResource(R.string.usb_diagnostics), titleIcon = ConnectivityIconType.DEBUG, containerColor = card, titleColor = text) {
                                Text("FT232H: ${state.status}", color = secondary)
                                Text("AS7331 · 0x74: ${if (state.uvAvailable) "✓" else "—"} · ${state.uvGain ?: "—"}×", color = secondary)
                                Text("AS7343 · 0x39: ${if (state.visibleAvailable) "✓" else "—"} · ${state.visibleGain ?: "—"}×", color = secondary)
                                state.error?.let { Text(it, color = secondary, fontSize = 12.sp) }
                                Text(stringResource(R.string.usb_hardware_pending), color = secondary, fontSize = 12.sp)
                                UsbPhoneAction(R.string.usb_retry, !state.simulation, onClick = runtime::retry)
                            }
                            SettingsStaticIsland(title = stringResource(R.string.usb_simulation), titleIcon = ConnectivityIconType.DEBUG, containerColor = card, titleColor = text) {
                                UvirSimulateEventsButton(simulatingEvents, { showSimulationConfirmation = true }, text, secondary)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(state.simulation, { runtime.setSimulation(it) }, enabled = state.automaticSession == null && state.alertSession == null && !state.busy,
                                        colors = uvirCheckboxColors(), modifier = Modifier.testTag("usb_simulation_toggle"))
                                    Text(stringResource(R.string.usb_simulation))
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(state.outOfRangeSimulation, runtime::setOutOfRangeSimulation,
                                        enabled = state.simulation, colors = uvirCheckboxColors())
                                    Text(stringResource(R.string.debug_fake_out_of_range))
                                }
                            }
                        }
                        UvirSettingsPage.APPEARANCE -> UvirThemeSettingsIsland(card, text, secondary)
                        UvirSettingsPage.ALERTS -> UsbPhoneAlertSettings(runtime, state, card, text, secondary)
                        else -> UvirSettingsGeneralSections(
                            database = runtime.database, autoEnabled = state.automaticSession != null || state.alertSession != null,
                            sensorRestoreEnabled = false,
                            numericFormatValue = numeric.storedValue, onNumericFormatValueChange = { onNumeric(UvirNumericFormat.fromStoredValue(it)) },
                            dateFormatValue = date.storedValue, onDateFormatValueChange = { onDate(UvirDateFormat.fromStoredValue(it)) },
                            timeFormatValue = time.storedValue, onTimeFormatValueChange = { onTime(UvirTimeFormat.fromStoredValue(it)) },
                            exportModeValue = loadUvirExportMode(context).storedValue, onExportModeValueChange = { saveUvirExportMode(context, UvirExportMode.fromStoredValue(it)) },
                            irradianceUnitValue = unit.storedValue, onIrradianceUnitValueChange = { onUnit(UvirIrradianceUnit.fromStoredValue(it)) },
                            numericFormatSectionExpanded = true, onNumericFormatSectionExpandedChange = {},
                            appLanguage = appLanguage, onAppLanguageChanged = { language ->
                                appLanguage = language
                                preferences.edit().putString(KEY_APP_LANGUAGE, language.storedValue).apply()
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
                            onLiveIrradianceDecimalsChange = { saveUvirLiveIrradianceDecimals(context, unit, it) })
                    }
                }
            }
        })
}

@Composable
internal fun UsbPhoneIntField(label: Int, stored: String, range: LongRange, onValue: (Long) -> Unit) {
    var draft by remember(stored) { mutableStateOf(stored) }
    OutlinedTextField(value = draft, onValueChange = { value ->
        if (value.length <= 10 && value.all(Char::isDigit)) { draft = value; value.toLongOrNull()?.takeIf { it in range }?.let(onValue) }
    }, label = { Text(stringResource(label)) }, modifier = Modifier.fillMaxWidth(), singleLine = true,
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number))
}

private fun usbSettingsTitle(page: UvirSettingsPage): Int = when (page) {
    UvirSettingsPage.SENSOR_MEASUREMENT -> R.string.sensor_info_measurement
    UvirSettingsPage.DEBUG -> R.string.settings_section_debug
    UvirSettingsPage.APPEARANCE -> R.string.settings_section_appearance
    UvirSettingsPage.ALERTS -> R.string.settings_section_notifications
    UvirSettingsPage.LANGUAGE -> R.string.settings_section_language
    UvirSettingsPage.LANGUAGE_AND_FORMATS -> R.string.settings_section_language_formats
    else -> R.string.data_and_restore_title
}

@Composable
private fun UsbPhoneAlertSettings(runtime: UvirUsbPhoneRuntime, state: UsbPhoneState, card: Color, text: Color, secondary: Color) {
    val context = LocalContext.current
    val preferences = remember { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }
    val scope = rememberCoroutineScope()
    var alerts by remember { mutableStateOf(loadThresholdAlertSettings(preferences).withMonitoringStopped()) }
    var showRules by remember { mutableStateOf(false) }
    var feedback by remember { mutableStateOf(loadAcquisitionFeedbackSettings(preferences)) }
    if (showRules) UvirAlertValuesScreen(alerts.rules, state.sample, true, card, text, secondary,
        onSave = { alerts = alerts.copy(rules = it); saveThresholdAlertSettings(preferences, alerts); showRules = false }, onBack = { showRules = false })
    SettingsStaticIsland(title = stringResource(R.string.threshold_alerts_title), titleIcon = ConnectivityIconType.NOTIFICATIONS, containerColor = card, titleColor = text) {
        UsbPhoneAction(R.string.alert_configure_values) { showRules = true }
        if (state.alertSession == null) UsbPhoneAction(R.string.start, state.ready && alerts.rules.any { it.enabled }) { runtime.startAlerts(alerts) }
        else UsbPhoneAction(R.string.stop) { runtime.stopAlerts() }
    }
    UvirSamplingAndAlertSettings(context, false, true, false, "1", {}, "100", {}, false, {}, null,
        true, {}, alerts.sound.name, { raw ->
            alerts = alerts.copy(sound = ThresholdAlertSound.valueOf(raw)); saveThresholdAlertSettings(preferences, alerts)
        }, alerts.volume.toFloat(), { volume -> alerts = alerts.copy(volume = volume.toInt()); saveThresholdAlertSettings(preferences, alerts) },
        onPreviewThresholdAlertSound = { sound, volume -> scope.launch { playThresholdAlertTone(context, sound, volume) } },
        acquisitionFeedbackSettings = feedback, onAcquisitionFeedbackSettingsChange = { feedback = it; saveAcquisitionFeedbackSettings(preferences, it) },
        onPreviewAcquisitionFeedback = { sound, volume -> scope.launch { playThresholdAlertTone(context, sound, volume, toneProfile = UvirFeedbackToneProfile.ACQUISITION) } },
        cardColor = card, primaryText = text, secondaryText = secondary)
}
