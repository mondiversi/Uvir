package me.mondiversi.uvir

import android.Manifest
import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
import android.content.pm.PackageManager
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.graphics.BitmapFactory
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.view.WindowCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference
import java.io.File
import org.json.JSONObject
import kotlin.math.roundToInt
import kotlin.random.Random

@Composable
internal fun LiveScreen(
    database: UvirDatabaseHelper,
    measurement: SensorSample,
    liveReady: Boolean,
    liveListState: LazyListState,
    automaticListState: LazyListState,
    versionInfoScrollState: ScrollState,
    parametersScrollState: ScrollState,

    viewMode: ViewMode,
    onViewModeChanged: (ViewMode) -> Unit,

    samplesPerMeasurement: Int,
    sampleSpacingMs: Long,
    discardExtremes: Boolean,
    useFakeSensorData: Boolean,
    usbSensorState: UvirUsbSensorState,
    wirelessSensorState: UvirWirelessSensorState,
    sensorProfiles: List<UvirSensorProfile>,
    selectedSensorDeviceId: String,
    sensorSelectionEnabled: Boolean,
    onSensorSelected: (String) -> Unit,
    onUseFakeSensorDataChanged: (Boolean) -> Unit,
    sensorConnectionMode: SensorConnectionMode,
    onSensorConnectionModeChanged:
        (SensorConnectionMode) -> Unit,
    onRequestBluetoothPermission:
        () -> Unit,
    onConfigureSensorWifi:
        suspend (String, String) -> Boolean,
    onConfigureSensorInternet:
        suspend (SensorInternetConfiguration) -> Boolean,
    onApplySensorRadioSettings:
        suspend (SensorRadioSettings) -> Boolean,
    sensorParameters: SensorParameters,
    onApplySensorParameters:
        suspend (SensorParameters) -> Boolean,
    onTestSensorStatusLed:
        suspend () -> Boolean,
    onTestSensorStatusBuzzer:
        suspend () -> Boolean,
    onStartDebugPerformance:
        suspend (String) -> Boolean,
    onStopDebugPerformance:
        suspend () -> Boolean,
    onDiagnosticProbe:
        suspend (String, SensorConnectionMode) -> UvirDiagnosticProbe,
    onPowerOffSensor:
        suspend () -> Boolean,
    onDisassociateSensor:
        suspend () -> Boolean,
    onRestoreApplicationDefaults:
        suspend () -> Boolean,
    onRestoreSensorDefaults:
        suspend () -> Boolean,
    sensorCalibrationSettings: SensorCalibrationSettings,
    onApplySensorCalibration:
        suspend (SensorCalibrationSettings) -> Boolean,
    currentWifiSsid: String?,
    onRequestCurrentWifiSsid:
        () -> Unit,
    appLanguage: AppLanguage,
    numericFormat: UvirNumericFormat,
    onAppLanguageChanged:
        (AppLanguage) -> Unit,
    onCommitAppLanguage:
        () -> Unit,
    onDiscardAppLanguage:
        () -> Unit,
    onApplyNumericFormat:
        (UvirNumericFormat) -> Unit,
    onApplyAcquisitionParameters:
        suspend (AcquisitionParameters) -> Boolean,

    thresholdAlertSettings:
        ThresholdAlertSettings,
    thresholdAlertSessionNote: String,
    thresholdAlertSessionActive: Boolean,
    thresholdAlertViolations:
        List<ThresholdAlertViolation>,
    onApplyThresholdAlertSettings:
        (ThresholdAlertSettings) -> Unit,
    onSaveThresholdAlertPreferences: suspend (ThresholdAlertSettings) -> Boolean,
    onStartThresholdAlertSession:
        (ThresholdAlertSettings, String) -> Unit,
    onPreviewThresholdAlertSound:
        (ThresholdAlertSound, Int) -> Unit,

    autoEnabled: Boolean,
    automaticStopInProgress: Boolean,
    autoIntervalSeconds: Long,
    autoNote: String,
    autoUseStartDelay: Boolean,
    autoStartDelaySeconds: Long,
    autoUseDuration: Boolean,
    autoDurationSeconds: Long,
    autoLimitEnabled: Boolean,
    autoMaxCount: Int,
    autoCompletedCount: Int,
    autoNextSaveMs: Long,
    autoConditionalPlan: ConditionalAcquisitionPlan?,
    autoConditionalWaiting: Boolean,
    autoConditionalEnabled: Boolean,
    autoConditionalMatch: AcquisitionConditionMatch,
    autoConditionalAction: AcquisitionConditionAction,
    autoConditionalRules: List<ThresholdAlertRule>,
    onSaveAcquisitionConditions: (List<ThresholdAlertRule>) -> Unit,

    onStartAutomaticAcquisition:
        (AutomaticAcquisitionRequest) -> Unit,

    onStopAutomaticAcquisition:
        () -> Unit,

    onResetAllCounters:
        () -> Unit,

    onAcquisitionSaved:
        () -> Unit,

    unreadAcquisitionCount: Int,
    unreadAlertCount: Int,
    sensorSyncInProgress: Boolean,
    acquisitionSyncInProgress: Boolean,
    alertSyncInProgress: Boolean,
    onAlertLogViewed: () -> Unit,
    onAlertLogVisibilityChanged: (Boolean) -> Unit,
    offlineDisconnectionNotice:
        UvirOfflineDisconnectionNotice?,

    backgroundColor: Color,
    cardColor: Color,
    primaryText: Color,
    secondaryText: Color,
    trackColor: Color,

    onOpenHistory: () -> Unit
) {

    val context =
        LocalContext.current

    val selectedSensorInfo =
        when (sensorConnectionMode) {
            SensorConnectionMode.USB -> usbSensorState.runtimeInfo
            SensorConnectionMode.WIFI,
            SensorConnectionMode.BLUETOOTH,
            SensorConnectionMode.INTERNET -> wirelessSensorState.runtimeInfo
        }

    val selectedSensorConnected =
        !useFakeSensorData &&
            when (sensorConnectionMode) {
                SensorConnectionMode.USB ->
                    usbSensorState.status ==
                        UsbSensorConnectionStatus.CONNECTED &&
                        usbSensorState.appConnectionConfirmed

                SensorConnectionMode.WIFI,
                SensorConnectionMode.BLUETOOTH,
                SensorConnectionMode.INTERNET ->
                    wirelessSensorState.status ==
                        WirelessSensorConnectionStatus.CONNECTED &&
                        wirelessSensorState.mode == sensorConnectionMode &&
                        wirelessSensorState.appConnectionConfirmed
            }
    val sensorOperationsAvailable =
        selectedSensorConnected || useFakeSensorData
    val sensorCalibrationSupported =
        firmwareSupportsSensorCalibration(selectedSensorInfo.firmwareVersion)
    val sensorFirmwareCurrent =
        useFakeSensorData ||
            firmwareIsCurrentForApp(selectedSensorInfo.firmwareVersion)

    val settingsApplyScope =
        rememberCoroutineScope()

    var settingsApplyInProgress by remember {
        mutableStateOf(false)
    }

    var wifiConfigurationInProgress by remember {
        mutableStateOf(false)
    }

    val sensorCredentials =
        if (usbSensorState.credentials.isProvisioned) {
            usbSensorState.credentials
        } else {
            UvirSensorCredentialStore.load(
                context.applicationContext
            )
        }

    val selectedSensorProfile =
        sensorProfiles.firstOrNull {
            it.hardwareUid.equals(selectedSensorDeviceId, ignoreCase = true)
        } ?: if (
            selectedSensorDeviceId.isBlank() &&
            sensorCredentials.isProvisioned
        ) {
            sensorProfiles.firstOrNull {
                it.hardwareUid.equals(sensorCredentials.deviceId, ignoreCase = true)
            }
        } else {
            null
        }
    val sensorProfileHardwareUid =
        selectedSensorProfile?.hardwareUid
            ?.takeIf { it.isNotBlank() }
            ?: selectedSensorDeviceId
    val settingsSaveQueue = remember(sensorProfileHardwareUid) {
        UvirSettingsSaveQueue(
            scope = settingsApplyScope,
            onBusy = { settingsApplyInProgress = it },
            onFailure = { showUvirBottomMessage(context,
                context.getString(R.string.sensor_radio_setting_usb_required)) }
        )
    }
    DisposableEffect(settingsSaveQueue) {
        onDispose { settingsSaveQueue.cancel() }
    }
    val settingsFocusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val sensorDisplayName =
        selectedSensorProfile?.displayName
            ?.takeIf { it.isNotBlank() }
            ?: sensorProfileHardwareUid.takeIf { it.isNotBlank() }
            ?: "—"
    var sensorNameText by rememberSaveable(sensorProfileHardwareUid) {
        mutableStateOf(sensorDisplayName)
    }
    var lastLoadedSensorName by remember(sensorProfileHardwareUid) { mutableStateOf(sensorDisplayName) }
    LaunchedEffect(selectedSensorProfile?.displayName, sensorProfileHardwareUid) {
        sensorNameText = refreshUneditedSetting(sensorNameText, lastLoadedSensorName, sensorDisplayName)
        lastLoadedSensorName = sensorDisplayName
    }

    var sensorWifiSsid by rememberSaveable(sensorCredentials.deviceId) {
        mutableStateOf(sensorCredentials.wifiSsid)
    }

    var sensorWifiPassword by rememberSaveable(sensorCredentials.deviceId) {
        mutableStateOf(sensorCredentials.wifiPassword)
    }

    var sensorInternetEnabled by rememberSaveable(sensorCredentials.deviceId) {
        mutableStateOf(sensorCredentials.internetEnabled)
    }
    var sensorInternetUsePrimaryWifi by rememberSaveable(sensorCredentials.deviceId) {
        mutableStateOf(sensorCredentials.internetUsePrimaryWifi)
    }
    var sensorInternetWifiSsid by rememberSaveable(sensorCredentials.deviceId) {
        mutableStateOf(sensorCredentials.internetWifiSsid)
    }
    var sensorInternetWifiPassword by rememberSaveable(sensorCredentials.deviceId) {
        mutableStateOf(sensorCredentials.internetWifiPassword)
    }
    var sensorInternetRelayHost by rememberSaveable(sensorCredentials.deviceId) {
        mutableStateOf(sensorCredentials.internetRelayHost)
    }
    var sensorInternetRelayPort by rememberSaveable(sensorCredentials.deviceId) {
        mutableStateOf(sensorCredentials.internetRelayPort.toString())
    }
    var sensorInternetMqttUsername by rememberSaveable(sensorCredentials.deviceId) {
        mutableStateOf(sensorCredentials.internetMqttUsername)
    }
    var sensorInternetMqttPassword by rememberSaveable(sensorCredentials.deviceId) {
        mutableStateOf(sensorCredentials.internetMqttPassword)
    }
    var appliedSensorInternetConfiguration by remember(sensorCredentials.deviceId) {
        mutableStateOf(
            SensorInternetConfiguration(
                enabled = sensorCredentials.internetEnabled,
                usePrimaryWifi = sensorCredentials.internetUsePrimaryWifi,
                wifiSsid = sensorCredentials.internetWifiSsid,
                wifiPassword = sensorCredentials.internetWifiPassword,
                relayHost = sensorCredentials.internetRelayHost,
                relayPort = sensorCredentials.internetRelayPort,
                mqttUsername = sensorCredentials.internetMqttUsername,
                mqttPassword = sensorCredentials.internetMqttPassword
            )
        )
    }

    var autonomousRecordingEnabled by rememberSaveable(sensorProfileHardwareUid) {
        mutableStateOf(sensorParameters.autonomousRecordingEnabled)
    }

    var automaticShutdownEnabled by rememberSaveable(sensorProfileHardwareUid) {
        mutableStateOf(sensorParameters.automaticShutdownEnabled)
    }

    var automaticShutdownHoursText by rememberSaveable(sensorProfileHardwareUid) {
        mutableStateOf((sensorParameters.automaticShutdownSeconds / 3_600).toString())
    }

    var automaticShutdownMinutesText by rememberSaveable(sensorProfileHardwareUid) {
        mutableStateOf(
            ((sensorParameters.automaticShutdownSeconds % 3_600) / 60).toString()
        )
    }

    var automaticShutdownSecondsText by rememberSaveable(sensorProfileHardwareUid) {
        mutableStateOf((sensorParameters.automaticShutdownSeconds % 60).toString())
    }

    var statusLedEnabled by rememberSaveable(sensorProfileHardwareUid) {
        mutableStateOf(sensorParameters.statusLedEnabled)
    }

    var statusLedBrightness by rememberSaveable(sensorProfileHardwareUid) {
        mutableFloatStateOf(sensorParameters.statusLedBrightness.toFloat())
    }

    var statusBuzzerEnabled by rememberSaveable(sensorProfileHardwareUid) {
        mutableStateOf(sensorParameters.statusBuzzerEnabled)
    }

    var statusBuzzerVolume by rememberSaveable(sensorProfileHardwareUid) {
        mutableFloatStateOf(sensorParameters.statusBuzzerVolume.toFloat())
    }

    var visibleCalibrationFactorText by
        rememberSaveable(sensorProfileHardwareUid) {
            mutableStateOf(sensorCalibrationSettings.visibleFactor.toString())
        }

    var uvCalibrationFactorText by
        rememberSaveable(sensorProfileHardwareUid) {
            mutableStateOf(sensorCalibrationSettings.uvFactor.toString())
        }

    var lastLoadedParameters by remember(sensorProfileHardwareUid) { mutableStateOf(sensorParameters) }
    LaunchedEffect(sensorParameters, sensorProfileHardwareUid) {
        autonomousRecordingEnabled = refreshUneditedSetting(autonomousRecordingEnabled, lastLoadedParameters.autonomousRecordingEnabled, sensorParameters.autonomousRecordingEnabled)
        automaticShutdownEnabled = refreshUneditedSetting(automaticShutdownEnabled, lastLoadedParameters.automaticShutdownEnabled, sensorParameters.automaticShutdownEnabled)
        statusLedEnabled = refreshUneditedSetting(statusLedEnabled, lastLoadedParameters.statusLedEnabled, sensorParameters.statusLedEnabled)
        statusBuzzerEnabled = refreshUneditedSetting(statusBuzzerEnabled, lastLoadedParameters.statusBuzzerEnabled, sensorParameters.statusBuzzerEnabled)
        statusLedBrightness = refreshUneditedSetting(statusLedBrightness, lastLoadedParameters.statusLedBrightness.toFloat(), sensorParameters.statusLedBrightness.toFloat())
        statusBuzzerVolume = refreshUneditedSetting(statusBuzzerVolume, lastLoadedParameters.statusBuzzerVolume.toFloat(), sensorParameters.statusBuzzerVolume.toFloat())
        automaticShutdownHoursText = refreshUneditedSetting(automaticShutdownHoursText, (lastLoadedParameters.automaticShutdownSeconds / 3_600).toString(), (sensorParameters.automaticShutdownSeconds / 3_600).toString())
        automaticShutdownMinutesText = refreshUneditedSetting(automaticShutdownMinutesText, ((lastLoadedParameters.automaticShutdownSeconds % 3_600) / 60).toString(), ((sensorParameters.automaticShutdownSeconds % 3_600) / 60).toString())
        automaticShutdownSecondsText = refreshUneditedSetting(automaticShutdownSecondsText, (lastLoadedParameters.automaticShutdownSeconds % 60).toString(), (sensorParameters.automaticShutdownSeconds % 60).toString())
        lastLoadedParameters = sensorParameters
    }
    var lastLoadedCalibration by remember(sensorProfileHardwareUid) { mutableStateOf(sensorCalibrationSettings) }
    LaunchedEffect(sensorCalibrationSettings, sensorProfileHardwareUid) {
        visibleCalibrationFactorText = refreshUneditedSetting(visibleCalibrationFactorText,
            lastLoadedCalibration.visibleFactor.toString(), sensorCalibrationSettings.visibleFactor.toString())
        uvCalibrationFactorText = refreshUneditedSetting(uvCalibrationFactorText,
            lastLoadedCalibration.uvFactor.toString(), sensorCalibrationSettings.uvFactor.toString())
        lastLoadedCalibration = sensorCalibrationSettings
    }

    var sensorWifiRadioEnabled by
        rememberSaveable(sensorCredentials.deviceId) {
            mutableStateOf(sensorCredentials.wifiEnabled)
        }

    var appliedSensorWifiRadioEnabled by
        rememberSaveable(sensorCredentials.deviceId) {
            mutableStateOf(sensorCredentials.wifiEnabled)
        }

    var sensorBluetoothRadioEnabled by
        rememberSaveable(sensorCredentials.deviceId) {
            mutableStateOf(sensorCredentials.bluetoothEnabled)
        }

    var appliedSensorBluetoothRadioEnabled by
        rememberSaveable(sensorCredentials.deviceId) {
            mutableStateOf(sensorCredentials.bluetoothEnabled)
        }

    var lastLoadedCredentials by remember { mutableStateOf(sensorCredentials) }
    LaunchedEffect(
        sensorCredentials.deviceId,
        sensorCredentials.wifiSsid,
        sensorCredentials.wifiPassword,
        sensorCredentials.wifiEnabled,
        sensorCredentials.bluetoothEnabled,
        sensorCredentials.internetEnabled,
        sensorCredentials.internetUsePrimaryWifi,
        sensorCredentials.internetWifiSsid,
        sensorCredentials.internetWifiPassword,
        sensorCredentials.internetRelayHost,
        sensorCredentials.internetRelayPort,
        sensorCredentials.internetMqttUsername,
        sensorCredentials.internetMqttPassword
    ) {
        if (sensorCredentials.deviceId != lastLoadedCredentials.deviceId ||
            sensorWifiSsid == lastLoadedCredentials.wifiSsid) {
        sensorWifiSsid = sensorCredentials.wifiSsid
        }
        if (sensorCredentials.deviceId != lastLoadedCredentials.deviceId ||
            sensorWifiPassword == lastLoadedCredentials.wifiPassword) {
        sensorWifiPassword = sensorCredentials.wifiPassword
        }
        appliedSensorWifiRadioEnabled = sensorCredentials.wifiEnabled
        appliedSensorBluetoothRadioEnabled = sensorCredentials.bluetoothEnabled
        if (sensorCredentials.deviceId != lastLoadedCredentials.deviceId ||
            sensorWifiRadioEnabled == lastLoadedCredentials.wifiEnabled) {
        sensorWifiRadioEnabled = sensorCredentials.wifiEnabled
        }
        if (sensorCredentials.deviceId != lastLoadedCredentials.deviceId ||
            sensorBluetoothRadioEnabled == lastLoadedCredentials.bluetoothEnabled) {
        sensorBluetoothRadioEnabled = sensorCredentials.bluetoothEnabled
        }
        if (sensorCredentials.deviceId != lastLoadedCredentials.deviceId ||
            sensorInternetEnabled == lastLoadedCredentials.internetEnabled) {
        sensorInternetEnabled = sensorCredentials.internetEnabled
        }
        if (sensorCredentials.deviceId != lastLoadedCredentials.deviceId ||
            sensorInternetUsePrimaryWifi == lastLoadedCredentials.internetUsePrimaryWifi) {
        sensorInternetUsePrimaryWifi = sensorCredentials.internetUsePrimaryWifi
        }
        if (sensorCredentials.deviceId != lastLoadedCredentials.deviceId ||
            sensorInternetWifiSsid == lastLoadedCredentials.internetWifiSsid) {
        sensorInternetWifiSsid = sensorCredentials.internetWifiSsid
        }
        if (sensorCredentials.deviceId != lastLoadedCredentials.deviceId ||
            sensorInternetWifiPassword == lastLoadedCredentials.internetWifiPassword) {
        sensorInternetWifiPassword = sensorCredentials.internetWifiPassword
        }
        if (sensorCredentials.deviceId != lastLoadedCredentials.deviceId ||
            sensorInternetRelayHost == lastLoadedCredentials.internetRelayHost) {
        sensorInternetRelayHost = sensorCredentials.internetRelayHost
        }
        if (sensorCredentials.deviceId != lastLoadedCredentials.deviceId ||
            sensorInternetRelayPort == lastLoadedCredentials.internetRelayPort.toString()) {
            sensorInternetRelayPort = sensorCredentials.internetRelayPort.toString()
        }
        if (sensorCredentials.deviceId != lastLoadedCredentials.deviceId ||
            sensorInternetMqttUsername == lastLoadedCredentials.internetMqttUsername) {
        sensorInternetMqttUsername = sensorCredentials.internetMqttUsername
        }
        if (sensorCredentials.deviceId != lastLoadedCredentials.deviceId ||
            sensorInternetMqttPassword == lastLoadedCredentials.internetMqttPassword) {
        sensorInternetMqttPassword = sensorCredentials.internetMqttPassword
        }
        appliedSensorInternetConfiguration =
            SensorInternetConfiguration(
                enabled = sensorCredentials.internetEnabled,
                usePrimaryWifi = sensorCredentials.internetUsePrimaryWifi,
                wifiSsid = sensorCredentials.internetWifiSsid,
                wifiPassword = sensorCredentials.internetWifiPassword,
                relayHost = sensorCredentials.internetRelayHost,
                relayPort = sensorCredentials.internetRelayPort,
                mqttUsername = sensorCredentials.internetMqttUsername,
                mqttPassword = sensorCredentials.internetMqttPassword
            )
        lastLoadedCredentials = sensorCredentials
    }

    LaunchedEffect(
        currentWifiSsid,
        sensorCredentials.wifiSsid
    ) {
        if (
            sensorCredentials.wifiSsid.isBlank() &&
            sensorWifiSsid.isBlank() &&
            !currentWifiSsid.isNullOrBlank()
        ) {
            sensorWifiSsid = currentWifiSsid
        }
    }

    val groups = remember {
        listOf(
            SensorGroup.UV,
            SensorGroup.VISIBLE,
            SensorGroup.NIR
        )
    }

    val expandedStates = remember {
        mutableStateMapOf<
                SensorGroup,
                Boolean
                >().apply {

            SensorGroup.values()
                .forEach { group ->

                    this[group] =
                        loadExpandedState(
                            context,
                            group
                        )
                }
        }
    }

    var showLiveChart by rememberSaveable {
        mutableStateOf(loadLiveChartMode(context))
    }

    val liveChartHistory = remember {
        mutableStateListOf<LiveSamplePoint>()
    }

    LaunchedEffect(
        measurement,
        liveReady
    ) {
        if (!liveReady) {
            liveChartHistory.clear()
            return@LaunchedEffect
        }

        val now = System.currentTimeMillis()
        liveChartHistory.add(
            LiveSamplePoint(
                timestamp = now,
                sample = measurement
            )
        )

        val oldestAllowed =
            now - LIVE_CHART_WINDOW_MILLIS

        while (
            liveChartHistory.isNotEmpty() &&
            (
                liveChartHistory.first().timestamp < oldestAllowed ||
                liveChartHistory.size > LIVE_CHART_MAX_POINTS
            )
        ) {
            liveChartHistory.removeAt(0)
        }
    }

    var showSaveDialog by rememberSaveable {
        mutableStateOf(false)
    }

    var manualAcquisitionPulseId by remember {
        mutableIntStateOf(0)
    }
    var manualAcquisitionBadgePulseActive by remember {
        mutableStateOf(false)
    }

    LaunchedEffect(manualAcquisitionPulseId) {
        if (manualAcquisitionPulseId <= 0) {
            return@LaunchedEffect
        }
        manualAcquisitionBadgePulseActive = true
        delay(1_400L)
        manualAcquisitionBadgePulseActive = false
    }

    var showAutomaticDialog by rememberSaveable {
        mutableStateOf(false)
    }

    var showStopConfirmation by rememberSaveable {
        mutableStateOf(false)
    }

    var showStopAllAlertsConfirmation by rememberSaveable {
        mutableStateOf(false)
    }

    var showStartAllAlertsConfirmation by rememberSaveable {
        mutableStateOf(false)
    }
    var pendingAlertSessionNote by rememberSaveable {
        mutableStateOf(thresholdAlertSessionNote)
    }

    var showParametersDialog by rememberSaveable {
        mutableStateOf(false)
    }

    var selectedSettingsPage by rememberSaveable {
        mutableStateOf<UvirSettingsPage?>(null)
    }

    var showCounterResetConfirmation by rememberSaveable {
        mutableStateOf(false)
    }

    var showRestoreDefaultsConfirmation by rememberSaveable {
        mutableStateOf(false)
    }

    var showVersionInfoDialog by rememberSaveable {
        mutableStateOf(false)
    }

    var showSensorSourceDialog by rememberSaveable {
        mutableStateOf(false)
    }

    var showSensorInfoDialog by rememberSaveable {
        mutableStateOf(false)
    }

    var showSensorPowerOffConfirmation by rememberSaveable {
        mutableStateOf(false)
    }

    var sensorPowerOffInProgress by rememberSaveable {
        mutableStateOf(false)
    }

    var showFirmwareUpdateRequiredDialog by rememberSaveable {
        mutableStateOf(false)
    }

    val manualPreferences =
        remember(context) {
            context.getSharedPreferences(
                PREFS_NAME,
                Context.MODE_PRIVATE
            )
        }

    var note by rememberSaveable {
        mutableStateOf(manualPreferences.getString(KEY_MANUAL_ACQUISITION_NOTE, "").orEmpty())
    }
    LaunchedEffect(note) {
        manualPreferences.edit().putString(KEY_MANUAL_ACQUISITION_NOTE, note).apply()
    }

    var manualSaveModeValue by rememberSaveable {
        mutableStateOf(
            ManualSaveMode.SINGLE.storedValue
        )
    }

    var recentManualSessionId by rememberSaveable {
        mutableStateOf<Long?>(null)
    }

    var recentManualSessionCount by rememberSaveable {
        mutableIntStateOf(0)
    }

    // -------------------------------------------------
    // AUTOMATIC ACQUISITION DIALOG STATE
    // -------------------------------------------------

    var timerHours by rememberSaveable {
        mutableStateOf("0")
    }

    var timerMinutes by rememberSaveable {
        mutableStateOf("1")
    }

    var timerSeconds by rememberSaveable {
        mutableStateOf("0")
    }

    var timerNote by rememberSaveable {
        mutableStateOf("")
    }

    var conditionalEnabled by rememberSaveable { mutableStateOf(false) }
    var showConditionalRulesEditor by rememberSaveable { mutableStateOf(false) }
    val conditionalConfiguredRulesAvailable = autoConditionalRules.any { it.enabled }
    // The card may be expanded without criteria. Only a configured plan is
    // included at start; opening the card never changes the sensor's runtime.
    var conditionalMatch by rememberSaveable { mutableStateOf(AcquisitionConditionMatch.ANY) }
    var conditionalAction by rememberSaveable { mutableStateOf(AcquisitionConditionAction.ACQUIRE) }

    var useStartDelay by rememberSaveable {
        mutableStateOf(false)
    }

    var startDelayHoursText by rememberSaveable {
        mutableStateOf("0")
    }

    var startDelayMinutesText by rememberSaveable {
        mutableStateOf("5")
    }

    var startDelaySecondsText by rememberSaveable {
        mutableStateOf("0")
    }

    var useDuration by rememberSaveable {
        mutableStateOf(false)
    }

    var durationHoursText by rememberSaveable {
        mutableStateOf("1")
    }

    var durationMinutesText by rememberSaveable {
        mutableStateOf("0")
    }

    var durationSecondsText by rememberSaveable {
        mutableStateOf("0")
    }

    var limitEnabled by rememberSaveable {
        mutableStateOf(false)
    }

    var maxCountText by rememberSaveable {
        mutableStateOf("10")
    }

    var timerError by rememberSaveable {
        mutableStateOf<String?>(
            null
        )
    }

    var showAutomaticBackgroundWarning by remember {
        mutableStateOf(false)
    }

    var pendingAutomaticStartRequest by remember {
        mutableStateOf<
                AutomaticAcquisitionRequest?
                >(null)
    }

    var showManualSessionInterruptionConfirmation by remember {
        mutableStateOf(false)
    }

    var pendingAutomaticAfterManualConfirmation by remember {
        mutableStateOf<AutomaticAcquisitionRequest?>(null)
    }

    // -------------------------------------------------
    // ACQUISITION PARAMETERS DIALOG STATE
    // -------------------------------------------------

    var samplesText by rememberSaveable(sensorProfileHardwareUid) {
        mutableStateOf(
            samplesPerMeasurement
                .toString()
        )
    }

    var spacingText by rememberSaveable(sensorProfileHardwareUid) {
        mutableStateOf(
            sampleSpacingMs
                .toString()
        )
    }

    var trimEnabled by rememberSaveable(sensorProfileHardwareUid) {
        mutableStateOf(
            discardExtremes
        )
    }

    var lastLoadedSampling by remember(sensorProfileHardwareUid) {
        mutableStateOf(AcquisitionParameters(samplesPerMeasurement, sampleSpacingMs, discardExtremes))
    }
    LaunchedEffect(samplesPerMeasurement, sampleSpacingMs, discardExtremes, sensorProfileHardwareUid) {
        samplesText = refreshUneditedSetting(samplesText, lastLoadedSampling.samplesPerMeasurement.toString(), samplesPerMeasurement.toString())
        spacingText = refreshUneditedSetting(spacingText, lastLoadedSampling.sampleSpacingMs.toString(), sampleSpacingMs.toString())
        trimEnabled = refreshUneditedSetting(trimEnabled, lastLoadedSampling.discardExtremes, discardExtremes)
        lastLoadedSampling = AcquisitionParameters(samplesPerMeasurement, sampleSpacingMs, discardExtremes)
    }

    var fakeSensorDataEnabled by
        rememberSaveable(useFakeSensorData) {
            mutableStateOf(useFakeSensorData)
        }

    val alertRuleEnabledStates =
        remember(thresholdAlertSettings) {
            mutableStateMapOf<
                    ThresholdAlertMetric,
                    Boolean
                    >().apply {
                thresholdAlertSettings.rules
                    .forEach { rule ->
                        this[rule.metric] =
                            rule.enabled
                    }
            }
        }

    val alertRuleDirectionValues =
        remember(thresholdAlertSettings) {
            mutableStateMapOf<
                    ThresholdAlertMetric,
                    String
                    >().apply {
                thresholdAlertSettings.rules
                    .forEach { rule ->
                        this[rule.metric] =
                            rule.direction.name
                    }
            }
        }

    val alertRuleThresholdTexts =
        remember(thresholdAlertSettings) {
            mutableStateMapOf<
                    ThresholdAlertMetric,
                    String
                    >().apply {
                thresholdAlertSettings.rules
                    .forEach { rule ->
                        this[rule.metric] =
                            rule.threshold.toString()
                    }
            }
        }

    var editingAlertMetric by remember {
        mutableStateOf<ThresholdAlertMetric?>(null)
    }

    var editingAlertGroup by remember {
        mutableStateOf<SensorGroup?>(null)
    }

    val groupAlertEnabledStates =
        remember {
            mutableStateMapOf<
                    ThresholdAlertMetric,
                    Boolean
                    >()
        }

    val groupAlertDirectionValues =
        remember {
            mutableStateMapOf<
                    ThresholdAlertMetric,
                    String
                    >()
        }

    val groupAlertThresholdTexts =
        remember {
            mutableStateMapOf<
                    ThresholdAlertMetric,
                    String
                    >()
        }

    var editingAlertEnabled by rememberSaveable {
        mutableStateOf(false)
    }

    var editingAlertDirectionValue by rememberSaveable {
        mutableStateOf(
            ThresholdAlertDirection.ABOVE.name
        )
    }

    var editingAlertThresholdText by rememberSaveable {
        mutableStateOf("1.0")
    }

    var editingAlertError by rememberSaveable {
        mutableStateOf<String?>(null)
    }

    fun openThresholdAlertEditor(
        metric: ThresholdAlertMetric
    ) {
        val rule =
            thresholdAlertSettings.rules
                .firstOrNull {
                    it.metric == metric
                } ?: ThresholdAlertRule(
                metric = metric,
                enabled = false,
                direction =
                    ThresholdAlertDirection.ABOVE,
                threshold = 1f
            )

        editingAlertMetric = metric
        editingAlertEnabled = rule.enabled
        editingAlertDirectionValue =
            rule.direction.name
        editingAlertThresholdText =
            rule.threshold.toString()
        editingAlertError = null
    }

    fun openThresholdAlertGroup(
        group: SensorGroup
    ) {
        thresholdAlertMetricsForGroup(group)
            .forEach { metric ->
                val rule =
                    thresholdAlertSettings.rules
                        .firstOrNull {
                            it.metric == metric
                        } ?: ThresholdAlertRule(
                        metric = metric,
                        enabled = false,
                        direction =
                            ThresholdAlertDirection.ABOVE,
                        threshold = 1f
                    )

                groupAlertEnabledStates[metric] =
                    rule.enabled
                groupAlertDirectionValues[metric] =
                    rule.direction.name
                groupAlertThresholdTexts[metric] =
                    rule.threshold.toString()
            }

        editingAlertError = null
        editingAlertGroup = group
    }

    var alertRepeatHoursText by rememberSaveable(sensorProfileHardwareUid) {
        mutableStateOf(
            (thresholdAlertSettings.repeatSeconds / 3_600)
                .toString()
        )
    }

    var alertRepeatMinutesText by rememberSaveable(sensorProfileHardwareUid) {
        mutableStateOf(
            ((thresholdAlertSettings.repeatSeconds % 3_600) / 60)
                .toString()
        )
    }

    var alertRepeatSecondsText by rememberSaveable(sensorProfileHardwareUid) {
        mutableStateOf(
            (thresholdAlertSettings.repeatSeconds % 60)
                .toString()
        )
    }

    var alertSoundValue by rememberSaveable(sensorProfileHardwareUid) {
        mutableStateOf(
            thresholdAlertSettings.sound.name
        )
    }

    var alertVolume by rememberSaveable(sensorProfileHardwareUid) {
        mutableFloatStateOf(
            thresholdAlertSettings.volume
                .toFloat()
        )
    }

    var lastLoadedAlertPreferences by remember(sensorProfileHardwareUid) { mutableStateOf(thresholdAlertSettings) }
    LaunchedEffect(thresholdAlertSettings, sensorProfileHardwareUid) {
        alertRepeatHoursText = refreshUneditedSetting(alertRepeatHoursText, (lastLoadedAlertPreferences.repeatSeconds / 3_600).toString(), (thresholdAlertSettings.repeatSeconds / 3_600).toString())
        alertRepeatMinutesText = refreshUneditedSetting(alertRepeatMinutesText, ((lastLoadedAlertPreferences.repeatSeconds % 3_600) / 60).toString(), ((thresholdAlertSettings.repeatSeconds % 3_600) / 60).toString())
        alertRepeatSecondsText = refreshUneditedSetting(alertRepeatSecondsText, (lastLoadedAlertPreferences.repeatSeconds % 60).toString(), (thresholdAlertSettings.repeatSeconds % 60).toString())
        alertSoundValue = refreshUneditedSetting(alertSoundValue,
            lastLoadedAlertPreferences.sound.name, thresholdAlertSettings.sound.name)
        alertVolume = refreshUneditedSetting(alertVolume,
            lastLoadedAlertPreferences.volume.toFloat(), thresholdAlertSettings.volume.toFloat())
        lastLoadedAlertPreferences = thresholdAlertSettings
    }

    var showAlertLogDialog by rememberSaveable {
        mutableStateOf(false)
    }

    LaunchedEffect(showAlertLogDialog) {
        onAlertLogVisibilityChanged(showAlertLogDialog)
    }

    DisposableEffect(Unit) {
        onDispose {
            onAlertLogVisibilityChanged(false)
        }
    }

    val thresholdAlertLogListState =
        rememberLazyListState()

    var alertLogEntries by remember {
        mutableStateOf<List<ThresholdAlertLogEntry>>(
            emptyList()
        )
    }

    var parametersError by rememberSaveable {
        mutableStateOf<String?>(
            null
        )
    }

    var numericFormatValue by
        rememberSaveable(numericFormat) {
            mutableStateOf(
                numericFormat.storedValue
            )
        }

    val settingsExpansion =
        rememberUvirSettingsExpansionState(context)

    LaunchedEffect(settingsExpansion.wifi) {
        if (settingsExpansion.wifi) {
            onRequestCurrentWifiSsid()
        }
    }

    val homeMenuPinned by remember {
        derivedStateOf {
            liveListState.firstVisibleItemIndex >= 1
        }
    }

    LaunchedEffect(
        showParametersDialog
    ) {
        if (showParametersDialog) {
            numericFormatValue =
                numericFormat.storedValue
        }

    }

    val invalidIntervalText =
        stringResource(
            R.string.invalid_interval
        )

    val invalidScheduleText =
        stringResource(
            R.string.invalid_schedule
        )

    val automaticStartedText =
        stringResource(
            R.string.automatic_started
        )

    fun completeAutomaticStart(
        request: AutomaticAcquisitionRequest
    ) {
        onStartAutomaticAcquisition(
            request
        )

        recentManualSessionId =
            null
        recentManualSessionCount =
            0
        manualSaveModeValue =
            ManualSaveMode.SINGLE.storedValue

        showUvirBottomMessage(
            context,
            automaticStartedText,
            longDuration = false
        )

        timerError =
            null
        showAutomaticBackgroundWarning =
            false
        pendingAutomaticStartRequest =
            null
        showAutomaticDialog =
            false
    }

    fun continueAutomaticStart(
        request: AutomaticAcquisitionRequest
    ) {
        if (
            isAutomaticBackgroundUnrestricted(
                context.applicationContext
            )
        ) {
            completeAutomaticStart(request)
        } else {
            pendingAutomaticStartRequest = request
            showAutomaticBackgroundWarning = true
        }
    }

    val automaticStoppingText =
        stringResource(
            R.string.automatic_stopping
        )

    val parametersSavedText =
        stringResource(
            R.string.parameters_saved
        )

    val sensorRadioConnectionRequiredText =
        stringResource(
            R.string.sensor_radio_setting_usb_required
        )

    val valueOutOfLimitsCorrectedText =
        stringResource(
            R.string.value_out_of_limits_corrected
        )

    val versionInfoDescription =
        stringResource(
            R.string.version_info
        )

    val uvTotal =
        measurement.uvc +
                measurement.uvb +
                measurement.uva

    val visibleTotal =
        measurement.violetto +
                measurement.blu +
                measurement.verde +
                measurement.giallo +
                measurement.arancione +
                measurement.rosso

    val nirTotal =
        measurement.f8 +
                measurement.nir

    val hev =
        measurement.violetto +
                measurement.blu

    val activeAlertMetrics =
        remember(thresholdAlertViolations) {
            thresholdAlertViolations
                .map { it.rule.metric }
                .toSet()
        }

    val configuredAlertMetrics =
        remember(thresholdAlertSettings) {
            thresholdAlertSettings.rules
                .filter { it.enabled }
                .map { it.metric }
                .toSet()
        }

    val alertSessionActive =
        thresholdAlertSessionActive ||
            thresholdAlertSettings.hasActiveMonitoring()

    val alertSessionReadyToStart =
        !alertSessionActive &&
            configuredAlertMetrics.isNotEmpty() &&
            sensorOperationsAvailable

    val alertFloatingControlVisible =
        alertSessionActive || alertSessionReadyToStart

    val monitoringAlertMetrics =
        if (alertSessionActive) {
            configuredAlertMetrics
        } else {
            emptySet()
        }

    val alertPulseTransition =
        rememberInfiniteTransition(
            label = "threshold-alert-edge-glow"
        )

    val alertGlowAlpha by
        alertPulseTransition.animateFloat(
            initialValue = 0.12f,
            targetValue = 0.46f,
            animationSpec =
                infiniteRepeatable(
                    animation = tween(1100),
                    repeatMode = RepeatMode.Reverse
                ),
            label = "threshold-alert-edge-glow-alpha"
        )

    val alertEdgeGlowDirections by
        remember(
            monitoringAlertMetrics,
            sensorOperationsAvailable,
            viewMode,
            groups,
            liveListState
        ) {
            derivedStateOf {
                if (
                    !sensorOperationsAvailable ||
                    viewMode != ViewMode.IRRADIANCE ||
                    monitoringAlertMetrics.isEmpty()
                ) {
                    false to false
                } else {
                    val layoutInfo =
                        liveListState.layoutInfo
                    val visibleItems =
                        layoutInfo.visibleItemsInfo
                    val visibleGroupItems =
                        visibleItems.filter {
                            it.index >= 2
                        }
                    val firstVisibleIndex =
                        visibleGroupItems.minOfOrNull {
                            it.index
                        } ?: liveListState
                            .firstVisibleItemIndex
                            .coerceAtLeast(2)
                    val lastVisibleIndex =
                        visibleGroupItems.maxOfOrNull {
                            it.index
                        } ?: firstVisibleIndex
                    var glowAbove = false
                    var glowBelow = false

                    monitoringAlertMetrics
                        .mapNotNull { metric ->
                            groups.indexOf(
                                metric.sensorGroup()
                            ).takeIf { it >= 0 }
                        }
                        .map { groupIndex ->
                            groupIndex + 2
                        }
                        .distinct()
                        .forEach { itemIndex ->
                            val visibleItem =
                                visibleGroupItems.firstOrNull {
                                    it.index == itemIndex
                                }

                            if (visibleItem == null) {
                                if (itemIndex < firstVisibleIndex) {
                                    glowAbove = true
                                }
                                if (itemIndex > lastVisibleIndex) {
                                    glowBelow = true
                                }
                            } else {
                                if (
                                    visibleItem.offset <
                                    layoutInfo.viewportStartOffset
                                ) {
                                    glowAbove = true
                                }
                                if (
                                    visibleItem.offset +
                                    visibleItem.size >
                                    layoutInfo.viewportEndOffset
                                ) {
                                    glowBelow = true
                                }
                            }
                        }

                    glowAbove to glowBelow
                }
            }
        }

    fun refreshRecentManualSessionState() {
        val rememberedChoice =
            rememberedManualSessionChoice(
                manualPreferences
            )
        val rememberedSessionId =
            rememberedChoice.lastSessionId
        val rememberedSessionCount =
            rememberedSessionId
                ?.let {
                    database
                        .acquisitionCountForSession(
                            it
                        )
                } ?: 0

        if (
            rememberedSessionId != null &&
            rememberedSessionCount > 0
        ) {
            manualSaveModeValue =
                rememberedChoice.mode.storedValue
            recentManualSessionId =
                rememberedSessionId
            recentManualSessionCount =
                rememberedSessionCount
        } else {
            clearRememberedManualSession(
                manualPreferences
            )
            manualSaveModeValue =
                ManualSaveMode.SINGLE.storedValue
            recentManualSessionId = null
            recentManualSessionCount = 0
        }
    }

    fun openAutomaticAcquisitionPage() {

        refreshRecentManualSessionState()

        val hours =
            autoIntervalSeconds /
                    3600L

        val minutes =
            (
                    autoIntervalSeconds %
                            3600L
                    ) / 60L

        val seconds =
            autoIntervalSeconds %
                    60L

        timerHours =
            hours.toString()

        timerMinutes =
            minutes.toString()

        timerSeconds =
            seconds.toString()

        timerNote = limitUvirNote(autoNote)
        conditionalEnabled = if (autoEnabled) autoConditionalPlan != null else autoConditionalEnabled
        conditionalMatch = autoConditionalPlan?.takeIf { autoEnabled }?.match ?: autoConditionalMatch
        conditionalAction = autoConditionalPlan?.takeIf { autoEnabled }?.action ?: autoConditionalAction

        useStartDelay =
            autoUseStartDelay

        startDelayHoursText =
            (
                    autoStartDelaySeconds /
                            3600L
                    ).toString()

        startDelayMinutesText =
            (
                    autoStartDelaySeconds %
                            3600L /
                            60L
                    ).toString()

        startDelaySecondsText =
            (
                    autoStartDelaySeconds %
                            60L
                    ).toString()

        useDuration =
            autoUseDuration

        durationHoursText =
            (
                    autoDurationSeconds /
                            3600L
                    ).toString()

        durationMinutesText =
            (
                    autoDurationSeconds %
                            3600L /
                            60L
                    ).toString()

        durationSecondsText =
            (
                    autoDurationSeconds %
                            60L
                    ).toString()

        limitEnabled =
            autoLimitEnabled

        maxCountText =
            autoMaxCount.toString()

        timerError =
            null

        showAutomaticDialog =
            true
    }

    // -------------------------------------------------
    // MANUAL SAVE
    // -------------------------------------------------

    if (showFirmwareUpdateRequiredDialog) {
        UvirFirmwareUpdateRequiredDialog(
            cardColor = cardColor,
            primaryText = primaryText,
            secondaryText = secondaryText,
            onDismissRequest = {
                showFirmwareUpdateRequiredDialog = false
            }
        )
    }

    if (showSaveDialog) {
        val measurementSavedText =
            stringResource(R.string.measurement_saved)
        val saveErrorText =
            stringResource(R.string.save_error)

        UvirManualAcquisitionDialog(
            note = note,
            selectedMode =
                ManualSaveMode.fromStoredValue(
                    manualSaveModeValue
                ),
            recentManualSessionId = recentManualSessionId,
            recentManualSessionCount = recentManualSessionCount,
            primaryText = primaryText,
            secondaryText = secondaryText,
            cardColor = cardColor,
            onNoteChanged = {
                note = it
            },
            onModeSelected = { mode ->
                manualSaveModeValue = mode.storedValue
            },
            onSave = {
                val selectedMode =
                    ManualSaveMode.fromStoredValue(
                        manualSaveModeValue
                    )

                // This also covers the unlikely case in which AUTO
                // is started remotely while this dialog is open.
                val requestedMode =
                    if (
                        autoEnabled &&
                        selectedMode ==
                            ManualSaveMode.LAST_MANUAL_SESSION
                    ) {
                        ManualSaveMode.SINGLE
                    } else {
                        selectedMode
                    }

                if (autoEnabled) {
                    onStopAutomaticAcquisition()
                }

                val manualSession =
                    resolveManualSession(
                        database = database,
                        preferences = manualPreferences,
                        requestedMode = requestedMode
                    )
                val result =
                    database.saveAcquisition(
                        sample = measurement,
                        note = note.trim(),
                        automatic = false,
                        sessionId =
                            manualSession.sessionId,
                        sessionSequence =
                            manualSession.sequence,
                        sensorDeviceId =
                            selectedSensorDeviceId
                    )

                if (result != -1L) {
                    rememberManualSaveSuccess(
                        manualPreferences,
                        manualSession
                    )
                    manualAcquisitionPulseId += 1
                    onAcquisitionSaved()
                }

                showUvirBottomMessage(
                    context,
                    if (result != -1L) {
                        measurementSavedText
                    } else {
                        saveErrorText
                    },
                    longDuration = false
                )

                note = ""
                showSaveDialog = false
            },
            onCancel = {
                note = ""
                showSaveDialog = false
            },
            onDismissRequest = {
                showSaveDialog = false
            }
        )
    }

    // -------------------------------------------------
    // STOP AUTOMATIC ACQUISITION CONFIRMATION
    // -------------------------------------------------

    if (showStopConfirmation) {
        UvirStopAutomaticDialog(
            cardColor = cardColor,
            primaryText = primaryText,
            secondaryText = secondaryText,
            stopEnabled = sensorOperationsAvailable && !automaticStopInProgress,
            onStop = {
                onStopAutomaticAcquisition()
                showUvirBottomMessage(
                    context,
                    automaticStoppingText,
                    longDuration = false
                )
                showStopConfirmation = false
                showAutomaticDialog = false
            },
            onContinue = {
                showStopConfirmation = false
            },
            onDismissRequest = {
                showStopConfirmation = false
            }
        )
    }

    if (showStopAllAlertsConfirmation) {
        UvirStopAllAlertsDialog(
            cardColor = cardColor,
            primaryText = primaryText,
            secondaryText = secondaryText,
            stopEnabled = sensorOperationsAvailable,
            onStop = {
                val stoppedSettings =
                    thresholdAlertSettings.withMonitoringStopped()
                onApplyThresholdAlertSettings(stoppedSettings)
                showStopAllAlertsConfirmation = false
            },
            onContinue = {
                showStopAllAlertsConfirmation = false
            },
            onDismissRequest = {
                showStopAllAlertsConfirmation = false
            }
        )
    }

    if (showStartAllAlertsConfirmation) {
        UvirStartAllAlertsDialog(
            note = pendingAlertSessionNote,
            cardColor = cardColor,
            primaryText = primaryText,
            secondaryText = secondaryText,
            onNoteChanged = {
                pendingAlertSessionNote = limitUvirNote(it)
            },
            onStart = { sessionNote ->
                if (!sensorFirmwareCurrent) {
                    showStartAllAlertsConfirmation = false
                    showFirmwareUpdateRequiredDialog = true
                } else {
                    onStartThresholdAlertSession(
                        thresholdAlertSettings.copy(enabled = true),
                        sessionNote
                    )
                    showStartAllAlertsConfirmation = false
                    pendingAlertSessionNote = limitUvirNote(sessionNote).trim()
                }
            },
            onCancel = {
                showStartAllAlertsConfirmation = false
                pendingAlertSessionNote = thresholdAlertSessionNote
            },
            onDismissRequest = {
                showStartAllAlertsConfirmation = false
                pendingAlertSessionNote = thresholdAlertSessionNote
            }
        )
    }

    if (showManualSessionInterruptionConfirmation) {
        pendingAutomaticAfterManualConfirmation?.let { request ->
            UvirInterruptManualSessionDialog(
                cardColor = cardColor,
                primaryText = primaryText,
                secondaryText = secondaryText,
                onInterrupt = {
                    showManualSessionInterruptionConfirmation = false
                    pendingAutomaticAfterManualConfirmation = null
                    continueAutomaticStart(request)
                },
                onContinueManualSession = {
                    showManualSessionInterruptionConfirmation = false
                    pendingAutomaticAfterManualConfirmation = null
                },
                onDismissRequest = {
                    showManualSessionInterruptionConfirmation = false
                    pendingAutomaticAfterManualConfirmation = null
                }
            )
        }
    }

    if (showAutomaticBackgroundWarning) {
        pendingAutomaticStartRequest?.let { request ->
            UvirAutomaticBackgroundWarningDialog(
                cardColor = cardColor,
                primaryText = primaryText,
                secondaryText = secondaryText,
                onOpenSettings = {
                    openAutomaticBackgroundSettings(
                        context.applicationContext
                    )
                    completeAutomaticStart(request)
                },
                onContinue = {
                    completeAutomaticStart(request)
                },
                onDismissRequest = {
                    showAutomaticBackgroundWarning = false
                    pendingAutomaticStartRequest = null
                }
            )
        }
    }

    // -------------------------------------------------
    // AUTOMATIC ACQUISITION
    // -------------------------------------------------


    if (showAutomaticDialog) {

        UvirFullScreenPage(
            onDismissRequest = {

                timerError =
                    null

                showAutomaticDialog =
                    false
            },

            title = {
                UvirMenuTitle(
                    text =
                        stringResource(
                            R.string.automatic_acquisition_title
                        )
                )
            },

            containerColor =
                backgroundColor,

            contentColor =
                primaryText,

            text = {
                UvirAutomaticAcquisitionForm(
                    listState = automaticListState,
                    timerHours = timerHours,
                    timerMinutes = timerMinutes,
                    timerSeconds = timerSeconds,
                    useStartDelay = useStartDelay,
                    startDelayHours = startDelayHoursText,
                    startDelayMinutes = startDelayMinutesText,
                    startDelaySeconds = startDelaySecondsText,
                    useDuration = useDuration,
                    durationHours = durationHoursText,
                    durationMinutes = durationMinutesText,
                    durationSeconds = durationSecondsText,
                    limitEnabled = limitEnabled,
                    maxCount = maxCountText,
                    note = timerNote,
                    error = timerError,
                    conditionalEnabled = conditionalEnabled,
                    conditionalMatch = conditionalMatch,
                    conditionalAction = conditionalAction,
                    conditionalRules = if (autoEnabled) autoConditionalPlan?.rules.orEmpty()
                        else autoConditionalRules,
                    conditionalWaiting = autoEnabled && autoConditionalWaiting,
                    conditionalLocked = autoEnabled,
                    numericFormat = numericFormat,
                    onConditionalEnabled = { conditionalEnabled = it },
                    onConditionalMatch = { conditionalMatch = it },
                    onConditionalAction = { conditionalAction = it },
                    onConfigureConditions = { showConditionalRulesEditor = true },
                    cardColor = cardColor,
                    primaryText = primaryText,
                    secondaryText = secondaryText,
                    onTimerHoursChanged = {
                        timerHours = it
                    },
                    onTimerMinutesChanged = {
                        timerMinutes = it
                    },
                    onTimerSecondsChanged = {
                        timerSeconds = it
                    },
                    onIntervalEditingComplete = {
                        val normalized =
                            normalizeDuration(
                                timerHours,
                                timerMinutes,
                                timerSeconds,
                                minimumTotalSeconds = 1L
                            )
                        timerHours = normalized.hoursText
                        timerMinutes = normalized.minutesText
                        timerSeconds = normalized.secondsText
                        if (normalized.corrected) {
                            showUvirBottomMessage(
                                context,
                                valueOutOfLimitsCorrectedText
                            )
                        }
                    },
                    onUseStartDelayChanged = {
                        useStartDelay = it
                    },
                    onStartDelayHoursChanged = {
                        startDelayHoursText = it
                    },
                    onStartDelayMinutesChanged = {
                        startDelayMinutesText = it
                    },
                    onStartDelaySecondsChanged = {
                        startDelaySecondsText = it
                    },
                    onStartDelayEditingComplete = {
                        val normalized =
                            normalizeDuration(
                                startDelayHoursText,
                                startDelayMinutesText,
                                startDelaySecondsText,
                                minimumTotalSeconds = 1L
                            )
                        startDelayHoursText = normalized.hoursText
                        startDelayMinutesText = normalized.minutesText
                        startDelaySecondsText = normalized.secondsText
                        if (normalized.corrected) {
                            showUvirBottomMessage(
                                context,
                                valueOutOfLimitsCorrectedText
                            )
                        }
                    },
                    onUseDurationChanged = {
                        useDuration = it
                    },
                    onDurationHoursChanged = {
                        durationHoursText = it
                    },
                    onDurationMinutesChanged = {
                        durationMinutesText = it
                    },
                    onDurationSecondsChanged = {
                        durationSecondsText = it
                    },
                    onDurationEditingComplete = {
                        val normalized =
                            normalizeDuration(
                                durationHoursText,
                                durationMinutesText,
                                durationSecondsText,
                                minimumTotalSeconds = 1L
                            )
                        durationHoursText = normalized.hoursText
                        durationMinutesText = normalized.minutesText
                        durationSecondsText = normalized.secondsText
                        if (normalized.corrected) {
                            showUvirBottomMessage(
                                context,
                                valueOutOfLimitsCorrectedText
                            )
                        }
                    },
                    onLimitEnabledChanged = {
                        limitEnabled = it
                    },
                    onMaxCountChanged = {
                        maxCountText = it
                    },
                    onMaxCountEditingComplete = {
                        val normalized =
                            normalizeBoundedInteger(
                                maxCountText,
                                1,
                                MAX_AUTOMATIC_ACQUISITIONS
                            )
                        maxCountText = normalized.text
                        if (normalized.corrected) {
                            showUvirBottomMessage(
                                context,
                                valueOutOfLimitsCorrectedText
                            )
                        }
                    },
                    onNoteChanged = {
                        timerNote = it
                    }
                )
            },

            contentOverlay = {
                Box(
                    modifier =
                        Modifier
                            .align(
                                Alignment.CenterEnd
                            )
                            .fillMaxHeight()
                            .width(16.dp)
                            .lazyScrollbarOverlay(
                                state =
                                    automaticListState,
                                color =
                                    secondaryText.copy(
                                        alpha = 0.46f
                                    )
                            )
                )
            },

            floatingActionButton = {
                val compactOfflineNotice =
                    offlineDisconnectionNotice
                        ?.takeIf {
                            autoEnabled && !sensorOperationsAvailable
                        }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    compactOfflineNotice?.let { notice ->
                        UvirCompactOfflineAutonomyNotice(
                            notice = notice,
                            primaryText = primaryText
                        )
                    }

                    UvirAutomaticAcquisitionFloatingAction(
                        automaticActive = autoEnabled,
                        completedCount = autoCompletedCount,
                        enabled =
                            sensorOperationsAvailable &&
                                (!autoEnabled || !automaticStopInProgress),
                        syncInProgress = acquisitionSyncInProgress,
                        onClick = automaticAction@{
                            if (autoEnabled) {
                                timerError = null
                                showStopConfirmation = true
                                return@automaticAction
                            }

                            val normalizedInterval =
                                normalizeDuration(
                                    timerHours,
                                    timerMinutes,
                                    timerSeconds,
                                    minimumTotalSeconds = 1L
                                )
                            val normalizedStartDelay =
                                if (useStartDelay) {
                                    normalizeDuration(
                                        startDelayHoursText,
                                        startDelayMinutesText,
                                        startDelaySecondsText,
                                        minimumTotalSeconds = 1L
                                    )
                                } else {
                                    null
                                }
                            val normalizedDuration =
                                if (useDuration) {
                                    normalizeDuration(
                                        durationHoursText,
                                        durationMinutesText,
                                        durationSecondsText,
                                        minimumTotalSeconds = 1L
                                    )
                                } else {
                                    null
                                }
                            val normalizedMaximum =
                                if (limitEnabled) {
                                    normalizeBoundedInteger(
                                        maxCountText,
                                        1,
                                        MAX_AUTOMATIC_ACQUISITIONS
                                    )
                                } else {
                                    null
                                }

                            timerHours = normalizedInterval.hoursText
                            timerMinutes = normalizedInterval.minutesText
                            timerSeconds = normalizedInterval.secondsText
                            normalizedStartDelay?.let {
                                startDelayHoursText = it.hoursText
                                startDelayMinutesText = it.minutesText
                                startDelaySecondsText = it.secondsText
                            }
                            normalizedDuration?.let {
                                durationHoursText = it.hoursText
                                durationMinutesText = it.minutesText
                                durationSecondsText = it.secondsText
                            }
                            normalizedMaximum?.let {
                                maxCountText = it.text
                            }

                            if (
                                normalizedInterval.corrected ||
                                normalizedStartDelay?.corrected == true ||
                                normalizedDuration?.corrected == true ||
                                normalizedMaximum?.corrected == true
                            ) {
                                showUvirBottomMessage(
                                    context,
                                    valueOutOfLimitsCorrectedText
                                )
                            }

                            if (!sensorFirmwareCurrent) {
                                showFirmwareUpdateRequiredDialog = true
                                return@automaticAction
                            }

                            val validation =
                                validateAutomaticAcquisitionInput(
                                    AutomaticAcquisitionInput(
                                        intervalHours = normalizedInterval.hoursText,
                                        intervalMinutes = normalizedInterval.minutesText,
                                        intervalSeconds = normalizedInterval.secondsText,
                                        note = timerNote,
                                        useStartDelay = useStartDelay,
                                        startDelayHours =
                                            normalizedStartDelay?.hoursText
                                                ?: startDelayHoursText,
                                        startDelayMinutes =
                                            normalizedStartDelay?.minutesText
                                                ?: startDelayMinutesText,
                                        startDelaySeconds =
                                            normalizedStartDelay?.secondsText
                                                ?: startDelaySecondsText,
                                        useDuration = useDuration,
                                        durationHours =
                                            normalizedDuration?.hoursText
                                                ?: durationHoursText,
                                        durationMinutes =
                                            normalizedDuration?.minutesText
                                                ?: durationMinutesText,
                                        durationSeconds =
                                            normalizedDuration?.secondsText
                                                ?: durationSecondsText,
                                        limitEnabled = limitEnabled,
                                        maxAcquisitions =
                                            normalizedMaximum?.text
                                                ?: maxCountText
                                    )
                                )

                            when (validation) {
                                AutomaticAcquisitionValidation.InvalidInterval -> {
                                    timerError =
                                        invalidIntervalText
                                }

                                AutomaticAcquisitionValidation.InvalidSchedule -> {
                                    timerError =
                                        invalidScheduleText
                                }

                                is AutomaticAcquisitionValidation.Valid -> {
                                    val useConditional = conditionalEnabled && conditionalConfiguredRulesAvailable
                                    val plan = if (useConditional) conditionalPlanFromRules(
                                        autoConditionalRules, conditionalMatch, conditionalAction) else null
                                    if (useConditional && plan == null) {
                                        showUvirBottomMessage(context, context.getString(R.string.conditional_no_rules))
                                        return@automaticAction
                                    }
                                    if (plan != null && !fakeSensorDataEnabled &&
                                        !firmwareSupportsImmediateConditionalAcquisition(selectedSensorInfo.firmwareVersion)) {
                                        showFirmwareUpdateRequiredDialog = true
                                        return@automaticAction
                                    }
                                    val request = validation.request.copy(conditionalPlan = plan)

                                    if (recentManualSessionId != null) {
                                        pendingAutomaticAfterManualConfirmation = request
                                        showManualSessionInterruptionConfirmation = true
                                    } else {
                                        continueAutomaticStart(request)
                                    }
                                }
                            }
                        }
                    )
                }
            }
        )
    if (showConditionalRulesEditor && !autoEnabled) {
        UvirAcquisitionConditionsDialog(
            initialRules = autoConditionalRules, cardColor = cardColor,
            primaryText = primaryText, secondaryText = secondaryText,
            currentSample = measurement.takeIf { liveReady },
            onSave = { rules ->
                onSaveAcquisitionConditions(rules)
                showConditionalRulesEditor = false
            },
            onDismissRequest = { showConditionalRulesEditor = false }
        )
    }
        return
    }

