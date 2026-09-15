package me.mondiversi.uvir

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
internal fun UvirSensorConnectionSettings(
    context: Context,
    sensorCredentials: UvirSensorCredentials,
    sensorConnectionSectionExpanded: Boolean,
    onSensorConnectionSectionExpandedChange: (Boolean) -> Unit,
    sensorSettingsEnabled: Boolean,
    usbSectionExpanded: Boolean,
    onUsbSectionExpandedChange: (Boolean) -> Unit,
    wifiSectionExpanded: Boolean,
    onWifiSectionExpandedChange: (Boolean) -> Unit,
    bluetoothSectionExpanded: Boolean,
    onBluetoothSectionExpandedChange: (Boolean) -> Unit,
    internetSectionExpanded: Boolean,
    onInternetSectionExpandedChange: (Boolean) -> Unit,
    sensorWifiRadioEnabled: Boolean,
    onSensorWifiRadioEnabledChange: (Boolean) -> Unit,
    sensorBluetoothRadioEnabled: Boolean,
    onSensorBluetoothRadioEnabledChange: (Boolean) -> Unit,
    sensorWifiSsid: String,
    onSensorWifiSsidChange: (String) -> Unit,
    sensorWifiPassword: String,
    onSensorWifiPasswordChange: (String) -> Unit,
    sensorInternetEnabled: Boolean,
    onSensorInternetEnabledChange: (Boolean) -> Unit,
    sensorInternetUsePrimaryWifi: Boolean,
    onSensorInternetUsePrimaryWifiChange: (Boolean) -> Unit,
    sensorInternetWifiSsid: String,
    onSensorInternetWifiSsidChange: (String) -> Unit,
    sensorInternetWifiPassword: String,
    onSensorInternetWifiPasswordChange: (String) -> Unit,
    sensorInternetRelayHost: String,
    onSensorInternetRelayHostChange: (String) -> Unit,
    sensorInternetRelayPort: String,
    onSensorInternetRelayPortChange: (String) -> Unit,
    sensorInternetMqttUsername: String,
    onSensorInternetMqttUsernameChange: (String) -> Unit,
    sensorInternetMqttPassword: String,
    onSensorInternetMqttPasswordChange: (String) -> Unit,
    wifiConfigurationInProgress: Boolean,
    onWifiConfigurationInProgressChange: (Boolean) -> Unit,
    settingsApplyInProgress: Boolean,
    settingsApplyScope: CoroutineScope,
    sensorFirmwareCurrent: Boolean,
    onFirmwareUpdateRequired: () -> Unit,
    onConfigureSensorWifi: suspend (String, String) -> Boolean,
    onSensorConnectionModeChanged: (SensorConnectionMode) -> Unit,
    cardColor: Color,
    primaryText: Color,
    secondaryText: Color
) {
    val resources = context.resources

    SettingsSection(
        settingsPage = UvirSettingsPage.SENSOR_CONNECTION,
        title =
            stringResource(
                R.string.settings_section_sensor_connection
            ),
        titleIcon =
            ConnectivityIconType.SENSOR_CONNECTION,
        expanded =
            sensorConnectionSectionExpanded,
        enabled = sensorSettingsEnabled,
        dimContentWhenDisabled = false,
        onExpandedChange = { expanded ->
            onSensorConnectionSectionExpandedChange(expanded)
            saveSettingsSectionExpanded(
                context,
                KEY_SETTINGS_SENSOR_CONNECTION_EXPANDED,
                expanded
            )
        },
        containerColor = cardColor,
        titleColor = primaryText,
        chevronColor = secondaryText,
        dividerColor =
            secondaryText.copy(
                alpha = 0.28f
            )
    ) {
        Column(
            verticalArrangement =
                Arrangement.spacedBy(UvirSettingsGroupGap)
        ) {
        Text(
            text =
                stringResource(
                    R.string.sensor_connection_provisioning_reminder
                ),
            color = secondaryText,
            fontSize = 11.sp,
            lineHeight = 14.sp
        )

        SettingsSection(
            title =
                stringResource(
                    R.string.sensor_connection_usb
            ),
            titleIcon = ConnectivityIconType.USB,
            expanded = usbSectionExpanded,
            enabled = sensorSettingsEnabled,
            onExpandedChange = { expanded ->
                onUsbSectionExpandedChange(expanded)
                saveSettingsSectionExpanded(
                    context,
                    KEY_SETTINGS_USB_EXPANDED,
                    expanded
                )
            },
            containerColor =
                secondaryText.copy(alpha = 0.08f),
            titleColor = primaryText,
            chevronColor = secondaryText,
            dividerColor =
                secondaryText.copy(alpha = 0.18f),
            contentSpacing = UvirSettingsControlGap
        ) {
            Text(
                text =
                    stringResource(
                        R.string.sensor_usb_priority_description
                    ),
                color = secondaryText,
                fontSize = 11.sp,
                lineHeight = 14.sp
            )
        }

        SettingsSection(
            title =
                stringResource(
                    R.string.sensor_connection_wifi
                ),
            titleIcon = ConnectivityIconType.WIFI,
            expanded = wifiSectionExpanded,
            enabled = sensorSettingsEnabled,
            onExpandedChange = { expanded ->
                onWifiSectionExpandedChange(expanded)
                saveSettingsSectionExpanded(
                    context,
                    KEY_SETTINGS_WIFI_EXPANDED,
                    expanded
                )
            },
            containerColor =
                secondaryText.copy(alpha = 0.08f),
            titleColor = primaryText,
            chevronColor = secondaryText,
            dividerColor =
                secondaryText.copy(alpha = 0.18f),
            contentSpacing = UvirSettingsControlGap
        ) {
            if (sensorCredentials.isProvisioned) {
                SettingsCheckboxWithDescription(
                    checked = sensorWifiRadioEnabled,
                    onCheckedChange = { enabled ->
                        onSensorWifiRadioEnabledChange(enabled)
                    },
                    title =
                        stringResource(
                            R.string.sensor_wifi_enabled
                        ),
                    description =
                        stringResource(
                            R.string.sensor_radio_usb_control_description
                        ),
                    primaryText = primaryText,
                    secondaryText = secondaryText,
                    enabled = sensorSettingsEnabled
                )
            }

            if (
                !sensorCredentials.isProvisioned ||
                sensorWifiRadioEnabled
            ) {

            Text(
                text =
                    stringResource(
                        R.string.sensor_wifi_esp32_description
                    ),
                color = secondaryText,
                fontSize = 11.sp,
                lineHeight = 14.sp
            )

            if (sensorCredentials.isProvisioned) {
                Text(
                    text = stringResource(
                        R.string.sensor_associated_value,
                        sensorCredentials.deviceId
                    ),
                    color = secondaryText,
                    fontSize = 11.sp
                )

                OutlinedTextField(
                    value = sensorWifiSsid,
                    onValueChange = {
                        onSensorWifiSsidChange(it)
                    },
                    modifier = Modifier.fillMaxWidth().settingsCommitOnBlur(SettingsSaveGroup.WIFI),
                    enabled = sensorSettingsEnabled,
                    label = {
                        Text(
                            stringResource(
                                R.string.sensor_wifi_network_label
                            )
                        )
                    },
                    singleLine = true,
                    colors = UvirOutlinedTextFieldColors()
                )

                OutlinedTextField(
                    value = sensorWifiPassword,
                    onValueChange = {
                        onSensorWifiPasswordChange(it)
                    },
                    modifier = Modifier.fillMaxWidth().settingsCommitOnBlur(SettingsSaveGroup.WIFI),
                    enabled = sensorSettingsEnabled,
                    label = {
                        Text(
                            stringResource(
                                R.string.sensor_wifi_password_label
                            )
                        )
                    },
                    singleLine = true,
                    visualTransformation =
                        PasswordVisualTransformation(),
                    colors = UvirOutlinedTextFieldColors()
                )

                if (sensorCredentials.wifiHost.isNotBlank()) {
                    Text(
                        text = stringResource(
                            R.string.sensor_wifi_address_value,
                            sensorCredentials.wifiHost,
                            sensorCredentials.wifiPort
                        ),
                        color = secondaryText,
                        fontSize = 11.sp
                    )
                }

                OutlinedButton(
                    onClick = {
                        runCatching {
                            context.startActivity(
                                Intent(
                                    Settings.ACTION_WIFI_SETTINGS
                                )
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = sensorSettingsEnabled,
                    colors = uvirOutlinedActionColors(primaryText),
                    border =
                        uvirOutlinedActionBorder(
                            sensorSettingsEnabled,
                            secondaryText
                        )
                ) {
                    AdaptiveSingleLineButtonText(
                        stringResource(
                            R.string.open_wifi_settings
                        )
                    )
                }
            }
            }
        }

        SettingsSection(
            title =
                stringResource(
                    R.string.sensor_connection_bluetooth
                ),
            titleIcon = ConnectivityIconType.BLUETOOTH,
            expanded = bluetoothSectionExpanded,
            enabled = sensorSettingsEnabled,
            onExpandedChange = { expanded ->
                onBluetoothSectionExpandedChange(expanded)
                saveSettingsSectionExpanded(
                    context,
                    KEY_SETTINGS_BLUETOOTH_EXPANDED,
                    expanded
                )
            },
            containerColor =
                secondaryText.copy(alpha = 0.08f),
            titleColor = primaryText,
            chevronColor = secondaryText,
            dividerColor =
                secondaryText.copy(alpha = 0.18f),
            contentSpacing = UvirSettingsControlGap
        ) {
            if (sensorCredentials.isProvisioned) {
                SettingsCheckboxWithDescription(
                    checked = sensorBluetoothRadioEnabled,
                    onCheckedChange = { enabled ->
                        onSensorBluetoothRadioEnabledChange(enabled)
                    },
                    title =
                        stringResource(
                            R.string.sensor_bluetooth_enabled
                        ),
                    description =
                        stringResource(
                            R.string.sensor_radio_usb_control_description
                        ),
                    primaryText = primaryText,
                    secondaryText = secondaryText,
                    enabled = sensorSettingsEnabled
                )
            }

            if (
                !sensorCredentials.isProvisioned ||
                sensorBluetoothRadioEnabled
            ) {
            Text(
                text =
                    stringResource(
                        R.string.sensor_bluetooth_esp32_description
                    ),
                color = secondaryText,
                fontSize = 11.sp,
                lineHeight = 14.sp
            )

            if (sensorCredentials.isProvisioned) {
                Text(
                    text = stringResource(
                        R.string.sensor_bluetooth_name_value,
                        sensorCredentials.bluetoothName
                    ),
                    color = primaryText,
                    fontSize = 12.sp
                )
                Text(
                    text = stringResource(
                        R.string.sensor_bluetooth_pin_value,
                        sensorCredentials.bluetoothPin
                    ),
                    color = primaryText,
                    fontSize = 12.sp
                )
                OutlinedButton(
                    onClick = {
                        onSensorConnectionModeChanged(
                            SensorConnectionMode.BLUETOOTH
                        )
                        runCatching {
                            context.startActivity(
                                Intent(
                                    Settings.ACTION_BLUETOOTH_SETTINGS
                                )
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = sensorSettingsEnabled,
                    colors = uvirOutlinedActionColors(primaryText),
                    border =
                        uvirOutlinedActionBorder(
                            sensorSettingsEnabled,
                            secondaryText
                        )
                ) {
                    AdaptiveSingleLineButtonText(
                        stringResource(
                            R.string.open_bluetooth_settings
                        )
                    )
                }
            }
            }
        }

        SettingsAutoSaveGroup(SettingsSaveGroup.INTERNET) {
        UvirSensorInternetSettings(
            context = context,
            expanded = internetSectionExpanded,
            onExpandedChange = onInternetSectionExpandedChange,
            sensorSettingsEnabled = sensorSettingsEnabled,
            settingsApplyInProgress = settingsApplyInProgress,
            enabled = sensorInternetEnabled,
            onEnabledChange = onSensorInternetEnabledChange,
            usePrimaryWifi = sensorInternetUsePrimaryWifi,
            onUsePrimaryWifiChange = onSensorInternetUsePrimaryWifiChange,
            wifiSsid = sensorInternetWifiSsid,
            onWifiSsidChange = onSensorInternetWifiSsidChange,
            wifiPassword = sensorInternetWifiPassword,
            onWifiPasswordChange = onSensorInternetWifiPasswordChange,
            relayHost = sensorInternetRelayHost,
            onRelayHostChange = onSensorInternetRelayHostChange,
            relayPort = sensorInternetRelayPort,
            onRelayPortChange = onSensorInternetRelayPortChange,
            mqttUsername = sensorInternetMqttUsername,
            onMqttUsernameChange = onSensorInternetMqttUsernameChange,
            mqttPassword = sensorInternetMqttPassword,
            onMqttPasswordChange = onSensorInternetMqttPasswordChange,
            primaryText = primaryText,
            secondaryText = secondaryText
        )
        }
    }
    }

}
