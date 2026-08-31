package me.mondiversi.uvir

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Collections
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

internal const val UVIR_REMOTE_PORT = 45871
internal const val UVIR_REMOTE_PREFS = "uvir_remote"
private const val UVIR_REMOTE_PIN = "pairing_pin"
private const val MAX_REQUEST_CHARS = 8 * 1024 * 1024

internal data class UvirRemoteSnapshot(
    val acquisition: SensorSample = SensorSample(),
    val liveReady: Boolean = false,
    val autoEnabled: Boolean = false,
    val autoIntervalSeconds: Long = 0L,
    val autoCompletedCount: Int = 0,
    val autoLimitEnabled: Boolean = false,
    val autoMaxCount: Int = 0,
    val autoNextSaveMs: Long = 0L,
    val screen: AppScreen = AppScreen.LIVE
)

internal data class UvirRemoteCommand(
    val action: String,
    val request: JSONObject,
    val response: CompletableDeferred<JSONObject>
)

internal object UvirRemoteRuntime {
    val commands =
        Channel<UvirRemoteCommand>(
            capacity = Channel.UNLIMITED
        )

    val snapshot =
        AtomicReference(
            UvirRemoteSnapshot()
        )

    val directNetworkEnabled =
        AtomicReference(false)

    val serverError =
        AtomicReference<String?>(null)
}

internal fun getOrCreateRemotePin(
    context: Context
): String {
    val preferences =
        context.getSharedPreferences(
            UVIR_REMOTE_PREFS,
            Context.MODE_PRIVATE
        )

    preferences.getString(
        UVIR_REMOTE_PIN,
        null
    )?.takeIf {
        it.length == 8 &&
                it.all(Char::isDigit)
    }?.let {
        return it
    }

    val pin =
        SecureRandom()
            .nextInt(100_000_000)
            .toString()
            .padStart(8, '0')

    preferences.edit()
        .putString(
            UVIR_REMOTE_PIN,
            pin
        )
        .apply()

    return pin
}

internal enum class UvirNetworkKind {
    WIFI,
    BLUETOOTH,
    USB,
    MOBILE,
    OTHER
}

internal data class UvirNetworkAddress(
    val address: String,
    val kind: UvirNetworkKind,
    val interfaceName: String
)

internal fun localIpv4Addresses(
    context: Context
): List<UvirNetworkAddress> =
    runCatching {
        val connectivityManager =
            context.getSystemService(
                ConnectivityManager::class.java
            )

        val primaryMobileInterface =
            connectivityManager
                ?.allNetworks
                ?.mapNotNull { network ->
                    val capabilities =
                        connectivityManager
                            .getNetworkCapabilities(
                                network
                            ) ?: return@mapNotNull null

                    if (
                        !capabilities.hasTransport(
                            NetworkCapabilities
                                .TRANSPORT_CELLULAR
                        ) ||
                        !capabilities.hasCapability(
                            NetworkCapabilities
                                .NET_CAPABILITY_INTERNET
                        )
                    ) {
                        return@mapNotNull null
                    }

                    val interfaceName =
                        connectivityManager
                            .getLinkProperties(
                                network
                            )
                            ?.interfaceName
                            ?: return@mapNotNull null

                    interfaceName to
                        capabilities.hasCapability(
                            NetworkCapabilities
                                .NET_CAPABILITY_VALIDATED
                        )
                }
                ?.sortedByDescending {
                    it.second
                }
                ?.firstOrNull()
                ?.first

        Collections.list(
            NetworkInterface
                .getNetworkInterfaces()
        )
            .filter {
                it.isUp &&
                        !it.isLoopback
            }
            .flatMap { network ->
                val interfaceName =
                    "${network.name} ${network.displayName}"
                        .lowercase(Locale.ROOT)

                val kind =
                    when {
                        interfaceName.contains("wlan") ||
                                interfaceName.contains("wifi") ->
                            UvirNetworkKind.WIFI

                        interfaceName.contains("bnep") ||
                                interfaceName.contains("bt-pan") ||
                                interfaceName.contains("bluetooth") ->
                            UvirNetworkKind.BLUETOOTH

                        interfaceName.contains("rndis") ||
                                interfaceName.contains("usb") ->
                            UvirNetworkKind.USB

                        interfaceName.contains("rmnet") ||
                                interfaceName.contains("ccmni") ||
                                interfaceName.contains("pdp") ||
                                interfaceName.contains("wwan") ->
                            UvirNetworkKind.MOBILE

                        else ->
                            UvirNetworkKind.OTHER
                    }

                Collections.list(
                    network.inetAddresses
                )
                    .filterIsInstance<Inet4Address>()
                    .filter {
                        !it.isLoopbackAddress &&
                                !it.isLinkLocalAddress
                    }
                    .mapNotNull { address ->
                        address.hostAddress?.let {
                            UvirNetworkAddress(
                                address = it,
                                kind = kind,
                                interfaceName =
                                    network.name
                            )
                        }
                    }
            }
            .filter {
                it.kind != UvirNetworkKind.MOBILE ||
                        primaryMobileInterface == null ||
                        it.interfaceName ==
                        primaryMobileInterface
            }
            .distinctBy { it.address }
            .sortedWith(
                compareBy<UvirNetworkAddress> { it.kind.ordinal }
                    .thenBy { it.address }
            )
    }.getOrDefault(emptyList())

