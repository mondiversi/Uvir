package me.mondiversi.uvir

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val UvirHoldConfirmationSentence = Regex("(?s)^(.+[.!?。！？؟।])\\s*(.+)$")

@Composable
internal fun UvirHoldConfirmationMessage(
    message: String,
    actionLabel: String,
    holdDurationSeconds: Int,
    replaceEmbeddedInstruction: Boolean = true
) {
    val mainMessage =
        remember(message, replaceEmbeddedInstruction) {
            if (replaceEmbeddedInstruction) {
                splitUvirHoldConfirmationMessage(message).first
            } else {
                message.trim()
            }
        }
    Column {
        Text(mainMessage)
        Spacer(Modifier.height(8.dp))
        UvirHoldConfirmationInstruction(actionLabel, holdDurationSeconds)
    }
}

@Composable
internal fun UvirHoldConfirmationInstruction(actionLabel: String, holdDurationSeconds: Int) {
    Text(
        text = stringResource(R.string.hold_action_seconds_confirmation, actionLabel, holdDurationSeconds),
        fontWeight = FontWeight.Medium
    )
}

internal fun splitUvirHoldConfirmationMessage(
    message: String
): Pair<String, String?> {
    val normalized = message.trim()
    val match = UvirHoldConfirmationSentence.matchEntire(normalized)
    return if (match != null) {
        match.groupValues[1].trim() to match.groupValues[2].trim()
    } else {
        normalized to null
    }
}

@Composable
internal fun UvirDeleteConfirmationMessage(warning: String) {
    UvirHoldConfirmationMessage(
        message = warning,
        actionLabel = stringResource(R.string.delete),
        holdDurationSeconds = 2
    )
}

@Composable
internal fun HoldToConfirmDeleteButton(
    label: String,
    onConfirmed: () -> Unit,
    holdDurationMillis: Long = 2_000L,
    compactLabel: Boolean = false
) {
    HoldToConfirmActionButton(
        label = label,
        onConfirmed = onConfirmed,
        holdDurationMillis = holdDurationMillis,
        compactLabel = compactLabel
    )
}

@Composable
internal fun HoldToConfirmActionButton(
    label: String,
    onConfirmed: () -> Unit,
    enabled: Boolean = true,
    holdDurationMillis: Long = 2_000L,
    compactLabel: Boolean = false,
    actionColor: Color = UvirDestructiveActionColor
) {
    var isHolding by remember { mutableStateOf(false) }
    val holdProgress = remember { Animatable(0f) }
    val accessibilityConfirmationArmed = remember { mutableStateOf(false) }
    val accessibilityConfirmationMessage =
        stringResource(R.string.accessibility_activate_again_to_confirm)

    val currentOnConfirmed by
        rememberUpdatedState(onConfirmed)

    LaunchedEffect(
        isHolding,
        enabled,
        holdDurationMillis
    ) {
        if (!enabled || !isHolding) {
            holdProgress.snapTo(0f)
            return@LaunchedEffect
        }

        holdProgress.snapTo(0f)
        holdProgress.animateTo(
            targetValue = 1f,
            animationSpec =
                tween(
                    durationMillis =
                        holdDurationMillis
                            .coerceIn(1L, Int.MAX_VALUE.toLong())
                            .toInt(),
                    easing = LinearEasing
                )
        )

        if (isHolding) {
            accessibilityConfirmationArmed.value = false
            currentOnConfirmed()
        }
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier =
            Modifier
                .heightIn(min = 48.dp)
                .clip(RoundedCornerShape(50))
                .drawBehind {
                    val progressWidth = size.width * holdProgress.value
                    if (progressWidth > 0f) {
                        drawRect(
                            color = actionColor.copy(alpha = 0.12f),
                            topLeft =
                                Offset(
                                    x =
                                        if (layoutDirection == LayoutDirection.Rtl) {
                                            size.width - progressWidth
                                        } else {
                                            0f
                                        },
                                    y = 0f
                                ),
                            size = Size(progressWidth, size.height)
                        )
                    }
                }
                // Pointer gestures keep the physical press-and-hold behavior without publishing a
                // competing semantic click action. Assistive technologies use the action below.
                .pointerInput(enabled) {
                    if (enabled) {
                        detectTapGestures(
                            onPress = {
                                isHolding = true
                                try {
                                    awaitRelease()
                                } finally {
                                    isHolding = false
                                }
                            }
                        )
                    }
                }
                .uvirAccessibleAction(
                    label = label,
                    enabled = enabled,
                    stateText =
                        if (accessibilityConfirmationArmed.value) {
                            accessibilityConfirmationMessage
                        } else {
                            null
                        },
                    onClick = {
                        if (accessibilityConfirmationArmed.value) {
                            accessibilityConfirmationArmed.value = false
                            currentOnConfirmed()
                        } else {
                            accessibilityConfirmationArmed.value = true
                        }
                    }
                )
                .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(
            text = label,
            color = if (enabled) actionColor else actionColor.copy(alpha = 0.38f),
            fontSize =
                if (compactLabel) {
                    11.sp
                } else {
                    14.sp
                },
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            softWrap = false
        )
    }
}