val sensorControlConnectionAvailable =
    selectedSensorConnected

val statusLedTestEnabled =
    sensorControlConnectionAvailable &&
        statusLedEnabled &&
        !autoEnabled &&
        !thresholdAlertSettings.hasActiveMonitoring() &&
        firmwareSupportsStatusLedTest(selectedSensorInfo.firmwareVersion)

val statusBuzzerTestEnabled =
    sensorControlConnectionAvailable &&
        statusBuzzerEnabled &&
        !autoEnabled &&
        !thresholdAlertSettings.hasActiveMonitoring() &&
        firmwareSupportsStatusBuzzerTest(selectedSensorInfo.firmwareVersion)

val debugPerformanceFirmwareSupported =
    firmwareSupportsDebugPerformance(selectedSensorInfo.firmwareVersion)

val debugPerformanceCompletionToken =
    when {
        sensorConnectionMode == SensorConnectionMode.USB &&
            usbSensorState.status == UsbSensorConnectionStatus.CONNECTED ->
            usbSensorState.debugPerformanceCompletionSequence * 2L

        wirelessSensorState.status ==
            WirelessSensorConnectionStatus.CONNECTED ->
            wirelessSensorState.debugPerformanceCompletionSequence * 2L + 1L

        else -> usbSensorState.debugPerformanceCompletionSequence * 2L
    }

