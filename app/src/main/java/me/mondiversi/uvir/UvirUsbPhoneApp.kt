package me.mondiversi.uvir

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.animation.core.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import android.app.Activity
import android.os.Build
import androidx.core.view.WindowCompat
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.mondiversi.uvir.usb.*

private val usbSettingsGroups = listOf(
    UvirHomeSettingsMenuGroup(R.string.settings_category_sensor, listOf(
        UvirHomeSettingsMenuEntry(UvirSettingsPage.SENSOR_MEASUREMENT, R.string.sensor_info_measurement, ConnectivityIconType.MEASUREMENT),
        UvirHomeSettingsMenuEntry(UvirSettingsPage.DEBUG, R.string.settings_section_debug, ConnectivityIconType.DEBUG))),
    UvirHomeSettingsMenuGroup(null, listOf(
        UvirHomeSettingsMenuEntry(UvirSettingsPage.GENERAL, R.string.settings_section_sensor_parameters, ConnectivityIconType.GEAR),
        UvirHomeSettingsMenuEntry(UvirSettingsPage.LANGUAGE, R.string.settings_section_language, ConnectivityIconType.LANGUAGE),
        UvirHomeSettingsMenuEntry(UvirSettingsPage.LANGUAGE_AND_FORMATS, R.string.settings_section_language_formats, ConnectivityIconType.NUMERIC_FORMAT))),
    uvirHomeSettingsMenuGroups[2]).map { it.copy(titleRes = null) }

/** USB entry point. Historical screens are reused unchanged; no legacy manager is started. */
@Composable
internal fun UvirUsbPhoneApp(runtime: UvirUsbPhoneRuntime) {
    val context = LocalContext.current
    val preferences = remember { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }
    // USB I/O runs on its own executor; apply UI snapshots on Android's main dispatcher.
    val state by runtime.state.collectAsState(context = Dispatchers.Main)
    var route by rememberSaveable { mutableStateOf("HOME") }
    var settingsPage by rememberSaveable { mutableStateOf(UvirSettingsPage.SENSOR_MEASUREMENT) }
    val settingsConfirmation = rememberUvirSettingsExitNotice(if (route == "SETTINGS") settingsPage.name else null)
    var record by remember { mutableStateOf<SavedRecordDetail?>(null) }
    var sessionId by rememberSaveable { mutableStateOf<Long?>(null) }
    var recordBack by rememberSaveable { mutableStateOf("HISTORY") }
    val dark = isSystemInDarkTheme()
    val background = if (dark) Color(0xff101418) else Color(0xfff4f7f9)
    val card = if (dark) Color(0xff1c242b) else Color.White
    val text = if (dark) Color.White else Color(0xff101418)
    val secondary = if (dark) Color(0xff90a4ae) else Color(0xff546e7a)
    val track = if (dark) Color(0xff37474f) else Color(0xffdce3e7)
    SideEffect {
        (context as? Activity)?.window?.let { window ->
            WindowCompat.getInsetsController(window, window.decorView).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
            if (Build.VERSION.SDK_INT < 35) {
                @Suppress("DEPRECATION")
                window.statusBarColor = background.toArgb()
                @Suppress("DEPRECATION")
                window.navigationBarColor = background.toArgb()
            }
        }
    }
    var unit by remember { mutableStateOf(loadUvirIrradianceUnit(context)) }
    var numeric by remember { mutableStateOf(loadUvirNumericFormat(context)) }
    var date by remember { mutableStateOf(loadUvirDateFormat(context)) }
    var time by remember { mutableStateOf(loadUvirTimeFormat(context)) }
    val decimals = rememberUvirIrradianceDisplayDecimals(context)
    val listState = rememberLazyListState()
    var anchor by remember { mutableStateOf(ListEdgeAnchor.START) }
    CompositionLocalProvider(LocalUvirIrradianceUnit provides unit, LocalUvirNumericFormat provides numeric,
        LocalUvirDateFormat provides date, LocalUvirTimeFormat provides resolveUvirTimeFormat(context, time),
        LocalUvirLiveIrradianceDecimals provides decimals.getValue(unit)) {
        UvirSystemBarsContent(backgroundColor = background) {
            Surface(Modifier.fillMaxSize(), color = background, contentColor = text) {
                when (route) {
                    "HISTORY" -> HistoryScreen(runtime.database, listState, anchor, { anchor = it }, background, card, text, secondary,
                        onBack = { route = "HOME" }, onOpenSessionChart = { sessionId = it; route = "SESSION" },
                        onOpenRecord = { record = it; recordBack = "HISTORY"; route = "RECORD" }, filterPreferences = preferences)
                    "RECORD" -> record?.let { item -> RecordDetailScreen(item, runtime.database, rememberLazyListState(),
                        background, card, text, secondary, track, onBack = { route = recordBack }, onDeleted = { route = recordBack }) }
                    "SESSION" -> sessionId?.let { id ->
                        val records by produceState(emptyList<SavedRecordDetail>(), id, route) {
                            value = withContext(Dispatchers.IO) { runtime.database.readSessionRecords(id) }
                        }
                        SessionChartScreen(id, records, runtime.database, background, card, text, secondary,
                            onDeleteSession = { runtime.database.deleteRecords(records.map { it.id }); route = "HISTORY" },
                            onOpenRecord = { item, _ -> record = item; recordBack = "SESSION"; route = "RECORD" },
                            onBack = { route = "HISTORY" })
                    }
                    "ALERTS" -> ThresholdAlertLogScreen(runtime.database, rememberLazyListState(), background, card, text, secondary,
                        onBack = { route = "HOME" }, filterPreferences = preferences)
                    "INFO" -> UvirVersionInfoScreen(rememberScrollState(), background, card, text, secondary) { route = "HOME" }
                    "AUTOMATIC" -> UsbPhoneAutomaticPage(state, background, card, text, secondary,
                        onBack = { route = "HOME" }, onStart = { runtime.startAutomatic(it); route = "HOME" },
                        onStop = runtime::stopAutomatic)
                    "ALERT_REGISTRATION" -> UsbPhoneAlertRegistrationPage(state, background, card, text, secondary,
                        onBack = { route = "HOME" }, onStart = { settings, note ->
                            runtime.startAlerts(settings, note); route = "HOME"
                        }, onStop = runtime::stopAlerts)
                    "SETTINGS" -> UsbPhoneSettings(runtime, state, settingsPage, background, card, text, secondary,
                        unit, { unit = it; saveUvirIrradianceUnit(context, it) }, numeric, { numeric = it; saveUvirNumericFormat(context, it) },
                        date, { date = it; saveUvirDateFormat(context, it) }, time, { time = it; saveUvirTimeFormat(context, it) },
                        onBack = { route = "HOME" }, onSettingsSaved = settingsConfirmation::saved)
                    else -> UsbPhoneHome(runtime, state, background, card, text, secondary, track,
                        onHistory = { route = "HISTORY" }, onAlerts = { route = "ALERTS" },
                        onAutomatic = { route = "AUTOMATIC" }, onInfo = { route = "INFO" },
                        onRegisterAlerts = { route = "ALERT_REGISTRATION" },
                        onSettings = { settingsPage = it; route = "SETTINGS" })
                }
            }
        }
    }
}

