package me.mondiversi.uvir

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

@Composable
internal fun AcquisitionChartShareDialog(
    record: SavedRecordDetail,
    selectedGroup: AcquisitionChartGroup,
    backgroundColor: Color,
    cardColor: Color,
    primaryText: Color,
    secondaryText: Color,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val shareErrorText =
        stringResource(R.string.acquisition_chart_share_error)
    var shareScope by rememberSaveable {
        mutableStateOf(loadUvirAcquisitionChartShareScope(context))
    }
    var exportMode by rememberSaveable {
        mutableStateOf(loadUvirExportMode(context))
    }
    val scrollState = rememberScrollState()

    fun export(destination: UvirExportDestination) {
        saveUvirExportMode(context, exportMode)
        saveUvirAcquisitionChartShareScope(context, shareScope)
        runCatching {
            shareAcquisitionCharts(
                context = context,
                record = record,
                groups =
                    if (shareScope == AcquisitionChartShareScope.ALL) {
                        AcquisitionChartGroup.entries
                    } else {
                        listOf(selectedGroup)
                    },
                destination = destination
            )
        }.onFailure { error ->
            UvirErrorLog.record(
                context,
                "share_acquisition_chart",
                error
            )
            showUvirBottomMessage(
                context,
                shareErrorText,
                longDuration = false
            )
        }
        onDismiss()
    }
    val exportRequest = rememberUvirExportRequestGate(::export)

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
                SessionChartShareOption(
                    selected =
                        shareScope == AcquisitionChartShareScope.CURRENT,
                    title =
                        stringResource(
                            R.string.acquisition_chart_share_current
                        ),
                    description =
                        stringResource(
                            R.string.acquisition_chart_share_current_description,
                            stringResource(selectedGroup.titleResource)
                        ),
                    primaryText = primaryText,
                    secondaryText = secondaryText,
                    onClick = {
                        shareScope = AcquisitionChartShareScope.CURRENT
                    }
                )

                SessionChartShareOption(
                    selected =
                        shareScope == AcquisitionChartShareScope.ALL,
                    title =
                        stringResource(
                            R.string.acquisition_chart_share_all
                        ),
                    description =
                        stringResource(
                            R.string.acquisition_chart_share_all_description
                        ),
                    primaryText = primaryText,
                    secondaryText = secondaryText,
                    onClick = {
                        shareScope = AcquisitionChartShareScope.ALL
                    }
                )

                UvirExportFileCountText(
                    format = UvirExportFileFormat.PNG,
                    count = 1,
                    secondaryText = secondaryText,
                    modifier = Modifier.padding(start = 38.dp, top = 2.dp)
                )

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
                    enabled = !exportRequest.inProgress,
                    primaryText = primaryText,
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