val debugPerformanceEnabled =
    sensorControlConnectionAvailable &&
        !autoEnabled &&
        !thresholdAlertSettings.hasActiveMonitoring()

val sensorPowerOffEnabled =
    selectedSensorConnected &&
        !autoEnabled &&
        !thresholdAlertSettings.hasActiveMonitoring()

if (showSensorSourceDialog) {
    UvirSensorSourceDialog(
        selectedMode = sensorConnectionMode,
        useFakeSensorData = useFakeSensorData,
        wifiEnabled = appliedSensorWifiRadioEnabled,
        bluetoothEnabled = appliedSensorBluetoothRadioEnabled,
        internetEnabled = appliedSensorInternetConfiguration.enabled,
        primaryText = primaryText,
        secondaryText = secondaryText,
        cardColor = cardColor,
        sensorInfo = selectedSensorInfo,
        sensorDisplayName = sensorDisplayName,
        sensorProfiles = sensorProfiles.filter {
            normalizeSensorDeviceId(it.hardwareUid) in UvirSensorCredentialStore.associatedDeviceIds(context)
        },
        selectedSensorDeviceId = sensorProfileHardwareUid,
        sensorSelectionEnabled = sensorSelectionEnabled && !settingsApplyInProgress &&
            !wifiConfigurationInProgress && !sensorPowerOffInProgress && !manualAcquisitionBadgePulseActive,
        onSensorSelected = { deviceId ->
            showSensorSourceDialog = false
            onSensorSelected(deviceId)
        },
        sensorConnected = selectedSensorConnected,
        sensorPowerOffEnabled = sensorPowerOffEnabled,
        onOpenSensorInfo = {
            showSensorSourceDialog = false
            showSensorInfoDialog = true
        },
        onRequestSensorPowerOff = {
            showSensorSourceDialog = false
            showSensorPowerOffConfirmation = true
        },
        onModeSelected = { mode ->
            onSensorConnectionModeChanged(mode)
            showSensorSourceDialog = false
        },
        onDismissRequest = {
            showSensorSourceDialog = false
        }
    )
}

