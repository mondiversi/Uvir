package me.mondiversi.uvir

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Alert registration uses the same island rhythm as automatic acquisition. */
@Composable
internal fun UvirAlertRegistrationForm(
    listState: LazyListState,
    repeatHours: String,
    repeatMinutes: String,
    repeatSeconds: String,
    useStartDelay: Boolean,
    startHours: String,
    startMinutes: String,
    startSeconds: String,
    useDuration: Boolean,
    durationHours: String,
    durationMinutes: String,
    durationSeconds: String,
    limitEnabled: Boolean,
    maxRegistrations: String,
    doNotRecord: Boolean,
    note: String,
    active: Boolean,
    configuredCount: Int,
    cardColor: Color,
    primaryText: Color,
    secondaryText: Color,
    onRepeatHours: (String) -> Unit,
    onRepeatMinutes: (String) -> Unit,
    onRepeatSeconds: (String) -> Unit,
    onStartDelayEnabled: (Boolean) -> Unit,
    onStartHours: (String) -> Unit,
    onStartMinutes: (String) -> Unit,
    onStartSeconds: (String) -> Unit,
    onDurationEnabled: (Boolean) -> Unit,
    onDurationHours: (String) -> Unit,
    onDurationMinutes: (String) -> Unit,
    onDurationSeconds: (String) -> Unit,
    onLimitEnabled: (Boolean) -> Unit,
    onMaxRegistrations: (String) -> Unit,
    onDoNotRecord: (Boolean) -> Unit,
    onNote: (String) -> Unit,
    onConfigureValues: () -> Unit
) {
    val editable = !active
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 72.dp),
        verticalArrangement = Arrangement.spacedBy(UvirIslandSpacing)
    ) {
        item {
            SettingsIsland(containerColor = cardColor, contentColor = primaryText) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AutomaticSettingIcon(AutomaticSettingIconType.INTERVAL, tint = primaryText)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.threshold_repeat_label), fontWeight = FontWeight.Bold)
                }
                HorizontalDivider(color = secondaryText.copy(alpha = 0.28f))
                DurationFields(
                    repeatHours, repeatMinutes, repeatSeconds,
                    onRepeatHours, onRepeatMinutes, onRepeatSeconds,
                    enabled = editable, maxHours = 24
                )
            }
        }
        item {
            SettingsIsland(containerColor = cardColor, contentColor = primaryText) {
                OutlinedButton(
                    onClick = onConfigureValues,
                    modifier = Modifier.fillMaxWidth(),
                    colors = uvirOutlinedActionColors(primaryText),
                    border = uvirOutlinedActionBorder(true, secondaryText)
                ) {
                    UvirLabeledButtonContent(text = stringResource(
                        R.string.alert_configure_values_count, configuredCount)) {
                        UvirMenuIcon(type = MenuIconType.ALERT_LOG, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
        item {
            UvirAutomaticAcquisitionSection(
                title = stringResource(R.string.scheduled_start),
                icon = AutomaticSettingIconType.START_DELAY,
                expanded = useStartDelay,
                onExpandedChange = onStartDelayEnabled,
                cardColor = cardColor, primaryText = primaryText, secondaryText = secondaryText,
                controlsEnabled = editable
            ) {
                DurationFields(startHours, startMinutes, startSeconds,
                    onStartHours, onStartMinutes, onStartSeconds, enabled = editable)
            }
        }
        item {
            UvirAutomaticAcquisitionSection(
                title = stringResource(R.string.scheduled_end),
                icon = AutomaticSettingIconType.DURATION,
                expanded = useDuration,
                onExpandedChange = onDurationEnabled,
                cardColor = cardColor, primaryText = primaryText, secondaryText = secondaryText,
                controlsEnabled = editable
            ) {
                DurationFields(durationHours, durationMinutes, durationSeconds,
                    onDurationHours, onDurationMinutes, onDurationSeconds, enabled = editable)
            }
        }
        item {
            UvirAutomaticAcquisitionSection(
                title = stringResource(R.string.alert_max_registrations),
                icon = AutomaticSettingIconType.MAXIMUM,
                expanded = limitEnabled,
                onExpandedChange = onLimitEnabled,
                cardColor = cardColor, primaryText = primaryText, secondaryText = secondaryText,
                controlsEnabled = editable && !doNotRecord
            ) {
                NumberField(
                    value = maxRegistrations,
                    onValueChange = onMaxRegistrations,
                    label = stringResource(R.string.alert_registration_count),
                    modifier = Modifier.fillMaxWidth(),
                    maxChars = 6,
                    enabled = editable && !doNotRecord
                )
            }
        }
        item {
            UvirAutomaticAcquisitionSection(
                title = stringResource(R.string.alert_session_do_not_record),
                icon = AutomaticSettingIconType.NO_SAVE,
                expanded = doNotRecord,
                onExpandedChange = onDoNotRecord,
                cardColor = cardColor, primaryText = primaryText, secondaryText = secondaryText,
                controlsEnabled = editable
            ) {
                Text(stringResource(R.string.alert_session_do_not_record_description),
                    color = secondaryText, fontSize = 12.sp)
            }
        }
        item {
            SettingsIsland(containerColor = cardColor, contentColor = primaryText) {
                UvirLimitedNoteField(
                    value = note, onValueChange = onNote,
                    label = stringResource(R.string.default_note),
                    supportingText = stringResource(R.string.add_note_or_leave_empty),
                    secondaryText = secondaryText,
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    enabled = editable
                )
            }
        }
    }
}
