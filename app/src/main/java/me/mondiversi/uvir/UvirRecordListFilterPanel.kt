package me.mondiversi.uvir

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.DateFormat
import java.util.Calendar
import java.util.Date

private val RecordListFilterSaver = listSaver<UvirRecordListFilters, String>(
    save = { listOf(it.fromInclusive?.toString().orEmpty(), it.untilInclusive?.toString().orEmpty(),
        it.note, it.sensorKey, it.automatic?.toString().orEmpty(), it.recordId, it.sessionId) },
    restore = { UvirRecordListFilters(it[0].toLongOrNull(), it[1].toLongOrNull(), it[2],
        it[3], it[4].takeIf(String::isNotEmpty)?.toBooleanStrictOrNull(), it[5], it[6]) }
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
    modifier: Modifier = Modifier,
    backgroundColor: Color = MaterialTheme.colorScheme.background
) {
    val fieldColors = UvirOutlinedTextFieldColors()
    val all = stringResource(R.string.list_filter_all)
    val allModes = stringResource(R.string.list_filter_all_modes)
    val context = LocalContext.current
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
                FilterDateButton(stringResource(R.string.list_filter_from), filters.fromInclusive,
                    Modifier.weight(1f).testTag("filter-from")) {
                    pickFilterDateTime(context, filters.fromInclusive, endOfRange = false) {
                        onChange(filters.withFrom(it))
                    }
                }
                FilterDateButton(stringResource(R.string.list_filter_until), filters.untilInclusive,
                    Modifier.weight(1f).testTag("filter-until")) {
                    pickFilterDateTime(context, filters.untilInclusive, endOfRange = true) {
                        onChange(filters.withUntil(it))
                    }
                }
            }
            OutlinedTextField(value = filters.note, onValueChange = { onChange(filters.copy(note = it)) },
                label = { Text(stringResource(R.string.share_note_label)) }, singleLine = true,
                colors = UvirOutlinedTextFieldColors(), modifier = Modifier.fillMaxWidth().testTag("filter-note"))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChoice(stringResource(R.string.sensor_selector_label),
                    sensorNames[filters.sensorKey] ?: all,
                    listOf("" to all) + sensorNames.toList(),
                    Modifier.weight(1f).testTag("filter-sensor")) { onChange(filters.copy(sensorKey = it)) }
                val modeNames = modes.sorted().map { mode ->
                    mode.toString() to stringResource(if (mode) R.string.share_automatic else R.string.share_manual)
                }
                FilterChoice(stringResource(R.string.acquisition_mode_label),
                    modeNames.firstOrNull { it.first == filters.automatic?.toString() }?.second ?: allModes,
                    listOf("" to allModes) + modeNames,
                    Modifier.weight(1f).testTag("filter-mode")) {
                    onChange(filters.copy(automatic = it.toBooleanStrictOrNull()))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterIdField(stringResource(R.string.list_filter_id), filters.recordId,
                    Modifier.weight(1f).testTag("filter-id")) { onChange(filters.copy(recordId = it)) }
                FilterIdField(stringResource(R.string.share_session_id_label), filters.sessionId,
                    Modifier.weight(1f).testTag("filter-session-id")) { onChange(filters.copy(sessionId = it)) }
            }
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
private fun FilterIdField(label: String, value: String, modifier: Modifier, onChange: (String) -> Unit) {
    OutlinedTextField(value, { text ->
        onChange(normalizeRecordFilterId(text))
    }, modifier = modifier, label = { Text(label) }, singleLine = true,
        colors = UvirOutlinedTextFieldColors(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
}

@Composable
private fun FilterDateButton(label: String, value: Long?, modifier: Modifier, onClick: () -> Unit) {
    val fieldColors = UvirOutlinedTextFieldColors()
    OutlinedButton(onClick, modifier = modifier, shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = fieldColors.unfocusedTextColor),
        border = androidx.compose.foundation.BorderStroke(1.dp, fieldColors.unfocusedIndicatorColor),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)) {
        Column(Modifier.fillMaxWidth()) {
            Text(label, fontSize = 11.sp, color = fieldColors.unfocusedLabelColor)
            Text(value?.let {
                DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(it))
            } ?: "—", fontSize = 12.sp, color = fieldColors.unfocusedTextColor, maxLines = 2)
        }
    }
}

@Composable
private fun FilterChoice(
    label: String, selected: String, choices: List<Pair<String, String>>,
    modifier: Modifier, onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val fieldColors = UvirOutlinedTextFieldColors()
    val nightMode = androidx.compose.foundation.isSystemInDarkTheme()
    Box(modifier) {
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
            modifier = Modifier.testTag("filter-choice-menu"),
            containerColor = if (nightMode) Color(0xFF27323B)
                else MenuDefaults.containerColor,
            tonalElevation = if (nightMode) 0.dp
                else MenuDefaults.TonalElevation) {
            choices.forEach { (key, title) ->
                DropdownMenuItem(text = { Text(title, color = fieldColors.unfocusedTextColor) },
                    onClick = { expanded = false; onSelect(key) })
            }
        }
    }
}

private fun pickFilterDateTime(
    context: Context, current: Long?, endOfRange: Boolean, onPicked: (Long) -> Unit
) {
    val calendar = Calendar.getInstance().apply {
        if (current != null) timeInMillis = current
        else { set(Calendar.HOUR_OF_DAY, if (endOfRange) 23 else 0); set(Calendar.MINUTE, if (endOfRange) 59 else 0) }
    }
    DatePickerDialog(context, { _, year, month, day ->
        calendar.set(year, month, day)
        TimePickerDialog(context, { _, hour, minute ->
            calendar.set(Calendar.HOUR_OF_DAY, hour)
            calendar.set(Calendar.MINUTE, minute)
            calendar.set(Calendar.SECOND, if (endOfRange) 59 else 0)
            calendar.set(Calendar.MILLISECOND, if (endOfRange) 999 else 0)
            onPicked(calendar.timeInMillis)
        }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE),
            android.text.format.DateFormat.is24HourFormat(context)).show()
    }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH),
        calendar.get(Calendar.DAY_OF_MONTH)).show()
}
