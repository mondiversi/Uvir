package me.mondiversi.uvir

import android.content.res.Resources
import android.content.Context
import androidx.core.app.NotificationCompat

internal enum class UvirConnectionState { DISCONNECTED, SEARCHING, CONNECTING, CONNECTED, SIMULATED }

/** Ephemeral transport state, never a restored connection or a sensor setting. */
internal data class UvirConnectionSensorState(
    val deviceId: String,
    val state: UvirConnectionState,
    val activityInProgress: Boolean = false
) {
    val indicator: UvirStatusIndicator
        get() = uvirStatusIndicator(
            state == UvirConnectionState.SIMULATED,
            state == UvirConnectionState.CONNECTED,
            state == UvirConnectionState.CONNECTING,
            activityInProgress
        )
}

internal fun uvirConnectionSensorState(
    deviceId: String,
    simulated: Boolean,
    connected: Boolean,
    initializing: Boolean,
    searching: Boolean,
    activityInProgress: Boolean
) = UvirConnectionSensorState(
    normalizeSensorDeviceId(deviceId),
    when {
        simulated -> UvirConnectionState.SIMULATED
        connected -> UvirConnectionState.CONNECTED
        initializing -> UvirConnectionState.CONNECTING
        searching -> UvirConnectionState.SEARCHING
        else -> UvirConnectionState.DISCONNECTED
    },
    activityInProgress
)

/** Selecting a sensor replaces only its own latest state; it never drops the others. */
internal fun uvirSelectedConnectionNotificationState(
    background: List<UvirConnectionSensorState>,
    fallback: UvirConnectionSensorState,
    transientActivity: Boolean
): UvirConnectionSensorState {
    val retained = background.firstOrNull { normalizeSensorDeviceId(it.deviceId) == normalizeSensorDeviceId(fallback.deviceId) }
        ?: return fallback
    // The retained worker knows sessions even when their UI is not on screen.
    return retained.copy(activityInProgress = retained.activityInProgress ||
        (retained.state == UvirConnectionState.CONNECTED && transientActivity))
}

/** Selecting a sensor replaces only its own latest state; it never drops the others. */
internal fun uvirConnectionNotificationSensors(
    background: List<UvirConnectionSensorState>,
    selected: UvirConnectionSensorState?
): List<UvirConnectionSensorState> {
    val states = linkedMapOf<String, UvirConnectionSensorState>()
    for (sensor in background + listOfNotNull(selected)) {
        val id = normalizeSensorDeviceId(sensor.deviceId)
        if (id.isNotBlank()) states[id] = sensor.copy(deviceId = id)
    }
    return states.values.toList()
}

internal data class UvirConnectionCounts(
    val connected: Int,
    val connecting: Int,
    val active: Int,
    val simulated: Int
)

internal fun uvirConnectionCounts(sensors: List<UvirConnectionSensorState>) = UvirConnectionCounts(
    connected = sensors.count { it.state == UvirConnectionState.CONNECTED },
    connecting = sensors.count { it.state == UvirConnectionState.CONNECTING || it.state == UvirConnectionState.SEARCHING },
    // A disconnected sensor may still be recording, but that is only its last known state.
    active = sensors.count { it.state == UvirConnectionState.CONNECTED && it.activityInProgress },
    simulated = sensors.count { it.state == UvirConnectionState.SIMULATED }
)

internal data class UvirConnectionNotification(
    val text: String = "",
    val lines: List<String> = emptyList(),
    val colorArgb: Int? = null,
    val busy: Boolean = false
)

internal fun uvirNotificationSensorName(profiles: List<UvirSensorProfile>, deviceId: String): String {
    val profile = profiles.firstOrNull { normalizeSensorDeviceId(it.hardwareUid) == normalizeSensorDeviceId(deviceId) }
    return normalizeSensorDisplayName(profile?.displayName.orEmpty(), profile?.hardwareUid ?: deviceId)
}

