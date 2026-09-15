package me.mondiversi.uvir

import android.util.Log
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import org.json.JSONObject

internal enum class SensorSyncSource {
    USB,
    WIRELESS
}

internal fun sensorSyncSourceMatchesSelection(
    source: SensorSyncSource,
    selectedMode: SensorConnectionMode,
    selectedSensorConnected: Boolean
): Boolean {
    if (!selectedSensorConnected) return false
    return when (source) {
        SensorSyncSource.USB ->
            selectedMode == SensorConnectionMode.USB
        SensorSyncSource.WIRELESS ->
            selectedMode == SensorConnectionMode.WIFI ||
                selectedMode == SensorConnectionMode.BLUETOOTH ||
                selectedMode == SensorConnectionMode.INTERNET
    }
}

data class SensorParameters(
    val autonomousRecordingEnabled: Boolean = true,
    val automaticShutdownEnabled: Boolean = false,
    val automaticShutdownSeconds: Int = 1_800,
    val statusLedEnabled: Boolean = true,
    val statusLedBrightness: Int = 10,
    val statusBuzzerEnabled: Boolean = true,
    val statusBuzzerVolume: Int = 10
)

internal sealed interface SensorSyncEvent {
    val source: SensorSyncSource

    data class Started(
        override val source: SensorSyncSource,
        val acquisitions: Int,
        val alerts: Int,
        val errors: Int,
        val storageWasFull: Boolean
    ) : SensorSyncEvent

    data class Acquisition(
        override val source: SensorSyncSource,
        val recordId: Long,
        val timestamp: Long,
        val sessionId: Long,
        val sequence: Int,
        val note: String,
        val sample: SensorSample
    ) : SensorSyncEvent

    data class Alert(
        override val source: SensorSyncSource,
        val recordId: Long,
        val timestamp: Long,
        val details: String,
        val sessionId: Long
    ) : SensorSyncEvent

    data class Error(
        override val source: SensorSyncSource,
        val recordId: Long,
        val timestamp: Long,
        val code: String,
        val message: String
    ) : SensorSyncEvent

    data class Complete(
        override val source: SensorSyncSource,
        val acquisitions: Int,
        val alerts: Int,
        val errors: Int,
        val storageWasFull: Boolean,
        val offlineJobActive: Boolean,
        val offlineSessionId: Long,
        val offlineCompleted: Int,
        val offlineNextAtMs: Long
    ) : SensorSyncEvent

    data class Failed(
        override val source: SensorSyncSource,
        val acquisitions: Int,
        val alerts: Int,
        val errors: Int,
        val storageWasFull: Boolean,
        val code: String
    ) : SensorSyncEvent
}

internal object SensorSyncEventBus {
    private val channel =
        Channel<SensorSyncEvent>(Channel.UNLIMITED)

    val events = channel.receiveAsFlow()

    fun emit(event: SensorSyncEvent) {
        Log.i(
            "UvirSync",
            when (event) {
                is SensorSyncEvent.Started ->
                    "${event.source} start " +
                        "a=${event.acquisitions} " +
                        "l=${event.alerts} e=${event.errors}"
                is SensorSyncEvent.Acquisition ->
                    "${event.source} acquisition id=${event.recordId}"
                is SensorSyncEvent.Alert ->
                    "${event.source} alert id=${event.recordId}"
                is SensorSyncEvent.Error ->
                    "${event.source} error id=${event.recordId}"
                is SensorSyncEvent.Complete ->
                    "${event.source} complete " +
                        "a=${event.acquisitions} " +
                        "l=${event.alerts} e=${event.errors}"
                is SensorSyncEvent.Failed ->
                    "${event.source} failed " +
                        "a=${event.acquisitions} " +
                        "l=${event.alerts} e=${event.errors} " +
                        "code=${event.code}"
            }
        )
        channel.trySend(event)
    }
}

internal sealed interface SensorRuntimeEvent {
    val source: SensorSyncSource

    data class Acquisition(
        override val source: SensorSyncSource,
        val recordId: Long,
        val timestamp: Long,
        val sessionId: Long,
        val sequence: Int,
        val note: String,
        val completedCount: Int,
        val jobActive: Boolean,
        val nextAtMs: Long,
        val sample: SensorSample
    ) : SensorRuntimeEvent

