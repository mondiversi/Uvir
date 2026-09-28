package me.mondiversi.uvir

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal fun filterSessionRecordsBySequence(
    records: List<SavedRecordDetail>,
    cycleSize: Int,
    cyclePosition: Int
): List<SavedRecordDetail> {
    if (
        cycleSize < 2 ||
        records.size % cycleSize != 0 ||
        cyclePosition !in 1..cycleSize
    ) {
        return records
    }

    return records.filterIndexed { index, record ->
        val sequence = record.sessionSequence?.takeIf { it > 0 } ?: (index + 1)
        ((sequence - 1) % cycleSize) + 1 == cyclePosition
    }
}

internal fun sessionSequenceCycleSizes(recordCount: Int): List<Int> =
    if (recordCount <= 0) {
        listOf(1)
    } else {
        (1..recordCount).filter { recordCount % it == 0 }
    }

internal val UvirSessionCyclePositionSelectorWidth = 93.dp

@Composable
internal fun UvirSessionVariantsIcon(
    tint: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier.graphicsLayer(alpha = tint.alpha)) {
        val tint = tint.copy(alpha = 1f)
        val stroke = UvirTitleActionIconStrokeWidth.toPx() * 0.82f
        listOf(
            Offset(size.width * 0.12f, size.height * 0.14f),
            Offset(size.width * 0.20f, size.height * 0.24f),
            Offset(size.width * 0.28f, size.height * 0.34f)
        ).forEach { topLeft ->
            drawRoundRect(
                color = tint,
                topLeft = topLeft,
                size = Size(size.width * 0.60f, size.height * 0.48f),
                cornerRadius = CornerRadius(3.dp.toPx()),
                style = Stroke(width = stroke)
            )
        }
    }
}

@Composable
internal fun UvirSessionCycleFilterButton(
    active: Boolean,
    enabled: Boolean,
    compact: Boolean = false,
    onClick: () -> Unit
) {
    val description = stringResource(R.string.session_sequence_filter_title)
    val fieldColors = UvirOutlinedTextFieldColors()
    val tint = if (compact) {
        if (enabled) androidx.compose.material3.MaterialTheme.colorScheme.primary
        else androidx.compose.material3.MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
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
            modifier = Modifier.fillMaxSize().testTag("session-cycle-filter-toggle"),
            enabled = enabled,
            selected = active,
            visualScale = if (compact) UvirTitleActionVisualScale else 1f,
            pressedColor = tint,
            pressedVisualSize = UvirActionIconBadgeSize
        ) {
            UvirSessionVariantsIcon(
                tint = tint,
                modifier = Modifier.size(UvirTitleActionIconSize)
            )
        }
        if (active) UvirTitleActionActiveDot(
            color = tint,
            modifier = Modifier.align(Alignment.BottomEnd)
                .padding(end = UvirTitleActionActiveDotInset, bottom = UvirTitleActionActiveDotInset)
                .testTag("session-cycle-active-dot")
        )
    }
}

@Composable
internal fun UvirSessionCyclePositionSelector(
    cycleSize: Int,
    position: Int,
    cardColor: Color,
    primaryText: Color,
    secondaryText: Color,
    onPositionChange: (Int) -> Unit
) {
    val enabled = cycleSize > 1
    val effectivePosition = position.coerceIn(1, cycleSize.coerceAtLeast(1))
    val totalPositions = cycleSize.coerceAtLeast(1)
    val positionBaseLabel = stringResource(R.string.session_sequence_filter_position)
    val positionLabel =
        if (enabled) "$positionBaseLabel ($totalPositions)" else positionBaseLabel
    val fieldColors = UvirOutlinedTextFieldColors()
    val tint = if (enabled) fieldColors.unfocusedTextColor else fieldColors.disabledTextColor
    val borderColor =
        if (enabled) fieldColors.unfocusedIndicatorColor else fieldColors.disabledIndicatorColor
    val labelColor =
        if (enabled) fieldColors.unfocusedLabelColor else fieldColors.disabledLabelColor
    var expanded by remember { mutableStateOf(false) }
    val nightMode = isSystemInDarkTheme()

    Box(
        modifier = Modifier.fillMaxWidth()
    ) {
        Surface(
            onClick = { expanded = true },
            enabled = enabled,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = 5.dp)
                    .height(41.dp)
                    .semantics { contentDescription = positionLabel },
            shape = RoundedCornerShape(14.dp),
            color = cardColor,
            border = BorderStroke(1.dp, borderColor)
        ) {
            Row(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text =
                        if (enabled) uvirVariantLabel(effectivePosition)
                        else "—",
                    modifier = Modifier.weight(1f),
                    color = tint,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
                Canvas(
                    Modifier
                        .size(16.dp)
                        .graphicsLayer(alpha = tint.alpha)
                ) {
                    val tint = tint.copy(alpha = 1f)
                    val stroke = UvirTitleActionIconStrokeWidth.toPx()
                    drawLine(
                        tint,
                        Offset(size.width * 0.22f, size.height * 0.38f),
                        Offset(size.width * 0.50f, size.height * 0.65f),
                        stroke,
                        StrokeCap.Round
                    )
                    drawLine(
                        tint,
                        Offset(size.width * 0.50f, size.height * 0.65f),
                        Offset(size.width * 0.78f, size.height * 0.38f),
                        stroke,
                        StrokeCap.Round
                    )
                }
            }
        }

        Text(
            text = positionLabel,
            modifier =
                Modifier
                    .padding(start = 9.dp)
                    .background(cardColor)
                    .padding(horizontal = 3.dp),
            color = labelColor,
            fontSize = 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.width(UvirSessionCyclePositionSelectorWidth),
            containerColor =
                if (nightMode) UvirDropdownNightContainerColor
                else UvirDropdownDayContainerColor,
            tonalElevation = UvirDropdownTonalElevation
        ) {
            (1..totalPositions).forEach { choice ->
                DropdownMenuItem(
                    modifier = Modifier,
                    text = {
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(end = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = uvirVariantLabel(choice),
                                color = primaryText,
                                textAlign = TextAlign.Center
                            )
                        }
                    },
                    contentPadding = PaddingValues(horizontal = 10.dp),
                    onClick = {
                        expanded = false
                        onPositionChange(choice)
                    }
                )
            }
        }
    }
}