if (showSensorPowerOffConfirmation) {
    SensorPowerOffConfirmation(
        inProgress = sensorPowerOffInProgress,
        onInProgressChange = { sensorPowerOffInProgress = it },
        coroutineScope = settingsApplyScope,
        onPowerOffSensor = onPowerOffSensor,
        onDismissRequest = {
            showSensorPowerOffConfirmation = false
        },
        cardColor = cardColor,
        primaryText = primaryText,
        secondaryText = secondaryText
    )
}

if (showSensorInfoDialog) {
    UvirSensorInfoDialog(
        connectionMode = sensorConnectionMode,
        sensorInfo = selectedSensorInfo,
        primaryText = primaryText,
        secondaryText = secondaryText,
        cardColor = cardColor,
        onDismissRequest = {
            showSensorInfoDialog = false
            showSensorSourceDialog = false
        }
    )
}

// -------------------------------------------------
// VERSION & INFO
// -------------------------------------------------

if (showVersionInfoDialog) {
    UvirVersionInfoScreen(
        scrollState = versionInfoScrollState,
        backgroundColor = backgroundColor,
        cardColor = cardColor,
        primaryText = primaryText,
        secondaryText = secondaryText,
        onDismissRequest = {
            showVersionInfoDialog = false
        }
    )
    return
}

