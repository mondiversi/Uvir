package me.mondiversi.uvir

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

@Composable
internal fun UvirSensorInternetSettings(
    sensorSettingsEnabled: Boolean,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    usePrimaryWifi: Boolean,
    onUsePrimaryWifiChange: (Boolean) -> Unit,
    wifiSsid: String,
    onWifiSsidChange: (String) -> Unit,
    wifiPassword: String,
    onWifiPasswordChange: (String) -> Unit,
    relayHost: String,
    onRelayHostChange: (String) -> Unit,
    relayPort: String,
    onRelayPortChange: (String) -> Unit,
    mqttUsername: String,
    onMqttUsernameChange: (String) -> Unit,
    mqttPassword: String,
    onMqttPasswordChange: (String) -> Unit,
    cardColor: Color,
    primaryText: Color,
    secondaryText: Color
) {
    val controlsEnabled = sensorSettingsEnabled
    val commit = LocalSettingsCommit.current
    val mqttPasswordVisible = rememberSaveable { mutableStateOf(false) }
    val revealMqttPassword = controlsEnabled && mqttPasswordVisible.value
    SettingsSection(
        title = stringResource(R.string.sensor_connection_internet),
        titleIcon = ConnectivityIconType.INTERNET,
        expanded = enabled,
        enabled = sensorSettingsEnabled,
        headerEnabled = sensorSettingsEnabled,
        headerControl = UvirSettingsHeaderControl.CHECKBOX,
        highlightExpandedHeader = false,
        showExpandedDivider = true,
        onExpandedChange = { value ->
            onEnabledChange(value)
            commit?.commit()
        },
        containerColor = cardColor,
        titleColor = primaryText,
        chevronColor = secondaryText,
        dividerColor = secondaryText.copy(alpha = 0.28f),
        contentSpacing = UvirSettingsControlGap
    ) {
        SettingsPageDescription(
            text = stringResource(R.string.sensor_internet_description),
            color = secondaryText
        )

        SettingsCheckboxWithDescription(
            checked = usePrimaryWifi,
            onCheckedChange = onUsePrimaryWifiChange,
            title = stringResource(R.string.sensor_internet_use_primary_wifi),
            description =
                stringResource(R.string.sensor_internet_use_primary_wifi_description),
            primaryText = primaryText,
            secondaryText = secondaryText,
            enabled = controlsEnabled
        )

        if (!usePrimaryWifi) {
            OutlinedTextField(
                value = wifiSsid,
                onValueChange = onWifiSsidChange,
                modifier = Modifier.fillMaxWidth().settingsCommitOnBlur(),
                enabled = controlsEnabled,
                label = { Text(stringResource(R.string.sensor_wifi_network_label)) },
                singleLine = true,
                colors = UvirOutlinedTextFieldColors()
            )
            OutlinedTextField(
                value = wifiPassword,
                onValueChange = onWifiPasswordChange,
                modifier = Modifier.fillMaxWidth().settingsCommitOnBlur(),
                enabled = controlsEnabled,
                label = { Text(stringResource(R.string.sensor_wifi_password_label)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                colors = UvirOutlinedTextFieldColors()
            )
        }

        OutlinedTextField(
            value = relayHost,
            onValueChange = onRelayHostChange,
            modifier = Modifier.fillMaxWidth().settingsCommitOnBlur(),
            enabled = controlsEnabled,
            label = { Text(stringResource(R.string.sensor_internet_relay_host_label)) },
            supportingText = {
                Text(stringResource(R.string.sensor_internet_relay_host_supporting))
            },
            singleLine = true,
            colors = UvirOutlinedTextFieldColors()
        )
        OutlinedTextField(
            value = relayPort,
            onValueChange = { value ->
                onRelayPortChange(value.filter(Char::isDigit).take(5))
            },
            modifier = Modifier.fillMaxWidth().settingsCommitOnBlur(),
            enabled = controlsEnabled,
            label = { Text(stringResource(R.string.sensor_internet_relay_port_label)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            colors = UvirOutlinedTextFieldColors()
        )
        OutlinedTextField(
            value = mqttUsername,
            onValueChange = onMqttUsernameChange,
            modifier = Modifier.fillMaxWidth().settingsCommitOnBlur(),
            enabled = controlsEnabled,
            label = { Text(stringResource(R.string.sensor_internet_mqtt_username_label)) },
            singleLine = true,
            colors = UvirOutlinedTextFieldColors()
        )
        OutlinedTextField(
            value = mqttPassword,
            onValueChange = onMqttPasswordChange,
            modifier = Modifier.fillMaxWidth().settingsCommitOnBlur(),
            enabled = controlsEnabled,
            label = { Text(stringResource(R.string.sensor_internet_mqtt_password_label)) },
            singleLine = true,
            visualTransformation =
                if (revealMqttPassword) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
            trailingIcon = {
                val visibilityDescription =
                    stringResource(
                        if (revealMqttPassword) {
                            R.string.hide_password
                        } else {
                            R.string.show_password
                        }
                    )
                IconButton(
                    onClick = {
                        mqttPasswordVisible.value = !mqttPasswordVisible.value
                    },
                    enabled = controlsEnabled
                ) {
                    UvirPasswordVisibilityIcon(
                        visible = revealMqttPassword,
                        modifier =
                            Modifier
                                .size(22.dp)
                                .semantics {
                                    contentDescription = visibilityDescription
                                },
                        tint = if (controlsEnabled) primaryText else secondaryText
                    )
                }
            },
            colors = UvirOutlinedTextFieldColors()
        )
    }
}
