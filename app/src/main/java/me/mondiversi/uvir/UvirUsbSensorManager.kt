package me.mondiversi.uvir

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import com.hoho.android.usbserial.util.SerialInputOutputManager
import java.nio.charset.StandardCharsets
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

enum class UsbSensorConnectionStatus {
    DISCONNECTED,
    WAITING_PERMISSION,
    CONNECTING,
    CONNECTED,
    ERROR
}

internal const val USB_SENSOR_IDENTITY_MISMATCH_ERROR =
    "sensor_identity_mismatch"

data class UvirUsbSensorState(
    val status: UsbSensorConnectionStatus =
        UsbSensorConnectionStatus.DISCONNECTED,
    val attached: Boolean = false,
    val deviceName: String? = null,
    val deviceId: String? = null,
    val sensorName: String? = null,
    val firmwareVersion: String? = null,
    val appConnectionConfirmed: Boolean = false,
    val runtimeInfo: UvirSensorRuntimeInfo = UvirSensorRuntimeInfo(),
    val calibrationKind: String = "datasheet_estimate",
    val credentials: UvirSensorCredentials = UvirSensorCredentials(),
    val sample: SensorSample? = null,
    val sampleSequence: Long = 0L,
    val alertEvent: SensorLiveAlertEvent? = null,
    val debugPerformanceCompletionSequence: Long = 0L,
    val calibrationProvisional: Boolean = true,
    val uvAvailable: Boolean = false,
    val saturated: Boolean = false,
    val error: String? = null
)

/**
 * Owns the Android USB-host connection to the ESP32 serial bridge.
 *
 * The sensor protocol is deliberately line-based and versioned so malformed
 * serial data or an unrelated CP210x device cannot be interpreted as a Uvir
 * measurement before it sends a valid hello frame.
 */
