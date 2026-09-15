package me.mondiversi.uvir

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

@Composable
internal fun UvirConditionalAcquisitionCard(
    enabled: Boolean,
    match: AcquisitionConditionMatch,
    action: AcquisitionConditionAction,
    rules: List<ThresholdAlertRule>,
    waiting: Boolean,
    locked: Boolean,
    cardColor: Color,
    primaryText: Color,
    secondaryText: Color,
    numericFormat: UvirNumericFormat,
    onEnabled: (Boolean) -> Unit,
    onMatch: (AcquisitionConditionMatch) -> Unit,
    onAction: (AcquisitionConditionAction) -> Unit,
    onConfigure: () -> Unit = {}
) {
    val rulesAvailable = rules.any { it.enabled }
    // Expansion is always available; it does not change an already running plan.
    var lockedExpanded by rememberSaveable(locked, enabled) { mutableStateOf(enabled) }
    val expanded = if (locked) lockedExpanded else enabled
    val choicesEnabled = rulesAvailable && !locked
    val choicesText = primaryText.copy(alpha = if (choicesEnabled) 1f else 0.62f)
    val descriptionText = if (choicesEnabled) secondaryText else choicesText
    UvirAutomaticAcquisitionSection(
        title = stringResource(R.string.conditional_acquisition),
        icon = AutomaticSettingIconType.CONDITIONAL,
        expanded = expanded,
        onExpandedChange = { if (locked) lockedExpanded = it else onEnabled(it) },
        cardColor = cardColor, primaryText = primaryText, secondaryText = secondaryText
    ) {
        OutlinedButton(
            onClick = onConfigure, enabled = !locked,
            modifier = Modifier.fillMaxWidth(),
            colors = uvirOutlinedActionColors(primaryText),
            border = uvirOutlinedActionBorder(!locked, secondaryText)
        ) {
            Text(stringResource(R.string.conditional_configure))
        }
        if (waiting) {
            Text(stringResource(R.string.conditional_waiting),
                fontSize = 12.sp, color = secondaryText)
        }
        if (!locked) {
            Text(stringResource(R.string.conditional_when), fontSize = 13.sp,
                fontWeight = FontWeight.Bold, color = choicesText)
            AcquisitionConditionMatch.entries.forEach { item ->
                SettingsRadioChoiceRow(item == match, primaryText, secondaryText,
                    onClick = { if (choicesEnabled) onMatch(item) }, enabled = choicesEnabled) {
                    Text(stringResource(when (item) {
                        AcquisitionConditionMatch.ANY -> R.string.conditional_any
                        AcquisitionConditionMatch.ALL -> R.string.conditional_all
                        AcquisitionConditionMatch.NONE -> R.string.conditional_none
                    }), fontSize = 13.sp, color = choicesText)
                }
            }
            Text(stringResource(R.string.conditional_action), fontSize = 13.sp,
                fontWeight = FontWeight.Bold, color = choicesText)
            AcquisitionConditionAction.entries.forEach { item ->
                SettingsRadioChoiceRow(item == action, primaryText, secondaryText,
                    onClick = { if (choicesEnabled) onAction(item) }, enabled = choicesEnabled) {
                    Column {
                        Text(stringResource(when (item) {
                            AcquisitionConditionAction.START -> R.string.conditional_start
                            AcquisitionConditionAction.STOP -> R.string.conditional_stop
                            AcquisitionConditionAction.ACQUIRE -> R.string.conditional_record
                        }), fontSize = 13.sp, color = choicesText)
                        Text(stringResource(when (item) {
                            AcquisitionConditionAction.START -> R.string.conditional_start_description
                            AcquisitionConditionAction.STOP -> R.string.conditional_stop_description
                            AcquisitionConditionAction.ACQUIRE -> R.string.conditional_record_description
                        }), fontSize = 12.sp, color = descriptionText)
                    }
                }
            }
        } else {
            Text(stringResource(when (match) {
                AcquisitionConditionMatch.ANY -> R.string.conditional_any
                AcquisitionConditionMatch.ALL -> R.string.conditional_all
                AcquisitionConditionMatch.NONE -> R.string.conditional_none
            }) + " · " + stringResource(when (action) {
                AcquisitionConditionAction.START -> R.string.conditional_start
                AcquisitionConditionAction.STOP -> R.string.conditional_stop
                AcquisitionConditionAction.ACQUIRE -> R.string.conditional_record
            }), fontSize = 13.sp, color = choicesText)
            Text(stringResource(when (action) {
                AcquisitionConditionAction.START -> R.string.conditional_start_description
                AcquisitionConditionAction.STOP -> R.string.conditional_stop_description
                AcquisitionConditionAction.ACQUIRE -> R.string.conditional_record_description
            }), fontSize = 12.sp, color = secondaryText)
        }
        Text(stringResource(R.string.conditional_limits_description),
            fontSize = 12.sp, color = if (locked) secondaryText else descriptionText)
    }
}
