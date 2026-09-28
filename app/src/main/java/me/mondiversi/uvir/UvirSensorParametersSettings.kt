package me.mondiversi.uvir

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private const val SENSOR_DISASSOCIATE_HOLD_SECONDS = 5
private const val SENSOR_POWER_OFF_HOLD_SECONDS = 5

@Composable
internal fun UvirSensorParametersSettings(
    context: Context,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    sensorSettingsEnabled: Boolean,
    sensorName: String,
    onSensorNameChange: (String) -> Unit,
    sensorNameEditable: Boolean,
    autonomousRecordingEnabled: Boolean,
    onAutonomousRecordingEnabledChange: (Boolean) -> Unit,
    automaticShutdownEnabled: Boolean,
    onAutomaticShutdownEnabledChange: (Boolean) -> Unit,
    automaticShutdownHoursText: String,
    onAutomaticShutdownHoursTextChange: (String) -> Unit,
    automaticShutdownMinutesText: String,
    onAutomaticShutdownMinutesTextChange: (String) -> Unit,
    automaticShutdownSecondsText: String,
    onAutomaticShutdownSecondsTextChange: (String) -> Unit,
    statusLedEnabled: Boolean,
    onStatusLedEnabledChange: (Boolean) -> Unit,
    statusLedBrightness: Float,
    onStatusLedBrightnessChange: (Float) -> Unit,
    statusBuzzerEnabled: Boolean,
    onStatusBuzzerEnabledChange: (Boolean) -> Unit,
    statusBuzzerVolume: Float,
    onStatusBuzzerVolumeChange: (Float) -> Unit,
    externalCommandEnabled: Boolean,
    onExternalCommandEnabledChange: (Boolean) -> Unit,
    statusLedTestEnabled: Boolean,
    onTestStatusLed: suspend () -> Boolean,
    statusBuzzerTestEnabled: Boolean,
    onTestStatusBuzzer: suspend () -> Boolean,
    sensorAssociated: Boolean,
    sensorPowerOffEnabled: Boolean,
    onSensorPowerOffRequested: () -> Unit,
    onDisassociateSensor: suspend () -> Boolean,
    cardColor: Color,
    primaryText: Color,
    secondaryText: Color
) {
    val resources = context.resources
    val coroutineScope = rememberCoroutineScope()
    var showStatusLedInfo by rememberSaveable { mutableStateOf(false) }
    var showStatusBuzzerInfo by rememberSaveable { mutableStateOf(false) }
    var showExternalCommandInfo by rememberSaveable { mutableStateOf(false) }
    var showSensorDisassociateConfirmation by rememberSaveable {
        mutableStateOf(false)
    }
    var sensorDisassociateInProgress by rememberSaveable {
        mutableStateOf(false)
    }
    val valueCorrectedText =
        stringResource(R.string.value_out_of_limits_corrected)
    val normalizeAutomaticShutdownDuration = {
        val normalized =
            normalizeDuration(
                automaticShutdownHoursText,
                automaticShutdownMinutesText,
                automaticShutdownSecondsText,
                minimumTotalSeconds = 60L,
                maximumTotalSeconds = 86_400L
            )
        onAutomaticShutdownHoursTextChange(normalized.hoursText)
        onAutomaticShutdownMinutesTextChange(normalized.minutesText)
        onAutomaticShutdownSecondsTextChange(normalized.secondsText)
        if (normalized.corrected) {
            showUvirBottomMessage(context, valueCorrectedText)
        }
    }

    SettingsSection(
        settingsPage = UvirSettingsPage.SENSOR_PARAMETERS,
        title = stringResource(R.string.settings_section_sensor_parameters),
        titleIcon = ConnectivityIconType.GENERAL,
        expanded = expanded,
        enabled = sensorSettingsEnabled,
        dimContentWhenDisabled = false,
        onExpandedChange = { value ->
            onExpandedChange(value)
            saveSettingsSectionExpanded(
                context,
                KEY_SETTINGS_SENSOR_PARAMETERS_EXPANDED,
                value
            )
        },
        containerColor = cardColor,
        titleColor = primaryText,
        chevronColor = secondaryText,
        dividerColor = secondaryText.copy(alpha = 0.28f),
        contentSpacing = UvirIslandSpacing,
        wrapDetailContent = false
    ) {
        SettingsStaticIsland(
            title = stringResource(R.string.sensor_general_profile_title),
            titleIcon = ConnectivityIconType.SENSOR,
            containerColor = cardColor,
            titleColor = primaryText
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(UvirSettingsControlGap)
            ) {
                SettingsPageDescription(
                    text = stringResource(R.string.sensor_general_profile_description),
                    color = secondaryText
                )

                Column(
                    verticalArrangement = Arrangement.spacedBy(UvirSettingsRelatedGap)
                ) {
                    OutlinedTextField(
                        value = sensorName,
                        onValueChange = {
                            onSensorNameChange(
                                it.take(MAX_SENSOR_DISPLAY_NAME_LENGTH)
                            )
                        },
                        modifier = Modifier.fillMaxWidth().settingsCommitOnBlur(SettingsSaveGroup.NAME),
                        enabled = sensorNameEditable,
                        singleLine = true,
                        label = {
                            AdaptiveFieldLabel(
                                stringResource(R.string.sensor_name_label)
                            )
                        },
                        colors = UvirOutlinedTextFieldColors()
                    )
                    Text(
                        text = stringResource(R.string.sensor_name_description),
                        color = secondaryText,
                        fontSize = 11.sp,
                        lineHeight = 14.sp
                    )
                }

            }
        }

        SettingsStaticIsland(
            title = stringResource(R.string.sensor_general_operation_title),
            titleIcon = ConnectivityIconType.GENERAL,
            containerColor = cardColor,
            titleColor = primaryText
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(UvirSettingsControlGap)
            ) {
                SettingsPageDescription(
                    text = stringResource(R.string.sensor_general_operation_description),
                    color = secondaryText
                )

                SettingsCheckboxWithDescription(
                    checked = autonomousRecordingEnabled,
                    onCheckedChange = onAutonomousRecordingEnabledChange,
                    title = stringResource(R.string.sensor_autonomous_recording),
                    description = stringResource(
                        R.string.sensor_autonomous_recording_description
                    ),
                    enabled = sensorSettingsEnabled,
                    primaryText = primaryText,
                    secondaryText = secondaryText
                )

                SettingsCheckboxWithDescription(
                    checked = automaticShutdownEnabled,
                    onCheckedChange = onAutomaticShutdownEnabledChange,
                    title = stringResource(R.string.sensor_automatic_shutdown),
                    description = stringResource(
                        R.string.sensor_automatic_shutdown_description
                    ),
                    enabled = sensorSettingsEnabled,
                    primaryText = primaryText,
                    secondaryText = secondaryText
                )

                if (automaticShutdownEnabled) {
                    Column(
                        verticalArrangement =
                            Arrangement.spacedBy(UvirSettingsRelatedGap)
                    ) {
                        Text(
                            text = stringResource(
                                R.string.sensor_automatic_shutdown_after
                            ),
                            color =
                                if (sensorSettingsEnabled) {
                                    primaryText
                                } else {
                                    secondaryText
                                },
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                        DurationFields(
                            hoursText = automaticShutdownHoursText,
                            minutesText = automaticShutdownMinutesText,
                            secondsText = automaticShutdownSecondsText,
                            onHoursChange =
                                onAutomaticShutdownHoursTextChange,
                            onMinutesChange =
                                onAutomaticShutdownMinutesTextChange,
                            onSecondsChange =
                                onAutomaticShutdownSecondsTextChange,
                            enabled = sensorSettingsEnabled,
                            maxHours = 24,
                            onEditingComplete =
                                normalizeAutomaticShutdownDuration
                        )
                    }
                }

            }
        }

        SensorParameterIsland(
            title = stringResource(R.string.sensor_status_led),
            titleIcon = ConnectivityIconType.LED,
            checked = statusLedEnabled,
            onCheckedChange = onStatusLedEnabledChange,
            enabled = sensorSettingsEnabled,
            cardColor = cardColor,
            primaryText = primaryText,
            secondaryText = secondaryText,
            info = {
                ParameterInfoButton(
                    contentDescription = resources.getString(R.string.sensor_led_info_action),
                    tint = primaryText,
                    onClick = { showStatusLedInfo = true }
                )
            }
        ) {
            SettingsPageDescription(stringResource(R.string.sensor_status_led_description), secondaryText)
            SensorParameterSlider(
                title = stringResource(R.string.sensor_led_brightness),
                value = statusLedBrightness,
                enabled = sensorSettingsEnabled && statusLedEnabled,
                primaryText = primaryText,
                secondaryText = secondaryText,
                onValueChange = onStatusLedBrightnessChange
            )

        }

        SensorParameterIsland(
            title = stringResource(R.string.sensor_status_buzzer),
            titleIcon = ConnectivityIconType.SOUND,
            checked = statusBuzzerEnabled,
            onCheckedChange = onStatusBuzzerEnabledChange,
            enabled = sensorSettingsEnabled,
            cardColor = cardColor,
            primaryText = primaryText,
            secondaryText = secondaryText,
            info = {
                ParameterInfoButton(
                    contentDescription = resources.getString(R.string.sensor_buzzer_info_action),
                    tint = primaryText,
                    onClick = { showStatusBuzzerInfo = true }
                )
            }
        ) {
            SettingsPageDescription(stringResource(R.string.sensor_status_buzzer_description), secondaryText)
            SensorParameterSlider(
                title = stringResource(R.string.sensor_buzzer_volume),
                value = statusBuzzerVolume,
                enabled = sensorSettingsEnabled && statusBuzzerEnabled,
                primaryText = primaryText,
                secondaryText = secondaryText,
                onValueChange = onStatusBuzzerVolumeChange
            )

        }

        SensorParameterIsland(
            title = stringResource(R.string.external_command),
            titleIconContent = { tint ->
                AutomaticSettingIcon(AutomaticSettingIconType.EXTERNAL_COMMAND, Modifier.size(20.dp), tint)
            },
            checked = externalCommandEnabled,
            onCheckedChange = onExternalCommandEnabledChange,
            enabled = sensorSettingsEnabled,
            cardColor = cardColor,
            primaryText = primaryText,
            secondaryText = secondaryText,
            info = {
                ParameterInfoButton(
                    contentDescription = resources.getString(R.string.sensor_external_command_info_action),
                    tint = primaryText,
                    onClick = { showExternalCommandInfo = true }
                )
            }
        ) {
            SettingsPageDescription(
                stringResource(R.string.sensor_external_command_info_description), secondaryText
            )
        }

        SensorParameterIsland(
            title = stringResource(R.string.sensor_general_actions_title),
            titleIcon = ConnectivityIconType.ACTIONS,
            cardColor = cardColor,
            primaryText = primaryText,
            secondaryText = secondaryText
        ) {
            SettingsPageDescription(stringResource(R.string.sensor_general_actions_description), secondaryText)
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(UvirActionButtonGap)
            ) {
                OutlinedButton(
                    onClick = onSensorPowerOffRequested,
                    enabled = sensorPowerOffEnabled,
                    modifier = Modifier.fillMaxWidth(),
                    colors = uvirDestructiveOutlinedButtonColors(),
                    border = uvirDestructiveOutlinedButtonBorder(
                        sensorPowerOffEnabled,
                        secondaryText
                    )
                ) {
                    UvirLabeledButtonContent(
                        text = stringResource(R.string.sensor_power_off),
                        fontWeight = FontWeight.Bold
                    ) {
                        UvirMenuIcon(
                            type = MenuIconType.POWER,
                            modifier = Modifier.size(20.dp),
                            strokeScale = UvirDestructiveOutlinedIconStrokeScale
                        )
                    }
                }

                OutlinedButton(
                    onClick = {
                        showSensorDisassociateConfirmation = true
                    },
                    enabled = sensorAssociated,
                    modifier = Modifier.fillMaxWidth(),
                    colors = uvirDestructiveOutlinedButtonColors(),
                    border = uvirDestructiveOutlinedButtonBorder(
                        sensorAssociated,
                        secondaryText
                    )
                ) {
                    UvirLabeledButtonContent(
                        text = stringResource(R.string.sensor_disassociate_action),
                        fontWeight = FontWeight.Bold
                    ) {
                        ConnectivitySectionIcon(
                            type = ConnectivityIconType.SENSOR_CONNECTION,
                            modifier = Modifier.size(20.dp),
                            strokeScale = UvirDestructiveOutlinedIconStrokeScale
                        )
                    }
                }

            }
        }
    }

    if (showStatusLedInfo) {
        UvirStatusLedInfoDialog(
            primaryText = primaryText,
            secondaryText = secondaryText,
            cardColor = cardColor,
            testEnabled = statusLedTestEnabled,
            onTestLed = onTestStatusLed,
            onDismissRequest = { showStatusLedInfo = false }
        )
    }

    if (showStatusBuzzerInfo) {
        UvirStatusBuzzerInfoDialog(
            primaryText = primaryText,
            secondaryText = secondaryText,
            cardColor = cardColor,
            testEnabled = statusBuzzerTestEnabled,
            onTestBuzzer = onTestStatusBuzzer,
            onDismissRequest = { showStatusBuzzerInfo = false }
        )
    }

    if (showExternalCommandInfo) {
        UvirExternalCommandInfoDialog(
            primaryText = primaryText,
            secondaryText = secondaryText,
            cardColor = cardColor,
            onDismissRequest = { showExternalCommandInfo = false }
        )
    }

    if (showSensorDisassociateConfirmation) {
        SensorDisassociateConfirmation(
            inProgress = sensorDisassociateInProgress,
            onInProgressChange = {
                sensorDisassociateInProgress = it
            },
            coroutineScope = coroutineScope,
            onDisassociateSensor = onDisassociateSensor,
            onDismissRequest = {
                showSensorDisassociateConfirmation = false
            },
            cardColor = cardColor,
            primaryText = primaryText,
            secondaryText = secondaryText
        )
    }

}

