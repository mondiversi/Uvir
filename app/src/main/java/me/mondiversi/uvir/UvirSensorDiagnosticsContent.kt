package me.mondiversi.uvir

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Composable
internal fun UvirSensorDiagnosticsContent(
    sensorConnected: Boolean,
    sensorInfo: UvirSensorRuntimeInfo,
    connectionMode: SensorConnectionMode,
    sensorName: String,
    cardColor: Color,
    primaryText: Color,
    secondaryText: Color,
    onProbe: suspend (String, SensorConnectionMode) -> UvirDiagnosticProbe,
    showDescription: Boolean = true
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    var showDialog by remember { mutableStateOf(false) }
    var showFirmwareDialog by remember { mutableStateOf(false) }
    var inProgress by remember { mutableStateOf(false) }
    var progress by remember { mutableIntStateOf(0) }
    var report by remember { mutableStateOf<UvirSensorDiagnosticReport?>(null) }
    var job by remember { mutableStateOf<Job?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(UvirSettingsRelatedGap)) {
        OutlinedButton(
            enabled = sensorConnected && !inProgress,
            modifier = Modifier.fillMaxWidth(),
            colors = uvirOutlinedActionColors(primaryText),
            border = uvirOutlinedActionBorder(sensorConnected && !inProgress, secondaryText),
            onClick = {
                if (compareFirmwareVersions(sensorInfo.firmwareVersion, UVIR_DIAGNOSTIC_MINIMUM_FIRMWARE) < 0) {
                    showFirmwareDialog = true
                } else {
                    val initialInfo = sensorInfo
                    val initialMode = connectionMode
                    val initialName = sensorName
                    progress = 0
                    report = null
                    inProgress = true
                    showDialog = true
                    job = scope.launch {
                        try {
                            report = runUvirSensorDiagnostics(
                                initialName, initialMode, initialInfo,
                                onProgress = { progress = it },
                                probe = { onProbe(initialInfo.deviceId, initialMode) }
                            )
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (error: Exception) {
                            UvirErrorLog.record(context, "sensor_diagnostic", error)
                            showUvirBottomMessage(context, resources.getString(R.string.diagnostic_failed))
                            showDialog = false
                        } finally {
                            inProgress = false
                        }
                    }
                }
            }
        ) {
            UvirLabeledButtonContent(
                text = stringResource(R.string.debug_diagnostic)
            ) {
                ConnectivitySectionIcon(
                    ConnectivityIconType.DEBUG,
                    Modifier.size(20.dp),
                    tint = LocalContentColor.current
                )
            }
        }
        if (showDescription) Text(
            stringResource(R.string.diagnostic_description),
            color = if (sensorConnected) secondaryText else secondaryText.copy(alpha = 0.46f),
            fontSize = 11.sp,
            lineHeight = 14.sp
        )
    }

    if (showFirmwareDialog) {
        UvirFirmwareUpdateRequiredDialog(cardColor, primaryText, secondaryText) {
            showFirmwareDialog = false
        }
    }
    fun exportDiagnostic(destination: UvirExportDestination) {
        val completedReport = report ?: return
        runCatching {
            val exportFormatting =
                uvirExportFormatting(
                    context = context,
                    mode = UvirExportMode.INTERNATIONAL
                )
            shareUvirSensorDiagnosticReport(
                context = context,
                text =
                    formatUvirSensorDiagnosticReport(
                        resources = exportFormatting.context.resources,
                        report = completedReport,
                        technicalFormat = true
                    ),
                deviceId = completedReport.initialInfo.deviceId,
                startedAtMs = completedReport.startedAtMs,
                destination = destination
            )
        }.onFailure { error ->
            UvirErrorLog.record(context, "export_sensor_diagnostic", error)
            showUvirBottomMessage(
                context,
                resources.getString(R.string.diagnostic_share_error)
            )
        }
    }
    val exportRequest = rememberUvirExportRequestGate(::exportDiagnostic)
    if (showDialog) {
        val formatted = report?.let { formatUvirSensorDiagnosticReport(resources, it) }
        fun dismiss() {
            if (exportRequest.inProgress) return
            job?.cancel()
            showDialog = false
        }
        UvirAlertDialog(
            onDismissRequest = ::dismiss,
            title = {
                UvirClosableDialogTitle(
                    title = stringResource(R.string.diagnostic_title),
                    onDismiss = ::dismiss
                )
            },
            text = {
                Box(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (inProgress) {
                            Text(
                                stringResource(R.string.diagnostic_progress, progress, UVIR_DIAGNOSTIC_REQUESTS),
                                color = primaryText
                            )
                            LinearProgressIndicator(
                                progress = { progress.toFloat() / UVIR_DIAGNOSTIC_REQUESTS },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text(stringResource(R.string.diagnostic_description), color = secondaryText, fontSize = 12.sp)
                        } else if (formatted != null) {
                            Text(formatted, color = primaryText, fontSize = 13.sp, lineHeight = 18.sp)
                            UvirExportFileCountText(
                                format = UvirExportFileFormat.TXT,
                                count = 1,
                                secondaryText = secondaryText
                            )
                        }
                    }
                }
            },
            confirmButton = {
                if (!inProgress && report != null) {
                    TextButton(
                        enabled = !exportRequest.inProgress,
                        onClick = {
                            exportRequest.request(UvirExportDestination.SHARE)
                        }
                    ) {
                        Text(stringResource(R.string.share))
                    }
                }
            },
            dismissButton = {
                if (!inProgress && report != null) {
                    TextButton(
                        enabled = !exportRequest.inProgress,
                        onClick = {
                            exportRequest.request(UvirExportDestination.SAVE)
                        }
                    ) {
                        Text(stringResource(R.string.save))
                    }
                }
            },
            containerColor = cardColor,
            titleContentColor = primaryText,
            textContentColor = primaryText
        )
    }
}
