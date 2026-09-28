package me.mondiversi.uvir

import android.content.Context

/** Transport records are durable before acknowledgement, regardless of visible screen/UID. */
internal class UvirMultiSensorPersistence(
    private val context: Context,
    private val database: UvirDatabaseHelper,
    private val send: (String, SensorSyncSource, List<String>) -> Boolean,
    private val completeSync: (String, SensorSyncSource) -> Unit
) {
    private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private data class Progress(var acquisitions: Int = 0, var alerts: Int = 0, var errors: Int = 0,
        val records: MutableSet<Pair<String, Long>> = mutableSetOf())
    private val progress = mutableMapOf<Pair<String, SensorSyncSource>, Progress>()

    private fun key(id: String, name: String): String =
        if (UvirSensorCredentialStore.load(context).deviceId.equals(id, true)) name
        else sensorContextPreferenceKey(id, name)

    private fun storeAcquisition(id: String, recordId: Long, timestamp: Long, sessionId: Long,
        sequence: Int, note: String, sample: SensorSample, externalCommand: Boolean?): UvirStoredDeviceEvent<Unit> {
        val existed = database.hasSensorAcquisition(id, recordId)
        val manual = if (sessionId == 0L && externalCommand != false) {
            preferences.getLong(key(id, KEY_LAST_MANUAL_SESSION_ID), 0L).takeIf {
                it > 0L && database.manualSessionBelongsToSensor(it, id, timestamp)
            }
        } else null
        val resolved = manual ?: if (isSensorOriginatedSessionId(sessionId))
            database.resolveSensorOriginatedAcquisitionSession(id, sessionId, timestamp) else sessionId
        val stored = existed || database.saveRecoveredAcquisition(
            timestamp, sample, manual?.let(database::manualSessionNote) ?: note,
            resolved, manual?.let(database::nextSequenceForSession) ?: sequence, id, recordId,
            externalCommand ?: (sessionId == 0L || isSensorOriginatedSessionId(sessionId)),
            externalSession = isSensorOriginatedSessionId(sessionId), manualSession = manual != null
        )
        if (stored && !existed) incrementUnread(KEY_UNREAD_ACQUISITION_COUNT)
        if (stored && resolved > 0L) preferences.edit().putInt(
            sensorContextPreferenceKey(id, KEY_AUTO_COMPLETED_COUNT),
            database.acquisitionCountForSession(resolved)).apply()
        return UvirStoredDeviceEvent(id, Unit, stored, stored && !existed, resolved)
    }

    private fun incrementUnread(name: String) { UvirUnreadCounters.increment(preferences, name) }

    private fun rememberAutomatic(id: String, event: SensorRuntimeEvent.AutomaticStatus): Long {
        val originated = isSensorOriginatedSessionId(event.sessionId)
        val resolved = if (originated) database.resolveSensorOriginatedAcquisitionSession(id, event.sessionId,
            event.startedAtMs?.takeIf { it > 0L } ?: System.currentTimeMillis()) else event.sessionId
        if (originated && event.jobActive) database.startAcquisitionSession(resolved, "",
            event.startedAtMs?.takeIf { it > 0L } ?: System.currentTimeMillis(), id, externalCommand = true)
        if (resolved > 0L) {
            val edit = preferences.edit()
                .putLong(sensorContextPreferenceKey(id, KEY_AUTO_SESSION_ID), resolved)
                .putBoolean(sensorContextPreferenceKey(id, KEY_AUTO_ENABLED), event.jobActive)
                .putInt(sensorContextPreferenceKey(id, KEY_AUTO_COMPLETED_COUNT), database.acquisitionCountForSession(resolved))
                .putLong(sensorContextPreferenceKey(id, KEY_AUTO_NEXT_SAVE_MS), event.nextAtMs)
            event.externalCommand?.let { edit.putBoolean(sensorContextPreferenceKey(id, KEY_AUTO_EXTERNAL_COMMAND), it) }
            event.conditionPlan?.let { edit.putString(sensorContextPreferenceKey(id, KEY_AUTO_CONDITIONAL_PLAN), it) }
            event.conditionWaiting?.let { edit.putBoolean(sensorContextPreferenceKey(id, KEY_AUTO_CONDITIONAL_WAITING), it) }
            event.endAtMs?.let { edit.putLong(sensorContextPreferenceKey(id, KEY_AUTO_END_MS), it) }
            edit.apply()
            if (!event.jobActive) database.finishAcquisitionSession(resolved)
        }
        return resolved
    }

    suspend fun handle(frame: UvirDeviceFrame) {
        val id = frame.deviceId
        if (normalizeSensorDeviceId(id) !in UvirSensorCredentialStore.associatedDeviceIds(context)) return
        when (frame) {
            is UvirDeviceFrame.Runtime -> when (val event = frame.event) {
                is SensorRuntimeEvent.Acquisition -> {
                    val saved = storeAcquisition(id, event.recordId, event.timestamp, event.sessionId,
                        event.sequence, event.note, event.sample, event.externalCommand)
                    if (saved.stored) send(id, event.source, listOf("ACQUISITION_ACK ${event.recordId}"))
                    SensorRuntimeEventBus.publish(UvirStoredDeviceEvent(id, event, saved.stored, saved.newRecord, saved.sessionId))
                }
                is SensorRuntimeEvent.AutomaticStatus -> {
                    val resolved = rememberAutomatic(id, event)
                    SensorRuntimeEventBus.publish(UvirStoredDeviceEvent(id, event, sessionId = resolved))
                }
            }
            is UvirDeviceFrame.Alert -> {
                val event = frame.event
                val violations = parseThresholdAlertLogDetails(event.details)
                val existed = database.hasMatchingSensorAlert(id, event.timestampMs, event.details)
                val stored = !event.recorded || existed || database.insertThresholdAlertLog(
                    violations, event.timestampMs, event.sessionId.takeIf { it > 0L }, id, frame.qualityFlags) != -1L
                if (event.recorded && stored && !existed) incrementUnread(KEY_UNREAD_ALERT_COUNT)
                UvirSensorEventHub.publishAlert(UvirStoredDeviceEvent(id, event, stored, stored && !existed && event.recorded))
            }
            is UvirDeviceFrame.Sync -> {
                val event = frame.event
                val identity = normalizeSensorDeviceId(id) to event.source
                var stored = true
                var newRecord = false
                var resolved = 0L
                when (event) {
                    is SensorSyncEvent.Started -> progress[identity] = Progress()
                    is SensorSyncEvent.Acquisition -> {
                        val saved = storeAcquisition(id, event.recordId, event.timestamp, event.sessionId,
                            event.sequence, event.note, event.sample, event.externalCommand)
                        stored = saved.stored; newRecord = saved.newRecord; resolved = saved.sessionId
                        if (stored) {
                            val p = progress.getOrPut(identity) { Progress() }
                            if (p.records.add("acquisition" to event.recordId)) p.acquisitions++
                            send(id, event.source, listOf("SYNC_ACK ${event.recordId}"))
                        }
                    }
                    is SensorSyncEvent.Alert -> {
                        val existed = database.hasMatchingSensorAlert(id, event.timestamp, event.details)
                        stored = database.insertRecoveredThresholdAlert(event.timestamp, event.details, id,
                            event.recordId, event.sessionId.takeIf { it > 0L }, event.qualityFlags)
                        newRecord = stored && !existed
                        if (newRecord) incrementUnread(KEY_UNREAD_ALERT_COUNT)
                        if (stored) {
                            val p = progress.getOrPut(identity) { Progress() }
                            if (p.records.add("alert" to event.recordId)) p.alerts++
                            send(id, event.source, listOf("SYNC_ACK ${event.recordId}"))
                        }
                    }
                    is SensorSyncEvent.Error -> {
                        val p = progress.getOrPut(identity) { Progress() }
                        if (p.records.add("error" to event.recordId)) {
                            UvirErrorLog.record(context, "sensor:$id:${event.code}",
                                "Sensor timestamp: ${event.timestamp}\n${event.message}")
                            p.errors++
                        }
                        send(id, event.source, listOf("SYNC_ACK ${event.recordId}"))
                    }
                    is SensorSyncEvent.Complete -> {
                        val p = progress[identity] ?: Progress()
                        stored = p.acquisitions >= event.acquisitions && p.alerts >= event.alerts && p.errors >= event.errors
                        if (stored) completeSync(id, event.source)
                        progress.remove(identity)
                    }
                    is SensorSyncEvent.Failed -> progress.remove(identity)
                }
                SensorSyncEventBus.publish(UvirStoredDeviceEvent(id, event, stored, newRecord, resolved))
            }
        }
    }
}
