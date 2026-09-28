package me.mondiversi.uvir

import androidx.compose.ui.graphics.Color

/** Sensor-scoped values used by the home header menus. */
internal data class UvirSensorSourceDialogSnapshot(
    val selectedMode: SensorConnectionMode,
    val useFakeSensorData: Boolean,
    val wifiEnabled: Boolean,
    val bluetoothEnabled: Boolean,
    val internetEnabled: Boolean,
    val primaryText: Color,
    val secondaryText: Color,
    val cardColor: Color,
    val sensorInfo: UvirSensorRuntimeInfo,
    val sensorDisplayName: String,
    val sensorProfiles: List<UvirSensorProfile>,
    val selectedSensorDeviceId: String,
    val sensorSelectionEnabled: Boolean,
    val sensorConnected: Boolean,
    val dateFormat: UvirDateFormat,
    val timeFormat: UvirTimeFormat
)
