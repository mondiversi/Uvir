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
import android.provider.DocumentsContract
import android.text.TextUtils
import android.view.View
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
import androidx.compose.ui.platform.LocalLayoutDirection
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
import androidx.compose.ui.unit.LayoutDirection
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

internal const val AUTOMATIC_NOTIFICATION_CHANNEL_ID =
    "uvir_automatic_acquisition"
internal const val AUTOMATIC_NOTIFICATION_ID = 1101
internal const val THRESHOLD_ALERT_NOTIFICATION_CHANNEL_ID =
    "uvir_threshold_alerts"
internal const val THRESHOLD_ALERT_NOTIFICATION_ID = 1102
internal const val EXTRA_OPEN_HOME =
    "me.mondiversi.uvir.OPEN_HOME"

@Volatile
private var runtimeActionsInitializedForProcess = false

@Volatile
private var baseContextAttachedForProcess = false

private fun localizedAppContext(
    baseContext: Context
): Context {
    val preferences =
        baseContext.getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE
        )

    val firstAttachmentInProcess =
        synchronized(AppLanguage::class.java) {
            if (baseContextAttachedForProcess) {
                false
            } else {
                baseContextAttachedForProcess = true
                true
            }
        }

    if (firstAttachmentInProcess) {
        preferences.edit()
            .remove(KEY_PENDING_APP_LANGUAGE)
            .commit()
    }

    val language =
        AppLanguage.fromStoredValue(
            preferences.getString(
                KEY_PENDING_APP_LANGUAGE,
                null
            ) ?: preferences.getString(
                    KEY_APP_LANGUAGE,
                    AppLanguage.SYSTEM.storedValue
                )
        )

    if (language == AppLanguage.SYSTEM) {
        return baseContext
    }

    val configuration =
        Configuration(
            baseContext.resources.configuration
        ).apply {
            val locale =
                Locale.forLanguageTag(
                    language.languageTag
                )
            setLocale(locale)
            setLayoutDirection(locale)
        }

    return baseContext.createConfigurationContext(
        configuration
    )
}

private fun configuredAppLayoutDirection(
    context: Context
): LayoutDirection {
    val preferences =
        context.getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE
        )
    val language =
        AppLanguage.fromStoredValue(
            preferences.getString(
                KEY_APP_LANGUAGE,
                AppLanguage.SYSTEM.storedValue
            )
        )
    val isRtl =
        if (language == AppLanguage.SYSTEM) {
            context.resources.configuration.layoutDirection ==
                View.LAYOUT_DIRECTION_RTL
        } else {
            TextUtils.getLayoutDirectionFromLocale(
                Locale.forLanguageTag(language.languageTag)
            ) == View.LAYOUT_DIRECTION_RTL
        }

    return if (isRtl) {
        LayoutDirection.Rtl
    } else {
        LayoutDirection.Ltr
    }
}

private fun terminateRuntimeActions(
    context: Context
) {
    val preferences =
        context
        .getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE
        )

    val automaticWasActive =
        preferences.getBoolean(
            KEY_AUTO_ENABLED,
            false
        )
    val configuredAlertRulesPresent =
        ThresholdAlertMetric.entries.any { metric ->
            preferences.getBoolean(
                thresholdRulePreferenceKey(
                    metric,
                    "enabled"
                ),
                false
            )
        }
    val alertsWereActive =
        preferences.getBoolean(
            KEY_THRESHOLD_ALERT_ENABLED,
            false
        ) && configuredAlertRulesPresent

    val editor = preferences.edit()

    if (automaticWasActive || alertsWereActive) {
        editor
            .putBoolean(
                KEY_OFFLINE_REOPEN_NOTICE_PENDING,
                true
            )
            .putBoolean(
                KEY_OFFLINE_REOPEN_AUTO_ACTIVE,
                automaticWasActive
            )
            .putBoolean(
                KEY_OFFLINE_REOPEN_ALERTS_ACTIVE,
                alertsWereActive
            )
            .putLong(
                KEY_OFFLINE_REOPEN_NEXT_SAVE_MS,
                preferences.getLong(
                    KEY_AUTO_NEXT_SAVE_MS,
                    0L
                )
            )
            .putLong(
                KEY_OFFLINE_REOPEN_END_MS,
                preferences.getLong(
                    KEY_AUTO_END_MS,
                    0L
                )
            )
            .putInt(
                KEY_OFFLINE_REOPEN_COMPLETED_COUNT,
                preferences.getInt(
                    KEY_AUTO_COMPLETED_COUNT,
                    0
                )
            )
    }

    // Automatic acquisition is owned by the sensor once it has been started.
    // Keep its persisted identity and schedule when Android closes the UI so
    // the app can reconcile the autonomous job on the next connection.
    editor.remove(KEY_PENDING_APP_LANGUAGE)

    // Configured thresholds and the current alert-session identity are user
    // data, not transient Activity state. Keep both across process restarts:
    // configured bells must remain visible even before monitoring is started,
    // while an already-started sensor session must be reconciled rather than
    // silently replaced by a new one.

    editor.apply()

    cancelAutomaticAcquisitionNotification(context)
    cancelThresholdAlertNotification(context)
}