    data class AutomaticStatus(
        override val source: SensorSyncSource,
        val sessionId: Long,
        val completedCount: Int,
        val jobActive: Boolean,
        val nextAtMs: Long,
        val conditionPlan: String? = null,
        val conditionWaiting: Boolean? = null,
        val endAtMs: Long? = null,
        val startedAtMs: Long? = null
    ) : SensorRuntimeEvent
}

internal object SensorRuntimeEventBus {
    private val channel =
        Channel<SensorRuntimeEvent>(Channel.UNLIMITED)

    val events = channel.receiveAsFlow()

    fun emit(event: SensorRuntimeEvent) {
        channel.trySend(event)
    }
}

internal fun parseSensorRuntimeFrame(
    json: JSONObject,
    source: SensorSyncSource
): Boolean {
    return when (json.optString("type")) {
        "acquisition_event" -> {
            val bands = json.optJSONObject("bands") ?: return true
            val recordId = json.optLong("record_id", -1L)
            val timestamp = json.optLong("timestamp_ms", 0L)
            if (recordId < 0L || timestamp <= 0L) return true
            SensorRuntimeEventBus.emit(
                SensorRuntimeEvent.Acquisition(
                    source = source,
                    recordId = recordId,
                    timestamp = timestamp,
                    sessionId = json.optLong("session_id", 0L),
                    sequence = json.optInt("sequence", 0),
                    note = json.optString("note"),
                    completedCount = json.optInt("completed_count", 0),
                    jobActive = json.optBoolean("job_active", false),
                    nextAtMs = json.optLong("next_at_ms", 0L),
                    sample = bands.toUvirBandSample()
                )
            )
            true
        }

        "automatic_status" -> {
            SensorRuntimeEventBus.emit(
                SensorRuntimeEvent.AutomaticStatus(
                    source = source,
                    sessionId = json.optLong("session_id", 0L),
                    completedCount = json.optInt("completed_count", 0),
                    jobActive = json.optBoolean("job_active", false),
                    nextAtMs = json.optLong("next_at_ms", 0L),
                    conditionPlan = json.optString("offline_condition_plan").takeIf { json.has("offline_condition_plan") },
                    conditionWaiting = (json.opt("offline_condition_waiting") as? Boolean),
                    endAtMs = json.optLong("offline_end_ms").takeIf { json.has("offline_end_ms") },
                    startedAtMs = json.optLong("offline_started_ms").takeIf { json.has("offline_started_ms") }
                )
            )
            true
        }

        else -> false
    }
}

internal fun parseSensorSyncFrame(
    json: JSONObject,
    source: SensorSyncSource
): Boolean {
    return when (json.optString("type")) {
        "sync_start" -> {
            SensorSyncEventBus.emit(
                SensorSyncEvent.Started(
                    source = source,
                    acquisitions = json.optInt("acquisitions", 0),
                    alerts = json.optInt("alerts", 0),
                    errors = json.optInt("errors", 0),
                    storageWasFull =
                        json.optBoolean("storage_was_full", false)
                )
            )
            true
        }

        "offline_record" -> {
            val kind = json.optString("record_kind")
            val recordId = json.optLong("record_id", -1L)
            val timestamp = json.optLong("timestamp_ms", 0L)
            if (recordId < 0L || timestamp <= 0L) {
                return true
            }
            val event = when (kind) {
                "acquisition" -> {
                    val bands = json.optJSONObject("bands") ?: return true
                    SensorSyncEvent.Acquisition(
                        source = source,
                        recordId = recordId,
                        timestamp = timestamp,
                        sessionId = json.optLong("session_id", 0L),
                        sequence = json.optInt("sequence", 0),
                        note = json.optString("note"),
                        sample = bands.toUvirBandSample()
                    )
                }

                "alert" -> SensorSyncEvent.Alert(
                    source = source,
                    recordId = recordId,
                    timestamp = timestamp,
                    details = json.optString("details"),
                    sessionId = json.optLong("session_id", 0L)
                )

                "error" -> SensorSyncEvent.Error(
                    source = source,
                    recordId = recordId,
                    timestamp = timestamp,
                    code = json.optString("code", "sensor_error"),
                    message = json.optString("message")
                )

                else -> return true
            }
            SensorSyncEventBus.emit(event)
            true
        }

        "sync_complete" -> {
            SensorSyncEventBus.emit(
                SensorSyncEvent.Complete(
                    source = source,
                    acquisitions = json.optInt("acquisitions", 0),
                    alerts = json.optInt("alerts", 0),
                    errors = json.optInt("errors", 0),
                    storageWasFull =
                        json.optBoolean("storage_was_full", false),
                    offlineJobActive =
                        json.optBoolean("offline_job_active", false),
                    offlineSessionId =
                        json.optLong("offline_session_id", 0L),
                    offlineCompleted =
                        json.optInt("offline_completed", 0),
                    offlineNextAtMs =
                        json.optLong("offline_next_ms", 0L)
                )
            )
            true
        }

        "sync_failed" -> {
            SensorSyncEventBus.emit(
                SensorSyncEvent.Failed(
                    source = source,
                    acquisitions = json.optInt("acquisitions", 0),
                    alerts = json.optInt("alerts", 0),
                    errors = json.optInt("errors", 0),
                    storageWasFull =
                        json.optBoolean("storage_was_full", false),
                    code = json.optString(
                        "code",
                        "offline_sync_failed"
                    )
                )
            )
            true
        }

        else -> false
    }
}

