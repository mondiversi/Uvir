package me.mondiversi.uvir

import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal const val UvirSensorListNameMaxWidthFraction = 0.55f

/** Compact modal radio choices retain each sensor's live connection status. */
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
    internetEnabled: Boolean = true,
    simulationActive: Boolean = false,
    onOpenSimulationSettings: () -> Unit = {},
    onAddSensor: () -> Unit = {}
) {
    if (!expanded) return
    val context = LocalContext.current
    val hasSelectedProfile = selectedSensorDeviceId.isNotBlank() && sensorProfiles.any {
        it.hardwareUid.equals(selectedSensorDeviceId, ignoreCase = true)
    }
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
            confirmButton = if (simulationActive) {
                {
                    TextButton(
                        onClick = onOpenSimulationSettings,
                        modifier = Modifier.testTag("home_sensor_simulation_settings")
                    ) {
                        Text(uvirActionLabel(R.string.sensor_simulation_open_settings))
                    }
                }
            } else null,
            dismissButton = if (simulationActive) {
                {
                    UvirDialogCloseButton(onDismissRequest,
                        modifier = Modifier.testTag("home_sensor_simulation_close"))
                }
            } else null,
            fixedBottomContent = if (simulationActive) null else {
                {
                    // This footer stays outside the shared dialog's scrollable sensor list.
                    Column(Modifier.fillMaxWidth().testTag("home_sensor_connection_footer")) {
                        HorizontalDivider(Modifier.padding(top = 10.dp, bottom = 10.dp), color = secondaryText.copy(alpha = .20f))
                        UvirSensorConnectionSelector(
                            selectedMode = selectedConnectionMode.takeIf { hasSelectedProfile },
                            enabled = selectionEnabled && connectionSelectionEnabled && hasSelectedProfile,
                            wifiEnabled = wifiEnabled, bluetoothEnabled = bluetoothEnabled, internetEnabled = internetEnabled,
                            primaryText = primaryText, secondaryText = secondaryText,
                            containerColor = containerColor,
                            onModeSelected = onConnectionModeSelected
                        )
                    }
                }
            },
            text = {
                Box(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.fillMaxWidth()
                            .testTag("home_sensor_selection_menu"),
                        verticalArrangement = Arrangement.spacedBy(SensorRadioOptionGap)
                    ) {
                        if (!simulationActive) {
                            UvirAddSensorAction(
                                onClick = onAddSensor,
                                enabled = selectionEnabled,
                                primaryText = primaryText,
                                secondaryText = secondaryText,
                                modifier = Modifier.testTag("home_sensor_add")
                            )
                            if (sensorProfiles.isNotEmpty()) {
                                HorizontalDivider(color = secondaryText.copy(alpha = .20f))
                            }
                        }
                        if (simulationActive) {
                            Text(
                                text = stringResource(R.string.sensor_source_debug_hint),
                                modifier = Modifier.fillMaxWidth().testTag("home_sensor_simulation_warning"),
                                color = primaryText
                            )
                        }
                        if (!simulationActive && sensorProfiles.isNotEmpty()) Column(Modifier.fillMaxWidth().selectableGroup().testTag("home_sensor_profiles"),
                            verticalArrangement = Arrangement.spacedBy(SensorRadioOptionGap)) {
                            sensorProfiles.sortedBy { it.displayName.lowercase() }.forEach { profile ->
                                val isSelected = !simulationActive && profile.hardwareUid.equals(selectedSensorDeviceId, ignoreCase = true)
                                val alertsActive = normalizeSensorDeviceId(profile.hardwareUid) in alertMonitoringDeviceIds
                                val name = profile.displayName.ifBlank { profile.hardwareUid }
                                val indicator = (sensorStatusIndicators[normalizeSensorDeviceId(profile.hardwareUid)]
                                    ?: uvirStatusIndicator(false, sensorInfoEnabled && isSelected, false, false))
                                    .let { if (it.dot == UvirStatusDot.DEBUG) UvirStatusIndicator(UvirStatusDot.RED, false) else it }
                                val rowText = if (indicator.dot == UvirStatusDot.GREEN || indicator.dot == UvirStatusDot.DEBUG)
                                    primaryText else primaryText.copy(alpha = 0.60f)
                                val statusLabel = stringResource(when (indicator.dot) {
                                    UvirStatusDot.GREEN -> R.string.sensor_status_connected
                                    UvirStatusDot.YELLOW -> R.string.sensor_info_connecting
                                    UvirStatusDot.DEBUG -> R.string.sensor_status_simulated
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
                                val connectionDescription = if (indicator.dot == UvirStatusDot.DEBUG)
                                    statusLabel else "$connectionLabel, $statusLabel"
                                Row(
                                    Modifier.fillMaxWidth().heightIn(min = SensorRadioOptionMinHeight)
                                        .testTag("home_sensor_row_${profile.hardwareUid}"),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    SensorRadioOptionSurface(
                                        modifier = Modifier.weight(1f).heightIn(min = SensorRadioOptionMinHeight)
                                            .testTag("home_sensor_option_${profile.hardwareUid}")
                                            .semantics {
                                                contentDescription = "$name, $connectionDescription$activityLabel"
                                                if (alertsActive) stateDescription = alertsLabel
                                            },
                                        selected = isSelected,
                                        enabled = !simulationActive && selectionEnabled,
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
                                                    type = uvirHomeStatusIconType(indicator.dot == UvirStatusDot.DEBUG, mode),
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
internal fun UvirSensorConnectionSelector(
    selectedMode: SensorConnectionMode?,
    enabled: Boolean,
    wifiEnabled: Boolean,
    bluetoothEnabled: Boolean,
    internetEnabled: Boolean,
    primaryText: Color,
    secondaryText: Color,
    containerColor: Color,
    onModeSelected: (SensorConnectionMode) -> Unit,
    tagPrefix: String = "home"
) {
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)
        .clip(RoundedCornerShape(14.dp))
        .background(primaryText.copy(alpha = .04f).compositeOver(containerColor))
        .testTag("${tagPrefix}_sensor_connection_grid")
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
            ViewModeButton(
                selected = selected, enabled = available,
                alerted = false, contentDescription = label,
                primaryText = primaryText, secondaryText = secondaryText,
                minimumHeight = 72.dp,
                modifier = Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(10.dp))
                    .testTag("${tagPrefix}_source_option_${mode.name}"),
                onClick = { if (mode != selectedMode) onModeSelected(mode) }
            ) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 3.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center) {
                    val tint = LocalContentColor.current
                    UvirHomeSensorSourceIcon(uvirSensorConnectionIconType(mode), tint, iconSize = 20.dp)
                    Spacer(Modifier.height(5.dp))
                    Text(label, color = tint, fontSize = 11.sp, lineHeight = 13.sp,
                        textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

/** One shared add action for the selection menu and the disconnected home island. */
@Composable
internal fun UvirAddSensorAction(
    onClick: () -> Unit,
    primaryText: Color,
    secondaryText: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    centered: Boolean = false
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.fillMaxWidth().heightIn(min = SensorRadioOptionMinHeight),
        contentPadding = PaddingValues(horizontal = SensorRadioOptionHorizontalPadding,
            vertical = SensorRadioOptionVerticalPadding)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = if (centered) Arrangement.Center else Arrangement.Start) {
            val tint = if (enabled) primaryText else secondaryText.copy(alpha = .42f)
            Canvas(Modifier.size(16.dp)) {
                val stroke = 1.6.dp.toPx()
                drawLine(tint, Offset(size.width * .15f, size.height * .5f),
                    Offset(size.width * .85f, size.height * .5f), stroke, StrokeCap.Round)
                drawLine(tint, Offset(size.width * .5f, size.height * .15f),
                    Offset(size.width * .5f, size.height * .85f), stroke, StrokeCap.Round)
            }
            Spacer(Modifier.width(12.dp))
            Text(stringResource(R.string.sensor_add_action), color = tint,
                style = sensorRadioOptionTextStyle())
        }
    }
}

@Composable
private fun HomeSensorSelectionRadio(selected: Boolean, secondaryText: Color, id: String, enabled: Boolean = true) {
    RadioButton(
        selected = selected,
        onClick = null,
        enabled = enabled,
        modifier = Modifier.size(24.dp).testTag("home_sensor_radio_$id")
            .semantics { if (!enabled) disabled() },
        colors = uvirRadioButtonColors(unselectedColor = secondaryText)
    )
}
