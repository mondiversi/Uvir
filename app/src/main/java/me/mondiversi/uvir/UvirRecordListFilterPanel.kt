package me.mondiversi.uvir

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val RecordListFilterSaver = listSaver<UvirRecordListFilters, String>(
    save = { listOf(it.fromInclusive?.toString().orEmpty(), it.untilInclusive?.toString().orEmpty(),
        it.note, it.sensorKey, it.automatic?.toString().orEmpty(), it.recordId, it.sessionId,
        it.externalCommand?.toString().orEmpty(), it.variantCount?.toString().orEmpty()) },
    restore = { UvirRecordListFilters(it[0].toLongOrNull(), it[1].toLongOrNull(), it[2],
        it[3], it[4].takeIf(String::isNotEmpty)?.toBooleanStrictOrNull(), it[5], it[6],
        it.getOrNull(7)?.takeIf(String::isNotEmpty)?.toBooleanStrictOrNull(),
        it.getOrNull(8)?.toIntOrNull()) }
)

@Composable
internal fun rememberRecordListFilters(): MutableState<UvirRecordListFilters> =
    rememberSaveable(stateSaver = RecordListFilterSaver) { mutableStateOf(UvirRecordListFilters()) }

@Composable
internal fun UvirRecordListFilterButton(
    active: Boolean, enabled: Boolean, onClick: () -> Unit
) = UvirRecordListFilterButton(active, enabled, compact = false, onClick = onClick)

