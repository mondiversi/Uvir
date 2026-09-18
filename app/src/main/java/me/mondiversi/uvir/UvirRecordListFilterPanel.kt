package me.mondiversi.uvir

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val RecordListFilterSaver = listSaver<UvirRecordListFilters, String>(
    save = { listOf(it.fromInclusive?.toString().orEmpty(), it.untilInclusive?.toString().orEmpty(),
        it.note, it.sensorKey, it.automatic?.toString().orEmpty(), it.recordId, it.sessionId,
        it.externalCommand?.toString().orEmpty()) },
    restore = { UvirRecordListFilters(it[0].toLongOrNull(), it[1].toLongOrNull(), it[2],
        it[3], it[4].takeIf(String::isNotEmpty)?.toBooleanStrictOrNull(), it[5], it[6],
        it.getOrNull(7)?.takeIf(String::isNotEmpty)?.toBooleanStrictOrNull()) }
)

@Composable
internal fun rememberRecordListFilters(): MutableState<UvirRecordListFilters> =
    rememberSaveable(stateSaver = RecordListFilterSaver) { mutableStateOf(UvirRecordListFilters()) }

@Composable
internal fun UvirRecordListFilterButton(
    active: Boolean, enabled: Boolean, onClick: () -> Unit
) {
    val description = stringResource(R.string.list_filters)
    val fieldColors = UvirOutlinedTextFieldColors()
    val tint = if (enabled) fieldColors.focusedIndicatorColor else fieldColors.disabledTextColor
    IconButton(onClick, enabled = enabled, modifier = Modifier.size(UvirTitleActionButtonSize)
        .testTag("list-filter-toggle").semantics { contentDescription = description }) {
        Canvas(Modifier.size(UvirTitleActionIconSize)) {
            val path = Path().apply {
                moveTo(size.width * 0.12f, size.height * 0.20f)
                lineTo(size.width * 0.88f, size.height * 0.20f)
                lineTo(size.width * 0.60f, size.height * 0.53f)
                lineTo(size.width * 0.60f, size.height * 0.78f)
                lineTo(size.width * 0.40f, size.height * 0.88f)
                lineTo(size.width * 0.40f, size.height * 0.53f)
                close()
            }
            drawPath(path, tint, style = Stroke(UvirTitleActionIconStrokeWidth.toPx(),
                cap = StrokeCap.Round, join = StrokeJoin.Round))
            if (active) drawCircle(tint, 2.dp.toPx(), Offset(size.width * 0.88f, size.height * 0.84f))
        }
    }
}

