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
import androidx.compose.foundation.shape.CircleShape
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
fun UvirHomeActionButton(
    type: MenuIconType,
    contentDescription: String,
    cardColor: Color,
    primaryText: Color,
    badgeCount: Int = 0,
    badgeColor: Color = MaterialTheme.colorScheme.primary,
    badgePulseEnabled: Boolean = false,
    syncInProgress: Boolean = false,
    lateralGlowColor: Color? = null,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier.size(42.dp)
    ) {
        Surface(
            onClick = onClick,
            modifier =
                Modifier
                    .matchParentSize()
                    .semantics {
                        this.contentDescription =
                            contentDescription
                    },
            shape = RoundedCornerShape(11.dp),
            color = cardColor,
            contentColor = primaryText
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .uvirLateralGlow(
                            active = lateralGlowColor != null,
                            color = lateralGlowColor ?: Color.Transparent
                        ),
                contentAlignment = Alignment.Center
            ) {
                val iconModifier = Modifier.size(22.dp)
                if (type == MenuIconType.SAVED_MEASUREMENTS) {
                    CaptureMeasurementIcon(
                        modifier = iconModifier,
                        tint = primaryText
                    )
                } else {
                    UvirMenuIcon(
                        type = type,
                        modifier = iconModifier,
                        tint = primaryText
                    )
                }
            }
        }

        if (badgeCount > 0 || badgePulseEnabled || syncInProgress) {
            val badgeText =
                badgeCount
                    .takeIf { count ->
                        count > 0 || badgePulseEnabled
                    }
                    ?.toString()
            val badgeFontSize =
                when {
                    badgeText == null -> 9.sp
                    badgeText.length >= 5 -> 7.sp
                    badgeText.length >= 3 -> 8.sp
                    else -> 9.sp
                }

            UvirAnimatedCountBadge(
                countText = badgeText,
                indicatorColor = badgeColor,
                containerColor = cardColor,
                contentColor = badgeColor,
                syncInProgress = syncInProgress,
                pulseEnabled = badgePulseEnabled,
                modifier =
                    Modifier
                        .align(Alignment.TopEnd)
                        .defaultMinSize(
                            minWidth = 18.dp,
                            minHeight = 18.dp
                        ),
                fontSize = badgeFontSize,
                horizontalPadding = 4.dp,
                shadowElevation = 2.dp
            )
        }
    }
}

internal val UvirMeasurementSelectorIconSize = 24.dp
internal val UvirMeasurementSelectorIconStrokeWidth = 2.4.dp
internal val UvirPinnedSelectorBottomSpacing = 4.dp

@Composable
fun MeasurementViewSelector(
    selectedMode: ViewMode,
    onModeSelected: (ViewMode) -> Unit,
    cardColor: Color,
    primaryText: Color,
    secondaryText: Color,
    irradianceAlerted: Boolean = false,
    biologicalAlerted: Boolean = false
) {

    val irradianceDescription =
        stringResource(
            R.string.irradiance_view
        )

    val effectsDescription =
        stringResource(
            R.string.biological_effects_view
        )

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(
                    cardColor,
                    RoundedCornerShape(
                        14.dp
                    )
                )
                .padding(
                    3.dp
                ),

        horizontalArrangement =
            Arrangement.spacedBy(
                3.dp
            )
    ) {

        ViewModeButton(
            selected =
                selectedMode ==
                        ViewMode.IRRADIANCE,
            alerted = irradianceAlerted,

            onClick = {
                onModeSelected(
                    ViewMode.IRRADIANCE
                )
            },

            contentDescription =
                irradianceDescription,

            modifier =
                Modifier.weight(1f),

            primaryText =
                primaryText,

            secondaryText =
                secondaryText
        ) {

            ElectromagneticWaveIcon(
                modifier =
                    Modifier.size(UvirMeasurementSelectorIconSize),
                color =
                    if (
                        selectedMode ==
                        ViewMode.IRRADIANCE
                    )
                        primaryText
                    else
                        secondaryText
            )
        }

        ViewModeButton(
            selected =
                selectedMode ==
                        ViewMode.BIOLOGICAL_EFFECTS,
            alerted = biologicalAlerted,

            onClick = {
                onModeSelected(
                    ViewMode.BIOLOGICAL_EFFECTS
                )
            },

            contentDescription =
                effectsDescription,

            modifier =
                Modifier.weight(1f),

            primaryText =
                primaryText,

            secondaryText =
                secondaryText
        ) {

            DnaIcon(
                modifier =
                    Modifier.size(UvirMeasurementSelectorIconSize),
                color =
                    if (
                        selectedMode ==
                        ViewMode.BIOLOGICAL_EFFECTS
                    )
                        primaryText
                    else
                        secondaryText
            )
        }
    }
}

