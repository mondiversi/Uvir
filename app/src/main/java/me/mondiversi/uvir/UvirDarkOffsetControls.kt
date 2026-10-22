package me.mondiversi.uvir

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Composable
internal fun UvirDarkOffsetControls(
    group: CalibrationGroup, enabled: Boolean, supported: Boolean,
    names: List<String>, cardColor: Color, primaryText: Color, secondaryText: Color,
    measure: (suspend (CalibrationGroup, (Int) -> Unit) -> DarkOffsetReading)?,
    onApply: (DarkOffsetReading) -> Unit, onReset: () -> Unit,
    firmwareUpdateRequired: Boolean = false,
    onFirmwareUpdateRequired: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    val commit = LocalSettingsCommit.current
    val unit = LocalUvirIrradianceUnit.current
    val numericFormat = LocalUvirNumericFormat.current
    var open by remember { mutableStateOf(false) }
    var running by remember { mutableStateOf(false) }
    var stopping by remember { mutableStateOf(false) }
    var elapsed by remember { mutableIntStateOf(0) }
    var result by remember { mutableStateOf<DarkOffsetReading?>(null) }
    var failed by remember { mutableStateOf(false) }
    var job by remember { mutableStateOf<Job?>(null) }
    DisposableEffect(Unit) { onDispose { job?.cancel() } }
    val suffix = group.protocolName.lowercase()
    Column(Modifier.testTag("calibration_actions_$suffix"),
        verticalArrangement = Arrangement.spacedBy(UvirActionButtonGap)) {
        UvirFirmwareGate(firmwareUpdateRequired || (enabled && !supported),
            stringResource(R.string.dark_offsets_calculate), "dark_offsets_$suffix", onFirmwareUpdateRequired) {
        OutlinedButton(
            onClick = { focus.clearFocus(); result = null; failed = false; open = true },
            enabled = enabled && supported && measure != null && !running,
            modifier = Modifier.fillMaxWidth().testTag("dark_offsets_$suffix"),
            colors = uvirPrimaryOutlinedButtonColors(),
            border = uvirPrimaryOutlinedButtonBorder(enabled && supported && measure != null && !running, secondaryText)
        ) {
            UvirLabeledButtonContent(uvirActionLabel(R.string.dark_offsets_calculate)) {
                ConnectivitySectionIcon(ConnectivityIconType.CALIBRATION, Modifier.size(20.dp))
            }
        }
        }
        UvirFirmwareGate(firmwareUpdateRequired, stringResource(R.string.calibration_reset_values),
            "calibration_reset_$suffix", onFirmwareUpdateRequired) {
        UvirCalibrationResetButton(enabled && !running,
            message = stringResource(R.string.calibration_reset_confirmation_message,
                if (group == CalibrationGroup.UV) stringResource(R.string.uv_radiation)
                else stringResource(R.string.visible_light) + " / " + stringResource(R.string.far_red_nir)),
            cardColor = cardColor, primaryText = primaryText, secondaryText = secondaryText,
            modifier = Modifier.testTag("calibration_reset_$suffix")) {
            onReset()
            commit?.commit()
        }
        }
    }
    if (open) {
        fun dismiss() { job?.cancel(); open = false }
        UvirAlertDialog(
            onDismissRequest = ::dismiss,
            title = { Text(stringResource(R.string.dark_offsets_calculate)) },
            containerColor = cardColor, titleContentColor = primaryText, textContentColor = secondaryText,
            modifier = Modifier.testTag("dark_offsets_dialog_$suffix"),
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(if (group == CalibrationGroup.UV) stringResource(R.string.uv_radiation)
                        else stringResource(R.string.visible_light) + " / " + stringResource(R.string.far_red_nir), color = primaryText)
                    // Once started, keep space for progress/the proposed channel values.
                    if (!running && result == null) Text(stringResource(R.string.dark_offsets_instructions))
                    when {
                        running -> {
                            Text(stringResource(R.string.dark_offsets_progress,
                                formatUvirNumber((elapsed / 1000).toDouble(), 0, numericFormat, grouping = false)))
                            LinearProgressIndicator(progress = { elapsed / 30_000f }, modifier = Modifier.fillMaxWidth())
                        }
                        failed -> Text(stringResource(R.string.dark_offsets_failed), color = UvirDestructiveActionColor)
                        result != null -> {
                            val reading = result!!
                            Text(stringResource(R.string.dark_offsets_result,
                                formatUvirNumber(reading.samples.toDouble(), 0, numericFormat, grouping = false)))
                            group.indices.forEachIndexed { index, channel ->
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    UvirCalibrationChannelDot(channel, modifier = Modifier.testTag("dark_offsets_dot_$channel"))
                                    Spacer(Modifier.width(8.dp))
                                    Text(names[channel], Modifier.weight(1f), color = primaryText)
                                    Text(formatUvirDecimalInputText(calibrationOffsetInput(reading.values[index], unit),
                                        numericFormat) + " " + unit.symbol, color = primaryText)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                if (result != null) {
                    TextButton(enabled = enabled && !running, modifier = Modifier.testTag("dark_offsets_apply"), onClick = {
                        result?.let(onApply)
                        commit?.commit()
                        open = false
                    }) { Text(uvirActionLabel(R.string.dark_offsets_apply)) }
                } else if (!running) {
                    TextButton(enabled = enabled && !running && measure != null,
                        modifier = Modifier.testTag("dark_offsets_start"), onClick = {
                            elapsed = 0; failed = false; stopping = false; running = true
                            job = scope.launch {
                                try {
                                    val measured = measure!!(group) { elapsed = it }
                                    check(measured.group == group) { "dark_offsets_group_changed" }
                                    result = measured
                                } catch (cancelled: CancellationException) { throw cancelled }
                                catch (error: Exception) {
                                    UvirErrorLog.record(context, "dark_offsets:$suffix", error)
                                    failed = true
                                } finally { running = false; stopping = false }
                            }
                        }) { Text(uvirActionLabel(R.string.dark_offsets_start)) }
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !stopping,
                    modifier = Modifier.testTag("dark_offsets_cancel"),
                    colors = uvirCancelTextButtonColors(),
                    onClick = {
                        if (running) {
                            // Keep the popup open. Restart is possible only after transport cleanup.
                            stopping = true
                            job?.cancel()
                        } else dismiss()
                    }
                ) { Text(uvirActionLabel(if (running) R.string.stop else R.string.cancel)) }
            }
        )
    }
}