private fun clearStaleRuntimeActionsOnProcessStart(
    context: Context
) {
    if (runtimeActionsInitializedForProcess) {
        return
    }

    synchronized(MainActivity::class.java) {
        if (!runtimeActionsInitializedForProcess) {
            terminateRuntimeActions(
                context.applicationContext
            )
            runtimeActionsInitializedForProcess = true
        }
    }
}

/**
 * Owns transports that must survive a recreation of MainActivity caused by
 * locale or appearance changes. Every manager uses the application context,
 * so retaining this object never retains the old Activity.
 */
private class UvirRetainedRuntime(
    context: Context
) {
    val remoteServer =
        UvirRemoteServer(context.applicationContext)

    val usbSensorManager =
        UvirUsbSensorManager(context.applicationContext)

    val wirelessSensorManager =
        UvirWirelessSensorManager(context.applicationContext)

    private var started = false
    private var directNetworkEnabled: Boolean? = null

    @Synchronized
    fun startIfNeeded(
        initialIntent: Intent?,
        directNetwork: Boolean
    ) {
        if (!started) {
            usbSensorManager.start(initialIntent)
            remoteServer.start(directNetwork)
            directNetworkEnabled = directNetwork
            started = true
        } else if (directNetworkEnabled != directNetwork) {
            remoteServer.start(directNetwork)
            directNetworkEnabled = directNetwork
        }
    }

    @Synchronized
    fun stop() {
        if (!started) return

        remoteServer.stop()
        usbSensorManager.stop()
        wirelessSensorManager.stop()
        started = false
        directNetworkEnabled = null
    }
}

private object UvirRetainedRuntimeStore {
    private var runtime: UvirRetainedRuntime? = null

    @Synchronized
    fun acquire(context: Context): UvirRetainedRuntime =
        runtime ?: UvirRetainedRuntime(context).also {
            runtime = it
        }

    @Synchronized
    fun release(runtimeToRelease: UvirRetainedRuntime) {
        if (runtime === runtimeToRelease) {
            runtimeToRelease.stop()
            runtime = null
        }
    }
}

class MainActivity : ComponentActivity(), UvirExportSaveHost, UvirSettingsImportHost {
    private lateinit var retainedRuntime:
            UvirRetainedRuntime

    private lateinit var remoteServer:
            UvirRemoteServer

    private lateinit var usbSensorManager:
            UvirUsbSensorManager

    private lateinit var wirelessSensorManager:
            UvirWirelessSensorManager

    private val remoteNetworkEnabledState =
        mutableStateOf(false)

    private val currentWifiSsidState =
        mutableStateOf<String?>(null)

    private val openHomeRequestState =
        mutableLongStateOf(0L)

    private val pendingSettingsImportUrisState =
        mutableStateOf<List<Uri>>(emptyList())

    private val settingsImportPasswordRejectedState =
        mutableStateOf(false)

    private var pendingExportFiles: List<File> = emptyList()

    private val exportDirectoryLauncher =
        registerForActivityResult(
            ActivityResultContracts.OpenDocumentTree()
        ) { treeUri ->
            val files = pendingExportFiles
            pendingExportFiles = emptyList()
            if (treeUri == null || files.isEmpty()) {
                return@registerForActivityResult
            }

            runCatching {
                val resolver = contentResolver
                val parentUri =
                    DocumentsContract.buildDocumentUriUsingTree(
                        treeUri,
                        DocumentsContract.getTreeDocumentId(treeUri)
                    )
                files.forEach { file ->
                    val targetUri =
                        checkNotNull(
                            DocumentsContract.createDocument(
                                resolver,
                                parentUri,
                                uvirExportMimeType(file),
                                file.name
                            )
                        )
                    resolver.openOutputStream(targetUri, "w").use { output ->
                        checkNotNull(output)
                        file.inputStream().use { input ->
                            input.copyTo(output)
                        }
                    }
                }
            }.onSuccess {
                showUvirBottomMessage(
                    this,
                    getString(R.string.export_saved_successfully)
                )
            }.onFailure { error ->
                UvirErrorLog.record(this, "save_export", error)
                showUvirBottomMessage(
                    this,
                    getString(R.string.export_save_failed)
                )
            }
        }

