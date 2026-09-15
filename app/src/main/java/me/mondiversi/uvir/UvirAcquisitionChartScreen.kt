package me.mondiversi.uvir

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun AcquisitionChartScreen(
    record: SavedRecordDetail,
    backgroundColor: Color,
    cardColor: Color,
    primaryText: Color,
    secondaryText: Color,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var selectedGroup by rememberSaveable {
        mutableStateOf(AcquisitionChartGroup.IRRADIANCE)
    }
    var showShareDialog by rememberSaveable {
        mutableStateOf(false)
    }
    val bars = remember(record, selectedGroup) {
        acquisitionChartBars(
            sample = record.sample,
            group = selectedGroup
        )
    }
    val shareDescription =
        stringResource(R.string.acquisition_chart_share)
    BackHandler(onBack = onBack)

    if (showShareDialog) {
        AcquisitionChartShareDialog(
            record = record,
            selectedGroup = selectedGroup,
            cardColor = cardColor,
            primaryText = primaryText,
            secondaryText = secondaryText,
            onDismiss = {
                showShareDialog = false
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
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    UvirBackButton(onClick = onBack)

                    Text(
                        text =
                            stringResource(
                                R.string.acquisition_chart_title
                            ),
                        modifier = Modifier.weight(1f),
                        color = primaryText,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )

                    IconButton(
                        onClick = {
                            showShareDialog = true
                        },
                        modifier =
                            Modifier
                                .size(40.dp)
                                .semantics {
                                    contentDescription = shareDescription
                                }
                    ) {
                        UvirTitleActionIcon(
                            type = MenuIconType.SHARE,
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
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
                    .verticalScroll(rememberScrollState())
                    .padding(
                        start = 20.dp,
                        end = 20.dp,
                        bottom = 20.dp
                    ),
            verticalArrangement = Arrangement.spacedBy(UvirIslandSpacing)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors =
                    CardDefaults.cardColors(
                        containerColor = cardColor
                    )
            ) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(UvirIslandContentPadding),
                    verticalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val acquisitionTypeText =
                            stringResource(
                                if (record.automatic) {
                                    R.string.automatic_measurement
                                } else {
                                    R.string.manual_measurement
                                }
                            )
                        Text(
                            text =
                                stringResource(
                                    R.string.share_measurement_id_label
                                ),
                            color = secondaryText,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )

                        Spacer(Modifier.width(6.dp))

                        AcquisitionIdBadge(
                            id = record.id,
                            primaryText = primaryText,
                            large = true
                        )

                        Spacer(Modifier.width(10.dp))

                        Text(
                            text = acquisitionTypeText,
                            modifier = Modifier.weight(1f),
                            color = primaryText,
                            fontSize = 13.sp,
                            textAlign = TextAlign.End
                        )
                    }

                    Spacer(Modifier.height(7.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text =
                                stringResource(
                                    R.string.acquisition_chart_note
                                ),
                            color = secondaryText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.width(7.dp))
                        Text(
                            text =
                                acquisitionDisplayNote(
                                    note = record.note,
                                    automatic = record.automatic,
                                    sessionSequence =
                                        record.sessionSequence,
                                    emptyNote =
                                        stringResource(R.string.no_note)
                                ),
                            modifier = Modifier.weight(1f),
                            color = primaryText,
                            fontSize = 12.sp,
                            lineHeight = 15.sp,
                            maxLines = 2
                        )
                    }

                    Spacer(Modifier.height(9.dp))

                    HorizontalDivider(
                        color = secondaryText.copy(alpha = 0.14f)
                    )

                    Spacer(Modifier.height(9.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        UvirMenuIcon(
                            type = MenuIconType.MEASUREMENT_DATE,
                            modifier = Modifier.size(17.dp),
                            tint = secondaryText
                        )
                        Spacer(Modifier.width(7.dp))
                        Text(
                            text =
                                stringResource(
                                    R.string.acquisition_chart_date,
                                    formatDateTime(record.timestamp)
                                ),
                            color = secondaryText,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 1.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(AcquisitionChartGroup.entries) { group ->
                    FilterChip(
                        selected = selectedGroup == group,
                        onClick = {
                            selectedGroup = group
                        },
                        label = {
                            Text(
                                stringResource(group.titleResource)
                            )
                        },
                        colors =
                            FilterChipDefaults.filterChipColors(
                                selectedContainerColor =
                                    MaterialTheme.colorScheme.primary,
                                selectedLabelColor =
                                    MaterialTheme.colorScheme.onPrimary,
                                labelColor = secondaryText
                            )
                    )
                }
            }

            AcquisitionVerticalBarChart(
                bars = bars,
                group = selectedGroup,
                cardColor = cardColor,
                primaryText = primaryText,
                secondaryText = secondaryText
            )
        }
    }
}

@Composable
internal fun AcquisitionVerticalBarChart(
    bars: List<AcquisitionChartBar>,
    group: AcquisitionChartGroup,
    cardColor: Color,
    primaryText: Color,
    secondaryText: Color
) {
    val expandedSections =
        remember {
            mutableStateMapOf<AcquisitionChartSection, Boolean>()
        }
    val sections =
        AcquisitionChartSection.entries.mapNotNull { section ->
            bars.filter { it.section == section }
                .takeIf { it.isNotEmpty() }
                ?.let { section to it }
        }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(UvirIslandSpacing)
    ) {
        sections.forEach { (section, sectionBars) ->
            val expanded = expandedSections[section] ?: true

            UvirCollapsibleChartCard(
                title = stringResource(section.titleResource),
                subtitle = stringResource(group.unitResource),
                iconGroup = section.spectrumIconGroup(),
                expanded = expanded,
                onToggle = {
                    expandedSections[section] = !expanded
                },
                cardColor = cardColor,
                primaryText = primaryText,
                secondaryText = secondaryText
            ) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(UvirIslandContentPadding)
                ) {
                    AcquisitionBarSection(
                        bars = sectionBars,
                        unit = group.exportUnit,
                        primaryText = primaryText,
                        secondaryText = secondaryText
                    )
                }
            }
        }
    }
}

