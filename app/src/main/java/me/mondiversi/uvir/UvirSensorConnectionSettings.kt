package me.mondiversi.uvir

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun UvirSensorConnectionSettings(
    context: Context,
    sensorCredentials: UvirSensorCredentials,
    sensorConnectionSectionExpanded: Boolean,
    onSensorConnectionSectionExpandedChange: (Boolean) -> Unit,
    sensorSettingsEnabled: Boolean,
    sensorWifiRadioEnabled: Boolean,
    onSensorWifiRadioEnabledChange: (Boolean) -> Unit,
    onRequestCurrentWifiSsid: () -> Unit,
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
    onSensorConnectionModeChanged: (SensorConnectionMode) -> Unit,
    cardColor: Color,
    primaryText: Color,
    secondaryText: Color
) {
    val radioCommit = LocalSettingsCommit.current
    val wirelessControlsEnabled =
        sensorSettingsEnabled && sensorCredentials.isProvisioned

    SettingsSection(
        settingsPage = UvirSettingsPage.SENSOR_CONNECTION,
        title = stringResource(R.string.settings_section_sensor_connection),
        titleIcon = ConnectivityIconType.SENSOR_CONNECTION,
        expanded = sensorConnectionSectionExpanded,
        enabled = true,
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
        dividerColor = secondaryText.copy(alpha = 0.28f),
        contentSpacing = UvirIslandSpacing,
        wrapDetailContent = false
    ) {
        SettingsSection(
            title = stringResource(R.string.sensor_connection_usb),
            titleIcon = ConnectivityIconType.USB,
            expanded = true,
            enabled = true,
            onExpandedChange = null,
            highlightExpandedHeader = false,
            showExpandedDivider = true,
            containerColor = cardColor,
            titleColor = primaryText,
            chevronColor = secondaryText,
            dividerColor = secondaryText.copy(alpha = 0.28f),
            contentSpacing = UvirSettingsControlGap
        ) {
            SettingsPageDescription(
                text = stringResource(R.string.sensor_usb_priority_description),
                color = secondaryText
            )

            SettingsPageDescription(
                text = stringResource(R.string.sensor_connection_provisioning_reminder),
                color = secondaryText
            )

            SettingsPageDescription(
                text = stringResource(R.string.sensor_connection_configuration_paths_description),
                color = secondaryText
            )
        }

        SettingsSection(
            title = stringResource(R.string.sensor_connection_wifi),
            titleIcon = ConnectivityIconType.WIFI,
            expanded = sensorWifiRadioEnabled,
            enabled = sensorSettingsEnabled,
            headerEnabled = wirelessControlsEnabled,
            onExpandedChange = { enabled ->
                if (enabled) {
                    onRequestCurrentWifiSsid()
                }
                onSensorWifiRadioEnabledChange(enabled)
                if (!enabled && sensorInternetEnabled) {
                    onSensorInternetEnabledChange(false)
                    radioCommit?.copy(group = SettingsSaveGroup.INTERNET)?.commit()
                }
                radioCommit?.commit()
            },
            headerControl = UvirSettingsHeaderControl.CHECKBOX,
            highlightExpandedHeader = false,
            showExpandedDivider = true,
            containerColor = cardColor,
            titleColor = primaryText,
            chevronColor = secondaryText,
            dividerColor = secondaryText.copy(alpha = 0.28f),
            contentSpacing = UvirSettingsControlGap
        ) {
            SettingsPageDescription(
                text = stringResource(R.string.sensor_wifi_esp32_description),
                color = secondaryText
            )
            Text(
                text = stringResource(
                    R.string.sensor_associated_value,
                    sensorCredentials.deviceId
                ),
                color = secondaryText,
                fontSize = 12.sp
            )
            OutlinedTextField(
                value = sensorWifiSsid,
                onValueChange = onSensorWifiSsidChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .settingsCommitOnBlur(SettingsSaveGroup.WIFI),
                enabled = sensorSettingsEnabled,
                label = { Text(stringResource(R.string.sensor_wifi_network_label)) },
                singleLine = true,
                colors = UvirOutlinedTextFieldColors()
            )
            OutlinedTextField(
                value = sensorWifiPassword,
                onValueChange = onSensorWifiPasswordChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .settingsCommitOnBlur(SettingsSaveGroup.WIFI),
                enabled = sensorSettingsEnabled,
                label = { Text(stringResource(R.string.sensor_wifi_password_label)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
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
                    fontSize = 12.sp
                )
            }
            OutlinedButton(
                onClick = {
                    runCatching {
                        context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = sensorSettingsEnabled,
                colors = uvirOutlinedActionColors(primaryText),
                border = uvirOutlinedActionBorder(sensorSettingsEnabled, secondaryText)
            ) {
                UvirLabeledButtonContent(text = stringResource(R.string.open_wifi_settings)) {
                    ConnectivitySectionIcon(
                        type = ConnectivityIconType.WIFI,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        SettingsSection(
            title = stringResource(R.string.sensor_connection_bluetooth),
            titleIcon = ConnectivityIconType.BLUETOOTH,
            expanded = sensorBluetoothRadioEnabled,
            enabled = sensorSettingsEnabled,
            headerEnabled = wirelessControlsEnabled,
            onExpandedChange = { enabled ->
                onSensorBluetoothRadioEnabledChange(enabled)
                radioCommit?.commit()
            },
            headerControl = UvirSettingsHeaderControl.CHECKBOX,
            highlightExpandedHeader = false,
            showExpandedDivider = true,
            containerColor = cardColor,
            titleColor = primaryText,
            chevronColor = secondaryText,
            dividerColor = secondaryText.copy(alpha = 0.28f),
            contentSpacing = UvirSettingsControlGap
        ) {
            SettingsPageDescription(
                text = stringResource(R.string.sensor_bluetooth_esp32_description),
                color = secondaryText
            )
            Text(
                text = stringResource(
                    R.string.sensor_bluetooth_name_value,
                    sensorCredentials.bluetoothName
                ),
                color = secondaryText,
                fontSize = 12.sp
            )
            Text(
                text = stringResource(
                    R.string.sensor_bluetooth_pin_value,
                    sensorCredentials.bluetoothPin
                ),
                color = secondaryText,
                fontSize = 12.sp
            )
            OutlinedButton(
                onClick = {
                    onSensorConnectionModeChanged(SensorConnectionMode.BLUETOOTH)
                    runCatching {
                        context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = sensorSettingsEnabled,
                colors = uvirOutlinedActionColors(primaryText),
                border = uvirOutlinedActionBorder(sensorSettingsEnabled, secondaryText)
            ) {
                UvirLabeledButtonContent(
                    text = stringResource(R.string.open_bluetooth_settings)
                ) {
                    ConnectivitySectionIcon(
                        type = ConnectivityIconType.BLUETOOTH,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        SettingsAutoSaveGroup(SettingsSaveGroup.INTERNET) {
            UvirSensorInternetSettings(
                sensorSettingsEnabled = wirelessControlsEnabled,
                enabled = sensorInternetEnabled,
                onEnabledChange = { enabled ->
                    if (enabled && !sensorWifiRadioEnabled) {
                        onSensorWifiRadioEnabledChange(true)
                        radioCommit?.commit()
                    }
                    onSensorInternetEnabledChange(enabled)
                },
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
                cardColor = cardColor,
                primaryText = primaryText,
                secondaryText = secondaryText
            )
        }
    }
}