/** Always derives choices from the unfiltered list, avoiding self-locking filters. */
@Composable
internal fun UvirRecordListFilterPanel(
    filters: UvirRecordListFilters,
    sensorNames: Map<String, String>,
    modes: Set<Boolean>,
    onChange: (UvirRecordListFilters) -> Unit,
    externalModeAvailable: Boolean = false,
    modifier: Modifier = Modifier,
    backgroundColor: Color = MaterialTheme.colorScheme.background,
    recordIds: List<Long> = emptyList(),
    sessionIds: List<Long> = emptyList(),
    notes: List<String> = emptyList(),
    dates: List<Long> = emptyList()
) {
    val fieldColors = UvirOutlinedTextFieldColors()
    val all = stringResource(R.string.list_filter_all)
    val allModes = stringResource(R.string.list_filter_all_modes)
    val allNotes = stringResource(R.string.list_filter_all_notes)
    val allDates = stringResource(R.string.list_filter_all_dates)
    val dateFormat = LocalUvirDateFormat.current
    val formattedDates = dates.map { day ->
        day.toString() to formatUvirDateOnly(day, dateFormat)
    }
    val scroll = rememberScrollState()
    val maximumHeight = (LocalConfiguration.current.screenHeightDp.dp * 0.42f).coerceAtMost(300.dp)
    Surface(color = backgroundColor, contentColor = fieldColors.unfocusedTextColor,
        modifier = modifier.fillMaxWidth().testTag("filter-panel")) {
        Column(Modifier.heightIn(max = maximumHeight)
            .scrollbarOverlay(scroll, fieldColors.unfocusedLabelColor.copy(alpha = 0.45f))
            .verticalScroll(scroll)
            .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChoice(stringResource(R.string.list_filter_id),
                    filters.recordId.ifEmpty { all },
                    listOf("" to all) + recordIds.map { it.toString() to it.toString() },
                    Modifier.weight(1f).testTag("filter-id")) { onChange(filters.copy(recordId = it)) }
                FilterChoice(stringResource(R.string.share_session_id_label),
                    filters.sessionId.ifEmpty { all },
                    listOf("" to all) + sessionIds.map { it.toString() to it.toString() },
                    Modifier.weight(1f).testTag("filter-session-id")) { onChange(filters.copy(sessionId = it)) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChoice(stringResource(R.string.list_filter_from),
                    filters.fromInclusive?.let {
                        formatUvirDateOnly(it, dateFormat)
                    } ?: allDates,
                    listOf("" to allDates) + formattedDates,
                    Modifier.weight(1f).testTag("filter-from")) { selected ->
                    onChange(if (selected.isEmpty()) filters.copy(fromInclusive = null)
                        else filters.withFromDay(selected.toLong()))
                }
                FilterChoice(stringResource(R.string.list_filter_until),
                    filters.untilInclusive?.let {
                        formatUvirDateOnly(it, dateFormat)
                    } ?: allDates,
                    listOf("" to allDates) + formattedDates,
                    Modifier.weight(1f).testTag("filter-until")) { selected ->
                    onChange(if (selected.isEmpty()) filters.copy(untilInclusive = null)
                        else filters.withUntilDay(selected.toLong()))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val modeNames = buildList {
                    if (false in modes) add("manual" to stringResource(R.string.share_manual))
                    if (true in modes) add("automatic" to stringResource(R.string.share_automatic))
                    if (externalModeAvailable) {
                        add("external" to stringResource(R.string.external_measurement))
                    }
                }
                val selectedMode = when {
                    filters.externalCommand == true -> "external"
                    filters.automatic == true -> "automatic"
                    filters.automatic == false -> "manual"
                    else -> ""
                }
                FilterChoice(stringResource(R.string.acquisition_mode_label),
                    modeNames.firstOrNull { it.first == selectedMode }?.second ?: allModes,
                    listOf("" to allModes) + modeNames,
                    Modifier.weight(1f).testTag("filter-mode")) {
                    onChange(
                        when (it) {
                            "manual" -> filters.copy(automatic = false, externalCommand = false)
                            "automatic" -> filters.copy(automatic = true, externalCommand = false)
                            "external" -> filters.copy(automatic = null, externalCommand = true)
                            else -> filters.copy(automatic = null, externalCommand = null)
                        }
                    )
                }
                FilterChoice(stringResource(R.string.sensor_selector_label),
                    sensorNames[filters.sensorKey] ?: all,
                    listOf("" to all) + sensorNames.toList(),
                    Modifier.weight(1f).testTag("filter-sensor")) { onChange(filters.copy(sensorKey = it)) }
            }
            FilterChoice(stringResource(R.string.share_note_label),
                filters.note.ifEmpty { allNotes },
                listOf("" to allNotes) + notes.map { it to it },
                Modifier.fillMaxWidth().testTag("filter-note")) { onChange(filters.copy(note = it)) }
            TextButton(onClick = { onChange(UvirRecordListFilters()) },
                enabled = filters.isActive,
                colors = ButtonDefaults.textButtonColors(contentColor = fieldColors.focusedIndicatorColor,
                    disabledContentColor = fieldColors.disabledTextColor),
                modifier = Modifier.align(Alignment.End).testTag("filter-reset")) {
                Text(stringResource(R.string.list_filter_reset))
            }
        }
    }
    HorizontalDivider(color = fieldColors.unfocusedIndicatorColor.copy(alpha = 0.12f))
}

@Composable
private fun FilterChoice(
    label: String, selected: String, choices: List<Pair<String, String>>,
    modifier: Modifier, onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val fieldColors = UvirOutlinedTextFieldColors()
    val nightMode = androidx.compose.foundation.isSystemInDarkTheme()
    val menuScrollState = rememberScrollState()
    // Expose the visible label and selected value as one accessible field.
    Box(
        modifier.semantics(mergeDescendants = true) {
            contentDescription = "$label: $selected"
        }
    ) {
        Surface(onClick = { expanded = true }, shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, fieldColors.unfocusedIndicatorColor),
            color = Color.Transparent, modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(label, fontSize = 11.sp, color = fieldColors.unfocusedLabelColor)
                    Text(selected, fontSize = 13.sp, color = fieldColors.unfocusedTextColor,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                val arrowTint = fieldColors.unfocusedTextColor
                Canvas(Modifier.size(16.dp)) {
                    val stroke = UvirTitleActionIconStrokeWidth.toPx()
                    drawLine(arrowTint, Offset(size.width * 0.22f, size.height * 0.38f),
                        Offset(size.width * 0.50f, size.height * 0.65f), stroke, StrokeCap.Round)
                    drawLine(arrowTint, Offset(size.width * 0.50f, size.height * 0.65f),
                        Offset(size.width * 0.78f, size.height * 0.38f), stroke, StrokeCap.Round)
                }
            }
        }
        DropdownMenu(expanded, { expanded = false },
            scrollState = menuScrollState,
            modifier = Modifier
                .testTag("filter-choice-menu")
                .scrollbarOverlay(
                    menuScrollState,
                    fieldColors.unfocusedLabelColor.copy(alpha = 0.45f)
                ),
            containerColor =
                if (nightMode) UvirDropdownNightContainerColor
                else UvirDropdownDayContainerColor,
            tonalElevation = UvirDropdownTonalElevation) {
            choices.forEach { (key, title) ->
                DropdownMenuItem(text = { Text(title, color = fieldColors.unfocusedTextColor) },
                    onClick = { expanded = false; onSelect(key) },
                    modifier = Modifier.testTag("filter-choice-option-" + key.ifEmpty { "all" }))
            }
        }
    }
}
