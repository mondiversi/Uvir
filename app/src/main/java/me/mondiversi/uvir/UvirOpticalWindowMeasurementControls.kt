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
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Composable
internal fun UvirOpticalWindowMeasurementControls(
    value: OpticalWindowSettings,
    enabled: Boolean,
    names: List<String>,
    cardColor: Color,
    primaryText: Color,
    secondaryText: Color,
    measure: (suspend (OpticalWindowMeasurementContext?, (Int) -> Unit) -> OpticalWindowReading)?,
    onApply: (OpticalWindowSettings) -> Unit
) {
    val context = LocalContext.current
    val focus = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val commit = LocalSettingsCommit.current
    val numericFormat = LocalUvirNumericFormat.current
    val latestValue by rememberUpdatedState(value)
    var open by remember { mutableStateOf(false) }
    var reference by remember { mutableStateOf<OpticalWindowReading?>(null) }
    var proposal by remember { mutableStateOf<OpticalWindowProposal?>(null) }
    var running by remember { mutableStateOf(false) }
    var stopping by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var elapsed by remember { mutableIntStateOf(0) }
    var job by remember { mutableStateOf<Job?>(null) }
    DisposableEffect(Unit) { onDispose { job?.cancel() } }
    fun reset() { reference = null; proposal = null; failed = false; elapsed = 0 }

    OutlinedButton(onClick = { focus.clearFocus(); reset(); open = true },
        enabled = enabled && measure != null && !running,
        modifier = Modifier.fillMaxWidth().testTag("optical_window_calculate"),
        colors = uvirPrimaryOutlinedButtonColors(),
        border = uvirPrimaryOutlinedButtonBorder(enabled && measure != null && !running, secondaryText)) {
        UvirLabeledButtonContent(uvirActionLabel(R.string.window_measure_calculate)) {
            ConnectivitySectionIcon(ConnectivityIconType.CALIBRATION, Modifier.size(20.dp))
        }
    }

    if (open) {
        fun dismiss() { job?.cancel(); open = false }
        fun start() {
            val previous = reference
            elapsed = 0; failed = false; stopping = false; running = true
            job = scope.launch {
                try {
                    commit?.queue?.awaitIdle()
                    val reading = checkNotNull(measure)(previous?.context) { elapsed = it }
                    check(reading.isCurrent()) { "window_measurement_changed" }
                    check(latestValue.forWindowEditor() == reading.context.window.forWindowEditor()) {
                        "window_measurement_settings_pending"
                    }
                    if (previous == null) {
                        check(reading.channels.any { it.issue == null }) { "window_measurement_no_usable_channels" }
                        reference = reading
                    } else {
                        proposal = opticalWindowProposal(previous, reading)
                        val discarded = proposal!!.channels.mapIndexedNotNull { index, channel ->
                            channel.issue?.let { "$index=$it" }
                        }
                        if (discarded.isNotEmpty()) UvirErrorLog.record(context,
                            "optical_window_measurement:channels", discarded.joinToString(", "))
                    }
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (error: Exception) {
                    UvirErrorLog.record(context, "optical_window_measurement", error)
                    reference = null; proposal = null; failed = true
                } finally { running = false; stopping = false }
            }
        }

        UvirAlertDialog(onDismissRequest = ::dismiss,
            title = { Text(stringResource(R.string.window_measure_calculate)) },
            modifier = Modifier.testTag("optical_window_measure_dialog"),
            containerColor = cardColor, titleContentColor = primaryText, textContentColor = secondaryText,
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    when {
                        running -> {
                            Text(stringResource(if (reference == null) R.string.window_measure_reference_progress
                                else R.string.window_measure_filtered_progress,
                                formatUvirNumber((elapsed / 1000).toDouble(), 0, numericFormat, grouping = false),
                                formatUvirNumber((OPTICAL_WINDOW_MEASUREMENT_MS / 1000).toDouble(), 0,
                                    numericFormat, grouping = false)))
                            LinearProgressIndicator(progress = { elapsed / OPTICAL_WINDOW_MEASUREMENT_MS.toFloat() },
                                modifier = Modifier.fillMaxWidth().testTag("optical_window_measure_progress"))
                        }
                        proposal != null -> {
                            Text(stringResource(R.string.window_measure_results))
                            Text(stringResource(R.string.window_measure_valid_channels,
                                formatUvirNumber(proposal!!.validChannels.toDouble(), 0, numericFormat, grouping = false),
                                formatUvirNumber(11.0, 0, numericFormat, grouping = false)), color = primaryText)
                            proposal!!.channels.forEachIndexed { index, channel ->
                                if (index == 3) SettingsGroupDivider(secondaryText)
                                Row(Modifier.fillMaxWidth().testTag("optical_window_proposal_$index"),
                                    verticalAlignment = Alignment.CenterVertically) {
                                    UvirCalibrationChannelDot(index)
                                    Spacer(Modifier.width(8.dp))
                                    Text(names[index], Modifier.weight(1f), color = primaryText)
                                    Spacer(Modifier.width(8.dp))
                                    if (channel.factor != null) {
                                        Text("×" + formatUvirNumber(channel.factor.toDouble(), 3, numericFormat,
                                            grouping = false, minimumDigits = 1), color = primaryText)
                                    } else {
                                        Text(stringResource(checkNotNull(channel.issue).messageResource()),
                                            Modifier.weight(1f), color = secondaryText, fontSize = 11.sp,
                                            lineHeight = 16.sp, textAlign = androidx.compose.ui.text.style.TextAlign.End)
                                    }
                                }
                            }
                        }
                        else -> {
                            if (failed) Text(stringResource(R.string.window_measure_failed),
                                color = UvirDestructiveActionColor)
                            Text(stringResource(if (reference == null) R.string.window_measure_reference_instructions
                                else R.string.window_measure_filtered_instructions))
                        }
                    }
                }
            },
            confirmButton = {
                if (!running && proposal != null) {
                    TextButton(enabled = enabled && proposal!!.canApplyTo(latestValue),
                        modifier = Modifier.testTag("optical_window_measure_apply"), onClick = {
                            try {
                                onApply(proposal!!.applyTo(latestValue))
                                commit?.commit()
                                open = false
                            } catch (error: Exception) {
                                UvirErrorLog.record(context, "optical_window_measurement:apply", error)
                                reset(); failed = true
                            }
                        }) { Text(uvirActionLabel(R.string.window_measure_apply)) }
                } else if (!running) {
                    TextButton(enabled = enabled && measure != null,
                        modifier = Modifier.testTag("optical_window_measure_start"), onClick = ::start) {
                        Text(uvirActionLabel(R.string.dark_offsets_start))
                    }
                }
            },
            dismissButton = {
                TextButton(enabled = !stopping, modifier = Modifier.testTag("optical_window_measure_cancel"),
                    colors = uvirCancelTextButtonColors(), onClick = {
                        if (running) { stopping = true; job?.cancel() } else dismiss()
                    }) { Text(uvirActionLabel(if (running) R.string.stop else R.string.cancel)) }
            })
    }
}

private fun OpticalWindowChannelIssue.messageResource(): Int = when (this) {
    OpticalWindowChannelIssue.UNAVAILABLE -> R.string.window_measure_unavailable
    OpticalWindowChannelIssue.INSUFFICIENT -> R.string.window_measure_insufficient
    OpticalWindowChannelIssue.UNSTABLE -> R.string.window_measure_unstable
    OpticalWindowChannelIssue.SATURATED -> R.string.window_measure_saturated
    OpticalWindowChannelIssue.BRIGHTER -> R.string.window_measure_brighter
    OpticalWindowChannelIssue.OUT_OF_RANGE -> R.string.window_measure_out_of_range
}