/** Match Connection: checking the header enables the feature and reveals its controls. */
@Composable
private fun SensorParameterIsland(
    title: String,
    cardColor: Color,
    primaryText: Color,
    secondaryText: Color,
    titleIcon: ConnectivityIconType? = null,
    titleIconContent: (@Composable (Color) -> Unit)? = null,
    checked: Boolean = true,
    onCheckedChange: ((Boolean) -> Unit)? = null,
    enabled: Boolean = true,
    info: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val commit = LocalSettingsCommit.current
    SettingsSection(
        title = title,
        titleIcon = titleIcon,
        titleIconContent = titleIconContent,
        expanded = checked,
        enabled = enabled,
        headerEnabled = enabled,
        dimContentWhenDisabled = false,
        onExpandedChange = onCheckedChange?.let { change ->
            { value -> change(value); commit?.commit() }
        },
        headerControl = UvirSettingsHeaderControl.CHECKBOX,
        highlightExpandedHeader = false,
        showExpandedDivider = true,
        headerTrailingContent = info,
        containerColor = cardColor,
        titleColor = primaryText,
        chevronColor = secondaryText,
        dividerColor = secondaryText.copy(alpha = 0.28f),
        contentSpacing = UvirSettingsControlGap,
        content = content
    )
}

@Composable
private fun ParameterInfoButton(
    contentDescription: String,
    tint: Color,
    onClick: () -> Unit
) {
    UvirAccessibleIconButton(
        contentDescription = contentDescription,
        onClick = onClick,
        modifier = Modifier.size(28.dp)
    ) {
        UvirMenuIcon(
            type = MenuIconType.VERSION_INFO,
            modifier = Modifier.size(17.dp),
            tint = tint
        )
    }
}

