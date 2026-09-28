package me.mondiversi.uvir

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun UvirStartAllAlertsFloatingButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconTint: Color? = null,
    iconStrokeWidth: Dp? = null
) {
    val description =
        stringResource(R.string.start_value_alert_session_accessibility)
    val contentColor = iconTint ?: uvirAlertSessionContentColor()
    val interactionSource = remember { MutableInteractionSource() }
    val pressedScale = uvirFloatingPressedScale(interactionSource)

    Surface(
        onClick = onClick,
        interactionSource = interactionSource,
        modifier =
            modifier
                .size(UvirSecondaryFloatingControlSize)
                .scale(pressedScale)
                .uvirHapticOnPress()
                .semantics {
                    contentDescription = description
                },
        shape = CircleShape,
        color = uvirAlertSessionIndicatorColor(isSystemInDarkTheme()),
        contentColor = contentColor,
        shadowElevation = UvirFloatingActionShadowElevation
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            UvirMenuIcon(
                type = MenuIconType.ALERT_LOG,
                modifier = Modifier.size(23.dp),
                tint = contentColor,
                uniformStrokeWidth = iconStrokeWidth
            )
        }
    }
}

@Composable
internal fun UvirStopAllAlertsFloatingButton(
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    opensRegistrationPage: Boolean = false,
    iconTint: Color? = null,
    iconStrokeWidth: Dp? = null
) {
    val description =
        stringResource(if (opensRegistrationPage) R.string.alert_registration_title
            else R.string.stop_all_value_alerts_accessibility)
    val interactionSource = remember { MutableInteractionSource() }
    val pressedScale = uvirFloatingPressedScale(interactionSource, enabled)
    val containerColor =
        if (enabled) {
            if (opensRegistrationPage) uvirAlertSessionIndicatorColor(isSystemInDarkTheme())
            else UvirDestructiveActionBaseColor
        } else {
            uvirDisabledActionContainerColor()
        }
    val contentColor =
        if (enabled) {
            iconTint ?: if (opensRegistrationPage) uvirAlertSessionContentColor() else Color.White
        } else {
            uvirDisabledActionContentColor()
        }

    Surface(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interactionSource,
        modifier =
            modifier
                .size(UvirSecondaryFloatingControlSize)
                .scale(pressedScale)
                .uvirHapticOnPress(enabled)
                .semantics {
                    contentDescription = description
                },
        shape = CircleShape,
        color = containerColor,
        contentColor = contentColor,
        shadowElevation = UvirFloatingActionShadowElevation
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Box(modifier = Modifier.size(23.dp)) {
                UvirMenuIcon(
                    type = MenuIconType.ALERT_LOG,
                    modifier = Modifier.fillMaxSize(),
                    tint = contentColor,
                    uniformStrokeWidth = iconStrokeWidth
                )
                if (!opensRegistrationPage) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawLine(
                            color = contentColor,
                            start = Offset(size.width * 0.12f, size.height * 0.12f),
                            end = Offset(size.width * 0.88f, size.height * 0.88f),
                            strokeWidth = 2.2.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun UvirAlertRegistrationActionButton(
    active: Boolean,
    completedCount: Int,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressedScale = uvirFloatingPressedScale(interactionSource, enabled)
    val content = if (enabled) Color.White else uvirDisabledActionContentColor()
    val countText = completedCount.toString()
    val showHint = uvirFloatingActionHintVisible(interactionSource, enabled)
    Box(modifier = Modifier.size(56.dp)) {
        UvirFloatingActionHint(
            text = stringResource(if (active) R.string.alert_hint_stop else R.string.alert_hint_start),
            visible = showHint
        )
        Surface(
            modifier = Modifier.size(56.dp).scale(pressedScale),
            shape = CircleShape,
            color = when {
                !enabled -> uvirDisabledActionContainerColor()
                else -> uvirDestructiveButtonContainerColor()
            },
            contentColor = content,
            shadowElevation = UvirFloatingActionShadowElevation
        ) {
            UvirAccessibleIconButton(
                contentDescription = stringResource(
                    if (active) R.string.stop_all_value_alerts_accessibility
                    else R.string.start_value_alert_session_accessibility
                ),
                onClick = onClick,
                hapticOnPress = true,
                enabled = enabled,
                pressedColor = content,
                interactionSource = interactionSource,
                modifier = Modifier.fillMaxSize()
            ) {
                if (active) {
                    Text(
                        text = countText,
                        color = content,
                        fontSize = when {
                            countText.length >= 6 -> 12.sp
                            countText.length >= 4 -> 15.sp
                            else -> 18.sp
                        },
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                } else {
                    Canvas(modifier = Modifier.size(20.dp)) {
                        drawCircle(color = content)
                    }
                }
            }
        }
    }
}

@Composable
internal fun UvirStartAllAlertsDialog(
    note: String,
    repeatHoursText: String,
    repeatMinutesText: String,
    repeatSecondsText: String,
    doNotRecord: Boolean,
    cardColor: Color,
    primaryText: Color,
    secondaryText: Color,
    onNoteChanged: (String) -> Unit,
    onRepeatHoursChanged: (String) -> Unit,
    onRepeatMinutesChanged: (String) -> Unit,
    onRepeatSecondsChanged: (String) -> Unit,
    onDoNotRecordChanged: (Boolean) -> Unit,
    onStart: (String, Int) -> Unit,
    onCancel: () -> Unit,
    onDismissRequest: () -> Unit
) {
    val context = LocalContext.current
    val correctedText = stringResource(R.string.value_out_of_limits_corrected)
    fun normalizedRepeat() =
        normalizeDuration(
            hoursText = repeatHoursText,
            minutesText = repeatMinutesText,
            secondsText = repeatSecondsText,
            minimumTotalSeconds = 1L,
            maximumTotalSeconds = MAX_ALERT_REPEAT_SECONDS
        )
    fun applyNormalizedRepeat(showCorrection: Boolean): NormalizedDurationInput {
        val normalized = normalizedRepeat()
        onRepeatHoursChanged(normalized.hoursText)
        onRepeatMinutesChanged(normalized.minutesText)
        onRepeatSecondsChanged(normalized.secondsText)
        if (showCorrection && normalized.corrected) {
            showUvirBottomMessage(context, correctedText)
        }
        return normalized
    }
    UvirAlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Text(stringResource(R.string.start_value_alert_session_title))
        },
        text = {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
            ) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth(),
                    verticalArrangement =
                        androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)
                ) {
                    Text(stringResource(R.string.start_value_alert_session_message))
                    Column(
                        verticalArrangement =
                            androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.threshold_repeat_label),
                            color = primaryText,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                        DurationFields(
                            hoursText = repeatHoursText,
                            minutesText = repeatMinutesText,
                            secondsText = repeatSecondsText,
                            onHoursChange = onRepeatHoursChanged,
                            onMinutesChange = onRepeatMinutesChanged,
                            onSecondsChange = onRepeatSecondsChanged,
                            enabled = true,
                            maxHours = 24,
                            onEditingComplete = {
                                applyNormalizedRepeat(showCorrection = true)
                            }
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = doNotRecord,
                            onCheckedChange = { checked ->
                                onDoNotRecordChanged(checked)
                            },
                            modifier = Modifier
                        )
                        Text(
                            text = stringResource(R.string.alert_session_do_not_record),
                            color = primaryText,
                            fontSize = 14.sp
                        )
                    }
                    Text(
                        text = stringResource(R.string.alert_session_do_not_record_description),
                        color = secondaryText,
                        fontSize = 12.sp
                    )
                    UvirLimitedNoteField(
                        value = note,
                        onValueChange = onNoteChanged,
                        label = stringResource(R.string.default_note),
                        supportingText =
                            stringResource(R.string.add_note_or_leave_empty),
                        secondaryText = secondaryText,
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2
                    )
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onCancel,
                colors =
                    ButtonDefaults.textButtonColors(
                        contentColor = UvirDestructiveActionColor
                    )
            ) {
                Text(stringResource(R.string.cancel))
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val normalized = applyNormalizedRepeat(showCorrection = true)
                    onStart(
                        limitUvirNote(note).trim(),
                        normalized.totalSeconds.toInt()
                    )
                }
            ) {
                Text(stringResource(R.string.start_value_alert_session_action))
            }
        },
        containerColor = cardColor,
        titleContentColor = primaryText,
        textContentColor = secondaryText
    )
}

@Composable
internal fun UvirStopAllAlertsDialog(
    cardColor: Color,
    primaryText: Color,
    secondaryText: Color,
    stopEnabled: Boolean,
    onStop: () -> Unit,
    onContinue: () -> Unit,
    onDismissRequest: () -> Unit
) {
    UvirAlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Text(stringResource(R.string.stop_all_value_alerts_title))
        },
        text = {
            UvirHoldConfirmationMessage(
                message = stringResource(R.string.stop_all_value_alerts_message),
                actionLabel = stringResource(R.string.stop_all_value_alerts_action),
                holdDurationSeconds = 2,
                replaceEmbeddedInstruction = false
            )
        },
        dismissButton = {
            HoldToConfirmActionButton(
                label = stringResource(R.string.stop_all_value_alerts_action),
                onConfirmed = onStop,
                enabled = stopEnabled,
                holdDurationMillis = 2_000L
            )
        },
        confirmButton = {
            TextButton(onClick = onContinue) {
                Text(stringResource(R.string.continue_alert_monitoring))
            }
        },
        containerColor = cardColor,
        titleContentColor = primaryText,
        textContentColor = secondaryText
    )
}