@Composable
internal fun UvirRecordListFilterButton(
    active: Boolean, enabled: Boolean, compact: Boolean, onClick: () -> Unit
) {
    val description = stringResource(R.string.list_filters)
    val fieldColors = UvirOutlinedTextFieldColors()
    val tint = if (compact) {
        if (enabled) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    } else {
        if (enabled) fieldColors.focusedIndicatorColor else fieldColors.disabledTextColor
    }
    Box(
        modifier = Modifier.size(UvirTitleActionButtonSize),
        contentAlignment = Alignment.Center
    ) {
        UvirAccessibleIconButton(
            contentDescription = description,
            onClick = onClick,
            modifier = Modifier.fillMaxSize().testTag("list-filter-toggle"),
            enabled = enabled,
            selected = active,
            visualScale = if (compact) UvirTitleActionVisualScale else 1f,
            pressedColor = tint,
            pressedVisualSize = UvirActionIconBadgeSize
        ) {
            val glyphTint = tint
            Canvas(
                Modifier.size(UvirTitleActionIconSize).graphicsLayer(alpha = glyphTint.alpha)
            ) {
                val tint = glyphTint.copy(alpha = 1f)
                val strokeWidth = UvirTitleActionIconStrokeWidth.toPx()
                listOf(0.28f to 0.86f, 0.50f to 0.66f, 0.72f to 0.46f).forEach { (y, endX) ->
                    drawLine(
                        color = tint,
                        start = Offset(size.width * 0.14f, size.height * y),
                        end = Offset(size.width * endX, size.height * y),
                        strokeWidth = strokeWidth,
                        cap = StrokeCap.Round
                    )
                }
            }
        }
        if (active) UvirTitleActionActiveDot(
            color = tint,
            modifier = Modifier.align(Alignment.BottomEnd)
                .padding(end = UvirTitleActionActiveDotInset, bottom = UvirTitleActionActiveDotInset)
                .testTag("list-filter-active-dot")
        )
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
    dates: List<Long> = emptyList(),
    variantCounts: List<Int> = emptyList(),
    variantFilterEnabled: Boolean = true
) {
    val fieldColors = UvirOutlinedTextFieldColors()
    val all = stringResource(R.string.list_filter_all)
    val allModes = stringResource(R.string.list_filter_all_modes)
    val allNotes = stringResource(R.string.list_filter_all_notes)
    val noVariants = stringResource(R.string.list_filter_no_variants)
    val onlyVariants = stringResource(R.string.list_filter_only_variants)
    val allDates = stringResource(R.string.list_filter_all_dates)
    val dateFormat = LocalUvirDateFormat.current
    val formattedDates = dates.map { day ->
        day.toString() to formatUvirDateOnly(day, dateFormat)
    }
    val scroll = rememberScrollState()
    val maximumHeight = (LocalConfiguration.current.screenHeightDp.dp * 0.62f).coerceAtMost(440.dp)
    Surface(color = backgroundColor, contentColor = fieldColors.unfocusedTextColor,
        modifier = modifier.fillMaxWidth().testTag("filter-panel")) {
        Column(Modifier.heightIn(max = maximumHeight)
            .scrollbarOverlay(scroll, fieldColors.unfocusedLabelColor.copy(alpha = 0.45f))
            .verticalScroll(scroll)
            .padding(horizontal = UvirScreenHorizontalPadding, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChoice(stringResource(R.string.list_filter_id),
                    filters.recordId.ifEmpty { all },
                    listOf("" to all) + recordIds.map { it.toString() to it.toString() },
                    Modifier.weight(1f).testTag("filter-id"), icon = FilterLabelIcon.ID) { onChange(filters.copy(recordId = it)) }
                FilterChoice(stringResource(R.string.share_session_id_label),
                    filters.sessionId.ifEmpty { all },
                    listOf("" to all) + sessionIds.map { it.toString() to it.toString() },
                    Modifier.weight(1f).testTag("filter-session-id"), icon = FilterLabelIcon.SESSION) { onChange(filters.copy(sessionId = it)) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChoice(stringResource(R.string.list_filter_from),
                    filters.fromInclusive?.let {
                        formatUvirDateOnly(it, dateFormat)
                    } ?: allDates,
                    listOf("" to allDates) + formattedDates,
                    Modifier.weight(1f).testTag("filter-from"), icon = FilterLabelIcon.FROM) { selected ->
                    onChange(if (selected.isEmpty()) filters.copy(fromInclusive = null)
                        else filters.withFromDay(selected.toLong()))
                }
                FilterChoice(stringResource(R.string.list_filter_until),
                    filters.untilInclusive?.let {
                        formatUvirDateOnly(it, dateFormat)
                    } ?: allDates,
                    listOf("" to allDates) + formattedDates,
                    Modifier.weight(1f).testTag("filter-until"), icon = FilterLabelIcon.UNTIL) { selected ->
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
                    Modifier.weight(1f).testTag("filter-mode"), icon = FilterLabelIcon.MODE) {
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
                    Modifier.weight(1f).testTag("filter-sensor"), icon = FilterLabelIcon.SENSOR) { onChange(filters.copy(sensorKey = it)) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChoice(stringResource(R.string.share_note_label),
                    filters.note.ifEmpty { allNotes },
                    listOf("" to allNotes) + notes.map { it to it },
                    Modifier.weight(1f).testTag("filter-note"), icon = FilterLabelIcon.NOTE) {
                    onChange(filters.copy(note = it))
                }
                val variantChoices = buildList {
                    add("" to "—")
                    add("0" to noVariants)
                    add("-1" to onlyVariants)
                    variantCounts.distinct().sorted().forEach { count ->
                        add(count.toString() to count.toString())
                    }
                }
                val selectedVariant = when (filters.variantCount) {
                    null -> "—"
                    0 -> noVariants
                    -1 -> onlyVariants
                    else -> filters.variantCount.toString()
                }
                FilterChoice(
                    label = stringResource(R.string.list_filter_variant),
                    selected = selectedVariant,
                    choices = variantChoices,
                    modifier = Modifier.weight(1f).testTag("filter-variant"),
                    icon = FilterLabelIcon.VARIANT,
                    enabled = variantFilterEnabled
                ) { selected ->
                    onChange(filters.copy(variantCount = selected.toIntOrNull()))
                }
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

private enum class FilterLabelIcon { ID, SESSION, FROM, UNTIL, MODE, SENSOR, NOTE, VARIANT }

/** Decorative label icons use the same tint as their label, including disabled fields. */
@Composable
private fun FilterLabelIcon(icon: FilterLabelIcon, tint: Color) {
    val modifier = Modifier.size(14.dp).testTag("filter-label-icon-${icon.name.lowercase(java.util.Locale.ROOT)}")
    when (icon) {
        FilterLabelIcon.ID, FilterLabelIcon.SESSION -> Canvas(modifier.graphicsLayer(alpha = tint.alpha)) {
            val color = tint.copy(alpha = 1f)
            val stroke = UvirDetailMetadataIconStrokeWidth.toPx()
            listOf(0.35f, 0.65f).forEach { x ->
                drawLine(color, Offset(size.width * (x + 0.06f), size.height * 0.15f),
                    Offset(size.width * (x - 0.06f), size.height * 0.85f), stroke, StrokeCap.Round)
            }
            listOf(0.36f, 0.64f).forEach { y ->
                drawLine(color, Offset(size.width * 0.16f, size.height * y),
                    Offset(size.width * 0.84f, size.height * y), stroke, StrokeCap.Round)
            }
        }
        FilterLabelIcon.MODE -> UvirMenuIcon(
            type = MenuIconType.ACQUISITION_PARAMETERS,
            modifier = modifier, tint = tint, uniformStrokeWidth = UvirDetailMetadataIconStrokeWidth
        )
        else -> UvirDetailMetadataIcon(
            kind = when (icon) {
                FilterLabelIcon.FROM, FilterLabelIcon.UNTIL -> UvirDetailMetadataIconKind.DATE
                FilterLabelIcon.SENSOR -> UvirDetailMetadataIconKind.SENSOR
                FilterLabelIcon.NOTE -> UvirDetailMetadataIconKind.NOTE
                else -> UvirDetailMetadataIconKind.VARIANT
            },
            tint = tint, modifier = modifier
        )
    }
}

@Composable
private fun FilterChoice(
    label: String, selected: String, choices: List<Pair<String, String>>,
    modifier: Modifier, icon: FilterLabelIcon, enabled: Boolean = true, onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val fieldColors = UvirOutlinedTextFieldColors()
    val nightMode = androidx.compose.foundation.isSystemInDarkTheme()
    val menuScrollState = rememberScrollState()
    // Expose the visible label and selected value as one accessible field.
    Box(
        modifier.semantics(mergeDescendants = true) {
            contentDescription = "$label: $selected"
            if (!enabled) disabled()
        }
    ) {
        Surface(onClick = { expanded = true }, enabled = enabled, shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                if (enabled) fieldColors.unfocusedIndicatorColor else fieldColors.disabledIndicatorColor
            ),
            color = Color.Transparent, modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    val labelTint = if (enabled) fieldColors.unfocusedLabelColor else fieldColors.disabledTextColor
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        FilterLabelIcon(icon, labelTint)
                        Text(label, modifier = Modifier.weight(1f), fontSize = 11.sp, color = labelTint)
                    }
                    Text(selected, fontSize = 13.sp, color = if (enabled) fieldColors.unfocusedTextColor
                        else fieldColors.disabledTextColor,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                val arrowTint = if (enabled) fieldColors.unfocusedTextColor else fieldColors.disabledTextColor
                Canvas(
                    Modifier
                        .size(16.dp)
                        .graphicsLayer(alpha = arrowTint.alpha)
                ) {
                    val arrowTint = arrowTint.copy(alpha = 1f)
                    val stroke = UvirTitleActionIconStrokeWidth.toPx()
                    drawLine(arrowTint, Offset(size.width * 0.22f, size.height * 0.38f),
                        Offset(size.width * 0.50f, size.height * 0.65f), stroke, StrokeCap.Round)
                    drawLine(arrowTint, Offset(size.width * 0.50f, size.height * 0.65f),
                        Offset(size.width * 0.78f, size.height * 0.38f), stroke, StrokeCap.Round)
                }
            }
        }
        DropdownMenu(expanded && enabled, { expanded = false },
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
                    onClick = {
                        expanded = false
                        onSelect(key)
                    },
                    modifier = Modifier
                        .testTag("filter-choice-option-" + key.ifEmpty { "all" }))
            }
        }
    }
}