// -------------------------------------------------
// ACQUISITION PARAMETERS
// -------------------------------------------------

editingAlertGroup?.let { group ->
    val groupMetrics = thresholdAlertMetricsForGroup(group)
    val groupName =
        when (group) {
            SensorGroup.UV ->
                stringResource(R.string.uv_radiation)

            SensorGroup.VISIBLE ->
                stringResource(R.string.visible_light)

            SensorGroup.NIR ->
                stringResource(R.string.far_red_nir)

            SensorGroup.BIOLOGICAL ->
                stringResource(R.string.biological_effects_group_name)
        }
    val groupEditorScrollbar =
        rememberUvirDialogScrollbar(
            secondaryText.copy(alpha = 0.58f)
        )
    val groupEditorScrollState =
        groupEditorScrollbar.scrollState
    val groupEditorScope =
        rememberCoroutineScope()
    val groupEditorRequesters =
        remember(group) {
            groupMetrics
                .associateWith {
                    BringIntoViewRequester()
                }
        }

    fun setGroupAlertEnabled(
        metric: ThresholdAlertMetric,
        enabled: Boolean
    ) {
        groupAlertEnabledStates[metric] = enabled
        editingAlertError = null

        if (enabled) {
            groupEditorScope.launch {
                delay(120L)
                groupEditorRequesters[metric]
                    ?.bringIntoView()
            }
        }
    }

    AlertDialog(
        onDismissRequest = {
            editingAlertGroup = null
            editingAlertMetric = null
            editingAlertError = null
        },
        modifier = groupEditorScrollbar.dialogModifier,
        title = {
            Text(
                stringResource(
                    R.string.threshold_group_editor_title,
                    groupName
                )
            )
        },
        text = {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .then(
                            groupEditorScrollbar.viewportModifier
                        )
            ) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                        .verticalScroll(
                            groupEditorScrollState
                        )
                ) {
                Text(
                    text =
                        stringResource(
                            R.string.threshold_group_choose_value
                        ),
                    color = secondaryText,
                    fontSize = 12.sp,
                    modifier =
                        Modifier.padding(
                            bottom = 8.dp
                        )
                )

                groupMetrics
                    .forEachIndexed { index, metric ->
                        val enabled =
                            groupAlertEnabledStates[metric]
                                ?: false
                        val directionValue =
                            groupAlertDirectionValues[metric]
                                ?: ThresholdAlertDirection.ABOVE.name
                        val thresholdText =
                            groupAlertThresholdTexts[metric]
                                ?: "1.0"

                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clickable(
                                        enabled = sensorOperationsAvailable
                                    ) {
                                        setGroupAlertEnabled(
                                            metric,
                                            !enabled
                                        )
                                    }
                                    .padding(
                                        vertical = 10.dp,
                                        horizontal = 2.dp
                                    ),
                            verticalAlignment =
                                Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = enabled,
                                enabled = sensorOperationsAvailable,
                                onCheckedChange = { checked ->
                                    setGroupAlertEnabled(
                                        metric,
                                        checked
                                    )
                                },
                                modifier =
                                    Modifier.size(40.dp)
                            )

                            ThresholdAlertMetricLabel(
                                metric = metric,
                                primaryText = primaryText,
                                secondaryText = secondaryText,
                                enabled = sensorOperationsAvailable,
                                modifier = Modifier.weight(1f)
                            )

                        }

                        if (enabled) {
                            Surface(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .bringIntoViewRequester(
                                            groupEditorRequesters
                                                .getValue(metric)
                                        )
                                        .padding(
                                            bottom = 8.dp
                                        ),
                                shape =
                                    RoundedCornerShape(12.dp),
                                color =
                                    cardColor,
                                border =
                                    BorderStroke(
                                        1.dp,
                                        secondaryText.copy(
                                            alpha = 0.18f
                                        )
                                    )
                            ) {
                                Column(
                                    modifier =
                                        Modifier.padding(12.dp),
                                    verticalArrangement =
                                        Arrangement.spacedBy(10.dp)
                                ) {
                                    if (enabled) {
                                        ThresholdConditionControls(
                                            selectedDirection =
                                                runCatching {
                                                    ThresholdAlertDirection.valueOf(
                                                        directionValue
                                                    )
                                                }.getOrDefault(
                                                    ThresholdAlertDirection.ABOVE
                                                ),
                                            onDirectionSelected = { direction ->
                                                groupAlertDirectionValues[metric] =
                                                    direction.name
                                            },
                                            currentValueEnabled = liveReady,
                                            onUseCurrentValue = {
                                                groupAlertThresholdTexts[metric] =
                                                    thresholdEditableValue(
                                                        measurement
                                                            .thresholdMetricValue(metric)
                                                    )
                                                editingAlertError = null
                                            },
                                            cardColor = cardColor,
                                            primaryText = primaryText,
                                            secondaryText = secondaryText,
                                            enabled = sensorOperationsAvailable
                                        )

                                        DecimalField(
                                            value =
                                                thresholdText,
                                            onValueChange = {
                                                groupAlertThresholdTexts[metric] =
                                                    it
                                                editingAlertError = null
                                            },
                                            label =
                                                stringResource(
                                                    if (
                                                        metric.isBiologicalEffect()
                                                    ) {
                                                        R.string.threshold_value_biological_label
                                                    } else {
                                                        R.string.threshold_value_label
                                                    }
                                                ),
                                            enabled = sensorOperationsAvailable,
                                            onEditingComplete = {
                                                val normalized =
                                                    normalizeNonNegativeDecimal(
                                                        groupAlertThresholdTexts[metric]
                                                            ?: "0"
                                                    )
                                                groupAlertThresholdTexts[metric] =
                                                    normalized.text
                                                if (normalized.corrected) {
                                                    showUvirBottomMessage(
                                                        context,
                                                        valueOutOfLimitsCorrectedText
                                                    )
                                                }
                                            },
                                            modifier =
                                                Modifier.fillMaxWidth()
                                        )

                                        Text(
                                            text =
                                                stringResource(
                                                    if (
                                                        directionValue ==
                                                        ThresholdAlertDirection.ABOVE.name
                                                    ) {
                                                        R.string.threshold_rule_above_summary
                                                    } else {
                                                        R.string.threshold_rule_below_summary
                                                    }
                                                ),
                                            color = secondaryText,
                                            fontSize = 11.sp
                                        )
                                    }

                                }
                            }
                        }

                        if (
                            index <
                            groupMetrics.lastIndex
                        ) {
                            HorizontalDivider(
                                color =
                                    secondaryText.copy(
                                        alpha = 0.16f
                                    )
                            )
                        }
                    }

                }
            }
        },
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = {
                        editingAlertGroup = null
                        editingAlertError = null
                    }
                ) {
                    Text(
                        stringResource(
                            R.string.cancel
                        ),
                        color = UvirDestructiveActionColor
                    )
                }

                TextButton(
                    enabled = sensorOperationsAvailable,
                    onClick = {
                        if (!sensorFirmwareCurrent) {
                            showFirmwareUpdateRequiredDialog = true
                            return@TextButton
                        }
                        val groupMetrics =
                            thresholdAlertMetricsForGroup(
                                group
                            )
                        var thresholdWasCorrected = false
                        val normalizedThresholds =
                            groupMetrics.associateWith { metric ->
                                val enabled = groupAlertEnabledStates[metric] ?: false
                                if (enabled) {
                                    normalizeNonNegativeDecimal(
                                        groupAlertThresholdTexts[metric] ?: "0"
                                    ).also { normalized ->
                                        if (normalized.corrected) {
                                            thresholdWasCorrected = true
                                            groupAlertThresholdTexts[metric] = normalized.text
                                        }
                                    }.value
                                } else {
                                    groupAlertThresholdTexts[metric]
                                        ?.replace(',', '.')
                                        ?.toFloatOrNull()
                                        ?: thresholdAlertSettings.rules
                                            .firstOrNull { it.metric == metric }
                                            ?.threshold
                                        ?: 1f
                                }
                            }

                        if (thresholdWasCorrected) {
                            showUvirBottomMessage(
                                context,
                                valueOutOfLimitsCorrectedText
                            )
                        }
                            val updatedRules =
                                thresholdAlertSettings.rules
                                    .map { currentRule ->
                                        if (
                                            currentRule.metric !in
                                            groupMetrics
                                        ) {
                                            currentRule
                                        } else {
                                            currentRule.copy(
                                                enabled =
                                                    groupAlertEnabledStates[
                                                        currentRule.metric
                                                    ] ?: false,
                                                direction =
                                                    runCatching {
                                                        ThresholdAlertDirection.valueOf(
                                                            groupAlertDirectionValues[
                                                                currentRule.metric
                                                            ] ?: ThresholdAlertDirection.ABOVE.name
                                                        )
                                                    }.getOrDefault(
                                                        ThresholdAlertDirection.ABOVE
                                                    ),
                                                threshold =
                                                    normalizedThresholds[
                                                        currentRule.metric
                                                    ] ?: currentRule.threshold
                                            )
                                        }
                                    }

                            updatedRules
                                .filter {
                                    it.metric in groupMetrics
                                }
                                .forEach { rule ->
                                    alertRuleEnabledStates[rule.metric] =
                                        rule.enabled
                                    alertRuleDirectionValues[rule.metric] =
                                        rule.direction.name
                                    alertRuleThresholdTexts[rule.metric] =
                                        rule.threshold.toString()
                                }

                            onApplyThresholdAlertSettings(
                                thresholdAlertSettings.copy(
                                    rules = updatedRules
                                )
                            )

                            editingAlertGroup = null
                            editingAlertError = null
                    }
                ) {
                    Text(
                        stringResource(
                            R.string.save
                        )
                    )
                }
            }
        },
        containerColor = cardColor,
        titleContentColor = primaryText,
        textContentColor = primaryText
    )
}