class UvirUsbSensorManager(
    context: Context
) : SerialInputOutputManager.Listener {

    private val applicationContext =
        context.applicationContext

    private val usbManager =
        applicationContext.getSystemService(
            Context.USB_SERVICE
        ) as UsbManager

    private val executor:
        ScheduledExecutorService =
        Executors.newSingleThreadScheduledExecutor()

    private val mutableState =
        MutableStateFlow(UvirUsbSensorState())

    val state: StateFlow<UvirUsbSensorState> =
        mutableState.asStateFlow()

    private val lineBuffer = StringBuilder()
    private val serialGeneration = AtomicLong(0L)
    private val connectionLock = Any()
    private val commandWriteLock = Any()
    private val diagnosticClient = UvirSensorDiagnosticClient()
    private val alertReceiptSequence = AtomicLong(0L)
    private val debugPerformanceCompletionCounter = AtomicLong(0L)

    @Volatile
    private var expectedRadioSettings: SensorRadioSettings? = null

    @Volatile
    private var radioSettingsAcknowledgement: CountDownLatch? = null

    @Volatile
    private var wifiConfigurationAcknowledgement: CountDownLatch? = null

    @Volatile
    private var internetConfigurationAcknowledgement: CountDownLatch? = null

    @Volatile
    private var automaticStopAcknowledgement: CountDownLatch? = null

    @Volatile
    private var automaticStopConfirmed = false

    @Volatile
    private var statusTestAcknowledgement: CountDownLatch? = null

    @Volatile
    private var statusTestExpectedField: String? = null

    @Volatile
    private var statusTestConfirmed = false

    @Volatile
    private var desiredStreaming = false

    @Volatile
    private var desiredIntervalMs = DEFAULT_SAMPLE_SPACING_MS

    @Volatile
    private var desiredWirelessMode: SensorConnectionMode? = null

    @Volatile
    private var protocolAccepted = false

    @Volatile
    private var offlineSyncRequested = false

    @Volatile
    private var offlineSyncCompleted = false

    @Volatile
    private var offlineSyncLastProgressAtMs = 0L

    private var receiverRegistered = false
    private var currentDeviceId: Int? = null
    private var deviceConnection:
        UsbDeviceConnection? = null
    private var serialPort:
        UsbSerialPort? = null
    private var ioManager:
        SerialInputOutputManager? = null

    private val permissionReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(
                context: Context,
                intent: Intent
            ) {
                if (intent.action != ACTION_USB_PERMISSION) {
                    return
                }

                val device =
                    intent.usbDeviceExtra()
                        ?: return

                if (
                    intent.getBooleanExtra(
                        UsbManager.EXTRA_PERMISSION_GRANTED,
                        false
                    )
                ) {
                    // A delayed permission result must not replace a newer USB
                    // candidate that has become current in the meantime.
                    if (device.deviceId != currentDeviceId) {
                        return
                    }
                    openDevice(device)
                } else {
                    mutableState.value =
                        UvirUsbSensorState(
                            status =
                                UsbSensorConnectionStatus.ERROR,
                            attached = true,
                            deviceName = device.productName,
                            error = "usb_permission_denied"
                        )
                }
            }
        }

    private val deviceReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(
                context: Context,
                intent: Intent
            ) {
                val device =
                    intent.usbDeviceExtra()
                        ?: return

                when (intent.action) {
                    UsbManager.ACTION_USB_DEVICE_ATTACHED ->
                        connectOrRequestPermission(device)

                    UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                        if (device.deviceId == currentDeviceId) {
                            executor.execute {
                                closeConnection(
                                    error = null,
                                    attached = false
                                )
                            }
                        }
                    }
                }
            }
        }

    fun start(initialIntent: Intent?) {
        registerReceivers()

        executor.scheduleAtFixedRate(
            {
                if (
                    mutableState.value.status ==
                    UsbSensorConnectionStatus.CONNECTED
                ) {
                    writeCommand("PING")
                    val now = SystemClock.elapsedRealtime()
                    if (
                        desiredStreaming &&
                        offlineSyncRequested &&
                        !offlineSyncCompleted &&
                        now - offlineSyncLastProgressAtMs >=
                            OFFLINE_SYNC_RETRY_TIMEOUT_MS
                    ) {
                        offlineSyncLastProgressAtMs = now
                        Log.w("UvirSync", "USB sync stalled; retrying")
                        writeCommand("SYNC_BEGIN")
                    }
                }
            },
            4L,
            4L,
            TimeUnit.SECONDS
        )

        val attachedDevice =
            if (
                initialIntent?.action ==
                UsbManager.ACTION_USB_DEVICE_ATTACHED
            ) {
                initialIntent.usbDeviceExtra()
            } else {
                null
            }

        if (attachedDevice != null) {
            connectOrRequestPermission(attachedDevice)
        } else {
            scanForSensor()
        }
    }

    fun handleIntent(intent: Intent) {
        if (
            intent.action ==
            UsbManager.ACTION_USB_DEVICE_ATTACHED
        ) {
            intent.usbDeviceExtra()?.let {
                connectOrRequestPermission(it)
            }
        }
    }

    fun scanForSensor() {
        val driver =
            UsbSerialProber
                .getDefaultProber()
                .findAllDrivers(usbManager)
                .firstOrNull {
                    it.device.vendorId == CP210X_VENDOR_ID &&
                            it.device.productId ==
                            CP210X_PRODUCT_ID
                }
                ?: return

        connectOrRequestPermission(driver.device)
    }

    fun setStreaming(
        enabled: Boolean,
        intervalMs: Long
    ) {
        if (enabled && !desiredStreaming) {
            resetOfflineSyncState()
        }
        desiredStreaming = enabled
        desiredIntervalMs =
            intervalMs.coerceIn(
                MINIMUM_STREAM_INTERVAL_MS,
                MAXIMUM_STREAM_INTERVAL_MS
            )

        executor.execute {
            if (
                usbTransportReady()
            ) {
                if (desiredStreaming) {
                    writeCommand(
                        "STREAM $desiredIntervalMs"
                    )
                } else {
                    writeCommand("STOP")
                }
            }
        }
    }

    /**
     * Selects the radio that the ESP32 should keep available after USB is
     * disconnected. Choosing USB as the data source deliberately preserves
     * the last wireless radio: otherwise removing the cable would leave no
     * channel through which the app could re-enable Wi-Fi or Bluetooth.
     */
    fun configureWirelessMode(mode: SensorConnectionMode) {
        desiredWirelessMode =
            mode.takeIf {
                it != SensorConnectionMode.USB
            }

        if (mode == SensorConnectionMode.USB) {
            return
        }

        val command = when (mode) {
            SensorConnectionMode.USB -> return
            SensorConnectionMode.WIFI -> "WIRELESS WIFI"
            SensorConnectionMode.BLUETOOTH -> "WIRELESS BLUETOOTH"
            SensorConnectionMode.INTERNET -> "WIRELESS INTERNET"
        }

        executor.execute {
            if (
                usbTransportReady()
            ) {
                writeCommand(command)
            }
        }
    }

    /** Applies the complete persisted radio selection over trusted USB. */
    @Synchronized
    fun configureWirelessTransportsEnabled(
        target: SensorRadioSettings
    ): Boolean {
        if (!usbTransportReady()) {
            return false
        }

        val credentials = mutableState.value.credentials
        var current =
            SensorRadioSettings(
                wifiEnabled = credentials.wifiEnabled,
                bluetoothEnabled = credentials.bluetoothEnabled
            )
        val changes =
            orderedSensorRadioChanges(
                current = current,
                target = target,
                activeMode = SensorConnectionMode.USB
            )

        for (change in changes) {
            val expected =
                current.withMode(change.mode, change.enabled)
            val acknowledgement = CountDownLatch(1)
            expectedRadioSettings = expected
            radioSettingsAcknowledgement = acknowledgement

            val transportName =
                when (change.mode) {
                    SensorConnectionMode.WIFI -> "WIFI"
                    SensorConnectionMode.BLUETOOTH -> "BLUETOOTH"
                    SensorConnectionMode.INTERNET -> return false
                    SensorConnectionMode.USB -> return false
                }
            val commandWritten =
                writeCommand(
                    "RADIO $transportName " +
                        if (change.enabled) "ON" else "OFF"
                )
            val acknowledged =
                commandWritten &&
                    runCatching {
                        acknowledgement.await(
                            RADIO_SETTINGS_ACK_TIMEOUT_MS,
                            TimeUnit.MILLISECONDS
                        )
                    }.getOrDefault(false)

            if (radioSettingsAcknowledgement === acknowledgement) {
                radioSettingsAcknowledgement = null
                expectedRadioSettings = null
            }
            if (!acknowledged) {
                return false
            }
            current = expected
        }

        return true
    }

    /** Stores Internet relay settings over the trusted USB control link. */
    @Synchronized
    internal fun configureInternet(
        configuration: SensorInternetConfiguration
    ): Boolean {
        if (!configuration.isComplete || !usbTransportReady()) return false
        val current = UvirSensorCredentialStore.load(applicationContext)
        if (!current.isProvisioned) return false

        val acknowledgement = CountDownLatch(1)
        internetConfigurationAcknowledgement = acknowledgement
        val acknowledged =
            try {
                writeCommand(configuration.protocolCommand) &&
                    acknowledgement.await(
                        INTERNET_CONFIGURATION_ACK_TIMEOUT_MS,
                        TimeUnit.MILLISECONDS
                    )
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                false
            } finally {
                if (internetConfigurationAcknowledgement === acknowledgement) {
                    internetConfigurationAcknowledgement = null
                }
            }

        if (!acknowledged) return false
        val updated = current.withInternetConfiguration(configuration)
        UvirSensorCredentialStore.save(applicationContext, updated)
        mutableState.value = mutableState.value.copy(credentials = updated)
        return true
    }

    /** Provisions Wi-Fi through the currently connected trusted USB link. */
    @Synchronized
    fun configureWifiNetwork(
        ssid: String,
        password: String
    ): Boolean {
        val configuration =
            validatedSensorWifiConfiguration(ssid, password)
                ?: return false
        if (!usbTransportReady()) {
            return false
        }

        val current = UvirSensorCredentialStore.load(applicationContext)
        if (!current.isProvisioned) {
            return false
        }

        val acknowledgement = CountDownLatch(1)
        wifiConfigurationAcknowledgement = acknowledgement
        val acknowledged =
            try {
                writeCommand(configuration.protocolCommand) &&
                    acknowledgement.await(
                        WIFI_CONFIGURATION_ACK_TIMEOUT_MS,
                        TimeUnit.MILLISECONDS
                    )
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                false
            } finally {
                if (wifiConfigurationAcknowledgement === acknowledgement) {
                    wifiConfigurationAcknowledgement = null
                }
            }

        if (!acknowledged) {
            return false
        }

        val updated =
            current.copy(
                wifiSsid = configuration.ssid,
                wifiPassword = configuration.password,
                wifiHost = ""
            )
        UvirSensorCredentialStore.save(applicationContext, updated)
        mutableState.value = mutableState.value.copy(credentials = updated)
        return true
    }

    fun sendSensorControlCommands(
        commands: List<String>
    ): Boolean {
        if (!usbTransportReady()) return false
        return commands.all(::writeCommand)
    }

    internal suspend fun diagnosticProbe(hardwareId: String): UvirDiagnosticProbe {
        val transportGeneration = serialGeneration.get()
        return diagnosticClient.request(
            hardwareId = hardwareId,
            connectionValid = {
                val current = mutableState.value
                serialGeneration.get() == transportGeneration &&
                    current.status == UsbSensorConnectionStatus.CONNECTED &&
                    current.appConnectionConfirmed &&
                    current.deviceId.equals(hardwareId, ignoreCase = true)
            },
            send = { sendSensorControlCommands(listOf(it)) }
        )
    }

    fun stopAutomaticAcquisitionAndAwait(timeoutMs: Long): Boolean {
        if (!usbTransportReady()) return false
        val acknowledgement = CountDownLatch(1)
        automaticStopConfirmed = false
        automaticStopAcknowledgement = acknowledgement
        return try {
            writeCommand("OFFLINE_STOP") &&
                acknowledgement.await(
                    timeoutMs.coerceIn(1_000L, 120_000L),
                    TimeUnit.MILLISECONDS
                ) &&
                automaticStopConfirmed
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            false
        } finally {
            if (automaticStopAcknowledgement === acknowledgement) {
                automaticStopAcknowledgement = null
            }
        }
    }

    fun testStatusLedAndAwait(timeoutMs: Long): Boolean =
        sendStatusTestAndAwait(
            command = "LED_TEST",
            expectedField = "led_test",
            timeoutMs = timeoutMs
        )

    fun testStatusBuzzerAndAwait(timeoutMs: Long): Boolean =
        sendStatusTestAndAwait(
            command = "BUZZER_TEST",
            expectedField = "buzzer_test",
            timeoutMs = timeoutMs
        )

    fun restoreDefaultsAndPowerOffAwait(timeoutMs: Long): Boolean =
        sendStatusTestAndAwait(
            command = "FACTORY_RESET",
            expectedField = "factory_reset",
            timeoutMs = timeoutMs
        )

    private fun sendStatusTestAndAwait(
        command: String,
        expectedField: String,
        timeoutMs: Long
    ): Boolean {
        if (!usbTransportReady()) return false
        val acknowledgement = CountDownLatch(1)
        statusTestConfirmed = false
        statusTestExpectedField = expectedField
        statusTestAcknowledgement = acknowledgement
        return try {
            writeCommand(command) &&
                acknowledgement.await(
                    timeoutMs.coerceIn(1_000L, 15_000L),
                    TimeUnit.MILLISECONDS
                ) &&
                statusTestConfirmed
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            false
        } finally {
            if (statusTestAcknowledgement === acknowledgement) {
                statusTestAcknowledgement = null
                statusTestExpectedField = null
            }
        }
    }

    fun acknowledgeOfflineRecord(recordId: Long) {
        executor.execute {
            if (usbTransportReady()) {
                offlineSyncLastProgressAtMs =
                    SystemClock.elapsedRealtime()
                if (writeCommand("SYNC_ACK $recordId")) {
                    Log.i("UvirSync", "USB ack id=$recordId")
                } else {
                    Log.e("UvirSync", "USB ack failed id=$recordId")
                }
            }
        }
    }

    fun markOfflineSyncComplete() {
        offlineSyncCompleted = true
        val current = mutableState.value
        val runtime = current.runtimeInfo
        mutableState.value =
            current.copy(
                runtimeInfo =
                    runtime.copy(
                        offlineUsed = 0,
                        offlineRemaining = runtime.offlineCapacity,
                        offlineStorageFull = false,
                        offlineAcquisitions = 0,
                        offlineAlerts = 0,
                        offlineErrors = 0
                    )
            )
        sendSensorControlCommands(listOf("HELLO"))
    }

    /**
     * Drops the current app-side association without modifying the sensor.
     * The USB device remains physically attached, but it must be attached
     * again (or rediscovered after an app restart) before it can be paired.
     */
    fun disconnectForDisassociation() {
        desiredStreaming = false
        desiredWirelessMode = null
        writeCommand("STOP")
        closeConnection(
            error = null,
            attached = currentDeviceId != null
        )
    }

    /** Drain queued USB work before installing another sensor's credentials. */
    fun disconnectForSensorSelection() {
        executor.submit {
            synchronized(lineBuffer) { disconnectForDisassociation() }
        }.get(5, TimeUnit.SECONDS)
    }

    fun refreshSelectedSensor() {
        mutableState.value = UvirUsbSensorState(
            credentials = UvirSensorCredentialStore.load(applicationContext)
        )
        scanForSensor()
    }

    fun stop() {
        desiredStreaming = false

        executor.execute {
            writeCommand("STOP")
            closeConnection(
                error = null,
                attached = false
            )
        }

        if (receiverRegistered) {
            runCatching {
                applicationContext.unregisterReceiver(
                    permissionReceiver
                )
            }
            runCatching {
                applicationContext.unregisterReceiver(
                    deviceReceiver
                )
            }
            receiverRegistered = false
        }

        executor.shutdown()
    }

    override fun onNewData(data: ByteArray) {
        processSerialData(data, serialGeneration.get())
    }

    private fun processSerialData(data: ByteArray, generation: Long) {
        synchronized(lineBuffer) {
            if (generation != serialGeneration.get()) return
            lineBuffer.append(
                String(
                    data,
                    StandardCharsets.UTF_8
                )
            )

            while (true) {
                val newlineIndex =
                    lineBuffer.indexOf("\n")

                if (newlineIndex < 0) {
                    if (
                        lineBuffer.length >
                        MAXIMUM_LINE_LENGTH
                    ) {
                        lineBuffer.clear()
                    }
                    break
                }

                val line =
                    lineBuffer
                        .substring(0, newlineIndex)
                        .trim()

                lineBuffer.delete(
                    0,
                    newlineIndex + 1
                )

                if (line.isNotEmpty()) {
                    parseLine(line)
                }
            }
        }
    }

    override fun onRunError(error: Exception) {
        processSerialError(error, serialGeneration.get())
    }

    private fun processSerialError(error: Exception, generation: Long) {
        if (generation != serialGeneration.get()) return
        if (
            mutableState.value.error ==
            USB_SENSOR_IDENTITY_MISMATCH_ERROR
        ) {
            return
        }
        UvirErrorLog.record(
            applicationContext,
            "usb_serial_run",
            error
        )
        executor.execute {
            if (generation != serialGeneration.get()) return@execute
            closeConnection(
                error = error.message
                    ?: "usb_serial_error",
                attached = true
            )
        }
    }

    private fun registerReceivers() {
        if (receiverRegistered) {
            return
        }

        ContextCompat.registerReceiver(
            applicationContext,
            permissionReceiver,
            IntentFilter(ACTION_USB_PERMISSION),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        ContextCompat.registerReceiver(
            applicationContext,
            deviceReceiver,
            IntentFilter().apply {
                addAction(
                    UsbManager.ACTION_USB_DEVICE_ATTACHED
                )
                addAction(
                    UsbManager.ACTION_USB_DEVICE_DETACHED
                )
            },
            ContextCompat.RECEIVER_EXPORTED
        )

        receiverRegistered = true
    }

    private fun connectOrRequestPermission(
        device: UsbDevice
    ) {
        val driver =
            UsbSerialProber
                .getDefaultProber()
                .probeDevice(device)
                ?: return

        if (
            device.vendorId != CP210X_VENDOR_ID ||
            device.productId != CP210X_PRODUCT_ID ||
            driver.ports.isEmpty()
        ) {
            return
        }

        val currentStatus = mutableState.value.status
        if (
            currentDeviceId != null &&
            currentDeviceId != device.deviceId &&
            currentStatus in
                setOf(
                    UsbSensorConnectionStatus.WAITING_PERMISSION,
                    UsbSensorConnectionStatus.CONNECTING,
                    UsbSensorConnectionStatus.CONNECTED
                )
        ) {
            // Do not let a second physical USB device evict the transport that
            // is already being used, for example when both are on a USB hub.
            Log.w(
                "UvirUsb",
                "Ignoring additional USB device while another is active"
            )
            return
        }

        if (currentDeviceId == device.deviceId &&
            mutableState.value.status in
                setOf(
                    UsbSensorConnectionStatus.CONNECTING,
                    UsbSensorConnectionStatus.CONNECTED
                )
        ) {
            return
        }

        currentDeviceId = device.deviceId

        if (usbManager.hasPermission(device)) {
            openDevice(device)
            return
        }

        mutableState.value =
            UvirUsbSensorState(
                status =
                    UsbSensorConnectionStatus.WAITING_PERMISSION,
                attached = true,
                deviceName = device.productName
            )

        val permissionIntent =
            PendingIntent.getBroadcast(
                applicationContext,
                device.deviceId,
                Intent(ACTION_USB_PERMISSION).apply {
                    setPackage(
                        applicationContext.packageName
                    )
                },
                PendingIntent.FLAG_UPDATE_CURRENT or
                        PendingIntent.FLAG_MUTABLE
            )

        usbManager.requestPermission(
            device,
            permissionIntent
        )
    }

    private fun openDevice(device: UsbDevice) {
        executor.execute {
            synchronized(connectionLock) {
                closeSerialResources()
                resetOfflineSyncState()

                mutableState.value =
                    UvirUsbSensorState(
                        status =
                            UsbSensorConnectionStatus.CONNECTING,
                        attached = true,
                        deviceName = device.productName
                    )

                val driver =
                    UsbSerialProber
                        .getDefaultProber()
                        .probeDevice(device)

                if (driver == null ||
                    driver.ports.isEmpty()
                ) {
                    closeConnection(
                        error = "usb_driver_not_found",
                        attached = true
                    )
                    return@synchronized
                }

                val connection =
                    usbManager.openDevice(device)

                if (connection == null) {
                    closeConnection(
                        error = "usb_open_failed",
                        attached = true
                    )
                    return@synchronized
                }

                try {
                    val port = driver.ports.first()
                    port.open(connection)
                    port.setParameters(
                        SERIAL_BAUD_RATE,
                        UsbSerialPort.DATABITS_8,
                        UsbSerialPort.STOPBITS_1,
                        UsbSerialPort.PARITY_NONE
                    )

                    deviceConnection = connection
                    serialPort = port
                    currentDeviceId = device.deviceId
                    protocolAccepted = false

                    val readerGeneration = serialGeneration.get()
                    val manager =
                        SerialInputOutputManager(
                            port,
                            object : SerialInputOutputManager.Listener {
                                override fun onNewData(data: ByteArray) =
                                    processSerialData(data, readerGeneration)
                                override fun onRunError(error: Exception) =
                                    processSerialError(error, readerGeneration)
                            }
                        )
                    ioManager = manager
                    manager.start()

                    mutableState.value =
                        UvirUsbSensorState(
                            status =
                                UsbSensorConnectionStatus.CONNECTING,
                            attached = true,
                            deviceName =
                                device.productName
                                    ?: device.deviceName
                        )

                    Thread.sleep(250L)
                    writeCommand("HELLO")
                } catch (error: Exception) {
                    UvirErrorLog.record(
                        applicationContext,
                        "usb_configuration",
                        error
                    )
                    runCatching {
                        connection.close()
                    }
                    closeConnection(
                        error = error.message
                            ?: "usb_configuration_failed",
                        attached = true
                    )
                }
            }
        }
    }

    private fun parseLine(line: String) {
        val json =
            runCatching {
                JSONObject(line)
            }.getOrNull() ?: return

        if (
            json.optString("protocol") !=
            PROTOCOL_NAME
        ) {
            return
        }

        val frameType = json.optString("type")
        if (frameType == "activity") {
            val previous = mutableState.value
            previous.runtimeInfo.updatedFromActivityFrame(json, previous.appConnectionConfirmed)?.let { info ->
                mutableState.value = previous.copy(runtimeInfo = info)
            }
            return
        }
        if (frameType == "diagnostic") {
            diagnosticClient.receive(
                json.optString("request_id"), mutableState.value.runtimeInfo.updatedFrom(json)
            )
            return
        }
        acknowledgeAutomaticStop(json)
        acknowledgeStatusTest(json)
        if (
            frameType == "sync_start" ||
            frameType == "offline_record" ||
            frameType == "sync_complete" ||
            frameType == "sync_failed"
        ) {
            offlineSyncLastProgressAtMs = SystemClock.elapsedRealtime()
            if (frameType == "sync_complete" || frameType == "sync_failed") {
                offlineSyncCompleted = true
            }
        }

        if (parseSensorSyncFrame(json, SensorSyncSource.USB)) {
            return
        }
        if (parseSensorRuntimeFrame(json, SensorSyncSource.USB)) {
            return
        }

        when (frameType) {
            "hello" -> parseHello(json)
            "sample" -> parseSample(json)
            "alert_event" -> parseAlertEvent(json)
            "status" -> {
                acknowledgeWifiConfiguration(json)
                acknowledgeInternetConfiguration(json)
                persistReportedRadioSettings(json)
                if (usbTransportReady()) {
                    val previous = mutableState.value
                    mutableState.value =
                        previous.copy(
                            status = UsbSensorConnectionStatus.CONNECTED,
                            attached = true,
                            appConnectionConfirmed =
                                when {
                                    json.has("app_connected") ->
                                        json.optBoolean("app_connected")

                                    json.optBoolean("streaming", false) -> true
                                    else -> previous.appConnectionConfirmed
                                },
                            runtimeInfo =
                                previous.runtimeInfo.updatedFrom(json),
                            debugPerformanceCompletionSequence =
                                if (
                                    json.optString("debug_performance") ==
                                    "finished"
                                ) {
                                    debugPerformanceCompletionCounter
                                        .incrementAndGet()
                                } else {
                                    previous.debugPerformanceCompletionSequence
                                },
                            error = null
                        )
                }
            }
            "error" -> {
                val previous = mutableState.value
                val code =
                    json.optString(
                        "code",
                        "sensor_error"
                    )
                val fatalError =
                    code == "sensor_not_found" ||
                            code == "sensor_read" ||
                            code == "sensor_init_failed"
                Log.e(
                    "UvirSync",
                    "USB sensor error $code: " +
                        json.optString("message", code)
                )
                UvirErrorLog.record(
                    context = applicationContext,
                    source = "sensor_usb:$code",
                    message = json.optString("message", code)
                )
                mutableState.value =
                    previous.copy(
                        status =
                            if (fatalError) {
                                UsbSensorConnectionStatus.ERROR
                            } else {
                                UsbSensorConnectionStatus.CONNECTED
                            },
                        attached = true,
                        error = code
                    )
            }
        }
    }

    private fun parseAlertEvent(json: JSONObject) {
        if (!protocolAccepted || !desiredStreaming) return
        val details = json.optString("details")
        if (details.isBlank()) return
        Log.i("UvirAlert", "USB live alert event received")
        mutableState.value =
            mutableState.value.copy(
                alertEvent =
                    SensorLiveAlertEvent(
                        receiptSequence =
                            alertReceiptSequence.incrementAndGet(),
                        timestampMs =
                            json.optLong("timestamp_ms")
                                .takeIf { it > 0L }
                                ?: System.currentTimeMillis(),
                        details = details,
                        sessionId = json.optLong("session_id", 0L)
                    )
            )
    }

    private fun persistReportedRadioSettings(json: JSONObject) {
        if (
            !json.has("wifi_enabled") &&
            !json.has("bluetooth_enabled") &&
            !json.has("internet_enabled") &&
            !json.has("internet_relay_host")
        ) {
            return
        }

        val credentials = mutableState.value.credentials
        val updated =
            credentials.copy(
                wifiEnabled =
                    json.optBoolean(
                        "wifi_enabled",
                        credentials.wifiEnabled
                    ),
                bluetoothEnabled =
                    json.optBoolean(
                        "bluetooth_enabled",
                        credentials.bluetoothEnabled
                    ),
                internetEnabled =
                    json.optBoolean(
                        "internet_enabled",
                        credentials.internetEnabled
                    ),
                internetUsePrimaryWifi =
                    json.optBoolean(
                        "internet_use_primary_wifi",
                        credentials.internetUsePrimaryWifi
                    ),
                internetRelayHost =
                    json.optString(
                        "internet_relay_host",
                        credentials.internetRelayHost
                    ),
                internetRelayPort =
                    json.optInt(
                        "internet_relay_port",
                        credentials.internetRelayPort
                    )
            )
        if (updated != credentials) {
            UvirSensorCredentialStore.save(applicationContext, updated)
            mutableState.value =
                mutableState.value.copy(credentials = updated)
        }

        val expected = expectedRadioSettings ?: return
        if (
            updated.wifiEnabled == expected.wifiEnabled &&
            updated.bluetoothEnabled == expected.bluetoothEnabled
        ) {
            radioSettingsAcknowledgement?.countDown()
        }
    }

    private fun acknowledgeWifiConfiguration(json: JSONObject) {
        if (json.optBoolean("wifi_configuration_saved", false)) {
            wifiConfigurationAcknowledgement?.countDown()
        }
    }

    private fun acknowledgeInternetConfiguration(json: JSONObject) {
        if (json.optBoolean("internet_configuration_saved", false)) {
            internetConfigurationAcknowledgement?.countDown()
        }
    }

    private fun acknowledgeAutomaticStop(json: JSONObject) {
        if (
            json.optString("type") == "automatic_status" &&
            !json.optBoolean("job_active", true)
        ) {
            automaticStopConfirmed = true
            automaticStopAcknowledgement?.countDown()
        }
    }

    private fun acknowledgeStatusTest(json: JSONObject) {
        val expectedField = statusTestExpectedField ?: return
        val accepted =
            json.optString("type") == "status" &&
                json.optString(expectedField) == "started"
        val rejected =
            json.optString("type") == "error" &&
                json.optString("code").startsWith(expectedField)
        if (accepted || rejected) {
            statusTestConfirmed = accepted
            statusTestAcknowledgement?.countDown()
        }
    }

    /**
     * Protocol-level command errors must not poison a healthy USB serial link.
     * In particular, a missing Wi-Fi configuration must still allow the app to
     * switch the ESP32 back to USB and resume streaming immediately.
     */
    private fun usbTransportReady(): Boolean =
        protocolAccepted && serialPort != null

    private fun parseHello(json: JSONObject) {
        val sensorAvailable =
            json.optBoolean(
                "sensor_available",
                true
            )

        if (!sensorAvailable) {
            protocolAccepted = true
            mutableState.value =
                mutableState.value.copy(
                    status =
                        UsbSensorConnectionStatus.ERROR,
                    attached = true,
                    sensorName =
                        json.optString(
                            "sensor",
                            "AS7343"
                        ),
                    error = "sensor_not_found"
                )
            return
        }

        val storedCredentials =
            UvirSensorCredentialStore.load(applicationContext)
        val deviceId = json.optString("device_id").trim()
        if (
            !isUsbSensorIdentityAllowed(
                associatedDeviceId = storedCredentials.deviceId,
                candidateDeviceId = deviceId
            )
        ) {
            rejectUnassociatedSensor(
                expectedDeviceId = storedCredentials.deviceId,
                candidateDeviceId = deviceId,
                storedCredentials = storedCredentials
            )
            return
        }
        val sensorStoredCredentials =
            UvirSensorCredentialStore.loadForDevice(
                applicationContext,
                deviceId
            ) ?: storedCredentials
        val sameSensor =
            sensorStoredCredentials.deviceId.isNotBlank() &&
                    sensorStoredCredentials.deviceId.equals(
                        deviceId,
                        ignoreCase = true
                    )
        val reportedWifiSsid = json.optString("wifi_ssid")
        val reportedWifiPassword = json.optString("wifi_password")
        val wifiConfigured =
            json.optBoolean(
                "wifi_configured",
                reportedWifiSsid.isNotBlank()
            )

        val credentials = UvirSensorCredentials(
            deviceId = deviceId,
            firmwareVersion =
                json.optString("firmware").ifBlank {
                    sensorStoredCredentials.firmwareVersion
                        .takeIf { sameSensor }
                        .orEmpty()
                },
            authToken = json.optString("auth_token"),
            wifiSsid = if (wifiConfigured) {
                reportedWifiSsid.ifBlank {
                    sensorStoredCredentials.wifiSsid.takeIf { sameSensor }.orEmpty()
                }
            } else "",
            wifiPassword = if (wifiConfigured) {
                reportedWifiPassword.ifBlank {
                    sensorStoredCredentials.wifiPassword.takeIf { sameSensor }.orEmpty()
                }
            } else "",
            wifiHost = if (wifiConfigured) {
                json.optString("wifi_host").ifBlank {
                    sensorStoredCredentials.wifiHost.takeIf { sameSensor }.orEmpty()
                }
            } else "",
            wifiPort = json.optInt("wifi_port", 8733),
            wifiDiscoveryPort =
                json.optInt("wifi_discovery_port", 8732),
            wifiEnabled =
                json.optBoolean(
                    "wifi_enabled",
                    sensorStoredCredentials.wifiEnabled
                        .takeIf { sameSensor }
                        ?: true
                ),
            bluetoothName = json.optString("bluetooth_name"),
            bluetoothPin = json.optString("bluetooth_pin"),
            bluetoothEnabled =
                json.optBoolean(
                    "bluetooth_enabled",
                    sensorStoredCredentials.bluetoothEnabled
                        .takeIf { sameSensor }
                        ?: true
                ),
            internetEnabled =
                json.optBoolean(
                    "internet_enabled",
                    sensorStoredCredentials.internetEnabled.takeIf { sameSensor }
                        ?: false
                ),
            internetUsePrimaryWifi =
                json.optBoolean(
                    "internet_use_primary_wifi",
                    sensorStoredCredentials.internetUsePrimaryWifi.takeIf { sameSensor }
                        ?: true
                ),
            internetWifiSsid =
                json.optString("internet_wifi_ssid").ifBlank {
                    sensorStoredCredentials.internetWifiSsid.takeIf { sameSensor }.orEmpty()
                },
            internetWifiPassword =
                json.optString("internet_wifi_password").ifBlank {
                    sensorStoredCredentials.internetWifiPassword.takeIf { sameSensor }.orEmpty()
                },
            internetRelayHost =
                json.optString("internet_relay_host").ifBlank {
                    sensorStoredCredentials.internetRelayHost.takeIf { sameSensor }.orEmpty()
                },
            internetRelayPort =
                json.optInt(
                    "internet_relay_port",
                    sensorStoredCredentials.internetRelayPort.takeIf { sameSensor }
                        ?: DEFAULT_UVIR_RELAY_PORT
                ),
            internetMqttUsername =
                json.optString("internet_mqtt_username").ifBlank {
                    sensorStoredCredentials.internetMqttUsername.takeIf { sameSensor }.orEmpty()
                },
            internetMqttPassword =
                json.optString("internet_mqtt_password").ifBlank {
                    sensorStoredCredentials.internetMqttPassword.takeIf { sameSensor }.orEmpty()
                }
        )
        val runtimeInfo =
            mutableState.value.runtimeInfo.updatedFrom(json)

        if (credentials.isProvisioned) {
            UvirSensorCredentialStore.save(
                applicationContext,
                credentials
            )
        }

        protocolAccepted = true
        mutableState.value =
            mutableState.value.copy(
                status =
                    UsbSensorConnectionStatus.CONNECTED,
                attached = true,
                sensorName =
                    json.optString(
                        "sensor",
                        "AS7343"
                    ),
                deviceId = credentials.deviceId.ifBlank { null },
                firmwareVersion =
                    credentials.firmwareVersion.ifBlank { null },
                runtimeInfo = runtimeInfo,
                calibrationKind =
                    json.optString(
                        "calibration",
                        "datasheet_estimate"
                    ),
                credentials = credentials,
                calibrationProvisional =
                    json.optString(
                        "calibration"
                    ) != "calibrated",
                uvAvailable =
                    json.optBoolean(
                        "uv_available",
                        false
                    ),
                error = null
            )

        val reportedWirelessMode =
            when (
                json.optString("wireless_mode")
                    .lowercase()
            ) {
                "wifi" -> SensorConnectionMode.WIFI
                "bluetooth" -> SensorConnectionMode.BLUETOOTH
                "internet" -> SensorConnectionMode.INTERNET
                "off" -> SensorConnectionMode.USB
                else -> null
            }

        // HELLO is also the reply to the periodic USB PING. Reapplying an
        // already active radio here would restart Wi-Fi/Bluetooth every four
        // seconds while the USB control cable remains connected.
        val requestedWirelessMode = desiredWirelessMode
        if (
            requestedWirelessMode != null &&
            reportedWirelessMode != requestedWirelessMode
        ) {
            writeCommand(
                when (requestedWirelessMode) {
                    SensorConnectionMode.USB -> return
                    SensorConnectionMode.WIFI -> "WIRELESS WIFI"
                    SensorConnectionMode.BLUETOOTH -> "WIRELESS BLUETOOTH"
                    SensorConnectionMode.INTERNET -> "WIRELESS INTERNET"
                }
            )
        }

        // Synchronize before enabling periodic samples so live frames cannot
        // compete with queued records and control acknowledgements.
        if (desiredStreaming && !offlineSyncRequested) {
            offlineSyncRequested = true
            offlineSyncCompleted = false
            offlineSyncLastProgressAtMs =
                SystemClock.elapsedRealtime()
            writeCommand("APP_CONNECT")
            writeCommand(sensorTimeCommand())
            writeCommand("SYNC_BEGIN")
            writeCommand(
                "STREAM $desiredIntervalMs"
            )
            Log.i("UvirSync", "USB sync requested")
        } else if (
            desiredStreaming &&
            !mutableState.value.appConnectionConfirmed
        ) {
            writeCommand("APP_CONNECT")
            writeCommand(
                "STREAM $desiredIntervalMs"
            )
        }
    }

    private fun rejectUnassociatedSensor(
        expectedDeviceId: String,
        candidateDeviceId: String,
        storedCredentials: UvirSensorCredentials
    ) {
        Log.w(
            "UvirUsb",
            "Rejected USB sensor identity; expected=$expectedDeviceId " +
                "received=${candidateDeviceId.ifBlank { "<missing>" }}"
        )
        UvirErrorLog.record(
            context = applicationContext,
            source = "sensor_usb:$USB_SENSOR_IDENTITY_MISMATCH_ERROR",
            message =
                "Expected sensor $expectedDeviceId but received " +
                    candidateDeviceId.ifBlank { "an empty device ID" }
        )

        val rejectedState =
            UvirUsbSensorState(
                status = UsbSensorConnectionStatus.ERROR,
                attached = true,
                deviceName = mutableState.value.deviceName,
                deviceId = storedCredentials.deviceId.ifBlank { null },
                credentials = storedCredentials,
                error = USB_SENSOR_IDENTITY_MISMATCH_ERROR
            )
        protocolAccepted = false
        mutableState.value = rejectedState

        executor.execute {
            synchronized(connectionLock) {
                closeSerialResources()
                resetOfflineSyncState()
                protocolAccepted = false
                mutableState.value = rejectedState
            }
        }
    }

    private fun parseSample(json: JSONObject) {
        if (!protocolAccepted) return
        val sample = json.toUvirSensorSampleOrNull() ?: return

        val sequence =
            json.optLong(
                "seq",
                mutableState.value.sampleSequence + 1L
            )

        mutableState.value =
            mutableState.value.copy(
                status =
                    UsbSensorConnectionStatus.CONNECTED,
                attached = true,
                appConnectionConfirmed = true,
                sample = sample,
                sampleSequence = sequence,
                runtimeInfo =
                    mutableState.value.runtimeInfo.updatedFrom(json),
                calibrationProvisional =
                    json.optString(
                        "calibration"
                    ) != "calibrated",
                uvAvailable =
                    json.optBoolean(
                        "uv_available",
                        false
                    ),
                saturated =
                    json.optBoolean(
                        "saturated",
                        false
                    ),
                error = null
            )
    }

    private fun writeCommand(command: String): Boolean {
        val port = serialPort ?: return false

        return runCatching {
            synchronized(commandWriteLock) {
                port.write(
                    "$command\n".toByteArray(
                        StandardCharsets.US_ASCII
                    ),
                    WRITE_TIMEOUT_MS
                )
            }
            true
        }.getOrElse {
            closeConnection(
                error = it.message ?: "usb_write_failed",
                attached = true
            )
            false
        }
    }

    private fun closeConnection(
        error: String?,
        attached: Boolean
    ) {
        automaticStopConfirmed = false
        automaticStopAcknowledgement?.countDown()
        statusTestConfirmed = false
        statusTestAcknowledgement?.countDown()
        synchronized(connectionLock) {
            closeSerialResources()
            protocolAccepted = false
            resetOfflineSyncState()

            if (!attached) {
                currentDeviceId = null
            }

            mutableState.value =
                UvirUsbSensorState(
                    status =
                        if (error == null) {
                            UsbSensorConnectionStatus.DISCONNECTED
                        } else {
                            UsbSensorConnectionStatus.ERROR
                        },
                    attached = attached,
                    error = error
                )
        }
    }

    private fun closeSerialResources() {
        serialGeneration.incrementAndGet()
        runCatching {
            ioManager?.stop()
        }
        ioManager = null

        runCatching {
            serialPort?.close()
        }
        serialPort = null

        runCatching {
            deviceConnection?.close()
        }
        deviceConnection = null

        synchronized(lineBuffer) {
            lineBuffer.clear()
        }
    }

    private fun resetOfflineSyncState() {
        offlineSyncRequested = false
        offlineSyncCompleted = false
        offlineSyncLastProgressAtMs = 0L
    }

    private fun Intent.usbDeviceExtra():
            UsbDevice? =
        if (Build.VERSION.SDK_INT >= 33) {
            getParcelableExtra(
                UsbManager.EXTRA_DEVICE,
                UsbDevice::class.java
            )
        } else {
            @Suppress("DEPRECATION")
            getParcelableExtra(
                UsbManager.EXTRA_DEVICE
            )
        }

    companion object {
        private const val ACTION_USB_PERMISSION =
            "me.mondiversi.uvir.USB_PERMISSION"
        private const val PROTOCOL_NAME =
            UVIR_SENSOR_PROTOCOL
        private const val SERIAL_BAUD_RATE = 115200
        private const val WRITE_TIMEOUT_MS = 1000
        private const val RADIO_SETTINGS_ACK_TIMEOUT_MS = 1500L
        private const val WIFI_CONFIGURATION_ACK_TIMEOUT_MS = 2000L
        private const val INTERNET_CONFIGURATION_ACK_TIMEOUT_MS = 3000L
        private const val CP210X_VENDOR_ID = 0x10C4
        private const val CP210X_PRODUCT_ID = 0xEA60
        private const val MINIMUM_STREAM_INTERVAL_MS = 150L
        private const val MAXIMUM_STREAM_INTERVAL_MS = 5000L
        private const val OFFLINE_SYNC_RETRY_TIMEOUT_MS = 12_000L
        private const val MAXIMUM_LINE_LENGTH = 16384
    }
}
