package me.mondiversi.uvir

import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

internal const val UVIR_DIAGNOSTIC_REQUESTS = 6
internal const val UVIR_DIAGNOSTIC_MINIMUM_FIRMWARE = "0.5.69"

internal enum class UvirDiagnosticOutcome {
    SUCCESS, TIMEOUT, WRITE_FAILED, CONNECTION_CHANGED
}

internal data class UvirDiagnosticProbe(
    val outcome: UvirDiagnosticOutcome,
    val responseMs: Double? = null,
    val info: UvirSensorRuntimeInfo? = null
)

internal data class UvirSensorDiagnosticReport(
    val startedAtMs: Long,
    val durationMs: Long,
    val sensorName: String,
    val connectionMode: SensorConnectionMode,
    val initialInfo: UvirSensorRuntimeInfo,
    val probes: List<UvirDiagnosticProbe>
) {
    val responseTimes: List<Double> get() = probes.mapNotNull { it.responseMs }
    val responses: Int get() = probes.count { it.outcome == UvirDiagnosticOutcome.SUCCESS }
    val latestInfo: UvirSensorRuntimeInfo? get() = probes.lastOrNull { it.info != null }?.info
    val successful: Boolean get() = probes.size == UVIR_DIAGNOSTIC_REQUESTS && responses == probes.size
}

/** One correlated, read-only request at a time. Ordinary HELLO/PING replies cannot
 * complete a probe. Latency ends at receipt, not when the UI gets scheduled. */
internal class UvirSensorDiagnosticClient {
    private data class Pending(
        val id: String,
        val hardwareId: String,
        val startedNanos: Long,
        val reply: CompletableDeferred<UvirDiagnosticProbe>
    )

    private val requestMutex = Mutex()
    private val lock = Any()
    private var pending: Pending? = null

    suspend fun request(
        hardwareId: String,
        connectionValid: () -> Boolean,
        send: (String) -> Boolean,
        timeoutMs: Long = 2_000L
    ): UvirDiagnosticProbe = requestMutex.withLock {
        if (hardwareId.isBlank() || !connectionValid()) {
            return@withLock UvirDiagnosticProbe(UvirDiagnosticOutcome.CONNECTION_CHANGED)
        }
        val request = Pending(
            UUID.randomUUID().toString().replace("-", "").uppercase(),
            hardwareId,
            System.nanoTime(),
            CompletableDeferred()
        )
        synchronized(lock) { pending = request }
        try {
            val sent = withContext(Dispatchers.IO) {
                connectionValid() && send("DIAGNOSTIC ${request.id}")
            }
            if (!sent) {
                return@withLock UvirDiagnosticProbe(
                    if (connectionValid()) UvirDiagnosticOutcome.WRITE_FAILED
                    else UvirDiagnosticOutcome.CONNECTION_CHANGED
                )
            }
            val result = withTimeoutOrNull(timeoutMs) { request.reply.await() }
                ?: UvirDiagnosticProbe(UvirDiagnosticOutcome.TIMEOUT)
            if (connectionValid()) result
            else UvirDiagnosticProbe(UvirDiagnosticOutcome.CONNECTION_CHANGED)
        } finally {
            synchronized(lock) { if (pending === request) pending = null }
        }
    }

    fun receive(requestId: String, info: UvirSensorRuntimeInfo) {
        val request = synchronized(lock) { pending } ?: return
        if (request.id != requestId) return
        val result = if (info.deviceId.equals(request.hardwareId, ignoreCase = true)) {
            UvirDiagnosticProbe(
                UvirDiagnosticOutcome.SUCCESS,
                (System.nanoTime() - request.startedNanos) / 1_000_000.0,
                info
            )
        } else {
            UvirDiagnosticProbe(UvirDiagnosticOutcome.CONNECTION_CHANGED)
        }
        request.reply.complete(result)
    }
}

internal suspend fun runUvirSensorDiagnostics(
    sensorName: String,
    connectionMode: SensorConnectionMode,
    initialInfo: UvirSensorRuntimeInfo,
    onProgress: (Int) -> Unit,
    probe: suspend () -> UvirDiagnosticProbe,
    spacingMs: Long = 700L
): UvirSensorDiagnosticReport {
    val startedAt = System.currentTimeMillis()
    val startedNanos = System.nanoTime()
    val probes = mutableListOf<UvirDiagnosticProbe>()
    repeat(UVIR_DIAGNOSTIC_REQUESTS) { index ->
        val result = probe()
        probes += result
        onProgress(probes.size)
        if (result.outcome == UvirDiagnosticOutcome.CONNECTION_CHANGED) {
            return UvirSensorDiagnosticReport(
                startedAt, (System.nanoTime() - startedNanos) / 1_000_000,
                sensorName, connectionMode, initialInfo, probes.toList()
            )
        }
        if (index < UVIR_DIAGNOSTIC_REQUESTS - 1) delay(spacingMs)
    }
    return UvirSensorDiagnosticReport(
        startedAt, (System.nanoTime() - startedNanos) / 1_000_000,
        sensorName, connectionMode, initialInfo, probes.toList()
    )
}
