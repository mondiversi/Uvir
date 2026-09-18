package me.mondiversi.uvir

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun AlertSessionChartScreen(
    sessionId: Long,
    entries: List<ThresholdAlertLogEntry>,
    database: UvirDatabaseHelper,
    backgroundColor: Color,
    cardColor: Color,
    primaryText: Color,
    secondaryText: Color,
    onBack: () -> Unit,
    onDeleted: () -> Unit
) {
    val context = LocalContext.current
    val resources = androidx.compose.ui.platform.LocalResources.current
    val sortedEntries = remember(entries) { entries.sortedBy { it.timestamp } }
    val detailSensorName =
        rememberDetailSensorName(sortedEntries.firstOrNull()?.sensorId, database)
    val availableSeries =
        remember(sortedEntries) {
            alertSessionChartSeries(sortedEntries)
        }
    val expandedValueSections =
        remember {
            mutableStateMapOf<ViewMode, Boolean>()
        }
    val expandedCombinedCharts =
        remember {
            mutableStateMapOf<ViewMode, Boolean>()
        }
    var viewMode by rememberSaveable(sessionId) {
        mutableStateOf(ViewMode.IRRADIANCE)
    }
    var showShareDialog by rememberSaveable {
        mutableStateOf(false)
    }
    var showDeleteConfirmation by rememberSaveable(sessionId) {
        mutableStateOf(false)
    }
    var currentNote by rememberSaveable(sessionId) {
        mutableStateOf(
            database.readAlertSessionNote(sessionId).ifBlank {
                sortedEntries.firstOrNull()?.note.orEmpty()
            }
        )
    }
    var showNoteEditor by rememberSaveable(sessionId) {
        mutableStateOf(false)
    }
    var showChart by rememberSaveable(sessionId) {
        mutableStateOf(false)
    }
    val visibleSeries =
        availableSeries.filter {
            it.metric.isBiologicalEffect() ==
                (viewMode == ViewMode.BIOLOGICAL_EFFECTS)
        }
    val visibleEvents =
        remember(sortedEntries, viewMode) {
            alertSessionValueEvents(
                entries = sortedEntries,
                biologicalEffects =
                    viewMode == ViewMode.BIOLOGICAL_EFFECTS
            )
        }
    val noteAwareEntries =
        remember(sortedEntries, currentNote) {
            sortedEntries.map { it.copy(note = currentNote) }
        }
    val darkMode = androidx.compose.foundation.isSystemInDarkTheme()
    val alertSessionColor = uvirAlertSessionIndicatorColor(darkMode)
    val alertSessionContentColor = uvirAlertSessionContentColor()
    val chartShareDescription =
        stringResource(R.string.alert_session_chart_share)
    val deleteDescription = stringResource(R.string.delete)
    val scrollState = rememberLazyListState()

    LaunchedEffect(sessionId) {
        scrollState.scrollToItem(0)
    }

    BackHandler(onBack = onBack)

    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = {
                showDeleteConfirmation = false
            },
            title = {
                Text(stringResource(R.string.delete_alert_session_question))
            },
            text = {
                Text(stringResource(R.string.delete_alert_session_warning))
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        val deleted =
                            database.deleteThresholdAlertLogs(
                                sortedEntries.map { it.id }
                            )
                        showDeleteConfirmation = false
                        if (deleted > 0) {
                            showUvirBottomMessage(
                                context,
                                resources.getString(R.string.alert_session_deleted),
                                longDuration = false
                            )
                            onDeleted()
                        }
                    },
                    colors =
                        ButtonDefaults.textButtonColors(
                            contentColor = UvirDestructiveActionColor
                        )
                ) {
                    Text(stringResource(R.string.delete))
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirmation = false
                    }
                ) {
                    Text(stringResource(R.string.cancel))
                }
            },
            containerColor = cardColor,
            titleContentColor = primaryText,
            textContentColor = secondaryText
        )
    }

    if (showShareDialog) {
        MeasurementDetailShareDialog(
            cardColor = cardColor,
            primaryText = primaryText,
            secondaryText = secondaryText,
            combinedChartFileCount = 1,
            separateChartFileCount = alertSessionChartGroupCount(sortedEntries),
            onDismiss = {
                showShareDialog = false
            },
            onSelectionConfirmed = { selection, destination ->
                runCatching {
                    shareAlertSessionDetail(
                        context = context,
                        sessionId = sessionId,
                        entries = noteAwareEntries,
                        selection = selection,
                        destination = destination
                    )
                }.onFailure { error ->
                    UvirErrorLog.record(
                        context,
                        "share_alert_session_detail",
                        error
                    )
                    showUvirBottomMessage(
                        context,
                        resources.getString(
                            R.string.alert_session_chart_share_error
                        ),
                        longDuration = false
                    )
                }
                showShareDialog = false
            }
        )
    }

    if (showNoteEditor) {
        UvirNoteEditDialog(
            initialNote = currentNote,
            cardColor = cardColor,
            primaryText = primaryText,
            secondaryText = secondaryText,
            onSave = { updatedNote ->
                if (database.updateAlertSessionNote(sessionId, updatedNote)) {
                    currentNote = updatedNote
                    showNoteEditor = false
                }
            },
            onDismiss = {
                showNoteEditor = false
            }
        )
    }

    Scaffold(
        containerColor = backgroundColor,
        topBar = {
            UvirCollapsingDetailTitleBar(scrollState) {
                Surface(color = backgroundColor) {
                    Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(UvirTitleBarContentPadding),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    UvirBackButton(onClick = onBack)

                    UvirMenuTitle(
                        text = stringResource(R.string.alert_session_chart_title),
                        modifier = Modifier.weight(1f),
                        color = primaryText
                    )

                    IconButton(
                        onClick = {
                            showShareDialog = true
                        },
                        enabled = availableSeries.isNotEmpty(),
                        modifier =
                            Modifier
                                .size(UvirTitleActionButtonSize)
                                .semantics {
                                    contentDescription = chartShareDescription
                                }
                    ) {
                        UvirTitleActionIcon(
                            type = MenuIconType.EXPORT,
                            modifier = Modifier.size(UvirTitleActionIconSize),
                            tint =
                                if (availableSeries.isNotEmpty()) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    secondaryText.copy(alpha = 0.38f)
                                }
                        )
                    }

                    IconButton(
                        onClick = {
                            showDeleteConfirmation = true
                        },
                        enabled = sortedEntries.isNotEmpty(),
                        modifier =
                            Modifier
                                .size(UvirTitleActionButtonSize)
                                .semantics {
                                    contentDescription = deleteDescription
                                }
                    ) {
                        UvirTitleActionIcon(
                            type = MenuIconType.DELETE,
                            modifier = Modifier.size(UvirTitleActionIconSize),
                            tint =
                                if (sortedEntries.isNotEmpty()) {
                                    UvirDestructiveActionColor
                                } else {
                                    secondaryText.copy(alpha = 0.38f)
                                }
                        )
                    }
                    }
                }
            }
        }
    ) { paddingValues ->
        LazyColumn(
            state = scrollState,
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .lazyScrollbarOverlay(
                        state = scrollState,
                        color = secondaryText.copy(alpha = 0.46f)
                    ),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(UvirIslandSpacing)
        ) {
            item { UvirDetailIdentityCard(
                primaryId = null,
                sessionId = sessionId,
                idLabel =
                    "ID / ${stringResource(R.string.session_label)}",
                dateLabel =
                    stringResource(R.string.session_date_duration_events_label),
                dateText =
                    sortedEntries.firstOrNull()?.let { entry ->
                        formatDetailDateTime(
                            entry.timestamp,
                            LocalUvirDateFormat.current,
                            LocalUvirTimeFormat.current
                        )
                    } ?: "—",
                endDateText =
                    sortedEntries.lastOrNull()?.let { entry ->
                        formatDetailDateTime(
                            entry.timestamp,
                            LocalUvirDateFormat.current,
                            LocalUvirTimeFormat.current
                        )
                    } ?: "—",
                durationText =
                    formatInterval(
                        (
                            (sortedEntries.lastOrNull()?.timestamp ?: 0L) -
                                (sortedEntries.firstOrNull()?.timestamp ?: 0L)
                        ).coerceAtLeast(0L) / 1_000L
                    ),
                durationCount = sortedEntries.size,
                durationCountKind = UvirDetailDurationCountKind.ALERT,
                cardColor = cardColor,
                primaryText = primaryText,
                secondaryText = secondaryText,
                primaryIdDescriptionLabelResource =
                    R.string.alert_chart_id_label,
                sessionContainerColor = alertSessionColor,
                sessionContentColor = alertSessionContentColor
            ) }

            item { UvirDetailContextCard(
                automatic = true,
                sensorName = detailSensorName,
                note = currentNote.ifBlank { stringResource(R.string.no_note) },
                cardColor = cardColor,
                primaryText = primaryText,
                secondaryText = secondaryText,
                onEditNote = {
                    showNoteEditor = true
                }
            ) }

            stickyHeader(key = "detail_view_selector") {
                Surface(color = backgroundColor) {
                    MeasurementDetailViewSelector(
                        selectedMode = viewMode,
                        onModeSelected = { viewMode = it },
                        showChart = showChart,
                        onShowChartChanged = { showChart = it },
                        cardColor = cardColor,
                        primaryText = primaryText,
                        secondaryText = secondaryText,
                        modifier = Modifier.padding(
                            bottom =
                                if (uvirDetailSelectorPinned(scrollState)) {
                                    UvirPinnedSelectorBottomSpacing
                                } else {
                                    0.dp
                                }
                        )
                    )
                }
            }

            if (
                (showChart && visibleSeries.isEmpty()) ||
                    (!showChart && visibleEvents.isEmpty())
            ) {
                item { Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = cardColor)
                ) {
                    Text(
                        text = stringResource(R.string.alert_session_chart_empty),
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        color = secondaryText,
                        textAlign = TextAlign.Center
                    )
                } }
            } else if (showChart) {
                val expanded = expandedCombinedCharts[viewMode] ?: true

                item { UvirCollapsibleChartCard(
                    title = stringResource(R.string.alert_chart_scale),
                    subtitle = stringResource(R.string.alert_chart_percentage_value),
                    iconViewMode = viewMode,
                    expanded = expanded,
                    onToggle = {
                        expandedCombinedCharts[viewMode] = !expanded
                    },
                    cardColor = cardColor,
                    primaryText = primaryText,
                    secondaryText = secondaryText
                ) {
                    AlertSessionCombinedLineChart(
                        entries = sortedEntries,
                        series = visibleSeries,
                        primaryText = primaryText,
                        secondaryText = secondaryText
                    )
                } }
            } else {
                val expanded = expandedValueSections[viewMode] ?: true
                item { AlertSessionValuesCard(
                    events = visibleEvents,
                    biologicalEffects =
                        viewMode == ViewMode.BIOLOGICAL_EFFECTS,
                    expanded = expanded,
                    onToggle = {
                        expandedValueSections[viewMode] = !expanded
                    },
                    cardColor = cardColor,
                    primaryText = primaryText,
                    secondaryText = secondaryText
                ) }
            }
        }
    }
}