@Composable
private fun AcquisitionBarSection(
    bars: List<AcquisitionChartBar>,
    unit: String,
    primaryText: Color,
    secondaryText: Color
) {
    val maximum =
        bars
            .maxOfOrNull { it.value.coerceAtLeast(0.0) }
            ?.coerceAtLeast(1.0)
            ?: 1.0
    val numericFormat = LocalUvirNumericFormat.current
    val inspectionPoints = bars.mapIndexed { index, bar ->
        UvirSavedChartPoint(
            UvirChartCoordinate(
                (index + 0.5f) / bars.size.coerceAtLeast(1),
                1f - (bar.value.coerceAtLeast(0.0) / maximum).toFloat().coerceIn(0f, 1f)),
            bar.displayLabelResource?.let { stringResource(it) } ?: bar.exportLabel,
            formatUvirNumber(bar.value, 3, numericFormat) + " " + unit,
            bar.color)
    }

    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(190.dp)
                .background(
                    color = primaryText.copy(alpha = 0.025f),
                    shape = RoundedCornerShape(12.dp)
                )
                .padding(horizontal = 10.dp, vertical = 10.dp)
    ) {
        UvirChartYAxis(
            maximum = maximum,
            fractionDigits = 2,
            secondaryText = secondaryText,
            modifier = Modifier.fillMaxSize()
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

                val slotWidth = size.width / bars.size.coerceAtLeast(1)
                val barWidth =
                    (slotWidth * 0.54f)
                        .coerceAtMost(42.dp.toPx())

                bars.forEachIndexed { index, bar ->
                    val normalized =
                        (bar.value.coerceAtLeast(0.0) / maximum)
                            .toFloat()
                            .coerceIn(0f, 1f)
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
                color = secondaryText,
                fontSize = 9.sp,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
    }

    Spacer(Modifier.height(11.dp))

    bars.chunked(2).forEach { rowBars ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            rowBars.forEach { bar ->
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier =
                            Modifier
                                .size(10.dp)
                                .background(
                                    bar.color,
                                    RoundedCornerShape(50)
                                )
                    )
                    Spacer(Modifier.width(6.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text =
                                bar.displayLabelResource
                                    ?.let { stringResource(it) }
                                    ?: bar.exportLabel,
                            color = primaryText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 2,
                            lineHeight = 13.sp
                        )
                        Text(
                            text = formatUvirNumber(bar.value, 3, numericFormat),
                            color = primaryText,
                            fontSize = 10.sp,
                            maxLines = 1
                        )
                    }
                }
            }
            repeat(2 - rowBars.size) {
                Spacer(Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}
