package me.mondiversi.uvir

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val UvirAutomaticAcquisitionActionColor = Color(0xFFD32F2F)
internal val UvirSecondaryFloatingControlSize = 42.dp
internal val UvirFloatingControlSpacing = 10.dp

@Composable
internal fun UvirAutomaticAcquisitionShortcut(onClick: () -> Unit) {
    val description = stringResource(R.string.automatic_acquisition)
    Surface(
        onClick = onClick,
        modifier = Modifier.size(UvirSecondaryFloatingControlSize).semantics {
            contentDescription = description
        },
        shape = CircleShape,
        color = Color.White,
        contentColor = Color.Black,
        shadowElevation = 5.dp
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            UvirMenuIcon(
                type = MenuIconType.AUTOMATIC_ACQUISITION,
                modifier = Modifier.size(23.dp),
                tint = Color.Black
            )
        }
    }
}

@Composable
internal fun UvirAutomaticAcquisitionFloatingAction(
    automaticActive: Boolean,
    completedCount: Int,
    enabled: Boolean,
    syncInProgress: Boolean,
    onClick: () -> Unit
) {
    val pulseTransition =
        rememberInfiniteTransition(label = "automaticActionPulse")
    val pulseAmount by
        pulseTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec =
                infiniteRepeatable(
                    animation = tween(durationMillis = 1_100),
                    repeatMode = RepeatMode.Reverse
                ),
            label = "automaticActionPulseAmount"
        )
    val syncRotation by
        pulseTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec =
                infiniteRepeatable(
                    animation = tween(durationMillis = 900),
                    repeatMode = RepeatMode.Restart
                ),
            label = "automaticActionSyncRotation"
        )
    val containerColor =
        when {
            !enabled -> uvirDisabledActionContainerColor()
            automaticActive ->
                lerp(
                    UvirAutomaticAcquisitionActionColor,
                    Color.White,
                    pulseAmount * 0.18f
                )
            else -> UvirAutomaticAcquisitionActionColor
        }
    val contentColor =
        if (enabled) {
            Color.White
        } else {
            uvirDisabledActionContentColor()
        }
    val description =
        if (automaticActive) {
            stringResource(R.string.stop_with_count, completedCount)
        } else {
            stringResource(R.string.start)
        }
    val countText = completedCount.toString()
    val countFontSize =
        when {
            countText.length >= 6 -> 12.sp
            countText.length >= 4 -> 15.sp
            else -> 18.sp
        }

    Box(
        modifier =
            Modifier
                .size(56.dp)
                .semantics {
                    contentDescription = description
                },
        contentAlignment = Alignment.Center
    ) {
        Surface(
            onClick = onClick,
            enabled = enabled,
            modifier =
                Modifier
                    .matchParentSize()
                    .padding(if (syncInProgress) 2.dp else 0.dp),
            shape = FloatingActionButtonDefaults.shape,
            color = containerColor,
            contentColor = contentColor,
            shadowElevation = 6.dp,
            tonalElevation = 6.dp
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                if (automaticActive) {
                    Text(
                        text = countText,
                        color = contentColor,
                        fontSize = countFontSize,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                } else {
                    Canvas(modifier = Modifier.size(19.dp)) {
                        drawCircle(color = contentColor)
                    }
                }
            }
        }

        if (syncInProgress) {
            val syncColor =
                uvirSessionIndicatorColor(isSystemInDarkTheme())
            Canvas(modifier = Modifier.matchParentSize()) {
                val strokeWidth = 2.5.dp.toPx()
                val inset = strokeWidth / 2f
                drawArc(
                    color = syncColor,
                    startAngle = syncRotation,
                    sweepAngle = 270f,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size =
                        Size(
                            width = size.width - strokeWidth,
                            height = size.height - strokeWidth
                        ),
                    style =
                        Stroke(
                            width = strokeWidth,
                            cap = StrokeCap.Round
                        )
                )
            }
        }
    }
}