    private val settingsImportLauncher =
        registerForActivityResult(
            ActivityResultContracts.OpenMultipleDocuments()
        ) { uris ->
            if (uris.isEmpty()) {
                return@registerForActivityResult
            }
            if (uvirSettingsFilesRequirePassword(this, uris)) {
                settingsImportPasswordRejectedState.value = false
                pendingSettingsImportUrisState.value = uris
            } else {
                importSelectedUvirSettings(uris)
            }
        }

    private fun importSelectedUvirSettings(
        uris: List<Uri>,
        decryptionPassword: CharArray? = null
    ) {
        try {
            runCatching {
                UvirDatabaseHelper(this).use { database ->
                    importUvirSettingsFiles(
                        this,
                        database,
                        uris,
                        decryptionPassword
                    )
                }
            }.onSuccess { count ->
                pendingSettingsImportUrisState.value = emptyList()
                settingsImportPasswordRejectedState.value = false
                showUvirBottomMessage(
                    this,
                    resources.getQuantityString(
                        R.plurals.settings_imported_count,
                        count,
                        count
                    )
                )
                recreate()
            }.onFailure { error ->
                if (
                    error is UvirSettingsDecryptionException &&
                    pendingSettingsImportUrisState.value.isNotEmpty()
                ) {
                    settingsImportPasswordRejectedState.value = true
                } else {
                    pendingSettingsImportUrisState.value = emptyList()
                    settingsImportPasswordRejectedState.value = false
                    UvirErrorLog.record(this, "import_settings", error)
                    showUvirBottomMessage(
                        this,
                        getString(R.string.settings_import_failed)
                    )
                }
            }
        } finally {
            decryptionPassword?.fill('\u0000')
        }
    }

    override fun saveUvirExportFiles(files: List<File>) {
        pendingExportFiles = files.toList()
        exportDirectoryLauncher.launch(null)
    }

    override fun selectUvirSettingsFiles() {
        settingsImportLauncher.launch(arrayOf("application/json", "application/octet-stream"))
    }