if (editingAlertGroup == null) editingAlertMetric?.let { metric ->
    val metricEditorScrollbar =
        rememberUvirDialogScrollbar(
            secondaryText.copy(alpha = 0.58f)
        )
    AlertDialog(
        onDismissRequest = {
            editingAlertMetric = null
            editingAlertError = null
        },
        modifier = metricEditorScrollbar.dialogModifier,
        title = {
            Text(
                stringResource(
                    R.string.threshold_editor_title,
                    stringResource(
                        thresholdAlertMetricLabelResource(metric)
                    )
                )
            )
        },
        text = {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .then(
                            metricEditorScrollbar.viewportModifier
                        )
            ) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .verticalScroll(
                                metricEditorScrollbar.scrollState
                            ),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable(
                                enabled = sensorOperationsAvailable
                            ) {
                                editingAlertEnabled =
                                    !editingAlertEnabled
                                editingAlertError = null
                            }
                            .padding(
                                vertical = 10.dp,
                                horizontal = 2.dp
                            ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = editingAlertEnabled,
                        enabled = sensorOperationsAvailable,
                        onCheckedChange = {
                            editingAlertEnabled = it
                            editingAlertError = null
                        },
                        modifier = Modifier.size(40.dp)
                    )

                    ThresholdAlertMetricLabel(
                        metric = metric,
                        primaryText = primaryText,
                        secondaryText = secondaryText,
                        enabled = sensorOperationsAvailable,
                        modifier = Modifier.weight(1f)
                    )
                }

                if (editingAlertEnabled) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = cardColor,
                        border =
                            BorderStroke(
                                1.dp,
                                secondaryText.copy(alpha = 0.18f)
                            )
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement =
                                Arrangement.spacedBy(10.dp)
                        ) {
                            ThresholdConditionControls(
                                selectedDirection =
                                    runCatching {
                                        ThresholdAlertDirection.valueOf(
                                            editingAlertDirectionValue
                                        )
                                    }.getOrDefault(
                                        ThresholdAlertDirection.ABOVE
                                    ),
                                onDirectionSelected = { direction ->
                                    editingAlertDirectionValue =
                                        direction.name
                                },
                                currentValueEnabled = liveReady,
                                onUseCurrentValue = {
                                    editingAlertThresholdText =
                                        thresholdEditableValue(
                                            measurement
                                                .thresholdMetricValue(metric)
                                        )
                                    editingAlertError = null
                                },
                                cardColor = cardColor,
                                primaryText = primaryText,
                                secondaryText = secondaryText,
                                enabled = sensorOperationsAvailable
                            )

                            DecimalField(
                                value = editingAlertThresholdText,
                                onValueChange = {
                                    editingAlertThresholdText = it
                                    editingAlertError = null
                                },
                                label =
                                    stringResource(
                                        if (metric.isBiologicalEffect()) {
                                            R.string.threshold_value_biological_label
                                        } else {
                                            R.string.threshold_value_label
                                        }
                                    ),
                                enabled = sensorOperationsAvailable,
                                onEditingComplete = {
                                    val normalized =
                                        normalizeNonNegativeDecimal(
                                            editingAlertThresholdText
                                        )
                                    editingAlertThresholdText = normalized.text
                                    if (normalized.corrected) {
                                        showUvirBottomMessage(
                                            context,
                                            valueOutOfLimitsCorrectedText
                                        )
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            )

                            Text(
                                text =
                                    stringResource(
                                        if (
                                            editingAlertDirectionValue ==
                                            ThresholdAlertDirection.ABOVE.name
                                        ) {
                                            R.string.threshold_rule_above_summary
                                        } else {
                                            R.string.threshold_rule_below_summary
                                        }
                                    ),
                                color = secondaryText,
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                }
            }
        },
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.End,
                verticalAlignment =
                    Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = {
                        editingAlertMetric = null
                        editingAlertError = null
                    }
                ) {
                    Text(
                        stringResource(
                            R.string.cancel
                        ),
                        color = UvirDestructiveActionColor
                    )
                }

                TextButton(
                    enabled = sensorOperationsAvailable,
                    onClick = {
                        if (!sensorFirmwareCurrent) {
                            showFirmwareUpdateRequiredDialog = true
                            return@TextButton
                        }
                        val normalizedThreshold =
                            if (editingAlertEnabled) {
                                normalizeNonNegativeDecimal(
                                    editingAlertThresholdText
                                )
                            } else {
                                null
                            }
                        normalizedThreshold?.let { normalized ->
                            editingAlertThresholdText = normalized.text
                            if (normalized.corrected) {
                                showUvirBottomMessage(
                                    context,
                                    valueOutOfLimitsCorrectedText
                                )
                            }
                        }
                        val threshold =
                            normalizedThreshold?.value
                                ?: editingAlertThresholdText
                                    .replace(',', '.')
                                    .toFloatOrNull()

                            val updatedRule =
                                ThresholdAlertRule(
                                    metric = metric,
                                    enabled =
                                        editingAlertEnabled,
                                    direction =
                                        runCatching {
                                            ThresholdAlertDirection.valueOf(
                                                editingAlertDirectionValue
                                            )
                                        }.getOrDefault(
                                            ThresholdAlertDirection.ABOVE
                                        ),
                                    threshold =
                                        threshold
                                            ?: thresholdAlertSettings.rules
                                                .firstOrNull {
                                                    it.metric == metric
                                                }?.threshold
                                            ?: 1f
                                )

                            val updatedRules =
                                thresholdAlertSettings.rules
                                    .map { rule ->
                                        if (rule.metric == metric) {
                                            updatedRule
                                        } else {
                                            rule
                                        }
                                    }

                            alertRuleEnabledStates[metric] =
                                updatedRule.enabled
                            alertRuleDirectionValues[metric] =
                                updatedRule.direction.name
                            alertRuleThresholdTexts[metric] =
                                updatedRule.threshold.toString()

                            onApplyThresholdAlertSettings(
                                thresholdAlertSettings.copy(
                                    rules = updatedRules
                                )
                            )

                            editingAlertMetric = null
                            editingAlertError = null
                    }
                ) {
                    Text(
                        stringResource(
                            R.string.save
                        )
                    )
                }

            }
        },
        containerColor = cardColor,
        titleContentColor = primaryText,
        textContentColor = primaryText
    )
}

if (showAlertLogDialog) {
    ThresholdAlertLogScreen(
        database = database,
        listState = thresholdAlertLogListState,
        backgroundColor = backgroundColor,
        cardColor = cardColor,
        primaryText = primaryText,
        secondaryText = secondaryText,
        onBack = {
            showAlertLogDialog = false
        }
    )
    return
}

