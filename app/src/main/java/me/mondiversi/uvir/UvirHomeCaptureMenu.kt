package me.mondiversi.uvir

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.indication
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.withTimeoutOrNull

internal enum class UvirCaptureMenuTarget { PRIMARY, AUTOMATIC, ALERT }
private enum class UvirCapturePressOutcome { TAP, DRAG, CANCELLED }
private const val UvirIdleCapturePeakScale = 1.10f

/** A gentle shade change driven by the same phase as the idle size pulse. */
internal fun uvirIdleCaptureColor(base: Color, scale: Float): Color {
    val phase = ((scale - 1f) / (UvirIdleCapturePeakScale - 1f)).coerceIn(0f, 1f)
    return lerp(base, Color.White, phase * 0.30f)
}

internal fun uvirIdleCaptureActionsAvailable(
    liveReady: Boolean,
    sensorOperationsAvailable: Boolean
): Boolean = liveReady && sensorOperationsAvailable

internal fun uvirCaptureMenuPrimarySelected(
    holding: Boolean,
    hovered: UvirCaptureMenuTarget?
): Boolean = holding || hovered == UvirCaptureMenuTarget.PRIMARY

/** Positions are relative to the 56 dp capture button, not to the expanded menu. */
internal fun uvirCaptureMenuTarget(
    position: Offset,
    primarySize: Float,
    secondarySize: Float,
    spacing: Float,
    extraTouchRadius: Float
): UvirCaptureMenuTarget? {
    val center = primarySize / 2f
    val secondaryCenter = -spacing - secondarySize / 2f
    val radiusSquared = (secondarySize / 2f + extraTouchRadius).let { it * it }
    val automaticDistance =
        (position.x - secondaryCenter).let { it * it } +
            (position.y - center).let { it * it }
    val alertDistance =
        (position.x - center).let { it * it } +
            (position.y - secondaryCenter).let { it * it }
    val primaryDistance =
        (position.x - center).let { it * it } +
            (position.y - center).let { it * it }
    return when {
        automaticDistance <= radiusSquared && automaticDistance <= alertDistance ->
            UvirCaptureMenuTarget.AUTOMATIC
        alertDistance <= radiusSquared -> UvirCaptureMenuTarget.ALERT
        primaryDistance <= center * center -> UvirCaptureMenuTarget.PRIMARY
        else -> null
    }
}

