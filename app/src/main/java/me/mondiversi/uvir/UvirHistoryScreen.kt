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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.state.ToggleableState
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
fun HistoryScreen(
    database: UvirDatabaseHelper,
    historyListState: LazyListState,
    historyScrollAnchor: ListEdgeAnchor,
    onHistoryScrollAnchorChange: (ListEdgeAnchor) -> Unit,
    backgroundColor: Color,
    cardColor: Color,
    primaryText: Color,
    secondaryText: Color,
    onBack: () -> Unit,
    onOpenSessionChart: (Long) -> Unit,
    onOpenRecord: (SavedRecordDetail) -> Unit
) {

    val context =
        LocalContext.current

    val darkMode =
        isSystemInDarkTheme()

    val sessionIndicatorColor =
        uvirSessionIndicatorColor(darkMode)

    var allRecords by rememberLiveAcquisitionRecords(database)
    val sensorProfiles by rememberLiveSensorProfiles(database)
    var filters by rememberRecordListFilters()
    var showFilters by rememberSaveable { mutableStateOf(false) }
    val records = remember(allRecords, filters) { filterAcquisitionRecords(allRecords, filters) }
    val filterSensorNames = remember(allRecords, sensorProfiles) {
        allRecords.map { it.sensorId }.distinct().associate { id ->
            id.filterSensorKey() to detailSensorDisplayName(sensorProfiles[id])
        }
    }
    val filterModes = remember(allRecords) {
        allRecords.filterNot { it.externalCommand }.map { it.automatic }.toSet()
    }
    val filterExternalModeAvailable = remember(allRecords) {
        allRecords.any { it.externalCommand }
    }
    val filterRecordIds = remember(allRecords) { allRecords.map { it.id }.distinct() }
    val filterSessionIds = remember(allRecords) { allRecords.mapNotNull { it.sessionId }.distinct() }
    val filterNotes = remember(allRecords) {
        allRecords.map { it.note.trim() }.filter { it.isNotEmpty() }.distinct()
    }
    val filterDays = remember(allRecords) { uvirAvailableFilterDays(allRecords.map { it.timestamp }) }
    val filterScope = rememberCoroutineScope()

    LaunchedEffect(records.size) {
        if (records.isNotEmpty()) {
            when (historyScrollAnchor) {
                ListEdgeAnchor.START ->
                    historyListState.scrollToItem(0)

                ListEdgeAnchor.END ->
                    historyListState.scrollToItem(
                        records.lastIndex
                    )

                ListEdgeAnchor.MIDDLE -> Unit
            }
        }
    }

    LaunchedEffect(historyListState) {
        snapshotFlow {
            resolveListEdgeAnchor(
                firstVisibleItemIndex =
                    historyListState.firstVisibleItemIndex,
                firstVisibleItemScrollOffset =
                    historyListState.firstVisibleItemScrollOffset,
                totalItemsCount =
                    historyListState.layoutInfo.totalItemsCount,
                canScrollForward =
                    historyListState.canScrollForward
            )
        }.collect { anchor ->
            onHistoryScrollAnchorChange(anchor)
        }
    }

    var showDeleteAllConfirmation by rememberSaveable {
        mutableStateOf(false)
    }

    var showDeleteSelectedConfirmation by rememberSaveable {
        mutableStateOf(false)
    }

    var showShareFormatDialog by remember {
        mutableStateOf(false)
    }

    var showShareAllConfirmation by rememberSaveable {
        mutableStateOf(false)
    }

    var selectionMode by rememberSaveable {
        mutableStateOf(false)
    }

    var selectedRecordIds by rememberSaveable {
        mutableStateOf<List<Long>>(
            emptyList()
        )
    }

    LaunchedEffect(records) {
        val visibleIds = records.map { it.id }.toSet()
        selectedRecordIds = selectedRecordIds.filter { it in visibleIds }
    }

    var pendingSharePlan by remember {
        mutableStateOf<AcquisitionExportPlan?>(null)
    }

    fun handleListBack() {
        when {
            showFilters -> showFilters = false
            selectionMode -> {
                selectionMode = false
                selectedRecordIds = emptyList()
            }
            else -> onBack()
        }
    }

    BackHandler { handleListBack() }

    val deleteAllDescription =
        stringResource(
            R.string.delete_all
        )

    val shareDescription =
        stringResource(
            R.string.share_measurements
        )

    val sessionChartOpenDescription =
        stringResource(
            R.string.session_chart_open
        )

    val shareErrorText =
        stringResource(
            R.string.share_error
        )

    val measurementsDeletedText =
        stringResource(
            R.string.measurements_deleted
        )

    val sessionCounts =
        remember(records) {
            records
                .mapNotNull {
                    it.sessionId
                }
                .groupingBy { it }
                .eachCount()
        }

    val sessionRecordIds =
        remember(records) {
            records
                .filter {
                    it.sessionId != null
                }
                .groupBy {
                    requireNotNull(
                        it.sessionId
                    )
                }
                .mapValues { (_, sessionRecords) ->
                    sessionRecords.map { it.id }
                }
        }

    val sessionStartTimestamps =
        remember(records) {
            records
                .asSequence()
                .filter {
                    it.sessionId != null
                }
                .groupBy {
                    requireNotNull(
                        it.sessionId
                    )
                }
                .mapValues { (_, sessionRecords) ->
                    sessionRecords.minOf {
                        it.timestamp
                    }
                }
        }

    val groupedRecords =
        remember(records) {
            val recordsBySession =
                records
                    .filter {
                        it.sessionId != null
                    }
                    .groupBy {
                        requireNotNull(
                            it.sessionId
                        )
                    }
            val emittedSessions =
                mutableSetOf<Long>()

            buildList {
                records.forEach { record ->
                    val sessionId =
                        record.sessionId
                    if (sessionId == null) {
                        add(record)
                    } else if (
                        emittedSessions.add(
                            sessionId
                        )
                    ) {
                        addAll(
                            recordsBySession[
                                sessionId
                            ].orEmpty()
                        )
                    }
                }
            }
        }

    val sessionFirstRecordIds =
        remember(groupedRecords) {
            mutableMapOf<Long, Long>()
                .apply {
                    groupedRecords.forEach {
                            record ->
                        record.sessionId
                            ?.let { sessionId ->
                                putIfAbsent(
                                    sessionId,
                                    record.id
                                )
                            }
                    }
                }
        }

    fun openShareFormat(ids: List<Long>) {
        val selectedDetails =
            ids.mapNotNull { id ->
                database.readRecord(id)
            }
        if (selectedDetails.isNotEmpty()) {
            pendingSharePlan =
                buildAcquisitionExportPlan(
                    selectedRecords = selectedDetails,
                    allRecords = database.readAllRecords()
                )
            showShareFormatDialog = true
        }
    }

    fun sharePendingRecords(
        selection: MeasurementDetailShareSelection,
        destination: UvirExportDestination
    ) {
        pendingSharePlan?.let { plan ->
            runCatching {
                shareAcquisitionExportPlan(
                    context,
                    plan,
                    selection,
                    destination
                )
            }.onFailure { error ->
                UvirErrorLog.record(
                    context,
                    "share_acquisitions",
                    error
                )
                showUvirBottomMessage(
                    context,
                    shareErrorText,
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
                        R.string.delete_all_measurements_question
                    )
                )
            },
            text = {
                Text(
                    stringResource(
                        R.string.delete_all_measurements_warning
                    )
                )
            },
            dismissButton = {
                HoldToConfirmDeleteButton(
                    label =
                        stringResource(
                            R.string.delete_all
                        ),
                    onConfirmed = {
                        database.deleteAllAcquisitions()
                        allRecords = database.readSavedRecords()
                        showDeleteAllConfirmation = false
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
                        R.string.delete_selected_measurements_question
                    )
                )
            },
            text = {
                Text(
                    pluralStringResource(
                        R.plurals.delete_selected_measurements_warning,
                        selectedRecordIds.size,
                        selectedRecordIds.size
                    )
                )
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        val deleted =
                            database.deleteRecords(
                                selectedRecordIds
                            )

                        allRecords =
                            database.readSavedRecords()
                        selectedRecordIds =
                            emptyList()
                        selectionMode = false
                        showDeleteSelectedConfirmation =
                            false

                        if (deleted > 0) {
                            showUvirBottomMessage(
                                context,
                                measurementsDeletedText,
                                longDuration = false
                            )
                        }
                    },
                    colors =
                        ButtonDefaults.textButtonColors(
                            contentColor =
                                UvirDestructiveActionColor
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
                        showDeleteSelectedConfirmation =
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

    if (showShareAllConfirmation) {
        AlertDialog(
            onDismissRequest = {
                showShareAllConfirmation = false
            },
            title = {
                Text(
                    stringResource(
                        if (filters.isActive) R.string.list_filter_export_question else R.string.export_all_measurements_question
                    )
                )
            },
            text = {
                Text(
                    stringResource(
                        if (filters.isActive) R.string.list_filter_export_warning else R.string.export_all_measurements_warning
                    )
                )
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showShareAllConfirmation = false
                        openShareFormat(
                            records.map { it.id }
                        )
                    }
                ) {
                    Text(
                        stringResource(
                            R.string.export_all
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
            partialSessionWarning =
                pendingSharePlan?.hasPartialSessions == true,
            combinedChartFileCount =
                pendingSharePlan?.let { plan ->
                    acquisitionExportChartFileCount(
                        plan,
                        UvirChartExportMode.COMBINED
                    )
                },
            separateChartFileCount =
                pendingSharePlan?.let { plan ->
                    acquisitionExportChartFileCount(
                        plan,
                        UvirChartExportMode.SEPARATE
                    )
                },
            onDismiss = {
                showShareFormatDialog = false
                pendingSharePlan = null
            },
            onSelectionConfirmed = { selection, destination ->
                sharePendingRecords(selection, destination)
            }
        )
    }

    Scaffold(
        containerColor = backgroundColor,
        topBar = {
            Surface(color = backgroundColor) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(UvirTitleBarContentPadding),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    UvirBackButton(
                        onClick = { handleListBack() }
                    )

                    UvirMenuTitle(
                        text =
                            stringResource(
                                R.string.saved_measurements
                            ),
                        modifier = Modifier.weight(1f),
                        color = primaryText
                    )

                    UvirRecordListFilterButton(
                        active = filters.isActive,
                        enabled = allRecords.isNotEmpty(),
                        onClick = { showFilters = !showFilters }
                    )

                    IconButton(
                        onClick = {
                            if (selectedRecordIds.isEmpty()) {
                                showShareAllConfirmation = true
                            } else {
                                openShareFormat(
                                    selectedRecordIds
                                )
                            }
                        },
                        enabled =
                            records.isNotEmpty(),
                        modifier =
                            Modifier
                                .size(UvirTitleActionButtonSize)
                                .semantics {
                                    contentDescription =
                                        shareDescription
                                }
                    ) {
                        UvirTitleActionIcon(
                            type =
                                MenuIconType.EXPORT,
                            modifier =
                                Modifier.size(UvirTitleActionIconSize),
                            tint =
                                if (records.isNotEmpty()) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    secondaryText.copy(alpha = 0.38f)
                                }
                        )
                    }

                    IconButton(
                        onClick = {
                            if (selectedRecordIds.isEmpty() && filters.isActive) {
                                selectedRecordIds = records.map { it.id }
                                showDeleteSelectedConfirmation = true
                            } else if (selectedRecordIds.isEmpty()) {
                                showDeleteAllConfirmation = true
                            } else {
                                showDeleteSelectedConfirmation = true
                            }
                        },
                        enabled =
                            records.isNotEmpty(),
                        modifier =
                            Modifier
                                .size(UvirTitleActionButtonSize)
                                .semantics {
                                    contentDescription =
                                        deleteAllDescription
                                }
                    ) {
                        UvirTitleActionIcon(
                            type =
                                MenuIconType.DELETE,
                            modifier =
                                Modifier.size(UvirTitleActionIconSize),
                            tint =
                                if (records.isNotEmpty()) {
                                    UvirDestructiveActionColor
                                } else {
                                    secondaryText.copy(alpha = 0.38f)
                                }
                        )
                    }
                }
            }
        },
        bottomBar = {
            if (records.isNotEmpty()) {
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
                                stringResource(R.string.list_filter_count, records.size, allRecords.size)
                            } else pluralStringResource(
                                    R.plurals.measurement_count_since,
                                    records.size,
                                    records.size,
                                    formatDateTime(
                                        records.asSequence()
                                            .map { it.timestamp }
                                            .filter { it > 0L }
                                            .minOrNull()
                                            ?: records.minOf { it.timestamp },
                                        LocalUvirDateFormat.current,
                                        LocalUvirTimeFormat.current
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
        if (allRecords.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.no_saved_measurements),
                    color = secondaryText
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
                        filters = filters,
                        recordIds = filterRecordIds,
                        sessionIds = filterSessionIds,
                        notes = filterNotes,
                        dates = filterDays,
                        sensorNames = filterSensorNames,
                        modes = filterModes,
                        externalModeAvailable = filterExternalModeAvailable,
                        backgroundColor = backgroundColor,
                        onChange = {
                            filters = it
                            selectedRecordIds = emptyList()
                            filterScope.launch { historyListState.scrollToItem(0) }
                        }
                    )
                }

                UvirVerticalReveal(selectionMode && records.isNotEmpty()) {
                    val allSelected =
                        records.all { record ->
                            record.id in selectedRecordIds
                        }

                    val selectAllIds = {
                        selectedRecordIds =
                            if (allSelected) {
                                emptyList()
                            } else {
                                records.map { it.id }
                            }
                    }

                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(
                                    horizontal = 20.dp
                                )
                                .clickable {
                                    selectAllIds()
                                }
                                .padding(
                                    start = 0.dp,
                                    end = 16.dp,
                                    top = 8.dp,
                                    bottom = 8.dp
                                ),
                        verticalAlignment =
                            Alignment.CenterVertically
                    ) {
                        CompositionLocalProvider(
                            LocalMinimumInteractiveComponentSize provides 0.dp
                        ) {
                            Checkbox(
                                checked = allSelected,
                                onCheckedChange = {
                                    checked ->
                                    selectedRecordIds =
                                        if (checked) {
                                            records.map { it.id }
                                        } else {
                                            emptyList()
                                        }
                                },
                                modifier =
                                    Modifier.size(24.dp),
                                colors =
                                    CheckboxDefaults.colors(
                                        checkedColor =
                                            MaterialTheme
                                                .colorScheme
                                                .primary,
                                        uncheckedColor =
                                            MaterialTheme
                                                .colorScheme
                                                .primary,
                                        checkmarkColor =
                                            MaterialTheme
                                                .colorScheme
                                                .onPrimary
                                    )
                            )
                        }

                        Spacer(
                            Modifier.width(12.dp)
                        )

                        Text(
                            text =
                                stringResource(
                                    if (allSelected) {
                                        R.string.deselect_all
                                    } else {
                                        R.string.select_all
                                    }
                                ),
                            color =
                                MaterialTheme.colorScheme.primary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    HorizontalDivider(
                        modifier =
                            Modifier.padding(
                                horizontal = 20.dp
                            ),
                        color =
                            secondaryText.copy(
                                alpha = 0.12f
                            )
                    )
                }

                LazyColumn(
                    state =
                        historyListState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .lazyScrollbarOverlay(
                            state =
                                historyListState,
                            color =
                                secondaryText.copy(
                                    alpha = 0.46f
                                )
                        ),
                    contentPadding = PaddingValues(
                        start = 20.dp,
                        end = 20.dp,
                        top = 4.dp,
                        bottom = 20.dp
                    ),
                    verticalArrangement =
                        Arrangement.Top
                ) {

                    if (records.isEmpty()) {
                        item { Text(stringResource(R.string.list_filter_no_results),
                            color = secondaryText, modifier = Modifier.fillMaxWidth().padding(24.dp),
                            textAlign = TextAlign.Center) }
                    }
                    itemsIndexed(
                        items = groupedRecords,
                        key = { _, record ->
                            record.id
                        }
                    ) { index, record ->
                        val selected =
                            record.id in selectedRecordIds

                        val sessionId =
                            record.sessionId

                        val continuesSession =
                            index > 0 &&
                                    sessionId != null &&
                                    groupedRecords[index - 1]
                                        .sessionId ==
                                    sessionId

                        val isLastSessionRecord =
                            sessionId != null &&
                                    (
                                            index ==
                                            groupedRecords.lastIndex ||
                                                    groupedRecords[
                                                        index + 1
                                                    ].sessionId !=
                                                    sessionId
                                            )

                        val headerSessionId =
                            sessionId?.takeIf {
                                sessionFirstRecordIds[
                                    it
                                ] == record.id
                            }

                        val sessionRailModifier =
                            if (sessionId != null) {
                                Modifier
                                    .padding(
                                        start = UvirRecordSessionContentInset,
                                        bottom = UvirRecordSessionItemGap
                                    )
                            } else {
                                Modifier.padding(
                                    start = UvirRecordSessionContentInset,
                                    bottom = UvirRecordSessionItemGap
                                )
                            }

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(
                                    top =
                                        if (
                                            index == 0 ||
                                            continuesSession
                                        ) {
                                            0.dp
                                        } else {
                                            4.dp
                                        }
                                )
                                .then(if (sessionId != null) Modifier.uvirRecordSessionRail(
                                    color = sessionIndicatorColor, isFirst = headerSessionId != null,
                                    isLast = isLastSessionRecord
                                ) else Modifier)
                        ) {
                            if (headerSessionId != null) {
                                val idsInSession =
                                    sessionRecordIds[
                                        headerSessionId
                                    ].orEmpty()
                                val allSessionSelected =
                                    idsInSession.isNotEmpty() &&
                                            idsInSession.all {
                                                it in selectedRecordIds
                                            }
                                val someSessionSelected =
                                    idsInSession.any {
                                        it in selectedRecordIds
                                    }

                                val toggleSessionSelection: () -> Unit = {
                                    selectedRecordIds = if (allSessionSelected) {
                                        selectedRecordIds - idsInSession.toSet()
                                    } else {
                                        (selectedRecordIds + idsInSession).distinct()
                                    }
                                }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = UvirRecordSessionContentInset)
                                        .uvirRecordSessionHeaderPressTarget(
                                            onClick = {
                                                if (selectionMode) toggleSessionSelection()
                                                else onOpenSessionChart(headerSessionId)
                                            },
                                            onLongClick = {
                                                selectionMode = true
                                                selectedRecordIds = (selectedRecordIds + idsInSession).distinct()
                                            }
                                        )
                                        .padding(UvirRecordSessionHeaderContentPadding)
                                        .semantics {
                                            if (!selectionMode) {
                                                contentDescription = sessionChartOpenDescription
                                            }
                                        },
                                    verticalAlignment =
                                        Alignment.CenterVertically
                                ) {
                                    UvirHorizontalReveal(selectionMode) {
                                        CompositionLocalProvider(
                                            LocalMinimumInteractiveComponentSize provides 0.dp
                                        ) {
                                            TriStateCheckbox(
                                                state =
                                                    when {
                                                        allSessionSelected ->
                                                            ToggleableState.On

                                                        someSessionSelected ->
                                                            ToggleableState.Indeterminate

                                                        else ->
                                                            ToggleableState.Off
                                                    },
                                                onClick = toggleSessionSelection,
                                                modifier =
                                                    Modifier.size(24.dp),
                                                colors =
                                                    CheckboxDefaults.colors(
                                                        checkedColor =
                                                            MaterialTheme
                                                                .colorScheme
                                                                .primary,
                                                        uncheckedColor =
                                                            MaterialTheme
                                                                .colorScheme
                                                                .primary,
                                                        checkmarkColor =
                                                            MaterialTheme
                                                                .colorScheme
                                                                .onPrimary
                                                    )
                                            )
                                        }

                                        Spacer(
                                            Modifier.width(8.dp)
                                        )
                                    }

                                    SessionIdBadge(
                                        id = headerSessionId,
                                        textColor = primaryText
                                    )

                                    Spacer(
                                        Modifier.width(4.dp)
                                    )

                                    Text(
                                        text = "·",
                                        color = secondaryText,
                                        fontSize = 12.sp,
                                        fontWeight =
                                            FontWeight.SemiBold
                                    )

                                    Spacer(
                                        Modifier.width(4.dp)
                                    )

                                    Text(
                                        text =
                                            pluralStringResource(
                                                R.plurals.automatic_session_details,
                                                sessionCounts[
                                                    headerSessionId
                                                ] ?: 1,
                                                formatAutomaticSessionDateTime(
                                                    sessionStartTimestamps[
                                                        headerSessionId
                                                    ] ?: record.timestamp,
                                                    LocalUvirDateFormat.current,
                                                    LocalUvirTimeFormat.current
                                                ),
                                                sessionCounts[
                                                    headerSessionId
                                                ] ?: 1
                                            ),
                                        modifier =
                                            Modifier.weight(1f),
                                        color = secondaryText,
                                        fontSize = 12.sp,
                                        fontWeight =
                                            FontWeight.SemiBold
                                    )

                                    if (!selectionMode) {
                                        Box(
                                            modifier =
                                                Modifier
                                                    .size(32.dp),
                                            contentAlignment =
                                                Alignment.Center
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
                                        .then(
                                            sessionRailModifier
                                        )
                            ) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .uvirRecordListPressTarget(
                                    onClick = {
                                        if (selectionMode) {
                                            selectedRecordIds =
                                                if (selected) {
                                                    selectedRecordIds - record.id
                                                } else {
                                                    selectedRecordIds + record.id
                                                }
                                        } else {
                                            val detail =
                                                database.readRecord(record.id)
                                            if (detail != null) {
                                                onOpenRecord(detail)
                                            }
                                        }
                                    },
                                    onLongClick = {
                                        selectionMode = true
                                        if (!selected) {
                                            selectedRecordIds =
                                                selectedRecordIds + record.id
                                        }
                                    }
                                    ),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(
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
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(
                                            horizontal = UvirIslandContentPadding,
                                            vertical = 9.dp
                                        ),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                UvirHorizontalReveal(selectionMode) {
                                    CompositionLocalProvider(
                                        LocalMinimumInteractiveComponentSize provides 0.dp
                                    ) {
                                        Checkbox(
                                            checked = selected,
                                            onCheckedChange = {
                                                isChecked ->
                                                selectedRecordIds =
                                                    if (isChecked) {
                                                        selectedRecordIds + record.id
                                                    } else {
                                                        selectedRecordIds - record.id
                                                    }
                                            },
                                            modifier =
                                                Modifier.size(24.dp)
                                        )
                                    }

                                    Spacer(
                                        Modifier.width(12.dp)
                                    )
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment =
                                            Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text =
                                                formatDateTime(
                                                    record.timestamp,
                                                    LocalUvirDateFormat.current,
                                                    LocalUvirTimeFormat.current
                                                ),
                                            color = primaryText,
                                            fontSize = 13.sp,
                                            fontWeight =
                                                FontWeight.Medium
                                        )
                                    }

                                    Spacer(Modifier.height(3.dp))

                                    UvirListRecordDescription(
                                        sensorName = detailSensorDisplayName(sensorProfiles[record.sensorId]),
                                        note = record.note,
                                        sessionSequence = record.sessionSequence.takeIf { record.automatic },
                                        emptyNote = stringResource(R.string.no_note),
                                        color = secondaryText
                                    )
                                }

                                AcquisitionTypeBadge(
                                    automatic = record.automatic,
                                    externalCommand = record.externalCommand,
                                    primaryText = primaryText,
                                    compact = true
                                )

                                if (!selectionMode) {
                                    Spacer(
                                        Modifier.width(6.dp)
                                    )

                                    UvirDisclosureChevron(
                                        tint = secondaryText
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

// =====================================================
// DETTAGLIO RECORD
// =====================================================