if (showParametersDialog) {

    if (showCounterResetConfirmation) {
        UvirCounterResetConfirmationDialog(
            cardColor = cardColor,
            primaryText = primaryText,
            secondaryText = secondaryText,
            onResetAllCounters = onResetAllCounters,
            onDismissRequest = {
                showCounterResetConfirmation = false
            }
        )
    }

    if (showRestoreDefaultsConfirmation) {
        UvirRestoreDefaultsConfirmationDialog(
            cardColor = cardColor,
            primaryText = primaryText,
            secondaryText = secondaryText,
            onRestoreDefaults = onRestoreApplicationDefaults,
            onRestored = {
                showRestoreDefaultsConfirmation = false
                showParametersDialog = false
                (context as? Activity)?.recreate()
            },
            onDismissRequest = {
                showRestoreDefaultsConfirmation = false
            }
        )
    }
    settingsSaveQueue.prepare = { group ->
            val input = UvirSettingsSaveInput(
                group = group,
                context = context,
                samplesText = samplesText,
                onSamplesTextChange = settingsTextCorrection(samplesText, { samplesText }) {
                    samplesText = it
                },
                spacingText = spacingText,
                onSpacingTextChange = settingsTextCorrection(spacingText, { spacingText }) {
                    spacingText = it
                },
                trimEnabled = trimEnabled,
                automaticShutdownHoursText = automaticShutdownHoursText,
                onAutomaticShutdownHoursTextChange = settingsTextCorrection(automaticShutdownHoursText, { automaticShutdownHoursText }) {
                    automaticShutdownHoursText = it
                },
                automaticShutdownMinutesText = automaticShutdownMinutesText,
                onAutomaticShutdownMinutesTextChange = settingsTextCorrection(automaticShutdownMinutesText, { automaticShutdownMinutesText }) {
                    automaticShutdownMinutesText = it
                },
                automaticShutdownSecondsText = automaticShutdownSecondsText,
                onAutomaticShutdownSecondsTextChange = settingsTextCorrection(automaticShutdownSecondsText, { automaticShutdownSecondsText }) {
                    automaticShutdownSecondsText = it
                },
                alertRepeatHoursText = alertRepeatHoursText,
                onAlertRepeatHoursTextChange = settingsTextCorrection(alertRepeatHoursText, { alertRepeatHoursText }) {
                    alertRepeatHoursText = it
                },
                alertRepeatMinutesText = alertRepeatMinutesText,
                onAlertRepeatMinutesTextChange = settingsTextCorrection(alertRepeatMinutesText, { alertRepeatMinutesText }) {
                    alertRepeatMinutesText = it
                },
                alertRepeatSecondsText = alertRepeatSecondsText,
                onAlertRepeatSecondsTextChange = settingsTextCorrection(alertRepeatSecondsText, { alertRepeatSecondsText }) {
                    alertRepeatSecondsText = it
                },
                visibleCalibrationFactorText =
                    visibleCalibrationFactorText,
                onVisibleCalibrationFactorTextChange = settingsTextCorrection(visibleCalibrationFactorText, { visibleCalibrationFactorText }) {
                    visibleCalibrationFactorText = it
                },
                uvCalibrationFactorText =
                    uvCalibrationFactorText,
                onUvCalibrationFactorTextChange = settingsTextCorrection(uvCalibrationFactorText, { uvCalibrationFactorText }) {
                    uvCalibrationFactorText = it
                },
                sensorCalibrationSettings =
                    sensorCalibrationSettings,
                alertSoundValue = alertSoundValue,
                alertVolume = alertVolume,
                thresholdAlertSettings = thresholdAlertSettings,
                sensorHardwareUid = sensorProfileHardwareUid,
                sensorNameText = sensorNameText,
                appliedSensorDisplayName = sensorDisplayName,
                onSensorNameTextChange = settingsTextCorrection(sensorNameText, { sensorNameText }) {
                    sensorNameText = it
                },
                onSaveSensorDisplayName = { displayName ->
                    withContext(Dispatchers.IO) {
                        database.renameSensor(
                            hardwareUid = sensorProfileHardwareUid,
                            displayName = displayName
                        ) != null
                    }
                },
                sensorSettingsEnabled = sensorControlConnectionAvailable,
                sensorCalibrationEnabled =
                    sensorControlConnectionAvailable &&
                        sensorCalibrationSupported,
                sensorFirmwareCurrent = sensorFirmwareCurrent,
                sensorWifiRadioEnabled = sensorWifiRadioEnabled,
                sensorBluetoothRadioEnabled =
                    sensorBluetoothRadioEnabled,
                sensorInternetEnabled = sensorInternetEnabled,
                sensorInternetUsePrimaryWifi = sensorInternetUsePrimaryWifi,
                sensorInternetWifiSsid = sensorInternetWifiSsid,
                sensorInternetWifiPassword = sensorInternetWifiPassword,
                sensorInternetRelayHost = sensorInternetRelayHost,
                sensorInternetRelayPortText = sensorInternetRelayPort,
                sensorInternetMqttUsername = sensorInternetMqttUsername,
                sensorInternetMqttPassword = sensorInternetMqttPassword,
                appliedSensorInternetConfiguration =
                    appliedSensorInternetConfiguration,
                onAppliedSensorInternetConfigurationChange = {
                    appliedSensorInternetConfiguration = it
                },
                appliedSensorWifiRadioEnabled =
                    appliedSensorWifiRadioEnabled,
                onAppliedSensorWifiRadioEnabledChange = {
                    appliedSensorWifiRadioEnabled = it
                },
                appliedSensorBluetoothRadioEnabled =
                    appliedSensorBluetoothRadioEnabled,
                onAppliedSensorBluetoothRadioEnabledChange = {
                    appliedSensorBluetoothRadioEnabled = it
                },
                sensorConnectionMode = sensorConnectionMode,
                sensorParameters = SensorParameters(
                    autonomousRecordingEnabled =
                        autonomousRecordingEnabled,
                    automaticShutdownEnabled =
                        automaticShutdownEnabled,
                    automaticShutdownSeconds =
                        sensorParameters.automaticShutdownSeconds,
                    statusLedEnabled = statusLedEnabled,
                    statusLedBrightness =
                        statusLedBrightness.roundToInt().coerceIn(1, 100),
                    statusBuzzerEnabled = statusBuzzerEnabled,
                    statusBuzzerVolume =
                        statusBuzzerVolume.roundToInt().coerceIn(1, 100)
                ),
                appliedSensorParameters = sensorParameters,
                appliedAcquisitionParameters =
                    AcquisitionParameters(
                        samplesPerMeasurement = samplesPerMeasurement,
                        sampleSpacingMs = sampleSpacingMs,
                        discardExtremes = discardExtremes
                    ),
                fakeSensorDataEnabled = fakeSensorDataEnabled,
                numericFormatValue = numericFormatValue,
                sensorRadioConnectionRequiredText =
                    sensorRadioConnectionRequiredText,
                onFirmwareUpdateRequired = {
                    showFirmwareUpdateRequiredDialog = true
                },
                valueCorrectedText =
                    valueOutOfLimitsCorrectedText,
                parametersSavedText = parametersSavedText,
                onParametersErrorChange = {
                    parametersError = it
                },
                onApplySensorRadioSettings =
                    onApplySensorRadioSettings,
                onApplySensorInternetConfiguration =
                    onConfigureSensorInternet,
                onApplySensorParameters = onApplySensorParameters,
                onApplySensorCalibration = onApplySensorCalibration,
                onSensorConnectionModeChanged =
                    onSensorConnectionModeChanged,
                onApplyAcquisitionParameters =
                    onApplyAcquisitionParameters,
                onApplyThresholdAlertSettings = onSaveThresholdAlertPreferences,
                onUseFakeSensorDataChanged =
                    onUseFakeSensorDataChanged,
                onCommitAppLanguage = onCommitAppLanguage,
                onApplyNumericFormat = onApplyNumericFormat,
                sensorWifiSsid = sensorWifiSsid,
                sensorWifiPassword = sensorWifiPassword,
                appliedSensorWifiSsid = sensorCredentials.wifiSsid,
                appliedSensorWifiPassword = sensorCredentials.wifiPassword,
                onConfigureSensorWifi = onConfigureSensorWifi
            )
        suspend { applyUvirSettingsGroup(input) }
    }
    UvirFullScreenPage(
        onDismissRequest = {
            settingsSaveQueue.finishEditing()
            settingsFocusManager.clearFocus(force = true)

            parametersError =
                null

            if (selectedSettingsPage != null) {
                selectedSettingsPage = null
                settingsApplyScope.launch {
                    parametersScrollState.scrollTo(0)
                }
            } else {
                showParametersDialog = false
            }
        },

        title = {
            UvirMenuTitle(
                text = stringResource(
                    when (selectedSettingsPage) {
                        UvirSettingsPage.SENSOR_CONNECTION ->
                            R.string.settings_section_sensor_connection
                        UvirSettingsPage.SENSOR_PARAMETERS ->
                            R.string.settings_section_sensor_parameters
                        UvirSettingsPage.SENSOR_CALIBRATION ->
                            R.string.sensor_calibration_title
                        UvirSettingsPage.SAMPLING ->
                            R.string.settings_section_acquisition
                        UvirSettingsPage.ALERTS ->
                            R.string.threshold_alerts_title
                        UvirSettingsPage.NUMERIC_FORMAT ->
                            R.string.settings_section_numeric_format
                        UvirSettingsPage.LANGUAGE ->
                            R.string.settings_section_language
                        UvirSettingsPage.DATA_RESTORE ->
                            R.string.data_and_restore_title
                        UvirSettingsPage.DEBUG ->
                            R.string.settings_section_debug
                        null -> R.string.acquisition_parameters
                    }
                )
            )
        },

        containerColor =
            backgroundColor,

        contentColor =
            primaryText,

        scrollState =
            parametersScrollState,

        scrollbarColor =
            secondaryText.copy(
                alpha = 0.46f
            ),

        text = {
            CompositionLocalProvider(
                LocalSettingsCommit provides UvirSettingsCommitScope(settingsSaveQueue, SettingsSaveGroup.PARAMETERS),
                LocalUvirSettingsNavigation provides UvirSettingsNavigation(
                    selectedPage = selectedSettingsPage,
                    onOpenPage = { page ->
                        settingsSaveQueue.finishEditing()
                        settingsFocusManager.clearFocus(force = true)
                        selectedSettingsPage = page
                        settingsApplyScope.launch {
                            parametersScrollState.scrollTo(0)
                        }
                    }
                )
            ) {

            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTapGestures(onTap = { settingsFocusManager.clearFocus() })
                        }
                        .verticalScroll(
                            parametersScrollState
                        ),
                verticalArrangement =
                    Arrangement.spacedBy(
                        UvirIslandSpacing
                    )
            ) {

                SettingsAutoSaveGroup(SettingsSaveGroup.RADIO) {
                UvirSensorConnectionSettings(
                    context = context,
                    sensorCredentials = sensorCredentials,
                    sensorConnectionSectionExpanded =
                        settingsExpansion.sensorConnection,
                    onSensorConnectionSectionExpandedChange = {
                        settingsExpansion.sensorConnection = it
                    },
                    sensorSettingsEnabled =
                        sensorControlConnectionAvailable,
                    usbSectionExpanded = settingsExpansion.usb,
                    onUsbSectionExpandedChange = {
                        settingsExpansion.usb = it
                    },
                    wifiSectionExpanded = settingsExpansion.wifi,
                    onWifiSectionExpandedChange = {
                        settingsExpansion.wifi = it
                    },
                    bluetoothSectionExpanded = settingsExpansion.bluetooth,
                    onBluetoothSectionExpandedChange = {
                        settingsExpansion.bluetooth = it
                    },
                    internetSectionExpanded = settingsExpansion.internet,
                    onInternetSectionExpandedChange = {
                        settingsExpansion.internet = it
                    },
                    sensorWifiRadioEnabled = sensorWifiRadioEnabled,
                    onSensorWifiRadioEnabledChange = {
                        sensorWifiRadioEnabled = it
                    },
                    sensorBluetoothRadioEnabled =
                        sensorBluetoothRadioEnabled,
                    onSensorBluetoothRadioEnabledChange = {
                        sensorBluetoothRadioEnabled = it
                    },
                    sensorWifiSsid = sensorWifiSsid,
                    onSensorWifiSsidChange = {
                        sensorWifiSsid = it
                    },
                    sensorWifiPassword = sensorWifiPassword,
                    onSensorWifiPasswordChange = {
                        sensorWifiPassword = it
                    },
                    sensorInternetEnabled = sensorInternetEnabled,
                    onSensorInternetEnabledChange = {
                        sensorInternetEnabled = it
                    },
                    sensorInternetUsePrimaryWifi = sensorInternetUsePrimaryWifi,
                    onSensorInternetUsePrimaryWifiChange = {
                        sensorInternetUsePrimaryWifi = it
                    },
                    sensorInternetWifiSsid = sensorInternetWifiSsid,
                    onSensorInternetWifiSsidChange = {
                        sensorInternetWifiSsid = it
                    },
                    sensorInternetWifiPassword = sensorInternetWifiPassword,
                    onSensorInternetWifiPasswordChange = {
                        sensorInternetWifiPassword = it
                    },
                    sensorInternetRelayHost = sensorInternetRelayHost,
                    onSensorInternetRelayHostChange = {
                        sensorInternetRelayHost = it
                    },
                    sensorInternetRelayPort = sensorInternetRelayPort,
                    onSensorInternetRelayPortChange = {
                        sensorInternetRelayPort = it
                    },
                    sensorInternetMqttUsername = sensorInternetMqttUsername,
                    onSensorInternetMqttUsernameChange = {
                        sensorInternetMqttUsername = it
                    },
                    sensorInternetMqttPassword = sensorInternetMqttPassword,
                    onSensorInternetMqttPasswordChange = {
                        sensorInternetMqttPassword = it
                    },
                    wifiConfigurationInProgress =
                        wifiConfigurationInProgress,
                    onWifiConfigurationInProgressChange = {
                        wifiConfigurationInProgress = it
                    },
                    settingsApplyInProgress = settingsApplyInProgress,
                    settingsApplyScope = settingsApplyScope,
                    sensorFirmwareCurrent = sensorFirmwareCurrent,
                    onFirmwareUpdateRequired = {
                        showFirmwareUpdateRequiredDialog = true
                    },
                    onConfigureSensorWifi = onConfigureSensorWifi,
                    onSensorConnectionModeChanged =
                        onSensorConnectionModeChanged,
                    cardColor = cardColor,
                    primaryText = primaryText,
                    secondaryText = secondaryText
                )
                }

                UvirSensorParametersSettings(
                    context = context,
                    expanded = settingsExpansion.sensorParameters,
                    onExpandedChange = {
                        settingsExpansion.sensorParameters = it
                    },
                    sensorSettingsEnabled =
                        sensorControlConnectionAvailable,
                    sensorName = sensorNameText,
                    onSensorNameChange = {
                        sensorNameText = it
                    },
                    sensorNameEditable =
                        sensorProfileHardwareUid.isNotBlank(),
                    autonomousRecordingEnabled =
                        autonomousRecordingEnabled,
                    onAutonomousRecordingEnabledChange = {
                        autonomousRecordingEnabled = it
                    },
                    automaticShutdownEnabled =
                        automaticShutdownEnabled,
                    onAutomaticShutdownEnabledChange = {
                        automaticShutdownEnabled = it
                    },
                    automaticShutdownHoursText =
                        automaticShutdownHoursText,
                    onAutomaticShutdownHoursTextChange = {
                        automaticShutdownHoursText = it
                    },
                    automaticShutdownMinutesText =
                        automaticShutdownMinutesText,
                    onAutomaticShutdownMinutesTextChange = {
                        automaticShutdownMinutesText = it
                    },
                    automaticShutdownSecondsText =
                        automaticShutdownSecondsText,
                    onAutomaticShutdownSecondsTextChange = {
                        automaticShutdownSecondsText = it
                    },
                    statusLedEnabled = statusLedEnabled,
                    onStatusLedEnabledChange = {
                        statusLedEnabled = it
                    },
                    statusLedBrightness = statusLedBrightness,
                    onStatusLedBrightnessChange = {
                        statusLedBrightness = it
                    },
                    statusBuzzerEnabled = statusBuzzerEnabled,
                    onStatusBuzzerEnabledChange = {
                        statusBuzzerEnabled = it
                    },
                    statusBuzzerVolume = statusBuzzerVolume,
                    onStatusBuzzerVolumeChange = {
                        statusBuzzerVolume = it
                    },
                    statusLedTestEnabled = statusLedTestEnabled,
                    onTestStatusLed = onTestSensorStatusLed,
                    statusBuzzerTestEnabled = statusBuzzerTestEnabled,
                    onTestStatusBuzzer = onTestSensorStatusBuzzer,
                    sensorAssociated =
                        sensorCredentials.isProvisioned,
                    sensorPowerOffEnabled = sensorPowerOffEnabled,
                    onDisassociateSensor = onDisassociateSensor,
                    onRestoreSensor = onRestoreSensorDefaults,
                    cardColor = cardColor,
                    primaryText = primaryText,
                    secondaryText = secondaryText
                )

                SettingsAutoSaveGroup(SettingsSaveGroup.CALIBRATION) {
                UvirSensorCalibrationSettings(
                    context = context,
                    expanded = settingsExpansion.sensorCalibration,
                    onExpandedChange = {
                        settingsExpansion.sensorCalibration = it
                    },
                    enabled =
                        sensorControlConnectionAvailable &&
                            sensorCalibrationSupported,
                    onUnsupportedInteraction = {
                        if (
                            sensorControlConnectionAvailable &&
                            !sensorCalibrationSupported
                        ) {
                            showFirmwareUpdateRequiredDialog = true
                        }
                    },
                    uvSensorAvailable = selectedSensorInfo.uvAvailable == true,
                    visibleFactorText = visibleCalibrationFactorText,
                    onVisibleFactorTextChange = {
                        visibleCalibrationFactorText = it
                    },
                    uvFactorText = uvCalibrationFactorText,
                    onUvFactorTextChange = {
                        uvCalibrationFactorText = it
                    },
                    cardColor = cardColor,
                    primaryText = primaryText,
                    secondaryText = secondaryText
                )
                }

                SettingsAutoSaveGroup(SettingsSaveGroup.ALERTS) {
                UvirSamplingAndAlertSettings(
                    context = context,
                    samplingSectionExpanded = settingsExpansion.sampling,
                    onSamplingSectionExpandedChange = {
                        settingsExpansion.sampling = it
                    },
                    sensorSettingsEnabled =
                        sensorControlConnectionAvailable,
                    samplesText = samplesText,
                    onSamplesTextChange = {
                        samplesText = it
                    },
                    spacingText = spacingText,
                    onSpacingTextChange = {
                        spacingText = it
                    },
                    trimEnabled = trimEnabled,
                    onTrimEnabledChange = {
                        trimEnabled = it
                    },
                    parametersError = parametersError,
                    alertsSectionExpanded = settingsExpansion.alerts,
                    onAlertsSectionExpandedChange = {
                        settingsExpansion.alerts = it
                    },
                    alertRepeatHoursText = alertRepeatHoursText,
                    onAlertRepeatHoursTextChange = {
                        alertRepeatHoursText = it
                    },
                    alertRepeatMinutesText = alertRepeatMinutesText,
                    onAlertRepeatMinutesTextChange = {
                        alertRepeatMinutesText = it
                    },
                    alertRepeatSecondsText = alertRepeatSecondsText,
                    onAlertRepeatSecondsTextChange = {
                        alertRepeatSecondsText = it
                    },
                    alertSoundValue = alertSoundValue,
                    onAlertSoundValueChange = {
                        alertSoundValue = it
                    },
                    alertVolume = alertVolume,
                    onAlertVolumeChange = {
                        alertVolume = it
                    },
                    onPreviewThresholdAlertSound =
                        onPreviewThresholdAlertSound,
                    cardColor = cardColor,
                    primaryText = primaryText,
                    secondaryText = secondaryText
                )
                }
                CompositionLocalProvider(LocalSettingsCommit provides null) {
                UvirSettingsGeneralSections(
                    database = database,
                    autoEnabled = autoEnabled,
                    numericFormatValue = numericFormatValue,
                    onNumericFormatValueChange = {
                        numericFormatValue = it
                        settingsSaveQueue.finishEditing()
                        settingsFocusManager.clearFocus(force = true)
                        settingsSaveQueue.request(SettingsSaveGroup.NUMERIC_FORMAT)
                    },
                    numericFormatSectionExpanded =
                        settingsExpansion.numericFormat,
                    onNumericFormatSectionExpandedChange = {
                        settingsExpansion.numericFormat = it
                    },
                    appLanguage = appLanguage,
                    onAppLanguageChanged = {
                        settingsSaveQueue.finishEditing()
                        settingsFocusManager.clearFocus(force = true)
                        onAppLanguageChanged(it)
                        settingsSaveQueue.request(SettingsSaveGroup.LANGUAGE)
                    },
                    languageSectionExpanded =
                        settingsExpansion.language,
                    onLanguageSectionExpandedChange = {
                        settingsExpansion.language = it
                    },
                    countersSectionExpanded =
                        settingsExpansion.counters,
                    onCountersSectionExpandedChange = {
                        settingsExpansion.counters = it
                    },
                    onResetAllRequested = {
                        showCounterResetConfirmation = true
                    },
                    onRestoreDefaultsRequested = {
                        showRestoreDefaultsConfirmation = true
                    },
                    debugSectionExpanded = settingsExpansion.debug,
                    onDebugSectionExpandedChange = {
                        settingsExpansion.debug = it
                    },
                    fakeSensorDataEnabled = fakeSensorDataEnabled,
                    onFakeSensorDataEnabledChange = {
                        settingsSaveQueue.finishEditing()
                        settingsFocusManager.clearFocus(force = true)
                        fakeSensorDataEnabled = it
                        settingsSaveQueue.request(SettingsSaveGroup.FAKE_DATA)
                    },
                    debugPerformanceEnabled = debugPerformanceEnabled,
                    debugPerformanceSensorConnected =
                        sensorControlConnectionAvailable,
                    debugPerformanceFirmwareSupported =
                        debugPerformanceFirmwareSupported,
                    debugPerformanceCompletionToken =
                        debugPerformanceCompletionToken,
                    onStartDebugPerformance =
                        onStartDebugPerformance,
                    onStopDebugPerformance = onStopDebugPerformance,
                    diagnosticSensorConnected = selectedSensorConnected,
                    diagnosticSensorInfo = selectedSensorInfo,
                    diagnosticConnectionMode = sensorConnectionMode,
                    diagnosticSensorName = detailSensorDisplayName(
                        sensorProfiles.firstOrNull {
                            it.hardwareUid.equals(selectedSensorDeviceId, ignoreCase = true)
                        }
                    ),
                    onDiagnosticProbe = onDiagnosticProbe,
                    cardColor = cardColor,
                    primaryText = primaryText,
                    secondaryText = secondaryText
                )
                }

            }
            }
        }

    )
    return
}

