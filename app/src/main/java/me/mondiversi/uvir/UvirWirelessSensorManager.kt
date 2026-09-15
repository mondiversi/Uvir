package me.mondiversi.uvir

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.MqttCallback
import org.eclipse.paho.client.mqttv3.MqttClient
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence

enum class WirelessSensorConnectionStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    PERMISSION_REQUIRED,
    PAIRING_REQUIRED,
    PROVISIONING_REQUIRED,
    ERROR
}

data class UvirWirelessSensorState(
    val status: WirelessSensorConnectionStatus =
        WirelessSensorConnectionStatus.DISCONNECTED,
    val mode: SensorConnectionMode? = null,
    val deviceId: String? = null,
    val sensorName: String? = null,
    val firmwareVersion: String? = null,
    val appConnectionConfirmed: Boolean = false,
    val runtimeInfo: UvirSensorRuntimeInfo = UvirSensorRuntimeInfo(),
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
 * Authenticated TCP (ESP32 on the same LAN), Bluetooth SPP and MQTT client.
 *
 * Provisioning data is learned from the ESP32 over USB. Only one worker and
 * one transport can be active, which prevents duplicate streams and samples.
 */
class UvirWirelessSensorManager(context: Context) {
    private val applicationContext = context.applicationContext
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private val generation = AtomicInteger(0)
    private val diagnosticClient = UvirSensorDiagnosticClient()
    private val mutableState = MutableStateFlow(UvirWirelessSensorState())
    private val outputWriteLock = Any()
    private val alertReceiptSequence = AtomicLong(0L)
    private val debugPerformanceCompletionCounter = AtomicLong(0L)

    val state: StateFlow<UvirWirelessSensorState> = mutableState.asStateFlow()

    @Volatile
    private var desiredMode: SensorConnectionMode? = null

    @Volatile
    private var desiredStreaming = false

    @Volatile
    private var desiredSampling = true

    @Volatile
    private var desiredIntervalMs = DEFAULT_STREAM_INTERVAL_MS

    private var worker: Future<*>? = null
    private var tcpSocket: Socket? = null
    private var bluetoothSocket: BluetoothSocket? = null
    private var mqttClient: MqttClient? = null

    @Volatile
    private var activeProtocolOutput: OutputStream? = null

    @Volatile
    private var expectedWirelessMode: SensorConnectionMode? = null

    @Volatile
    private var wirelessModeAcknowledgement: CountDownLatch? = null

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
    private var offlineSyncRequested = false

    @Volatile
    private var offlineSyncCompleted = false

    @Volatile
    private var offlineSyncLastProgressAtMs = 0L

    @Synchronized
    fun setStreaming(
        mode: SensorConnectionMode,
        enabled: Boolean,
        intervalMs: Long,
        requestSamples: Boolean = enabled
    ) {
        val newMode = mode.takeIf {
            it == SensorConnectionMode.WIFI ||
                    it == SensorConnectionMode.BLUETOOTH ||
                    it == SensorConnectionMode.INTERNET
        }
        val newStreaming = enabled && newMode != null
        val newInterval = intervalMs.coerceIn(
            MINIMUM_STREAM_INTERVAL_MS,
            MAXIMUM_STREAM_INTERVAL_MS
        )
        val newSampling = newStreaming && requestSamples
        val previousMode = desiredMode
        val previousStreaming = desiredStreaming
        val previousSampling = desiredSampling
        val previousInterval = desiredIntervalMs
        val configurationChanged =
            previousMode != newMode ||
                    previousStreaming != newStreaming ||
                    previousSampling != newSampling ||
                    previousInterval != newInterval

        val currentState = mutableState.value
        val canReconfigureInPlace =
            configurationChanged &&
                    previousMode == newMode &&
                    previousStreaming &&
                    newStreaming &&
                    currentState.status ==
                    WirelessSensorConnectionStatus.CONNECTED &&
                    currentState.mode == newMode &&
                    activeProtocolOutput != null &&
                    !(
                        previousSampling &&
                                !newSampling &&
                                offlineSyncRequested &&
                                !offlineSyncCompleted
                        )

        desiredMode = newMode
        desiredStreaming = newStreaming
        desiredSampling = newSampling
        desiredIntervalMs = newInterval

        if (configurationChanged) {
            val updatedInPlace =
                canReconfigureInPlace && reconfigureConnectedStream(
                    previousSampling = previousSampling,
                    newSampling = newSampling,
                    intervalChanged = previousInterval != newInterval,
                    newInterval = newInterval
                )
            if (!updatedInPlace) {
                restartWorker()
            }
        } else {
            retryIfNeeded()
        }
    }

    /**
     * Reuses an already authenticated transport when the user returns from an
     * unavailable USB source to the same wireless source. If any command
     * fails, the caller falls back to the normal close-and-reconnect path.
     */
    private fun reconfigureConnectedStream(
        previousSampling: Boolean,
        newSampling: Boolean,
        intervalChanged: Boolean,
        newInterval: Long
    ): Boolean {
        val output = activeProtocolOutput ?: return false
        return runCatching {
            when {
                !previousSampling && newSampling -> {
                    resetOfflineSyncState()
                    offlineSyncRequested = true
                    offlineSyncLastProgressAtMs =
                        SystemClock.elapsedRealtime()
                    writeLine(output, "APP_CONNECT")
                    writeLine(output, sensorTimeCommand())
                    writeLine(output, "SYNC_BEGIN")
                    writeLine(output, "STREAM $newInterval")
                    Log.i(
                        "UvirSync",
                        "WIRELESS standby resumed in place"
                    )
                }

                previousSampling && !newSampling -> {
                    writeLine(output, "STOP")
                    mutableState.value =
                        mutableState.value.copy(
                            appConnectionConfirmed = false
                        )
                    offlineSyncRequested = false
                    offlineSyncCompleted = true
                    offlineSyncLastProgressAtMs = 0L
                    Log.i(
                        "UvirSync",
                        "WIRELESS stream paused in place"
                    )
                }

                newSampling && intervalChanged -> {
                    writeLine(output, "STREAM $newInterval")
                    Log.i(
                        "UvirSync",
                        "WIRELESS stream interval updated in place"
                    )
                }
            }
            true
        }.onFailure { error ->
            Log.w(
                "UvirSync",
                "WIRELESS in-place update failed; reconnecting",
                error
            )
        }.getOrDefault(false)
    }

    fun retry() {
        restartWorker()
    }

    fun retryIfNeeded() {
        if (
            desiredStreaming &&
            mutableState.value.status !=
            WirelessSensorConnectionStatus.CONNECTED
        ) {
            restartWorker()
        }
    }

    /**
     * Asks the sensor to expose another radio through the currently
     * authenticated wireless connection. Provisioning still requires USB, but
     * an already paired sensor can move between Wi-Fi and Bluetooth without
     * being physically reconnected first.
     */
    @Synchronized
    fun requestWirelessModeAndAwait(
        mode: SensorConnectionMode,
        timeoutMs: Long = WIRELESS_MODE_ACK_TIMEOUT_MS
    ): Boolean {
        val currentMode = desiredMode
        if (
            !desiredStreaming ||
            currentMode == null ||
            currentMode == mode ||
            mutableState.value.status !=
            WirelessSensorConnectionStatus.CONNECTED
        ) {
            return false
        }

        val output = activeProtocolOutput ?: return false
        val command = when (mode) {
            SensorConnectionMode.USB -> "WIRELESS OFF"
            SensorConnectionMode.WIFI -> "WIRELESS WIFI"
            SensorConnectionMode.BLUETOOTH -> "WIRELESS BLUETOOTH"
            SensorConnectionMode.INTERNET -> "WIRELESS INTERNET"
        }
        val acknowledgement = CountDownLatch(1)
        expectedWirelessMode = mode
        wirelessModeAcknowledgement = acknowledgement

        return try {
            writeLine(output, command)
            acknowledgement.await(
                timeoutMs.coerceIn(
                    WIRELESS_MODE_ACK_TIMEOUT_MS,
                    MAXIMUM_WIRELESS_MODE_ACK_TIMEOUT_MS
                ),
                TimeUnit.MILLISECONDS
            )
        } catch (_: Exception) {
            false
        } finally {
            if (wirelessModeAcknowledgement === acknowledgement) {
                wirelessModeAcknowledgement = null
                expectedWirelessMode = null
            }
        }
    }

    @Synchronized
    fun configureWirelessTransportsEnabled(
        target: SensorRadioSettings
    ): Boolean {
        val state = mutableState.value
        if (
            state.status != WirelessSensorConnectionStatus.CONNECTED ||
            !target.hasWirelessTransport
        ) {
            return false
        }

        val output = activeProtocolOutput ?: return false
        val credentials =
            UvirSensorCredentialStore.load(applicationContext)
        var current =
            SensorRadioSettings(
                wifiEnabled = credentials.wifiEnabled,
                bluetoothEnabled = credentials.bluetoothEnabled
            )
        val changes =
            orderedSensorRadioChanges(
                current = current,
                target = target,
                activeMode = state.mode
            )

        for (change in changes) {
            val expected =
                current.withMode(change.mode, change.enabled)
            val acknowledgement = CountDownLatch(1)
            expectedRadioSettings = expected
            radioSettingsAcknowledgement = acknowledgement

            val acknowledged =
                try {
                    val transportName =
                        when (change.mode) {
                            SensorConnectionMode.WIFI -> "WIFI"
                            SensorConnectionMode.BLUETOOTH -> "BLUETOOTH"
                            SensorConnectionMode.INTERNET -> return false
                            SensorConnectionMode.USB -> return false
                        }
                    writeLine(
                        output,
                        "RADIO $transportName " +
                            if (change.enabled) "ON" else "OFF"
                    )
                    acknowledgement.await(
                        RADIO_SETTINGS_ACK_TIMEOUT_MS,
                        TimeUnit.MILLISECONDS
                    )
                } catch (_: Exception) {
                    false
                } finally {
                    if (radioSettingsAcknowledgement === acknowledgement) {
                        radioSettingsAcknowledgement = null
                        expectedRadioSettings = null
                    }
                }

            if (!acknowledged) {
                return false
            }
            current = expected
        }

        return true
    }

    /**
     * Provisions Wi-Fi through an already authenticated Wi-Fi or Bluetooth
     * connection. Credentials are persisted locally only after the sensor
     * confirms that they were written to its non-volatile storage.
     */
    @Synchronized
    fun configureWifiNetwork(
        ssid: String,
        password: String
    ): Boolean {
        val configuration =
            validatedSensorWifiConfiguration(ssid, password)
                ?: return false
        val currentState = mutableState.value
        if (currentState.status != WirelessSensorConnectionStatus.CONNECTED) {
            return false
        }

        val output = activeProtocolOutput ?: return false
        val current = UvirSensorCredentialStore.load(applicationContext)
        if (!current.isProvisioned) {
            return false
        }

        val acknowledgement = CountDownLatch(1)
        wifiConfigurationAcknowledgement = acknowledgement
        val acknowledged =
            try {
                writeLine(output, configuration.protocolCommand)
                acknowledgement.await(
                    WIFI_CONFIGURATION_ACK_TIMEOUT_MS,
                    TimeUnit.MILLISECONDS
                )
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                false
            } catch (_: Exception) {
                false
            } finally {
                if (wifiConfigurationAcknowledgement === acknowledgement) {
                    wifiConfigurationAcknowledgement = null
                }
            }

        if (!acknowledged) {
            return false
        }

        UvirSensorCredentialStore.save(
            applicationContext,
            current.copy(
                wifiSsid = configuration.ssid,
                wifiPassword = configuration.password,
                wifiHost = ""
            )
        )
        return true
    }

    @Synchronized
    internal fun configureInternet(
        configuration: SensorInternetConfiguration
    ): Boolean {
        if (!configuration.isComplete) return false
        if (
            mutableState.value.status !=
                WirelessSensorConnectionStatus.CONNECTED
        ) {
            return false
        }
        val output = activeProtocolOutput ?: return false
        val current = UvirSensorCredentialStore.load(applicationContext)
        if (!current.isProvisioned) return false

        val acknowledgement = CountDownLatch(1)
        internetConfigurationAcknowledgement = acknowledgement
        val acknowledged =
            try {
                writeLine(output, configuration.protocolCommand)
                acknowledgement.await(
                    INTERNET_CONFIGURATION_ACK_TIMEOUT_MS,
                    TimeUnit.MILLISECONDS
                )
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                false
            } catch (_: Exception) {
                false
            } finally {
                if (internetConfigurationAcknowledgement === acknowledgement) {
                    internetConfigurationAcknowledgement = null
                }
            }
        if (!acknowledged) return false

        UvirSensorCredentialStore.save(
            applicationContext,
            current.withInternetConfiguration(configuration)
        )
        return true
    }

    fun sendSensorControlCommands(
        commands: List<String>
    ): Boolean {
        if (
            mutableState.value.status !=
            WirelessSensorConnectionStatus.CONNECTED
        ) {
            return false
        }
        val output = activeProtocolOutput ?: return false
        return runCatching {
            commands.forEach { writeLine(output, it) }
            true
        }.getOrDefault(false)
    }

    internal suspend fun diagnosticProbe(
        hardwareId: String,
        mode: SensorConnectionMode
    ): UvirDiagnosticProbe {
        val transportOutput = activeProtocolOutput
        return diagnosticClient.request(
            hardwareId = hardwareId,
            connectionValid = {
                val current = mutableState.value
                transportOutput != null && activeProtocolOutput === transportOutput &&
                    current.status == WirelessSensorConnectionStatus.CONNECTED &&
                    current.appConnectionConfirmed && current.mode == mode &&
                    current.deviceId.equals(hardwareId, ignoreCase = true)
            },
            send = { sendSensorControlCommands(listOf(it)) }
        )
    }

    fun stopAutomaticAcquisitionAndAwait(timeoutMs: Long): Boolean {
        if (
            mutableState.value.status !=
            WirelessSensorConnectionStatus.CONNECTED
        ) {
            return false
        }
        val output = activeProtocolOutput ?: return false
        val acknowledgement = CountDownLatch(1)
        automaticStopConfirmed = false
        automaticStopAcknowledgement = acknowledgement
        return try {
            writeLine(output, "OFFLINE_STOP")
            acknowledgement.await(
                timeoutMs.coerceIn(1_000L, 120_000L),
                TimeUnit.MILLISECONDS
            ) && automaticStopConfirmed
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            false
        } catch (_: Exception) {
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
        if (
            mutableState.value.status !=
            WirelessSensorConnectionStatus.CONNECTED
        ) {
            return false
        }
        val output = activeProtocolOutput ?: return false
        val acknowledgement = CountDownLatch(1)
        statusTestConfirmed = false
        statusTestExpectedField = expectedField
        statusTestAcknowledgement = acknowledgement
        return try {
            writeLine(output, command)
            acknowledgement.await(
                timeoutMs.coerceIn(1_000L, 15_000L),
                TimeUnit.MILLISECONDS
            ) && statusTestConfirmed
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            false
        } catch (_: Exception) {
            false
        } finally {
            if (statusTestAcknowledgement === acknowledgement) {
                statusTestAcknowledgement = null
                statusTestExpectedField = null
            }
        }
    }

    fun acknowledgeOfflineRecord(recordId: Long) {
        val output = activeProtocolOutput ?: return
        runCatching {
            offlineSyncLastProgressAtMs = SystemClock.elapsedRealtime()
            writeLine(output, "SYNC_ACK $recordId")
            Log.i("UvirSync", "WIRELESS ack id=$recordId")
        }.onFailure {
            Log.e("UvirSync", "WIRELESS ack failed id=$recordId", it)
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

    /** Disconnects every app-side wireless transport without changing the sensor. */
    @Synchronized
    fun disconnectForDisassociation() {
        desiredStreaming = false
        desiredSampling = false
        desiredMode = null
        generation.incrementAndGet()
        resetOfflineSyncState()
        closeTransport()
        worker?.cancel(true)
        worker = null
        mutableState.value = UvirWirelessSensorState()
    }

    /** Wait for the old reader to exit before changing the active profile. */
    fun disconnectForSensorSelection() {
        disconnectForDisassociation()
        executor.submit {}.get(5, TimeUnit.SECONDS)
    }

    fun stop() {
        desiredStreaming = false
        generation.incrementAndGet()
        closeTransport()
        worker?.cancel(true)
        executor.shutdownNow()
        mutableState.value = UvirWirelessSensorState()
    }

    @Synchronized
    private fun restartWorker() {
        val currentGeneration = generation.incrementAndGet()
        resetOfflineSyncState()
        closeTransport()
        worker?.cancel(true)

        val mode = desiredMode
        if (!desiredStreaming || mode == null) {
            mutableState.value = UvirWirelessSensorState(mode = mode)
            return
        }

        worker = executor.submit {
            connectionLoop(mode, currentGeneration)
        }
    }

    private fun connectionLoop(
        mode: SensorConnectionMode,
        currentGeneration: Int
    ) {
        while (
            desiredStreaming &&
            desiredMode == mode &&
            generation.get() == currentGeneration &&
            !Thread.currentThread().isInterrupted
        ) {
            val credentials = UvirSensorCredentialStore.load(applicationContext)
            if (!credentials.isProvisioned) {
                mutableState.value = UvirWirelessSensorState(
                    status = WirelessSensorConnectionStatus.PROVISIONING_REQUIRED,
                    mode = mode,
                    error = "sensor_provisioning_required"
                )
                return
            }

            mutableState.value = UvirWirelessSensorState(
                status = WirelessSensorConnectionStatus.CONNECTING,
                mode = mode
            )

            try {
                when (mode) {
                    SensorConnectionMode.WIFI ->
                        connectWifi(credentials, mode, currentGeneration)

                    SensorConnectionMode.BLUETOOTH ->
                        connectBluetooth(credentials, mode, currentGeneration)

                    SensorConnectionMode.INTERNET ->
                        connectInternet(credentials, mode, currentGeneration)

                    SensorConnectionMode.USB -> return
                }
            } catch (error: SecurityException) {
                mutableState.value = UvirWirelessSensorState(
                    status = WirelessSensorConnectionStatus.PERMISSION_REQUIRED,
                    mode = mode,
                    error = "bluetooth_permission_required"
                )
                return
            } catch (error: PairingRequiredException) {
                mutableState.value = UvirWirelessSensorState(
                    status = WirelessSensorConnectionStatus.PAIRING_REQUIRED,
                    mode = mode,
                    error = "bluetooth_pairing_required"
                )
                return
            } catch (error: Exception) {
                if (generation.get() != currentGeneration || !desiredStreaming) {
                    return
                }
                Log.e(
                    "UvirWireless",
                    "${mode.name} connection failed: " +
                        "${error.javaClass.simpleName}: ${error.message}",
                    error
                )
                UvirErrorLog.record(
                    applicationContext,
                    "wireless_${mode.name.lowercase()}",
                    error
                )
                mutableState.value = UvirWirelessSensorState(
                    status = WirelessSensorConnectionStatus.ERROR,
                    mode = mode,
                    error = error.message ?: "wireless_connection_error"
                )
            } finally {
                closeTransport()
            }

            if (generation.get() == currentGeneration && desiredStreaming) {
                Thread.sleep(RETRY_DELAY_MS)
            }
        }
    }

    private fun connectWifi(
        credentials: UvirSensorCredentials,
        mode: SensorConnectionMode,
        currentGeneration: Int
    ) {
        if (credentials.wifiSsid.isBlank()) {
            throw IllegalStateException("wifi_not_configured")
        }

        val discoveredHost = discoverWifiSensor(credentials)
            ?: throw IllegalStateException("wifi_sensor_not_found")

        if (discoveredHost != credentials.wifiHost) {
            UvirSensorCredentialStore.save(
                applicationContext,
                credentials.copy(wifiHost = discoveredHost)
            )
        }

        connectWifiAtHost(
            discoveredHost,
            credentials.copy(wifiHost = discoveredHost),
            mode,
            currentGeneration
        )
    }

    private fun connectWifiAtHost(
        host: String,
        credentials: UvirSensorCredentials,
        mode: SensorConnectionMode,
        currentGeneration: Int
    ) {
        val socket = Socket()
        tcpSocket = socket
        socket.tcpNoDelay = true
        socket.soTimeout = READ_TIMEOUT_MS
        socket.connect(
            InetSocketAddress(host, credentials.wifiPort),
            CONNECT_TIMEOUT_MS
        )

        runProtocol(
            input = BufferedInputStream(socket.getInputStream()),
            output = BufferedOutputStream(socket.getOutputStream()),
            credentials = credentials,
            mode = mode,
            currentGeneration = currentGeneration
        )
    }

    private fun connectInternet(
        credentials: UvirSensorCredentials,
        mode: SensorConnectionMode,
        currentGeneration: Int
    ) {
        if (
            !credentials.internetEnabled ||
            credentials.internetRelayHost.isBlank() ||
            credentials.internetRelayPort !in 1..65_535 ||
            credentials.internetMqttUsername.isBlank() ||
            credentials.internetMqttPassword.isBlank()
        ) {
            throw IllegalStateException("internet_not_configured")
        }

        val deviceTopicId = credentials.deviceId.lowercase()
        val commandTopic = "uvir/$deviceTopicId/commands"
        val eventTopic = "uvir/$deviceTopicId/events"
        val brokerUri =
            "ssl://${credentials.internetRelayHost}:${credentials.internetRelayPort}"
        val clientId =
            "uvir-app-${credentials.deviceId.takeLast(12).lowercase()}-" +
                UUID.randomUUID().toString().take(8)
        val input = PipedInputStream(MQTT_INPUT_BUFFER_SIZE)
        val incomingMessages = PipedOutputStream(input)
        val incomingLock = Any()
        val client = MqttClient(brokerUri, clientId, MemoryPersistence())
        mqttClient = client
        client.setCallback(
            object : MqttCallback {
                override fun connectionLost(cause: Throwable?) {
                    Log.w(
                        "UvirInternet",
                        "MQTT connection lost: " +
                            "${cause?.javaClass?.simpleName}: ${cause?.message}",
                        cause
                    )
                    runCatching { incomingMessages.close() }
                }

                override fun messageArrived(topic: String?, message: MqttMessage?) {
                    if (topic != eventTopic || message == null || message.isRetained) {
                        return
                    }
                    synchronized(incomingLock) {
                        incomingMessages.write(message.payload)
                        incomingMessages.write('\n'.code)
                        incomingMessages.flush()
                    }
                }

                override fun deliveryComplete(token: IMqttDeliveryToken?) = Unit
            }
        )
        val options =
            MqttConnectOptions().apply {
                isCleanSession = true
                isAutomaticReconnect = false
                connectionTimeout = MQTT_CONNECT_TIMEOUT_SECONDS
                keepAliveInterval = MQTT_KEEP_ALIVE_SECONDS
                userName = credentials.internetMqttUsername
                password = credentials.internetMqttPassword.toCharArray()
            }

        try {
            client.connect(options)
            Log.i("UvirInternet", "MQTT broker connection established")
            client.subscribe(eventTopic, MQTT_RELIABLE_QOS)
            Log.i("UvirInternet", "MQTT sensor event topic subscribed")
            runProtocol(
                input = input,
                output = MqttLineOutputStream(client, commandTopic),
                credentials = credentials,
                mode = mode,
                currentGeneration = currentGeneration
            )
        } finally {
            runCatching { incomingMessages.close() }
            runCatching { input.close() }
            closeMqttClient(client)
        }
    }

    private fun discoverWifiSensor(
        credentials: UvirSensorCredentials
    ): String? {
        val nonceBytes = ByteArray(DISCOVERY_NONCE_SIZE).also {
            SecureRandom().nextBytes(it)
        }
        val nonce = nonceBytes.toHex()
        val request =
            "UVIR_DISCOVER ${credentials.deviceId} $nonce"
                .toByteArray(StandardCharsets.US_ASCII)
        val expectedProof = hmacSha256(
            credentials.authToken,
            "DISCOVERY:${credentials.deviceId}:$nonce"
        )

        DatagramSocket().use { socket ->
            socket.broadcast = true
            socket.soTimeout = DISCOVERY_TIMEOUT_MS

            repeat(DISCOVERY_ATTEMPTS) {
                socket.send(
                    DatagramPacket(
                        request,
                        request.size,
                        InetAddress.getByName("255.255.255.255"),
                        credentials.wifiDiscoveryPort
                    )
                )

                val responseBytes = ByteArray(DISCOVERY_RESPONSE_SIZE)
                val response = DatagramPacket(
                    responseBytes,
                    responseBytes.size
                )

                try {
                    socket.receive(response)
                } catch (_: java.net.SocketTimeoutException) {
                    return@repeat
                }

                val json = runCatching {
                    JSONObject(
                        String(
                            response.data,
                            response.offset,
                            response.length,
                            StandardCharsets.UTF_8
                        )
                    )
                }.getOrNull() ?: return@repeat

                if (
                    json.optString("protocol") == PROTOCOL_NAME &&
                    json.optString("type") == "discovery" &&
                    json.optString("device_id") == credentials.deviceId &&
                    json.optString("nonce") == nonce &&
                    MessageDigest.isEqual(
                        json.optString("proof").hexToBytes(),
                        expectedProof
                    )
                ) {
                    return response.address.hostAddress
                }
            }
        }
        return null
    }

    private fun hmacSha256(secret: String, message: String): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(
            SecretKeySpec(
                secret.toByteArray(StandardCharsets.UTF_8),
                "HmacSHA256"
            )
        )
        return mac.doFinal(message.toByteArray(StandardCharsets.UTF_8))
    }

    private fun ByteArray.toHex(): String =
        joinToString("") { byte -> "%02x".format(byte.toInt() and 0xFF) }

    private fun String.hexToBytes(): ByteArray {
        if (length % 2 != 0) {
            return ByteArray(0)
        }
        return runCatching {
            ByteArray(length / 2) { index ->
                substring(index * 2, index * 2 + 2).toInt(16).toByte()
            }
        }.getOrDefault(ByteArray(0))
    }

    private fun connectBluetooth(
        credentials: UvirSensorCredentials,
        mode: SensorConnectionMode,
        currentGeneration: Int
    ) {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            throw SecurityException("bluetooth_permission_required")
        }

        val bluetoothManager =
            applicationContext.getSystemService(BluetoothManager::class.java)
        val adapter = bluetoothManager?.adapter
            ?: throw IllegalStateException("bluetooth_not_supported")

        if (!adapter.isEnabled) {
            throw IllegalStateException("bluetooth_disabled")
        }

        val device = adapter.bondedDevices.firstOrNull {
            it.name == credentials.bluetoothName
        } ?: throw PairingRequiredException()

        val socket = connectBluetoothSocket(device)

        runProtocol(
            input = BufferedInputStream(socket.inputStream),
            output = BufferedOutputStream(socket.outputStream),
            credentials = credentials,
            mode = mode,
            currentGeneration = currentGeneration
        )
    }

    private fun connectBluetoothSocket(
        device: BluetoothDevice
    ): BluetoothSocket {
        val standardSocket =
            device.createRfcommSocketToServiceRecord(SPP_UUID)
        bluetoothSocket = standardSocket

        try {
            standardSocket.connect()
            return standardSocket
        } catch (standardError: Exception) {
            runCatching { standardSocket.close() }
            bluetoothSocket = null

            if (Thread.currentThread().isInterrupted) {
                throw standardError
            }

            // Some Android Bluetooth stacks intermittently fail during SPP
            // service discovery after the ESP32 switches from Wi-Fi. Channel
            // 1 is the ESP32 SPP server and this remains a secure RFCOMM
            // socket; the Uvir token authentication is still applied above it.
            val directSocket =
                runCatching {
                    BluetoothDevice::class.java
                        .getMethod(
                            "createRfcommSocket",
                            Int::class.javaPrimitiveType
                        )
                        .invoke(device, 1) as BluetoothSocket
                }.getOrElse {
                    throw standardError
                }

            bluetoothSocket = directSocket
            return try {
                directSocket.connect()
                directSocket
            } catch (directError: Exception) {
                runCatching { directSocket.close() }
                bluetoothSocket = null
                standardError.addSuppressed(directError)
                throw standardError
            }
        }
    }

    private fun runProtocol(
        input: InputStream,
        output: OutputStream,
        credentials: UvirSensorCredentials,
        mode: SensorConnectionMode,
        currentGeneration: Int
    ) {
        activeProtocolOutput = output
        writeLine(output, "AUTH ${credentials.authToken}")

        val lineBuffer = StringBuilder()
        val inputBuffer = ByteArray(INPUT_BUFFER_SIZE)
        var lastPingAt = SystemClock.elapsedRealtime()
        var lastReceiveAt = lastPingAt
        var lastAuthenticationAt = lastPingAt

        while (
            desiredStreaming &&
            desiredMode == mode &&
            generation.get() == currentGeneration &&
            !Thread.currentThread().isInterrupted
        ) {
            val available = input.available()
            if (available > 0) {
                val readCount =
                    input.read(
                        inputBuffer,
                        0,
                        minOf(available, inputBuffer.size)
                    )
                if (readCount == -1) {
                    throw IllegalStateException("wireless_connection_closed")
                }
                lastReceiveAt = SystemClock.elapsedRealtime()
                repeat(readCount) { index ->
                    val character =
                        (inputBuffer[index].toInt() and 0xFF).toChar()
                    if (character == '\n' || character == '\r') {
                        if (lineBuffer.isNotEmpty()) {
                            parseLine(
                                line = lineBuffer.toString(),
                                mode = mode,
                                credentials = credentials
                            )
                            lineBuffer.clear()
                        }
                    } else if (lineBuffer.length < MAXIMUM_LINE_LENGTH) {
                        lineBuffer.append(character)
                    } else {
                        lineBuffer.clear()
                    }
                }
            } else {
                Thread.sleep(INPUT_POLL_INTERVAL_MS)
            }

            val now = SystemClock.elapsedRealtime()
            if (
                mode == SensorConnectionMode.INTERNET &&
                mutableState.value.status != WirelessSensorConnectionStatus.CONNECTED &&
                now - lastAuthenticationAt >= MQTT_AUTH_RETRY_INTERVAL_MS
            ) {
                writeLine(output, "AUTH ${credentials.authToken}")
                lastAuthenticationAt = now
            }
            if (now - lastPingAt >= PING_INTERVAL_MS) {
                writeLine(output, "PING")
                lastPingAt = now
            }

            if (
                offlineSyncRequested &&
                !offlineSyncCompleted &&
                now - offlineSyncLastProgressAtMs >=
                    OFFLINE_SYNC_RETRY_TIMEOUT_MS
            ) {
                // Replaying is safe: the database deduplicates records using
                // the stable identifier assigned by this sensor.
                offlineSyncLastProgressAtMs = now
                Log.w("UvirSync", "WIRELESS sync stalled; retrying")
                writeLine(output, "SYNC_BEGIN")
            }

            val staleTimeoutMs =
                if (
                    mode == SensorConnectionMode.INTERNET &&
                    mutableState.value.status !=
                        WirelessSensorConnectionStatus.CONNECTED
                ) {
                    MQTT_SENSOR_DISCOVERY_TIMEOUT_MS
                } else {
                    CONNECTION_STALE_TIMEOUT_MS
                }
            if (now - lastReceiveAt >= staleTimeoutMs) {
                throw IllegalStateException("wireless_connection_timeout")
            }
        }

        runCatching { writeLine(output, "STOP") }
    }

    private fun parseLine(
        line: String,
        mode: SensorConnectionMode,
        credentials: UvirSensorCredentials
    ) {
        val json = runCatching { JSONObject(line) }.getOrNull() ?: return
        if (json.optString("protocol") != PROTOCOL_NAME) {
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
        acknowledgeInternetConfiguration(json)
        val isSynchronizationFrame =
            frameType == "sync_start" ||
            frameType == "offline_record" ||
            frameType == "sync_complete" ||
            frameType == "sync_failed"

        // When USB is selected without a cable, Android deliberately keeps
        // one authenticated wireless channel available only as a recovery
        // path. That standby channel must never consume queued records or
        // produce a synchronization popup: synchronization belongs solely to
        // the source explicitly selected by the user.
        if (isSynchronizationFrame && !desiredSampling) {
            Log.i("UvirSync", "WIRELESS standby sync frame ignored")
            return
        }

        if (isSynchronizationFrame) {
            offlineSyncLastProgressAtMs = SystemClock.elapsedRealtime()
            if (frameType == "sync_complete" || frameType == "sync_failed") {
                offlineSyncCompleted = true
            }
        }

        if (parseSensorSyncFrame(json, SensorSyncSource.WIRELESS)) {
            return
        }
        if (
            desiredSampling &&
            parseSensorRuntimeFrame(json, SensorSyncSource.WIRELESS)
        ) {
            return
        }

        when (frameType) {
            "hello" -> {
                acknowledgeWifiConfiguration(json)
                val reportedDeviceId =
                    json.optString("device_id")
                if (
                    reportedDeviceId.isNotBlank() &&
                    reportedDeviceId != credentials.deviceId
                ) {
                    mutableState.value =
                        UvirWirelessSensorState(
                            status =
                                WirelessSensorConnectionStatus.ERROR,
                            mode = mode,
                            error = "sensor_identity_mismatch"
                        )
                    return
                }

                if (!json.optBoolean("sensor_available", true)) {
                    mutableState.value = UvirWirelessSensorState(
                        status = WirelessSensorConnectionStatus.ERROR,
                        mode = mode,
                        sensorName = json.optString("sensor", "AS7343"),
                        error = "sensor_not_found"
                    )
                    return
                }

                val firmwareVersion =
                    json.optString("firmware").ifBlank {
                        credentials.firmwareVersion
                    }
                // A protocol connection carries a credentials snapshot. A
                // settings save may have produced a newer durable copy while
                // this connection remained alive, so never merge a HELLO into
                // the stale snapshot or MQTT credentials could be rolled back.
                val currentCredentials =
                    UvirSensorCredentialStore.load(applicationContext)
                val updatedCredentials =
                    currentCredentials.copy(
                        firmwareVersion = firmwareVersion,
                        wifiEnabled =
                            json.optBoolean(
                                "wifi_enabled",
                                currentCredentials.wifiEnabled
                            ),
                        bluetoothEnabled =
                            json.optBoolean(
                                "bluetooth_enabled",
                                currentCredentials.bluetoothEnabled
                            ),
                        internetEnabled =
                            json.optBoolean(
                                "internet_enabled",
                                currentCredentials.internetEnabled
                            ),
                        internetUsePrimaryWifi =
                            json.optBoolean(
                                "internet_use_primary_wifi",
                                currentCredentials.internetUsePrimaryWifi
                            ),
                        internetRelayHost =
                            json.optString(
                                "internet_relay_host",
                                currentCredentials.internetRelayHost
                            ),
                        internetRelayPort =
                            json.optInt(
                                "internet_relay_port",
                                currentCredentials.internetRelayPort
                            )
                    )

                if (updatedCredentials != currentCredentials) {
                    UvirSensorCredentialStore.save(
                        applicationContext,
                        updatedCredentials
                    )
                }

                mutableState.value = mutableState.value.copy(
                    status = WirelessSensorConnectionStatus.CONNECTED,
                    mode = mode,
                    deviceId = credentials.deviceId,
                    sensorName = json.optString("sensor", "AS7343"),
                    firmwareVersion =
                        firmwareVersion.ifBlank { null },
                    runtimeInfo =
                        mutableState.value.runtimeInfo.updatedFrom(json),
                    calibrationProvisional =
                        json.optString("calibration") != "calibrated",
                    uvAvailable = json.optBoolean("uv_available", false),
                    error = null
                )
                if (
                    desiredSampling &&
                    !offlineSyncRequested &&
                    !offlineSyncCompleted
                ) {
                    activeProtocolOutput?.let { output ->
                        offlineSyncRequested = true
                        offlineSyncLastProgressAtMs =
                            SystemClock.elapsedRealtime()
                        writeLine(output, "APP_CONNECT")
                        writeLine(output, sensorTimeCommand())
                        writeLine(output, "SYNC_BEGIN")
                        writeLine(
                            output,
                            if (desiredSampling) {
                                "STREAM $desiredIntervalMs"
                            } else {
                                "STOP"
                            }
                        )
                        Log.i("UvirSync", "WIRELESS sync requested")
                    }
                } else if (
                    !desiredSampling &&
                    !offlineSyncRequested &&
                    !offlineSyncCompleted
                ) {
                    // The connection is alive only to allow a later switch
                    // away from unavailable USB. Explicitly stop any stream
                    // left by the previous source, but do not synchronize.
                    offlineSyncCompleted = true
                    activeProtocolOutput?.let { output ->
                        writeLine(output, "STOP")
                        Log.i("UvirSync", "WIRELESS standby; stream stopped")
                    }
                }
            }

            "sample" -> {
                if (desiredSampling) {
                    parseSample(json, mode)
                }
            }
            "alert_event" -> {
                if (desiredSampling) {
                    parseAlertEvent(json)
                }
            }
            "status" -> {
                acknowledgeWifiConfiguration(json)
                persistReportedRadioAvailability(json)
                acknowledgeRadioSettings(json)
                acknowledgeWirelessMode(json)
                val previous = mutableState.value
                mutableState.value =
                    previous.copy(
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
                            }
                    )
            }
            "error" -> {
                val code = json.optString("code", "sensor_error")
                if (
                    code == "auth_required" &&
                    mode == SensorConnectionMode.INTERNET
                ) {
                    // The ESP32 deliberately clears its Uvir authentication
                    // whenever its MQTT session is recreated. The broker link
                    // on Android can remain alive across that event, so answer
                    // the request immediately instead of dropping the whole
                    // Internet connection and waiting for another retry cycle.
                    activeProtocolOutput?.let { output ->
                        runCatching {
                            writeLine(output, "AUTH ${credentials.authToken}")
                        }
                    }
                    Log.i(
                        "UvirInternet",
                        "Sensor requested Uvir re-authentication"
                    )
                    return
                }
                Log.e(
                    "UvirSync",
                    "WIRELESS sensor error $code: " +
                        json.optString("message", code)
                )
                UvirErrorLog.record(
                    context = applicationContext,
                    source = "sensor_${mode.name.lowercase()}:$code",
                    message = json.optString("message", code)
                )
                mutableState.value = mutableState.value.copy(
                    status = WirelessSensorConnectionStatus.ERROR,
                    mode = mode,
                    error = code
                )
            }
        }
    }

    private fun parseAlertEvent(json: JSONObject) {
        val details = json.optString("details")
        if (details.isBlank()) return
        Log.i("UvirAlert", "Wireless live alert event received")
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

    private fun persistReportedRadioAvailability(json: JSONObject) {
        if (
            !json.has("wifi_enabled") &&
            !json.has("bluetooth_enabled") &&
            !json.has("internet_enabled") &&
            !json.has("internet_relay_host")
        ) {
            return
        }
        // The credentials passed to the long-running protocol loop are a
        // connection snapshot. Internet settings can be changed while that
        // connection is still active, so merging a status frame into that
        // snapshot would restore stale values (most notably blank MQTT
        // credentials) immediately after a successful configuration save.
        // Always merge sensor-reported availability into the latest durable
        // copy instead.
        val current = UvirSensorCredentialStore.load(applicationContext)
        val updated =
            current.copy(
                wifiEnabled =
                    json.optBoolean(
                        "wifi_enabled",
                        current.wifiEnabled
                    ),
                bluetoothEnabled =
                    json.optBoolean(
                        "bluetooth_enabled",
                        current.bluetoothEnabled
                    ),
                internetEnabled =
                    json.optBoolean(
                        "internet_enabled",
                        current.internetEnabled
                    ),
                internetUsePrimaryWifi =
                    json.optBoolean(
                        "internet_use_primary_wifi",
                        current.internetUsePrimaryWifi
                    ),
                internetRelayHost =
                    json.optString(
                        "internet_relay_host",
                        current.internetRelayHost
                    ),
                internetRelayPort =
                    json.optInt(
                        "internet_relay_port",
                        current.internetRelayPort
                    )
            )
        if (updated != current) {
            UvirSensorCredentialStore.save(
                applicationContext,
                updated
            )
        }
    }

    private fun acknowledgeWirelessMode(json: JSONObject) {
        val reportedMode =
            when (json.optString("wireless_mode").lowercase()) {
                "wifi" -> SensorConnectionMode.WIFI
                "bluetooth" -> SensorConnectionMode.BLUETOOTH
                "internet" -> SensorConnectionMode.INTERNET
                "off" -> SensorConnectionMode.USB
                else -> null
            }

        if (reportedMode == expectedWirelessMode) {
            wirelessModeAcknowledgement?.countDown()
        }
    }

    private fun acknowledgeRadioSettings(json: JSONObject) {
        val expected = expectedRadioSettings ?: return
        if (
            json.optBoolean("wifi_enabled", !expected.wifiEnabled) ==
                expected.wifiEnabled &&
            json.optBoolean(
                "bluetooth_enabled",
                !expected.bluetoothEnabled
            ) == expected.bluetoothEnabled
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

    private fun parseSample(json: JSONObject, mode: SensorConnectionMode) {
        val sample = json.toUvirSensorSampleOrNull() ?: return

        mutableState.value = mutableState.value.copy(
            status = WirelessSensorConnectionStatus.CONNECTED,
            mode = mode,
            appConnectionConfirmed = true,
            sample = sample,
            sampleSequence = json.optLong(
                "seq",
                mutableState.value.sampleSequence + 1L
            ),
            runtimeInfo =
                mutableState.value.runtimeInfo.updatedFrom(json),
            calibrationProvisional =
                json.optString("calibration") != "calibrated",
            uvAvailable = json.optBoolean("uv_available", false),
            saturated = json.optBoolean("saturated", false),
            error = null
        )
    }

    private fun writeLine(output: OutputStream, value: String) {
        synchronized(outputWriteLock) {
            output.write("$value\n".toByteArray(StandardCharsets.US_ASCII))
            output.flush()
        }
    }

    @Synchronized
    private fun closeTransport() {
        automaticStopConfirmed = false
        automaticStopAcknowledgement?.countDown()
        statusTestConfirmed = false
        statusTestAcknowledgement?.countDown()
        activeProtocolOutput = null
        resetOfflineSyncState()
        closeTcpSocket()
        closeMqttClient()
        runCatching { bluetoothSocket?.close() }
        bluetoothSocket = null
    }

    private fun resetOfflineSyncState() {
        offlineSyncRequested = false
        offlineSyncCompleted = false
        offlineSyncLastProgressAtMs = 0L
    }

    private fun closeTcpSocket() {
        runCatching { tcpSocket?.close() }
        tcpSocket = null
    }

    @Synchronized
    private fun closeMqttClient(expected: MqttClient? = null) {
        val client = mqttClient ?: return
        if (expected != null && client !== expected) return
        mqttClient = null
        runCatching {
            if (client.isConnected) {
                client.disconnectForcibly(250L, 250L, false)
            }
        }
        runCatching { client.close() }
    }

    private class MqttLineOutputStream(
        private val client: MqttClient,
        private val topic: String
    ) : OutputStream() {
        private val line = ByteArrayOutputStream()

        @Synchronized
        override fun write(value: Int) {
            if (value == '\n'.code || value == '\r'.code) {
                publishLine()
            } else if (line.size() < MAXIMUM_LINE_LENGTH) {
                line.write(value)
            } else {
                line.reset()
                throw IOException("mqtt_command_too_long")
            }
        }

        @Synchronized
        override fun flush() {
            publishLine()
        }

        private fun publishLine() {
            if (line.size() == 0) return
            if (!client.isConnected) {
                line.reset()
                throw IOException("internet_mqtt_disconnected")
            }
            val payload = line.toByteArray()
            line.reset()
            client.publish(
                topic,
                MqttMessage(payload).apply {
                    qos = MQTT_RELIABLE_QOS
                    isRetained = false
                }
            )
        }
    }

    private class PairingRequiredException : Exception()

    companion object {
        private const val PROTOCOL_NAME = UVIR_SENSOR_PROTOCOL
        private const val DEFAULT_STREAM_INTERVAL_MS = DEFAULT_SAMPLE_SPACING_MS
        private const val MINIMUM_STREAM_INTERVAL_MS = 150L
        private const val MAXIMUM_STREAM_INTERVAL_MS = 5000L
        private const val CONNECT_TIMEOUT_MS = 3500
        private const val READ_TIMEOUT_MS = 1000
        private const val DISCOVERY_TIMEOUT_MS = 900
        private const val DISCOVERY_ATTEMPTS = 3
        private const val DISCOVERY_RESPONSE_SIZE = 512
        private const val DISCOVERY_NONCE_SIZE = 16
        private const val RETRY_DELAY_MS = 2500L
        private const val PING_INTERVAL_MS = 4000L
        private const val CONNECTION_STALE_TIMEOUT_MS = 10000L
        private const val INPUT_BUFFER_SIZE = 2048
        private const val INPUT_POLL_INTERVAL_MS = 20L
        private const val OFFLINE_SYNC_RETRY_TIMEOUT_MS = 12_000L
        private const val WIRELESS_MODE_ACK_TIMEOUT_MS = 1500L
        private const val MAXIMUM_WIRELESS_MODE_ACK_TIMEOUT_MS = 120_000L
        private const val RADIO_SETTINGS_ACK_TIMEOUT_MS = 1500L
        private const val WIFI_CONFIGURATION_ACK_TIMEOUT_MS = 2000L
        private const val INTERNET_CONFIGURATION_ACK_TIMEOUT_MS = 3000L
        private const val MQTT_CONNECT_TIMEOUT_SECONDS = 8
        private const val MQTT_KEEP_ALIVE_SECONDS = 15
        private const val MQTT_RELIABLE_QOS = 1
        private const val MQTT_INPUT_BUFFER_SIZE = 65_536
        private const val MQTT_AUTH_RETRY_INTERVAL_MS = 2500L
        private const val MQTT_SENSOR_DISCOVERY_TIMEOUT_MS = 30_000L
        private const val MAXIMUM_LINE_LENGTH = 16384
        private val SPP_UUID: UUID =
            UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }
}
