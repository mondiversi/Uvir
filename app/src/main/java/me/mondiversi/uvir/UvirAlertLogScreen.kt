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
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.random.Random

@Composable
internal fun ThresholdAlertLogScreen(
    database: UvirDatabaseHelper,
    listState: LazyListState,
    backgroundColor: Color,
    cardColor: Color,
    primaryText: Color,
    secondaryText: Color,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    var allEntries by rememberLiveAlertEntries(database)
    val sensorProfiles by rememberLiveSensorProfiles(database)
    var filters by rememberRecordListFilters()
    var showFilters by rememberSaveable { mutableStateOf(false) }
    val entries = remember(allEntries, filters) { filterAlertEntries(allEntries, filters) }
    val filterSensorNames = remember(allEntries, sensorProfiles) {
        allEntries.map { it.sensorId }.distinct().associate { id ->
            id.filterSensorKey() to detailSensorDisplayName(sensorProfiles[id])
        }
    }
    val filterScope = rememberCoroutineScope()

    var showDeleteAllConfirmation by rememberSaveable {
        mutableStateOf(false)
    }
    var showDeleteSelectedConfirmation by rememberSaveable {
        mutableStateOf(false)
    }
    var showShareAllConfirmation by rememberSaveable {
        mutableStateOf(false)
    }
    var showShareFormatDialog by rememberSaveable {
        mutableStateOf(false)
    }
    var selectionMode by rememberSaveable {
        mutableStateOf(false)
    }
    var selectedAlertIds by rememberSaveable {
        mutableStateOf<List<Long>>(emptyList())
    }
    LaunchedEffect(entries) {
        val visibleIds = entries.map { it.id }.toSet()
        selectedAlertIds = selectedAlertIds.filter { it in visibleIds }
    }

    var pendingSharePlan by remember {
        mutableStateOf<AlertExportPlan?>(null)
    }
    var selectedChartSessionId by rememberSaveable {
        mutableStateOf<Long?>(null)
    }
    var selectedChartAlertId by rememberSaveable {
        mutableStateOf<Long?>(null)
    }
    val darkMode = isSystemInDarkTheme()
    val alertSessionColor =
        uvirAlertSessionIndicatorColor(darkMode)
    val alertSessionContentColor =
        uvirAlertSessionContentColor()

    val sessionCounts =
        remember(entries) {
            entries.mapNotNull { it.sessionId }
                .groupingBy { it }
                .eachCount()
        }
    val sessionAlertIds =
        remember(entries) {
            entries.filter { it.sessionId != null }
                .groupBy { requireNotNull(it.sessionId) }
                .mapValues { (_, alerts) -> alerts.map { it.id } }
        }
    val sessionStartTimestamps =
        remember(entries) {
            entries.filter { it.sessionId != null }
                .groupBy { requireNotNull(it.sessionId) }
                .mapValues { (_, alerts) ->
                    alerts.minOf { it.timestamp }
                }
        }
    val sessionFirstAlertIds =
        remember(entries) {
            mutableMapOf<Long, Long>().apply {
                entries.forEach { entry ->
                    entry.sessionId?.let { sessionId ->
                        putIfAbsent(sessionId, entry.id)
                    }
                }
            }
        }

    selectedChartSessionId?.let { sessionId ->
        val chartEntries =
            allEntries.filter { it.sessionId == sessionId }
        if (chartEntries.isNotEmpty()) {
            AlertSessionChartScreen(
                sessionId = sessionId,
                entries = chartEntries,
                database = database,
                backgroundColor = backgroundColor,
                cardColor = cardColor,
                primaryText = primaryText,
                secondaryText = secondaryText,
                onBack = {
                    selectedChartSessionId = null
                },
                onDeleted = {
                    allEntries =
                        database.readThresholdAlertLog(
                            limit = 100_000
                        )
                    selectedChartSessionId = null
                }
            )
            return
        }
    }

    selectedChartAlertId?.let { alertId ->
        allEntries.firstOrNull { it.id == alertId }?.let { entry ->
            AlertChartScreen(
                entry = entry,
                database = database,
                backgroundColor = backgroundColor,
                cardColor = cardColor,
                primaryText = primaryText,
                secondaryText = secondaryText,
                onBack = {
                    selectedChartAlertId = null
                },
                onDeleted = {
                    allEntries =
                        database.readThresholdAlertLog(
                            limit = 100_000
                        )
                    selectedChartAlertId = null
                }
            )
            return
        }
    }

    fun handleListBack() {
        when {
            showFilters -> showFilters = false
            selectionMode -> {
                selectionMode = false
                selectedAlertIds = emptyList()
            }
            else -> onBack()
        }
    }

    BackHandler { handleListBack() }

    val alertSessionChartOpenDescription =
        stringResource(R.string.alert_session_chart_open)
    val alertChartOpenDescription =
        stringResource(R.string.alert_chart_open)
    fun openShareFormat(
        ids: List<Long>
    ) {
        val selectedEntries =
            entries.filter { it.id in ids }
        if (selectedEntries.isNotEmpty()) {
            pendingSharePlan =
                buildAlertExportPlan(
                    selectedEntries = selectedEntries,
                    allEntries = allEntries
                )
            showShareFormatDialog = true
        }
    }

    fun sharePendingAlerts(
        selection: MeasurementDetailShareSelection
    ) {
        pendingSharePlan?.let { plan ->
            runCatching {
                shareAlertExportPlan(
                    context = context,
                    plan = plan,
                    selection = selection
                )
            }.onFailure { error ->
                UvirErrorLog.record(
                    context,
                    "share_alert_log",
                    error
                )
                showUvirBottomMessage(
                    context,
                    context.getString(R.string.share_error),
                    longDuration = false
                )
            }
        }

        showShareFormatDialog = false
        pendingSharePlan = null
    }

    if (showDeleteAllConfirmation) {
        AlertDialog(
            onDismissRequest = {
                showDeleteAllConfirmation = false
            },
            title = {
                Text(
                    stringResource(
                        R.string.threshold_alert_log_delete_title
                    )
                )
            },
            text = {
                Text(
                    stringResource(
                        R.string.threshold_alert_log_delete_warning
                    )
                )
            },
            dismissButton = {
                HoldToConfirmDeleteButton(
                    label =
                        stringResource(
                            R.string.threshold_alert_log_delete_all
                        ),
                    onConfirmed = {
                        database.deleteAllThresholdAlertLogs()
                        allEntries = emptyList()
                        selectedAlertIds = emptyList()
                        selectionMode = false
                        showDeleteAllConfirmation = false
                        showUvirBottomMessage(
                            context,
                            context.getString(
                                R.string.threshold_alert_log_deleted
                            ),
                            longDuration = false
                        )
                    }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteAllConfirmation = false
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

    if (showDeleteSelectedConfirmation) {
        AlertDialog(
            onDismissRequest = {
                showDeleteSelectedConfirmation = false
            },
            title = {
                Text(
                    stringResource(
                        R.string.delete_selected_alerts_question
                    )
                )
            },
            text = {
                Text(
                    pluralStringResource(
                        R.plurals.delete_selected_alerts_warning,
                        selectedAlertIds.size,
                        selectedAlertIds.size
                    )
                )
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        val deleted =
                            database.deleteThresholdAlertLogs(
                                selectedAlertIds
                            )

                        allEntries =
                            database.readThresholdAlertLog(
                                limit = 100_000
                            )
                        selectedAlertIds = emptyList()
                        selectionMode = false
                        showDeleteSelectedConfirmation = false

                        if (deleted > 0) {
                            showUvirBottomMessage(
                                context,
                                context.getString(
                                    R.string.threshold_alerts_deleted
                                ),
                                longDuration = false
                            )
                        }
                    },
                    colors =
                        ButtonDefaults.textButtonColors(
                            contentColor = UvirDestructiveActionColor
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
                        showDeleteSelectedConfirmation = false
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

    if (showShareAllConfirmation) {
        AlertDialog(
            onDismissRequest = {
                showShareAllConfirmation = false
            },
            title = {
                Text(
                    stringResource(
                        if (filters.isActive) R.string.list_filter_share_question else R.string.share_all_alerts_question
                    )
                )
            },
            text = {
                Text(
                    stringResource(
                        if (filters.isActive) R.string.list_filter_share_warning else R.string.share_all_alerts_warning
                    )
                )
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showShareAllConfirmation = false
                        openShareFormat(
                            entries.map { it.id }
                        )
                    }
                ) {
                    Text(
                        stringResource(
                            R.string.share_all
                        )
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showShareAllConfirmation = false
                    },
                    colors =
                        ButtonDefaults.textButtonColors(
                            contentColor = UvirDestructiveActionColor
                        )
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
            chartsDescription =
                stringResource(
                    R.string.share_alert_list_charts_description
                ),
            partialSessionWarningMessage =
                if (pendingSharePlan?.hasPartialSessions == true) {
                    stringResource(
                        R.string.share_partial_alert_session_warning
                    )
                } else {
                    null
                },
            onDismiss = {
                showShareFormatDialog = false
                pendingSharePlan = null
            },
            onSelectionConfirmed = { selection ->
                sharePendingAlerts(selection)
            }
        )
    }

    Scaffold(
        containerColor = backgroundColor,
        topBar = {
            Surface(color = backgroundColor) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(UvirTitleBarContentPadding),
                    verticalAlignment =
                        Alignment.CenterVertically
                ) {
                    UvirBackButton(
                        onClick = { handleListBack() }
                    )

                    UvirMenuTitle(
                        text =
                            stringResource(
                                R.string.threshold_alert_log_title
                            ),
                        modifier = Modifier.weight(1f),
                        color = primaryText
                    )

                    UvirRecordListFilterButton(
                        active = filters.isActive,
                        enabled = allEntries.isNotEmpty(),
                        onClick = { showFilters = !showFilters }
                    )

                    IconButton(
                        onClick = {
                            if (selectedAlertIds.isEmpty()) {
                                showShareAllConfirmation = true
                            } else {
                                openShareFormat(selectedAlertIds)
                            }
                        },
                        enabled = entries.isNotEmpty(),
                        modifier = Modifier.size(UvirTitleActionButtonSize)
                    ) {
                        UvirTitleActionIcon(
                            type = MenuIconType.SHARE,
                            modifier = Modifier.size(UvirTitleActionIconSize),
                            tint =
                                if (entries.isNotEmpty()) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    secondaryText.copy(
                                        alpha = 0.38f
                                    )
                                }
                        )
                    }

                    IconButton(
                        onClick = {
                            if (selectedAlertIds.isEmpty() && filters.isActive) {
                                selectedAlertIds = entries.map { it.id }
                                showDeleteSelectedConfirmation = true
                            } else if (selectedAlertIds.isEmpty()) {
                                showDeleteAllConfirmation = true
                            } else {
                                showDeleteSelectedConfirmation = true
                            }
                        },
                        enabled = entries.isNotEmpty(),
                        modifier = Modifier.size(UvirTitleActionButtonSize)
                    ) {
                        UvirTitleActionIcon(
                            type = MenuIconType.DELETE,
                            modifier = Modifier.size(UvirTitleActionIconSize),
                            tint =
                                if (entries.isNotEmpty()) {
                                    UvirDestructiveActionColor
                                } else {
                                    secondaryText.copy(
                                        alpha = 0.38f
                                    )
                                }
                        )
                    }
                }
            }
        },
        bottomBar = {
            if (entries.isNotEmpty()) {
                Surface(
                    color = cardColor,
                    shadowElevation = 4.dp
                ) {
                    Column {
                        HorizontalDivider(
                            color =
                                secondaryText.copy(
                                    alpha = 0.16f
                                )
                        )

                        Text(
                            text = if (filters.isActive) {
                                stringResource(R.string.list_filter_count, entries.size, allEntries.size)
                            } else pluralStringResource(
                                    R.plurals.threshold_alert_count_since,
                                    entries.size,
                                    entries.size,
                                    formatDateTime(
                                        entries.last().timestamp
                                    )
                                ),
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(
                                        horizontal = 20.dp,
                                        vertical = 6.dp
                                    ),
                            color = secondaryText,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        if (allEntries.isEmpty()) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text =
                        stringResource(
                            R.string.threshold_alert_log_empty
                        ),
                    color = secondaryText,
                    textAlign = TextAlign.Center,
                    modifier =
                        Modifier.padding(horizontal = 24.dp)
                )
            }
        } else {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
            ) {
                UvirVerticalReveal(showFilters) {
                    UvirRecordListFilterPanel(
                        filters = filters, sensorNames = filterSensorNames, modes = setOf(true),
                        backgroundColor = backgroundColor,
                        onChange = {
                            filters = it
                            selectedAlertIds = emptyList()
                            filterScope.launch { listState.scrollToItem(0) }
                        }
                    )
                }

                UvirVerticalReveal(selectionMode && entries.isNotEmpty()) {
                    val allSelected =
                        entries.all {
                            it.id in selectedAlertIds
                        }

                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp)
                                .clickable {
                                    selectedAlertIds =
                                        if (allSelected) {
                                            emptyList()
                                        } else {
                                            entries.map { it.id }
                                        }
                                }
                                .padding(
                                    start = 0.dp,
                                    end = 14.dp,
                                    top = 8.dp,
                                    bottom = 8.dp
                                ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CompositionLocalProvider(
                            LocalMinimumInteractiveComponentSize provides 0.dp
                        ) {
                            Checkbox(
                                checked = allSelected,
                                onCheckedChange = { checked ->
                                    selectedAlertIds =
                                        if (checked) {
                                            entries.map { it.id }
                                        } else {
                                            emptyList()
                                        }
                                },
                                modifier = Modifier.size(24.dp),
                                colors =
                                    CheckboxDefaults.colors(
                                        checkedColor =
                                            MaterialTheme.colorScheme.primary,
                                        uncheckedColor =
                                            MaterialTheme.colorScheme.primary,
                                        checkmarkColor =
                                            MaterialTheme.colorScheme.onPrimary
                                    )
                            )
                        }

                        Spacer(Modifier.width(12.dp))

                        Text(
                            text =
                                stringResource(
                                    if (allSelected) {
                                        R.string.deselect_all
                                    } else {
                                        R.string.select_all
                                    }
                                ),
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    HorizontalDivider(
                        modifier =
                            Modifier.padding(horizontal = 20.dp),
                        color =
                            secondaryText.copy(alpha = 0.12f)
                    )
                }

                LazyColumn(
                    state = listState,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .lazyScrollbarOverlay(
                                state = listState,
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
                            bottom = 20.dp
                        ),
                    verticalArrangement = Arrangement.Top
                ) {
                    if (entries.isEmpty()) {
                        item { Text(stringResource(R.string.list_filter_no_results),
                            color = secondaryText, modifier = Modifier.fillMaxWidth().padding(24.dp),
                            textAlign = TextAlign.Center) }
                    }
                    itemsIndexed(
                        items = entries,
                        key = { _, entry -> entry.id }
                    ) { index, entry ->
                        val selected = entry.id in selectedAlertIds
                        val violations =
                            remember(entry.details) {
                                parseThresholdAlertLogDetails(entry.details)
                            }
                        val sessionId = entry.sessionId
                        val continuesSession =
                            index > 0 && sessionId != null &&
                                entries[index - 1].sessionId == sessionId
                        val isLastSessionAlert =
                            sessionId != null &&
                                (index == entries.lastIndex ||
                                    entries[index + 1].sessionId != sessionId)
                        val headerSessionId =
                            sessionId?.takeIf {
                                sessionFirstAlertIds[it] == entry.id
                            }
                        val sessionRailModifier =
                            if (sessionId != null) {
                                Modifier
                                    .padding(start = UvirRecordSessionContentInset, bottom = UvirRecordSessionItemGap)
                            } else {
                                Modifier.padding(bottom = UvirIslandSpacing)
                            }

                        Column(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(
                                        top =
                                            if (index == 0 || continuesSession) {
                                                0.dp
                                            } else {
                                                4.dp
                                            }
                                    )
                                .then(if (sessionId != null) Modifier.uvirRecordSessionRail(
                                    color = alertSessionColor, isFirst = headerSessionId != null,
                                    isLast = isLastSessionAlert
                                ) else Modifier)
                        ) {
                            if (headerSessionId != null) {
                                val idsInSession =
                                    sessionAlertIds[headerSessionId].orEmpty()
                                val allSessionSelected =
                                    idsInSession.isNotEmpty() &&
                                        idsInSession.all { it in selectedAlertIds }
                                val someSessionSelected =
                                    idsInSession.any { it in selectedAlertIds }

                                val toggleSessionSelection: () -> Unit = {
                                    selectedAlertIds = if (allSessionSelected) {
                                        selectedAlertIds - idsInSession.toSet()
                                    } else {
                                        (selectedAlertIds + idsInSession).distinct()
                                    }
                                }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = UvirRecordSessionContentInset)
                                        .uvirRecordSessionHeaderPressTarget(
                                            onClick = {
                                                if (selectionMode) toggleSessionSelection()
                                                else selectedChartSessionId = headerSessionId
                                            },
                                            onLongClick = {
                                                selectionMode = true
                                                selectedAlertIds = (selectedAlertIds + idsInSession).distinct()
                                            }
                                        )
                                        .padding(UvirRecordSessionHeaderContentPadding)
                                        .semantics {
                                            if (!selectionMode) {
                                                contentDescription = alertSessionChartOpenDescription
                                            }
                                        },
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    UvirHorizontalReveal(selectionMode) {
                                        CompositionLocalProvider(
                                            LocalMinimumInteractiveComponentSize provides 0.dp
                                        ) {
                                            TriStateCheckbox(
                                                state =
                                                    when {
                                                        allSessionSelected -> ToggleableState.On
                                                        someSessionSelected -> ToggleableState.Indeterminate
                                                        else -> ToggleableState.Off
                                                    },
                                                onClick = toggleSessionSelection,
                                                modifier = Modifier.size(24.dp),
                                                colors = CheckboxDefaults.colors(
                                                    checkedColor = MaterialTheme.colorScheme.primary,
                                                    uncheckedColor = MaterialTheme.colorScheme.primary,
                                                    checkmarkColor = MaterialTheme.colorScheme.onPrimary
                                                )
                                            )
                                        }
                                        Spacer(Modifier.width(8.dp))
                                    }

                                    SessionIdBadge(
                                        id = headerSessionId,
                                        textColor = primaryText,
                                        containerColor = alertSessionColor,
                                        contentColor = alertSessionContentColor
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text = "·",
                                        color = secondaryText,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text =
                                            pluralStringResource(
                                                R.plurals.alert_session_details,
                                                sessionCounts[headerSessionId] ?: 1,
                                                formatAutomaticSessionDateTime(
                                                    sessionStartTimestamps[headerSessionId]
                                                        ?: entry.timestamp
                                                ),
                                                sessionCounts[headerSessionId] ?: 1
                                            ),
                                        modifier = Modifier.weight(1f),
                                        color = secondaryText,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1
                                    )

                                    if (!selectionMode) {
                                        Box(
                                            modifier = Modifier.size(32.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            UvirDisclosureChevron(
                                                tint = secondaryText
                                            )
                                        }
                                    }
                                }
                                Spacer(Modifier.height(UvirRecordSessionItemGap))
                            }

                            Box(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .then(sessionRailModifier)
                            ) {
                                Card(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .uvirRecordListPressTarget(
                                                onClick = {
                                                    if (selectionMode) {
                                                        selectedAlertIds =
                                                            if (selected) {
                                                                selectedAlertIds - entry.id
                                                            } else {
                                                            selectedAlertIds + entry.id
                                                            }
                                                    } else {
                                                        selectedChartAlertId = entry.id
                                                    }
                                                },
                                                onLongClick = {
                                                    selectionMode = true
                                                    if (!selected) {
                                                        selectedAlertIds =
                                                            selectedAlertIds + entry.id
                                                    }
                                                }
                                            ),
                                    shape = RoundedCornerShape(16.dp),
                                    colors =
                                        CardDefaults.cardColors(
                                            containerColor =
                                                if (selected) {
                                                    MaterialTheme.colorScheme.primary
                                                        .copy(alpha = 0.14f)
                                                } else {
                                                    cardColor
                                                }
                                        )
                                ) {
                                    Row(
                                        modifier =
                                            Modifier
                                                .fillMaxWidth()
                                                .padding(
                                                    start = UvirIslandContentPadding,
                                                    end = 0.dp,
                                                    top = 9.dp,
                                                    bottom = 9.dp
                                                ),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        UvirHorizontalReveal(selectionMode) {
                                            CompositionLocalProvider(
                                                LocalMinimumInteractiveComponentSize provides 0.dp
                                            ) {
                                                Checkbox(
                                                    checked = selected,
                                                    onCheckedChange = { checked ->
                                                        selectedAlertIds =
                                                            if (checked) {
                                                                selectedAlertIds + entry.id
                                                            } else {
                                                                selectedAlertIds - entry.id
                                                            }
                                                    },
                                                    modifier = Modifier.size(24.dp)
                                                )
                                            }
                                            Spacer(Modifier.width(12.dp))
                                        }

                                        Column(
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Row(
                                                modifier =
                                                    Modifier
                                                        .fillMaxWidth()
                                                        .padding(
                                                            end =
                                                                if (selectionMode) {
                                                                    UvirIslandContentPadding
                                                                } else {
                                                                    0.dp
                                                                }
                                                        ),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    Text(
                                                        text = formatDateTime(entry.timestamp),
                                                        color = primaryText,
                                                        fontSize = 13.sp,
                                                        fontWeight = FontWeight.Medium,
                                                        maxLines = 1
                                                    )

                                                    Spacer(Modifier.height(3.dp))

                                                    UvirListRecordDescription(
                                                        sensorName = detailSensorDisplayName(sensorProfiles[entry.sensorId]),
                                                        note = entry.note,
                                                        sessionSequence = entry.sessionSequence,
                                                        emptyNote = stringResource(R.string.no_note),
                                                        color = secondaryText
                                                    )
                                                }

                                                if (!selectionMode) {
                                                    Box(
                                                        modifier =
                                                            Modifier
                                                                .size(32.dp),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        UvirDisclosureChevron(
                                                            tint = secondaryText
                                                        )
                                                    }
                                                }
                                            }

                                            Spacer(Modifier.height(4.dp))

                                            HorizontalDivider(
                                                modifier =
                                                    Modifier.padding(
                                                        end = UvirIslandContentPadding
                                                    ),
                                                color = secondaryText.copy(alpha = 0.16f)
                                            )

                                            Spacer(Modifier.height(4.dp))

                                            violations.forEach { violation ->
                                                    AlertViolationSummary(
                                                        violation = violation,
                                                        primaryText = primaryText,
                                                        secondaryText = secondaryText,
                                                        showBiologicalEquivalentUnit = false,
                                                        showThresholdDeltaOnly = true,
                                                        modifier =
                                                            Modifier.padding(
                                                                end = UvirIslandContentPadding
                                                            )
                                                    )
                                                }
                                        }

                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun AlertViolationSummary(
    violation: ThresholdAlertViolation,
    primaryText: Color,
    secondaryText: Color,
    modifier: Modifier = Modifier,
    showBiologicalEquivalentUnit: Boolean = true,
    showThresholdDeltaOnly: Boolean = false
) {
    val metricLabel =
        stringResource(
            thresholdAlertMetricLabelResource(
                violation.rule.metric
            )
        )
    val unit =
        if (
            violation.rule.metric.isBiologicalEffect() &&
            showBiologicalEquivalentUnit
        ) {
            "µW/cm² eq."
        } else {
            "µW/cm²"
        }
    val symbol =
        if (
            violation.rule.direction ==
            ThresholdAlertDirection.ABOVE
        ) {
            "≥"
        } else {
            "≤"
        }
    val numericFormat = LocalUvirNumericFormat.current
    val detailText =
        if (showThresholdDeltaOnly) {
            alertThresholdDeltaPercent(
                value = violation.value,
                threshold = violation.rule.threshold.toDouble()
            )?.let { delta ->
                val sign =
                    when {
                        delta > 0.0 -> "+"
                        delta < 0.0 -> "−"
                        else -> ""
                    }
                "$sign${formatUvirNumber(abs(delta), 1, numericFormat)}%"
            } ?: "—"
        } else {
            formatUvirNumber(
                violation.value,
                3,
                numericFormat
            ) +
                " $symbol " +
                formatUvirNumber(
                    violation.rule.threshold.toDouble(),
                    3,
                    numericFormat
                ) +
                " $unit"
        }

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = metricLabel,
            modifier =
                Modifier
                    .weight(1f)
                    .padding(end = 8.dp),
            color =
                thresholdAlertMetricDisplayColor(
                    violation.rule.metric
                ),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = detailText,
            color = primaryText,
            fontSize = 10.sp,
            textAlign = TextAlign.End,
            maxLines = 1,
            softWrap = false
        )
    }
}

// =====================================================
// STORICO
// =====================================================