// -------------------------------------------------
// MAIN SCREEN
// -------------------------------------------------

val openSettingsFromHome: () -> Unit = {
    sensorNameText = sensorDisplayName
    autonomousRecordingEnabled = sensorParameters.autonomousRecordingEnabled
    automaticShutdownEnabled = sensorParameters.automaticShutdownEnabled
    automaticShutdownHoursText =
        (sensorParameters.automaticShutdownSeconds / 3_600).toString()
    automaticShutdownMinutesText =
        ((sensorParameters.automaticShutdownSeconds % 3_600) / 60).toString()
    automaticShutdownSecondsText =
        (sensorParameters.automaticShutdownSeconds % 60).toString()
    statusLedEnabled = sensorParameters.statusLedEnabled
    statusLedBrightness = sensorParameters.statusLedBrightness.toFloat()
    statusBuzzerEnabled = sensorParameters.statusBuzzerEnabled
    statusBuzzerVolume = sensorParameters.statusBuzzerVolume.toFloat()
    samplesText = samplesPerMeasurement.toString()
    spacingText = sampleSpacingMs.toString()
    trimEnabled = discardExtremes
    alertRuleEnabledStates.clear()
    alertRuleDirectionValues.clear()
    alertRuleThresholdTexts.clear()

    thresholdAlertSettings.rules.forEach { rule ->
        alertRuleEnabledStates[rule.metric] = rule.enabled
        alertRuleDirectionValues[rule.metric] = rule.direction.name
        alertRuleThresholdTexts[rule.metric] = rule.threshold.toString()
    }

    alertRepeatHoursText =
        (thresholdAlertSettings.repeatSeconds / 3_600).toString()
    alertRepeatMinutesText =
        ((thresholdAlertSettings.repeatSeconds % 3_600) / 60).toString()
    alertRepeatSecondsText =
        (thresholdAlertSettings.repeatSeconds % 60).toString()
    alertSoundValue = thresholdAlertSettings.sound.name
    alertVolume = thresholdAlertSettings.volume.toFloat()
    fakeSensorDataEnabled = useFakeSensorData
    parametersError = null
    selectedSettingsPage = null
    showParametersDialog = true
}

val homeHeaderContent: @Composable () -> Unit = {
    UvirHomeHeader(
        sensorConnectionMode = sensorConnectionMode,
        useFakeSensorData = useFakeSensorData,
        usbSensorStatus = usbSensorState.status,
        usbAppConnectionConfirmed =
            usbSensorState.appConnectionConfirmed,
        wirelessSensorStatus = wirelessSensorState.status,
        wirelessSensorMode = wirelessSensorState.mode,
        wirelessAppConnectionConfirmed =
            wirelessSensorState.appConnectionConfirmed,
        sensorActivityInProgress =
            selectedSensorInfo.operationActive == true ||
                sensorSyncInProgress ||
                automaticStopInProgress ||
                settingsApplyInProgress ||
                wifiConfigurationInProgress ||
                autoEnabled ||
                alertSessionActive ||
                manualAcquisitionBadgePulseActive,
        versionInfoDescription = versionInfoDescription,
        primaryText = primaryText,
        secondaryText = secondaryText,
        onOpenVersionInfo = {
            showVersionInfoDialog = true
        },
        onOpenSensorSource = {
            showSensorSourceDialog = true
        },
        onOpenSettings = openSettingsFromHome
    )
}

val homeMenuContent: @Composable (Boolean) -> Unit = { pinned ->
    UvirHomeMenuBar(
        pinned = pinned,
        viewMode = viewMode,
        showChart = showLiveChart,
        monitoringAlertMetrics = monitoringAlertMetrics,
        backgroundColor = backgroundColor,
        cardColor = cardColor,
        primaryText = primaryText,
        secondaryText = secondaryText,
        unreadAcquisitionCount = unreadAcquisitionCount,
        unreadAlertCount = unreadAlertCount,
        acquisitionActivityInProgress =
            autoEnabled || manualAcquisitionBadgePulseActive,
        alertActivityInProgress = alertSessionActive,
        acquisitionSyncInProgress = acquisitionSyncInProgress,
        alertSyncInProgress = alertSyncInProgress,
        onViewModeChanged = onViewModeChanged,
        onShowChartChanged = { showChart ->
            showLiveChart = showChart
            saveLiveChartMode(context, showChart)
        },
        onOpenHistory = onOpenHistory,
        onOpenAlertLog = {
            onAlertLogViewed()
            alertLogEntries = database.readThresholdAlertLog()
            showParametersDialog = false
            showAlertLogDialog = true
        }
    )
}

Scaffold(
containerColor =
backgroundColor,

floatingActionButton = {
    val compactOfflineNotice =
        offlineDisconnectionNotice
            ?.takeIf { !sensorOperationsAvailable }

    Column(
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(UvirFloatingControlSpacing)
    ) {
        if (alertSessionActive) {
            UvirStopAllAlertsFloatingButton(
                enabled = sensorOperationsAvailable,
                onClick = {
                    showStopAllAlertsConfirmation = true
                },
                modifier = Modifier.padding(end = 7.dp)
            )
        } else if (alertSessionReadyToStart) {
            UvirStartAllAlertsFloatingButton(
                onClick = {
                    pendingAlertSessionNote = thresholdAlertSessionNote
                    showStartAllAlertsConfirmation = true
                },
                modifier = Modifier.padding(end = 7.dp)
            )
        }

        Row(
            modifier = Modifier.widthIn(
                max = (LocalConfiguration.current.screenWidthDp - 32).coerceAtLeast(0).dp
            ),
            horizontalArrangement = Arrangement.spacedBy(UvirFloatingControlSpacing),
            verticalAlignment = Alignment.CenterVertically
        ) {
            compactOfflineNotice?.let { notice ->
                UvirCompactOfflineAutonomyNotice(
                    notice = notice,
                    primaryText = primaryText,
                    modifier = Modifier.weight(1f, fill = false)
                )
            }

            if (!autoEnabled) {
                UvirAutomaticAcquisitionShortcut(
                    onClick = { openAutomaticAcquisitionPage() }
                )
            }

            if (autoEnabled) {
                PulsingAutomaticCountBadge(
                    completed = autoCompletedCount,
                    syncInProgress = acquisitionSyncInProgress,
                    onClick = {
                        openAutomaticAcquisitionPage()
                    },
                    color =
                        uvirSessionIndicatorColor(
                            isSystemInDarkTheme()
                        ),
                    containerColor = cardColor,
                    modifier = Modifier.size(56.dp)
                )
            } else if (liveReady) {
                FloatingActionButton(
                    onClick = {
                        note = ""
                        refreshRecentManualSessionState()
                        showSaveDialog = true
                    },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    CaptureMeasurementIcon(
                        modifier = Modifier.size(25.dp),
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            } else {
                Surface(
                    modifier = Modifier.size(56.dp),
                    shape = FloatingActionButtonDefaults.shape,
                    color = uvirDisabledActionContainerColor(),
                    contentColor = uvirDisabledActionContentColor(),
                    shadowElevation = 5.dp
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CaptureMeasurementIcon(
                            modifier = Modifier.size(25.dp),
                            tint = LocalContentColor.current
                        )
                    }
                }
            }
        }
    }
}

) { paddingValues ->
    if (!sensorOperationsAvailable) {
        var disconnectedIslandHeightPx by remember {
            mutableIntStateOf(0)
        }
        var disconnectedTopChromeHeightPx by remember {
            mutableIntStateOf(0)
        }
        val disconnectedScrollState = rememberScrollState()

        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(
                        start = 20.dp,
                        end = 20.dp,
                        top = 4.dp
                    )
        ) {
            Column(
                modifier =
                    Modifier.onSizeChanged {
                        disconnectedTopChromeHeightPx = it.height
                    }
            ) {
                homeHeaderContent()
                Spacer(modifier = Modifier.height(UvirIslandSpacing))
                homeMenuContent(false)
                Spacer(modifier = Modifier.height(UvirIslandSpacing))
            }

            BoxWithConstraints(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
            ) {
                val density = LocalDensity.current
                val floatingControlsClearance =
                    if (alertFloatingControlVisible) {
                        136.dp
                    } else {
                        84.dp
                    }
                val clearancePx =
                    with(density) {
                        floatingControlsClearance.toPx()
                    }
                val wholeScreenCenterInRemainingSpacePx =
                    (constraints.maxHeight - disconnectedTopChromeHeightPx) / 2f
                val centeredIslandTopPx =
                    wholeScreenCenterInRemainingSpacePx -
                        disconnectedIslandHeightPx / 2f
                val centeredIslandBottomPx =
                    wholeScreenCenterInRemainingSpacePx +
                        disconnectedIslandHeightPx / 2f
                val safeBottomPx =
                    constraints.maxHeight - clearancePx
                val requiresCompactHeightLayout =
                    disconnectedIslandHeightPx > 0 &&
                        (
                            centeredIslandTopPx < 0f ||
                                centeredIslandBottomPx > safeBottomPx
                        )

                val disconnectedIsland: @Composable () -> Unit = {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .onSizeChanged {
                                    disconnectedIslandHeightPx = it.height
                                }
                    ) {
                        UvirDisconnectedSensorIsland(
                            cardColor = cardColor,
                            primaryText = primaryText,
                            secondaryText = secondaryText
                        )
                    }
                }

                if (requiresCompactHeightLayout) {
                    val safeContentHeight =
                        (maxHeight - floatingControlsClearance)
                            .coerceAtLeast(0.dp)

                    Column(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .verticalScroll(disconnectedScrollState)
                    ) {
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = safeContentHeight),
                            contentAlignment = Alignment.Center
                        ) {
                            disconnectedIsland()
                        }
                        Spacer(
                            modifier =
                                Modifier.height(floatingControlsClearance)
                        )
                    }
                } else {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .offset {
                                    IntOffset(
                                        x = 0,
                                        y = -disconnectedTopChromeHeightPx / 2
                                    )
                                },
                        contentAlignment = Alignment.Center
                    ) {
                        disconnectedIsland()
                    }
                }
            }
        }
    } else {
    LazyColumn(
        state =
            liveListState,
        modifier =
            Modifier
                .fillMaxSize()
                .padding(
                    paddingValues
                )
                .drawWithContent {
                    drawContent()

                    val glowHeight =
                        76.dp.toPx()
                    val glowColor =
                        Color(0xFFF57C00).copy(
                            alpha = alertGlowAlpha
                        )

                    if (alertEdgeGlowDirections.first) {
                        drawRect(
                            brush =
                                Brush.verticalGradient(
                                    colors =
                                        listOf(
                                            glowColor,
                                            Color.Transparent
                                        ),
                                    startY = 0f,
                                    endY = glowHeight
                                ),
                            size =
                                Size(
                                    size.width,
                                    glowHeight
                                )
                        )
                    }

                    if (alertEdgeGlowDirections.second) {
                        drawRect(
                            brush =
                                Brush.verticalGradient(
                                    colors =
                                        listOf(
                                            Color.Transparent,
                                            glowColor
                                        ),
                                    startY =
                                        size.height - glowHeight,
                                    endY = size.height
                                ),
                            topLeft =
                                Offset(
                                    0f,
                                    size.height - glowHeight
                                ),
                            size =
                                Size(
                                    size.width,
                                    glowHeight
                                )
                        )
                    }
                }
                .lazyScrollbarOverlay(
                    state =
                        liveListState,
                    color =
                        secondaryText.copy(
                            alpha = 0.46f
                        )
                ),

        contentPadding =
            PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = 4.dp,
                bottom =
                    if (alertFloatingControlVisible) {
                        150.dp
                    } else {
                        100.dp
                    }
            ),

        verticalArrangement =
            Arrangement.spacedBy(
                UvirIslandSpacing
            )
    ) {

        item {
            homeHeaderContent()
        }

        stickyHeader {
            homeMenuContent(homeMenuPinned)
        }

        if (
            viewMode ==
            ViewMode.IRRADIANCE
        ) {

            itemsIndexed(
                items = groups,

                key = { _, group ->
                    group.name
                }
            ) { _, group ->

                SensorGroupContent(
                    group = group,

                    expanded =
                        expandedStates[group]
                            ?: false,

                    onToggle = {

                        val newState =
                            !(
                                    expandedStates[group]
                                        ?: false
                                    )

                        expandedStates[group] =
                            newState

                        saveExpandedState(
                            context,
                            group,
                            newState
                        )
                    },

                    showChart = showLiveChart,

                    liveHistory = liveChartHistory,

                    sample =
                        measurement,

                    uvTotal =
                        uvTotal,

                    visibleTotal =
                        visibleTotal,

                    nirTotal =
                        nirTotal,

                    hev =
                        hev,

                    alertedMetrics =
                        activeAlertMetrics,

                    configuredAlertMetrics =
                        configuredAlertMetrics,

                    monitoringAlertMetrics =
                        monitoringAlertMetrics,

                    onConfigureAlert = {
                        it.sensorGroup()?.let { group ->
                            openThresholdAlertGroup(group)
                        }
                    },

                    cardColor =
                        cardColor,

                    primaryText =
                        primaryText,

                    secondaryText =
                        secondaryText,

                    trackColor =
                        trackColor
                )
            }

        } else {

            item {

                BiologicalEffectsContent(
                    sample =
                        measurement,

                    expanded = expandedStates[SensorGroup.BIOLOGICAL] ?: true,
                    onToggle = {
                        val newState = !(expandedStates[SensorGroup.BIOLOGICAL] ?: true)
                        expandedStates[SensorGroup.BIOLOGICAL] = newState
                        saveExpandedState(context, SensorGroup.BIOLOGICAL, newState)
                    },

                    showChart = showLiveChart,

                    liveHistory = liveChartHistory,

                    alertedMetrics =
                        activeAlertMetrics,

                    configuredAlertMetrics =
                        configuredAlertMetrics,

                    monitoringAlertMetrics =
                        monitoringAlertMetrics,

                    onConfigureAlerts = {
                        openThresholdAlertGroup(
                            SensorGroup.BIOLOGICAL
                        )
                    },

                    cardColor =
                        cardColor,

                    primaryText =
                        primaryText,

                    secondaryText =
                        secondaryText
                )
            }
        }
    }
    }
}

}