internal fun sensorTimeCommand(now: Long = System.currentTimeMillis()): String =
    "TIME $now"

internal fun sensorParametersCommand(parameters: SensorParameters): String =
    buildString {
        append("SENSOR_CONFIG ")
        append(if (parameters.statusLedEnabled) "ON" else "OFF")
        append(' ')
        append(parameters.statusLedBrightness.coerceIn(1, 100))
        append(' ')
        append(if (parameters.autonomousRecordingEnabled) "ON" else "OFF")
        append(' ')
        append(if (parameters.statusBuzzerEnabled) "ON" else "OFF")
        append(' ')
        append(parameters.statusBuzzerVolume.coerceIn(1, 100))
        append(' ')
        append(if (parameters.automaticShutdownEnabled) "ON" else "OFF")
        append(' ')
        append(parameters.automaticShutdownSeconds.coerceIn(60, 86_400))
    }

internal fun sensorSamplingCommand(parameters: AcquisitionParameters): String =
    listOf(
        "SAMPLING_CONFIG",
        parameters.samplesPerMeasurement.coerceIn(1, 21).toString(),
        parameters.sampleSpacingMs.coerceIn(150L, 5_000L).toString(),
        if (parameters.discardExtremes) "1" else "0"
    ).joinToString(" ")

internal fun sensorOfflineJobCommand(
    request: AutomaticAcquisitionRequest,
    sessionId: Long,
    nextAtMs: Long,
    endAtMs: Long,
    completedCount: Int,
    acquisitionParameters: AcquisitionParameters
): String =
    listOf(
        if (request.conditionalPlan == null) "OFFLINE_JOB" else "OFFLINE_CONDITIONAL_JOB",
        sessionId.toString(),
        nextAtMs.toString(),
        request.intervalSeconds.coerceAtLeast(1L).toString(),
        endAtMs.coerceAtLeast(0L).toString(),
        if (request.limitEnabled) {
            request.maxAcquisitions.coerceAtLeast(1).toString()
        } else {
            "0"
        },
        completedCount.coerceAtLeast(0).toString(),
        acquisitionParameters.samplesPerMeasurement.coerceIn(1, 21).toString(),
        acquisitionParameters.sampleSpacingMs.coerceIn(0L, 10_000L).toString(),
        if (acquisitionParameters.discardExtremes) "1" else "0",
        // Reserved compatibility token: the note is normalized in Android's
        // session table and is no longer duplicated in every ESP32 record.
        "-"
    ).joinToString(" ") + (request.conditionalPlan?.let { plan ->
        require(plan.isValid())
        " ${plan.action.name} ${plan.match.name} " +
            (if (request.useDuration) request.durationSeconds.coerceAtLeast(1L) else 0L) +
            " ${plan.rules.size} " +
            plan.rules.joinToString(" ") {
                "${it.metric.name} ${it.direction.name} ${it.threshold}"
            }
    } ?: "")

internal fun sensorAlertCommands(
    settings: ThresholdAlertSettings,
    sessionId: Long = 0L
): List<String> =
    buildList {
        add("ALERTS_CLEAR")
        settings.rules
            .filter { it.enabled }
            .take(24)
            .forEach { rule ->
                add(
                    "ALERT_RULE ${rule.metric.name} " +
                        "${rule.direction.name} ${rule.threshold}"
                )
            }
        add(
            "ALERT_CONFIG " +
                "${if (settings.hasActiveMonitoring()) "ON" else "OFF"} " +
                "${settings.repeatSeconds.coerceIn(1, 86_400)} " +
                sessionId.coerceAtLeast(0L)
        )
    }
