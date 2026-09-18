package me.mondiversi.uvir

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun UvirStartAllAlertsFloatingButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val description =
        stringResource(R.string.start_value_alert_session_accessibility)

    Surface(
        onClick = onClick,
        modifier =
            modifier
                .size(UvirSecondaryFloatingControlSize)
                .semantics {
                    contentDescription = description
                },
        shape = CircleShape,
        color = UvirAttentionColor,
        contentColor = Color.White,
        shadowElevation = 5.dp
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            UvirMenuIcon(
                type = MenuIconType.ALERT_LOG,
                modifier = Modifier.size(23.dp),
                tint = Color.White
            )
        }
    }
}

@Composable
internal fun UvirStopAllAlertsFloatingButton(
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val description =
        stringResource(R.string.stop_all_value_alerts_accessibility)
    val containerColor =
        if (enabled) {
            UvirDestructiveActionBaseColor
        } else {
            uvirDisabledActionContainerColor()
        }
    val contentColor =
        if (enabled) {
            Color.White
        } else {
            uvirDisabledActionContentColor()
        }

    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier =
            modifier
                .size(UvirSecondaryFloatingControlSize)
                .semantics {
                    contentDescription = description
                },
        shape = CircleShape,
        color = containerColor,
        contentColor = contentColor,
        shadowElevation = 5.dp
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Box(modifier = Modifier.size(23.dp)) {
                UvirMenuIcon(
                    type = MenuIconType.ALERT_LOG,
                    modifier = Modifier.fillMaxSize(),
                    tint = contentColor
                )
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

@Composable
internal fun UvirStartAllAlertsDialog(
    note: String,
    repeatHoursText: String,
    repeatMinutesText: String,
    repeatSecondsText: String,
    cardColor: Color,
    primaryText: Color,
    secondaryText: Color,
    onNoteChanged: (String) -> Unit,
    onRepeatHoursChanged: (String) -> Unit,
    onRepeatMinutesChanged: (String) -> Unit,
    onRepeatSecondsChanged: (String) -> Unit,
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
    val dialogScrollbar =
        rememberUvirDialogScrollbar(
            color = secondaryText.copy(alpha = 0.58f)
        )
    AlertDialog(
        onDismissRequest = onDismissRequest,
        modifier = dialogScrollbar.dialogModifier,
        title = {
            Text(stringResource(R.string.start_value_alert_session_title))
        },
        text = {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .then(dialogScrollbar.viewportModifier)
            ) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .verticalScroll(dialogScrollbar.scrollState),
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
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Text(stringResource(R.string.stop_all_value_alerts_title))
        },
        text = {
            Text(stringResource(R.string.stop_all_value_alerts_message))
        },
        dismissButton = {
            TextButton(
                onClick = onStop,
                enabled = stopEnabled,
                colors =
                    ButtonDefaults.textButtonColors(
                        contentColor = UvirDestructiveActionColor
                    )
            ) {
                Text(stringResource(R.string.stop_all_value_alerts_action))
            }
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