@Composable
internal fun UvirSessionSequenceFilterPanel(
    records: List<SavedRecordDetail>,
    cycleSize: Int,
    backgroundColor: Color,
    primaryText: Color,
    secondaryText: Color,
    onSave: (cycleSize: Int) -> Unit,
    onDismiss: () -> Unit
) {
    val allowedCycleSizes = remember(records.size) {
        sessionSequenceCycleSizes(records.size)
    }
    val initialCycleSize = cycleSize.takeIf { it in allowedCycleSizes } ?: 1
    var draftCycleSize by remember(records.size, initialCycleSize) {
        mutableStateOf(initialCycleSize)
    }
    val visibleRecords = remember(records, draftCycleSize) {
        filterSessionRecordsBySequence(
            records = records,
            cycleSize = draftCycleSize,
            cyclePosition = 1
        )
    }
    val fieldColors = UvirOutlinedTextFieldColors()

    UvirAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.session_sequence_filter_title),
                color = primaryText
            )
        },
        text = {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
            ) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.session_sequence_filter_description),
                        color = secondaryText,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                    Text(
                        text = stringResource(R.string.session_sequence_filter_cycle_size),
                        color = primaryText,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        lineHeight = 17.sp
                    )
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        maxItemsInEachRow = 5
                    ) {
                        allowedCycleSizes.forEach { choice ->
                            val selected = draftCycleSize == choice
                            val accent = fieldColors.focusedIndicatorColor
                            Surface(
                                onClick = { draftCycleSize = choice },
                                shape = RoundedCornerShape(10.dp),
                                color =
                                    if (selected) accent.copy(alpha = 0.14f)
                                    else Color.Transparent,
                                border =
                                    BorderStroke(
                                        1.dp,
                                        if (selected) accent
                                        else fieldColors.unfocusedIndicatorColor
                                    )
                            ) {
                                Box(
                                    modifier =
                                        Modifier
                                            .sizeIn(minWidth = 44.dp, minHeight = 40.dp)
                                            .padding(horizontal = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = if (choice == 1) "—" else choice.toString(),
                                        color =
                                            if (selected) accent
                                            else fieldColors.unfocusedTextColor,
                                        fontSize = 13.sp,
                                        fontWeight =
                                            if (selected) FontWeight.SemiBold
                                            else FontWeight.Normal
                                    )
                                }
                            }
                        }
                    }
                    Text(
                        text =
                            if (draftCycleSize > 1) {
                                stringResource(
                                    R.string.session_sequence_filter_preview,
                                    visibleRecords.size
                                )
                            } else {
                                stringResource(R.string.session_sequence_filter_preview_total)
                            },
                        modifier = Modifier.fillMaxWidth(),
                        color = secondaryText,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Start,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = stringResource(R.string.session_sequence_filter_export_note),
                        modifier = Modifier.fillMaxWidth(),
                        color = secondaryText.copy(alpha = 0.78f),
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(contentColor = UvirDestructiveActionColor)
            ) {
                Text(stringResource(R.string.cancel))
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(draftCycleSize) },
                colors = ButtonDefaults.textButtonColors(contentColor = fieldColors.focusedIndicatorColor)
            ) {
                Text(stringResource(R.string.save))
            }
        },
        containerColor = backgroundColor
    )
}