internal class UvirRemoteServer(
    private val context: Context
) {
    private val generation =
        AtomicInteger(0)

    @Volatile
    private var serverSocket: ServerSocket? = null

    fun start(
        directNetwork: Boolean
    ) {
        val currentGeneration =
            generation.incrementAndGet()

        runCatching {
            serverSocket?.close()
        }

        UvirRemoteRuntime
            .directNetworkEnabled
            .set(directNetwork)

        Thread(
            {
                runServer(
                    currentGeneration,
                    directNetwork
                )
            },
            "UvirRemoteServer"
        ).apply {
            isDaemon = true
            start()
        }
    }

    fun stop() {
        generation.incrementAndGet()

        runCatching {
            serverSocket?.close()
        }

        serverSocket = null
    }

    private fun runServer(
        currentGeneration: Int,
        directNetwork: Boolean
    ) {
        try {
            val socket =
                ServerSocket().apply {
                    reuseAddress = true

                    bind(
                        if (directNetwork) {
                            InetSocketAddress(
                                UVIR_REMOTE_PORT
                            )
                        } else {
                            InetSocketAddress(
                                InetAddress
                                    .getLoopbackAddress(),
                                UVIR_REMOTE_PORT
                            )
                        }
                    )
                }

            serverSocket = socket
            UvirRemoteRuntime.serverError.set(null)

            while (
                currentGeneration ==
                generation.get()
            ) {
                val client =
                    try {
                        socket.accept()
                    } catch (_: Exception) {
                        break
                    }

                Thread(
                    {
                        handleClient(client)
                    },
                    "UvirRemoteClient"
                ).apply {
                    isDaemon = true
                    start()
                }
            }
        } catch (error: Exception) {
            if (
                currentGeneration ==
                generation.get()
            ) {
                UvirRemoteRuntime
                    .serverError
                    .set(
                        error.message ?:
                        error.javaClass.simpleName
                    )
            }
        }
    }

    private fun handleClient(
        socket: Socket
    ) {
        socket.use { client ->
            client.soTimeout = 15_000

            val reader =
                BufferedReader(
                    InputStreamReader(
                        client.getInputStream(),
                        Charsets.UTF_8
                    )
                )

            val writer =
                BufferedWriter(
                    OutputStreamWriter(
                        client.getOutputStream(),
                        Charsets.UTF_8
                    )
                )

            val response =
                try {
                    val line =
                        reader.readLine()
                            ?: throw IllegalArgumentException(
                                "Richiesta vuota."
                            )

                    if (
                        line.length >
                        MAX_REQUEST_CHARS
                    ) {
                        throw IllegalArgumentException(
                            "Richiesta troppo grande."
                        )
                    }

                    val request =
                        JSONObject(line)

                    authenticate(
                        request,
                        client.inetAddress
                    )

                    dispatch(request)
                } catch (error: Exception) {
                    remoteError(
                        error.message ?:
                        error.javaClass.simpleName
                    )
                }

            writer.write(
                response.toString()
            )
            writer.newLine()
            writer.flush()
        }
    }

    private fun authenticate(
        request: JSONObject,
        remoteAddress: InetAddress
    ) {
        // ADB port forwarding reaches the app through loopback and is already
        // protected by Android's debugging authorization. Direct LAN/PAN
        // connections additionally require the pairing PIN shown in Uvir.
        if (remoteAddress.isLoopbackAddress) {
            return
        }

        val suppliedPin =
            request.optString(
                "pin",
                ""
            )

        val expectedPin =
            getOrCreateRemotePin(context)

        val valid =
            MessageDigest.isEqual(
                suppliedPin.toByteArray(
                    Charsets.UTF_8
                ),
                expectedPin.toByteArray(
                    Charsets.UTF_8
                )
            )

        if (!valid) {
            throw SecurityException(
                "Codice di abbinamento non valido."
            )
        }
    }

    private fun dispatch(
        request: JSONObject
    ): JSONObject {
        val action =
            request.optString(
                "action",
                ""
            ).trim()

        if (action.isBlank()) {
            return remoteError(
                "Azione mancante."
            )
        }

        if (
            action == "ping" ||
            action == "status"
        ) {
            return remoteOk(
                remoteStatusJson()
            )
        }

        val response =
            CompletableDeferred<JSONObject>()

        if (
            !UvirRemoteRuntime.commands
                .trySend(
                    UvirRemoteCommand(
                        action = action,
                        request = request,
                        response = response
                    )
                ).isSuccess
        ) {
            return remoteError(
                "Interfaccia Uvir non disponibile."
            )
        }

        return runBlocking {
            withTimeoutOrNull(20_000L) {
                response.await()
            } ?: remoteError(
                "Tempo scaduto in attesa dell'app."
            )
        }
    }
}

