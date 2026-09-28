package me.mondiversi.uvir

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
internal fun SensorRestoreConfirmation(
    sensorProfiles: List<UvirSensorProfile>,
    sensorIndicators: Map<String, UvirStatusIndicator>,
    inProgress: Boolean,
    onInProgressChange: (Boolean) -> Unit,
    coroutineScope: CoroutineScope,
    onRestoreSensor: suspend (List<String>) -> Set<String>,
    onDismissRequest: () -> Unit,
    cardColor: Color,
    primaryText: Color,
    secondaryText: Color
) {
    val context = LocalContext.current
    var selectedIds by remember { mutableStateOf(emptySet<String>()) }
    var completedIds by remember { mutableStateOf(emptySet<String>()) }
    val targets = uvirSensorResetTargets(selectedIds, sensorProfiles, sensorIndicators)
        .filter { normalizeSensorDeviceId(it) !in completedIds }
    val dismiss = { if (!inProgress) onDismissRequest() }

    UvirAlertDialog(
        onDismissRequest = dismiss,
        modifier = Modifier.testTag("sensor_reset_dialog"),
        title = {
            UvirClosableDialogTitle(stringResource(R.string.sensor_reset_selection_title), dismiss)
        },
        text = {
            Box(Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth()
                    .testTag("sensor_reset_scroll"),
                    verticalArrangement = Arrangement.spacedBy(UvirSettingsControlGap)) {
                    Text(stringResource(R.string.sensor_reset_selection_message))
                    Column {
                        sensorProfiles.forEach { profile ->
                            val id = normalizeSensorDeviceId(profile.hardwareUid)
                            val eligible = id !in completedIds &&
                                uvirSensorCanReset(sensorIndicators[id])
                            SettingsExportOptionRow(
                                selected = eligible && id in selectedIds,
                                enabled = eligible && !inProgress,
                                title = profile.displayName,
                                description = profile.hardwareUid,
                                iconType = ConnectivityIconType.SENSOR,
                                primaryText = primaryText, secondaryText = secondaryText,
                                onClick = {
                                    selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id
                                }
                            )
                        }
                    }
                    UvirHoldConfirmationInstruction(
                        actionLabel = stringResource(R.string.sensor_restore_confirm),
                        holdDurationSeconds = 5
                    )
                }
            }
        },
        confirmButton = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically) {
                HoldToConfirmActionButton(
                    label = stringResource(R.string.sensor_restore_confirm),
                    enabled = !inProgress && targets.isNotEmpty(),
                    holdDurationMillis = 5_000L,
                    onConfirmed = {
                        if (!inProgress && targets.isNotEmpty()) {
                            val requested = targets.toList()
                            onInProgressChange(true)
                            coroutineScope.launch {
                                val restored = try {
                                    onRestoreSensor(requested).map(::normalizeSensorDeviceId).toSet()
                                } catch (error: Exception) {
                                    if (error is CancellationException) throw error
                                    UvirErrorLog.record(context, "reset_selected_sensors", error)
                                    emptySet()
                                } finally {
                                    onInProgressChange(false)
                                }
                                completedIds = completedIds + restored
                                selectedIds = selectedIds - restored
                                val failed = requested.filter { normalizeSensorDeviceId(it) !in restored }
                                if (failed.isEmpty()) onDismissRequest()
                                else {
                                    val names = failed.map { id ->
                                        sensorProfiles.firstOrNull { it.hardwareUid.equals(id, true) }
                                            ?.displayName ?: id
                                    }.joinToString(", ")
                                    showUvirBottomMessage(context,
                                        context.getString(R.string.sensor_reset_selection_failed, names))
                                }
                            }
                        }
                    }
                )
                TextButton(onClick = dismiss, enabled = !inProgress) {
                    Text(stringResource(R.string.cancel))
                }
            }
        },
        containerColor = cardColor,
        titleContentColor = primaryText,
        textContentColor = secondaryText
    )
}
