package me.mondiversi.uvir

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
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
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors =
                    ButtonDefaults.textButtonColors(
                        contentColor = UvirDestructiveActionColor
                    )
            ) {
                Text(stringResource(R.string.cancel))
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    runCatching {
                        shareAcquisitionCharts(
                            context = context,
                            record = record,
                            groups =
                                if (
                                    shareScope == AcquisitionChartShareScope.ALL
                                ) {
                                    AcquisitionChartGroup.entries
                                } else {
                                    listOf(selectedGroup)
                                }
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
            ) {
                Text(stringResource(R.string.share))
            }
        },
        containerColor = cardColor,
        titleContentColor = primaryText,
        textContentColor = secondaryText
    )
}
