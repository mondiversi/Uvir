package me.mondiversi.uvir

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun UvirSensorSourceDialog(
    selectedMode: SensorConnectionMode,
    useFakeSensorData: Boolean,
    wifiEnabled: Boolean,
    bluetoothEnabled: Boolean,
    internetEnabled: Boolean,
    primaryText: Color,
    secondaryText: Color,
    cardColor: Color,
    sensorInfo: UvirSensorRuntimeInfo,
    onModeSelected: (SensorConnectionMode) -> Unit,
    onDismissRequest: () -> Unit
) {
    val wifiLabel = stringResource(R.string.sensor_connection_wifi)
    val bluetoothLabel = stringResource(R.string.sensor_connection_bluetooth)

    UvirAlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            UvirClosableDialogTitle(
                title = stringResource(R.string.sensor_source_title),
                onDismiss = onDismissRequest
            )
        },
        text = {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
            ) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .selectableGroup().testTag("home_sensor_source_menu"),
                    verticalArrangement = Arrangement.spacedBy(SensorRadioOptionGap)
                ) {
                    SensorSourceOptionRow(
                        mode = SensorConnectionMode.USB,
                        modifier = Modifier.testTag("home_source_option_USB"),
                        selected = selectedMode == SensorConnectionMode.USB,
                        label = stringResource(R.string.sensor_connection_usb),
                        primaryText = primaryText,
                        secondaryText = secondaryText,
                        onClick = {
                            onModeSelected(SensorConnectionMode.USB)
                        }
                    )

                    SensorSourceOptionRow(
                        mode = SensorConnectionMode.WIFI,
                        modifier = Modifier.testTag("home_source_option_WIFI"),
                        selected = selectedMode == SensorConnectionMode.WIFI,
                        label = wifiLabel,
                        primaryText = primaryText,
                        secondaryText = secondaryText,
                        enabled = wifiEnabled,
                        signalLevel =
                            if (
                                wifiEnabled &&
                                sensorInfo.wifiConnected == true &&
                                    sensorInfo.wifiRssiDbm != null
                            ) {
                                wifiSignalLevel(sensorInfo.wifiRssiDbm)
                            } else {
                                null
                            },
                        signalPercentage =
                            if (
                                wifiEnabled &&
                                sensorInfo.wifiConnected == true &&
                                    sensorInfo.wifiRssiDbm != null
                            ) {
                                wifiSignalPercentage(sensorInfo.wifiRssiDbm)
                            } else {
                                null
                            },
                        onClick = {
                            onModeSelected(SensorConnectionMode.WIFI)
                        }
                    )

                    SensorSourceOptionRow(
                        mode = SensorConnectionMode.BLUETOOTH,
                        modifier = Modifier.testTag("home_source_option_BLUETOOTH"),
                        selected = selectedMode == SensorConnectionMode.BLUETOOTH,
                        label = bluetoothLabel,
                        primaryText = primaryText,
                        secondaryText = secondaryText,
                        enabled = bluetoothEnabled,
                        signalLevel =
                            if (
                                bluetoothEnabled &&
                                sensorInfo.bluetoothConnected == true &&
                                    sensorInfo.bluetoothRssiDelta != null
                            ) {
                                bluetoothSignalLevel(
                                    sensorInfo.bluetoothRssiDelta
                                )
                            } else {
                                null
                            },
                        signalPercentage =
                            if (
                                bluetoothEnabled &&
                                sensorInfo.bluetoothConnected == true &&
                                    sensorInfo.bluetoothRssiDelta != null
                            ) {
                                bluetoothSignalPercentage(
                                    sensorInfo.bluetoothRssiDelta
                                )
                            } else {
                                null
                            },
                        onClick = {
                            onModeSelected(SensorConnectionMode.BLUETOOTH)
                        }
                    )

                    SensorSourceOptionRow(
                        mode = SensorConnectionMode.INTERNET,
                        modifier = Modifier.testTag("home_source_option_INTERNET"),
                        selected = selectedMode == SensorConnectionMode.INTERNET,
                        label = stringResource(R.string.sensor_connection_internet),
                        primaryText = primaryText,
                        secondaryText = secondaryText,
                        enabled = internetEnabled,
                        signalLevel =
                            if (internetEnabled) {
                                sensorInfo.wifiRssiDbm?.let(::wifiSignalLevel)
                            } else {
                                null
                            },
                        signalPercentage =
                            if (internetEnabled) {
                                sensorInfo.wifiRssiDbm?.let(::wifiSignalPercentage)
                            } else {
                                null
                            },
                        onClick = {
                            onModeSelected(SensorConnectionMode.INTERNET)
                        }
                    )

                    if (useFakeSensorData) {
                        UvirAttentionMessage(
                            text =
                                stringResource(
                                    R.string.sensor_source_debug_hint
                                ),
                            modifier = Modifier.padding(top = 6.dp),
                            fontSize = 11.sp,
                            lineHeight = 14.sp
                        )
                    }
                }
            }
        },
        confirmButton = {},
        tonalElevation = 0.dp,
        containerColor = cardColor,
        titleContentColor = primaryText,
        textContentColor = primaryText
    )
}
