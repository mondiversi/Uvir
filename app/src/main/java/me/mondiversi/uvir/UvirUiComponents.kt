package me.mondiversi.uvir

import android.Manifest
import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
import android.content.pm.PackageManager
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.graphics.BitmapFactory
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.view.WindowCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference
import java.io.File
import org.json.JSONObject
import kotlin.math.roundToInt
import kotlin.random.Random

@Composable
internal fun UvirAnimatedCountBadge(
    countText: String?,
    indicatorColor: Color,
    containerColor: Color,
    contentColor: Color,
    syncInProgress: Boolean,
    pulseEnabled: Boolean = false,
    modifier: Modifier,
    fontSize: TextUnit,
    horizontalPadding: Dp,
    borderWidth: Dp = 1.8.dp,
    syncStrokeWidth: Dp = 2.dp,
    contentDescriptionText: String? = null,
    onClick: (() -> Unit)? = null,
    shadowElevation: Dp = 0.dp,
    tonalElevation: Dp = 0.dp
) {
    val pulseTransition =
        rememberInfiniteTransition(
            label = "countBadgePulse"
        )
    val borderAlpha by
        pulseTransition.animateFloat(
            initialValue = 0.24f,
            targetValue = 1f,
            animationSpec =
                infiniteRepeatable(
                    animation =
                        tween(
                            durationMillis = 1200
                        ),
                    repeatMode =
                        RepeatMode.Reverse
                ),
            label = "countBadgeBorderAlpha"
        )
    val syncRotation =
        if (syncInProgress) {
            val syncTransition =
                rememberInfiniteTransition(
                    label = "countBadgeSync"
                )
            val angle by syncTransition.animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec =
                    infiniteRepeatable(
                        animation = tween(900),
                        repeatMode = RepeatMode.Restart
                    ),
                label = "countBadgeSyncRotation"
            )
            angle
        } else {
            0f
        }

    val describedModifier =
        contentDescriptionText?.let { description ->
            modifier.semantics {
                contentDescription = description
            }
        } ?: modifier
    val badgeBorder =
        if (syncInProgress) {
            null
        } else {
            BorderStroke(
                width = borderWidth,
                color =
                    indicatorColor.copy(
                        alpha =
                            if (pulseEnabled) {
                                borderAlpha
                            } else {
                                1f
                            }
                    )
            )
        }
    Box(
        modifier = describedModifier,
        contentAlignment = Alignment.Center
    ) {
        val badgeModifier =
            Modifier
                .matchParentSize()
                .padding(
                    if (syncInProgress) 2.dp else 0.dp
                )

        if (onClick != null) {
            Surface(
                onClick = onClick,
                modifier = badgeModifier,
                shape = RoundedCornerShape(50),
                color = containerColor,
                contentColor = contentColor,
                border = badgeBorder,
                shadowElevation = shadowElevation,
                tonalElevation = tonalElevation
            ) {}
        } else {
            Surface(
                modifier = badgeModifier,
                shape = RoundedCornerShape(50),
                color = containerColor,
                contentColor = contentColor,
                border = badgeBorder,
                shadowElevation = shadowElevation,
                tonalElevation = tonalElevation
            ) {}
        }

        countText?.let { text ->
            Text(
                modifier =
                    Modifier.padding(
                        horizontal = horizontalPadding
                    ),
                text = text,
                color = contentColor,
                fontSize = fontSize,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }

        if (syncInProgress) {
            Canvas(
                modifier = Modifier.matchParentSize()
            ) {
                val strokeWidth = syncStrokeWidth.toPx()
                val inset = strokeWidth / 2f
                val arcSize = Size(
                    width = size.width - strokeWidth,
                    height = size.height - strokeWidth
                )
                drawArc(
                    color = indicatorColor,
                    startAngle = syncRotation,
                    sweepAngle = 270f,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(
                        width = strokeWidth,
                        cap = StrokeCap.Round
                    )
                )
            }
        }
    }
}

@Composable
fun PulsingAutomaticCountBadge(
    completed: Int,
    onClick: () -> Unit,
    color: Color,
    containerColor: Color,
    syncInProgress: Boolean = false,
    modifier: Modifier = Modifier
) {
    val countText = completed.toString()
    val countFontSize =
        when {
            countText.length >= 6 -> 8.sp
            countText.length >= 4 -> 10.sp
            else -> 13.sp
        }

    UvirAnimatedCountBadge(
        countText = countText,
        indicatorColor = color,
        containerColor = containerColor,
        contentColor = color,
        syncInProgress = syncInProgress,
        pulseEnabled = true,
        modifier = modifier,
        fontSize = countFontSize,
        horizontalPadding = 4.dp,
        borderWidth = 2.5.dp,
        syncStrokeWidth = 2.5.dp,
        contentDescriptionText =
            stringResource(
                R.string.automatic_completed_count,
                completed
            ),
        onClick = onClick,
        shadowElevation = 5.dp,
        tonalElevation = 1.dp
    )
}