@Composable
internal fun SensorParameterSlider(
    title: String,
    value: Float,
    enabled: Boolean,
    primaryText: Color,
    secondaryText: Color,
    onValueChange: (Float) -> Unit
) {
    val commit = LocalSettingsCommit.current
    val sliderValue = if (value <= 1f) 0f else value.coerceIn(0f, 100f)
    Column(verticalArrangement = Arrangement.spacedBy(UvirSettingsRelatedGap)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                color = if (enabled) primaryText else secondaryText,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "${value.roundToInt()}%",
                color = if (enabled) primaryText else secondaryText,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Slider(
            value = sliderValue,
            onValueChange = { sliderPosition ->
                onValueChange(if (sliderPosition <= 0f) 1f else sliderPosition)
            },
            onValueChangeFinished = { commit?.commit() },
            enabled = enabled,
            valueRange = 0f..100f,
            steps = 9
        )
    }
}

@Composable
internal fun SensorPowerOffConfirmation(
    inProgress: Boolean,
    onInProgressChange: (Boolean) -> Unit,
    coroutineScope: CoroutineScope,
    onPowerOffSensor: suspend () -> Boolean,
    onDismissRequest: () -> Unit,
    cardColor: Color,
    primaryText: Color,
    secondaryText: Color
) {
    UvirAlertDialog(
        onDismissRequest = { if (!inProgress) onDismissRequest() },
        title = { Text(stringResource(R.string.sensor_power_off_title)) },
        text = {
            UvirHoldConfirmationMessage(
                message = stringResource(R.string.sensor_power_off_message),
                actionLabel = stringResource(R.string.sensor_power_off_confirm),
                holdDurationSeconds = SENSOR_POWER_OFF_HOLD_SECONDS
            )
        },
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                HoldToConfirmDeleteButton(
                    label = stringResource(R.string.sensor_power_off_confirm),
                    holdDurationMillis = SENSOR_POWER_OFF_HOLD_SECONDS * 1_000L,
                    onConfirmed = {
                        if (!inProgress) {
                            onInProgressChange(true)
                            coroutineScope.launch {
                                val poweredOff = onPowerOffSensor()
                                onInProgressChange(false)
                                if (poweredOff) onDismissRequest()
                            }
                        }
                    }
                )
                TextButton(
                    onClick = onDismissRequest,
                    enabled = !inProgress
                ) {
                    Text(stringResource(R.string.cancel))
                }
            }
        },
        containerColor = cardColor,
        titleContentColor = primaryText,
        textContentColor = secondaryText
    )
}



