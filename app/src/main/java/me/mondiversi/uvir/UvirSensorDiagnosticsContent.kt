package me.mondiversi.uvir

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
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
    onProbe: suspend (String, SensorConnectionMode) -> UvirDiagnosticProbe
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
            ConnectivitySectionIcon(ConnectivityIconType.DEBUG, Modifier.size(20.dp),
                tint = LocalContentColor.current)
            Spacer(Modifier.width(8.dp))
            AdaptiveSingleLineButtonText(stringResource(R.string.debug_diagnostic))
        }
        Text(
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
    if (showDialog) {
        val scrollbar = rememberUvirDialogScrollbar(secondaryText.copy(alpha = 0.58f))
        val formatted = report?.let { formatUvirSensorDiagnosticReport(resources, it) }
        fun dismiss() {
            job?.cancel()
            showDialog = false
        }
        AlertDialog(
            onDismissRequest = ::dismiss,
            modifier = scrollbar.dialogModifier,
            title = { Text(stringResource(R.string.diagnostic_title)) },
            text = {
                Box(Modifier.fillMaxWidth().heightIn(max = 360.dp).then(scrollbar.viewportModifier)) {
                    Column(
                        Modifier.fillMaxWidth().verticalScroll(scrollbar.scrollState),
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
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = ::dismiss) { Text(stringResource(R.string.close)) }
            },
            dismissButton = {
                TextButton(enabled = !inProgress && report != null, onClick = {
                    val result = report ?: return@TextButton
                    runCatching {
                        shareUvirSensorDiagnosticReport(context,
                            formatUvirSensorDiagnosticReport(resources, result), result.startedAtMs)
                    }.onFailure { error ->
                        UvirErrorLog.record(context, "share_sensor_diagnostic", error)
                        showUvirBottomMessage(context, resources.getString(R.string.diagnostic_share_error))
                    }
                }) { Text(stringResource(R.string.share)) }
            },
            containerColor = cardColor,
            titleContentColor = primaryText,
            textContentColor = primaryText
        )
    }
}