@Composable
fun UvirActivityEllipsis(
    color: Color,
    modifier: Modifier = Modifier
) {
    var activeDot by remember {
        mutableIntStateOf(0)
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(300L)
            activeDot = (activeDot + 1) % 3
        }
    }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(3) { index ->
            Box(
                modifier =
                    Modifier
                        .size(4.dp)
                        .background(
                            color = color.copy(
                                alpha = if (index == activeDot) 1f else 0.30f
                            ),
                            shape = RoundedCornerShape(50)
                        )
            )
        }
    }
}

@Composable
fun PulsingAutomaticStatusCard(
    autoNextSaveMs: Long,
    intervalSeconds: Long,
    limitEnabled: Boolean,
    completed: Int,
    maxCount: Int,
    color: Color,
    containerColor: Color,
    fontSize: TextUnit,
    fontWeight: FontWeight,
    modifier: Modifier = Modifier
) {

    val pulseTransition =
        rememberInfiniteTransition(
            label =
                "automaticStatusPulse"
        )

    val borderAlpha by
        pulseTransition.animateFloat(
            initialValue = 0.24f,
            targetValue = 1f,
            animationSpec =
                infiniteRepeatable(
                    animation =
                        tween(
                            durationMillis = 1200
                        ),
                    repeatMode =
                        RepeatMode.Reverse
                ),
            label =
                "automaticStatusBorderAlpha"
        )

    Surface(
        modifier = modifier,
        shape =
            RoundedCornerShape(14.dp),
        color = containerColor,
        contentColor = color,
        border =
            BorderStroke(
                width = 1.5.dp,
                color =
                    color.copy(
                        alpha = borderAlpha
                    )
            ),
        shadowElevation = 5.dp,
        tonalElevation = 1.dp
    ) {

        AutomaticStatusLabel(
            autoNextSaveMs =
                autoNextSaveMs,
            intervalSeconds =
                intervalSeconds,
            limitEnabled =
                limitEnabled,
            completed = completed,
            maxCount = maxCount,
            color = color,
            fontSize = fontSize,
            fontWeight = fontWeight,
            modifier =
                Modifier.padding(
                    horizontal = 10.dp,
                    vertical = 7.dp
                )
        )
    }
}