    private val localNetworkPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts
                .RequestPermission()
        ) { granted ->
            applyRemoteNetworkEnabled(
                granted
            )

            if (!granted) {
                showUvirBottomMessage(
                    this,
                    getString(R.string.remote_permission_denied)
                )
            }
        }

    private val bluetoothPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) {
            if (::wirelessSensorManager.isInitialized) {
                wirelessSensorManager.retry()
            }
        }

    private val wifiNamePermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { grants ->
            if (grants.values.any { it }) {
                refreshCurrentWifiSsid()
            }
        }

    override fun attachBaseContext(
        newBase: Context
    ) {
        super.attachBaseContext(
            localizedAppContext(newBase)
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        consumeOpenHomeRequest(intent)

        UvirErrorLog.install(
            applicationContext
        )

        clearStaleRuntimeActionsOnProcessStart(
            applicationContext
        )

        retainedRuntime =
            UvirRetainedRuntimeStore.acquire(
                applicationContext
            )

        remoteServer = retainedRuntime.remoteServer
        usbSensorManager = retainedRuntime.usbSensorManager
        wirelessSensorManager = retainedRuntime.wirelessSensorManager

        val directAccessAllowed =
            hasLocalNetworkPermission()

        remoteNetworkEnabledState.value =
            directAccessAllowed

        retainedRuntime.startIfNeeded(
            initialIntent = intent,
            directNetwork = directAccessAllowed
        )

        val appLayoutDirection =
            configuredAppLayoutDirection(this)

        setContent {
            UvirActionTheme {
            CompositionLocalProvider(
                LocalLayoutDirection provides appLayoutDirection
            ) {
                UvirLaunchGate(
                    showLaunchScreen = savedInstanceState == null
                ) {
                    UvirApp(
                        remoteNetworkEnabled =
                            remoteNetworkEnabledState.value,
                        usbSensorManager =
                            usbSensorManager,
                        wirelessSensorManager =
                            wirelessSensorManager,
                        onRequestBluetoothPermission =
                            ::requestBluetoothSensorPermission,
                        currentWifiSsid =
                            currentWifiSsidState.value,
                        onRequestCurrentWifiSsid =
                            ::requestCurrentWifiSsid,
                        openHomeRequestId =
                            openHomeRequestState.longValue
                    )
                }
                val pendingSettingsImportUris =
                    pendingSettingsImportUrisState.value
                if (pendingSettingsImportUris.isNotEmpty()) {
                    UvirSettingsPasswordDialog(
                        title =
                            stringResource(
                                R.string.settings_decryption_password_title
                            ),
                        description =
                            stringResource(
                                R.string.settings_decryption_password_description
                            ),
                        confirmationRequired = false,
                        confirmLabel =
                            stringResource(R.string.settings_decrypt),
                        cardColor = MaterialTheme.colorScheme.surface,
                        primaryText = MaterialTheme.colorScheme.onSurface,
                        secondaryText =
                            MaterialTheme.colorScheme.onSurfaceVariant,
                        errorMessage =
                            if (settingsImportPasswordRejectedState.value) {
                                stringResource(
                                    R.string.settings_decryption_password_incorrect
                                )
                            } else {
                                null
                            },
                        onInputChanged = {
                            settingsImportPasswordRejectedState.value = false
                        },
                        onDismiss = {
                            pendingSettingsImportUrisState.value = emptyList()
                            settingsImportPasswordRejectedState.value = false
                        },
                        onConfirm = { password ->
                            importSelectedUvirSettings(
                                pendingSettingsImportUris,
                                password
                            )
                        }
                    )
                }
            }
            }
        }

        if (!directAccessAllowed) {
            localNetworkPermissionLauncher.launch(
                Manifest.permission
                    .ACCESS_LOCAL_NETWORK
            )
        }
    }

    override fun onDestroy() {
        if (!isChangingConfigurations) {
            if (::retainedRuntime.isInitialized) {
                UvirRetainedRuntimeStore.release(
                    retainedRuntime
                )
            }

            terminateRuntimeActions(
                applicationContext
            )
        }

        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()

        if (hasWifiNamePermission()) {
            refreshCurrentWifiSsid()
        }

        if (::wirelessSensorManager.isInitialized) {
            wirelessSensorManager.retryIfNeeded()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)

        if (::usbSensorManager.isInitialized) {
            usbSensorManager.handleIntent(intent)
        }

        if (intent.getBooleanExtra(EXTRA_OPEN_HOME, false)) {
            consumeOpenHomeRequest(intent)
        }
    }

    private fun consumeOpenHomeRequest(intent: Intent) {
        if (!intent.getBooleanExtra(EXTRA_OPEN_HOME, false)) {
            return
        }

        intent.removeExtra(EXTRA_OPEN_HOME)
        setIntent(intent)
        openHomeRequestState.longValue += 1L
    }

    private fun hasLocalNetworkPermission():
            Boolean {
        return Build.VERSION.SDK_INT < 37 ||
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission
                        .ACCESS_LOCAL_NETWORK
                ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestBluetoothSensorPermission() {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            bluetoothPermissionLauncher.launch(
                Manifest.permission.BLUETOOTH_CONNECT
            )
        } else if (::wirelessSensorManager.isInitialized) {
            wirelessSensorManager.retry()
        }
    }

    private fun requestCurrentWifiSsid() {
        if (hasWifiNamePermission()) {
            refreshCurrentWifiSsid()
            return
        }

        val permissions =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                arrayOf(
                    Manifest.permission.NEARBY_WIFI_DEVICES
                )
            } else {
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            }

        wifiNamePermissionLauncher.launch(permissions)
    }

    private fun hasWifiNamePermission(): Boolean {
        return if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        ) {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.NEARBY_WIFI_DEVICES
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED
        }
    }

    @Suppress("DEPRECATION")
    private fun refreshCurrentWifiSsid() {
        val wifiManager =
            applicationContext.getSystemService(
                Context.WIFI_SERVICE
            ) as WifiManager

        val connectivityManager =
            getSystemService(
                Context.CONNECTIVITY_SERVICE
            ) as ConnectivityManager

        val activeNetwork = connectivityManager.activeNetwork
        val capabilities =
            activeNetwork?.let {
                connectivityManager.getNetworkCapabilities(it)
            }

        if (
            capabilities?.hasTransport(
                NetworkCapabilities.TRANSPORT_WIFI
            ) != true
        ) {
            currentWifiSsidState.value = null
            return
        }

        val rawSsid =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                (capabilities.transportInfo as? WifiInfo)?.ssid
                    ?: wifiManager.connectionInfo?.ssid
            } else {
                wifiManager.connectionInfo?.ssid
            }

        currentWifiSsidState.value =
            rawSsid
                ?.removeSurrounding("\"")
                ?.takeUnless {
                    it.isBlank() ||
                            it == WifiManager.UNKNOWN_SSID
                }
    }

    private fun applyRemoteNetworkEnabled(
        enabled: Boolean
    ) {
        remoteNetworkEnabledState.value =
            enabled

        if (::remoteServer.isInitialized) {
            remoteServer.start(
                directNetwork = enabled
            )
        }
    }
}
