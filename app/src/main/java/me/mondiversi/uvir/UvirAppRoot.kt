package me.mondiversi.uvir

import android.Manifest
import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.ContextWrapper
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
import android.util.Log
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
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
import kotlinx.coroutines.flow.collect
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import java.io.File
import org.json.JSONObject
import kotlin.math.roundToInt
import kotlin.random.Random

private const val FAKE_SENSOR_SETTINGS_ID = "__fake_sensor__"

private tailrec fun Context.findActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }

@Composable
internal fun UvirAppContent(
    remoteNetworkEnabled: Boolean,
    usbSensorManager: UvirUsbSensorManager,
    wirelessSensorManager: UvirWirelessSensorManager,
    onRequestBluetoothPermission: () -> Unit,
    currentWifiSsid: String?,
    onRequestCurrentWifiSsid: () -> Unit,
    openHomeRequestId: Long = 0L,
    onSensorSelected: (String) -> Unit
) {

    val context = LocalContext.current
    val resources = LocalResources.current

    val usbSensorState by
        usbSensorManager.state.collectAsState()

    val wirelessSensorState by
        wirelessSensorManager.state.collectAsState()

    val database = remember {
        UvirDatabaseHelper(
            context.applicationContext
        ).also { helper ->
            // Open immediately so a schema reset also clears stale AUTO state
            // before the related Compose state is restored below.
            helper.writableDatabase
        }
    }

    var sensorSyncSummary by remember {
        mutableStateOf<SensorSyncSummary?>(null)
    }
    var sensorSyncIncompleteSummary by remember {
        mutableStateOf<SensorSyncIncompleteSummary?>(null)
    }
    var sensorSyncInProgress by remember {
        mutableStateOf(false)
    }
    var acquisitionSyncInProgress by remember {
        mutableStateOf(false)
    }
    var alertSyncInProgress by remember {
        mutableStateOf(false)
    }
    var sensorSyncReady by remember {
        mutableStateOf(false)
    }
    var sensorAssociationRevision by remember { mutableIntStateOf(0) }

    DisposableEffect(Unit) {
        onDispose {
            database.close()
        }
    }

    val darkMode = isSystemInDarkTheme()

    val backgroundColor =
        if (darkMode) Color(0xFF101418)
        else Color(0xFFF4F7F9)

    val cardColor =
        if (darkMode) Color(0xFF1C242B)
        else Color.White

    val primaryText =
        if (darkMode) Color.White
        else Color(0xFF101418)

    val secondaryText =
        if (darkMode) Color(0xFF90A4AE)
        else Color(0xFF546E7A)

    val trackColor =
        if (darkMode) Color(0xFF37474F)
        else Color(0xFFDCE3E7)

    val view =
        LocalView.current

    SideEffect {
        val activity =
            view.context as? Activity

        activity?.window?.let { window ->
            window.statusBarColor =
                backgroundColor.toArgb()

            window.navigationBarColor =
                backgroundColor.toArgb()

            val insetsController =
                WindowCompat
                .getInsetsController(
                    window,
                    view
                )

            insetsController.isAppearanceLightStatusBars =
                !darkMode
            insetsController.isAppearanceLightNavigationBars =
                !darkMode

            if (Build.VERSION.SDK_INT >= 29) {
                window.isStatusBarContrastEnforced =
                    false
                window.isNavigationBarContrastEnforced =
                    false
            }
        }
    }

    val preferences = remember {
        context.getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE
        )
    }

    var acquisitionHistoryVisible by rememberSaveable {
        mutableStateOf(false)
    }

    var alertLogVisible by rememberSaveable {
        mutableStateOf(false)
    }

    var unreadAcquisitionCount by rememberSaveable {
        mutableIntStateOf(
            preferences.getInt(
                KEY_UNREAD_ACQUISITION_COUNT,
                0
            )
        )
    }
    var unreadAlertCount by rememberSaveable {
        mutableIntStateOf(
            preferences.getInt(
                KEY_UNREAD_ALERT_COUNT,
                0
            )
        )
    }

    fun incrementUnreadAcquisitions(amount: Int = 1) {
        if (amount <= 0 || acquisitionHistoryVisible) return
        unreadAcquisitionCount =
            (unreadAcquisitionCount + amount)
                .coerceAtMost(9_999)
        preferences.edit()
            .putInt(
                KEY_UNREAD_ACQUISITION_COUNT,
                unreadAcquisitionCount
            )
            .apply()
    }

    fun incrementUnreadAlerts(amount: Int = 1) {
        if (amount <= 0 || alertLogVisible) return
        unreadAlertCount =
            (unreadAlertCount + amount)
                .coerceAtMost(9_999)
        preferences.edit()
            .putInt(
                KEY_UNREAD_ALERT_COUNT,
                unreadAlertCount
            )
            .apply()
    }

    fun clearUnreadAcquisitions() {
        unreadAcquisitionCount = 0
        preferences.edit()
            .putInt(KEY_UNREAD_ACQUISITION_COUNT, 0)
            .apply()
    }

    fun clearUnreadAlerts() {
        unreadAlertCount = 0
        preferences.edit()
            .putInt(KEY_UNREAD_ALERT_COUNT, 0)
            .apply()
    }

    var storedSensorRuntimeSnapshot by remember {
        mutableStateOf(UvirSensorRuntimeInfoStore.load(context))
    }
    var pendingOfflineReopenState by remember {
        mutableStateOf(consumeOfflineReopenState(preferences))
    }

    var numericFormat by rememberSaveable {
        mutableStateOf(
            loadUvirNumericFormat(context)
        )
    }

    var dateFormat by rememberSaveable {
        mutableStateOf(
            loadUvirDateFormat(context)
        )
    }

    var timeFormat by rememberSaveable {
        mutableStateOf(
            loadUvirTimeFormat(context)
        )
    }

    var exportMode by rememberSaveable {
        mutableStateOf(
            loadUvirExportMode(context)
        )
    }

    var irradianceUnit by rememberSaveable {
        mutableStateOf(loadUvirIrradianceUnit(context))
    }

    var useFakeSensorData by rememberSaveable {
        mutableStateOf(
            preferences.getBoolean(
                KEY_USE_FAKE_SENSOR_DATA,
                true
            )
        )
    }

    var fakeSensorOutOfRangeEnabled by rememberSaveable {
        mutableStateOf(
            preferences.getBoolean(KEY_FAKE_SENSOR_OUT_OF_RANGE, false)
        )
    }

    var sensorConnectionModeValue by rememberSaveable {
        mutableStateOf(
            preferences.getString(
                KEY_SENSOR_CONNECTION_MODE,
                SensorConnectionMode.USB.name
            ) ?: SensorConnectionMode.USB.name
        )
    }

    var lastWirelessSensorConnectionModeValue by rememberSaveable {
        val storedMode =
            SensorConnectionMode.fromStoredValue(
                preferences.getString(
                    KEY_LAST_WIRELESS_SENSOR_CONNECTION_MODE,
                    sensorConnectionModeValue
                )
            )
        mutableStateOf(
            if (storedMode == SensorConnectionMode.USB) {
                SensorConnectionMode.WIFI.name
            } else {
                storedMode.name
            }
        )
    }

    var usbWasAuthorized by rememberSaveable {
        mutableStateOf(false)
    }

    val authorizedUsbConnected =
        usbSensorState.status == UsbSensorConnectionStatus.CONNECTED &&
            !usbSensorState.deviceId.isNullOrBlank()

    LaunchedEffect(authorizedUsbConnected) {
        if (authorizedUsbConnected && !usbWasAuthorized) {
            usbSensorManager.configureWirelessMode(
                SensorConnectionMode.USB
            )
            sensorConnectionModeValue =
                SensorConnectionMode.USB.name

            preferences.edit()
                .putString(
                    KEY_SENSOR_CONNECTION_MODE,
                    SensorConnectionMode.USB.name
                )
                .apply()
        }
        usbWasAuthorized = authorizedUsbConnected
    }

    LaunchedEffect(usbSensorState.error) {
        if (
            usbSensorState.error ==
            USB_SENSOR_IDENTITY_MISMATCH_ERROR
        ) {
            showUvirBottomMessage(
                context.applicationContext,
                resources.getString(
                    R.string.sensor_usb_identity_mismatch
                ),
                longDuration = true
            )
        }
    }

    var appLanguageValue by remember {
        mutableStateOf(
            preferences.getString(
                KEY_PENDING_APP_LANGUAGE,
                null
            ) ?: preferences.getString(
                    KEY_APP_LANGUAGE,
                    AppLanguage.SYSTEM.storedValue
                ) ?: AppLanguage.SYSTEM.storedValue
        )
    }

    var screen by rememberSaveable {
        mutableStateOf(AppScreen.LIVE)
    }

    var selectedRecordId by rememberSaveable {
        mutableStateOf<Long?>(null)
    }

    var selectedSessionId by rememberSaveable {
        mutableStateOf<Long?>(null)
    }

    var detailReturnScreen by rememberSaveable {
        mutableStateOf(AppScreen.HISTORY)
    }

    LaunchedEffect(screen) {
        acquisitionHistoryVisible =
            screen == AppScreen.HISTORY
        if (acquisitionHistoryVisible) {
            clearUnreadAcquisitions()
        }
    }

    LaunchedEffect(openHomeRequestId) {
        if (openHomeRequestId > 0L) {
            selectedRecordId = null
            selectedSessionId = null
            screen = AppScreen.LIVE
        }
    }

    val liveListState =
        rememberSaveable(
            saver = LazyListState.Saver
        ) {
            LazyListState()
        }

    val automaticListState =
        rememberSaveable(
            saver = LazyListState.Saver
        ) {
            LazyListState()
        }

    val historyListState =
        rememberSaveable(
            saver = LazyListState.Saver
        ) {
            LazyListState()
        }

    var historyScrollAnchor by rememberSaveable {
        mutableStateOf(
            if (
                historyListState.firstVisibleItemIndex == 0 &&
                historyListState.firstVisibleItemScrollOffset == 0
            ) {
                ListEdgeAnchor.START
            } else {
                ListEdgeAnchor.MIDDLE
            }
        )
    }

    val detailListState =
        rememberSaveable(
            saver = LazyListState.Saver
        ) {
            LazyListState()
        }

    val versionInfoScrollState =
        rememberSaveable(
            saver = ScrollState.Saver
        ) {
            ScrollState(0)
        }

    val parametersScrollState =
        rememberSaveable(
            saver = ScrollState.Saver
        ) {
            ScrollState(0)
        }

    var viewMode by remember {
        mutableStateOf(
            runCatching {
                ViewMode.valueOf(
                    preferences.getString(
                        KEY_VIEW_MODE,
                        ViewMode.IRRADIANCE.name
                    ) ?: ViewMode.IRRADIANCE.name
                )
            }.getOrDefault(
                ViewMode.IRRADIANCE
            )
        )
    }

    fun setViewMode(mode: ViewMode) {
        viewMode = mode

        preferences.edit()
            .putString(
                KEY_VIEW_MODE,
                mode.name
            )
            .apply()
    }

    // -------------------------------------------------
    // ACQUISITION PARAMETERS
    // -------------------------------------------------

    val initiallyAssociatedSensorSettings = remember(database) {
        readInitialSensorSettings(useFakeSensorData) {
            val associatedDeviceId =
                UvirSensorCredentialStore.load(context).deviceId
            associatedDeviceId
                .takeIf { it.isNotBlank() }
                ?.let { deviceId ->
                    runCatching {
                        database.readSensorSettings(deviceId)
                    }.getOrNull()
                }
        }
    }

    var samplesPerMeasurement by remember {
        mutableIntStateOf(
            initiallyAssociatedSensorSettings
                ?.acquisitionParameters
                ?.samplesPerMeasurement
                ?: preferences
                    .getInt(
                        KEY_SAMPLES_PER_MEASUREMENT,
                        5
                    )
                    .coerceIn(1, 21)
        )
    }

    var sampleSpacingMs by remember {
        mutableLongStateOf(
            initiallyAssociatedSensorSettings
                ?.acquisitionParameters
                ?.sampleSpacingMs
                ?: readSampleSpacingMs(preferences)
        )
    }

    var discardExtremes by remember {
        mutableStateOf(
            initiallyAssociatedSensorSettings
                ?.acquisitionParameters
                ?.discardExtremes
                ?: preferences.getBoolean(
                    KEY_DISCARD_EXTREMES,
                    true
                )
        )
    }

    var sensorParameters by remember {
        mutableStateOf(
            initiallyAssociatedSensorSettings?.sensorParameters
                ?: SensorParameters(
                autonomousRecordingEnabled =
                    preferences.getBoolean(
                        KEY_SENSOR_AUTONOMOUS_RECORDING,
                        true
                    ),
                automaticShutdownEnabled =
                    preferences.getBoolean(
                        KEY_SENSOR_AUTOMATIC_SHUTDOWN_ENABLED,
                        false
                    ),
                automaticShutdownSeconds =
                    preferences.getInt(
                        KEY_SENSOR_AUTOMATIC_SHUTDOWN_SECONDS,
                        1_800
                    ).coerceIn(60, 86_400),
                statusLedEnabled =
                    preferences.getBoolean(
                        KEY_SENSOR_STATUS_LED_ENABLED,
                        true
                    ),
                statusLedBrightness =
                    preferences.getInt(
                        KEY_SENSOR_STATUS_LED_BRIGHTNESS,
                        10
                    ).coerceIn(1, 100),
                statusBuzzerEnabled =
                    preferences.getBoolean(
                        KEY_SENSOR_STATUS_BUZZER_ENABLED,
                        true
                    ),
                statusBuzzerVolume =
                    preferences.getInt(
                        KEY_SENSOR_STATUS_BUZZER_VOLUME,
                        10
                    ).coerceIn(1, 100),
                externalCommandEnabled =
                    preferences.getBoolean(
                        KEY_SENSOR_EXTERNAL_COMMAND_ENABLED,
                        true
                    )
            )
        )
    }

    var sensorCalibrationSettings by remember {
        mutableStateOf(
            initiallyAssociatedSensorSettings?.calibrationSettings
                ?: SensorCalibrationSettings(
                visibleFactor =
                    preferences.getFloat(
                        KEY_SENSOR_VISIBLE_CALIBRATION_FACTOR,
                        DEFAULT_SENSOR_CALIBRATION_FACTOR
                    ).coerceIn(
                        MIN_SENSOR_CALIBRATION_FACTOR,
                        MAX_SENSOR_CALIBRATION_FACTOR
                    ),
                uvFactor =
                    preferences.getFloat(
                        KEY_SENSOR_UV_CALIBRATION_FACTOR,
                        DEFAULT_SENSOR_CALIBRATION_FACTOR
                    ).coerceIn(
                        MIN_SENSOR_CALIBRATION_FACTOR,
                        MAX_SENSOR_CALIBRATION_FACTOR
                    )
            )
        )
    }

    var thresholdAlertSettings by remember {
        mutableStateOf(
            initiallyAssociatedSensorSettings
                ?.sensorBackedAlertSettings(
                    loadThresholdAlertSettings(preferences)
                )
                ?: loadThresholdAlertSettings(preferences)
        )
    }

    var acquisitionFeedbackSettings by remember {
        mutableStateOf(
            loadAcquisitionFeedbackSettings(preferences)
        )
    }
    val currentAcquisitionFeedbackSettings by
        rememberUpdatedState(acquisitionFeedbackSettings)

    var thresholdAlertSessionId by remember {
        mutableLongStateOf(
            initiallyAssociatedSensorSettings
                ?.alertSessionId
                ?.takeIf {
                    initiallyAssociatedSensorSettings.alertMonitoringEnabled
                }
                ?: preferences.getLong(
                    KEY_THRESHOLD_ALERT_SESSION_ID,
                    0L
                ).coerceAtLeast(0L)
        )
    }

    var thresholdAlertSessionNote by remember {
        mutableStateOf(
            initiallyAssociatedSensorSettings
                ?.alertSessionId
                ?.takeIf { it > 0L }
                ?.let(database::readAlertSessionNote)
                ?.takeIf { it.isNotBlank() }
                ?: limitUvirNote(
                    preferences.getString(
                        KEY_THRESHOLD_ALERT_NOTE,
                        ""
                    ) ?: ""
                )
        )
    }

    LaunchedEffect(Unit) {
        if (
            thresholdAlertSettings.hasActiveMonitoring()
        ) {
            if (thresholdAlertSessionId == 0L) {
                thresholdAlertSessionId =
                    withContext(Dispatchers.IO) {
                        database.nextSessionId()
                    }
            }
            withContext(Dispatchers.IO) {
                database.startAlertSession(
                    sessionId = thresholdAlertSessionId,
                    note = thresholdAlertSessionNote,
                    sensorDeviceId =
                        usbSensorState.credentials.deviceId.ifBlank {
                            wirelessSensorState.deviceId.orEmpty()
                        }
                )
            }
            preferences.edit()
                .putLong(
                    KEY_THRESHOLD_ALERT_SESSION_ID,
                    thresholdAlertSessionId
                )
                .apply()
        }
    }

    var latestThresholdNotificationAlert by remember {
        mutableStateOf<ThresholdNotificationAlert?>(null)
    }

    val thresholdPreviewScope =
        rememberCoroutineScope()

    val sensorConnectionScope =
        rememberCoroutineScope()

    var thresholdPreviewJob by remember {
        mutableStateOf<Job?>(null)
    }

    var sensorConnectionSwitchJob by remember {
        mutableStateOf<Job?>(null)
    }
    var auxiliarySensorCommandInProgress by remember { mutableStateOf(false) }
    var auxiliarySensorCommandJob by remember { mutableStateOf<Job?>(null) }

    fun finishAuxiliarySensorCommand() {
        auxiliarySensorCommandJob?.cancel()
        auxiliarySensorCommandInProgress = false
    }

    fun beginAuxiliarySensorCommand(durationMs: Long) {
        finishAuxiliarySensorCommand()
        auxiliarySensorCommandInProgress = true
        auxiliarySensorCommandJob = sensorConnectionScope.launch {
            delay(durationMs)
            auxiliarySensorCommandInProgress = false
        }
    }

    LaunchedEffect(usbSensorState.debugPerformanceCompletionSequence,
        wirelessSensorState.debugPerformanceCompletionSequence) {
        finishAuxiliarySensorCommand()
    }

    fun sendSensorControlCommands(commands: List<String>): Boolean {
        val selectedMode =
            SensorConnectionMode.fromStoredValue(
                sensorConnectionModeValue
            )
        return if (
            selectedMode == SensorConnectionMode.USB &&
            usbSensorState.status == UsbSensorConnectionStatus.CONNECTED
        ) {
            usbSensorManager.sendSensorControlCommands(commands)
        } else if (
            wirelessSensorState.status ==
                WirelessSensorConnectionStatus.CONNECTED
        ) {
            wirelessSensorManager.sendSensorControlCommands(commands)
        } else if (
            usbSensorState.status == UsbSensorConnectionStatus.CONNECTED
        ) {
            usbSensorManager.sendSensorControlCommands(commands)
        } else {
            false
        }
    }

    fun runSensorStatusTest(
        led: Boolean,
        timeoutMs: Long = 5_000L
    ): Boolean {
        val selectedMode =
            SensorConnectionMode.fromStoredValue(
                sensorConnectionModeValue
            )
        return if (
            selectedMode == SensorConnectionMode.USB &&
            usbSensorState.status == UsbSensorConnectionStatus.CONNECTED
        ) {
            if (led) {
                usbSensorManager.testStatusLedAndAwait(timeoutMs)
            } else {
                usbSensorManager.testStatusBuzzerAndAwait(timeoutMs)
            }
        } else if (
            wirelessSensorState.status ==
                WirelessSensorConnectionStatus.CONNECTED
        ) {
            if (led) {
                wirelessSensorManager.testStatusLedAndAwait(timeoutMs)
            } else {
                wirelessSensorManager.testStatusBuzzerAndAwait(timeoutMs)
            }
        } else if (
            usbSensorState.status == UsbSensorConnectionStatus.CONNECTED
        ) {
            if (led) {
                usbSensorManager.testStatusLedAndAwait(timeoutMs)
            } else {
                usbSensorManager.testStatusBuzzerAndAwait(timeoutMs)
            }
        } else {
            false
        }
    }

    fun restoreSensorDefaultsAndPowerOff(
        timeoutMs: Long = 8_000L
    ): Boolean {
        val selectedMode =
            SensorConnectionMode.fromStoredValue(
                sensorConnectionModeValue
            )
        return if (
            selectedMode == SensorConnectionMode.USB &&
            usbSensorState.status == UsbSensorConnectionStatus.CONNECTED
        ) {
            usbSensorManager.restoreDefaultsAndPowerOffAwait(timeoutMs)
        } else if (
            wirelessSensorState.status ==
                WirelessSensorConnectionStatus.CONNECTED
        ) {
            wirelessSensorManager.restoreDefaultsAndPowerOffAwait(timeoutMs)
        } else if (
            usbSensorState.status == UsbSensorConnectionStatus.CONNECTED
        ) {
            usbSensorManager.restoreDefaultsAndPowerOffAwait(timeoutMs)
        } else {
            false
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            thresholdPreviewJob?.cancel()
            sensorConnectionSwitchJob?.cancel()
        }
    }

    fun previewThresholdAlertSound(
        sound: ThresholdAlertSound,
        volume: Int
    ) {
        thresholdPreviewJob?.cancel()

        thresholdPreviewJob =
            thresholdPreviewScope.launch {
                playThresholdAlertTone(
                    context = context,
                    sound = sound,
                    volume = volume
                )
            }
    }

    fun playAcquisitionFeedback(
        settings: AcquisitionFeedbackSettings =
            currentAcquisitionFeedbackSettings
    ) {
        thresholdPreviewScope.launch {
            playThresholdAlertTone(
                context = context,
                sound = settings.sound,
                volume = settings.volume,
                vibrationPulses = 1
            )
        }
    }

    // -------------------------------------------------
    // CURRENT ACQUISITION
    // Mock data can override the selected real source. USB samples arrive from
    // the ESP32 through UvirUsbSensorManager and use the same averaging path.
    // -------------------------------------------------

    var measurement by rememberSaveable(
        stateSaver = SensorSampleSaver
    ) {
        mutableStateOf(SensorSample())
    }

    // Keeps the full-rate sensor result available to automatic acquisition,
    // independently from the intentionally throttled screen refresh.
    val latestMeasurement =
        remember {
            AtomicReference(
                measurement
            )
        }

    val lastLiveUiRefreshMs =
        remember {
            AtomicLong(0L)
        }

    var liveReady by rememberSaveable {
        mutableStateOf(false)
    }

    var liveSamples by rememberSaveable(
        stateSaver = SensorSampleListSaver
    ) {
        mutableStateOf<List<SensorSample>>(
            emptyList()
        )
    }

    LaunchedEffect(
        useFakeSensorData,
        fakeSensorOutOfRangeEnabled,
        samplesPerMeasurement,
        sampleSpacingMs,
        discardExtremes
    ) {

        if (!useFakeSensorData) {
            val emptyMeasurement = SensorSample()

            liveSamples = emptyList()
            measurement = emptyMeasurement
            latestMeasurement.set(emptyMeasurement)
            liveReady = false

            return@LaunchedEffect
        }

        var lastUiRefreshMs = 0L

        while (true) {

            liveSamples =
                (
                        liveSamples +
                                generateRandomSample(fakeSensorOutOfRangeEnabled)
                        ).takeLast(
                    samplesPerMeasurement
                        .coerceAtLeast(1)
                )

            val combinedMeasurement =
                combineSamples(
                    liveSamples,
                    discardExtremes
                )

            latestMeasurement.set(
                combinedMeasurement
            )

            val nowMs =
                System.currentTimeMillis()

            if (
                lastUiRefreshMs == 0L ||
                nowMs - lastUiRefreshMs >=
                LIVE_UI_REFRESH_MS
            ) {
                measurement =
                    combinedMeasurement

                lastUiRefreshMs =
                    nowMs
            }

            liveReady =
                liveSamples.size >=
                        samplesPerMeasurement

            delay(sampleSpacingMs)
        }
    }

    val selectedSensorConnectionMode =
        SensorConnectionMode.fromStoredValue(
            sensorConnectionModeValue
        )

    val selectedRuntimeInfo =
        if (selectedSensorConnectionMode == SensorConnectionMode.USB) {
            usbSensorState.runtimeInfo
        } else {
            wirelessSensorState.runtimeInfo
        }
    val selectedSensorDeviceId =
        selectedRuntimeInfo.deviceId
            .ifBlank {
                if (selectedSensorConnectionMode == SensorConnectionMode.USB) {
                    usbSensorState.credentials.deviceId
                        .ifBlank { usbSensorState.deviceId.orEmpty() }
                } else {
                    wirelessSensorState.deviceId.orEmpty()
                }
            }
            .ifBlank { usbSensorState.credentials.deviceId }
            .ifBlank { UvirSensorCredentialStore.load(context).deviceId }
            .trim()
    val selectedSensorIsConnected =
        if (selectedSensorConnectionMode == SensorConnectionMode.USB) {
            usbSensorState.status == UsbSensorConnectionStatus.CONNECTED &&
                usbSensorState.appConnectionConfirmed
        } else {
            wirelessSensorState.status ==
                WirelessSensorConnectionStatus.CONNECTED &&
                wirelessSensorState.mode == selectedSensorConnectionMode &&
                wirelessSensorState.appConnectionConfirmed
        }
    val selectedSensorSettingsSnapshot =
        remember(selectedRuntimeInfo) {
            selectedRuntimeInfo.toSensorSettingsSnapshotOrNull()
        }
    var sensorSettingsHydratedForDeviceId by remember {
        mutableStateOf("")
    }

    LaunchedEffect(
        useFakeSensorData,
        selectedSensorIsConnected,
        selectedSensorDeviceId,
        selectedRuntimeInfo.firmwareVersion,
        selectedSensorSettingsSnapshot
    ) {
        if (useFakeSensorData) {
            sensorSettingsHydratedForDeviceId = FAKE_SENSOR_SETTINGS_ID
            return@LaunchedEffect
        }
        if (!selectedSensorIsConnected) {
            sensorSettingsHydratedForDeviceId = ""
            return@LaunchedEffect
        }

        val deviceId = selectedSensorDeviceId.trim()
        if (deviceId.isBlank()) return@LaunchedEffect
        if (
            !firmwareSupportsSensorSettingsSnapshot(
                selectedRuntimeInfo.firmwareVersion
            )
        ) {
            // Older compatible firmware keeps the previous app-authoritative
            // behavior and can still be used until it is updated.
            sensorSettingsHydratedForDeviceId = deviceId
            return@LaunchedEffect
        }

        val snapshot = selectedSensorSettingsSnapshot ?: return@LaunchedEffect
        withContext(Dispatchers.IO) {
            database.upsertSensorSettings(
                hardwareUid = deviceId,
                settings = snapshot
            )
            if (
                snapshot.alertMonitoringEnabled &&
                snapshot.alertSessionId > 0L
            ) {
                database.ensureAlertSession(
                    sessionId = snapshot.alertSessionId,
                    note = database.readAlertSessionNote(
                        snapshot.alertSessionId
                    ),
                    startedAt = System.currentTimeMillis(),
                    sensorDeviceId = deviceId
                )
            }
        }

        sensorParameters = snapshot.sensorParameters
        sensorCalibrationSettings = snapshot.calibrationSettings
        samplesPerMeasurement =
            snapshot.acquisitionParameters.samplesPerMeasurement
        sampleSpacingMs = snapshot.acquisitionParameters.sampleSpacingMs
        discardExtremes = snapshot.acquisitionParameters.discardExtremes
        thresholdAlertSettings =
            snapshot.sensorBackedAlertSettings(thresholdAlertSettings)
        thresholdAlertSessionId =
            snapshot.alertSessionId.takeIf {
                snapshot.alertMonitoringEnabled
            } ?: 0L
        thresholdAlertSessionNote =
            withContext(Dispatchers.IO) {
                restoreAlertSessionNote(
                    activeSessionId = thresholdAlertSessionId,
                    currentNote = thresholdAlertSessionNote,
                    readSessionNote = database::readAlertSessionNote
                )
            }

        preferences.edit()
            .putBoolean(
                KEY_SENSOR_AUTONOMOUS_RECORDING,
                snapshot.sensorParameters.autonomousRecordingEnabled
            )
            .putBoolean(
                KEY_SENSOR_AUTOMATIC_SHUTDOWN_ENABLED,
                snapshot.sensorParameters.automaticShutdownEnabled
            )
            .putInt(
                KEY_SENSOR_AUTOMATIC_SHUTDOWN_SECONDS,
                snapshot.sensorParameters.automaticShutdownSeconds
            )
            .putBoolean(
                KEY_SENSOR_STATUS_LED_ENABLED,
                snapshot.sensorParameters.statusLedEnabled
            )
            .putInt(
                KEY_SENSOR_STATUS_LED_BRIGHTNESS,
                snapshot.sensorParameters.statusLedBrightness
            )
            .putBoolean(
                KEY_SENSOR_STATUS_BUZZER_ENABLED,
                snapshot.sensorParameters.statusBuzzerEnabled
            )
            .putInt(
                KEY_SENSOR_STATUS_BUZZER_VOLUME,
                snapshot.sensorParameters.statusBuzzerVolume
            )
            .putInt(
                KEY_SAMPLES_PER_MEASUREMENT,
                snapshot.acquisitionParameters.samplesPerMeasurement
            )
            .putLong(
                KEY_SAMPLE_SPACING_MS,
                snapshot.acquisitionParameters.sampleSpacingMs
            )
            .putBoolean(
                KEY_DISCARD_EXTREMES,
                snapshot.acquisitionParameters.discardExtremes
            )
            .putFloat(
                KEY_SENSOR_VISIBLE_CALIBRATION_FACTOR,
                snapshot.calibrationSettings.visibleFactor
            )
            .putFloat(
                KEY_SENSOR_UV_CALIBRATION_FACTOR,
                snapshot.calibrationSettings.uvFactor
            )
            .putLong(
                KEY_THRESHOLD_ALERT_SESSION_ID,
                thresholdAlertSessionId
            )
            .putString(
                KEY_THRESHOLD_ALERT_NOTE,
                thresholdAlertSessionNote
            )
            .commit()
        saveThresholdAlertSettings(
            preferences,
            thresholdAlertSettings
        )
        sensorSettingsHydratedForDeviceId = deviceId
    }

    val selectedSensorIsConnecting =
        if (selectedSensorConnectionMode == SensorConnectionMode.USB) {
            usbSensorState.status == UsbSensorConnectionStatus.CONNECTING ||
                usbSensorState.status ==
                    UsbSensorConnectionStatus.WAITING_PERMISSION ||
                (
                    usbSensorState.status ==
                        UsbSensorConnectionStatus.CONNECTED &&
                        !usbSensorState.appConnectionConfirmed
                )
        } else {
            wirelessSensorState.status ==
                WirelessSensorConnectionStatus.CONNECTING ||
                (
                    wirelessSensorState.status ==
                        WirelessSensorConnectionStatus.CONNECTED &&
                        wirelessSensorState.mode == selectedSensorConnectionMode &&
                        !wirelessSensorState.appConnectionConfirmed
                )
        }
    LaunchedEffect(database, selectedSensorDeviceId) {
        withContext(Dispatchers.IO) {
            database.assignDefaultSensorToUnassociatedAlerts(
                UvirSensorCredentialStore.load(context).deviceId
                    .ifBlank { selectedSensorDeviceId }
            )
        }
    }
    val sensorProfileRevision by
        database.sensorProfileRevision.collectAsState()
    var sensorProfiles by remember {
        mutableStateOf<List<UvirSensorProfile>>(emptyList())
    }
    var sensorProfileConnectionState by remember {
        mutableStateOf<Pair<String, Boolean>?>(null)
    }
    LaunchedEffect(
        selectedSensorDeviceId,
        selectedSensorIsConnected,
        sensorProfileRevision
    ) {
        val normalizedSensorId = selectedSensorDeviceId.trim()
        val previousConnectionState = sensorProfileConnectionState
        val connectionStarted =
            selectedSensorIsConnected &&
                (
                    previousConnectionState == null ||
                        !previousConnectionState.second ||
                        !previousConnectionState.first.equals(
                            normalizedSensorId,
                            ignoreCase = true
                        )
                )
        val connectionEnded =
            !selectedSensorIsConnected &&
                previousConnectionState?.second == true &&
                previousConnectionState.first.equals(
                    normalizedSensorId,
                    ignoreCase = true
                )
        sensorProfileConnectionState =
            normalizedSensorId to selectedSensorIsConnected
        sensorProfiles =
            withContext(Dispatchers.IO) {
                if (
                    normalizedSensorId.isNotBlank() &&
                    (connectionStarted || connectionEnded)
                ) {
                    database.ensureSensorProfile(
                        hardwareUid = normalizedSensorId,
                        seenAt = System.currentTimeMillis()
                    )
                }
                database.readSensorProfiles()
            }
        UvirErrorLog.updateAssociatedSensor(
            profile = sensorProfiles.firstOrNull {
                it.hardwareUid.equals(normalizedSensorId, ignoreCase = true)
            },
            hardwareUid = normalizedSensorId
        )
    }
    val currentSyncConnectionMode by
        rememberUpdatedState(selectedSensorConnectionMode)
    val currentSyncSensorIsConnected by
        rememberUpdatedState(selectedSensorIsConnected)
    var lastConnectedSensorRuntimeInfo by remember {
        mutableStateOf(
            storedSensorRuntimeSnapshot?.info
                ?: selectedRuntimeInfo
        )
    }
    var sensorWasConnected by remember {
        mutableStateOf(false)
    }
    var offlineDisconnectionNotice by remember {
        mutableStateOf(
            loadPersistedOfflineDisconnectionNotice(preferences)
        )
    }

    fun updateOfflineDisconnectionNotice(
        notice: UvirOfflineDisconnectionNotice?
    ) {
        offlineDisconnectionNotice = notice
        persistOfflineDisconnectionNotice(
            preferences = preferences,
            notice = notice
        )
    }

    LaunchedEffect(
        useFakeSensorData,
        selectedSensorConnectionMode,
        selectedSensorIsConnected,
        selectedRuntimeInfo
    ) {
        if (!useFakeSensorData && selectedSensorIsConnected) {
            UvirSensorRuntimeInfoStore.save(
                context = context,
                connectionMode = selectedSensorConnectionMode,
                info = selectedRuntimeInfo
            )
        }
    }

    LaunchedEffect(
        useFakeSensorData,
        selectedSensorConnectionMode,
        lastWirelessSensorConnectionModeValue,
        usbSensorState.attached,
        sampleSpacingMs
    ) {
        val fallbackWirelessMode =
            SensorConnectionMode.fromStoredValue(
                lastWirelessSensorConnectionModeValue
            ).takeIf {
                it != SensorConnectionMode.USB
            } ?: SensorConnectionMode.WIFI

        val keepWirelessControlAvailable =
            selectedSensorConnectionMode ==
                    SensorConnectionMode.USB &&
                    !usbSensorState.attached

        usbSensorManager.setStreaming(
            enabled =
                !useFakeSensorData &&
                        selectedSensorConnectionMode ==
                        SensorConnectionMode.USB,
            intervalMs = sampleSpacingMs
        )

        wirelessSensorManager.setStreaming(
            mode =
                if (keepWirelessControlAvailable) {
                    fallbackWirelessMode
                } else {
                    selectedSensorConnectionMode
                },
            enabled =
                !useFakeSensorData &&
                        (
                                selectedSensorConnectionMode !=
                                SensorConnectionMode.USB ||
                                keepWirelessControlAvailable
                                ),
            intervalMs = sampleSpacingMs,
            requestSamples =
                selectedSensorConnectionMode !=
                        SensorConnectionMode.USB
        )
    }

    LaunchedEffect(
        useFakeSensorData,
        selectedSensorConnectionMode,
        usbSensorState.status,
        usbSensorState.sampleSequence,
        wirelessSensorState.status,
        wirelessSensorState.sampleSequence
    ) {
        if (useFakeSensorData) {
            return@LaunchedEffect
        }

        val activeSample =
            if (
                selectedSensorConnectionMode ==
                SensorConnectionMode.USB
            ) {
                usbSensorState.sample
            } else {
                wirelessSensorState.sample
            }

        val activeSaturated =
            if (selectedSensorConnectionMode == SensorConnectionMode.USB) {
                usbSensorState.saturated
            } else {
                wirelessSensorState.saturated
            }

        val activeSequence =
            if (
                selectedSensorConnectionMode ==
                SensorConnectionMode.USB
            ) {
                usbSensorState.sampleSequence
            } else {
                wirelessSensorState.sampleSequence
            }

        val activeConnected =
            if (
                selectedSensorConnectionMode ==
                SensorConnectionMode.USB
            ) {
                usbSensorState.status ==
                        UsbSensorConnectionStatus.CONNECTED &&
                    usbSensorState.appConnectionConfirmed
            } else {
                wirelessSensorState.status ==
                        WirelessSensorConnectionStatus.CONNECTED &&
                    wirelessSensorState.mode ==
                        selectedSensorConnectionMode &&
                    wirelessSensorState.appConnectionConfirmed
            }

        if (
            !activeConnected ||
            activeSample == null ||
            activeSequence <= 0L
        ) {
            liveSamples = emptyList()
            val emptyMeasurement = SensorSample()
            measurement = emptyMeasurement
            latestMeasurement.set(emptyMeasurement)
            lastLiveUiRefreshMs.set(0L)
            liveReady = false
            return@LaunchedEffect
        }

        // Real sensor frames are already the final averaged result produced
        // by the ESP32. Only fake debug data is combined inside Android.
        // Current firmware reports AS7343 saturation as one aggregate flag.
        // Keep it separate from the numeric payload; future firmware can add
        // the UV-family bit without changing persistence or presentation.
        val qualitySample =
            activeSample.copy(
                qualityFlags =
                    activeSample.qualityFlags or
                        if (activeSaturated) {
                            UVIR_QUALITY_VISIBLE_NIR_OUT_OF_RANGE
                        } else {
                            0
                        }
            )
        latestMeasurement.set(qualitySample)
        val nextLiveReady = true

        val nowMs =
            System.currentTimeMillis()
        val previousUiRefreshMs =
            lastLiveUiRefreshMs.get()

        if (
            previousUiRefreshMs == 0L ||
            nowMs - previousUiRefreshMs >=
                LIVE_UI_REFRESH_MS ||
            (!liveReady && nextLiveReady)
        ) {
            measurement = qualitySample
            lastLiveUiRefreshMs.set(nowMs)
        }

        liveReady = nextLiveReady
    }

    suspend fun recordThresholdAlert(
        violations: List<ThresholdAlertViolation>,
        timestampMs: Long,
        sessionId: Long = thresholdAlertSessionId
    ) {
        if (violations.isEmpty()) return
        val insertedAlertId =
            database.insertThresholdAlertLog(
                violations = violations,
                timestamp = timestampMs,
                sessionId = sessionId.takeIf { it > 0L },
                sensorDeviceId = selectedSensorDeviceId,
                qualityFlags = latestMeasurement.get().qualityFlags
            )
        if (insertedAlertId != -1L) {
            incrementUnreadAlerts()
            val latestViolation = violations.first()
            latestThresholdNotificationAlert =
                ThresholdNotificationAlert(
                    metric = latestViolation.rule.metric,
                    value = latestViolation.value,
                    timestamp = timestampMs
                )
        }
        playThresholdAlertTone(
            context = context,
            sound = thresholdAlertSettings.sound,
            volume = thresholdAlertSettings.volume
        )
    }

    LaunchedEffect(
        useFakeSensorData,
        thresholdAlertSettings,
        liveReady,
        sensorSyncInProgress
    ) {
        if (
            !useFakeSensorData ||
            !thresholdAlertSettings.hasActiveMonitoring() ||
            !liveReady ||
            sensorSyncInProgress
        ) {
            latestThresholdNotificationAlert = null
            return@LaunchedEffect
        }

        while (true) {
            val violations =
                thresholdAlertViolations(
                    latestMeasurement.get(),
                    thresholdAlertSettings
                )

            if (violations.isEmpty()) {
                latestThresholdNotificationAlert = null
                delay(250L)
            } else {
                recordThresholdAlert(
                    violations = violations,
                    timestampMs = System.currentTimeMillis()
                )
                // During this cooldown no threshold is evaluated. When it
                // expires, monitoring resumes until the next real violation.
                delay(
                    thresholdAlertSettings.repeatSeconds
                        .coerceIn(1, 3600) * 1000L
                )
            }
        }
    }

    var lastHandledUsbAlertSequence by rememberSaveable {
        mutableLongStateOf(0L)
    }
    var lastHandledWirelessAlertSequence by rememberSaveable {
        mutableLongStateOf(0L)
    }
    val selectedLiveAlertEvent =
        if (selectedSensorConnectionMode == SensorConnectionMode.USB) {
            usbSensorState.alertEvent
        } else {
            wirelessSensorState.alertEvent
        }

    LaunchedEffect(
        useFakeSensorData,
        selectedSensorConnectionMode,
        selectedLiveAlertEvent?.receiptSequence
    ) {
        if (useFakeSensorData || !selectedSensorIsConnected) {
            return@LaunchedEffect
        }

        val event = selectedLiveAlertEvent ?: return@LaunchedEffect
        val alreadyHandled =
            if (selectedSensorConnectionMode == SensorConnectionMode.USB) {
                event.receiptSequence <= lastHandledUsbAlertSequence
            } else {
                event.receiptSequence <= lastHandledWirelessAlertSequence
            }
        if (alreadyHandled) return@LaunchedEffect

        if (selectedSensorConnectionMode == SensorConnectionMode.USB) {
            lastHandledUsbAlertSequence = event.receiptSequence
        } else {
            lastHandledWirelessAlertSequence = event.receiptSequence
        }

        val violations = parseThresholdAlertLogDetails(event.details)
        if (violations.isNotEmpty()) {
            recordThresholdAlert(
                violations = violations,
                timestampMs = event.timestampMs,
                sessionId =
                    event.sessionId.takeIf { it > 0L }
                        ?: thresholdAlertSessionId
            )
        }
    }

    val currentThresholdViolations =
        remember(
            measurement,
            thresholdAlertSettings,
            liveReady
        ) {
            if (liveReady) {
                thresholdAlertViolations(
                    measurement,
                    thresholdAlertSettings
                )
            } else {
                emptyList()
            }
        }

    // -------------------------------------------------
    // AUTOMATIC ACQUISITION
    // -------------------------------------------------

    var autoEnabled by remember {
        mutableStateOf(
            preferences.getBoolean(
                KEY_AUTO_ENABLED,
                false
            )
        )
    }

    var automaticScheduleKnown by remember {
        mutableStateOf(preferences.getBoolean(KEY_AUTO_SCHEDULE_KNOWN, true))
    }

    var autoSessionSimulated by remember {
        mutableStateOf(preferences.getBoolean(KEY_AUTO_SIMULATED, false))
    }

    var automaticStopInProgress by remember {
        mutableStateOf(false)
    }

    var autoIntervalSeconds by remember {
        mutableLongStateOf(
            preferences.getLong(
                KEY_AUTO_INTERVAL_SECONDS,
                60L
            )
        )
    }

    var autoNote by remember {
        mutableStateOf(
            limitUvirNote(
                preferences.getString(
                    KEY_AUTO_NOTE,
                    ""
                ) ?: ""
            )
        )
    }

    var autoExternalCommand by remember {
        mutableStateOf(
            preferences.getBoolean(KEY_AUTO_EXTERNAL_COMMAND, false)
        )
    }

    var autoConditionalRules by remember { mutableStateOf(loadAcquisitionConditions(preferences)) }
    var autoConditionalEnabled by remember { mutableStateOf(preferences.getBoolean(KEY_AUTO_CONDITIONAL_ENABLED, false)) }
    var autoConditionalMatch by remember { mutableStateOf(runCatching {
        AcquisitionConditionMatch.valueOf(preferences.getString(KEY_AUTO_CONDITIONAL_MATCH, "ANY") ?: "ANY")
    }.getOrDefault(AcquisitionConditionMatch.ANY)) }
    var autoConditionalAction by remember { mutableStateOf(runCatching {
        AcquisitionConditionAction.valueOf(preferences.getString(KEY_AUTO_CONDITIONAL_ACTION, "ACQUIRE") ?: "ACQUIRE")
    }.getOrDefault(AcquisitionConditionAction.ACQUIRE)) }
    var autoConditionalPlan by remember { mutableStateOf(decodeConditionalAcquisitionPlan(
        preferences.getString(KEY_AUTO_CONDITIONAL_PLAN, "") ?: ""
    )) }
    var autoConditionalWaiting by remember { mutableStateOf(preferences.getBoolean(KEY_AUTO_CONDITIONAL_WAITING, false)) }

    var autoUseStartDelay by remember {
        mutableStateOf(
            preferences.getBoolean(
                KEY_AUTO_USE_START_DELAY,
                false
            )
        )
    }

    var autoStartDelaySeconds by remember {
        mutableLongStateOf(
            preferences.getLong(
                KEY_AUTO_START_DELAY_SECONDS,
                0L
            )
        )
    }

    var autoUseDuration by remember {
        mutableStateOf(
            preferences.getBoolean(
                KEY_AUTO_USE_DURATION,
                preferences.getBoolean(
                    LEGACY_KEY_AUTO_USE_END,
                    false
                )
            )
        )
    }

    var autoDurationSeconds by remember {
        mutableLongStateOf(
            preferences.getLong(
                KEY_AUTO_DURATION_SECONDS,
                3600L
            )
        )
    }

    var autoLimitEnabled by remember {
        mutableStateOf(
            preferences.getBoolean(
                KEY_AUTO_LIMIT_ENABLED,
                false
            )
        )
    }

    var autoMaxCount by remember {
        mutableIntStateOf(
            preferences.getInt(
                KEY_AUTO_MAX_COUNT,
                10
            ).coerceAtLeast(1)
        )
    }

    var autoCompletedCount by remember {
        mutableIntStateOf(
            preferences.getInt(
                KEY_AUTO_COMPLETED_COUNT,
                0
            ).coerceAtLeast(0)
        )
    }

    var autoSessionId by remember {
        mutableLongStateOf(
            preferences.getLong(
                KEY_AUTO_SESSION_ID,
                0L
            ).coerceAtLeast(0L)
        )
    }

    var autoNextSaveMs by remember {
        mutableLongStateOf(
            preferences.getLong(
                KEY_AUTO_NEXT_SAVE_MS,
                System.currentTimeMillis()
            )
        )
    }

    var automaticJobReadbackGuard by remember(sensorAssociationRevision) {
        mutableStateOf(UvirAutomaticJobReadbackGuard())
    }

    LaunchedEffect(selectedSensorIsConnected, sensorAssociationRevision) {
        if (!selectedSensorIsConnected) {
            automaticJobReadbackGuard = automaticJobReadbackGuard.afterDisconnection()
        }
    }

    var autoEndMs by remember {
        mutableLongStateOf(
            preferences.getLong(
                KEY_AUTO_END_MS,
                0L
            )
        )
    }

    LaunchedEffect(Unit) {
        if (autoEnabled && autoSessionId > 0L) {
            withContext(Dispatchers.IO) {
                // Remembered UI state alone must not reopen a session already closed by sensor readback.
                database.ensureAcquisitionSession(
                    sessionId = autoSessionId,
                    note = autoNote,
                    startedAt = System.currentTimeMillis(),
                    sensorDeviceId = selectedSensorDeviceId,
                    externalCommand = autoExternalCommand
                )
            }
        }
    }

    LaunchedEffect(autoEnabled, autoSessionId) {
        if (!autoEnabled && autoSessionId > 0L) {
            withContext(Dispatchers.IO) {
                database.finishAcquisitionSession(autoSessionId)
            }
        }
    }

    LaunchedEffect(useFakeSensorData, autoEnabled, autoSessionId, autoSessionSimulated) {
        if (!autoSessionSimulated || !autoEnabled || autoSessionId <= 0L) {
            return@LaunchedEffect
        }
        val simulationSessionId = autoSessionId
        val firstAllowedAtMs = preferences.getLong(KEY_AUTO_FIRST_ALLOWED_MS,
            if (autoCompletedCount == 0) autoNextSaveMs else 0L)

        fun finishSimulation(messageId: Int) {
            autoEnabled = false
            autoNextSaveMs = 0L
            preferences.edit()
                .putBoolean(KEY_AUTO_ENABLED, false)
                .putLong(KEY_AUTO_NEXT_SAVE_MS, 0L)
                .apply()
            showUvirBottomMessage(context, resources.getString(messageId), longDuration = false)
        }

        if (!useFakeSensorData) {
            finishSimulation(R.string.automatic_stopped)
            return@LaunchedEffect
        }
        autoCompletedCount = withContext(Dispatchers.IO) {
            database.acquisitionCountForSession(simulationSessionId)
        }
        preferences.edit().putInt(KEY_AUTO_COMPLETED_COUNT, autoCompletedCount).apply()

        while (useFakeSensorData && autoEnabled && autoSessionId == simulationSessionId) {
            when (val step = simulatedConditionalAutomaticAcquisitionStep(
                firstAllowedAtMs = firstAllowedAtMs,
                plan = autoConditionalPlan,
                started = !autoConditionalWaiting,
                condition = { autoConditionalPlan?.let { evaluateAcquisitionCondition(it, latestMeasurement.get()) } },
                nowMs = System.currentTimeMillis(),
                nextAtMs = autoNextSaveMs,
                intervalSeconds = autoIntervalSeconds,
                endAtMs = autoEndMs.takeIf { autoUseDuration && it > 0L },
                maximumCount = autoMaxCount.takeIf { autoLimitEnabled },
                completedCount = autoCompletedCount,
                sampleReady = liveReady
            )) {
                SimulatedAutomaticAcquisitionStep.Stop -> {
                    finishSimulation(R.string.automatic_stopped)
                    break
                }
                is SimulatedAutomaticAcquisitionStep.Wait -> delay(step.delayMs)
                is SimulatedAutomaticAcquisitionStep.Acquire -> {
                    val sample = latestMeasurement.get()
                    if (step.startsSession) {
                        autoConditionalWaiting = false
                        val triggeredAt = System.currentTimeMillis()
                        withContext(Dispatchers.IO) { database.confirmConditionalSessionStart(
                            simulationSessionId, selectedSensorDeviceId, triggeredAt
                        ) }
                        autoEndMs = if (autoUseDuration) triggeredAt + autoDurationSeconds * 1000L else 0L
                        preferences.edit().putBoolean(KEY_AUTO_CONDITIONAL_WAITING, false)
                            .putLong(KEY_AUTO_END_MS, autoEndMs).apply()
                    }
                    val note = autoNote
                    val minimumSequence = autoCompletedCount + 1
                    val result = withContext(Dispatchers.IO) {
                        runCatching {
                            database.saveAcquisition(
                                sample = sample,
                                note = note,
                                automatic = true,
                                sessionId = simulationSessionId,
                                sessionSequence = maxOf(
                                    minimumSequence,
                                    database.nextSequenceForSession(simulationSessionId)
                                ),
                                sensorDeviceId = selectedSensorDeviceId
                            )
                        }
                    }
                    if (result.getOrDefault(-1L) == -1L) {
                        UvirErrorLog.record(
                            context.applicationContext,
                            source = "simulated_automatic_acquisition",
                            message = result.exceptionOrNull()?.message ?: "Simulated acquisition save failed"
                        )
                        finishSimulation(R.string.save_error)
                        break
                    }
                    autoCompletedCount += 1
                    autoNextSaveMs = if (autoConditionalPlan?.action == AcquisitionConditionAction.ACQUIRE) {
                        (simulatedAutomaticAcquisitionStep(System.currentTimeMillis(), 0L,
                            autoIntervalSeconds, null, null, 0, true)
                            as SimulatedAutomaticAcquisitionStep.Acquire).nextAtMs
                    } else step.nextAtMs
                    preferences.edit()
                        .putInt(KEY_AUTO_COMPLETED_COUNT, autoCompletedCount)
                        .putLong(KEY_AUTO_NEXT_SAVE_MS, autoNextSaveMs)
                        .apply()
                    incrementUnreadAcquisitions()
                    playAcquisitionFeedback()
                }
            }
        }
    }

    val currentThresholdAlertRepeatSeconds by
        rememberUpdatedState(
            thresholdAlertSettings.repeatSeconds
        )

    // HELLO reports the sensor-side total, including records that Android may
    // still have to receive. The database is the only authoritative source for
    // visible counters, so every counter advances only after local persistence.
    LaunchedEffect(
        selectedSensorIsConnected,
        selectedRuntimeInfo.automaticJobReadback,
        selectedRuntimeInfo.offlineSessionId,
        selectedRuntimeInfo.offlineCompleted,
        selectedRuntimeInfo.offlineAcquisitions,
        selectedRuntimeInfo.offlineNextAtMs,
        selectedRuntimeInfo.offlineRecording,
        selectedRuntimeInfo.offlineConditionPlan,
        selectedRuntimeInfo.offlineConditionWaiting,
        selectedRuntimeInfo.offlineEndAtMs,
        sensorSyncInProgress,
        autoSessionId,
        autoEnabled,
        sensorAssociationRevision
    ) {
        if (!selectedSensorIsConnected) return@LaunchedEffect

        val rawReportedSessionId = selectedRuntimeInfo.offlineSessionId ?: 0L
        val shouldResolveReportedSession =
            isSensorOriginatedSessionId(rawReportedSessionId) &&
                (selectedRuntimeInfo.offlineRecording == true || autoEnabled)
        val reportedSessionId =
            if (shouldResolveReportedSession) {
                withContext(Dispatchers.IO) {
                    database.resolveSensorOriginatedAcquisitionSession(
                        sensorDeviceId = selectedSensorDeviceId,
                        sensorSessionId = rawReportedSessionId,
                        startedAt = selectedRuntimeInfo.offlineStartedAtMs
                            ?.takeIf { it > 0L }
                            ?: System.currentTimeMillis()
                    )
                }
            } else {
                rawReportedSessionId
            }
        val readback = selectedRuntimeInfo.automaticJobReadback?.let {
            if (isSensorOriginatedSessionId(it.sessionId) &&
                shouldResolveReportedSession
            ) {
                it.copy(sessionId = reportedSessionId)
            } else {
                it
            }
        }
        if (readback?.active == true && readback.sessionId == autoSessionId &&
            readback.revision > automaticJobReadbackGuard.minimumRevision
        ) {
            automaticJobReadbackGuard = automaticJobReadbackGuard.afterActiveConfirmation()
        }
        val confirmedStoppedKnownSession =
            autoEnabled && autoSessionId > 0L && readback != null &&
                !readback.active &&
                readback.sessionId == autoSessionId &&
                readback.revision > automaticJobReadbackGuard.minimumRevision &&
                automaticJobReadbackGuard.mayResolveMissingJob
        if (!automaticStopInProgress && (
                shouldResolveMissingAutomaticJob(
                readback = readback,
                guard = automaticJobReadbackGuard,
                associatedDeviceId = UvirSensorCredentialStore.load(context).deviceId,
                connectionConfirmed = selectedSensorIsConnected,
                simulated = useFakeSensorData || autoSessionSimulated,
                appJobActive = autoEnabled,
                appSessionId = autoSessionId
                ) || confirmedStoppedKnownSession
            )
        ) {
            // No STOP command: the selected sensor has already confirmed that its RAM job is gone.
            val interruptedSessionId = autoSessionId
            val recoveredCount = withContext(Dispatchers.IO) {
                if (database.finishInterruptedAcquisitionSession(interruptedSessionId, selectedSensorDeviceId)) {
                    database.acquisitionCountForSession(interruptedSessionId)
                } else null // The remembered session must also belong to this sensor in the database.
            } ?: return@LaunchedEffect
            autoCompletedCount = recoveredCount
            autoEnabled = false
            autoNextSaveMs = 0L
            preferences.edit()
                .putBoolean(KEY_AUTO_ENABLED, false)
                .putLong(KEY_AUTO_NEXT_SAVE_MS, 0L)
                .putInt(KEY_AUTO_COMPLETED_COUNT, autoCompletedCount)
                .apply()
            return@LaunchedEffect
        }

        val sensorActiveSessionId =
            selectedRuntimeInfo.activeAutomaticSessionIdOrNull()?.let {
                if (isSensorOriginatedSessionId(it)) reportedSessionId else it
            }
        if (
            sensorActiveSessionId != null &&
            !automaticStopInProgress &&
            !autoEnabled && autoSessionId == 0L &&
            firmwareSupportsSensorSettingsSnapshot(
                selectedRuntimeInfo.firmwareVersion
            )
        ) {
            // A reassociation must read the sensor's actual job, not stop it
            // because Android intentionally forgot its former active context.
            val restoredNote = withContext(Dispatchers.IO) {
                val note = database.readAcquisitionSessionNote(sensorActiveSessionId)
                database.startAcquisitionSession(
                    sessionId = sensorActiveSessionId,
                    note = note,
                    sensorDeviceId = selectedSensorDeviceId,
                    externalCommand = selectedRuntimeInfo.offlineExternalCommand == true
                )
                note
            }
            autoSessionId = sensorActiveSessionId
            autoSessionSimulated = false
            autoEnabled = true
            autoNote = restoredNote
            autoExternalCommand = selectedRuntimeInfo.offlineExternalCommand == true
            automaticScheduleKnown = false
            autoEndMs = 0L
            preferences.edit()
                .putLong(KEY_AUTO_SESSION_ID, autoSessionId)
                .putBoolean(KEY_AUTO_SIMULATED, false)
                .putBoolean(KEY_AUTO_ENABLED, true)
                .putString(KEY_AUTO_NOTE, restoredNote)
                .putBoolean(KEY_AUTO_EXTERNAL_COMMAND, autoExternalCommand)
                .putBoolean(KEY_AUTO_SCHEDULE_KNOWN, false)
                .putLong(KEY_AUTO_END_MS, 0L)
                .commit()
        }

        if (
            !autoEnabled ||
            autoSessionId <= 0L ||
            reportedSessionId != autoSessionId
        ) {
            return@LaunchedEffect
        }

        if (firmwareSupportsConditionalAcquisition(selectedRuntimeInfo.firmwareVersion)) {
            autoExternalCommand =
                selectedRuntimeInfo.offlineExternalCommand == true
            autoConditionalPlan = selectedRuntimeInfo.offlineConditionPlan
            autoConditionalWaiting = selectedRuntimeInfo.offlineConditionWaiting == true
            selectedRuntimeInfo.offlineEndAtMs?.let { autoEndMs = it }
            if (autoConditionalPlan?.action == AcquisitionConditionAction.START) {
                selectedRuntimeInfo.offlineStartedAtMs?.let { start ->
                    withContext(Dispatchers.IO) { database.confirmConditionalSessionStart(
                        autoSessionId, selectedSensorDeviceId, start
                    ) }
                }
            }
            preferences.edit()
                .putBoolean(KEY_AUTO_EXTERNAL_COMMAND, autoExternalCommand)
                .putString(KEY_AUTO_CONDITIONAL_PLAN, autoConditionalPlan?.encode() ?: "")
                .putBoolean(KEY_AUTO_CONDITIONAL_WAITING, autoConditionalWaiting)
                .putLong(KEY_AUTO_END_MS, autoEndMs).apply()
        }
        val reportedNextAtMs =
            selectedRuntimeInfo.offlineNextAtMs?.coerceAtLeast(0L)
        autoCompletedCount = withContext(Dispatchers.IO) {
            database.acquisitionCountForSession(autoSessionId)
        }
        if (reportedNextAtMs != null && reportedNextAtMs > 0L) {
            autoNextSaveMs = reportedNextAtMs
        }
        if (
            selectedRuntimeInfo.offlineRecording == false &&
            !automaticStopInProgress
        ) {
            autoEnabled = false
        }

        preferences.edit()
            .putInt(KEY_AUTO_COMPLETED_COUNT, autoCompletedCount)
            .putLong(KEY_AUTO_NEXT_SAVE_MS, autoNextSaveMs)
            .putBoolean(KEY_AUTO_ENABLED, autoEnabled)
            .apply()
    }

    LaunchedEffect(
        database,
        usbSensorManager,
        wirelessSensorManager,
        sensorAssociationRevision
    ) {
        SensorRuntimeEventBus.events.collect { event ->
            if (
                !sensorSyncSourceMatchesSelection(
                    source = event.source,
                    selectedMode = currentSyncConnectionMode,
                    selectedSensorConnected = currentSyncSensorIsConnected
                )
            ) {
                return@collect
            }

            when (event) {
                is SensorRuntimeEvent.Acquisition -> {
                    val deviceId =
                        when (event.source) {
                            SensorSyncSource.USB ->
                                usbSensorManager.state.value.deviceId
                            SensorSyncSource.WIRELESS ->
                                wirelessSensorManager.state.value.deviceId
                        }.orEmpty()
                    if (deviceId.isBlank()) return@collect

                    val resolvedSessionId =
                        if (isSensorOriginatedSessionId(event.sessionId)) {
                            withContext(Dispatchers.IO) {
                                database.resolveSensorOriginatedAcquisitionSession(
                                    sensorDeviceId = deviceId,
                                    sensorSessionId = event.sessionId,
                                    startedAt = event.timestamp
                                )
                            }
                        } else {
                            event.sessionId
                        }

                    val wasAlreadyStored = withContext(Dispatchers.IO) {
                        database.hasSensorAcquisition(
                            sensorDeviceId = deviceId,
                            sensorRecordId = event.recordId
                        )
                    }
                    // A live record is deliberately retransmitted until the
                    // sensor receives its ACK. If Android stored the first
                    // copy but the acknowledgement was lost during a transient
                    // transport handover, the repeated copy is already in the
                    // database and still has to be acknowledged. Treating the
                    // duplicate insert as a failure left the ESP32 permanently
                    // waiting on record 1 and blocked every later acquisition.
                    val saved =
                        if (wasAlreadyStored) {
                            true
                        } else {
                            withContext(Dispatchers.IO) {
                                database.saveRecoveredAcquisition(
                                    timestamp = event.timestamp,
                                    sample = event.sample,
                                    note = event.note,
                                    sessionId = resolvedSessionId,
                                    sequence = event.sequence,
                                    sensorDeviceId = deviceId,
                                    sensorRecordId = event.recordId,
                                    externalCommand =
                                        isSensorOriginatedSessionId(event.sessionId)
                                )
                            }
                        }
                    if (!saved) return@collect

                    val acknowledgementSent = withContext(Dispatchers.IO) {
                        val command =
                            listOf("ACQUISITION_ACK ${event.recordId}")
                        when (event.source) {
                            SensorSyncSource.USB ->
                                usbSensorManager.sendSensorControlCommands(command)
                            SensorSyncSource.WIRELESS ->
                                wirelessSensorManager.sendSensorControlCommands(command)
                        }
                    }
                    if (!acknowledgementSent) {
                        Log.w(
                            "UvirAutomatic",
                            "Acquisition ACK deferred; record=${event.recordId} " +
                                "source=${event.source}"
                        )
                    }
                    if (!wasAlreadyStored) {
                        incrementUnreadAcquisitions()
                        playAcquisitionFeedback()
                    }

                    if (resolvedSessionId > 0L && resolvedSessionId == autoSessionId) {
                        if (event.jobActive) {
                            automaticJobReadbackGuard = automaticJobReadbackGuard.afterActiveConfirmation()
                        }
                        autoCompletedCount = withContext(Dispatchers.IO) {
                            database.acquisitionCountForSession(autoSessionId)
                        }
                        autoNextSaveMs = event.nextAtMs
                        if (automaticStopInProgress) {
                            if (!event.jobActive) {
                                automaticStopInProgress = false
                                autoEnabled = false
                                showUvirBottomMessage(
                                    context,
                                    resources.getString(R.string.automatic_stopped),
                                    longDuration = false
                                )
                            }
                        } else {
                            autoEnabled = event.jobActive
                        }
                        preferences.edit()
                            .putInt(
                                KEY_AUTO_COMPLETED_COUNT,
                                autoCompletedCount
                            )
                            .putLong(
                                KEY_AUTO_NEXT_SAVE_MS,
                                autoNextSaveMs
                            )
                            .putBoolean(
                                KEY_AUTO_ENABLED,
                                autoEnabled
                            )
                            .apply()
                    }
                }

                is SensorRuntimeEvent.AutomaticStatus -> {
                    val deviceId =
                        when (event.source) {
                            SensorSyncSource.USB ->
                                usbSensorManager.state.value.deviceId
                            SensorSyncSource.WIRELESS ->
                                wirelessSensorManager.state.value.deviceId
                        }.orEmpty()
                    if (deviceId.isBlank()) return@collect
                    val sensorOriginated =
                        isSensorOriginatedSessionId(event.sessionId)
                    val resolvedSessionId =
                        if (sensorOriginated) {
                            withContext(Dispatchers.IO) {
                                database.resolveSensorOriginatedAcquisitionSession(
                                    sensorDeviceId = deviceId,
                                    sensorSessionId = event.sessionId,
                                    startedAt = event.startedAtMs
                                        ?.takeIf { it > 0L }
                                        ?: System.currentTimeMillis()
                                )
                            }
                        } else {
                            event.sessionId
                        }

                    if (sensorOriginated && event.jobActive &&
                        resolvedSessionId > 0L &&
                        resolvedSessionId != autoSessionId &&
                        !automaticStopInProgress
                    ) {
                        withContext(Dispatchers.IO) {
                            database.startAcquisitionSession(
                                sessionId = resolvedSessionId,
                                note = "",
                                startedAt = event.startedAtMs
                                    ?.takeIf { it > 0L }
                                    ?: System.currentTimeMillis(),
                                sensorDeviceId = deviceId,
                                externalCommand = true
                            )
                        }
                        autoSessionId = resolvedSessionId
                        autoSessionSimulated = false
                        autoEnabled = true
                        autoNote = ""
                        autoExternalCommand = true
                        automaticScheduleKnown = false
                        autoEndMs = 0L
                        automaticJobReadbackGuard =
                            automaticJobReadbackGuard.afterActiveConfirmation()
                        preferences.edit()
                            .putLong(KEY_AUTO_SESSION_ID, resolvedSessionId)
                            .putBoolean(KEY_AUTO_SIMULATED, false)
                            .putBoolean(KEY_AUTO_ENABLED, true)
                            .putString(KEY_AUTO_NOTE, "")
                            .putBoolean(KEY_AUTO_EXTERNAL_COMMAND, true)
                            .putBoolean(KEY_AUTO_SCHEDULE_KNOWN, false)
                            .putLong(KEY_AUTO_END_MS, 0L)
                            .commit()
                    }
                    if (resolvedSessionId != autoSessionId) return@collect
                    if (event.jobActive) {
                        automaticJobReadbackGuard = automaticJobReadbackGuard.afterActiveConfirmation()
                    }
                    autoCompletedCount = withContext(Dispatchers.IO) {
                        database.acquisitionCountForSession(autoSessionId)
                    }
                    autoNextSaveMs = event.nextAtMs
                    event.externalCommand?.let { autoExternalCommand = it }
                    event.conditionPlan?.let { autoConditionalPlan = decodeConditionalAcquisitionPlan(it) }
                    event.conditionWaiting?.let { autoConditionalWaiting = it }
                    event.endAtMs?.let { autoEndMs = it }
                    if (autoConditionalPlan?.action == AcquisitionConditionAction.START) {
                        event.startedAtMs?.let { start ->
                            withContext(Dispatchers.IO) { database.confirmConditionalSessionStart(
                                autoSessionId, selectedSensorDeviceId, start
                            ) }
                        }
                    }
                    preferences.edit()
                        .putBoolean(KEY_AUTO_EXTERNAL_COMMAND, autoExternalCommand)
                        .putString(KEY_AUTO_CONDITIONAL_PLAN, autoConditionalPlan?.encode() ?: "")
                        .putBoolean(KEY_AUTO_CONDITIONAL_WAITING, autoConditionalWaiting)
                        .putLong(KEY_AUTO_END_MS, autoEndMs).apply()
                    if (automaticStopInProgress) {
                        if (!event.jobActive) {
                            automaticStopInProgress = false
                            autoEnabled = false
                            showUvirBottomMessage(
                                context,
                                resources.getString(R.string.automatic_stopped),
                                longDuration = false
                            )
                        }
                    } else {
                        autoEnabled = event.jobActive
                    }
                    preferences.edit()
                        .putInt(
                            KEY_AUTO_COMPLETED_COUNT,
                            autoCompletedCount
                        )
                        .putLong(
                            KEY_AUTO_NEXT_SAVE_MS,
                            autoNextSaveMs
                        )
                        .putBoolean(
                            KEY_AUTO_ENABLED,
                            autoEnabled
                        )
                        .apply()
                }
            }
        }
    }

    LaunchedEffect(
        database,
        usbSensorManager,
        wirelessSensorManager,
        sensorAssociationRevision
    ) {
        var activeSyncSource: SensorSyncSource? = null
        var expectedAcquisitions = 0
        var expectedAlerts = 0
        var expectedErrors = 0
        var processedAcquisitions = 0
        var processedAlerts = 0
        var savedAlerts = 0
        var processedErrors = 0
        var lastSavedAlertTimestamp = 0L
        var storageWasFull = false
        var summaryShown = false

        fun showRecoveredSummaryIfReady(force: Boolean = false) {
            val expectedTotal =
                expectedAcquisitions + expectedAlerts + expectedErrors
            val processedTotal =
                processedAcquisitions + processedAlerts + processedErrors
            val recoveredTotal = processedTotal
            if (
                !summaryShown &&
                (force || (expectedTotal > 0 && processedTotal >= expectedTotal)) &&
                (recoveredTotal > 0 || storageWasFull)
            ) {
                sensorSyncSummary = SensorSyncSummary(
                    acquisitions = processedAcquisitions,
                    alerts = savedAlerts,
                    errors = processedErrors,
                    storageWasFull = storageWasFull
                )
                sensorSyncInProgress = false
                acquisitionSyncInProgress = false
                alertSyncInProgress = false
                summaryShown = true
            }
        }

        SensorSyncEventBus.events.collect { event ->
            if (
                !sensorSyncSourceMatchesSelection(
                    source = event.source,
                    selectedMode = currentSyncConnectionMode,
                    selectedSensorConnected = currentSyncSensorIsConnected
                )
            ) {
                Log.i(
                    "UvirSync",
                    "Ignored ${event.source} sync event from an unselected source"
                )
                return@collect
            }

            if (event is SensorSyncEvent.Started) {
                activeSyncSource = event.source
                expectedAcquisitions = event.acquisitions
                expectedAlerts = event.alerts
                expectedErrors = event.errors
                processedAcquisitions = 0
                processedAlerts = 0
                savedAlerts = 0
                processedErrors = 0
                lastSavedAlertTimestamp = 0L
                storageWasFull = event.storageWasFull
                summaryShown = false
                sensorSyncInProgress = true
                acquisitionSyncInProgress = event.acquisitions > 0
                alertSyncInProgress = event.alerts > 0
                sensorSyncReady = false
                if (autoSessionId > 0L) {
                    autoCompletedCount = withContext(Dispatchers.IO) {
                        database.acquisitionCountForSession(autoSessionId)
                    }
                    preferences.edit()
                        .putInt(
                            KEY_AUTO_COMPLETED_COUNT,
                            autoCompletedCount
                        )
                        .apply()
                }
                if (event.acquisitions + event.alerts + event.errors > 0) {
                    showUvirBottomMessage(
                        context,
                        resources.getString(R.string.sensor_sync_in_progress),
                        longDuration = false
                    )
                }
                return@collect
            }
            if (event.source != activeSyncSource) {
                return@collect
            }

            val deviceId =
                when (event.source) {
                    SensorSyncSource.USB ->
                        usbSensorManager.state.value.deviceId
                    SensorSyncSource.WIRELESS ->
                        wirelessSensorManager.state.value.deviceId
                }.orEmpty()

            suspend fun acknowledge(recordId: Long) {
                withContext(Dispatchers.IO) {
                    when (event.source) {
                        SensorSyncSource.USB ->
                            usbSensorManager.acknowledgeOfflineRecord(recordId)
                        SensorSyncSource.WIRELESS ->
                            wirelessSensorManager.acknowledgeOfflineRecord(recordId)
                    }
                }
            }

            when (event) {
                is SensorSyncEvent.Started -> Unit

                is SensorSyncEvent.Acquisition -> {
                    val resolvedSessionId =
                        if (isSensorOriginatedSessionId(event.sessionId)) {
                            withContext(Dispatchers.IO) {
                                database.resolveSensorOriginatedAcquisitionSession(
                                    sensorDeviceId = deviceId,
                                    sensorSessionId = event.sessionId,
                                    startedAt = event.timestamp
                                )
                            }
                        } else {
                            event.sessionId
                        }
                    val result = withContext(Dispatchers.IO) {
                        runCatching {
                            database.saveRecoveredAcquisition(
                                timestamp = event.timestamp,
                                sample = event.sample,
                                note = event.note,
                                sessionId = resolvedSessionId,
                                sequence = event.sequence,
                                sensorDeviceId = deviceId,
                                sensorRecordId = event.recordId,
                                externalCommand =
                                    isSensorOriginatedSessionId(event.sessionId)
                            )
                        }
                    }
                    if (result.getOrDefault(false)) {
                        ++processedAcquisitions
                        incrementUnreadAcquisitions()
                        if (resolvedSessionId == autoSessionId && autoConditionalPlan?.action == AcquisitionConditionAction.START) {
                            withContext(Dispatchers.IO) { database.confirmConditionalSessionStart(
                                autoSessionId, deviceId, event.timestamp
                            ) }
                        }
                        if (resolvedSessionId == autoSessionId) {
                            autoCompletedCount =
                                withContext(Dispatchers.IO) {
                                    database.acquisitionCountForSession(
                                        autoSessionId
                                    )
                                }
                            preferences.edit()
                                .putInt(
                                    KEY_AUTO_COMPLETED_COUNT,
                                    autoCompletedCount
                                )
                                .apply()
                        }
                        acknowledge(event.recordId)
                        acquisitionSyncInProgress =
                            processedAcquisitions < expectedAcquisitions
                        showRecoveredSummaryIfReady()
                    } else {
                        sensorSyncIncompleteSummary =
                            SensorSyncIncompleteSummary(
                                acquisitions =
                                    (expectedAcquisitions - processedAcquisitions)
                                        .coerceAtLeast(1),
                                alerts =
                                    (expectedAlerts - processedAlerts)
                                        .coerceAtLeast(0),
                                errors =
                                    (expectedErrors - processedErrors)
                                        .coerceAtLeast(0)
                            )
                        Log.e(
                            "UvirSync",
                            "Acquisition save failed id=${event.recordId}",
                            result.exceptionOrNull()
                        )
                    }
                }

                is SensorSyncEvent.Alert -> {
                    val minimumIntervalMs =
                        currentThresholdAlertRepeatSeconds
                            .coerceIn(1, 3600) * 1000L
                    val elapsedSinceLastSaved =
                        event.timestamp - lastSavedAlertTimestamp
                    if (
                        lastSavedAlertTimestamp > 0L &&
                        elapsedSinceLastSaved in 0 until minimumIntervalMs
                    ) {
                        ++processedAlerts
                        Log.w(
                            "UvirAlert",
                            "Discarded premature offline alert; " +
                                "id=${event.recordId} " +
                                "elapsed=${elapsedSinceLastSaved}ms " +
                                "minimum=${minimumIntervalMs}ms"
                        )
                        acknowledge(event.recordId)
                        alertSyncInProgress =
                            processedAlerts < expectedAlerts
                        showRecoveredSummaryIfReady()
                        return@collect
                    }
                    Log.i(
                        "UvirAlert",
                        "Recovered offline alert; id=${event.recordId} " +
                            "timestamp=${event.timestamp}"
                    )
                    val result = withContext(Dispatchers.IO) {
                        runCatching {
                            database.insertRecoveredThresholdAlert(
                                timestamp = event.timestamp,
                                details = event.details,
                                sensorDeviceId = deviceId,
                                sensorRecordId = event.recordId,
                                sessionId =
                                    event.sessionId.takeIf { it > 0L }
                                        ?: thresholdAlertSessionId,
                                qualityFlags = event.qualityFlags
                            )
                        }
                    }
                    if (result.getOrDefault(false)) {
                        ++processedAlerts
                        ++savedAlerts
                        incrementUnreadAlerts()
                        lastSavedAlertTimestamp = event.timestamp
                        acknowledge(event.recordId)
                        alertSyncInProgress =
                            processedAlerts < expectedAlerts
                        showRecoveredSummaryIfReady()
                    } else {
                        sensorSyncIncompleteSummary =
                            SensorSyncIncompleteSummary(
                                acquisitions =
                                    (expectedAcquisitions - processedAcquisitions)
                                        .coerceAtLeast(0),
                                alerts =
                                    (expectedAlerts - processedAlerts)
                                        .coerceAtLeast(1),
                                errors =
                                    (expectedErrors - processedErrors)
                                        .coerceAtLeast(0)
                            )
                        Log.e(
                            "UvirSync",
                            "Alert save failed id=${event.recordId}",
                            result.exceptionOrNull()
                        )
                    }
                }

                is SensorSyncEvent.Error -> {
                    UvirErrorLog.record(
                        context = context.applicationContext,
                        source = "sensor:${event.code}",
                        message =
                            "Sensor timestamp: ${event.timestamp}\n" +
                                event.message
                    )
                    ++processedErrors
                    acknowledge(event.recordId)
                    showRecoveredSummaryIfReady()
                }

                is SensorSyncEvent.Complete -> {
                    sensorSyncInProgress = false
                    acquisitionSyncInProgress = false
                    alertSyncInProgress = false
                    val incompleteSummary =
                        SensorSyncIncompleteSummary(
                            acquisitions =
                                (expectedAcquisitions - processedAcquisitions)
                                    .coerceAtLeast(0),
                            alerts =
                                (expectedAlerts - processedAlerts)
                                    .coerceAtLeast(0),
                            errors =
                                (expectedErrors - processedErrors)
                                    .coerceAtLeast(0)
                        )
                    sensorSyncIncompleteSummary =
                        incompleteSummary.takeIf { it.total > 0 }
                    if (incompleteSummary.total == 0) {
                        withContext(Dispatchers.IO) {
                            when (event.source) {
                                SensorSyncSource.USB ->
                                    usbSensorManager.markOfflineSyncComplete()
                                SensorSyncSource.WIRELESS ->
                                    wirelessSensorManager.markOfflineSyncComplete()
                            }
                        }
                    }
                    expectedAcquisitions =
                        maxOf(expectedAcquisitions, event.acquisitions)
                    expectedAlerts = maxOf(expectedAlerts, event.alerts)
                    expectedErrors = maxOf(expectedErrors, event.errors)
                    storageWasFull =
                        storageWasFull || event.storageWasFull
                    if (incompleteSummary.total > 0) {
                        sensorSyncSummary = null
                        summaryShown = true
                    } else {
                        if (!summaryShown) {
                            showRecoveredSummaryIfReady(force = true)
                        } else if (storageWasFull) {
                            sensorSyncSummary = SensorSyncSummary(
                                acquisitions = processedAcquisitions,
                                alerts = processedAlerts,
                                errors = processedErrors,
                                storageWasFull = true
                            )
                        }
                    }
                    if (autoSessionId > 0L) {
                        val databaseCount = withContext(Dispatchers.IO) {
                            database.acquisitionCountForSession(
                                autoSessionId
                            )
                        }
                        val completedSessionId =
                            if (isSensorOriginatedSessionId(event.offlineSessionId)) {
                                withContext(Dispatchers.IO) {
                                    database.resolveSensorOriginatedAcquisitionSession(
                                        sensorDeviceId = deviceId,
                                        sensorSessionId = event.offlineSessionId,
                                        startedAt = System.currentTimeMillis()
                                    )
                                }
                            } else {
                                event.offlineSessionId
                            }
                        val sameSensorJob =
                            completedSessionId == autoSessionId
                        autoCompletedCount = databaseCount
                        if (sameSensorJob && event.offlineNextAtMs > 0L) {
                            autoNextSaveMs = event.offlineNextAtMs
                        }
                        if (sameSensorJob && autoEnabled && !event.offlineJobActive) {
                            autoEnabled = false
                        }
                        preferences.edit()
                            .putInt(
                                KEY_AUTO_COMPLETED_COUNT,
                                autoCompletedCount
                            )
                            .putLong(
                                KEY_AUTO_NEXT_SAVE_MS,
                                autoNextSaveMs
                            )
                            .putBoolean(
                                KEY_AUTO_ENABLED,
                                autoEnabled
                            )
                            .apply()
                    }
                    // Sync completion is not a user stop request. In
                    // particular, a forgotten app context after dissociation
                    // must never terminate a still-active sensor job.
                    sensorSyncReady = true
                    activeSyncSource = null
                }

                is SensorSyncEvent.Failed -> {
                    sensorSyncInProgress = false
                    acquisitionSyncInProgress = false
                    alertSyncInProgress = false
                    expectedAcquisitions =
                        maxOf(expectedAcquisitions, event.acquisitions)
                    expectedAlerts = maxOf(expectedAlerts, event.alerts)
                    expectedErrors = maxOf(expectedErrors, event.errors)
                    storageWasFull = storageWasFull || event.storageWasFull
                    sensorSyncSummary = null
                    sensorSyncIncompleteSummary =
                        SensorSyncIncompleteSummary(
                            acquisitions = event.acquisitions,
                            alerts = event.alerts,
                            errors = event.errors
                        )
                    UvirErrorLog.record(
                        context = context.applicationContext,
                        source = "sensor_sync:${event.code}",
                        message =
                            "Offline synchronization failed; " +
                                "acquisitions=${event.acquisitions}, " +
                                "alerts=${event.alerts}, " +
                                "errors=${event.errors}"
                    )
                    sensorSyncReady = true
                    activeSyncSource = null
                }
            }
        }
    }

    LaunchedEffect(
        selectedSensorConnectionMode,
        selectedSensorIsConnected,
        selectedSensorDeviceId,
        sensorSettingsHydratedForDeviceId,
        selectedRuntimeInfo.firmwareVersion
    ) {
        if (!selectedSensorIsConnected) {
            sensorSyncInProgress = false
            acquisitionSyncInProgress = false
            alertSyncInProgress = false
            return@LaunchedEffect
        }

        val deviceId = selectedSensorDeviceId.trim()
        if (
            deviceId.isBlank() ||
            !sensorSettingsHydratedForDeviceId.equals(
                deviceId,
                ignoreCase = true
            )
        ) {
            return@LaunchedEffect
        }

        if (
            !firmwareIsCurrentForApp(
                selectedRuntimeInfo.firmwareVersion
            )
        ) {
            return@LaunchedEffect
        }

        // Firmware with a settings snapshot is authoritative on connection.
        // User changes are sent only by their explicit save actions below,
        // avoiding repeated NVS writes at every reconnect.
        if (
            firmwareSupportsSensorSettingsSnapshot(
                selectedRuntimeInfo.firmwareVersion
            )
        ) {
            return@LaunchedEffect
        }

        withContext(Dispatchers.IO) {
            Log.i(
                "UvirAlert",
                "Sending sensor alert configuration; enabled=" +
                    "${thresholdAlertSettings.enabled} " +
                    "repeat=${thresholdAlertSettings.repeatSeconds}s"
            )
            val commands = buildList {
                add(sensorTimeCommand())
                add(sensorParametersCommand(sensorParameters))
                if (
                    firmwareSupportsSensorCalibration(
                        selectedRuntimeInfo.firmwareVersion
                    )
                ) {
                    add(sensorCalibrationCommand(sensorCalibrationSettings))
                }
                add(
                    sensorSamplingCommand(
                        AcquisitionParameters(
                            samplesPerMeasurement = samplesPerMeasurement,
                            sampleSpacingMs = sampleSpacingMs,
                            discardExtremes = discardExtremes
                        )
                    )
                )
                addAll(
                    sensorAlertCommands(
                        settings = thresholdAlertSettings,
                        sessionId = thresholdAlertSessionId
                    )
                )
            }
            sendSensorControlCommands(commands)
        }
    }

    LaunchedEffect(
        useFakeSensorData,
        selectedSensorConnectionMode
    ) {
        sensorSyncReady = useFakeSensorData
    }

    LaunchedEffect(
        useFakeSensorData,
        selectedSensorIsConnected
    ) {
        if (!useFakeSensorData && !selectedSensorIsConnected) {
            sensorSyncReady = false
        }
    }

    val automaticWakeLock = remember(context) {
        (
                context.applicationContext
                    .getSystemService(
                        Context.POWER_SERVICE
                    ) as PowerManager
                ).newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "Uvir:AutomaticAcquisition"
            ).apply {
                setReferenceCounted(false)
            }
    }

    DisposableEffect(
        autoEnabled,
        automaticWakeLock
    ) {
        if (
            autoEnabled &&
            !automaticWakeLock.isHeld
        ) {
            automaticWakeLock.acquire()
        }

        onDispose {
            if (automaticWakeLock.isHeld) {
                automaticWakeLock.release()
            }
        }
    }

    val activeNotificationAlertMetrics =
        remember(currentThresholdViolations) {
            currentThresholdViolations
                .map { it.rule.metric }
                .toSet()
        }

    var notificationPermissionGranted by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT <
                    Build.VERSION_CODES.TIRAMISU ||
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val notificationPermissionLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            notificationPermissionGranted = granted
        }

    val thresholdNotificationsConfigured =
        thresholdAlertSettings.hasActiveMonitoring()

    val currentSelectedSensorIsConnected by
        rememberUpdatedState(selectedSensorIsConnected)
    val currentSelectedSensorIsConnecting by
        rememberUpdatedState(selectedSensorIsConnecting)
    var lastAnnouncedSensorConnection by remember {
        mutableStateOf<Boolean?>(null)
    }

    LaunchedEffect(
        useFakeSensorData,
        selectedSensorIsConnected,
        selectedSensorIsConnecting
    ) {
        if (useFakeSensorData) {
            lastAnnouncedSensorConnection = null
            return@LaunchedEffect
        }

        val previous = lastAnnouncedSensorConnection
        if (previous == null) {
            // Establish the initial state without announcing a disconnection
            // every time the app starts.
            lastAnnouncedSensorConnection = selectedSensorIsConnected
            return@LaunchedEffect
        }
        if (selectedSensorIsConnecting || previous == selectedSensorIsConnected) {
            return@LaunchedEffect
        }

        if (!selectedSensorIsConnected) {
            // Ignore very short transport hand-offs. A new state change
            // cancels this effect before the message is displayed.
            delay(1_200L)
            if (
                currentSelectedSensorIsConnected ||
                currentSelectedSensorIsConnecting
            ) {
                return@LaunchedEffect
            }
        }

        showUvirBottomMessage(
            context.applicationContext,
            resources.getString(
                if (selectedSensorIsConnected) {
                    R.string.sensor_connection_toast_connected
                } else {
                    R.string.sensor_connection_toast_disconnected
                }
            ),
            longDuration = false
        )
        lastAnnouncedSensorConnection = selectedSensorIsConnected
    }

    LaunchedEffect(pendingOfflineReopenState) {
        val pending =
            pendingOfflineReopenState
                ?: return@LaunchedEffect

        // Give the selected transport a short opportunity to reconnect. If it
        // does, the live status and eventual synchronization are more useful
        // than a stale estimate captured before the app was closed.
        delay(1_800L)
        if (
            useFakeSensorData ||
            currentSelectedSensorIsConnected ||
            currentSelectedSensorIsConnecting
        ) {
            return@LaunchedEffect
        }

        val now = System.currentTimeMillis()
        val secondsUntilNextAcquisition =
            if (
                pending.automaticActive &&
                pending.nextAutomaticSaveMs > now
            ) {
                (pending.nextAutomaticSaveMs - now) / 1_000L
            } else {
                0L
            }
        val remainingAutomaticWindow =
            if (
                pending.automaticActive &&
                pending.automaticEndMs > 0L
            ) {
                ((pending.automaticEndMs - now) / 1_000L)
                    .coerceAtLeast(0L)
            } else {
                null
            }
        val automaticRunAfterNext =
            remainingAutomaticWindow?.let { remaining ->
                (remaining - secondsUntilNextAcquisition)
                    .coerceAtLeast(0L)
            }
        val remainingAutomaticAcquisitions =
            if (pending.automaticActive && autoLimitEnabled) {
                (autoMaxCount - pending.automaticCompletedCount)
                    .coerceAtLeast(0)
            } else {
                null
            }

        updateOfflineDisconnectionNotice(
            UvirOfflineDisconnectionNotice(
                estimate =
                    if (sensorParameters.autonomousRecordingEnabled) {
                        storedSensorRuntimeSnapshot?.info?.let { info ->
                            estimateOfflineAutonomy(
                                runtimeInfo = info,
                                automaticIntervalSeconds =
                                    autoIntervalSeconds.takeIf {
                                        pending.automaticActive && !autoExternalCommand
                                    },
                                alertsEnabled = pending.alertsActive,
                                alertRepeatSeconds =
                                    thresholdAlertSettings.repeatSeconds,
                                startDelaySeconds =
                                    secondsUntilNextAcquisition,
                                automaticDurationSeconds =
                                    automaticRunAfterNext,
                                maximumAutomaticAcquisitions =
                                    remainingAutomaticAcquisitions,
                                samplesPerMeasurement =
                                    samplesPerMeasurement,
                                sampleSpacingMs = sampleSpacingMs
                            )?.withConditionalSchedule(autoConditionalPlan.takeIf { autoEnabled }, autoConditionalWaiting)?.withKnownAutomaticSchedule(
                                automaticActive = pending.automaticActive,
                                scheduleKnown = automaticScheduleKnown
                            )
                        }
                    } else {
                        null
                    },
                autonomousRecordingEnabled =
                    sensorParameters.autonomousRecordingEnabled,
                showNotification = false
            )
        )
    }

    LaunchedEffect(
        useFakeSensorData,
        selectedSensorConnectionMode,
        selectedSensorIsConnected,
        selectedSensorIsConnecting,
        selectedRuntimeInfo,
        autoEnabled,
        autoIntervalSeconds,
        autoUseDuration,
        autoEndMs,
        autoLimitEnabled,
        autoMaxCount,
        autoCompletedCount,
        autoNextSaveMs,
        automaticScheduleKnown,
        thresholdNotificationsConfigured,
        thresholdAlertSettings.repeatSeconds,
        sensorParameters.autonomousRecordingEnabled,
        samplesPerMeasurement,
        sampleSpacingMs
    ) {
        if (useFakeSensorData) {
            sensorWasConnected = false
            updateOfflineDisconnectionNotice(null)
            return@LaunchedEffect
        }

        if (selectedSensorIsConnected) {
            lastConnectedSensorRuntimeInfo = selectedRuntimeInfo
            sensorWasConnected = true
            updateOfflineDisconnectionNotice(null)
            return@LaunchedEffect
        }

        if (selectedSensorIsConnecting) {
            return@LaunchedEffect
        }

        if (
            sensorWasConnected &&
            (autoEnabled || thresholdNotificationsConfigured)
        ) {
            // Ignore very short transport hand-offs and transient reconnects.
            delay(1_200L)

            val now = System.currentTimeMillis()
            val rawSecondsUntilNextAcquisition =
                if (autoEnabled) {
                    ((autoNextSaveMs - now) / 1_000L)
                        .coerceAtLeast(0L)
                } else {
                    0L
                }
            val remainingAutomaticWindow =
                if (autoEnabled && autoUseDuration && autoEndMs > 0L) {
                    ((autoEndMs - now) / 1_000L)
                        .coerceAtLeast(0L)
                } else {
                    null
                }
            val secondsUntilNextAcquisition =
                remainingAutomaticWindow?.let { remaining ->
                    minOf(
                        rawSecondsUntilNextAcquisition,
                        remaining
                    )
                } ?: rawSecondsUntilNextAcquisition
            val automaticRunAfterNext =
                remainingAutomaticWindow?.let { remaining ->
                    (remaining - secondsUntilNextAcquisition)
                        .coerceAtLeast(0L)
                }
            val remainingAutomaticAcquisitions =
                if (autoEnabled && autoLimitEnabled) {
                    (autoMaxCount - autoCompletedCount)
                        .coerceAtLeast(0)
                } else {
                    null
                }

            updateOfflineDisconnectionNotice(
                UvirOfflineDisconnectionNotice(
                    estimate =
                        if (sensorParameters.autonomousRecordingEnabled) {
                            estimateOfflineAutonomy(
                                runtimeInfo =
                                    lastConnectedSensorRuntimeInfo,
                                automaticIntervalSeconds =
                                    autoIntervalSeconds.takeIf {
                                        autoEnabled && !autoExternalCommand
                                    },
                                alertsEnabled =
                                    thresholdNotificationsConfigured,
                                alertRepeatSeconds =
                                    thresholdAlertSettings.repeatSeconds,
                                startDelaySeconds =
                                    secondsUntilNextAcquisition,
                                automaticDurationSeconds =
                                    automaticRunAfterNext,
                                maximumAutomaticAcquisitions =
                                    remainingAutomaticAcquisitions,
                                samplesPerMeasurement =
                                    samplesPerMeasurement,
                                sampleSpacingMs = sampleSpacingMs
                            )?.withConditionalSchedule(autoConditionalPlan.takeIf { autoEnabled }, autoConditionalWaiting)?.withKnownAutomaticSchedule(
                                automaticActive = autoEnabled,
                                scheduleKnown = automaticScheduleKnown
                            )
                        } else {
                            null
                        },
                    autonomousRecordingEnabled =
                        sensorParameters.autonomousRecordingEnabled
                )
            )
        }

        sensorWasConnected = false
    }

    LaunchedEffect(
        autoEnabled,
        thresholdNotificationsConfigured,
        offlineDisconnectionNotice,
        sensorSyncSummary
    ) {
        if (
            (
                autoEnabled ||
                thresholdNotificationsConfigured ||
                offlineDisconnectionNotice != null ||
                sensorSyncSummary != null
            ) &&
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.TIRAMISU &&
            !notificationPermissionGranted
        ) {
            notificationPermissionLauncher.launch(
                Manifest.permission.POST_NOTIFICATIONS
            )
        }
    }

    LaunchedEffect(
        autoEnabled,
        autoCompletedCount,
        notificationPermissionGranted
    ) {
        if (autoEnabled && notificationPermissionGranted) {
            updateAutomaticAcquisitionNotification(
                context = context.applicationContext,
                completedCount = autoCompletedCount
            )
        } else if (!autoEnabled) {
            cancelAutomaticAcquisitionNotification(
                context.applicationContext
            )
        }
    }

    val currentThresholdNotificationAlert =
        remember(
            activeNotificationAlertMetrics,
            latestThresholdNotificationAlert
        ) {
            if (activeNotificationAlertMetrics.isEmpty()) {
                null
            } else {
                latestThresholdNotificationAlert
                    ?.takeIf { alert ->
                        alert.metric in
                                activeNotificationAlertMetrics
                    }
                    ?: currentThresholdViolations
                        .firstOrNull { violation ->
                            violation.rule.metric in
                                    activeNotificationAlertMetrics
                        }
                        ?.let { violation ->
                            ThresholdNotificationAlert(
                                metric = violation.rule.metric,
                                value = violation.value,
                                timestamp = System.currentTimeMillis()
                            )
                        }
            }
        }

    LaunchedEffect(
        currentThresholdNotificationAlert,
        notificationPermissionGranted
    ) {
        val activeAlert =
            currentThresholdNotificationAlert

        if (
            activeAlert != null &&
            notificationPermissionGranted
        ) {
            updateThresholdAlertNotification(
                context = context.applicationContext,
                alert = activeAlert
            )
        } else if (activeAlert == null) {
            cancelThresholdAlertNotification(
                context.applicationContext
            )
        }
    }

    LaunchedEffect(
        offlineDisconnectionNotice,
        notificationPermissionGranted
    ) {
        val notice = offlineDisconnectionNotice
        if (
            notice?.showNotification == true &&
            notificationPermissionGranted
        ) {
            showOfflineDisconnectionNotification(
                context = context.applicationContext,
                notice = notice
            )
        } else if (notice == null) {
            cancelOfflineDisconnectionNotification(
                context.applicationContext
            )
        }
    }

    LaunchedEffect(
        sensorSyncSummary,
        notificationPermissionGranted
    ) {
        val summary = sensorSyncSummary
        if (
            summary != null &&
            notificationPermissionGranted
        ) {
            showSensorSyncCompleteNotification(
                context = context.applicationContext,
                summary = summary
            )
        }
    }

    fun stopAutomaticAcquisition() {

        // The sensor owns an autonomous session while disconnected. Do not
        // pretend to stop it locally when no command can reach the hardware.
        if (!autoSessionSimulated && !selectedSensorIsConnected) {
            return
        }

        if (automaticStopInProgress) return

        if (autoSessionSimulated) {
            autoEnabled = false
            preferences.edit()
                .putBoolean(KEY_AUTO_ENABLED, false)
                .apply()
            showUvirBottomMessage(
                context,
                resources.getString(R.string.automatic_stopped),
                longDuration = false
            )
            return
        }

        automaticStopInProgress = true
        val selectedMode =
            SensorConnectionMode.fromStoredValue(sensorConnectionModeValue)
        val stopTimeoutMs =
            (
                samplesPerMeasurement.toLong() *
                    (sampleSpacingMs.coerceAtLeast(150L) + 1_000L) +
                    5_000L
            ).coerceIn(5_000L, 120_000L)

        sensorConnectionScope.launch(Dispatchers.IO) {
            val confirmed =
                when (selectedMode) {
                    SensorConnectionMode.USB ->
                        usbSensorManager.stopAutomaticAcquisitionAndAwait(
                            stopTimeoutMs
                        )
                    SensorConnectionMode.WIFI,
                    SensorConnectionMode.BLUETOOTH,
                    SensorConnectionMode.INTERNET ->
                        wirelessSensorManager.stopAutomaticAcquisitionAndAwait(
                            stopTimeoutMs
                        )
                }
            if (!confirmed) {
                withContext(Dispatchers.Main) {
                    if (automaticStopInProgress) {
                        automaticStopInProgress = false
                        showUvirBottomMessage(
                            context,
                            resources.getString(R.string.automatic_stop_failed)
                        )
                    }
                }
            }
        }
    }

    fun startAutomaticAcquisition(
        request: AutomaticAcquisitionRequest
    ) {

        // Safety guard: never start a second AUTO session
        // while one is already running.
        if (autoEnabled) {
            return
        }

        if (request.externalCommand && !useFakeSensorData &&
            !firmwareSupportsExternalCommand(selectedRuntimeInfo.firmwareVersion)) {
            showUvirBottomMessage(
                context,
                resources.getString(R.string.sensor_firmware_update_required)
            )
            return
        }
        val plan = request.conditionalPlan?.takeUnless { request.externalCommand }?.let {
            conditionalPlanFromRules(autoConditionalRules, it.match, it.action)
        }
        if (request.conditionalPlan != null && (plan == null ||
                (!useFakeSensorData && !firmwareSupportsImmediateConditionalAcquisition(selectedRuntimeInfo.firmwareVersion)))) {
            showUvirBottomMessage(context, resources.getString(
                if (plan == null) R.string.conditional_no_rules else R.string.sensor_firmware_update_required
            ))
            return
        }
        val effectiveRequest = request.copy(conditionalPlan = plan)
        autoExternalCommand = request.externalCommand
        autoConditionalPlan = plan
        autoConditionalEnabled = plan != null
        autoConditionalWaiting = plan?.action == AcquisitionConditionAction.START
        plan?.let { autoConditionalMatch = it.match; autoConditionalAction = it.action }

        // A manual and an automatic session cannot coexist. Starting AUTO
        // permanently closes the last resumable manual session.
        clearRememberedManualSession(
            preferences
        )

        val now =
            System.currentTimeMillis()

        val startDelayMs =
            request.startDelaySeconds
                .coerceAtLeast(0L)
                .coerceAtMost(
                    Long.MAX_VALUE / 1000L
                ) * 1000L

        val startAt =
            if (request.externalCommand) {
                now
            } else if (request.useStartDelay) {
                now + startDelayMs
            } else {
                now
            }

        val newSessionId =
            database.nextSessionId()

        val durationMs =
            request.durationSeconds
                .coerceAtLeast(0L)
                .coerceAtMost(
                    Long.MAX_VALUE / 1000L
                ) * 1000L

        val endAt =
            if (!request.externalCommand && request.useDuration && !autoConditionalWaiting) {
                startAt + durationMs
            } else {
                0L
            }

        autoIntervalSeconds =
            request.intervalSeconds

        val normalizedSessionNote =
            limitUvirNote(request.note).trim()

        database.startAcquisitionSession(
            sessionId = newSessionId,
            note = normalizedSessionNote,
            startedAt = now,
            sensorDeviceId = selectedSensorDeviceId,
            externalCommand = request.externalCommand
        )

        autoNote = normalizedSessionNote

        autoUseStartDelay =
            request.useStartDelay

        autoStartDelaySeconds =
            request.startDelaySeconds

        autoUseDuration =
            request.useDuration

        autoDurationSeconds =
            request.durationSeconds

        autoLimitEnabled =
            request.limitEnabled

        autoMaxCount =
            request.maxAcquisitions
                .coerceAtLeast(1)

        autoCompletedCount = 0
        autoSessionId = newSessionId
        automaticJobReadbackGuard = automaticJobReadbackGuard.afterStart()
        autoSessionSimulated = useFakeSensorData
        autoNextSaveMs = startAt
        autoEndMs = endAt
        autoEnabled = true
        automaticScheduleKnown = true

        preferences.edit()
            .putBoolean(KEY_AUTO_EXTERNAL_COMMAND, request.externalCommand)
            .putString(KEY_AUTO_CONDITIONAL_PLAN, plan?.encode() ?: "")
            .putBoolean(KEY_AUTO_CONDITIONAL_ENABLED, autoConditionalEnabled)
            .putBoolean(KEY_AUTO_CONDITIONAL_WAITING, autoConditionalWaiting)
            .putString(KEY_AUTO_CONDITIONAL_MATCH, autoConditionalMatch.name)
            .putString(KEY_AUTO_CONDITIONAL_ACTION, autoConditionalAction.name)
            .putBoolean(KEY_AUTO_SCHEDULE_KNOWN, true)
            .putBoolean(KEY_AUTO_SIMULATED, autoSessionSimulated)
            .putLong(KEY_AUTO_FIRST_ALLOWED_MS, startAt)
            .putBoolean(
                KEY_AUTO_ENABLED,
                true
            )
            .putLong(
                KEY_AUTO_INTERVAL_SECONDS,
                request.intervalSeconds
            )
            .putString(
                KEY_AUTO_NOTE,
                normalizedSessionNote
            )
            .putBoolean(
                KEY_AUTO_USE_START_DELAY,
                request.useStartDelay
            )
            .putLong(
                KEY_AUTO_START_DELAY_SECONDS,
                request.startDelaySeconds
            )
            .putBoolean(
                KEY_AUTO_USE_DURATION,
                request.useDuration
            )
            .putLong(
                KEY_AUTO_DURATION_SECONDS,
                request.durationSeconds
            )
            .putLong(
                KEY_AUTO_END_MS,
                endAt
            )
            .putBoolean(
                KEY_AUTO_LIMIT_ENABLED,
                request.limitEnabled
            )
            .putInt(
                KEY_AUTO_MAX_COUNT,
                request.maxAcquisitions
                    .coerceAtLeast(1)
            )
            .putInt(
                KEY_AUTO_COMPLETED_COUNT,
                0
            )
            .putLong(
                KEY_AUTO_SESSION_ID,
                newSessionId
            )
            .putLong(
                KEY_AUTO_NEXT_SAVE_MS,
                startAt
            )
            .apply()

        val acquisitionParameters = AcquisitionParameters(
            samplesPerMeasurement = samplesPerMeasurement,
            sampleSpacingMs = sampleSpacingMs,
            discardExtremes = discardExtremes
        )
        // Debug data is scheduled locally by the simulation effect above.
        // It must never start a real job on a sensor kept connected in standby.
        if (useFakeSensorData) return
        sensorConnectionScope.launch(Dispatchers.IO) {
            sendSensorControlCommands(
                listOf(
                    sensorTimeCommand(),
                    sensorOfflineJobCommand(
                        request = effectiveRequest,
                        sessionId = newSessionId,
                        nextAtMs = startAt,
                        endAtMs = endAt,
                        completedCount = 0,
                        acquisitionParameters = acquisitionParameters
                    )
                )
            )
        }
    }

    SideEffect {
        UvirRemoteRuntime.snapshot.set(
            UvirRemoteSnapshot(
                acquisition =
                    latestMeasurement.get(),
                liveReady = liveReady,
                autoEnabled = autoEnabled,
                autoIntervalSeconds =
                    autoIntervalSeconds,
                autoCompletedCount =
                    autoCompletedCount,
                autoLimitEnabled =
                    autoLimitEnabled,
                autoMaxCount =
                    autoMaxCount,
                autoNextSaveMs =
                    autoNextSaveMs,
                screen = screen
            )
        )
    }

    LaunchedEffect(database) {
        for (
            command in
            UvirRemoteRuntime.commands
        ) {
            val response =
                try {
                    val payload =
                        command.request
                            .optJSONObject(
                                "payload"
                            ) ?: JSONObject()

                    when (command.action) {
                        "save_acquisition" -> {
                            if (!liveReady) {
                                throw IllegalStateException(
                                    "Acquisizione non ancora pronta."
                                )
                            }

                            // A manual acquisition takes ownership of the
                            // active session and therefore stops AUTO first.
                            val automaticWasEnabled =
                                autoEnabled

                            if (automaticWasEnabled) {
                                stopAutomaticAcquisition()
                            }

                            val selectedMode =
                                ManualSaveMode.fromStoredValue(
                                    payload.optString(
                                        "session_mode",
                                        ManualSaveMode
                                            .SINGLE
                                            .storedValue
                                    )
                                )
                            val requestedMode =
                                if (
                                    automaticWasEnabled &&
                                    selectedMode ==
                                    ManualSaveMode.LAST_MANUAL_SESSION
                                ) {
                                    ManualSaveMode.SINGLE
                                } else {
                                    selectedMode
                                }
                            val manualSession =
                                resolveManualSession(
                                    database = database,
                                    preferences = preferences,
                                    requestedMode = requestedMode
                                )
                            val id =
                                database.saveAcquisition(
                                    sample =
                                        latestMeasurement.get(),
                                    note =
                                        payload.optString(
                                            "note",
                                            ""
                                        ).trim(),
                                    automatic = false,
                                    sessionId =
                                        manualSession.sessionId,
                                    sessionSequence =
                                        manualSession.sequence,
                                    sensorDeviceId =
                                        selectedSensorDeviceId
                                )

                            if (id == -1L) {
                                throw IllegalStateException(
                                    "Salvataggio non riuscito."
                                )
                            }

                            rememberManualSaveSuccess(
                                preferences,
                                manualSession
                            )
                            incrementUnreadAcquisitions()
                            playAcquisitionFeedback()

                            withContext(Dispatchers.IO) {
                                sendSensorControlCommands(
                                    listOf("LED_EVENT ACQUISITION")
                                )
                            }

                            remoteOk(
                                JSONObject()
                                    .put("id", id)
                                    .put(
                                        "session_id",
                                        manualSession.sessionId
                                            ?: JSONObject.NULL
                                    )
                                    .put(
                                        "session_sequence",
                                        manualSession.sequence
                                            ?: JSONObject.NULL
                                    )
                            )
                        }

                        "start_auto" -> {
                            if (autoEnabled) {
                                throw IllegalStateException(
                                    "Acquisizione automatica già attiva."
                                )
                            }

                            val intervalSeconds =
                                payload.optLong(
                                    "interval_seconds",
                                    0L
                                )

                            if (intervalSeconds <= 0L) {
                                throw IllegalArgumentException(
                                    "Intervallo non valido."
                                )
                            }

                            val limitEnabled =
                                payload.optBoolean(
                                    "limit_enabled",
                                    false
                                )

                            val maxAcquisitions =
                                payload.optInt(
                                    "max_acquisitions",
                                    1
                                )

                            if (
                                limitEnabled &&
                                maxAcquisitions <= 0
                            ) {
                                throw IllegalArgumentException(
                                    "Numero massimo non valido."
                                )
                            }

                            startAutomaticAcquisition(
                                AutomaticAcquisitionRequest(
                                    intervalSeconds =
                                        intervalSeconds,
                                    note =
                                        payload.optString(
                                            "note",
                                            ""
                                        ).trim(),
                                    useStartDelay =
                                        payload.optBoolean(
                                            "use_start_delay",
                                            false
                                        ),
                                    startDelaySeconds =
                                        payload.optLong(
                                            "start_delay_seconds",
                                            0L
                                        ).coerceAtLeast(0L),
                                    useDuration =
                                        payload.optBoolean(
                                            "use_duration",
                                            false
                                        ),
                                    durationSeconds =
                                        payload.optLong(
                                            "duration_seconds",
                                            0L
                                        ).coerceAtLeast(0L),
                                    limitEnabled =
                                        limitEnabled,
                                    maxAcquisitions =
                                        maxAcquisitions
                                            .coerceAtLeast(1)
                                )
                            )

                            remoteOk(
                                JSONObject()
                                    .put("started", true)
                            )
                        }

                        "stop_auto" -> {
                            stopAutomaticAcquisition()

                            remoteOk(
                                JSONObject()
                                    .put("stopped", true)
                            )
                        }

                        "list_acquisitions" -> {
                            val rememberedManualSessionId =
                                rememberedManualSessionChoice(
                                    preferences
                                ).lastSessionId
                            val activeManualSessionId =
                                rememberedManualSessionId
                                    ?.takeIf {
                                        database
                                            .acquisitionCountForSession(
                                                it
                                            ) > 0
                                    }

                            if (
                                rememberedManualSessionId != null &&
                                activeManualSessionId == null
                            ) {
                                clearRememberedManualSession(
                                    preferences
                                )
                            }

                            remoteOk(
                                recordsToJson(
                                    database
                                        .readAllRecords()
                                )
                                    .put(
                                        "acquisition_counter",
                                        database
                                            .currentAcquisitionCounter()
                                    )
                                    .put(
                                        "session_counter",
                                        database
                                            .currentSessionCounter()
                                    )
                                    .put(
                                        "manual_session_id",
                                        activeManualSessionId
                                            ?: JSONObject.NULL
                                    )
                            )
                        }

                        "update_acquisition" -> {
                            val record =
                                payload
                                    .getJSONObject(
                                        "record"
                                    )
                                    .toSavedRecordDetail()

                            val updated =
                                database.updateAcquisition(
                                    record
                                )

                            remoteOk(
                                JSONObject()
                                    .put(
                                        "updated",
                                        updated
                                    )
                            )
                        }

                        "delete_acquisitions" -> {
                            val idsJson =
                                payload.getJSONArray(
                                    "ids"
                                )

                            val ids =
                                buildList {
                                    for (
                                        index in
                                        0 until idsJson.length()
                                    ) {
                                        idsJson.optLong(
                                            index,
                                            -1L
                                        ).takeIf {
                                            it > 0L
                                        }?.let(::add)
                                    }
                                }.distinct()

                            val deleted =
                                database.deleteRecords(ids)

                            remoteOk(
                                JSONObject()
                                    .put(
                                        "deleted",
                                        deleted
                                    )
                            )
                        }

                        "delete_all" -> {
                            val deleted =
                                database
                                    .deleteAllAcquisitions()

                            remoteOk(
                                JSONObject()
                                    .put(
                                        "deleted",
                                        deleted
                                    )
                            )
                        }

                        "reset_counters" -> {
                            if (autoEnabled) {
                                throw IllegalStateException(
                                    "Ferma prima l'acquisizione automatica."
                                )
                            }

                            val deleted =
                                database.resetAllCounters()
                            autoSessionId = 0L
                            autoCompletedCount = 0
                            clearUnreadAcquisitions()
                            clearUnreadAlerts()
                            thresholdAlertSessionId =
                                if (
                                    thresholdAlertSettings
                                        .hasActiveMonitoring()
                                ) {
                                    database.nextSessionId()
                                } else {
                                    0L
                                }
                            preferences.edit()
                                .putLong(
                                    KEY_AUTO_SESSION_ID,
                                    0L
                                )
                                .putInt(
                                    KEY_AUTO_COMPLETED_COUNT,
                                    0
                                )
                                .putLong(
                                    KEY_THRESHOLD_ALERT_SESSION_ID,
                                    thresholdAlertSessionId
                                )
                                .apply()

                            remoteOk(
                                JSONObject()
                                    .put("deleted", deleted)
                                    .put("acquisition_counter", 0)
                                    .put("session_counter", 0)
                            )
                        }

                        "replace_acquisitions" -> {
                            val recordsJson =
                                payload.getJSONArray(
                                    "records"
                                )

                            if (
                                recordsJson.length() >
                                100_000
                            ) {
                                throw IllegalArgumentException(
                                    "Troppe acquisizioni."
                                )
                            }

                            val records =
                                buildList {
                                    for (
                                        index in
                                        0 until recordsJson.length()
                                    ) {
                                        add(
                                            recordsJson
                                                .getJSONObject(index)
                                                .toSavedRecordDetail()
                                        )
                                    }
                                }

                            val replaced =
                                database
                                    .replaceAllAcquisitions(
                                        records,
                                        acquisitionCounter =
                                            payload.optLong(
                                                "acquisition_counter",
                                                0L
                                            ),
                                        sessionCounter =
                                            payload.optLong(
                                                "session_counter",
                                                0L
                                            )
                                    )

                            remoteOk(
                                JSONObject()
                                    .put(
                                        "replaced",
                                        replaced
                                    )
                            )
                        }

                        "open_screen" -> {
                            when (
                                payload.optString(
                                    "screen",
                                    "live"
                                ).lowercase()
                            ) {
                                "live" -> {
                                    selectedRecordId = null
                                    screen = AppScreen.LIVE
                                }

                                "acquisitions",
                                "history" -> {
                                    selectedRecordId = null
                                    clearUnreadAcquisitions()
                                    screen = AppScreen.HISTORY
                                }

                                "detail" -> {
                                    val id =
                                        payload.optLong(
                                            "id",
                                            -1L
                                        )

                                    if (
                                        id <= 0L ||
                                        database.readRecord(id) == null
                                    ) {
                                        throw IllegalArgumentException(
                                            "Acquisizione non trovata."
                                        )
                                    }

                                    selectedRecordId = id
                                    screen = AppScreen.DETAIL
                                }

                                else -> {
                                    throw IllegalArgumentException(
                                        "Schermata non valida."
                                    )
                                }
                            }

                            remoteOk(
                                JSONObject()
                                    .put(
                                        "screen",
                                        screen.name.lowercase()
                                    )
                            )
                        }

                        else -> {
                            remoteError(
                                "Azione non supportata: ${command.action}"
                            )
                        }
                    }
                } catch (error: Exception) {
                    remoteError(
                        error.message ?:
                        error.javaClass.simpleName
                    )
                }

            command.response.complete(
                response
            )
        }
    }

    CompositionLocalProvider(
        LocalUvirNumericFormat provides numericFormat,
        LocalUvirDateFormat provides dateFormat,
        LocalUvirTimeFormat provides
            resolveUvirTimeFormat(context, timeFormat),
        LocalUvirIrradianceUnit provides irradianceUnit
    ) {
    when (screen) {

        AppScreen.LIVE -> {

            key(openHomeRequestId) {
            LiveScreen(
                database = database,
                measurement = measurement,
                liveReady = liveReady,
                liveListState = liveListState,
                automaticListState =
                    automaticListState,
                versionInfoScrollState =
                    versionInfoScrollState,
                parametersScrollState =
                    parametersScrollState,

                viewMode = viewMode,
                onViewModeChanged = {
                    setViewMode(it)
                },

                samplesPerMeasurement =
                    samplesPerMeasurement,
                sampleSpacingMs =
                    sampleSpacingMs,
                discardExtremes =
                    discardExtremes,

                useFakeSensorData =
                    useFakeSensorData,
                fakeSensorOutOfRangeEnabled = fakeSensorOutOfRangeEnabled,
                usbSensorState =
                    usbSensorState,
                wirelessSensorState =
                    wirelessSensorState,
                sensorProfiles = sensorProfiles,
                selectedSensorDeviceId = selectedSensorDeviceId,
                sensorSelectionEnabled = !sensorSyncInProgress &&
                    !automaticStopInProgress && !auxiliarySensorCommandInProgress &&
                    sensorConnectionSwitchJob?.isActive != true,
                onSensorSelected = { deviceId ->
                    if (autoEnabled || thresholdNotificationsConfigured) {
                        val now = System.currentTimeMillis()
                        val remainingWindow = if (autoEnabled && autoUseDuration && autoEndMs > 0L)
                            ((autoEndMs - now) / 1_000L).coerceAtLeast(0L) else null
                        val nextDelay = if (autoEnabled)
                            ((autoNextSaveMs - now) / 1_000L).coerceAtLeast(0L) else 0L
                        val effectiveDelay = remainingWindow?.let { minOf(it, nextDelay) } ?: nextDelay
                        updateOfflineDisconnectionNotice(UvirOfflineDisconnectionNotice(
                            estimate = if (sensorParameters.autonomousRecordingEnabled)
                                estimateOfflineAutonomy(
                                    runtimeInfo = lastConnectedSensorRuntimeInfo,
                                    automaticIntervalSeconds = autoIntervalSeconds.takeIf {
                                        autoEnabled && !autoExternalCommand
                                    },
                                    alertsEnabled = thresholdNotificationsConfigured,
                                    alertRepeatSeconds = thresholdAlertSettings.repeatSeconds,
                                    startDelaySeconds = effectiveDelay,
                                    automaticDurationSeconds = remainingWindow?.let { (it - effectiveDelay).coerceAtLeast(0L) },
                                    maximumAutomaticAcquisitions = if (autoEnabled && autoLimitEnabled)
                                        (autoMaxCount - autoCompletedCount).coerceAtLeast(0) else null,
                                    samplesPerMeasurement = samplesPerMeasurement,
                                    sampleSpacingMs = sampleSpacingMs
                                )?.withConditionalSchedule(autoConditionalPlan.takeIf { autoEnabled }, autoConditionalWaiting)?.withKnownAutomaticSchedule(autoEnabled, automaticScheduleKnown) else null,
                            autonomousRecordingEnabled = sensorParameters.autonomousRecordingEnabled
                        ))
                    }
                    onSensorSelected(deviceId)
                },
                onUseFakeSensorDataChanged = { enabled ->
                    useFakeSensorData = enabled
                    preferences.edit()
                        .putBoolean(
                            KEY_USE_FAKE_SENSOR_DATA,
                            enabled
                        )
                        .apply()
                },
                onFakeSensorOutOfRangeChanged = { enabled ->
                    fakeSensorOutOfRangeEnabled = enabled
                    preferences.edit()
                        .putBoolean(KEY_FAKE_SENSOR_OUT_OF_RANGE, enabled)
                        .apply()
                },
                sensorConnectionMode =
                    SensorConnectionMode.fromStoredValue(
                        sensorConnectionModeValue
                    ),
                onSensorConnectionModeChanged = { mode ->
                    val previousMode =
                        SensorConnectionMode.fromStoredValue(
                            sensorConnectionModeValue
                        )
                    val completeModeChange = {
                        usbSensorManager.configureWirelessMode(
                            mode
                        )
                        sensorConnectionModeValue = mode.name
                        preferences.edit()
                            .putString(
                                KEY_SENSOR_CONNECTION_MODE,
                                mode.name
                            )
                            .apply {
                                if (
                                    mode !=
                                    SensorConnectionMode.USB
                                ) {
                                    putString(
                                        KEY_LAST_WIRELESS_SENSOR_CONNECTION_MODE,
                                        mode.name
                                    )
                                }
                            }
                            .apply()

                        if (
                            mode != SensorConnectionMode.USB
                        ) {
                            lastWirelessSensorConnectionModeValue =
                                mode.name
                        }

                        if (
                            mode ==
                            SensorConnectionMode.BLUETOOTH
                        ) {
                            onRequestBluetoothPermission()
                        }
                    }

                    sensorConnectionSwitchJob?.cancel()
                    // Keep the old authenticated transport alive until the
                    // ESP32 explicitly acknowledges the new radio. This
                    // prevents a socket close with unread samples from
                    // discarding the switch command.
                    sensorConnectionSwitchJob =
                        sensorConnectionScope.launch {
                            if (
                                mode != SensorConnectionMode.USB
                            ) {
                                withContext(Dispatchers.IO) {
                                    wirelessSensorManager
                                        .requestWirelessModeAndAwait(
                                            mode = mode,
                                            timeoutMs =
                                                (
                                                        samplesPerMeasurement
                                                            .coerceAtLeast(1)
                                                            .toLong() *
                                                        sampleSpacingMs +
                                                        3_000L
                                                        )
                                        )
                                }
                            } else if (
                                previousMode != SensorConnectionMode.USB &&
                                wirelessSensorState.status ==
                                    WirelessSensorConnectionStatus.CONNECTED
                            ) {
                                withContext(Dispatchers.IO) {
                                    wirelessSensorManager
                                        .sendSensorControlCommands(
                                            listOf("STOP")
                                        )
                                }
                            }
                            completeModeChange()
                        }
                },
                onRequestBluetoothPermission =
                    onRequestBluetoothPermission,
                onConfigureSensorWifi = { ssid, password ->
                    withContext(Dispatchers.IO) {
                        val configuredOverUsb =
                            usbSensorState.status ==
                                UsbSensorConnectionStatus.CONNECTED &&
                                usbSensorManager.configureWifiNetwork(
                                    ssid,
                                    password
                                )
                        configuredOverUsb ||
                            wirelessSensorManager.configureWifiNetwork(
                                ssid,
                                password
                            )
                    }
                },
                onConfigureSensorInternet = { configuration ->
                    withContext(Dispatchers.IO) {
                        val configuredOverUsb =
                            usbSensorState.status ==
                                UsbSensorConnectionStatus.CONNECTED &&
                                usbSensorManager.configureInternet(configuration)
                        configuredOverUsb ||
                            wirelessSensorManager.configureInternet(configuration)
                    }
                },
                onApplySensorRadioSettings =
                    { settings ->
                        withContext(Dispatchers.IO) {
                            if (
                                usbSensorState.status ==
                                UsbSensorConnectionStatus.CONNECTED
                            ) {
                                usbSensorManager
                                    .configureWirelessTransportsEnabled(
                                        settings
                                    )
                            } else {
                                wirelessSensorManager
                                    .configureWirelessTransportsEnabled(
                                        settings
                                    )
                            }
                        }
                    },
                sensorParameters = sensorParameters,
                onApplySensorParameters = { parameters ->
                    val accepted = withContext(Dispatchers.IO) {
                        sendSensorControlCommands(
                            buildList {
                                add(sensorParametersCommand(parameters))
                                if (
                                    firmwareSupportsSensorSettingsSnapshot(
                                        selectedRuntimeInfo.firmwareVersion
                                    )
                                ) {
                                    add("HELLO")
                                }
                            }
                        )
                    }
                    if (accepted) {
                    sensorParameters = parameters
                    preferences.edit()
                        .putBoolean(
                            KEY_SENSOR_AUTONOMOUS_RECORDING,
                            parameters.autonomousRecordingEnabled
                        )
                        .putBoolean(
                            KEY_SENSOR_AUTOMATIC_SHUTDOWN_ENABLED,
                            parameters.automaticShutdownEnabled
                        )
                        .putInt(
                            KEY_SENSOR_AUTOMATIC_SHUTDOWN_SECONDS,
                            parameters.automaticShutdownSeconds
                        )
                        .putBoolean(
                            KEY_SENSOR_STATUS_LED_ENABLED,
                            parameters.statusLedEnabled
                        )
                        .putInt(
                            KEY_SENSOR_STATUS_LED_BRIGHTNESS,
                            parameters.statusLedBrightness
                        )
                        .putBoolean(
                            KEY_SENSOR_STATUS_BUZZER_ENABLED,
                            parameters.statusBuzzerEnabled
                        )
                        .putInt(
                            KEY_SENSOR_STATUS_BUZZER_VOLUME,
                            parameters.statusBuzzerVolume
                        )
                        .putBoolean(
                            KEY_SENSOR_EXTERNAL_COMMAND_ENABLED,
                            parameters.externalCommandEnabled
                        )
                        .apply()
                    }
                    accepted
                },

                onTestSensorStatusLed = {
                    beginAuxiliarySensorCommand(21_500L)
                    val accepted = withContext(Dispatchers.IO) {
                        runSensorStatusTest(led = true)
                    }
                    if (!accepted) finishAuxiliarySensorCommand()
                    accepted
                },
                onTestSensorStatusBuzzer = {
                    beginAuxiliarySensorCommand(9_530L)
                    val accepted = withContext(Dispatchers.IO) {
                        runSensorStatusTest(led = false)
                    }
                    if (!accepted) finishAuxiliarySensorCommand()
                    accepted
                },
                onStartDebugPerformance = { performance ->
                    beginAuxiliarySensorCommand((UvirDebugPerformance.entries.firstOrNull {
                        it.protocolValue == performance
                    }?.expectedDurationMs ?: 20_000L) + 5_000L)
                    val accepted = withContext(Dispatchers.IO) {
                        sendSensorControlCommands(
                            listOf(debugPerformanceCommand(performance))
                        )
                    }
                    if (!accepted) finishAuxiliarySensorCommand()
                    accepted
                },
                onStopDebugPerformance = {
                    val accepted = withContext(Dispatchers.IO) {
                        sendSensorControlCommands(
                            listOf("DEBUG_PERFORMANCE_STOP")
                        )
                    }
                    if (accepted) finishAuxiliarySensorCommand()
                    accepted
                },
                onDiagnosticProbe = { hardwareId, mode ->
                    // Pin diagnostics to the selected peer/source. Unlike normal
                    // controls, a probe must never fall back to another transport.
                    if (mode == SensorConnectionMode.USB) {
                        usbSensorManager.diagnosticProbe(hardwareId)
                    } else {
                        wirelessSensorManager.diagnosticProbe(hardwareId, mode)
                    }
                },
                onPowerOffSensor = {
                    withContext(Dispatchers.IO) {
                        sendSensorControlCommands(
                            listOf("POWER_OFF")
                        )
                    }
                },
                onDisassociateSensor = {
                    val disassociated = withContext(Dispatchers.IO) {
                        runCatching {
                        val disassociatedDeviceId = UvirSensorCredentialStore.load(context).deviceId
                        usbSensorManager.disconnectForSensorSelection()
                        wirelessSensorManager.disconnectForSensorSelection()
                        saveSelectedSensorContext(preferences, disassociatedDeviceId)
                        forgetSelectedSensorOperationalContext(preferences, disassociatedDeviceId)
                        val credentialsCleared =
                            UvirSensorCredentialStore.disassociate(context)
                        val runtimeInfoCleared =
                            UvirSensorRuntimeInfoStore.clearActive(context)
                        val operationalStateCleared =
                            clearSensorOperationalState(preferences)
                        if (
                            credentialsCleared && runtimeInfoCleared &&
                            operationalStateCleared
                        ) {
                            cancelAutomaticAcquisitionNotification(context)
                            cancelThresholdAlertNotification(context)
                            cancelOfflineDisconnectionNotification(context)
                        }
                        credentialsCleared && runtimeInfoCleared &&
                            operationalStateCleared
                        }.getOrElse { error ->
                            UvirErrorLog.record(context, "sensor_disassociation", error)
                            false
                        }
                    }
                    if (disassociated) {
                        // Forget the UI context, not the actual sensor job or
                        // historical database session. Clear the ID before
                        // the running flag so no session is marked finished.
                        sensorAssociationRevision++
                        autoSessionId = 0L
                        autoEnabled = false
                        autoCompletedCount = 0
                        autoNextSaveMs = 0L
                        autoEndMs = 0L
                        automaticStopInProgress = false
                        automaticScheduleKnown = true
                        thresholdAlertSessionId = 0L
                        thresholdAlertSettings =
                            thresholdAlertSettings.withoutAssociatedSensor()
                        latestThresholdNotificationAlert = null
                        sensorSettingsHydratedForDeviceId = ""
                        storedSensorRuntimeSnapshot = null
                        pendingOfflineReopenState = null
                        lastConnectedSensorRuntimeInfo = UvirSensorRuntimeInfo()
                        sensorWasConnected = false
                        updateOfflineDisconnectionNotice(null)
                        sensorSyncInProgress = false
                        acquisitionSyncInProgress = false
                        alertSyncInProgress = false
                        sensorSyncReady = false
                        sensorSyncSummary = null
                        sensorSyncIncompleteSummary = null
                        onSensorSelected("")
                    }
                    disassociated
                },
                onRestoreApplicationDefaults = {
                    withContext(Dispatchers.IO) {
                        restoreUvirApplicationDefaults(
                            context = context,
                            database = database
                        )
                    }
                },
                onRestoreSensorDefaults = {
                    withContext(Dispatchers.IO) {
                        restoreSensorDefaultsAndPowerOff()
                    }
                },
                sensorCalibrationSettings = sensorCalibrationSettings,
                onApplySensorCalibration = { calibration ->
                    if (
                        !firmwareSupportsSensorCalibration(
                            selectedRuntimeInfo.firmwareVersion
                        )
                    ) {
                        false
                    } else {
                    val applied =
                        withContext(Dispatchers.IO) {
                            sendSensorControlCommands(
                                buildList {
                                    add(sensorCalibrationCommand(calibration))
                                    if (
                                        firmwareSupportsSensorSettingsSnapshot(
                                            selectedRuntimeInfo.firmwareVersion
                                        )
                                    ) {
                                        add("HELLO")
                                    }
                                }
                            )
                        }
                    if (applied) {
                        sensorCalibrationSettings = calibration
                        preferences.edit()
                            .putFloat(
                                KEY_SENSOR_VISIBLE_CALIBRATION_FACTOR,
                                calibration.visibleFactor
                            )
                            .putFloat(
                                KEY_SENSOR_UV_CALIBRATION_FACTOR,
                                calibration.uvFactor
                            )
                            .apply()
                    }
                    applied
                    }
                },
                currentWifiSsid = currentWifiSsid,
                onRequestCurrentWifiSsid =
                    onRequestCurrentWifiSsid,
                appLanguage =
                    AppLanguage.fromStoredValue(
                        appLanguageValue
                    ),
                numericFormat = numericFormat,
                dateFormat = dateFormat,
                timeFormat = timeFormat,
                exportMode = exportMode,
                irradianceUnit = irradianceUnit,
                onAppLanguageChanged = { language ->
                    appLanguageValue = language.storedValue
                    val savedLanguage =
                        preferences.getString(
                            KEY_APP_LANGUAGE,
                            AppLanguage.SYSTEM.storedValue
                        ) ?: AppLanguage.SYSTEM.storedValue

                    preferences.edit().apply {
                        if (
                            language.storedValue ==
                            savedLanguage
                        ) {
                            remove(
                                KEY_PENDING_APP_LANGUAGE
                            )
                        } else {
                            putString(
                                KEY_PENDING_APP_LANGUAGE,
                                language.storedValue
                            )
                        }
                    }.apply()
                },
                onCommitAppLanguage = {
                    val savedLanguage =
                        preferences.getString(
                            KEY_APP_LANGUAGE,
                            AppLanguage.SYSTEM.storedValue
                        ) ?: AppLanguage.SYSTEM.storedValue

                    preferences.edit()
                        .putString(
                            KEY_APP_LANGUAGE,
                            appLanguageValue
                        )
                        .remove(
                            KEY_PENDING_APP_LANGUAGE
                        )
                        .apply()

                    val changed = savedLanguage != appLanguageValue
                    if (changed) {
                        context.findActivity()
                            ?.window
                            ?.decorView
                            ?.post {
                                context.findActivity()?.recreate()
                            }
                    }
                    changed
                },
                onDiscardAppLanguage = {
                    if (
                        preferences.contains(
                            KEY_PENDING_APP_LANGUAGE
                        )
                    ) {
                        appLanguageValue =
                            preferences.getString(
                                KEY_APP_LANGUAGE,
                                AppLanguage.SYSTEM.storedValue
                            ) ?: AppLanguage.SYSTEM.storedValue

                        preferences.edit()
                            .remove(
                                KEY_PENDING_APP_LANGUAGE
                            )
                            .apply()
                    }
                },
                onApplyNumericFormat = { format ->
                    numericFormat = format
                    saveUvirNumericFormat(
                        context,
                        format
                    )
                },
                onApplyDateFormat = { format ->
                    dateFormat = format
                    saveUvirDateFormat(
                        context,
                        format
                    )
                },
                onApplyTimeFormat = { format ->
                    timeFormat = format
                    saveUvirTimeFormat(
                        context,
                        format
                    )
                },
                onApplyExportMode = { mode ->
                    exportMode = mode
                    saveUvirExportMode(
                        context,
                        mode
                    )
                },
                onApplyIrradianceUnit = { unit ->
                    irradianceUnit = unit
                    saveUvirIrradianceUnit(context, unit)
                },

                onApplyAcquisitionParameters = {
                        params ->

                    val accepted = withContext(Dispatchers.IO) {
                        sendSensorControlCommands(
                            buildList {
                                add(sensorSamplingCommand(params))
                                if (
                                    firmwareSupportsSensorSettingsSnapshot(
                                        selectedRuntimeInfo.firmwareVersion
                                    )
                                ) {
                                    add("HELLO")
                                }
                            }
                        )
                    }
                    if (accepted) {
                    samplesPerMeasurement =
                        params.samplesPerMeasurement

                    sampleSpacingMs =
                        params.sampleSpacingMs

                    discardExtremes =
                        params.discardExtremes

                    preferences.edit()
                        .putInt(
                            KEY_SAMPLES_PER_MEASUREMENT,
                            params.samplesPerMeasurement
                        )
                        .putLong(
                            KEY_SAMPLE_SPACING_MS,
                            params.sampleSpacingMs
                        )
                        .putBoolean(
                            KEY_DISCARD_EXTREMES,
                            params.discardExtremes
                        )
                        .apply()

                    }
                    accepted
                },

                thresholdAlertSettings =
                    thresholdAlertSettings,
                thresholdAlertSessionNote =
                    thresholdAlertSessionNote,
                thresholdAlertSessionActive =
                    thresholdAlertSessionId > 0L ||
                        thresholdAlertSettings.hasActiveMonitoring(),
                thresholdAlertViolations =
                    currentThresholdViolations,

                onSaveThresholdAlertPreferences = { settings ->
                    val target = thresholdAlertSettings.copy(
                        repeatSeconds = settings.repeatSeconds,
                        sound = settings.sound,
                        volume = settings.volume
                    )
                    val accepted =
                        !target.firmwareConfigurationDiffersFrom(thresholdAlertSettings) ||
                        withContext(Dispatchers.IO) {
                            sendSensorControlCommands(buildList {
                                addAll(sensorAlertCommands(target, thresholdAlertSessionId))
                                if (firmwareSupportsSensorSettingsSnapshot(selectedRuntimeInfo.firmwareVersion))
                                    add("HELLO")
                            })
                        }
                    if (accepted) {
                        // Keep rules and running-session state changed meanwhile.
                        thresholdAlertSettings = thresholdAlertSettings.copy(
                            repeatSeconds = target.repeatSeconds, sound = target.sound, volume = target.volume)
                        saveThresholdAlertSettings(preferences, thresholdAlertSettings)
                    }
                    accepted
                },

                onApplyThresholdAlertSettings = {
                        settings ->
                    val previousSettings = thresholdAlertSettings
                    val previousSessionId = thresholdAlertSessionId
                    val normalizedSettings =
                        settings.copy(
                            enabled =
                                settings.enabled &&
                                    settings.rules.any { rule ->
                                    rule.enabled
                                }
                        )
                    val needsNewSession =
                        shouldStartNewThresholdAlertSession(
                            previous = previousSettings,
                            next = normalizedSettings,
                            currentSessionId = thresholdAlertSessionId
                        )
                    val nextSessionId =
                        when {
                            !normalizedSettings.hasActiveMonitoring() -> 0L
                            needsNewSession -> database.nextSessionId()
                            else -> thresholdAlertSessionId
                        }
                    if (previousSessionId > 0L && nextSessionId == 0L) {
                        database.finishAlertSession(previousSessionId)
                    }
                    if (needsNewSession && nextSessionId > 0L) {
                        database.startAlertSession(
                            sessionId = nextSessionId,
                            note = "",
                            sensorDeviceId = selectedSensorDeviceId
                        )
                    }
                    thresholdAlertSessionId = nextSessionId
                    thresholdAlertSettings =
                        normalizedSettings
                    saveThresholdAlertSettings(
                        preferences,
                        normalizedSettings
                    )
                    preferences.edit()
                        .putLong(
                            KEY_THRESHOLD_ALERT_SESSION_ID,
                            thresholdAlertSessionId
                        )
                        .apply()
                    if (nextSessionId != previousSessionId ||
                        normalizedSettings.firmwareConfigurationDiffersFrom(previousSettings)) {
                    sensorConnectionScope.launch(Dispatchers.IO) {
                        sendSensorControlCommands(
                            buildList {
                                addAll(
                                    sensorAlertCommands(
                                        settings = normalizedSettings,
                                        sessionId = nextSessionId
                                    )
                                )
                                if (
                                    firmwareSupportsSensorSettingsSnapshot(
                                        selectedRuntimeInfo.firmwareVersion
                                    )
                                ) {
                                    add("HELLO")
                                }
                            }
                        )
                    }
                    }
                },

                onStartThresholdAlertSession = {
                        settings,
                        sessionNote ->
                    val normalizedSessionNote =
                        limitUvirNote(sessionNote).trim()
                    val normalizedSettings =
                        settings.copy(
                            enabled =
                                settings.rules.any { rule -> rule.enabled }
                        )
                    if (normalizedSettings.hasActiveMonitoring()) {
                        val previousSessionId = thresholdAlertSessionId
                        if (previousSessionId > 0L) {
                            database.finishAlertSession(previousSessionId)
                        }
                        val newSessionId = database.nextSessionId()
                        database.startAlertSession(
                            sessionId = newSessionId,
                            note = normalizedSessionNote,
                            sensorDeviceId = selectedSensorDeviceId
                        )
                        thresholdAlertSessionId = newSessionId
                        thresholdAlertSessionNote = normalizedSessionNote
                        thresholdAlertSettings = normalizedSettings
                        saveThresholdAlertSettings(
                            preferences,
                            normalizedSettings
                        )
                        preferences.edit()
                            .putLong(
                                KEY_THRESHOLD_ALERT_SESSION_ID,
                                newSessionId
                            )
                            .putString(
                                KEY_THRESHOLD_ALERT_NOTE,
                                normalizedSessionNote
                            )
                            .commit()
                        sensorConnectionScope.launch(Dispatchers.IO) {
                            sendSensorControlCommands(
                                buildList {
                                    addAll(
                                        sensorAlertCommands(
                                            settings = normalizedSettings,
                                            sessionId = newSessionId
                                        )
                                    )
                                    if (
                                        firmwareSupportsSensorSettingsSnapshot(
                                            selectedRuntimeInfo.firmwareVersion
                                        )
                                    ) {
                                        add("HELLO")
                                    }
                                }
                            )
                        }
                    }
                },

                onPreviewThresholdAlertSound = {
                        sound,
                        volume ->
                    previewThresholdAlertSound(
                        sound,
                        volume
                    )
                },
                acquisitionFeedbackSettings =
                    acquisitionFeedbackSettings,
                onAcquisitionFeedbackSettingsChange = { settings ->
                    if (settings != acquisitionFeedbackSettings) {
                        acquisitionFeedbackSettings = settings
                        saveAcquisitionFeedbackSettings(preferences, settings)
                        showUvirBottomMessage(
                            context,
                            resources.getString(R.string.parameters_saved)
                        )
                    }
                },
                onPreviewAcquisitionFeedback = { sound, volume ->
                    thresholdPreviewJob?.cancel()
                    thresholdPreviewJob = thresholdPreviewScope.launch {
                        playThresholdAlertTone(
                            context = context,
                            sound = sound,
                            volume = volume,
                            vibrationPulses = 1
                        )
                    }
                },

                autoEnabled =
                    autoEnabled,
                automaticStopInProgress =
                    automaticStopInProgress,
                autoIntervalSeconds =
                    autoIntervalSeconds,
                autoNote =
                    autoNote,
                autoUseStartDelay =
                    autoUseStartDelay,
                autoStartDelaySeconds =
                    autoStartDelaySeconds,
                autoUseDuration =
                    autoUseDuration,
                autoDurationSeconds =
                    autoDurationSeconds,
                autoLimitEnabled =
                    autoLimitEnabled,
                autoMaxCount =
                    autoMaxCount,
                autoCompletedCount =
                    autoCompletedCount,
                autoNextSaveMs =
                    autoNextSaveMs,

                autoConditionalPlan = autoConditionalPlan,
                autoConditionalWaiting = autoConditionalWaiting,
                autoExternalCommand = autoExternalCommand,
                autoConditionalEnabled = autoConditionalEnabled,
                autoConditionalMatch = autoConditionalMatch,
                autoConditionalAction = autoConditionalAction,
                autoConditionalRules = autoConditionalRules,
                onSaveAcquisitionConditions = { rules ->
                    if (saveAcquisitionConditions(preferences, rules)) {
                        autoConditionalRules = loadAcquisitionConditions(preferences)
                    }
                },
                onStartAutomaticAcquisition = {
                        request ->
                    startAutomaticAcquisition(
                        request
                    )
                },

                onStopAutomaticAcquisition = {
                    stopAutomaticAcquisition()
                },

                onResetAllCounters = {
                    if (!autoEnabled) {
                        database.resetAllCounters()
                        clearUnreadAcquisitions()
                        clearUnreadAlerts()
                        autoSessionId = 0L
                        autoCompletedCount = 0
                        selectedRecordId = null
                        thresholdAlertSessionId =
                            if (
                                thresholdAlertSettings
                                    .hasActiveMonitoring()
                            ) {
                                database.nextSessionId()
                            } else {
                                0L
                            }
                        preferences.edit()
                            .putLong(
                                KEY_AUTO_SESSION_ID,
                                0L
                            )
                            .putInt(
                                KEY_AUTO_COMPLETED_COUNT,
                                0
                            )
                            .putLong(
                                KEY_THRESHOLD_ALERT_SESSION_ID,
                                thresholdAlertSessionId
                            )
                            .apply()
                    }
                },

                onAcquisitionSaved = {
                    incrementUnreadAcquisitions()
                    playAcquisitionFeedback()
                    sensorConnectionScope.launch(Dispatchers.IO) {
                        sendSensorControlCommands(
                            listOf("LED_EVENT ACQUISITION")
                        )
                    }
                },

                backgroundColor =
                    backgroundColor,
                cardColor =
                    cardColor,
                primaryText =
                    primaryText,
                secondaryText =
                    secondaryText,
                trackColor =
                    trackColor,

                unreadAcquisitionCount =
                    unreadAcquisitionCount,
                unreadAlertCount =
                    unreadAlertCount,
                sensorSyncInProgress =
                    sensorSyncInProgress,
                acquisitionSyncInProgress =
                    acquisitionSyncInProgress,
                alertSyncInProgress =
                    alertSyncInProgress,
                onAlertLogViewed = {
                    clearUnreadAlerts()
                },

                onAlertLogVisibilityChanged = { visible ->
                    alertLogVisible = visible
                    if (visible) {
                        clearUnreadAlerts()
                    }
                },

                offlineDisconnectionNotice =
                    offlineDisconnectionNotice,

                onOpenHistory = {
                    clearUnreadAcquisitions()
                    screen =
                        AppScreen.HISTORY
                }
            )
            }
        }

        AppScreen.HISTORY -> {

            HistoryScreen(
                database = database,
                historyListState =
                    historyListState,
                historyScrollAnchor =
                    historyScrollAnchor,
                onHistoryScrollAnchorChange = {
                    historyScrollAnchor = it
                },
                backgroundColor =
                    backgroundColor,
                cardColor =
                    cardColor,
                primaryText =
                    primaryText,
                secondaryText =
                    secondaryText,

                onBack = {
                    screen =
                        AppScreen.LIVE
                },

                onOpenSessionChart = { sessionId ->
                    selectedSessionId = sessionId
                    screen =
                        AppScreen.SESSION_CHART
                },

                onOpenRecord = { record ->

                    selectedRecordId =
                        record.id

                    detailReturnScreen =
                        AppScreen.HISTORY

                    screen =
                        AppScreen.DETAIL
                }
            )
        }

        AppScreen.SESSION_CHART -> {
            val sessionRecords =
                remember(
                    selectedSessionId,
                    database
                ) {
                    selectedSessionId?.let {
                        database.readSessionRecords(it)
                    }.orEmpty()
                }

            if (
                selectedSessionId != null &&
                sessionRecords.isNotEmpty()
            ) {
                SessionChartScreen(
                    sessionId =
                        requireNotNull(
                            selectedSessionId
                        ),
                    records = sessionRecords,
                    database = database,
                    backgroundColor = backgroundColor,
                    cardColor = cardColor,
                    primaryText = primaryText,
                    secondaryText = secondaryText,
                    onDeleteSession = {
                        database.deleteRecords(
                            sessionRecords.map { it.id }
                        )
                        selectedSessionId = null
                        screen = AppScreen.HISTORY
                    },
                    onBack = {
                        screen =
                            AppScreen.HISTORY
                    }
                )
            } else {
                selectedSessionId = null
                screen =
                    AppScreen.HISTORY
            }
        }

        AppScreen.ACQUISITION_CHART -> {
            val record =
                remember(
                    selectedRecordId,
                    database
                ) {
                    selectedRecordId?.let {
                        database.readRecord(it)
                    }
                }

            if (record != null) {
                AcquisitionChartScreen(
                    record = record,
                    backgroundColor = backgroundColor,
                    cardColor = cardColor,
                    primaryText = primaryText,
                    secondaryText = secondaryText,
                    onBack = {
                        screen = AppScreen.DETAIL
                    }
                )
            } else {
                selectedRecordId = null
                screen = AppScreen.HISTORY
            }
        }

        AppScreen.DETAIL -> {

            val record =
                remember(
                    selectedRecordId,
                    database
                ) {
                    selectedRecordId?.let {
                        database.readRecord(it)
                    }
                }

            if (record != null) {

                RecordDetailScreen(
                    record = record,
                    database = database,
                    detailListState =
                        detailListState,
                    backgroundColor =
                        backgroundColor,
                    cardColor =
                        cardColor,
                    primaryText =
                        primaryText,
                    secondaryText =
                        secondaryText,
                    trackColor =
                        trackColor,

                    onBack = {
                        screen =
                            detailReturnScreen
                    },

                    onDeleted = {
                        selectedRecordId =
                            null

                        screen =
                            detailReturnScreen
                    }
                )

            } else {

                screen =
                    AppScreen.HISTORY
            }
        }
    }

    sensorSyncSummary?.let { summary ->
        SensorSyncCompleteDialog(
            summary = summary,
            cardColor = cardColor,
            primaryText = primaryText,
            secondaryText = secondaryText,
            onDismiss = {
                sensorSyncSummary = null
            }
        )
    }

    sensorSyncIncompleteSummary?.let { summary ->
        SensorSyncIncompleteDialog(
            summary = summary,
            cardColor = cardColor,
            primaryText = primaryText,
            secondaryText = secondaryText,
            onDismiss = {
                sensorSyncIncompleteSummary = null
            }
        )
    }

    }
}