internal fun uvirNamedNotificationTitle(sensorName: String, title: String): String =
    if (sensorName.isBlank()) title else "$sensorName · $title"

internal fun buildUvirConnectionNotification(
    resources: Resources,
    sensors: List<UvirConnectionSensorState>,
    profiles: List<UvirSensorProfile>,
    selectedDeviceId: String
): UvirConnectionNotification {
    val states = uvirConnectionNotificationSensors(sensors, null)
    if (states.isEmpty()) return UvirConnectionNotification(resources.getString(R.string.sensor_status_no_sensor))
    val selectedId = normalizeSensorDeviceId(selectedDeviceId)
    val names = profiles.associateBy { normalizeSensorDeviceId(it.hardwareUid) }
    val counts = uvirConnectionCounts(states)
    fun line(sensor: UvirConnectionSensorState, markSelected: Boolean): String {
        val profile = names[sensor.deviceId]
        val name = normalizeSensorDisplayName(profile?.displayName.orEmpty(), profile?.hardwareUid ?: sensor.deviceId)
        val stateRes = when (sensor.state) {
            UvirConnectionState.CONNECTED -> R.string.connection_notification_connected
            UvirConnectionState.CONNECTING -> R.string.sensor_info_connecting
            UvirConnectionState.SEARCHING -> R.string.sensor_info_searching
            UvirConnectionState.SIMULATED -> R.string.connection_notification_simulation
            UvirConnectionState.DISCONNECTED -> R.string.connection_notification_disconnected
        }
        val parts = mutableListOf("${sensor.indicator.dot.glyph} $name", resources.getString(stateRes))
        if (sensor.activityInProgress && sensor.state != UvirConnectionState.SIMULATED) {
            parts += resources.getString(if (sensor.state == UvirConnectionState.CONNECTED)
                R.string.connection_notification_activity else R.string.connection_notification_last_activity)
        }
        if (markSelected && sensor.deviceId == selectedId) parts += resources.getString(R.string.connection_notification_selected)
        return parts.joinToString(" · ")
    }
    val lines = states.sortedBy { it.deviceId != selectedId }.map { line(it, states.size > 1) }
    val text = if (states.size == 1) line(states.single(), false) else {
        val parts = mutableListOf(resources.getQuantityString(R.plurals.connection_notification_connected_count, counts.connected, counts.connected))
        if (counts.connecting > 0) parts += resources.getQuantityString(R.plurals.connection_notification_connecting_count, counts.connecting, counts.connecting)
        if (counts.active > 0) parts += resources.getQuantityString(R.plurals.connection_notification_activity_count, counts.active, counts.active)
        if (counts.simulated > 0) parts += resources.getQuantityString(R.plurals.connection_notification_simulation_count, counts.simulated, counts.simulated)
        parts.joinToString(" · ")
    }
    return UvirConnectionNotification(text, lines,
        // A single colour must not pretend that every sensor has the same state.
        colorArgb = states.singleOrNull()?.indicator?.dot?.colorArgb,
        busy = counts.active > 0 || states.any { it.state == UvirConnectionState.CONNECTING })
}

/** One quiet foreground notification; the OS renders the expanded per-sensor detail. */
internal fun uvirConnectionNotificationBuilder(
    context: Context,
    channelId: String,
    notification: UvirConnectionNotification
): NotificationCompat.Builder = NotificationCompat.Builder(context, channelId)
    .setSmallIcon(R.drawable.ic_notification_uvir)
    .setContentTitle(context.getString(R.string.app_name))
    .setContentText(notification.text)
    .setStyle(NotificationCompat.BigTextStyle().bigText(notification.lines.joinToString("\n").ifBlank { notification.text }))
    .setProgress(0, 0, notification.busy)
    .setCategory(NotificationCompat.CATEGORY_SERVICE)
    .setPriority(NotificationCompat.PRIORITY_LOW)
    .setSilent(true)
    .setOnlyAlertOnce(true)
    .setOngoing(true)
    .apply { notification.colorArgb?.let { setColor(it) } }
