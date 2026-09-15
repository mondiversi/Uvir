package me.mondiversi.uvir

data class SensorRadioSettings(
    val wifiEnabled: Boolean,
    val bluetoothEnabled: Boolean
) {
    fun enabled(mode: SensorConnectionMode): Boolean =
        when (mode) {
            SensorConnectionMode.WIFI -> wifiEnabled
            SensorConnectionMode.BLUETOOTH -> bluetoothEnabled
            SensorConnectionMode.INTERNET -> wifiEnabled
            SensorConnectionMode.USB -> false
        }

    fun withMode(
        mode: SensorConnectionMode,
        enabled: Boolean
    ): SensorRadioSettings =
        when (mode) {
            SensorConnectionMode.WIFI -> copy(wifiEnabled = enabled)
            SensorConnectionMode.BLUETOOTH -> copy(bluetoothEnabled = enabled)
            SensorConnectionMode.INTERNET -> copy(wifiEnabled = enabled)
            SensorConnectionMode.USB -> this
        }

    val hasWirelessTransport: Boolean
        get() = wifiEnabled || bluetoothEnabled
}

internal data class SensorRadioChange(
    val mode: SensorConnectionMode,
    val enabled: Boolean
)

/**
 * Enables a replacement radio before disabling the active one. This keeps an
 * authenticated wireless path available while applying a Wi-Fi/Bluetooth
 * change and gives USB the same deterministic command order.
 */
internal fun orderedSensorRadioChanges(
    current: SensorRadioSettings,
    target: SensorRadioSettings,
    activeMode: SensorConnectionMode?
): List<SensorRadioChange> {
    val changedModes =
        listOf(
            SensorConnectionMode.WIFI,
            SensorConnectionMode.BLUETOOTH
        ).filter { mode ->
            current.enabled(mode) != target.enabled(mode)
        }

    val enabling =
        changedModes
            .filter(target::enabled)
            .map { mode -> SensorRadioChange(mode, true) }

    val disabling =
        changedModes
            .filterNot(target::enabled)
            .sortedBy { mode -> mode == activeMode }
            .map { mode -> SensorRadioChange(mode, false) }

    return enabling + disabling
}
