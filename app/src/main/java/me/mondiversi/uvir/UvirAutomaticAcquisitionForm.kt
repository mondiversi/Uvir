package me.mondiversi.uvir

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun UvirAutomaticAcquisitionForm(
    listState: LazyListState,
    timerHours: String,
    timerMinutes: String,
    timerSeconds: String,
    useStartDelay: Boolean,
    startDelayHours: String,
    startDelayMinutes: String,
    startDelaySeconds: String,
    useDuration: Boolean,
    durationHours: String,
    durationMinutes: String,
    durationSeconds: String,
    limitEnabled: Boolean,
    maxCount: String,
    note: String,
    error: String?,
    conditionalEnabled: Boolean,
    conditionalMatch: AcquisitionConditionMatch,
    conditionalAction: AcquisitionConditionAction,
    conditionalRules: List<ThresholdAlertRule>,
    conditionalWaiting: Boolean,
    conditionalLocked: Boolean,
    externalCommand: Boolean,
    numericFormat: UvirNumericFormat,
    onConditionalEnabled: (Boolean) -> Unit,
    onConditionalMatch: (AcquisitionConditionMatch) -> Unit,
    onConditionalAction: (AcquisitionConditionAction) -> Unit,
    onExternalCommandChanged: (Boolean) -> Unit,
    onConfigureConditions: () -> Unit,
    cardColor: Color,
    primaryText: Color,
    secondaryText: Color,
    onTimerHoursChanged: (String) -> Unit,
    onTimerMinutesChanged: (String) -> Unit,
    onTimerSecondsChanged: (String) -> Unit,
    onIntervalEditingComplete: () -> Unit,
    onUseStartDelayChanged: (Boolean) -> Unit,
    onStartDelayHoursChanged: (String) -> Unit,
    onStartDelayMinutesChanged: (String) -> Unit,
    onStartDelaySecondsChanged: (String) -> Unit,
    onStartDelayEditingComplete: () -> Unit,
    onUseDurationChanged: (Boolean) -> Unit,
    onDurationHoursChanged: (String) -> Unit,
    onDurationMinutesChanged: (String) -> Unit,
    onDurationSecondsChanged: (String) -> Unit,
    onDurationEditingComplete: () -> Unit,
    onLimitEnabledChanged: (Boolean) -> Unit,
    onMaxCountChanged: (String) -> Unit,
    onMaxCountEditingComplete: () -> Unit,
    onNoteChanged: (String) -> Unit
) {
    val scheduleControlsEnabled = !externalCommand && !conditionalLocked
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 72.dp),
        verticalArrangement = Arrangement.spacedBy(UvirIslandSpacing)
    ) {
        item {
            Box(Modifier.alpha(if (scheduleControlsEnabled) 1f else 0.48f)) {
                SettingsIsland(
                    containerColor = cardColor,
                    contentColor = primaryText
                ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AutomaticSettingIcon(
                        type = AutomaticSettingIconType.INTERVAL,
                        tint = primaryText
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.execution_interval),
                        fontWeight = FontWeight.Bold
                    )
                }

                HorizontalDivider(
                    color = secondaryText.copy(alpha = 0.28f)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    NumberField(
                        value = timerHours,
                        onValueChange = onTimerHoursChanged,
                        label = stringResource(R.string.hours),
                        modifier = Modifier.weight(1f),
                        maxChars = 5,
                        enabled = scheduleControlsEnabled,
                        onEditingComplete = onIntervalEditingComplete
                    )
                    NumberField(
                        value = timerMinutes,
                        onValueChange = onTimerMinutesChanged,
                        label = stringResource(R.string.minutes),
                        modifier = Modifier.weight(1f),
                        maxChars = 5,
                        enabled = scheduleControlsEnabled,
                        onEditingComplete = onIntervalEditingComplete
                    )
                    NumberField(
                        value = timerSeconds,
                        onValueChange = onTimerSecondsChanged,
                        label = stringResource(R.string.seconds),
                        modifier = Modifier.weight(1f),
                        maxChars = 5,
                        enabled = scheduleControlsEnabled,
                        onEditingComplete = onIntervalEditingComplete
                    )
                }
            }
        }
        }

        item {
            UvirAutomaticAcquisitionSection(
                title = stringResource(R.string.scheduled_start),
                icon = AutomaticSettingIconType.START_DELAY,
                expanded = useStartDelay, onExpandedChange = onUseStartDelayChanged,
                controlsEnabled = scheduleControlsEnabled,
                cardColor = cardColor, primaryText = primaryText, secondaryText = secondaryText
            ) {
                DurationFields(
                    hoursText = startDelayHours,
                    minutesText = startDelayMinutes,
                    secondsText = startDelaySeconds,
                    onHoursChange = onStartDelayHoursChanged,
                    onMinutesChange = onStartDelayMinutesChanged,
                    onSecondsChange = onStartDelaySecondsChanged,
                    enabled = scheduleControlsEnabled,
                    onEditingComplete = onStartDelayEditingComplete
                )
            }
        }

        item {
            UvirAutomaticAcquisitionSection(
                title = stringResource(R.string.scheduled_end),
                icon = AutomaticSettingIconType.DURATION,
                expanded = useDuration, onExpandedChange = onUseDurationChanged,
                controlsEnabled = scheduleControlsEnabled,
                cardColor = cardColor, primaryText = primaryText, secondaryText = secondaryText
            ) {
                DurationFields(
                    hoursText = durationHours,
                    minutesText = durationMinutes,
                    secondsText = durationSeconds,
                    onHoursChange = onDurationHoursChanged,
                    onMinutesChange = onDurationMinutesChanged,
                    onSecondsChange = onDurationSecondsChanged,
                    enabled = scheduleControlsEnabled,
                    onEditingComplete = onDurationEditingComplete
                )
            }
        }

        item {
            UvirAutomaticAcquisitionSection(
                title = stringResource(R.string.limit_acquisitions),
                icon = AutomaticSettingIconType.MAXIMUM,
                expanded = limitEnabled, onExpandedChange = onLimitEnabledChanged,
                controlsEnabled = scheduleControlsEnabled,
                cardColor = cardColor, primaryText = primaryText, secondaryText = secondaryText
            ) {
                NumberField(
                    value = maxCount,
                    onValueChange = onMaxCountChanged,
                    label = stringResource(
                        R.string.number_of_acquisitions
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    maxChars = 6,
                    enabled = scheduleControlsEnabled,
                    onEditingComplete = onMaxCountEditingComplete
                )
            }
        }

        item {
            UvirConditionalAcquisitionCard(
                enabled = conditionalEnabled,
                match = conditionalMatch, action = conditionalAction,
                rules = conditionalRules, waiting = conditionalWaiting, locked = conditionalLocked,
                controlsEnabled = !externalCommand,
                cardColor = cardColor, primaryText = primaryText, secondaryText = secondaryText,
                numericFormat = numericFormat, onEnabled = onConditionalEnabled,
                onMatch = onConditionalMatch, onAction = onConditionalAction,
                onConfigure = onConfigureConditions)
        }

        item {
            UvirExternalCommandCard(
                enabled = externalCommand,
                locked = conditionalLocked,
                cardColor = cardColor,
                primaryText = primaryText,
                secondaryText = secondaryText,
                onEnabled = onExternalCommandChanged
            )
        }

        item {
            SettingsIsland(
                containerColor = cardColor,
                contentColor = primaryText
            ) {
                UvirLimitedNoteField(
                    value = note,
                    onValueChange = onNoteChanged,
                    modifier = Modifier.fillMaxWidth(),
                    label = stringResource(R.string.default_note),
                    supportingText =
                        stringResource(R.string.add_note_or_leave_empty),
                    secondaryText = secondaryText,
                    minLines = 2
                )
            }
        }

        error?.let { message ->
            item {
                Text(
                    text = message,
                    color = if (isSystemInDarkTheme()) UvirDestructiveActionColor
                        else MaterialTheme.colorScheme.error,
                    fontSize = 12.sp
                )
            }
        }
    }
}