@Composable
internal fun UvirHomeCaptureMenu(
    alertActive: Boolean,
    onManual: () -> Unit,
    onAutomatic: () -> Unit,
    onAlert: () -> Unit,
    automaticActive: Boolean = false,
    completedCount: Int = 0,
    alertCompletedCount: Int = 0,
    syncInProgress: Boolean = false,
    automaticIndicatorColor: Color = Color.Unspecified,
    alertIndicatorColor: Color = Color.Unspecified,
    automaticContainerColor: Color = Color.Unspecified
) {
    val primarySize = 56.dp
    val secondarySize = UvirSecondaryFloatingControlSize
    val spacing = UvirFloatingControlSpacing
    val secondaryCenterOffset = (primarySize - secondarySize) / 2
    var menuOpen by remember { mutableStateOf(false) }
    var hovered by remember { mutableStateOf<UvirCaptureMenuTarget?>(null) }
    var holding by remember { mutableStateOf(false) }
    val hapticFeedback = rememberUvirHapticController()
    val sessionActive = automaticActive || alertActive
    val latestSessionActive by rememberUpdatedState(sessionActive)
    LaunchedEffect(sessionActive) {
        if (sessionActive) {
            menuOpen = false
            holding = false
            hovered = null
        }
    }
    val menuReveal by animateFloatAsState(
        targetValue = if (holding || menuOpen) 1f else 0f,
        animationSpec = if (holding || menuOpen) {
            spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMedium)
        } else {
            tween(durationMillis = 150, easing = FastOutSlowInEasing)
        },
        label = "captureMenuReveal"
    )
    val automaticEmphasis by animateFloatAsState(
        targetValue = if (hovered == UvirCaptureMenuTarget.AUTOMATIC)
            UvirFloatingPressedTargetScale else 1f,
        animationSpec = spring(dampingRatio = 0.68f, stiffness = Spring.StiffnessHigh),
        label = "captureAutomaticEmphasis"
    )
    val alertEmphasis by animateFloatAsState(
        targetValue = if (hovered == UvirCaptureMenuTarget.ALERT)
            UvirFloatingPressedTargetScale else 1f,
        animationSpec = spring(dampingRatio = 0.68f, stiffness = Spring.StiffnessHigh),
        label = "captureAlertEmphasis"
    )
    val primaryEmphasis by animateFloatAsState(
        targetValue = if (!sessionActive && uvirCaptureMenuPrimarySelected(holding, hovered))
            UvirFloatingPressedTargetScale else 1f,
        animationSpec = spring(dampingRatio = 0.68f, stiffness = Spring.StiffnessHigh),
        label = "capturePrimaryEmphasis"
    )
    val visibleReveal = menuReveal.coerceIn(0f, 1f)
    val idleTransition = rememberInfiniteTransition(label = "captureIdleBreath")
    val idleScale by idleTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
            durationMillis = 3_100
            1f at 0
            UvirIdleCapturePeakScale at 1_050 using LinearOutSlowInEasing
            1.025f at 2_150 using FastOutSlowInEasing
            1f at 3_100
            }
        ),
        label = "captureIdleScale"
    )
    val latestOnPrimary by rememberUpdatedState(
        when {
            alertActive -> onAlert
            automaticActive -> onAutomatic
            else -> onManual
        }
    )
    val latestOnAutomatic by rememberUpdatedState(onAutomatic)
    val latestOnAlert by rememberUpdatedState(onAlert)
    val interactionSource = remember { MutableInteractionSource() }
    val pressedScale = uvirFloatingPressedScale(interactionSource)
    val showPressedHint = uvirFloatingActionHintVisible(interactionSource)
    val darkTheme = isSystemInDarkTheme()
    val idleBreathing = !sessionActive && !holding && !menuOpen
    val primaryContainerColor = if (idleBreathing) {
        uvirIdleCaptureColor(MaterialTheme.colorScheme.primary, idleScale)
    } else MaterialTheme.colorScheme.primary
    val shortcutIconTint = if (darkTheme) Color.Black else Color.White
    val countIndicatorColor = if (automaticIndicatorColor == Color.Unspecified) {
        MaterialTheme.colorScheme.primary
    } else automaticIndicatorColor
    val countContainerColor = if (automaticContainerColor == Color.Unspecified) {
        MaterialTheme.colorScheme.surface
    } else automaticContainerColor
    val activeCountColor = when {
        alertActive && alertIndicatorColor != Color.Unspecified -> alertIndicatorColor
        alertActive -> uvirAlertSessionIndicatorColor(isSystemInDarkTheme())
        else -> countIndicatorColor
    }
    val primaryDescription = when {
        alertActive -> "${stringResource(R.string.alert_registration_count)}: $alertCompletedCount"
        automaticActive -> stringResource(R.string.automatic_completed_count, completedCount)
        else -> stringResource(R.string.save_measurement)
    }
    val moreActionsDescription = stringResource(R.string.capture_more_actions)

    BackHandler(menuOpen) { menuOpen = false }

    Box(modifier = Modifier.size(primarySize + secondarySize + spacing)) {
        UvirFloatingActionHint(
            text = stringResource(when (hovered) {
                UvirCaptureMenuTarget.AUTOMATIC -> R.string.automatic_acquisition
                UvirCaptureMenuTarget.ALERT -> R.string.alert_registration_title
                else -> when {
                    alertActive -> R.string.alert_registration_title
                    automaticActive -> R.string.automatic_acquisition
                    else -> R.string.save_measurement
                }
            }),
            visible = hovered != null || (!menuOpen && showPressedHint)
        )
        if ((holding || menuOpen) && !sessionActive) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .offset(y = -secondaryCenterOffset)
                    .size(secondarySize)
                    .graphicsLayer {
                        alpha = visibleReveal
                        scaleX = (0.55f + 0.45f * visibleReveal) * automaticEmphasis
                        scaleY = scaleX
                        translationX = (1f - visibleReveal) * 36.dp.toPx()
                        rotationZ = -12f * (1f - visibleReveal)
                    },
                contentAlignment = Alignment.Center
            ) {
                UvirAutomaticAcquisitionShortcut(
                    onClick = {
                        menuOpen = false
                        latestOnAutomatic()
                    },
                    containerColor = uvirSessionIndicatorColor(isSystemInDarkTheme()),
                    iconTint = shortcutIconTint,
                    iconStrokeWidth = 2.2.dp
                )
            }

            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = -secondaryCenterOffset)
                    .size(secondarySize)
                    .graphicsLayer {
                        alpha = visibleReveal
                        scaleX = (0.55f + 0.45f * visibleReveal) * alertEmphasis
                        scaleY = scaleX
                        translationY = (1f - visibleReveal) * 36.dp.toPx()
                        rotationZ = 12f * (1f - visibleReveal)
                    },
                contentAlignment = Alignment.Center
            ) {
                if (alertActive) {
                    UvirStopAllAlertsFloatingButton(
                        enabled = true,
                        onClick = {
                            menuOpen = false
                            latestOnAlert()
                        },
                        opensRegistrationPage = true,
                        iconTint = shortcutIconTint,
                        iconStrokeWidth = 2.2.dp
                    )
                } else {
                    UvirStartAllAlertsFloatingButton(
                        onClick = {
                            menuOpen = false
                            latestOnAlert()
                        },
                        iconTint = shortcutIconTint,
                        iconStrokeWidth = 2.2.dp
                    )
                }
            }
        }

        Surface(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(primarySize)
                .scale((if (idleBreathing) idleScale else 1f) *
                    primaryEmphasis *
                    (if (sessionActive) pressedScale else 1f)),
            shape = CircleShape,
            color = if (sessionActive) countContainerColor else primaryContainerColor,
            contentColor = if (sessionActive) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onPrimary,
            shadowElevation = UvirFloatingActionShadowElevation + 4.dp * visibleReveal
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            hapticFeedback.uvirHaptic(UvirHapticCue.CHOICE)
                            val press = PressInteraction.Press(down.position)
                            interactionSource.tryEmit(press)
                            if (latestSessionActive) {
                                while (true) {
                                    val change = awaitPointerEvent().changes
                                        .firstOrNull { it.id == down.id }
                                    if (change == null || change.isConsumed) {
                                        interactionSource.tryEmit(PressInteraction.Cancel(press))
                                        break
                                    }
                                    if (!change.pressed) {
                                        val center = size.width / 2f
                                        val distance = change.position - Offset(center, center)
                                        change.consume()
                                        interactionSource.tryEmit(PressInteraction.Release(press))
                                        if (distance.getDistance() <= center) {
                                            latestOnPrimary()
                                        }
                                        break
                                    }
                                }
                                return@awaitEachGesture
                            }
                            holding = true
                            var initialDragPosition: Offset? = null
                            val pressOutcome = withTimeoutOrNull(
                                viewConfiguration.longPressTimeoutMillis.toLong()
                            ) {
                                var outcome = UvirCapturePressOutcome.CANCELLED
                                while (true) {
                                    val change = awaitPointerEvent().changes
                                        .firstOrNull { it.id == down.id }
                                    if (change == null || change.isConsumed) break
                                    if (!change.pressed) {
                                        change.consume()
                                        outcome = UvirCapturePressOutcome.TAP
                                        break
                                    }
                                    if ((change.position - down.position).getDistance() >
                                        viewConfiguration.touchSlop) {
                                        initialDragPosition = change.position
                                        change.consume()
                                        outcome = UvirCapturePressOutcome.DRAG
                                        break
                                    }
                                }
                                outcome
                            }
                            when (pressOutcome) {
                                UvirCapturePressOutcome.TAP -> {
                                    holding = false
                                    interactionSource.tryEmit(PressInteraction.Release(press))
                                    menuOpen = false
                                    latestOnPrimary()
                                }
                                UvirCapturePressOutcome.CANCELLED -> {
                                    holding = false
                                    interactionSource.tryEmit(PressInteraction.Cancel(press))
                                }
                                UvirCapturePressOutcome.DRAG, null -> {
                                    holding = false
                                    interactionSource.tryEmit(PressInteraction.Release(press))
                                    menuOpen = true
                                    val secondaryPx = secondarySize.toPx()
                                    val spacingPx = spacing.toPx()
                                    val extraTouchPx = 7.dp.toPx()
                                    hovered = if (pressOutcome == null) {
                                        UvirCaptureMenuTarget.PRIMARY
                                    } else initialDragPosition?.let {
                                        uvirCaptureMenuTarget(
                                            position = it,
                                            primarySize = size.width.toFloat(),
                                            secondarySize = secondaryPx,
                                            spacing = spacingPx,
                                            extraTouchRadius = extraTouchPx
                                        )
                                    }
                                    while (true) {
                                        val change = awaitPointerEvent().changes
                                            .firstOrNull { it.id == down.id }
                                        if (change == null) {
                                            hovered = null
                                            menuOpen = false
                                            break
                                        }
                                        val target = uvirCaptureMenuTarget(
                                            position = change.position,
                                            primarySize = size.width.toFloat(),
                                            secondarySize = secondaryPx,
                                            spacing = spacingPx,
                                            extraTouchRadius = extraTouchPx
                                        )
                                        change.consume()
                                        if (change.pressed) {
                                            hovered = target
                                        } else {
                                            hovered = null
                                            menuOpen = false
                                            when (target) {
                                                UvirCaptureMenuTarget.PRIMARY -> latestOnPrimary()
                                                UvirCaptureMenuTarget.AUTOMATIC -> latestOnAutomatic()
                                                UvirCaptureMenuTarget.ALERT -> latestOnAlert()
                                                null -> Unit
                                            }
                                            break
                                        }
                                    }
                                }
                            }
                        }
                    }
                    .indication(interactionSource, ripple(bounded = true))
                    .semantics {
                        contentDescription = primaryDescription
                        role = Role.Button
                        onClick(label = primaryDescription) {
                            menuOpen = false
                            hapticFeedback.uvirHaptic(UvirHapticCue.CHOICE)
                            latestOnPrimary()
                            true
                        }
                        if (!sessionActive) {
                            onLongClick(label = moreActionsDescription) {
                                menuOpen = true
                                hapticFeedback.uvirHaptic(UvirHapticCue.CHOICE)
                                true
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                if (sessionActive) {
                    PulsingAutomaticCountBadge(
                        completed = if (alertActive) alertCompletedCount else completedCount,
                        onClick = null,
                        color = activeCountColor,
                        containerColor = countContainerColor,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        syncInProgress = syncInProgress,
                        shadowElevation = 0.dp,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    CaptureMeasurementIcon(
                        modifier = Modifier.size(25.dp),
                        tint = LocalContentColor.current
                    )
                }
            }
        }
    }
}