@Composable
private fun AlertSessionCombinedLineChart(
    entries: List<ThresholdAlertLogEntry>,
    series: List<AlertSessionChartSeries>,
    primaryText: Color,
    secondaryText: Color
) {
    val percentageScale = series.all { it.usesPercentageScale() }
    val irradianceUnit = LocalUvirIrradianceUnit.current
    val valueScale: (Double) -> Double = if (percentageScale) {
        { value: Double -> value }
    } else {
        irradianceUnit::fromCanonicalUwCm2
    }
    val chartValues =
        series.flatMap { item ->
            item.points.filterNot { it.outOfRange }.map { point ->
                valueScale(point.chartValue(percentageScale))
            }
        }
    val chartThresholds =
        series.flatMap { item ->
            item.points.filterNot { it.outOfRange }.map { point ->
                valueScale(point.chartThreshold(percentageScale))
            }
        }
    val maximum =
        (chartValues + chartThresholds)
            .maxOrNull()
            ?.coerceAtLeast(if (percentageScale) 100.0 else 1.0)
            ?: 1.0
    val numericFormat = LocalUvirNumericFormat.current
    val logExtent =
        if (percentageScale) {
            alertThresholdCenteredLogExtent(chartValues)
        } else {
            1.0
        }
    val axisLabels =
        if (percentageScale) {
            alertThresholdCenteredLogTicks(logExtent).map { value ->
                formatUvirNumber(
                    value,
                    alertThresholdAxisFractionDigits(value),
                    numericFormat
                ) + "%"
            }
        } else {
            null
        }
    val startTime = entries.first().timestamp
    val endTime = entries.last().timestamp
    val timeSpan = (endTime - startTime).coerceAtLeast(1L)
    val timeFormat = LocalUvirTimeFormat.current
    val dateFormat = LocalUvirDateFormat.current
    val inspectionPoints = series.flatMap { item ->
        val label = stringResource(thresholdAlertMetricLabelResource(item.metric))
        item.points.mapNotNull { point ->
            if (point.outOfRange) return@mapNotNull null
            val value = valueScale(point.chartValue(percentageScale))
            UvirSavedChartPoint(
                UvirChartCoordinate(
                    if (startTime == endTime) 0.5f
                    else (point.timestamp - startTime).toFloat() / timeSpan.toFloat(),
                    1f - if (percentageScale) alertThresholdCenteredLogFraction(value, logExtent)
                          else (value.coerceAtLeast(0.0) / maximum).toFloat()),
                label, formatUvirNumber(value, if (percentageScale) 1 else 3, numericFormat) +
                    if (percentageScale) "%" else " ${irradianceUnit.symbol}",
                item.color,
                formatUvirDateTime(
                    point.timestamp,
                    dateFormat,
                    separator = " ",
                    timeFormat = timeFormat
                )
            )
        }
    }

    Column(modifier = Modifier.padding(16.dp)) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val tickFractions =
                if (startTime == endTime) {
                    listOf(0.5f)
                } else {
                    sessionChartTimeTickFractions(
                        availableWidth = (maxWidth - 20.dp).value,
                        minimumTickSpacing = 74f
                    )
                }

            Column {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(260.dp)
                            .background(
                                primaryText.copy(alpha = 0.025f),
                                RoundedCornerShape(12.dp)
                            )
                            .padding(10.dp)
                ) {
                    UvirChartYAxis(
                        maximum = maximum,
                        fractionDigits = 2,
                        secondaryText = secondaryText,
                        modifier = Modifier.fillMaxSize(),
                        labels = axisLabels
                    ) { chartModifier ->
                        UvirSavedChartInspector(
                            points = inspectionPoints, modifier = chartModifier, bars = false
                        ) { inspectionModifier ->
                        Canvas(modifier = inspectionModifier) {
                            repeat(5) { index ->
                                val y = size.height * index / 4f
                                drawLine(
                                    color = secondaryText.copy(alpha = 0.15f),
                                    start = Offset(0f, y),
                                    end = Offset(size.width, y),
                                    strokeWidth = 1.dp.toPx()
                                )
                            }
                        tickFractions.drop(1).dropLast(1).forEach { fraction ->
                            val x = size.width * fraction
                            drawLine(
                                color = secondaryText.copy(alpha = 0.11f),
                                start = Offset(x, 0f),
                                end = Offset(x, size.height),
                                strokeWidth = 1.dp.toPx()
                            )
                        }

                        val thresholdStyle =
                            Stroke(
                                width = 1.5.dp.toPx(),
                                pathEffect =
                                    PathEffect.dashPathEffect(
                                        floatArrayOf(9.dp.toPx(), 6.dp.toPx())
                                    )
                            )
                        if (percentageScale) {
                            val thresholdY = size.height / 2f
                            drawLine(
                                color = UvirAttentionColor,
                                start = Offset(0f, thresholdY),
                                end = Offset(size.width, thresholdY),
                                strokeWidth = 1.5.dp.toPx(),
                                pathEffect = thresholdStyle.pathEffect
                            )
                        } else {
                            series.forEach { item ->
                                val thresholdPath = Path()
                                var hasPreviousThreshold = false
                                item.points.forEachIndexed { index, point ->
                                    if (point.outOfRange) {
                                        hasPreviousThreshold = false
                                        return@forEachIndexed
                                    }
                                    val x =
                                        if (startTime == endTime) {
                                            size.width / 2f
                                        } else {
                                            size.width *
                                                (point.timestamp - startTime).toFloat() /
                                                timeSpan.toFloat()
                                        }
                                    val y =
                                        size.height -
                                            (valueScale(point.threshold) / maximum).toFloat() *
                                            size.height
                                    if (item.points.size == 1) {
                                        thresholdPath.moveTo(0f, y)
                                        thresholdPath.lineTo(size.width, y)
                                    } else if (!hasPreviousThreshold) {
                                        thresholdPath.moveTo(x, y)
                                    } else {
                                        thresholdPath.lineTo(x, y)
                                    }
                                    hasPreviousThreshold = true
                                }
                                drawPath(
                                    path = thresholdPath,
                                    color = UvirAttentionColor.copy(alpha = 0.65f),
                                    style = thresholdStyle
                                )
                            }
                        }

                        series.forEach { item ->
                            val valuePath = Path()
                            var hasPreviousValue = false
                            item.points.forEachIndexed { index, point ->
                                if (point.outOfRange) {
                                    hasPreviousValue = false
                                    return@forEachIndexed
                                }
                                val x =
                                    if (startTime == endTime) {
                                        size.width / 2f
                                    } else {
                                        size.width *
                                            (point.timestamp - startTime).toFloat() /
                                            timeSpan.toFloat()
                                    }
                                val y =
                                    if (percentageScale) {
                                        size.height -
                                            alertThresholdCenteredLogFraction(
                                                point.chartValue(true),
                                                logExtent
                                            ) * size.height
                                    } else {
                                        size.height -
                                            (valueScale(point.chartValue(false)).coerceAtLeast(0.0) / maximum)
                                                .toFloat() * size.height
                                    }
                                if (!hasPreviousValue) valuePath.moveTo(x, y)
                                else valuePath.lineTo(x, y)
                                hasPreviousValue = true
                                drawCircle(
                                    color = item.color,
                                    radius = 3.dp.toPx(),
                                    center = Offset(x, y)
                                )
                            }
                            drawPath(
                                path = valuePath,
                                color = item.color,
                                style = Stroke(width = 2.dp.toPx())
                            )
                        }
                        }
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                SessionChartTimeAxis(
                    startTimestamp = startTime,
                    endTimestamp = endTime,
                    fractions = tickFractions,
                    dateFormat = dateFormat,
                    timeFormat = timeFormat,
                    secondaryText = secondaryText
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        if (percentageScale) {
            Text(
                text = stringResource(R.string.alert_chart_threshold_reference),
                modifier = Modifier.fillMaxWidth(),
                color = UvirAttentionColor,
                fontSize = 11.sp,
                textAlign = TextAlign.End,
                maxLines = 1
            )
        }
        Spacer(Modifier.height(12.dp))
        series.chunked(2).forEach { rowSeries ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                rowSeries.forEach { item ->
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier =
                                Modifier
                                    .size(10.dp)
                                    .background(
                                        item.color,
                                        RoundedCornerShape(50)
                                    )
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text =
                                stringResource(
                                    thresholdAlertMetricLabelResource(
                                        item.metric
                                    )
                                ),
                            color = primaryText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Normal,
                            lineHeight = 13.sp
                        )
                    }
                }
                repeat(2 - rowSeries.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(7.dp))
        }
    }
}