internal fun remoteOk(
    data: JSONObject = JSONObject()
): JSONObject =
    JSONObject()
        .put("ok", true)
        .put("data", data)

internal fun remoteError(
    message: String
): JSONObject =
    JSONObject()
        .put("ok", false)
        .put("error", message)

internal fun remoteStatusJson(): JSONObject {
    val snapshot =
        UvirRemoteRuntime.snapshot.get()

    return JSONObject()
        .put("package", BuildConfig.APPLICATION_ID)
        .put("version", BuildConfig.VERSION_NAME)
        .put("port", UVIR_REMOTE_PORT)
        .put(
            "direct_network_enabled",
            UvirRemoteRuntime
                .directNetworkEnabled
                .get()
        )
        .put(
            "server_error",
            UvirRemoteRuntime
                .serverError
                .get() ?: JSONObject.NULL
        )
        .put("live_ready", snapshot.liveReady)
        .put("screen", snapshot.screen.name.lowercase())
        .put(
            "acquisition",
            sensorSampleToJson(
                snapshot.acquisition
            )
        )
        .put("auto_enabled", snapshot.autoEnabled)
        .put(
            "auto_interval_seconds",
            snapshot.autoIntervalSeconds
        )
        .put(
            "auto_completed_count",
            snapshot.autoCompletedCount
        )
        .put(
            "auto_limit_enabled",
            snapshot.autoLimitEnabled
        )
        .put(
            "auto_max_count",
            snapshot.autoMaxCount
        )
        .put(
            "auto_next_save_ms",
            snapshot.autoNextSaveMs
        )
}

internal fun sensorSampleToJson(
    sample: SensorSample
): JSONObject =
    JSONObject()
        .put("uvc", sample.uvc)
        .put("uvb", sample.uvb)
        .put("uva", sample.uva)
        .put("violetto", sample.violetto)
        .put("blu", sample.blu)
        .put("verde", sample.verde)
        .put("giallo", sample.giallo)
        .put("arancione", sample.arancione)
        .put("rosso", sample.rosso)
        .put("f8", sample.f8)
        .put("nir", sample.nir)

internal fun JSONObject.toSensorSample(): SensorSample =
    SensorSample(
        uvc = optDouble("uvc", 0.0),
        uvb = optDouble("uvb", 0.0),
        uva = optDouble("uva", 0.0),
        violetto = optDouble("violetto", 0.0),
        blu = optDouble("blu", 0.0),
        verde = optDouble("verde", 0.0),
        giallo = optDouble("giallo", 0.0),
        arancione = optDouble("arancione", 0.0),
        rosso = optDouble("rosso", 0.0),
        f8 = optDouble("f8", 0.0),
        nir = optDouble("nir", 0.0)
    )

internal fun savedRecordToJson(
    record: SavedRecordDetail
): JSONObject =
    JSONObject()
        .put("id", record.id)
        .put("timestamp", record.timestamp)
        .put("note", record.note)
        .put("automatic", record.automatic)
        .put(
            "automatic_session_id",
            record.automaticSessionId
                ?: JSONObject.NULL
        )
        .put(
            "automatic_sequence",
            record.automaticSequence
                ?: JSONObject.NULL
        )
        .put(
            "sample",
            sensorSampleToJson(
                record.sample
            )
        )

internal fun JSONObject.toSavedRecordDetail(): SavedRecordDetail {
    val id =
        optLong("id", 0L)

    if (id <= 0L) {
        throw IllegalArgumentException(
            "ID acquisizione non valido."
        )
    }

    return SavedRecordDetail(
        id = id,
        timestamp =
            optLong(
                "timestamp",
                System.currentTimeMillis()
            ),
        note = optString("note", ""),
        automatic = optBoolean("automatic", false),
        sample =
            optJSONObject("sample")
                ?.toSensorSample()
                ?: SensorSample(),
        automaticSessionId =
            optLong(
                "automatic_session_id",
                0L
            ).takeIf { it > 0L },
        automaticSequence =
            optInt(
                "automatic_sequence",
                0
            ).takeIf { it > 0 }
    )
}

internal fun recordsToJson(
    records: List<SavedRecordDetail>
): JSONObject {
    val array = JSONArray()

    records.forEach {
        array.put(
            savedRecordToJson(it)
        )
    }

    return JSONObject()
        .put("count", records.size)
        .put("records", array)
}
