package me.mondiversi.uvir

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

@Composable
internal fun SessionChartIcon(
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.primary
) {
    Canvas(modifier = modifier) {
        val stroke = max(1.6.dp.toPx(), size.minDimension * 0.075f)
        val left = size.width * 0.14f
        val bottom = size.height * 0.84f

        drawLine(
            color = tint,
            start = Offset(left, size.height * 0.14f),
            end = Offset(left, bottom),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
        drawLine(
            color = tint,
            start = Offset(left, bottom),
            end = Offset(size.width * 0.88f, bottom),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )

        val line = Path().apply {
            moveTo(size.width * 0.22f, size.height * 0.68f)
            lineTo(size.width * 0.42f, size.height * 0.48f)
            lineTo(size.width * 0.58f, size.height * 0.60f)
            lineTo(size.width * 0.82f, size.height * 0.27f)
        }
        drawPath(
            path = line,
            color = tint,
            style = Stroke(
                width = stroke,
                cap = StrokeCap.Round
            )
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SessionChartScreen(
    sessionId: Long,
    records: List<SavedRecordDetail>,
    database: UvirDatabaseHelper,
    backgroundColor: Color,
    cardColor: Color,
    primaryText: Color,
    secondaryText: Color,
    onDeleteSession: () -> Unit,
    onBack: () -> Unit
) {
    val irradianceUnit = LocalUvirIrradianceUnit.current
    val context = LocalContext.current
    val resources = androidx.compose.ui.platform.LocalResources.current
    val sortedRecords =
        remember(records) {
            records.sortedBy { it.timestamp }
        }
    val expandedChartGroups =
        remember {
            mutableStateMapOf<SessionChartGroup, Boolean>()
        }
    var showShareDialog by rememberSaveable {
        mutableStateOf(false)
    }
    var showDeleteConfirmation by rememberSaveable {
        mutableStateOf(false)
    }
    var currentNote by rememberSaveable(sessionId) {
        mutableStateOf(
            database.readAcquisitionSessionNote(sessionId).ifBlank {
                sessionChartNote(sortedRecords, "")
            }
        )
    }
    var showNoteEditor by rememberSaveable(sessionId) {
        mutableStateOf(false)
    }
    var showSequenceFilterPanel by rememberSaveable {
        mutableStateOf(false)
    }
    var sequenceFilterCycleSize by remember(sessionId, database) {
        mutableIntStateOf(database.readAcquisitionSessionVariantsPerPosition(sessionId))
    }
    var sequenceFilterPosition by rememberSaveable(sessionId) {
        mutableIntStateOf(1)
    }
    var showChart by rememberSaveable(sessionId) {
        mutableStateOf(false)
    }
    var viewMode by rememberSaveable(sessionId) {
        mutableStateOf(ViewMode.IRRADIANCE)
    }
    val chartShareDescription =
        stringResource(
            R.string.session_chart_share
        )
    val detailSensorName =
        rememberDetailSensorName(sortedRecords.firstOrNull()?.sensorId, database)
    val availableSequenceCycleSizes = remember(sortedRecords.size) {
        sessionSequenceCycleSizes(sortedRecords.size)
    }
    val sequenceFilterActive =
        sequenceFilterCycleSize >= 2 &&
            sequenceFilterCycleSize in availableSequenceCycleSizes
    val variantAwareRecords =
        remember(sortedRecords, sequenceFilterCycleSize) {
            recordsWithAcquisitionVariantMetadata(
                records = sortedRecords,
                variantsPerPosition = sequenceFilterCycleSize
            )
        }
    val noteAwareRecords =
        remember(variantAwareRecords, currentNote) {
            variantAwareRecords.map { it.copy(note = currentNote) }
        }
    LaunchedEffect(sortedRecords.size) {
        if (sequenceFilterCycleSize !in availableSequenceCycleSizes) {
            sequenceFilterCycleSize = 1
            sequenceFilterPosition = 1
            database.updateAcquisitionSessionVariantsPerPosition(sessionId, 1)
        } else if (sequenceFilterPosition !in 1..sequenceFilterCycleSize) {
            sequenceFilterPosition = 1
        }
    }

    val displayedRecords =
        remember(
            sortedRecords,
            sequenceFilterCycleSize,
            sequenceFilterPosition
        ) {
            filterSessionRecordsBySequence(
                records = variantAwareRecords,
                cycleSize = sequenceFilterCycleSize,
                cyclePosition = sequenceFilterPosition
            )
        }

    val handleBack = {
        if (showSequenceFilterPanel) {
            showSequenceFilterPanel = false
        } else {
            onBack()
        }
    }

    BackHandler(onBack = handleBack)

    if (showSequenceFilterPanel) {
        UvirSessionSequenceFilterPanel(
            records = sortedRecords,
            cycleSize = sequenceFilterCycleSize,
            backgroundColor = cardColor,
            primaryText = primaryText,
            secondaryText = secondaryText,
            onSave = { cycleSize ->
                if (database.updateAcquisitionSessionVariantsPerPosition(sessionId, cycleSize)) {
                    sequenceFilterCycleSize = cycleSize
                    sequenceFilterPosition = 1
                }
                showSequenceFilterPanel = false
            },
            onDismiss = {
                showSequenceFilterPanel = false
            }
        )
    }

    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = {
                showDeleteConfirmation = false
            },
            title = {
                Text(
                    stringResource(
                        R.string.delete_acquisition_session_question
                    )
                )
            },
            text = {
                Text(
                    stringResource(
                        R.string.delete_acquisition_session_warning
                    )
                )
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirmation = false
                        showUvirBottomMessage(
                            context,
                            resources.getString(R.string.acquisition_session_deleted),
                            longDuration = false
                        )
                        onDeleteSession()
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
            combinedChartFileCount =
                acquisitionSessionChartFileCount(
                    noteAwareRecords,
                    UvirChartExportMode.COMBINED
                ),
            separateChartFileCount =
                acquisitionSessionChartFileCount(
                    noteAwareRecords,
                    UvirChartExportMode.SEPARATE
                ),
            onDismiss = {
                showShareDialog = false
            },
            onSelectionConfirmed = { selection, destination ->
                runCatching {
                    shareAcquisitionSessionDetail(
                        context = context,
                        sessionId = sessionId,
                        records = noteAwareRecords,
                        selection = selection,
                        destination = destination
                    )
                }.onFailure { error ->
                    UvirErrorLog.record(
                        context,
                        "share_acquisition_session",
                        error
                    )
                    showUvirBottomMessage(
                        context,
                        resources.getString(
                            R.string.session_chart_share_error
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
                if (database.updateAcquisitionSessionNote(sessionId, updatedNote)) {
                    currentNote = updatedNote
                    showNoteEditor = false
                }
            },
            onDismiss = {
                showNoteEditor = false
            }
        )
    }

    val listState = androidx.compose.foundation.lazy.rememberLazyListState()

    LaunchedEffect(sessionId) {
        listState.scrollToItem(0)
    }

    Scaffold(
        containerColor = backgroundColor,
        topBar = {
            UvirCollapsingDetailTitleBar(listState) {
                Surface(color = backgroundColor) {
                    Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(UvirTitleBarContentPadding),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    UvirBackButton(onClick = handleBack)

                    UvirMenuTitle(
                        text = stringResource(R.string.session_chart_title),
                        modifier = Modifier.weight(1f),
                        color = primaryText
                    )

                    UvirSessionCycleFilterButton(
                        active = sequenceFilterActive,
                        enabled = sortedRecords.size >= 2,
                        onClick = {
                            showSequenceFilterPanel = true
                        }
                    )

                    IconButton(
                        onClick = {
                            showShareDialog = true
                        },
                        enabled = sortedRecords.isNotEmpty(),
                        modifier =
                            Modifier
                                .size(40.dp)
                                .semantics {
                                    contentDescription = chartShareDescription
                                }
                    ) {
                        UvirTitleActionIcon(
                            type = MenuIconType.EXPORT,
                            modifier = Modifier.size(24.dp),
                            tint =
                                if (sortedRecords.isNotEmpty()) {
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
                        enabled = sortedRecords.isNotEmpty(),
                        modifier =
                            Modifier
                                .size(40.dp)
                                .semantics {
                                    contentDescription =
                                        resources.getString(R.string.delete)
                                }
                    ) {
                        UvirTitleActionIcon(
                            type = MenuIconType.DELETE,
                            modifier = Modifier.size(24.dp),
                            tint =
                                if (sortedRecords.isNotEmpty()) {
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
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
        ) {
            LazyColumn(
                state = listState,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .lazyScrollbarOverlay(
                            state = listState,
                            color = secondaryText.copy(alpha = 0.46f)
                        ),
                contentPadding =
                    PaddingValues(
                        start = 20.dp,
                        end = 20.dp,
                        top = 4.dp,
                        bottom = 40.dp
                    ),
                verticalArrangement = Arrangement.spacedBy(UvirIslandSpacing)
            ) {
            item {
                UvirDetailIdentityCard(
                    primaryId = null,
                    sessionId = sessionId,
                    idLabel =
                        "ID / ${stringResource(R.string.session_label)}",
                    dateLabel =
                        stringResource(R.string.session_date_duration_events_label),
                    dateText =
                        sortedRecords.firstOrNull()?.let { record ->
                            formatDetailDateTime(
                                record.timestamp,
                                LocalUvirDateFormat.current,
                                LocalUvirTimeFormat.current
                            )
                        } ?: "—",
                    endDateText =
                        sortedRecords.lastOrNull()?.let { record ->
                            formatDetailDateTime(
                                record.timestamp,
                                LocalUvirDateFormat.current,
                                LocalUvirTimeFormat.current
                            )
                        } ?: "—",
                    durationText =
                        formatInterval(
                            (
                                (sortedRecords.lastOrNull()?.timestamp ?: 0L) -
                                    (sortedRecords.firstOrNull()?.timestamp ?: 0L)
                            ).coerceAtLeast(0L) / 1_000L
                        ),
                    durationCount = sortedRecords.size,
                    durationCountKind = UvirDetailDurationCountKind.ACQUISITION,
                    cardColor = cardColor,
                    primaryText = primaryText,
                    secondaryText = secondaryText
                )
            }

            item {
                UvirDetailContextCard(
                    automatic = sortedRecords.firstOrNull()?.automatic,
                    externalCommand = sortedRecords.any { it.externalCommand },
                    sensorName = detailSensorName,
                    note = currentNote.ifBlank { stringResource(R.string.no_note) },
                    cardColor = cardColor,
                    primaryText = primaryText,
                    secondaryText = secondaryText,
                    onEditNote = {
                        showNoteEditor = true
                    }
                )
            }

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
                    middleContent = {
                        UvirSessionCyclePositionSelector(
                            cycleSize = sequenceFilterCycleSize,
                            position = sequenceFilterPosition,
                            cardColor = cardColor,
                            primaryText = primaryText,
                            secondaryText = secondaryText,
                            onPositionChange = { newPosition ->
                                sequenceFilterPosition =
                                    newPosition.coerceIn(
                                        1,
                                        sequenceFilterCycleSize.coerceAtLeast(1)
                                    )
                            }
                        )
                    },
                    modifier = Modifier.padding(
                        bottom =
                            if (uvirDetailSelectorPinned(listState)) {
                                UvirPinnedSelectorBottomSpacing
                            } else {
                                0.dp
                            }
                    )
                    )
                }
            }

            val visibleGroups =
                if (viewMode == ViewMode.IRRADIANCE) {
                    SessionChartGroup.entries.filterNot { it.biological }
                } else {
                    listOf(SessionChartGroup.BIOLOGICAL)
                }

            if (showChart) {
                items(
                    items = visibleGroups,
                    key = { "chart_${it.name}" }
                ) { group ->
                    val series =
                        remember(displayedRecords, group) {
                            sessionChartSeries(displayedRecords, group)
                        }
                    val expanded =
                        expandedChartGroups[group] ?: true

                    UvirCollapsibleChartCard(
                        title = stringResource(group.titleResource),
                        iconGroup = group.spectrumIconGroup(),
                        subtitle =
                            stringResource(
                                if (group.biological) {
                                    R.string.session_chart_unit_biological
                                } else {
                                    R.string.session_chart_unit
                                }
                            ).withUvirIrradianceUnit(irradianceUnit),
                        expanded = expanded,
                        onToggle = {
                            expandedChartGroups[group] = !expanded
                        },
                        cardColor = cardColor,
                        primaryText = primaryText,
                        secondaryText = secondaryText
                    ) {
                        if (displayedRecords.isEmpty()) {
                            Text(
                                text = stringResource(R.string.session_chart_empty),
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(24.dp),
                                color = secondaryText,
                                textAlign = TextAlign.Center
                            )
                        } else {
                            SessionLineChart(
                                records = displayedRecords,
                                series = series,
                                unit = stringResource(
                                    if (group.biological) {
                                        R.string.session_chart_unit_biological
                                    } else {
                                        R.string.session_chart_unit
                                    }
                                ).withUvirIrradianceUnit(irradianceUnit),
                                valueScale = irradianceUnit::fromCanonicalUwCm2,
                                primaryText = primaryText,
                                secondaryText = secondaryText
                            )
                        }
                    }
                }
            } else {
                items(
                    items = visibleGroups,
                    key = { "values_${it.name}" }
                ) { group ->
                    val expanded =
                        expandedChartGroups[group] ?: true

                    SessionAcquisitionValuesCard(
                        records = displayedRecords,
                        group = group,
                        showRelativeBreakdown = true,
                        expanded = expanded,
                        onToggle = {
                            expandedChartGroups[group] = !expanded
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
}

@Composable
internal fun SessionChartShareOption(
    selected: Boolean,
    title: String,
    description: String,
    primaryText: Color,
    secondaryText: Color,
    onClick: () -> Unit
) {
    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color =
            if (selected) {
                MaterialTheme.colorScheme.primary
                    .copy(alpha = 0.10f)
            } else {
                Color.Transparent
            }
    ) {
        Row(
            modifier =
                Modifier.padding(
                    horizontal = 8.dp,
                    vertical = 10.dp
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = selected,
                onClick = onClick,
                colors =
                    RadioButtonDefaults.colors(
                        selectedColor =
                            MaterialTheme.colorScheme.primary,
                        unselectedColor = secondaryText
                    )
            )

            Spacer(Modifier.width(8.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = primaryText,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = description,
                    color = secondaryText,
                    fontSize = 12.sp,
                    lineHeight = 15.sp
                )
            }
        }
    }
}

@Composable
private fun SessionLineChart(
    records: List<SavedRecordDetail>,
    series: List<SessionChartSeries>,
    unit: String,
    valueScale: (Double) -> Double,
    primaryText: Color,
    secondaryText: Color
) {
    val maximum =
        series
            .flatMap { item ->
                item.values.filterIndexed { index, _ -> item.outOfRange.getOrNull(index) != true }
            }
            .map(valueScale)
            .maxOrNull()
            ?.coerceAtLeast(1.0)
            ?: 1.0
    val startTime = records.first().timestamp
    val endTime = records.last().timestamp
    val timeSpan =
        (endTime - startTime).coerceAtLeast(1L)
    val timeFormat = LocalUvirTimeFormat.current
    val numericFormat = LocalUvirNumericFormat.current
    val irradianceUnit = LocalUvirIrradianceUnit.current
    val dateFormat = LocalUvirDateFormat.current
    val inspectionPoints = series.flatMap { item ->
        val label = item.displayLabelResource?.let { stringResource(it) } ?: item.label
        item.values.mapIndexedNotNull { index, value ->
            if (item.outOfRange.getOrNull(index) == true) return@mapIndexedNotNull null
            val displayValue = valueScale(value)
            records.getOrNull(index)?.let { record ->
                UvirSavedChartPoint(
                    UvirChartCoordinate(
                        if (item.values.size <= 1) 0.5f
                        else (record.timestamp - startTime).toFloat() / timeSpan.toFloat(),
                        1f - (displayValue.coerceAtLeast(0.0) / maximum).toFloat()),
                    label, formatUvirNumber(
                        displayValue,
                        irradianceUnit.displayFractionDigits(3),
                        numericFormat
                    ) + " " + unit,
                    item.color,
                    formatUvirDateTime(
                        record.timestamp,
                        dateFormat,
                        separator = " ",
                        timeFormat = timeFormat
                    )
                )
            }
        }
    }

    Column(
        modifier =
            Modifier.padding(16.dp)
    ) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxWidth()
        ) {
            val timeTickFractions =
                if (startTime == endTime) {
                    listOf(0.5f)
                } else {
                    sessionChartTimeTickFractions(
                        availableWidth =
                            (maxWidth - 20.dp).value,
                        minimumTickSpacing = 74f
                    )
                }

            Column {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(190.dp)
                            .background(
                                color =
                                    primaryText.copy(
                                        alpha = 0.025f
                                    ),
                                shape =
                                    RoundedCornerShape(12.dp)
                            )
                            .padding(10.dp)
                ) {
                    UvirChartYAxis(
                        maximum = maximum,
                        fractionDigits = irradianceUnit.displayFractionDigits(2),
                        secondaryText = secondaryText,
                        modifier = Modifier.fillMaxSize()
                    ) { chartModifier ->
                        UvirSavedChartInspector(
                            points = inspectionPoints, modifier = chartModifier, bars = false
                        ) { inspectionModifier ->
                        Canvas(modifier = inspectionModifier) {
                            repeat(5) { index ->
                                val y =
                                    size.height * index / 4f
                                drawLine(
                                    color =
                                        secondaryText.copy(
                                            alpha = 0.15f
                                        ),
                                    start = Offset(0f, y),
                                    end = Offset(size.width, y),
                                    strokeWidth = 1.dp.toPx()
                                )
                            }

                        timeTickFractions
                            .drop(1)
                            .dropLast(1)
                            .forEach { fraction ->
                                val x =
                                    size.width * fraction
                                drawLine(
                                    color =
                                        secondaryText.copy(
                                            alpha = 0.11f
                                        ),
                                    start = Offset(x, 0f),
                                    end =
                                        Offset(
                                            x,
                                            size.height
                                        ),
                                    strokeWidth =
                                        1.dp.toPx()
                                )
                            }

                            series.forEach { item ->
                                val path = Path()
                                var hasPreviousPoint = false
                                item.values.forEachIndexed {
                                    index,
                                    value ->
                                if (item.outOfRange.getOrNull(index) == true) {
                                    hasPreviousPoint = false
                                    return@forEachIndexed
                                }
                                val displayValue = valueScale(value)
                                val x =
                                    if (
                                        item.values.size <= 1
                                    ) {
                                        size.width / 2f
                                    } else {
                                        size.width *
                                            (
                                                records[index]
                                                    .timestamp -
                                                    startTime
                                                ).toFloat() /
                                            timeSpan.toFloat()
                                    }
                                val y =
                                    size.height -
                                        (
                                            displayValue.coerceAtLeast(
                                                0.0
                                            ) / maximum
                                            ).toFloat() *
                                        size.height

                                if (!hasPreviousPoint) {
                                    path.moveTo(x, y)
                                } else {
                                    path.lineTo(x, y)
                                }
                                hasPreviousPoint = true

                                if (
                                    item.values.size == 1
                                ) {
                                    drawCircle(
                                        color = item.color,
                                        radius = 3.dp.toPx(),
                                        center = Offset(x, y)
                                    )
                                }
                            }

                                drawPath(
                                    path = path,
                                    color = item.color,
                                    style =
                                        Stroke(
                                            width = 2.dp.toPx(),
                                            cap = StrokeCap.Round
                                        )
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
                    fractions = timeTickFractions,
                    dateFormat = dateFormat,
                    timeFormat = timeFormat,
                    secondaryText = secondaryText
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        series.chunked(2).forEach { rowSeries ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.spacedBy(12.dp)
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
                        Spacer(Modifier.width(5.dp))
                        Text(
                            text =
                                item.displayLabelResource
                                    ?.let { resource ->
                                        stringResource(resource)
                                    }
                                    ?: item.label,
                            color = primaryText,
                            fontSize = 11.sp,
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

@Composable
internal fun SessionChartTimeAxis(
    startTimestamp: Long,
    endTimestamp: Long,
    fractions: List<Float>,
    dateFormat: UvirDateFormat,
    timeFormat: UvirTimeFormat,
    secondaryText: Color,
    modifier: Modifier = Modifier
) {
    val showDate = sessionChartSpansMultipleDays(startTimestamp, endTimestamp)
    Layout(
        modifier = modifier.fillMaxWidth(),
        content = {
            fractions.forEach { fraction ->
                val timestamp =
                    sessionChartTimestampAt(
                        startTimestamp = startTimestamp,
                        endTimestamp = endTimestamp,
                        fraction = fraction
                    )
                Text(
                    text =
                        if (showDate) {
                            formatUvirDateOnly(timestamp, dateFormat) + "\n" +
                                formatUvirTimeOnly(timestamp, timeFormat)
                        } else {
                            formatUvirTimeOnly(timestamp, timeFormat)
                        },
                    color = secondaryText,
                    fontSize = 11.sp,
                    lineHeight = 12.sp,
                    textAlign = TextAlign.Center,
                    maxLines = if (showDate) 2 else 1
                )
            }
        }
    ) { measurables, constraints ->
        val placeables =
            measurables.map { measurable ->
                measurable.measure(
                    constraints.copy(
                        minWidth = 0,
                        minHeight = 0
                    )
                )
            }
        val height =
            placeables.maxOfOrNull { it.height }
                ?: 0

        layout(constraints.maxWidth, height) {
            placeables.forEachIndexed {
                    index,
                    placeable ->
                val fraction = fractions[index]
                val centeredX =
                    (
                        constraints.maxWidth * fraction -
                            placeable.width / 2f
                        ).roundToInt()
                val x =
                    centeredX.coerceIn(
                        0,
                        (constraints.maxWidth -
                            placeable.width).coerceAtLeast(0)
                    )
                placeable.placeRelative(x, 0)
            }
        }
    }
}
