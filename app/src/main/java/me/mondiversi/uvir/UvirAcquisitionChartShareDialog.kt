package me.mondiversi.uvir

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

@Composable
internal fun AcquisitionChartShareDialog(
    record: SavedRecordDetail,
    selectedGroup: AcquisitionChartGroup,
    cardColor: Color,
    primaryText: Color,
    secondaryText: Color,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val shareErrorText =
        stringResource(R.string.acquisition_chart_share_error)
    var shareScope by rememberSaveable {
        mutableStateOf(AcquisitionChartShareScope.CURRENT)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    R.string.acquisition_chart_share_title
                )
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
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
            }
        },
        confirmButton = {
            fun export(destination: UvirExportDestination) {
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

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = { export(UvirExportDestination.SAVE) }) {
                        Text(stringResource(R.string.save))
                    }
                    TextButton(onClick = { export(UvirExportDestination.SHARE) }) {
                        Text(stringResource(R.string.share))
                    }
                }
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End),
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = UvirDestructiveActionColor
                    )
                ) {
                    Text(stringResource(R.string.cancel))
                }
            }
        },
        containerColor = cardColor,
        titleContentColor = primaryText,
        textContentColor = secondaryText
    )
}
