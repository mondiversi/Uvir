package me.mondiversi.uvir

internal fun uvirSensorConnectionIconType(mode: SensorConnectionMode): ConnectivityIconType = when (mode) {
    SensorConnectionMode.USB -> ConnectivityIconType.USB
    SensorConnectionMode.WIFI -> ConnectivityIconType.WIFI
    SensorConnectionMode.BLUETOOTH -> ConnectivityIconType.BLUETOOTH
    SensorConnectionMode.INTERNET -> ConnectivityIconType.INTERNET
}

internal fun uvirSensorConnectionLabelRes(mode: SensorConnectionMode): Int = when (mode) {
    SensorConnectionMode.USB -> R.string.sensor_connection_usb
    SensorConnectionMode.WIFI -> R.string.sensor_connection_wifi
    SensorConnectionMode.BLUETOOTH -> R.string.sensor_connection_bluetooth
    SensorConnectionMode.INTERNET -> R.string.sensor_connection_internet
}