@Composable
private fun UsbPhoneHome(runtime: UvirUsbPhoneRuntime, state: UsbPhoneState, background: Color, card: Color,
    text: Color, secondary: Color, track: Color, onHistory: () -> Unit, onAlerts: () -> Unit,
    onAutomatic: () -> Unit, onInfo: () -> Unit, onRegisterAlerts: () -> Unit,
    onSettings: (UvirSettingsPage) -> Unit) {
    var biological by rememberSaveable { mutableStateOf(false) }
    var chart by rememberSaveable { mutableStateOf(false) }
    var display by rememberUvirLiveSpectrumDisplay("usb-phone")
    var order by rememberUvirMainIslandOrder()
    val reordering = rememberUvirMainIslandReordering(order)
    val sample = state.sample ?: SensorSample()
    val context = LocalContext.current
    var disconnectedChromeHeight by remember { mutableIntStateOf(0) }
    var disconnectedIslandHeight by remember { mutableIntStateOf(0) }
    val preferences = remember { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }
    var unreadAcquisitions by remember { mutableIntStateOf(preferences.getInt(KEY_UNREAD_ACQUISITION_COUNT, 0)) }
    var unreadAlerts by remember { mutableIntStateOf(preferences.getInt(KEY_UNREAD_ALERT_COUNT, 0)) }
    DisposableEffect(preferences) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
            if (key == KEY_UNREAD_ACQUISITION_COUNT) unreadAcquisitions = prefs.getInt(key, 0)
            if (key == KEY_UNREAD_ALERT_COUNT) unreadAlerts = prefs.getInt(key, 0)
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        onDispose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    val history = remember { mutableStateListOf<LiveSamplePoint>() }
    LaunchedEffect(state.sequence) {
        if (state.sample != null) {
            history.add(LiveSamplePoint(System.currentTimeMillis(), sample))
            while (history.size > 180) history.removeAt(0)
        }
    }
    var manualDialog by remember { mutableStateOf(false) }
    var manualMode by rememberSaveable { mutableStateOf(ManualSaveMode.SINGLE) }
    var note by rememberSaveable { mutableStateOf("") }
    val statusIndicator = uvirStatusIndicator(state.simulation, state.status == UsbPhoneStatus.READY,
        state.status == UsbPhoneStatus.INITIALIZING || state.status == UsbPhoneStatus.PERMISSION,
        state.automaticSession != null || state.alertSession != null || state.busy)
    val pulse = rememberInfiniteTransition(label = "usbStatusPulse")
    val statusAlpha by pulse.animateFloat(1f, .5f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "usbStatusAlpha")
    val alertMetrics = if (state.alertSession != null) loadThresholdAlertSettings(
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)).rules.filter { it.enabled }.map { it.metric } else emptyList()
    if (manualDialog) UvirManualAcquisitionDialog(note, manualMode, state.manualSession, state.manualCount, text, secondary, card,
        onNoteChanged = { note = it }, onModeSelected = { manualMode = it },
        onSave = { runtime.acquire(note, manualMode); manualDialog = false }, onCancel = { manualDialog = false }, onDismissRequest = { manualDialog = false })
    CompositionLocalProvider(LocalUvirLiveReadingAvailable provides state.ready) {
        Scaffold(containerColor = background, floatingActionButton = {
            Box(Modifier.testTag("home_capture_menu")) {
                UvirHomeCaptureMenu(alertActive = state.alertSession != null,
                    onManual = { if (state.ready && !state.busy && !state.measuringReference) manualDialog = true },
                    onAutomatic = onAutomatic, onAlert = onRegisterAlerts,
                    automaticActive = state.automaticSession != null, completedCount = state.automaticCount,
                    alertCompletedCount = state.alertCount,
                    manualEnabled = state.ready && !state.busy && !state.measuringReference,
                    automaticIndicatorColor = uvirSessionIndicatorColor(isSystemInDarkTheme()),
                    alertIndicatorColor = uvirAlertSessionIndicatorColor(isSystemInDarkTheme()),
                    automaticContainerColor = card)
            }
        }) { contentPadding ->
        Column(Modifier.fillMaxSize().padding(contentPadding).padding(horizontal = UvirScreenHorizontalPadding).padding(top = 4.dp)) {
            Column(Modifier.fillMaxWidth().onSizeChanged { disconnectedChromeHeight = it.height }) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                UvirHomeInfoLogo(stringResource(R.string.version_info), onInfo)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f).clickable(indication = null,
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }) { onInfo() }) {
                    Box(Modifier.fillMaxWidth().height(36.dp), contentAlignment = Alignment.CenterStart) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.app_name), fontWeight = FontWeight.Bold, fontSize = 22.sp,
                                modifier = Modifier.alignByBaseline().testTag("home_app_title"))
                            Spacer(Modifier.width(8.dp))
                            Text("V. ${BuildConfig.VERSION_NAME}", color = secondary, fontSize = 12.sp,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false).alignByBaseline().testTag("home_app_version"))
                        }
                    }
                    val status = if (state.simulation) stringResource(R.string.sensor_status_simulated)
                        else stringResource(when (state.status) {
                            UsbPhoneStatus.DETACHED -> R.string.sensor_status_no_sensor
                            UsbPhoneStatus.PERMISSION -> R.string.usb_permission
                            UsbPhoneStatus.INITIALIZING -> R.string.usb_initializing
                            UsbPhoneStatus.READY -> if (state.ready) R.string.usb_ready else R.string.live_chart_waiting
                            UsbPhoneStatus.ERROR -> R.string.usb_error })
                    UvirConnectionLabel(text = status,
                        type = if (state.simulation) ConnectivityIconType.DEBUG else ConnectivityIconType.USB,
                        iconColor = Color(statusIndicator.dot.colorArgb),
                        textColor = if (state.simulation) Color(statusIndicator.dot.colorArgb) else secondary,
                        iconSize = UvirHomeStatusConnectionIconSize,
                        strokeScale = UvirHomeHeaderIconStrokeScale * .6f,
                        modifier = Modifier.fillMaxWidth().testTag("home_sensor_status"),
                        iconModifier = Modifier.testTag("home_sensor_glyph"),
                        textModifier = Modifier.testTag("home_sensor_status_text"),
                        iconAlpha = if (statusIndicator.pulses) statusAlpha else 1f,
                        style = TextStyle(fontSize = 11.sp), softWrap = false)
                }
                Spacer(Modifier.width(UvirHomeHeaderActionGap))
                UvirHomeSettingsDropdown(text, secondary, card, onOpenPage = onSettings, onOpenInfo = onInfo, groups = usbSettingsGroups)
            }
            Spacer(Modifier.height(UvirIslandSpacing))
            Box(Modifier.testTag("home_menu_bar")) {
                UvirHomeMenuBar(pinned = false,
                    viewMode = if (biological) ViewMode.BIOLOGICAL_EFFECTS else ViewMode.IRRADIANCE,
                    showChart = chart, monitoringAlertMetrics = alertMetrics,
                    backgroundColor = background, cardColor = card, primaryText = text, secondaryText = secondary,
                    unreadAcquisitionCount = unreadAcquisitions, unreadAlertCount = unreadAlerts,
                    acquisitionActivityInProgress = state.automaticSession != null,
                    alertActivityInProgress = state.alertSession != null,
                    acquisitionSyncInProgress = false, alertSyncInProgress = false,
                    onViewModeChanged = { biological = it == ViewMode.BIOLOGICAL_EFFECTS },
                    onShowChartChanged = { chart = it },
                    onOpenHistory = { UvirUnreadCounters.clear(preferences, KEY_UNREAD_ACQUISITION_COUNT); onHistory() },
                    onOpenAlertLog = { UvirUnreadCounters.clear(preferences, KEY_UNREAD_ALERT_COUNT); onAlerts() })
            }
            Spacer(Modifier.height(UvirIslandSpacing))
            }
            if (!state.ready && state.status == UsbPhoneStatus.DETACHED) {
                BoxWithConstraints(Modifier.fillMaxWidth().weight(1f).testTag("disconnected_viewport")) {
                    val clearance = 84.dp
                    val safeContentHeight = (maxHeight - clearance).coerceAtLeast(0.dp)
                    val clearancePx = with(LocalDensity.current) { clearance.toPx() }
                    val centeredTop = (constraints.maxHeight - disconnectedChromeHeight - disconnectedIslandHeight) / 2f
                    val compact = disconnectedIslandHeight > 0 &&
                        (centeredTop < 0f || centeredTop + disconnectedIslandHeight > constraints.maxHeight - clearancePx)
                    val island: @Composable () -> Unit = {
                        Box(Modifier.fillMaxWidth().onSizeChanged { disconnectedIslandHeight = it.height }
                            .testTag("disconnected_island")) {
                            UvirDisconnectedSensorIsland(card, text, secondary, onAddSensor = {}, showAddSensor = false)
                        }
                    }
                    if (compact) {
                        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                            Box(Modifier.fillMaxWidth().heightIn(min = safeContentHeight),
                                contentAlignment = Alignment.Center) { island() }
                            Spacer(Modifier.height(clearance))
                        }
                    } else {
                        Box(Modifier.fillMaxSize().offset { IntOffset(0, -disconnectedChromeHeight / 2) },
                            contentAlignment = Alignment.Center) { island() }
                    }
                }
            } else LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 100.dp),
                verticalArrangement = Arrangement.spacedBy(UvirIslandSpacing)) {
                if (!state.ready) item {
                    SettingsStaticIsland(title = stringResource(R.string.usb_sensor_name), titleIcon = ConnectivityIconType.SENSOR, containerColor = card, titleColor = text) {
                        Text(stringResource(if (state.status == UsbPhoneStatus.READY) R.string.live_chart_waiting else R.string.usb_connect), color = secondary)
                        Text(stringResource(R.string.usb_hardware_pending), color = secondary, fontSize = 12.sp)
                        if (state.error != null) Text(state.error, color = secondary, fontSize = 12.sp)
                        UsbPhoneAction(R.string.usb_retry, onClick = runtime::retry)
                    }
                }
                else if (biological) item { BiologicalEffectsContent(sample, true, {}, chart, history.toList(),
                    cardColor = card, primaryText = text, secondaryText = secondary, collapsible = false) }
                else item {
                    UvirIslandColumns(spacing = UvirIslandSpacing) {
                    order.forEach { group -> key(group) {
                    Box(Modifier.fillMaxWidth().uvirMainIslandPlacement(reordering)) {
                        SensorGroupContent(group, true, {}, chart, history.toList(), sample,
                            sample.uvc + sample.uvb + sample.uva,
                            sample.violetto + sample.blu + sample.verde + sample.giallo + sample.arancione + sample.rosso,
                            sample.f8 + sample.nir, sample.violetto + sample.blu,
                            cardColor = card, primaryText = text, secondaryText = secondary, trackColor = track,
                            display = display, onDisplayChange = { display = it }, collapsible = false,
                            reorderControls = { title -> UvirMainIslandMoveControls(group, title, text,
                                if (order.indexOf(group) > 0) ({ order = moveUvirMainIsland(order, group, -1) }) else null,
                                if (order.indexOf(group) < order.lastIndex) ({ order = moveUvirMainIsland(order, group, 1) }) else null) })
                    }
                    } }
                    }
                }
            }
        }
        }
    }
}

