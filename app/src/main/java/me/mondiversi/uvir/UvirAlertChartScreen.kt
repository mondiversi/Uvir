package me.mondiversi.uvir

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun AlertChartScreen(
    entry: ThresholdAlertLogEntry,
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
    val detailSensorName = rememberDetailSensorName(entry.sensorId, database)
    val allBars = remember(entry) { alertChartBars(entry) }
    val allViolations =
        remember(entry.details) {
            parseThresholdAlertLogDetails(entry.details)
        }
    var viewMode by rememberSaveable(entry.id) {
        mutableStateOf(ViewMode.IRRADIANCE)
    }
    var showChart by rememberSaveable(entry.id) {
        mutableStateOf(false)
    }
    var chartExpanded by rememberSaveable(entry.id) {
        mutableStateOf(true)
    }
    var dataExpanded by rememberSaveable(entry.id) {
        mutableStateOf(true)
    }
    var showDeleteConfirmation by rememberSaveable(entry.id) {
        mutableStateOf(false)
    }
    var showShareDialog by rememberSaveable(entry.id) {
        mutableStateOf(false)
    }
    var currentNote by rememberSaveable(entry.id) {
        mutableStateOf(entry.note)
    }
    var showNoteEditor by rememberSaveable(entry.id) {
        mutableStateOf(false)
    }
    val currentEntry =
        remember(entry, currentNote) {
            entry.copy(note = currentNote)
        }
    val showBiological = viewMode == ViewMode.BIOLOGICAL_EFFECTS
    val bars =
        allBars.filter {
            it.metric.isBiologicalEffect() == showBiological
        }
    val violations =
        allViolations.filter {
            it.rule.metric.isBiologicalEffect() == showBiological
        }
    val shareDescription = stringResource(R.string.alert_chart_share)
    val shareErrorText = stringResource(R.string.alert_chart_share_error)
    val deleteDescription = stringResource(R.string.delete)
    val scrollState = rememberLazyListState()
    LaunchedEffect(entry.id) {
        scrollState.scrollToItem(0)
    }
    BackHandler(onBack = onBack)

    if (showShareDialog) {
        MeasurementDetailShareDialog(
            cardColor = cardColor,
            primaryText = primaryText,
            secondaryText = secondaryText,
            combinedChartFileCount = 1,
            separateChartFileCount = alertChartGroupCount(entry),
            onDismiss = {
                showShareDialog = false
            },
            onSelectionConfirmed = { selection, destination ->
                runCatching {
                    shareAlertDetail(
                        context = context,
                        entry = currentEntry,
                        selection = selection,
                        destination = destination
                    )
                }.onFailure { error ->
                    UvirErrorLog.record(
                        context,
                        "share_alert_detail",
                        error
                    )
                    showUvirBottomMessage(
                        context,
                        shareErrorText,
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
                if (database.updateAlertNote(entry.id, updatedNote)) {
                    currentNote = updatedNote
                    showNoteEditor = false
                }
            },
            onDismiss = {
                showNoteEditor = false
            }
        )
    }

    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = {
                showDeleteConfirmation = false
            },
            title = {
                Text(stringResource(R.string.delete_alert_question))
            },
            text = {
                Text(stringResource(R.string.delete_alert_warning))
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        val deleted =
                            database.deleteThresholdAlertLogs(
                                listOf(entry.id)
                            )
                        showDeleteConfirmation = false
                        if (deleted > 0) {
                            showUvirBottomMessage(
                                context,
                                resources.getString(R.string.alert_deleted),
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
                        text = stringResource(R.string.alert_chart_title),
                        modifier = Modifier.weight(1f),
                        color = primaryText
                    )
                    IconButton(
                        onClick = {
                            showShareDialog = true
                        },
                        enabled = allBars.isNotEmpty(),
                        modifier =
                            Modifier
                                .size(UvirTitleActionButtonSize)
                                .semantics {
                                    contentDescription = shareDescription
                                }
                    ) {
                        UvirTitleActionIcon(
                            type = MenuIconType.EXPORT,
                            modifier = Modifier.size(UvirTitleActionIconSize),
                            tint =
                                if (allBars.isNotEmpty()) {
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
                            tint = UvirDestructiveActionColor
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
                primaryId = entry.id,
                sessionId = entry.sessionId,
                idLabel =
                    "ID / ${stringResource(R.string.session_label)}",
                dateLabel = stringResource(R.string.share_date_label),
                dateText = formatDetailDateTime(
                    entry.timestamp,
                    LocalUvirDateFormat.current,
                    LocalUvirTimeFormat.current
                ),
                cardColor = cardColor,
                primaryText = primaryText,
                secondaryText = secondaryText,
                primaryIdDescriptionLabelResource =
                    R.string.alert_chart_id_label,
                sessionContainerColor =
                    uvirAlertSessionIndicatorColor(
                        androidx.compose.foundation.isSystemInDarkTheme()
                    ),
                sessionContentColor = uvirAlertSessionContentColor()
            ) }

            item { UvirDetailContextCard(
                automatic = true,
                sensorName = detailSensorName,
                note =
                    alertDisplayNote(
                        note = currentNote,
                        sessionSequence = entry.sessionSequence,
                        emptyNote = stringResource(R.string.no_note)
                    ),
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

            if (bars.isEmpty()) {
                item { Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = cardColor)
                ) {
                    Text(
                        text = stringResource(R.string.alert_chart_empty),
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        color = secondaryText,
                        textAlign = TextAlign.Center
                    )
                } }
            } else if (showChart) {
                item { UvirCollapsibleChartCard(
                    title = stringResource(R.string.alert_chart_scale),
                    subtitle = stringResource(R.string.alert_chart_percentage_value),
                    iconViewMode = viewMode,
                    expanded = chartExpanded,
                    onToggle = {
                        chartExpanded = !chartExpanded
                    },
                    cardColor = cardColor,
                    primaryText = primaryText,
                    secondaryText = secondaryText
                ) {
                    AlertVerticalBarChart(
                        bars = bars,
                        primaryText = primaryText,
                        secondaryText = secondaryText
                    )
                } }
            } else {
                item { AlertViolationDataCard(
                    biologicalEffects = showBiological,
                    violations = violations,
                    qualityFlags = entry.qualityFlags,
                    expanded = dataExpanded,
                    onToggle = {
                        dataExpanded = !dataExpanded
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
private fun AlertVerticalBarChart(
    bars: List<AlertChartBar>,
    primaryText: Color,
    secondaryText: Color
) {
    val numericFormat = LocalUvirNumericFormat.current
    val logExtent =
        alertThresholdCenteredLogExtent(
            bars.filterNot { it.outOfRange }.map { it.thresholdPercent }
        )
    val axisLabels =
        alertThresholdCenteredLogTicks(logExtent).map { value ->
            formatUvirNumber(
                value,
                alertThresholdAxisFractionDigits(value),
                numericFormat
            ) + "%"
        }

    val inspectionPoints = bars.mapIndexedNotNull { index, bar ->
        if (bar.outOfRange) return@mapIndexedNotNull null
        val delta = bar.thresholdDeltaPercent
        UvirSavedChartPoint(
            UvirChartCoordinate((index + 0.5f) / bars.size.coerceAtLeast(1),
                1f - alertThresholdCenteredLogFraction(bar.thresholdPercent, logExtent)),
            stringResource(thresholdAlertMetricLabelResource(bar.metric)),
            (if (delta < 0.0) "−" else "+") + formatUvirNumber(kotlin.math.abs(delta), 1, numericFormat) + "%",
            bar.color)
    }

    Column(modifier = Modifier.padding(16.dp)) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(270.dp)
                    .background(
                        primaryText.copy(alpha = 0.025f),
                        RoundedCornerShape(12.dp)
                    )
                    .padding(horizontal = 10.dp, vertical = 10.dp)
        ) {
            UvirChartYAxis(
                maximum = 100.0,
                fractionDigits = 0,
                secondaryText = secondaryText,
                modifier = Modifier.fillMaxSize(),
                labels = axisLabels
            ) { chartModifier ->
                UvirSavedChartInspector(
                            points = inspectionPoints, modifier = chartModifier, bars = true
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

                    val thresholdY = size.height / 2f
                    drawPath(
                        path =
                            Path().apply {
                                moveTo(0f, thresholdY)
                                lineTo(size.width, thresholdY)
                            },
                        color = UvirAttentionColor,
                        style =
                            Stroke(
                                width = 1.5.dp.toPx(),
                                pathEffect =
                                    PathEffect.dashPathEffect(
                                        floatArrayOf(
                                            9.dp.toPx(),
                                            6.dp.toPx()
                                        )
                                    )
                            )
                    )

                    val slotWidth = size.width / bars.size
                    val barWidth =
                        (slotWidth * 0.48f)
                            .coerceAtMost(42.dp.toPx())
                    bars.forEachIndexed { index, bar ->
                        if (bar.outOfRange) return@forEachIndexed
                        val normalized =
                            alertThresholdCenteredLogFraction(
                                bar.thresholdPercent,
                                logExtent
                            )
                        val barHeight = size.height * normalized
                        val left =
                            slotWidth * index +
                                (slotWidth - barWidth) / 2f
                        drawRoundRect(
                            color = bar.color,
                            topLeft = Offset(left, size.height - barHeight),
                            size = Size(barWidth, barHeight),
                            cornerRadius =
                                androidx.compose.ui.geometry.CornerRadius(
                                    barWidth * 0.22f,
                                    barWidth * 0.22f
                                )
                            )
                    }
                }
                        }
            }
        }

        Row(modifier = Modifier.fillMaxWidth()) {
            bars.forEach { bar ->
                Text(
                    text = bar.shortLabel,
                    modifier = Modifier.weight(1f),
                    color = primaryText,
                    fontSize = 9.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.alert_chart_threshold_reference),
            modifier = Modifier.fillMaxWidth(),
            color = UvirAttentionColor,
            fontSize = 11.sp,
            textAlign = TextAlign.End
        )
        Spacer(Modifier.height(12.dp))

        bars.chunked(2).forEach { rowBars ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                rowBars.forEach { bar ->
                    val delta = bar.thresholdDeltaPercent
                    val deltaText =
                        if (bar.outOfRange) {
                            stringResource(R.string.out_of_range_short)
                        } else {
                            (if (delta < 0.0) "−" else "+") +
                                formatUvirNumber(abs(delta), 1, numericFormat) + "%"
                        }
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier =
                                Modifier
                                    .size(10.dp)
                                    .background(bar.color, RoundedCornerShape(50))
                        )
                        Spacer(Modifier.width(6.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text =
                                    stringResource(
                                        thresholdAlertMetricLabelResource(bar.metric)
                                    ),
                                color = primaryText,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 2,
                                lineHeight = 13.sp
                            )
                            Text(
                                text = deltaText,
                                color = primaryText,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Normal,
                                maxLines = 1
                            )
                        }
                    }
                }
                repeat(2 - rowBars.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(7.dp))
        }
    }
}
