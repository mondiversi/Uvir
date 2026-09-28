package me.mondiversi.uvir

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.RadioButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Outside the sensor-specific screen, so switching sensors keeps this window open. */
@Composable
internal fun UvirSensorSelectionDialog(
    sensorProfiles: List<UvirSensorProfile>,
    selectedSensorDeviceId: String,
    enabled: Boolean,
    primaryText: Color,
    secondaryText: Color,
    cardColor: Color,
    onSensorSelected: (String) -> Unit,
    onDismissRequest: () -> Unit,
    connectedSensorDeviceId: String = "",
    onSensorInfoRequested: (String) -> Unit = {}
) {
    UvirActionTheme {
    UvirAlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(R.string.sensor_selection_title)) },
        text = {
            Box(Modifier.fillMaxWidth()) {
                Column(
                    Modifier.fillMaxWidth()
                        .selectableGroup().testTag("sensor_profile_menu")
                ) {
                    SensorSelectionRow(
                        name = stringResource(R.string.sensor_no_selection), activity = null,
                        selected = selectedSensorDeviceId.isBlank(), enabled = enabled,
                        primaryText = primaryText, secondaryText = secondaryText,
                        description = stringResource(R.string.sensor_no_selection),
                        onClick = {
                            if (selectedSensorDeviceId.isNotBlank()) onSensorSelected(NO_SENSOR_SELECTED_REQUEST)
                        }
                    )
                    sensorProfiles.sortedBy { it.displayName.lowercase() }.forEach { profile ->
                        val selected = profile.hardwareUid.equals(selectedSensorDeviceId, ignoreCase = true)
                        SensorSelectionRow(
                            name = profile.displayName.ifBlank { profile.hardwareUid },
                            activity = formatSensorListLastActivity(profile.lastSeenAt,
                                LocalUvirDateFormat.current, LocalUvirTimeFormat.current),
                            selected = selected, enabled = enabled,
                            primaryText = primaryText, secondaryText = secondaryText,
                            infoEnabled = enabled && connectedSensorDeviceId.isNotBlank() &&
                                profile.hardwareUid.equals(connectedSensorDeviceId, ignoreCase = true),
                            infoTag = "sensor_profile_info_${profile.hardwareUid}",
                            onInfo = { onSensorInfoRequested(profile.hardwareUid) },
                            onClick = { if (!selected) onSensorSelected(profile.hardwareUid) }
                        )
                    }
                }
            }
        },
        confirmButton = {},
        containerColor = cardColor,
        titleContentColor = primaryText,
        textContentColor = primaryText
    )
    }
}

@Composable
private fun SensorSelectionRow(
    name: String,
    activity: String?,
    selected: Boolean,
    enabled: Boolean,
    primaryText: Color,
    secondaryText: Color,
    description: String? = null,
    infoEnabled: Boolean = false,
    infoTag: String = "",
    onInfo: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .heightIn(min = 48.dp).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f)
                .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
                .then(if (description == null) Modifier else Modifier.semantics { contentDescription = description }),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(selected = selected, onClick = null, enabled = enabled,
                colors = RadioButtonDefaults.colors(
                    selectedColor = MaterialTheme.colorScheme.primary, unselectedColor = secondaryText,
                    disabledSelectedColor = secondaryText.copy(alpha = 0.42f),
                    disabledUnselectedColor = secondaryText.copy(alpha = 0.42f)
                ))
            Spacer(Modifier.width(10.dp))
            Text(name, Modifier.weight(1f), color = if (enabled) primaryText else secondaryText,
                fontSize = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (activity != null) {
                Spacer(Modifier.width(10.dp))
                Text(activity, color = secondaryText, fontSize = 11.sp, lineHeight = 12.sp,
                    modifier = Modifier.testTag("${infoTag}_activity"),
                    textAlign = TextAlign.End, maxLines = 2, softWrap = false)
            }
        }
        if (onInfo != null) {
            Spacer(Modifier.width(4.dp))
            UvirSensorInfoButton(
                enabled = infoEnabled,
                primaryText = primaryText, secondaryText = secondaryText,
                onClick = onInfo,
                contentDescription = stringResource(R.string.sensor_info_action) + ": " + name,
                modifier = Modifier.testTag(infoTag)
            )
        }
    }
}

/** Independent row action: opening info must not select or reconnect a sensor. */
@Composable
internal fun UvirSensorInfoButton(
    enabled: Boolean,
    primaryText: Color,
    secondaryText: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String? = null
) {
    UvirAccessibleIconButton(
        contentDescription = contentDescription ?: stringResource(R.string.sensor_info_action),
        enabled = enabled,
        onClick = onClick,
        modifier = modifier.size(32.dp),
        pressedVisualSize = 28.dp
    ) {
        UvirMenuIcon(
            type = MenuIconType.VERSION_INFO,
            modifier = Modifier.size(20.dp),
            tint = if (enabled) primaryText else secondaryText.copy(alpha = 0.42f)
        )
    }
}