@Composable
internal fun MeasurementDetailViewSelector(
    selectedMode: ViewMode,
    onModeSelected: (ViewMode) -> Unit,
    showChart: Boolean,
    onShowChartChanged: (Boolean) -> Unit,
    cardColor: Color,
    primaryText: Color,
    secondaryText: Color,
    middleContent: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(modifier = Modifier.weight(1f)) {
            MeasurementViewSelector(
                selectedMode = selectedMode,
                onModeSelected = onModeSelected,
                cardColor = cardColor,
                primaryText = primaryText,
                secondaryText = secondaryText
            )
        }

        middleContent?.let { content ->
            Box(modifier = Modifier.width(UvirSessionCyclePositionSelectorWidth)) {
                content()
            }
        }

        MeasurementDataChartSelector(
            showChart = showChart,
            onShowChartChanged = onShowChartChanged,
            cardColor = cardColor,
            primaryText = primaryText,
            secondaryText = secondaryText,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
internal fun MeasurementDataChartSelector(
    showChart: Boolean,
    onShowChartChanged: (Boolean) -> Unit,
    cardColor: Color,
    primaryText: Color,
    secondaryText: Color,
    modifier: Modifier = Modifier
) {
    val valuesDescription = stringResource(R.string.show_live_values)
    val chartDescription = stringResource(R.string.show_live_chart)

    Row(
        modifier = modifier
            .background(cardColor, RoundedCornerShape(14.dp))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        ViewModeButton(
            selected = !showChart,
            alerted = false,
            onClick = { onShowChartChanged(false) },
            contentDescription = valuesDescription,
            modifier = Modifier.weight(1f),
            primaryText = primaryText,
            secondaryText = secondaryText
        ) {
            LiveIslandModeIcon(
                showChart = false,
                tint = if (!showChart) primaryText else secondaryText,
                modifier = Modifier.size(UvirMeasurementSelectorIconSize)
            )
        }

        ViewModeButton(
            selected = showChart,
            alerted = false,
            onClick = { onShowChartChanged(true) },
            contentDescription = chartDescription,
            modifier = Modifier.weight(1f),
            primaryText = primaryText,
            secondaryText = secondaryText
        ) {
            LiveIslandModeIcon(
                showChart = true,
                tint = if (showChart) primaryText else secondaryText,
                modifier = Modifier.size(UvirMeasurementSelectorIconSize)
            )
        }
    }
}

@Composable
fun ViewModeButton(
    selected: Boolean,
    alerted: Boolean,
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    primaryText: Color,
    secondaryText: Color,
    enabled: Boolean = true,
    content:
    @Composable () -> Unit
) {

    val selectedColor =
        uvirSegmentedSelectedContainerColor(
            primaryText
        )

    Box(
        modifier =
            modifier
                .height(
                    40.dp
                )
                .background(
                    if (selected)
                        selectedColor
                    else
                        Color.Transparent,
                    RoundedCornerShape(
                        10.dp
                    )
                )
                .uvirLateralGlow(
                    active = alerted,
                    color = UvirAttentionColor
                )
                .border(
                    width =
                        if (selected)
                            1.dp
                        else
                            0.dp,
                    color =
                        if (selected)
                            uvirSegmentedSelectedBorderColor(
                                secondaryText
                            )
                        else
                            Color.Transparent,
                    shape =
                        RoundedCornerShape(
                            10.dp
                        )
                )
                .clickable(enabled = enabled) {
                    onClick()
                }
                .semantics {
                    this.contentDescription =
                        contentDescription
                },

        contentAlignment =
            Alignment.Center
    ) {

        content()
    }
}

internal fun uvirSegmentedSelectedContainerColor(
    primaryText: Color
): Color = primaryText.copy(alpha = 0.12f)

internal fun uvirSegmentedSelectedBorderColor(
    secondaryText: Color
): Color = secondaryText.copy(alpha = 0.45f)

@Composable
fun ElectromagneticWaveIcon(
    color: Color,
    modifier: Modifier = Modifier,
    iconSize: Dp = UvirMeasurementSelectorIconSize
) {

    Canvas(
        modifier =
            modifier.size(
                iconSize
            )
    ) {

        val midY =
            size.height / 2f

        val amplitude =
            size.height * 0.23f

        val path =
            Path()

        val points =
            40

        for (i in 0..points) {

            val x =
                size.width *
                        i / points

            val radians =
                (
                        i.toFloat() /
                                points.toFloat()
                        ) *
                        Math.PI.toFloat() *
                        4f

            val y =
                midY +
                        kotlin.math.sin(
                            radians
                        ) *
                        amplitude

            if (i == 0) {
                path.moveTo(
                    x,
                    y
                )
            } else {
                path.lineTo(
                    x,
                    y
                )
            }
        }

        drawPath(
            path =
                path,
            color =
                color,
            style =
                Stroke(
                    width =
                        UvirMeasurementSelectorIconStrokeWidth.toPx() *
                            (iconSize / UvirMeasurementSelectorIconSize),
                    cap =
                        StrokeCap.Round
                )
        )
    }
}

@Composable
fun DnaIcon(
    color: Color,
    modifier: Modifier = Modifier,
    iconSize: Dp = UvirMeasurementSelectorIconSize
) {

    Canvas(
        modifier =
            modifier.size(
                iconSize
            )
    ) {

        val left =
            Path()

        val right =
            Path()

        val steps =
            32

        for (i in 0..steps) {

            val t =
                i.toFloat() /
                        steps.toFloat()

            val y =
                size.height *
                        t

            val phase =
                t *
                        Math.PI.toFloat() *
                        2.3f

            val center =
                size.width / 2f

            val offset =
                kotlin.math.sin(
                    phase
                ) *
                        size.width *
                        0.24f

            val x1 =
                center + offset

            val x2 =
                center - offset

            if (i == 0) {

                left.moveTo(
                    x1,
                    y
                )

                right.moveTo(
                    x2,
                    y
                )

            } else {

                left.lineTo(
                    x1,
                    y
                )

                right.lineTo(
                    x2,
                    y
                )
            }
        }

        val stroke =
            Stroke(
                width =
                    UvirMeasurementSelectorIconStrokeWidth.toPx() *
                        (iconSize / UvirMeasurementSelectorIconSize),
                cap =
                    StrokeCap.Round
            )

        drawPath(
            path = left,
            color = color,
            style = stroke
        )

        drawPath(
            path = right,
            color = color,
            style = stroke
        )

        for (i in 2 until steps step 5) {

            val t =
                i.toFloat() /
                        steps.toFloat()

            val y =
                size.height *
                        t

            val phase =
                t *
                        Math.PI.toFloat() *
                        2.3f

            val center =
                size.width / 2f

            val offset =
                kotlin.math.sin(
                    phase
                ) *
                        size.width *
                        0.24f

            drawLine(
                color =
                    color,

                start =
                    Offset(
                        center + offset,
                        y
                    ),

                end =
                    Offset(
                        center - offset,
                        y
                    ),

                strokeWidth =
                    UvirMeasurementSelectorIconStrokeWidth.toPx() *
                        (iconSize / UvirMeasurementSelectorIconSize),

                cap =
                    StrokeCap.Round
            )
        }
    }
}