@Composable
fun AutomaticStatusLabel(
    autoNextSaveMs: Long,
    intervalSeconds: Long,
    limitEnabled: Boolean,
    completed: Int,
    maxCount: Int,
    color: Color,
    fontSize: TextUnit,
    fontWeight: FontWeight,
    modifier: Modifier = Modifier
) {

    // This state is local to the label: the countdown no longer invalidates
    // and recomposes the entire live screen while the user is scrolling.
    var nowMs by remember(
        autoNextSaveMs
    ) {
        mutableLongStateOf(
            System.currentTimeMillis()
        )
    }

    LaunchedEffect(
        autoNextSaveMs
    ) {
        while (true) {
            val currentTime =
                System.currentTimeMillis()

            nowMs = currentTime

            delay(
                (1000L - currentTime % 1000L)
                    .coerceAtLeast(50L)
            )
        }
    }

    val remainingSeconds =
        (
                (
                        autoNextSaveMs -
                                nowMs
                        ).coerceAtLeast(0L) +
                        999L
                ) / 1000L

    val countText =
        completed.toString()

    val countFontSize =
        when {
            countText.length >= 7 -> 6.5.sp
            countText.length >= 5 -> 7.5.sp
            countText.length >= 4 -> 9.sp
            else -> 11.sp
        }

    val statusDescription =
        automaticStatusText(
            autoNextSaveMs =
                autoNextSaveMs,
            intervalSeconds =
                intervalSeconds,
            limitEnabled =
                limitEnabled,
            completed =
                completed,
            maxCount =
                maxCount,
            nowMs =
                nowMs
        )

    Row(
        modifier =
            modifier.semantics(
                mergeDescendants = true
            ) {
                contentDescription =
                    statusDescription
            },
        verticalAlignment =
            Alignment.CenterVertically,
        horizontalArrangement =
            Arrangement.spacedBy(6.dp)
    ) {

        Surface(
            modifier =
                Modifier.size(34.dp),
            shape =
                RoundedCornerShape(50),
            color =
                color.copy(alpha = 0.16f),
            contentColor = color
        ) {

            Box(
                contentAlignment =
                    Alignment.Center
            ) {

                Text(
                    text = countText,
                    color = color,
                    fontSize =
                        countFontSize,
                    fontWeight =
                        FontWeight.Bold,
                    maxLines = 1
                )
            }
        }

        Column(
            horizontalAlignment =
                Alignment.Start,
            verticalArrangement =
                Arrangement.spacedBy(1.dp)
        ) {

            Row(
                verticalAlignment =
                    Alignment.CenterVertically,
                horizontalArrangement =
                    Arrangement.spacedBy(3.dp)
            ) {

                AutomaticIntervalTimerIcon(
                    color = color,
                    modifier =
                        Modifier.size(14.dp)
                )

                Text(
                    text =
                        formatInterval(
                            intervalSeconds
                        ),
                    color = color,
                    fontSize = fontSize,
                    fontWeight = fontWeight,
                    maxLines = 1
                )
            }

            Row(
                verticalAlignment =
                    Alignment.CenterVertically,
                horizontalArrangement =
                    Arrangement.spacedBy(3.dp)
            ) {

                AutomaticNextIcon(
                    color = color,
                    modifier =
                        Modifier.size(14.dp)
                )

                Text(
                    text =
                        formatInterval(
                            remainingSeconds
                        ),
                    color = color,
                    fontSize = fontSize,
                    fontWeight = fontWeight,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
fun AutomaticIntervalTimerIcon(
    color: Color,
    modifier: Modifier = Modifier
) {

    Canvas(
        modifier = modifier
    ) {

        val strokeWidth =
            maxOf(
                1.3.dp.toPx(),
                size.minDimension * 0.10f
            )

        val center =
            Offset(
                x = size.width * 0.50f,
                y = size.height * 0.57f
            )

        val radius =
            size.minDimension * 0.34f

        drawCircle(
            color = color,
            radius = radius,
            center = center,
            style =
                Stroke(
                    width = strokeWidth
                )
        )

        drawLine(
            color = color,
            start =
                Offset(
                    x = size.width * 0.38f,
                    y = size.height * 0.10f
                ),
            end =
                Offset(
                    x = size.width * 0.62f,
                    y = size.height * 0.10f
                ),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )

        drawLine(
            color = color,
            start =
                Offset(
                    x = size.width * 0.50f,
                    y = size.height * 0.10f
                ),
            end =
                Offset(
                    x = size.width * 0.50f,
                    y = size.height * 0.22f
                ),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )

        drawLine(
            color = color,
            start = center,
            end =
                Offset(
                    x = size.width * 0.50f,
                    y = size.height * 0.35f
                ),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )

        drawLine(
            color = color,
            start = center,
            end =
                Offset(
                    x = size.width * 0.68f,
                    y = size.height * 0.61f
                ),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
    }
}

@Composable
fun AutomaticNextIcon(
    color: Color,
    modifier: Modifier = Modifier
) {

    Canvas(
        modifier = modifier
    ) {

        val strokeWidth =
            maxOf(
                1.3.dp.toPx(),
                size.minDimension * 0.10f
            )

        val arrowTip =
            Offset(
                x = size.width * 0.78f,
                y = size.height * 0.50f
            )

        drawLine(
            color = color,
            start =
                Offset(
                    x = size.width * 0.20f,
                    y = size.height * 0.50f
                ),
            end = arrowTip,
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )

        drawLine(
            color = color,
            start =
                Offset(
                    x = size.width * 0.57f,
                    y = size.height * 0.29f
                ),
            end = arrowTip,
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )

        drawLine(
            color = color,
            start =
                Offset(
                    x = size.width * 0.57f,
                    y = size.height * 0.71f
                ),
            end = arrowTip,
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
    }
}

@Composable
fun automaticStatusText(
    autoNextSaveMs: Long,
    intervalSeconds: Long,
    limitEnabled: Boolean,
    completed: Int,
    maxCount: Int,
    nowMs: Long
): String {

    val remainingMs =
        (
                autoNextSaveMs -
                        nowMs
                ).coerceAtLeast(
                0L
            )

    // Ceiling to seconds so the countdown does not show 00:00:00
    // almost one second before the actual acquisition.
    val remainingSeconds =
        (
                remainingMs +
                        999L
                ) / 1000L

    val countdown =
        formatInterval(
            remainingSeconds
        )

    return when {

        autoNextSaveMs >
                nowMs + 1500L &&
                completed == 0 -> {

            stringResource(
                R.string.auto_waiting,
                countdown
            )
        }

        limitEnabled -> {

            stringResource(
                R.string.auto_status_limited,
                formatInterval(
                    intervalSeconds
                ),
                completed,
                maxCount,
                countdown
            )
        }

        else -> {

            stringResource(
                R.string.auto_status,
                formatInterval(
                    intervalSeconds
                ),
                completed,
                countdown
            )
        }
    }
}
