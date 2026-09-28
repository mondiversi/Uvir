package me.mondiversi.uvir

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Name and disclosure chevron open the selection dialog without a pressed background. */
@Composable
internal fun UvirSensorSelector(
    sensorDisplayName: String,
    sensorSelectionEnabled: Boolean,
    sensorInfoEnabled: Boolean,
    sensorProfiles: List<UvirSensorProfile>,
    sensorConnectionModes: Map<String, SensorConnectionMode>,
    selectedConnectionMode: SensorConnectionMode,
    selectedSensorDeviceId: String,
    primaryText: Color,
    secondaryText: Color,
    onOpenSensorInfo: () -> Unit,
    onSensorSelected: (String) -> Unit,
    selectionExpanded: Boolean,
    onSelectionExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    sensorStatusIndicators: Map<String, UvirStatusIndicator> = emptyMap(),
    statusPulseAlpha: Float = 1f,
    dialogColor: Color = MaterialTheme.colorScheme.surface,
    alertMonitoringDeviceIds: Set<String> = emptySet(),
    onConnectionModeSelected: (SensorConnectionMode) -> Unit = {},
    connectionSelectionEnabled: Boolean = true,
    wifiEnabled: Boolean = true,
    bluetoothEnabled: Boolean = true,
    internetEnabled: Boolean = true
) {
    val menuEnabled = sensorSelectionEnabled
    val displayName = sensorDisplayName.ifBlank { stringResource(R.string.sensor_no_selection) }
    val alertIds = remember(alertMonitoringDeviceIds) {
        alertMonitoringDeviceIds.map(::normalizeSensorDeviceId).filter { it.isNotBlank() }.toSet()
    }
    val foreignAlerts = uvirOtherSensorHasAlerts(selectedSensorDeviceId, alertIds)
    val alertPulseAlpha = if (alertIds.isNotEmpty()) rememberUvirSensorAlertPulseAlpha() else 0f
    val alertColor = uvirAlertSessionIndicatorColor(androidx.compose.foundation.isSystemInDarkTheme())
    Row(
        modifier = modifier.height(36.dp).testTag("home_sensor_selector"),
        verticalAlignment = Alignment.CenterVertically
    ) {
            Row(
                modifier = Modifier.weight(1f, fill = false).height(36.dp)
                    .testTag("home_sensor_name").clip(RoundedCornerShape(8.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        enabled = menuEnabled,
                        onClick = { onSelectionExpandedChange(true) }
                    )
                    .uvirAccessibleAction(
                        label = stringResource(R.string.sensor_selection_title) + ": " + displayName,
                        enabled = menuEnabled,
                        onClick = { onSelectionExpandedChange(true) }),
                verticalAlignment = Alignment.CenterVertically
            ) {
                UvirScrollingText(text = displayName,
                    modifier = Modifier.weight(1f, fill = false).testTag("home_sensor_name_text"),
                    color = if (foreignAlerts) lerp(primaryText, alertColor, alertPulseAlpha) else primaryText,
                    style = LocalTextStyle.current.copy(fontSize = 22.sp, fontWeight = FontWeight.Bold))
                Spacer(Modifier.width(6.dp))
                UvirDisclosureChevron(
                    tint = secondaryText,
                    modifier = Modifier.testTag("home_sensor_name_chevron")
                )
            }
        UvirHomeSensorSelectionDialog(
            expanded = selectionExpanded,
            onDismissRequest = { onSelectionExpandedChange(false) },
            sensorProfiles = sensorProfiles,
            sensorConnectionModes = sensorConnectionModes,
            sensorStatusIndicators = sensorStatusIndicators,
            statusPulseAlpha = statusPulseAlpha,
            alertMonitoringDeviceIds = alertIds,
            alertPulseAlpha = alertPulseAlpha,
            selectedConnectionMode = selectedConnectionMode,
            selectedSensorDeviceId = selectedSensorDeviceId,
            sensorInfoEnabled = sensorInfoEnabled,
            onOpenSensorInfo = onOpenSensorInfo,
            primaryText = primaryText, secondaryText = secondaryText,
            containerColor = dialogColor,
            onSensorSelected = { request ->
                onSelectionExpandedChange(false)
                if (request == NO_SENSOR_SELECTED_REQUEST) {
                    if (selectedSensorDeviceId.isNotBlank()) onSensorSelected(request)
                } else if (!request.equals(selectedSensorDeviceId, ignoreCase = true)) onSensorSelected(request)
            },
            selectionEnabled = menuEnabled,
            onConnectionModeSelected = onConnectionModeSelected,
            connectionSelectionEnabled = connectionSelectionEnabled,
            wifiEnabled = wifiEnabled, bluetoothEnabled = bluetoothEnabled, internetEnabled = internetEnabled
        )
    }
}