@Composable
internal fun UsbPhoneAction(label: Int, enabled: Boolean = true, modifier: Modifier = Modifier,
    onClick: () -> Unit) {
    val text = if (isSystemInDarkTheme()) Color.White else Color(0xff101418)
    val destructive = label == R.string.stop
    val primary = label == R.string.start
    CompositionLocalProvider(LocalUvirSettingsActionButtons provides true) {
        OutlinedButton(onClick = onClick, enabled = enabled, modifier = modifier,
            colors = if (destructive) uvirDestructiveOutlinedButtonColors() else if (primary) uvirPrimaryOutlinedButtonColors() else uvirOutlinedActionColors(text),
            border = if (destructive) uvirDestructiveOutlinedButtonBorder(enabled, text) else if (primary) uvirPrimaryOutlinedButtonBorder(enabled, text) else uvirOutlinedActionBorder(enabled, text)) {
            UvirLabeledButtonContent(text = stringResource(label).uppercase(java.util.Locale.getDefault())) {
                if (label == R.string.usb_retry) ConnectivitySectionIcon(ConnectivityIconType.USB, Modifier.size(20.dp), LocalContentColor.current)
                else UvirButtonGlyphIcon(if (destructive) UvirButtonGlyph.STOP else UvirButtonGlyph.PLAY)
            }
        }
    }
}

