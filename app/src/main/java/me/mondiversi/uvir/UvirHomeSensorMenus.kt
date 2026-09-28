package me.mondiversi.uvir

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal const val UvirSensorListNameMaxWidthFraction = 0.55f

/** Modal radio choices retain live status and the last confirmed connection start. */
@Composable
internal fun UvirHomeSensorSelectionDialog(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    sensorProfiles: List<UvirSensorProfile>,
    selectedSensorDeviceId: String,
    sensorConnectionModes: Map<String, SensorConnectionMode>,
    selectedConnectionMode: SensorConnectionMode,
    sensorInfoEnabled: Boolean,
    onOpenSensorInfo: () -> Unit,
    primaryText: Color,
    secondaryText: Color,
    containerColor: Color,
    onSensorSelected: (String) -> Unit,
    sensorStatusIndicators: Map<String, UvirStatusIndicator> = emptyMap(),
    statusPulseAlpha: Float = 1f,
    alertMonitoringDeviceIds: Set<String> = emptySet(),
    alertPulseAlpha: Float = 0f,
    selectionEnabled: Boolean = true,
    onConnectionModeSelected: (SensorConnectionMode) -> Unit = {},
    connectionSelectionEnabled: Boolean = true,
    wifiEnabled: Boolean = true,
    bluetoothEnabled: Boolean = true,
    internetEnabled: Boolean = true
) {
    if (!expanded) return
    val context = LocalContext.current
    val dateFormat = LocalUvirDateFormat.current
    val timeFormat = LocalUvirTimeFormat.current
    val noSensorLabel = stringResource(R.string.sensor_no_selection)
    val alertColor = uvirAlertSessionIndicatorColor(androidx.compose.foundation.isSystemInDarkTheme())
    val alertsLabel = stringResource(R.string.threshold_alerts_title)
    UvirActionTheme {
        UvirAlertDialog(
            onDismissRequest = onDismissRequest,
            modifier = Modifier.testTag("home_sensor_dialog"),
            title = { Text(stringResource(R.string.sensor_selection_title)) },
            containerColor = containerColor,
            tonalElevation = 0.dp,
            titleContentColor = primaryText,
            textContentColor = primaryText,
            confirmButton = null,
            fixedBottomContent = {
                // This footer stays outside the shared dialog's scrollable sensor list.
                Column(Modifier.fillMaxWidth().testTag("home_sensor_connection_footer")) {
                    HorizontalDivider(Modifier.padding(top = 10.dp, bottom = 10.dp), color = secondaryText.copy(alpha = .20f))
                    UvirSensorConnectionSelector(
                        selectedMode = selectedConnectionMode,
                        enabled = selectionEnabled && connectionSelectionEnabled && selectedSensorDeviceId.isNotBlank(),
                        wifiEnabled = wifiEnabled, bluetoothEnabled = bluetoothEnabled, internetEnabled = internetEnabled,
                        primaryText = primaryText, secondaryText = secondaryText,
                        containerColor = containerColor,
                        onModeSelected = onConnectionModeSelected
                    )
                }
            },
            text = {
                Box(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.fillMaxWidth()
                            .testTag("home_sensor_selection_menu"),
                        verticalArrangement = Arrangement.spacedBy(SensorRadioOptionGap)
                    ) {
                        Column(Modifier.fillMaxWidth().selectableGroup(),
                            verticalArrangement = Arrangement.spacedBy(SensorRadioOptionGap)) {
                            SensorRadioOptionSurface(
                                modifier = Modifier.heightIn(min = SensorRadioOptionMinHeight)
                                    .testTag("home_sensor_option_none")
                                    .semantics { contentDescription = noSensorLabel },
                                selected = selectedSensorDeviceId.isBlank(),
                                enabled = selectionEnabled,
                                primaryText = primaryText, secondaryText = secondaryText,
                                onClick = { onSensorSelected(NO_SENSOR_SELECTED_REQUEST) }
                            ) {
                                Row(Modifier.fillMaxWidth().heightIn(min = SensorRadioOptionMinHeight)
                                    .padding(horizontal = SensorRadioOptionHorizontalPadding,
                                        vertical = SensorRadioOptionVerticalPadding),
                                    verticalAlignment = Alignment.CenterVertically) {
                                    Text(noSensorLabel, Modifier.weight(1f), color = primaryText, style = sensorRadioOptionTextStyle())
                                    HomeSensorSelectionRadio(selectedSensorDeviceId.isBlank(), secondaryText, "none")
                                }
                            }
                            sensorProfiles.sortedBy { it.displayName.lowercase() }.forEach { profile ->
                                val isSelected = profile.hardwareUid.equals(selectedSensorDeviceId, ignoreCase = true)
                                val alertsActive = normalizeSensorDeviceId(profile.hardwareUid) in alertMonitoringDeviceIds
                                val name = profile.displayName.ifBlank { profile.hardwareUid }
                                val indicator = sensorStatusIndicators[normalizeSensorDeviceId(profile.hardwareUid)]
                                    ?: uvirStatusIndicator(false, sensorInfoEnabled && isSelected, false, false)
                                val rowText = if (indicator.dot == UvirStatusDot.GREEN || indicator.dot == UvirStatusDot.DEBUG)
                                    primaryText else primaryText.copy(alpha = 0.60f)
                                val statusLabel = stringResource(when (indicator.dot) {
                                    UvirStatusDot.GREEN -> R.string.sensor_status_connected
                                    UvirStatusDot.YELLOW -> R.string.sensor_info_connecting
                                    UvirStatusDot.DEBUG -> R.string.sensor_source_debug_accessibility
                                    UvirStatusDot.RED -> R.string.home_sensor_disconnected_title
                                })
                                val activityLabel = if (indicator.pulses && indicator.dot != UvirStatusDot.YELLOW)
                                    ", " + stringResource(R.string.sensor_info_activity) else ""
                                val preferredMode = sensorConnectionModes[normalizeSensorDeviceId(profile.hardwareUid)]
                                    ?: if (isSelected) selectedConnectionMode else SensorConnectionMode.USB
                                val mode = remember(context, profile.hardwareUid, isSelected, indicator.dot,
                                    preferredMode, selectedConnectionMode) {
                                    if (isSelected) selectedConnectionMode
                                    else UvirSensorConnectionHistory.lastConnectedMode(context, profile.hardwareUid) ?: preferredMode
                                }
                                val connectionLabel = stringResource(uvirSensorConnectionLabelRes(mode))
                                val lastConnectedAt = remember(context, profile.hardwareUid, indicator.dot, mode) {
                                    if (indicator.dot == UvirStatusDot.GREEN) {
                                        UvirSensorConnectionHistory.lastConnectedAt(context, profile.hardwareUid, mode)
                                            ?: UvirSensorConnectionHistory.lastConnectedAt(context, profile.hardwareUid)
                                    } else UvirSensorConnectionHistory.lastConnectedAt(context, profile.hardwareUid)
                                }
                                val connectionDate = lastConnectedAt?.let {
                                    formatUvirDateTime(it, dateFormat, separator = "\n", timeFormat = timeFormat)
                                } ?: "—"
                                Row(
                                    Modifier.fillMaxWidth().heightIn(min = SensorRadioOptionMinHeight)
                                        .testTag("home_sensor_row_${profile.hardwareUid}"),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    SensorRadioOptionSurface(
                                        modifier = Modifier.weight(1f).heightIn(min = SensorRadioOptionMinHeight)
                                            .testTag("home_sensor_option_${profile.hardwareUid}")
                                            .semantics {
                                                contentDescription = "$name, $connectionLabel, $statusLabel$activityLabel, " +
                                                    context.getString(R.string.sensor_info_last_connection) + ": " +
                                                    connectionDate.replace('\n', ' ')
                                                if (alertsActive) stateDescription = alertsLabel
                                            },
                                        selected = isSelected,
                                        enabled = selectionEnabled,
                                        // A full-row tint stays softer than the compact sensor-name signal.
                                        highlightColor = if (alertsActive) alertColor.copy(alpha = alertPulseAlpha * 0.5f)
                                            else Color.Transparent,
                                        primaryText = primaryText, secondaryText = secondaryText,
                                        onClick = { onSensorSelected(profile.hardwareUid) }
                                    ) {
                                        BoxWithConstraints(Modifier.fillMaxWidth().heightIn(min = SensorRadioOptionMinHeight)
                                            .padding(horizontal = SensorRadioOptionHorizontalPadding,
                                                vertical = SensorRadioOptionVerticalPadding)) {
                                            val nameMaxWidth = maxWidth * UvirSensorListNameMaxWidthFraction
                                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                                UvirConnectionLabel(
                                                    text = name,
                                                    type = uvirSensorConnectionIconType(mode),
                                                    iconColor = Color(indicator.dot.colorArgb), iconSize = 16.dp,
                                                    textColor = rowText, style = sensorRadioOptionTextStyle(),
                                                    scrollOverflow = true, textMaxWidth = nameMaxWidth,
                                                    modifier = Modifier.weight(1f),
                                                    iconModifier = Modifier.testTag("home_sensor_status_${profile.hardwareUid}"),
                                                    textModifier = Modifier.testTag("home_sensor_label_${profile.hardwareUid}"),
                                                    iconAlpha = if (indicator.pulses) statusPulseAlpha else 1f
                                                )
                                                Spacer(Modifier.width(10.dp))
                                                UvirSensorInfoButton(
                                                    enabled = selectionEnabled && sensorInfoEnabled && isSelected,
                                                    primaryText = primaryText, secondaryText = secondaryText,
                                                    onClick = onOpenSensorInfo,
                                                    contentDescription = stringResource(R.string.sensor_info_action) + ": " + name,
                                                    // Fit the same 48dp row including its shared vertical padding.
                                                    modifier = Modifier.size(SensorRadioOptionMinHeight - SensorRadioOptionVerticalPadding * 2)
                                                        .testTag("sensor_profile_info_${profile.hardwareUid}")
                                                )
                                                Spacer(Modifier.width(4.dp))
                                                Text(connectionDate, color = secondaryText,
                                                    fontSize = 11.sp, lineHeight = 12.sp,
                                                    textAlign = TextAlign.End, maxLines = 2, softWrap = false,
                                                    modifier = Modifier.testTag("home_sensor_last_connection_${profile.hardwareUid}"))
                                                Spacer(Modifier.width(10.dp))
                                                HomeSensorSelectionRadio(isSelected, secondaryText, profile.hardwareUid)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        )
    }
}

/** Four equal segments share the measurement selector's selected frame and press behavior. */
@Composable
private fun UvirSensorConnectionSelector(
    selectedMode: SensorConnectionMode,
    enabled: Boolean,
    wifiEnabled: Boolean,
    bluetoothEnabled: Boolean,
    internetEnabled: Boolean,
    primaryText: Color,
    secondaryText: Color,
    containerColor: Color,
    onModeSelected: (SensorConnectionMode) -> Unit
) {
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)
        .clip(RoundedCornerShape(14.dp))
        .background(primaryText.copy(alpha = .04f).compositeOver(containerColor))
        .testTag("home_sensor_connection_grid")
        .padding(3.dp)
        .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        SensorConnectionMode.entries.forEach { mode ->
            val available = enabled && when (mode) {
                SensorConnectionMode.USB -> true
                SensorConnectionMode.WIFI -> wifiEnabled
                SensorConnectionMode.BLUETOOTH -> bluetoothEnabled
                SensorConnectionMode.INTERNET -> internetEnabled
            }
            val selected = mode == selectedMode
            val label = stringResource(uvirSensorConnectionLabelRes(mode))
            val tint = if (!available) secondaryText.copy(alpha = .46f)
                else if (selected) primaryText else secondaryText
            ViewModeButton(
                selected = selected, enabled = available,
                alerted = false, contentDescription = label,
                primaryText = primaryText, secondaryText = secondaryText,
                minimumHeight = 72.dp,
                modifier = Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(10.dp))
                    .testTag("home_source_option_${mode.name}"),
                onClick = { if (mode != selectedMode) onModeSelected(mode) }
            ) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 3.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center) {
                    UvirHomeSensorSourceIcon(uvirSensorConnectionIconType(mode), tint, iconSize = 20.dp)
                    Spacer(Modifier.height(5.dp))
                    Text(label, color = tint, fontSize = 12.sp, lineHeight = 14.sp,
                        textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun HomeSensorSelectionRadio(selected: Boolean, secondaryText: Color, id: String) {
    RadioButton(
        selected = selected,
        onClick = null,
        modifier = Modifier.size(24.dp).testTag("home_sensor_radio_$id"),
        colors = RadioButtonDefaults.colors(
            selectedColor = MaterialTheme.colorScheme.primary,
            unselectedColor = secondaryText
        )
    )
}
