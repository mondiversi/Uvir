package me.mondiversi.uvir

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

internal data class UvirExportRequestGate(
    val inProgress: Boolean,
    val request: (UvirExportDestination) -> Unit
)

@Composable
internal fun rememberUvirExportRequestGate(
    onExport: (UvirExportDestination) -> Unit
): UvirExportRequestGate {
    var pendingDestination by remember {
        mutableStateOf<UvirExportDestination?>(null)
    }
    val currentOnExport by rememberUpdatedState(onExport)

    LaunchedEffect(pendingDestination) {
        val destination = pendingDestination ?: return@LaunchedEffect
        withFrameNanos { }
        try {
            currentOnExport(destination)
        } finally {
            pendingDestination = null
        }
    }

    return UvirExportRequestGate(
        inProgress = pendingDestination != null,
        request = { destination ->
            if (pendingDestination == null) {
                pendingDestination = destination
            }
        }
    )
}

@Composable
internal fun MeasurementShareFormatDialog(
    cardColor: Color,
    primaryText: Color,
    secondaryText: Color,
    onDismiss: () -> Unit,
    onFormatSelected: (MeasurementShareFormat, UvirExportDestination) -> Unit
) {
    var csvSelected by rememberSaveable {
        mutableStateOf(false)
    }
    var readableTableSelected by rememberSaveable {
        mutableStateOf(false)
    }

    val selectedFormat =
        when {
            csvSelected && readableTableSelected ->
                MeasurementShareFormat.BOTH

            csvSelected ->
                MeasurementShareFormat.CSV

            readableTableSelected ->
                MeasurementShareFormat.READABLE_TABLE

            else -> null
        }
    val exportRequest =
        rememberUvirExportRequestGate { destination ->
            selectedFormat?.let { format ->
                onFormatSelected(format, destination)
            }
        }

    UvirAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            UvirClosableDialogTitle(
                title = stringResource(R.string.choose_share_format),
                onDismiss = onDismiss
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement =
                    Arrangement.spacedBy(6.dp)
            ) {
                ShareFormatOptionRow(
                    format = MeasurementShareFormat.CSV,
                    selected = csvSelected,
                    label =
                        stringResource(
                            R.string.share_as_csv
                        ),
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
                        modifier = Modifier.padding(start = UvirExportOptionDetailInset)
                    )
                }

                ShareFormatOptionRow(
                    format =
                        MeasurementShareFormat.READABLE_TABLE,
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
                if (readableTableSelected) {
                    UvirExportFileCountText(
                        format = UvirExportFileFormat.TXT,
                        count = 1,
                        secondaryText = secondaryText,
                        modifier = Modifier.padding(start = UvirExportOptionDetailInset)
                    )
                }
            }
        },
        confirmButton = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        enabled = selectedFormat != null && !exportRequest.inProgress,
                        onClick = {
                            exportRequest.request(UvirExportDestination.SAVE)
                        }
                    ) {
                        Text(stringResource(R.string.save))
                    }

                    TextButton(
                        enabled = selectedFormat != null && !exportRequest.inProgress,
                        onClick = {
                            exportRequest.request(UvirExportDestination.SHARE)
                        }
                    ) {
                        Text(stringResource(R.string.share))
                    }
                }

            }
        },
        containerColor = cardColor,
        titleContentColor = primaryText,
        textContentColor = secondaryText
    )
}

@Composable
internal fun UvirSaveOrShareDialog(
    cardColor: Color,
    primaryText: Color,
    secondaryText: Color,
    title: String,
    description: String,
    fileFormat: UvirExportFileFormat,
    fileCount: Int = 1,
    onDismiss: () -> Unit,
    onExport: (UvirExportDestination) -> Unit
) {
    val exportRequest = rememberUvirExportRequestGate(onExport)
    UvirAlertDialog(
        onDismissRequest = {
            if (!exportRequest.inProgress) onDismiss()
        },
        title = {
            UvirClosableDialogTitle(
                title = title,
                onDismiss = {
                    if (!exportRequest.inProgress) onDismiss()
                }
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = description,
                    color = secondaryText
                )
                UvirExportFileCountText(
                    format = fileFormat,
                    count = fileCount,
                    secondaryText = secondaryText
                )
            }
        },
        confirmButton = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        enabled = !exportRequest.inProgress,
                        onClick = {
                            exportRequest.request(UvirExportDestination.SAVE)
                        }
                    ) {
                        Text(stringResource(R.string.save))
                    }
                    TextButton(
                        enabled = !exportRequest.inProgress,
                        onClick = {
                            exportRequest.request(UvirExportDestination.SHARE)
                        }
                    ) {
                        Text(stringResource(R.string.share))
                    }
                }
            }
        },
        containerColor = cardColor,
        titleContentColor = primaryText,
        textContentColor = secondaryText
    )
}
