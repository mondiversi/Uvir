package me.mondiversi.uvir

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
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
    sensorDisplayName: String,
    sensorProfiles: List<UvirSensorProfile>,
    selectedSensorDeviceId: String,
    sensorSelectionEnabled: Boolean,
    onSensorSelected: (String) -> Unit,
    sensorConnected: Boolean,
    sensorPowerOffEnabled: Boolean,
    onOpenSensorInfo: () -> Unit,
    onRequestSensorPowerOff: () -> Unit,
    onModeSelected: (SensorConnectionMode) -> Unit,
    onDismissRequest: () -> Unit
) {
    var sensorMenuExpanded by remember { mutableStateOf(false) }
    val selectorEnabled = sensorSelectionEnabled && sensorProfiles.isNotEmpty()
    val sensorFieldColors = UvirOutlinedTextFieldColors()
    val nightMode = isSystemInDarkTheme()
    val sensorMenuColors = if (nightMode) {
        MenuDefaults.itemColors(textColor = primaryText)
    } else {
        MenuDefaults.itemColors()
    }
    val wifiLabel = stringResource(R.string.sensor_connection_wifi)
    val bluetoothLabel = stringResource(R.string.sensor_connection_bluetooth)
    val dialogScrollbar =
        rememberUvirDialogScrollbar(
            color = secondaryText.copy(alpha = 0.58f)
        )

    AlertDialog(
        onDismissRequest = onDismissRequest,
        modifier = dialogScrollbar.dialogModifier,
        title = {
            Text(
                text = stringResource(R.string.sensor_source_title)
            )
        },
        text = {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 460.dp)
                        .then(dialogScrollbar.viewportModifier)
            ) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .verticalScroll(dialogScrollbar.scrollState),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ExposedDropdownMenuBox(
                        expanded = sensorMenuExpanded && selectorEnabled,
                        onExpandedChange = { if (selectorEnabled) sensorMenuExpanded = it }
                    ) {
                    OutlinedTextField(
                        value = sensorDisplayName.ifBlank { "—" },
                        onValueChange = {},
                        modifier = Modifier.fillMaxWidth().menuAnchor(
                            ExposedDropdownMenuAnchorType.PrimaryNotEditable, selectorEnabled
                        ),
                        enabled = selectorEnabled,
                        readOnly = true,
                        singleLine = true,
                        label = {
                            Text(stringResource(R.string.sensor_selector_label))
                        },
                        suffix = {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CompositionLocalProvider(
                                    LocalMinimumInteractiveComponentSize provides 0.dp
                                ) {
                                    SensorSourceActionIcon(
                                        type = MenuIconType.VERSION_INFO,
                                        contentDescription =
                                            stringResource(R.string.sensor_info_action),
                                        enabled = sensorConnected,
                                        enabledTint = primaryText,
                                        disabledTint = secondaryText,
                                        onClick = onOpenSensorInfo
                                    )
                                    SensorSourceActionIcon(
                                        type = MenuIconType.POWER,
                                        contentDescription =
                                            stringResource(R.string.sensor_power_off),
                                        enabled = sensorPowerOffEnabled,
                                        enabledTint = UvirDestructiveActionColor,
                                        disabledTint = secondaryText,
                                        onClick = onRequestSensorPowerOff
                                    )
                                }
                                CompositionLocalProvider(
                                    LocalContentColor provides if (selectorEnabled)
                                        sensorFieldColors.unfocusedTextColor else sensorFieldColors.disabledTextColor
                                ) {
                                    ExposedDropdownMenuDefaults.TrailingIcon(
                                        sensorMenuExpanded && selectorEnabled,
                                        modifier = Modifier.testTag("sensor_selector_arrow")
                                    )
                                }
                            }
                        },
                        colors = sensorFieldColors
                    )
                    ExposedDropdownMenu(
                        expanded = sensorMenuExpanded && selectorEnabled,
                        onDismissRequest = { sensorMenuExpanded = false },
                        modifier = Modifier.testTag("sensor_profile_menu"),
                        containerColor = if (nightMode) Color(0xFF27323B) else MenuDefaults.containerColor,
                        tonalElevation = if (nightMode) 0.dp else MenuDefaults.TonalElevation
                    ) {
                        sensorProfiles.sortedBy { it.displayName.lowercase() }.forEach { profile ->
                            DropdownMenuItem(
                                colors = sensorMenuColors,
                                text = {
                                    Text(
                                        profile.displayName.ifBlank { profile.hardwareUid },
                                        maxLines = 1, overflow = TextOverflow.Ellipsis
                                    )
                                },
                                trailingIcon = {
                                    Text(
                                        text = formatSensorListLastActivity(profile.lastSeenAt),
                                        color = secondaryText,
                                        fontSize = 11.sp,
                                        lineHeight = 12.sp,
                                        maxLines = 2,
                                        softWrap = false,
                                        textAlign = TextAlign.End
                                    )
                                },
                                onClick = {
                                    sensorMenuExpanded = false
                                    if (!profile.hardwareUid.equals(selectedSensorDeviceId, ignoreCase = true)) {
                                        onSensorSelected(profile.hardwareUid)
                                    }
                                }
                            )
                        }
                        if (selectedSensorDeviceId.isNotBlank()) {
                            DropdownMenuItem(
                                colors = sensorMenuColors,
                                text = { Text(stringResource(R.string.sensor_associate_action)) },
                                onClick = {
                                    sensorMenuExpanded = false
                                    onSensorSelected(ASSOCIATE_NEW_SENSOR_REQUEST)
                                }
                            )
                        }
                    }
                    }

                    Spacer(Modifier.height(UvirSettingsControlGap))

                    SensorSourceOptionRow(
                        mode = SensorConnectionMode.USB,
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
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(R.string.close))
            }
        },
        containerColor = cardColor,
        titleContentColor = primaryText,
        textContentColor = primaryText
    )
}

@Composable
private fun SensorSourceActionIcon(
    type: MenuIconType,
    contentDescription: String,
    enabled: Boolean,
    enabledTint: Color,
    disabledTint: Color,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier =
            Modifier
                .size(36.dp)
                .semantics {
                    this.contentDescription = contentDescription
                }
    ) {
        UvirMenuIcon(
            type = type,
            modifier = Modifier.size(21.dp),
            tint =
                if (enabled) {
                    enabledTint
                } else {
                    disabledTint.copy(alpha = 0.42f)
                }
        )
    }
}