@Composable
private fun SensorDisassociateConfirmation(
    inProgress: Boolean,
    onInProgressChange: (Boolean) -> Unit,
    coroutineScope: CoroutineScope,
    onDisassociateSensor: suspend () -> Boolean,
    onDismissRequest: () -> Unit,
    cardColor: Color,
    primaryText: Color,
    secondaryText: Color
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val completeMessage =
        stringResource(R.string.sensor_disassociate_complete)
    val failureMessage =
        stringResource(R.string.sensor_disassociate_failed)

    UvirAlertDialog(
        onDismissRequest = { if (!inProgress) onDismissRequest() },
        title = {
            Text(stringResource(R.string.sensor_disassociate_title))
        },
        text = {
            UvirHoldConfirmationMessage(
                message = stringResource(R.string.sensor_disassociate_message),
                actionLabel =
                    stringResource(R.string.sensor_disassociate_confirm),
                holdDurationSeconds = 5
            )
        },
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                HoldToConfirmDeleteButton(
                    label =
                        stringResource(
                            R.string.sensor_disassociate_confirm
                        ),
                    holdDurationMillis = 5_000L,
                    onConfirmed = {
                        if (!inProgress) {
                            onInProgressChange(true)
                            coroutineScope.launch {
                                val disassociated =
                                    onDisassociateSensor()
                                onInProgressChange(false)
                                if (disassociated) {
                                    onDismissRequest()
                                    showUvirBottomMessage(
                                        context,
                                        completeMessage,
                                        longDuration = false
                                    )
                                } else {
                                    showUvirBottomMessage(
                                        context,
                                        failureMessage
                                    )
                                }
                            }
                        }
                    }
                )
                TextButton(
                    onClick = onDismissRequest,
                    enabled = !inProgress
                ) {
                    Text(stringResource(R.string.cancel))
                }
            }
        },
        containerColor = cardColor,
        titleContentColor = primaryText,
        textContentColor = secondaryText
    )
}
