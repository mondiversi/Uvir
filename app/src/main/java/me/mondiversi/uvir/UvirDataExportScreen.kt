package me.mondiversi.uvir

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun MeasurementDataExportScreen(
    backgroundColor: Color,
    cardColor: Color,
    primaryText: Color,
    secondaryText: Color,
    partialSessionWarning: Boolean = false,
    partialSessionWarningMessage: String? = null,
    readableTableFileCount: Int? = null,
    readableTableGroupingAvailable: Boolean = false,
    showReadableTableGroupingScopeNote: Boolean = false,
    combinedChartFileCount: Int? = null,
    separateChartFileCount: Int? = null,
    variantChartGroupingAvailable: Boolean = false,
    groupedVariantChartFileCount: Int? = null,
    showVariantChartGroupingScopeNote: Boolean = false,
    onDismiss: () -> Unit,
    onSelectionConfirmed: (
        MeasurementDetailShareSelection,
        UvirExportDestination
    ) -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val savedExportPreferences =
        remember(context) {
            loadUvirDataExportPreferences(context)
        }
    var csvSelected by rememberSaveable {
        mutableStateOf(false)
    }
    var readableTableSelected by rememberSaveable {
        mutableStateOf(false)
    }
    var chartsSelected by rememberSaveable {
        mutableStateOf(false)
    }
    var chartExportMode by rememberSaveable {
        mutableStateOf(savedExportPreferences.chartExportMode)
    }
    var readableTableGrouping by rememberSaveable {
        mutableStateOf(savedExportPreferences.readableTableGrouping)
    }
    var variantChartGrouping by rememberSaveable {
        mutableStateOf(savedExportPreferences.variantChartGrouping)
    }
    var exportMode by rememberSaveable {
        mutableStateOf(loadUvirExportMode(context))
    }

    val dataFormat =
        when {
            csvSelected && readableTableSelected ->
                MeasurementShareFormat.BOTH

            csvSelected -> MeasurementShareFormat.CSV
            readableTableSelected -> MeasurementShareFormat.READABLE_TABLE
            else -> null
        }
    val canExport = dataFormat != null || chartsSelected
    val selection =
        MeasurementDetailShareSelection(
            dataFormat = dataFormat,
            includeCharts = chartsSelected,
            chartExportMode = chartExportMode,
            variantChartGrouping = variantChartGrouping,
            readableTableGrouping = readableTableGrouping
        )
    val exportRequest =
        rememberUvirExportRequestGate { destination ->
            saveUvirExportMode(context, exportMode)
            saveUvirDataExportPreferences(
                context = context,
                preferences =
                    UvirDataExportPreferences(
                        chartExportMode = chartExportMode,
                        readableTableGrouping = readableTableGrouping,
                        variantChartGrouping = variantChartGrouping
                    )
            )
            onSelectionConfirmed(selection, destination)
        }

    UvirFullScreenPage(
        onDismissRequest = {
            if (!exportRequest.inProgress) onDismiss()
        },
        title = {
            UvirMenuTitle(
                text = stringResource(R.string.data_export_title),
                color = primaryText
            )
        },
        containerColor = backgroundColor,
        contentColor = primaryText,
        scrollState = scrollState,
        scrollbarColor = secondaryText.copy(alpha = 0.46f),
        text = {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = cardColor,
                    contentColor = primaryText
                ) {
                    Column(
                        modifier = Modifier.padding(UvirIslandContentPadding),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                SettingsPageDescription(
                    text = stringResource(R.string.data_export_description),
                    color = secondaryText
                )
                UvirExportFilesHeading(primaryText)
                ShareChartsOptionRow(
                    selected = chartsSelected,
                    primaryText = primaryText,
                    secondaryText = secondaryText,
                    onClick = {
                        chartsSelected = !chartsSelected
                    }
                )

                if (chartsSelected) {
                    ChartExportModeRow(
                        selected = chartExportMode == UvirChartExportMode.COMBINED,
                        label = stringResource(R.string.share_charts_combined_groups),
                        primaryText = primaryText,
                        secondaryText = secondaryText,
                        onClick = {
                            chartExportMode = UvirChartExportMode.COMBINED
                        }
                    )
                    if (
                        chartExportMode == UvirChartExportMode.COMBINED &&
                        variantChartGroupingAvailable
                    ) {
                        ChartExportModeRow(
                            selected =
                                variantChartGrouping ==
                                    UvirVariantChartGrouping.BY_VARIANT,
                            label =
                                stringResource(
                                    R.string.share_variant_charts_by_variant
                                ),
                            primaryText = primaryText,
                            secondaryText = secondaryText,
                            modifier = Modifier.padding(start = 22.dp),
                            onClick = {
                                variantChartGrouping =
                                    UvirVariantChartGrouping.BY_VARIANT
                            }
                        )
                        ChartExportModeRow(
                            selected =
                                variantChartGrouping ==
                                    UvirVariantChartGrouping.BY_GROUP,
                            label =
                                stringResource(
                                    R.string.share_variant_charts_by_group
                                ),
                            primaryText = primaryText,
                            secondaryText = secondaryText,
                            modifier = Modifier.padding(start = 22.dp),
                            onClick = {
                                variantChartGrouping =
                                    UvirVariantChartGrouping.BY_GROUP
                            }
                        )
                        if (showVariantChartGroupingScopeNote) {
                            Text(
                                text =
                                    stringResource(
                                        R.string.share_variant_grouping_complete_sessions_only
                                    ),
                                modifier =
                                    Modifier.padding(
                                        start = UvirExportOptionDetailInset,
                                        top = 1.dp,
                                        end = 10.dp,
                                        bottom = 1.dp
                                    ),
                                color = secondaryText,
                                fontSize = 11.sp,
                                lineHeight = 14.sp
                            )
                        }
                    }
                    ChartExportModeRow(
                        selected = chartExportMode == UvirChartExportMode.SEPARATE,
                        label = stringResource(R.string.share_charts_separate_groups),
                        primaryText = primaryText,
                        secondaryText = secondaryText,
                        onClick = {
                            chartExportMode = UvirChartExportMode.SEPARATE
                        }
                    )

                    val chartFileCount =
                        when (chartExportMode) {
                            UvirChartExportMode.COMBINED ->
                                if (
                                    variantChartGroupingAvailable &&
                                    variantChartGrouping ==
                                        UvirVariantChartGrouping.BY_GROUP
                                ) {
                                    groupedVariantChartFileCount
                                } else {
                                    combinedChartFileCount
                                }
                            UvirChartExportMode.SEPARATE -> separateChartFileCount
                        }
                    if (chartFileCount != null) {
                        UvirExportFileCountText(
                            format = UvirExportFileFormat.PNG,
                            count = chartFileCount,
                            secondaryText = secondaryText,
                            modifier =
                                Modifier.padding(
                                    start = UvirExportOptionDetailInset,
                                    top = 2.dp,
                                    end = 10.dp
                                )
                        )
                    }
                }

                ShareFormatOptionRow(
                    format = MeasurementShareFormat.READABLE_TABLE,
                    selected = readableTableSelected,
                    label =
                        stringResource(
                            R.string.share_as_readable_table
                        ),
                    primaryText = primaryText,
                    secondaryText = secondaryText,
                    onClick = {
                        readableTableSelected =
                            !readableTableSelected
                    }
                )

                if (readableTableSelected && readableTableGroupingAvailable) {
                    ChartExportModeRow(
                        selected =
                            readableTableGrouping ==
                                UvirReadableTableGrouping.BY_ACQUISITION,
                        label =
                            stringResource(
                                R.string.share_readable_table_by_acquisition
                            ),
                        primaryText = primaryText,
                        secondaryText = secondaryText,
                        onClick = {
                            readableTableGrouping =
                                UvirReadableTableGrouping.BY_ACQUISITION
                        }
                    )
                    ChartExportModeRow(
                        selected =
                            readableTableGrouping ==
                                UvirReadableTableGrouping.BY_SPECTRAL_AREA,
                        label =
                            stringResource(
                                R.string.share_readable_table_by_spectral_area
                            ),
                        primaryText = primaryText,
                        secondaryText = secondaryText,
                        onClick = {
                            readableTableGrouping =
                                UvirReadableTableGrouping.BY_SPECTRAL_AREA
                        }
                    )
                    if (showReadableTableGroupingScopeNote) {
                        Text(
                            text =
                                stringResource(
                                    R.string.share_readable_grouping_complete_sessions_only
                                ),
                            modifier =
                                Modifier.padding(
                                    start = UvirExportOptionDetailInset,
                                    top = 1.dp,
                                    end = 10.dp,
                                    bottom = 1.dp
                                ),
                            color = secondaryText,
                            fontSize = 11.sp,
                            lineHeight = 14.sp
                        )
                    }
                }

                if (readableTableSelected && readableTableFileCount != null) {
                    UvirExportFileCountText(
                        format = UvirExportFileFormat.TXT,
                        count = readableTableFileCount,
                        secondaryText = secondaryText,
                        modifier =
                            Modifier.padding(
                                start = UvirExportOptionDetailInset,
                                top = 2.dp,
                                end = 10.dp
                            )
                    )
                }

                ShareFormatOptionRow(
                    format = MeasurementShareFormat.CSV,
                    selected = csvSelected,
                    label = stringResource(R.string.share_as_csv),
                    primaryText = primaryText,
                    secondaryText = secondaryText,
                    onClick = {
                        csvSelected = !csvSelected
                    }
                )
                if (csvSelected) {
                    UvirExportFileCountText(
                        format = UvirExportFileFormat.CSV,
                        count = 1,
                        secondaryText = secondaryText,
                        modifier =
                            Modifier.padding(
                                start = UvirExportOptionDetailInset,
                                top = 2.dp,
                                end = 10.dp
                            )
                    )
                }

                if (
                    partialSessionWarning ||
                    partialSessionWarningMessage != null
                ) {
                    UvirAttentionMessage(
                        text =
                            partialSessionWarningMessage
                                ?: stringResource(
                                    R.string.share_partial_session_warning
                                ),
                        modifier = Modifier.padding(top = 4.dp),
                        fontSize = 11.sp,
                        lineHeight = 14.sp
                    )
                }

                Spacer(Modifier.height(8.dp))
                HorizontalDivider(color = secondaryText.copy(alpha = 0.22f))
                Spacer(Modifier.height(2.dp))
                UvirExportProfileSection(
                    exportMode = exportMode,
                    onExportModeChange = { exportMode = it },
                    primaryText = primaryText,
                    secondaryText = secondaryText
                )
                Spacer(Modifier.height(8.dp))
                UvirExportBottomActions(
                    enabled = canExport && !exportRequest.inProgress,
                    secondaryText = secondaryText,
                    onSave = {
                        exportRequest.request(UvirExportDestination.SAVE)
                    },
                    onShare = {
                        exportRequest.request(UvirExportDestination.SHARE)
                    }
                )
                    }
                }
            }
        }
    )
}
