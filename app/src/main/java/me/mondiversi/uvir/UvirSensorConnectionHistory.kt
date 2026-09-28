package me.mondiversi.uvir

import android.content.Context

/** App-observed connection starts, not last traffic or the sensor's clock. */
internal object UvirSensorConnectionHistory {
    private const val PREFERENCES_NAME = "uvir_sensor_connection_history"

    fun recordIfStarted(
        context: Context,
        deviceId: String,
        mode: SensorConnectionMode,
        wasConfirmed: Boolean,
        isConfirmed: Boolean,
        nowMs: Long = System.currentTimeMillis()
    ) {
        if (wasConfirmed || !isConfirmed || deviceId.isBlank() || nowMs <= 0L) return
        context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit().putLong(key(deviceId, mode), nowMs).apply()
    }

    fun lastConnectedAt(context: Context, deviceId: String, mode: SensorConnectionMode): Long? {
        if (deviceId.isBlank()) return null
        return context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .getLong(key(deviceId, mode), 0L).takeIf { it > 0L }
    }

    /** Most recent connection start across transports, retained while the sensor is offline. */
    fun lastConnectedAt(context: Context, deviceId: String): Long? =
        SensorConnectionMode.entries.mapNotNull { lastConnectedAt(context, deviceId, it) }.maxOrNull()

    /** Last successful transport, not merely the next method chosen in the UI. */
    fun lastConnectedMode(context: Context, deviceId: String): SensorConnectionMode? =
        SensorConnectionMode.entries.mapNotNull { mode ->
            lastConnectedAt(context, deviceId, mode)?.let { mode to it }
        }.maxByOrNull { it.second }?.first

    fun clear(context: Context): Boolean =
        context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit().clear().commit()

    private fun key(deviceId: String, mode: SensorConnectionMode) =
        "connected_at.${normalizeSensorDeviceId(deviceId)}.${mode.name}"
}
