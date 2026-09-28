package me.mondiversi.uvir

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue

/** Alert monitoring is independent of acquisition/synchronization activity. */
internal fun uvirSensorAlertMonitoringActive(
    deviceId: String,
    info: UvirSensorRuntimeInfo,
    lastKnownActive: Boolean
): Boolean = if (info.deviceId.trim().equals(deviceId.trim(), ignoreCase = true))
    info.alertMonitoringEnabled ?: lastKnownActive else lastKnownActive

internal fun uvirSensorAlertDeviceIds(
    backgroundIds: Set<String>, selectedDeviceId: String, selectedAlertsActive: Boolean
): Set<String> = backgroundIds.map(::normalizeSensorDeviceId).filter { it.isNotBlank() }.toMutableSet().apply {
    val selected = normalizeSensorDeviceId(selectedDeviceId)
    if (selected.isNotBlank()) {
        if (selectedAlertsActive) add(selected) else remove(selected)
    }
}

internal fun uvirOtherSensorHasAlerts(selectedDeviceId: String, alertDeviceIds: Set<String>): Boolean {
    val selected = normalizeSensorDeviceId(selectedDeviceId)
    return selected.isNotBlank() && alertDeviceIds.any {
        val id = normalizeSensorDeviceId(it)
        id.isNotBlank() && id != selected
    }
}

/** Same soft 1100 ms fading cadence as the live alert edge indicators. */
@Composable
internal fun rememberUvirSensorAlertPulseAlpha(): Float {
    val transition = rememberInfiniteTransition(label = "sensorAlertSelectionPulse")
    val alpha by transition.animateFloat(
        initialValue = 0.12f,
        targetValue = 0.46f,
        animationSpec = infiniteRepeatable(animation = tween(1100), repeatMode = RepeatMode.Reverse),
        label = "sensorAlertSelectionAlpha"
    )
    return alpha
}
