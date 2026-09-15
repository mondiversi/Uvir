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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RecordDetailScreen(
    record: SavedRecordDetail,
    database: UvirDatabaseHelper,
    detailListState: LazyListState,
    backgroundColor: Color,
    cardColor: Color,
    primaryText: Color,
    secondaryText: Color,
    trackColor: Color,
    onBack: () -> Unit,
    onDeleted: () -> Unit
) {

    val context =
        LocalContext.current
    val detailSensorName = rememberDetailSensorName(record.sensorId, database)

    LaunchedEffect(record.id) {
        detailListState.scrollToItem(0)
    }

    BackHandler {
        onBack()
    }

    var showDeleteConfirmation
            by rememberSaveable {
                mutableStateOf(false)
            }

    var showShareFormatDialog
            by rememberSaveable {
                mutableStateOf(false)
            }

    var showChart
            by rememberSaveable(record.id) {
                mutableStateOf(false)
            }

    var viewMode
            by rememberSaveable(record.id) {
                mutableStateOf(ViewMode.IRRADIANCE)
            }

    val deleteDescription =
        stringResource(
            R.string.delete
        )

    val shareDescription =
        stringResource(
            R.string.share_measurements
        )

    val shareErrorText =
        stringResource(
            R.string.share_error
        )

    val expandedStates = remember {
        mutableStateMapOf(
            SessionChartGroup.UV to true,
            SessionChartGroup.VISIBLE to true,
            SessionChartGroup.FAR_RED_NIR to true,
            SessionChartGroup.BIOLOGICAL to true
        )
    }

    val measurement =
        record.sample

    val selectedChartGroup =
        if (viewMode == ViewMode.IRRADIANCE) {
            AcquisitionChartGroup.IRRADIANCE
        } else {
            AcquisitionChartGroup.BIOLOGICAL
        }

    val chartBars =
        remember(measurement, selectedChartGroup) {
            acquisitionChartBars(
                sample = measurement,
                group = selectedChartGroup
            )
        }

    val singleRecord =
        remember(record) {
            listOf(record)
        }

    if (showDeleteConfirmation) {

        val measurementDeletedText =
            stringResource(
                R.string.measurement_deleted
            )

        AlertDialog(
            onDismissRequest = {
                showDeleteConfirmation =
                    false
            },

            title = {
                Text(
                    stringResource(
                        R.string.delete_measurement_question
                    )
                )
            },

            text = {
                Text(
                    stringResource(
                        R.string.delete_measurement_warning
                    )
                )
            },

            dismissButton = {

                TextButton(
                    onClick = {

                        val deleted =
                            database.deleteRecord(
                                record.id
                            )

                        if (deleted > 0) {

                            showUvirBottomMessage(
                                context,
                                measurementDeletedText,
                                longDuration = false
                            )

                            onDeleted()
                        }
                    },

                    colors =
                        ButtonDefaults
                            .textButtonColors(
                                contentColor =
                                    Color(
                                        0xFFD32F2F
                                    )
                            )
                ) {
                    Text(
                        stringResource(
                            R.string.delete
                        )
                    )
                }
            },

            confirmButton = {

                TextButton(
                    onClick = {
                        showDeleteConfirmation =
                            false
                    }
                ) {
                    Text(
                        stringResource(
                            R.string.cancel
                        )
                    )
                }
            },

            containerColor = cardColor,
            titleContentColor = primaryText,
            textContentColor = secondaryText
        )
    }

    if (showShareFormatDialog) {
        MeasurementDetailShareDialog(
            cardColor = cardColor,
            primaryText = primaryText,
            secondaryText = secondaryText,
            onDismiss = {
                showShareFormatDialog = false
            },
            onSelectionConfirmed = { selection ->
                runCatching {
                    shareAcquisitionDetail(
                        context = context,
                        record = record,
                        selection = selection
                    )
                }.onFailure { error ->
                    UvirErrorLog.record(
                        context,
                        "share_acquisition",
                        error
                    )
                    showUvirBottomMessage(
                        context,
                        shareErrorText,
                        longDuration = false
                    )
                }
                showShareFormatDialog = false
            }
        )
    }

    Scaffold(
        containerColor =
            backgroundColor,

        topBar = {
            UvirCollapsingDetailTitleBar(detailListState) {
                Surface(
                    color =
                        backgroundColor
                ) {

                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(UvirTitleBarContentPadding),

                    verticalAlignment =
                        Alignment.CenterVertically
                ) {

                    UvirBackButton(
                        onClick = onBack
                    )

                    UvirMenuTitle(
                        text = stringResource(R.string.share_acquisition_label),
                        modifier = Modifier.weight(1f),
                        color = primaryText
                    )

                    IconButton(
                        onClick = {
                            showShareFormatDialog = true
                        },
                        modifier =
                            Modifier
                                .size(40.dp)
                                .semantics {
                                    contentDescription =
                                        shareDescription
                                }
                    ) {
                        UvirTitleActionIcon(
                            type =
                                MenuIconType.SHARE,
                            modifier =
                                Modifier.size(24.dp),
                            tint =
                                MaterialTheme.colorScheme.primary
                        )
                    }

                    IconButton(
                        onClick = {
                            showDeleteConfirmation =
                                true
                        },

                        modifier =
                            Modifier
                                .size(40.dp)
                                .semantics {
                                    contentDescription =
                                        deleteDescription
                                }
                    ) {
                        UvirTitleActionIcon(
                            type =
                                MenuIconType.DELETE,
                            modifier =
                                Modifier.size(24.dp),
                            tint =
                                UvirDestructiveActionColor
                        )
                    }
                    }
                }
            }
        }

    ) { paddingValues ->

        LazyColumn(
            state =
                detailListState,
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(
                        paddingValues
                    )
                    .lazyScrollbarOverlay(
                        state =
                            detailListState,
                        color =
                            secondaryText.copy(
                                alpha = 0.46f
                            )
                    ),

            contentPadding =
                PaddingValues(
                    start = 20.dp,
                    end = 20.dp,
                    top = 4.dp,
                    bottom = 40.dp
                ),

            verticalArrangement =
                Arrangement.spacedBy(
                    UvirIslandSpacing
                )
        ) {

            item {

                UvirDetailIdentityCard(
                    primaryId = record.id,
                    sessionId = record.sessionId,
                    idLabel =
                        "ID / ${stringResource(R.string.session_label)}",
                    dateLabel =
                        stringResource(R.string.share_date_label),
                    dateText = formatDetailDateTime(record.timestamp),
                    cardColor = cardColor,
                    primaryText = primaryText,
                    secondaryText = secondaryText
                )
            }

            item {
                UvirDetailContextCard(
                    automatic = record.automatic,
                    sensorName = detailSensorName,
                    note =
                        acquisitionDisplayNote(
                            note = record.note,
                            automatic = record.automatic,
                            sessionSequence = record.sessionSequence,
                            emptyNote = stringResource(R.string.no_note)
                        ),
                    cardColor = cardColor,
                    primaryText = primaryText,
                    secondaryText = secondaryText
                )
            }

            stickyHeader(key = "detail_view_selector") {
                Surface(color = backgroundColor) {
                    MeasurementDetailViewSelector(
                    selectedMode =
                        viewMode,

                    onModeSelected =
                        { viewMode = it },

                    showChart = showChart,

                    onShowChartChanged = {
                        showChart = it
                    },

                    cardColor =
                        cardColor,

                    primaryText =
                        primaryText,

                    secondaryText =
                        secondaryText,

                    modifier =
                        Modifier.padding(bottom = UvirPinnedSelectorBottomSpacing)
                    )
                }
            }

            if (showChart) {

                item {
                    AcquisitionVerticalBarChart(
                        bars = chartBars,
                        group = selectedChartGroup,
                        cardColor = cardColor,
                        primaryText = primaryText,
                        secondaryText = secondaryText
                    )
                }

            } else {
                val visibleGroups =
                    if (viewMode == ViewMode.IRRADIANCE) {
                        SessionChartGroup.entries.filterNot { it.biological }
                    } else {
                        listOf(SessionChartGroup.BIOLOGICAL)
                    }

                items(
                    items = visibleGroups,
                    key = { "values_${it.name}" }
                ) { group ->
                    val expanded =
                        expandedStates[group] ?: true

                    SessionAcquisitionValuesCard(
                        records = singleRecord,
                        group = group,
                        showRecordHeader = false,
                        showRelativeBreakdown = true,
                        expanded = expanded,
                        onToggle = {
                            expandedStates[group] = !expanded
                        },
                        cardColor = cardColor,
                        primaryText = primaryText,
                        secondaryText = secondaryText
                    )
                }
            }
        }
    }
}