@Composable
private fun UsbPhoneAutomaticPage(state: UsbPhoneState, background: Color, card: Color, text: Color, secondary: Color,
    onBack: () -> Unit, onStart: (AutomaticAcquisitionRequest) -> Unit, onStop: () -> Unit) {
    val context = LocalContext.current
    val preferences = remember { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }
    var interval by rememberSaveable { mutableStateOf(preferences.getString("usb_auto_interval", "10").orEmpty()) }
    var delayText by rememberSaveable { mutableStateOf(preferences.getString("usb_auto_delay", "0").orEmpty()) }
    var duration by rememberSaveable { mutableStateOf(preferences.getString("usb_auto_duration", "0").orEmpty()) }
    var limit by rememberSaveable { mutableStateOf(preferences.getString("usb_auto_limit", "0").orEmpty()) }
    var note by rememberSaveable { mutableStateOf(preferences.getString("usb_auto_note", "").orEmpty()) }
    var conditional by rememberSaveable { mutableStateOf(preferences.getBoolean("usb_auto_conditional", false)) }
    var match by rememberSaveable { mutableStateOf(runCatching {
        AcquisitionConditionMatch.valueOf(preferences.getString("usb_auto_match", AcquisitionConditionMatch.ANY.name)!!)
    }.getOrDefault(AcquisitionConditionMatch.ANY)) }
    var action by rememberSaveable { mutableStateOf(runCatching {
        AcquisitionConditionAction.valueOf(preferences.getString("usb_auto_action", AcquisitionConditionAction.ACQUIRE.name)!!)
    }.getOrDefault(AcquisitionConditionAction.ACQUIRE)) }
    LaunchedEffect(interval, delayText, duration, limit, note, conditional, match, action) {
        preferences.edit().putString("usb_auto_interval", interval).putString("usb_auto_delay", delayText)
            .putString("usb_auto_duration", duration).putString("usb_auto_limit", limit).putString("usb_auto_note", note)
            .putBoolean("usb_auto_conditional", conditional).putString("usb_auto_match", match.name)
            .putString("usb_auto_action", action.name).apply()
    }
    var rules by remember { mutableStateOf(loadAcquisitionConditions(preferences)) }
    var showRules by remember { mutableStateOf(false) }
    if (showRules) UvirAcquisitionConditionsDialog(rules, card, text, secondary, state.sample,
        onSave = { rules = it; saveAcquisitionConditions(preferences, it); showRules = false }, onDismissRequest = { showRules = false })
    val valid = (interval.toLongOrNull() ?: 0) in 1..86400 && listOf(delayText, duration, limit).all { (it.toLongOrNull() ?: -1) in 0..1000000 }
    val active = state.automaticSession != null
    var showStop by remember { mutableStateOf(false) }
    if (showStop) UvirStopAutomaticDialog(card, text, secondary, true,
        onStop = { onStop(); showStop = false }, onContinue = { showStop = false }, onDismissRequest = { showStop = false })
    UvirFullScreenPage(onDismissRequest = onBack, title = { UvirMenuTitle(stringResource(R.string.automatic_acquisition_title)) },
        containerColor = background, contentColor = text,
        floatingActionButton = {
            Box(Modifier.testTag("usb_automatic_action")) {
                UvirAutomaticAcquisitionFloatingAction(active, state.automaticCount,
                    active || (valid && state.ready && state.alertSession == null && (!conditional || rules.any { it.enabled })), false) {
                    if (active) showStop = true
                    else onStart(AutomaticAcquisitionRequest(interval.toLong(), note, delayText.toLong() > 0, delayText.toLong(),
                        duration.toLong() > 0, duration.toLong(), limit.toInt() > 0, limit.toInt(),
                        if (conditional) conditionalPlanFromRules(rules, match, action) else null))
                }
            }
        }, text = {
            CompositionLocalProvider(LocalUvirSettingsActionButtons provides true) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(UvirIslandSpacing)) {
                SettingsStaticIsland(title = stringResource(R.string.automatic_acquisition_title), titleIcon = ConnectivityIconType.SAMPLING, containerColor = card, titleColor = text) {
                    SettingsPageDescription(stringResource(R.string.usb_phone_note), secondary)
                    for ((label, value, setter) in listOf(
                        Triple(R.string.usb_interval, interval, { s: String -> interval = s }),
                        Triple(R.string.scheduled_start, delayText, { s: String -> delayText = s }),
                        Triple(R.string.scheduled_end, duration, { s: String -> duration = s }),
                        Triple(R.string.number_of_acquisitions, limit, { s: String -> limit = s }))) {
                        NumberField(value, setter, stringResource(label),
                            Modifier.fillMaxWidth().testTag("usb_auto_field_$label"), maxChars = 7, enabled = !active)
                    }
                    UvirLimitedNoteField(value = note, onValueChange = { note = it }, enabled = !active, label = stringResource(R.string.optional_note), secondaryText = secondary)
                    CheckSettingRow(conditional, { conditional = it }, stringResource(R.string.conditional_acquisition),
                        compact = true, enabled = !active)
                    if (conditional) {
                        UsbPhoneAction(R.string.conditional_configure, !active) { showRules = true }
                        Column(verticalArrangement = Arrangement.spacedBy(UvirSettingsChoiceSpacing)) {
                        AcquisitionConditionMatch.entries.forEach { choice ->
                            SettingsRadioChoiceRow(match == choice, text, secondary, enabled = !active, onClick = { match = choice }) {
                                Text(stringResource(when (choice) {
                                    AcquisitionConditionMatch.ANY -> R.string.conditional_any
                                    AcquisitionConditionMatch.ALL -> R.string.conditional_all
                                    AcquisitionConditionMatch.NONE -> R.string.conditional_none
                                }), fontSize = 14.sp)
                            }
                        }
                        }
                        SettingsGroupDivider(secondary)
                        Column(verticalArrangement = Arrangement.spacedBy(UvirSettingsChoiceSpacing)) {
                        AcquisitionConditionAction.entries.forEach { choice ->
                            SettingsRadioChoiceRow(action == choice, text, secondary, enabled = !active, onClick = { action = choice }) {
                                Text(stringResource(when (choice) {
                                    AcquisitionConditionAction.START -> R.string.conditional_start
                                    AcquisitionConditionAction.STOP -> R.string.conditional_stop
                                    AcquisitionConditionAction.ACQUIRE -> R.string.conditional_record
                                }), fontSize = 14.sp)
                            }
                        }
                        }
                    }
                    Spacer(Modifier.height(72.dp))
                }
            }
            }
        })
}
