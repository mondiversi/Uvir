package me.mondiversi.uvir

import android.content.res.Resources
import androidx.core.text.BidiFormatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

internal data class SensorSyncSummary(
    val acquisitions: Int,
    val alerts: Int,
    val errors: Int,
    val storageWasFull: Boolean = false,
    val sensorDeviceId: String = "",
    val sensorName: String = ""
)

/** Stamp the event's origin; never borrow the currently selected sensor's name. */
internal fun SensorSyncSummary.withSensorOrigin(
    deviceId: String,
    profiles: List<UvirSensorProfile>
): SensorSyncSummary = copy(
    sensorDeviceId = deviceId.trim(),
    sensorName = uvirConnectionAnnouncementSensorName(deviceId, profiles)
)

internal fun sensorSyncCompleteTitle(resources: Resources, summary: SensorSyncSummary): String {
    val name = summary.sensorName.trim().ifBlank { summary.sensorDeviceId.trim() }
    return if (name.isBlank()) resources.getString(R.string.sensor_sync_complete)
    else resources.getString(R.string.sensor_sync_complete_from,
        BidiFormatter.getInstance(resources.configuration.locales[0]).unicodeWrap(name))
}

internal data class SensorSyncIncompleteSummary(
    val acquisitions: Int,
    val alerts: Int,
    val errors: Int
) {
    val total: Int
        get() = acquisitions + alerts + errors
}

@Composable
internal fun SensorSyncCompleteDialog(
    summary: SensorSyncSummary,
    cardColor: Color,
    primaryText: Color,
    secondaryText: Color,
    onDismiss: () -> Unit
) {
    UvirAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            UvirClosableDialogTitle(
                title = sensorSyncCompleteTitle(LocalResources.current, summary),
                onDismiss = onDismiss
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (summary.acquisitions > 0) SensorSyncCountRow(
                    label = stringResource(
                        R.string.sensor_sync_acquisitions_recovered
                    ),
                    value = summary.acquisitions,
                    primaryText = primaryText,
                    secondaryText = secondaryText
                )
                if (summary.alerts > 0) SensorSyncCountRow(
                    label = stringResource(
                        R.string.sensor_sync_alerts_recovered
                    ),
                    value = summary.alerts,
                    primaryText = primaryText,
                    secondaryText = secondaryText
                )
                if (summary.errors > 0) {
                    SensorSyncCountRow(
                        label = stringResource(
                            R.string.sensor_sync_errors_recovered
                        ),
                        value = summary.errors,
                        primaryText = primaryText,
                        secondaryText = secondaryText
                    )
                }
                if (summary.storageWasFull) {
                    UvirAttentionMessage(
                        text = stringResource(
                            R.string.sensor_sync_storage_full_warning
                        ),
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        },
        confirmButton = {},
        containerColor = cardColor,
        titleContentColor = primaryText,
        textContentColor = primaryText
    )
}

@Composable
internal fun SensorSyncIncompleteDialog(
    summary: SensorSyncIncompleteSummary,
    cardColor: Color,
    primaryText: Color,
    secondaryText: Color,
    onDismiss: () -> Unit
) {
    UvirAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            UvirClosableDialogTitle(
                title = stringResource(R.string.sensor_sync_incomplete),
                onDismiss = onDismiss
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = stringResource(
                        R.string.sensor_sync_incomplete_description
                    ),
                    color = secondaryText
                )
                if (summary.acquisitions > 0) {
                    SensorSyncCountRow(
                        label = stringResource(
                            R.string.sensor_pending_acquisitions
                        ),
                        value = summary.acquisitions,
                        primaryText = primaryText,
                        secondaryText = secondaryText
                    )
                }
                if (summary.alerts > 0) {
                    SensorSyncCountRow(
                        label = stringResource(
                            R.string.sensor_pending_alerts
                        ),
                        value = summary.alerts,
                        primaryText = primaryText,
                        secondaryText = secondaryText
                    )
                }
                if (summary.errors > 0) {
                    SensorSyncCountRow(
                        label = stringResource(
                            R.string.sensor_pending_errors
                        ),
                        value = summary.errors,
                        primaryText = primaryText,
                        secondaryText = secondaryText
                    )
                }
            }
        },
        confirmButton = {},
        containerColor = cardColor,
        titleContentColor = primaryText,
        textContentColor = primaryText
    )
}

@Composable
private fun SensorSyncCountRow(
    label: String,
    value: Int,
    primaryText: Color,
    secondaryText: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, color = secondaryText)
        Text(
            text = value.toString(),
            color = primaryText,
            fontWeight = FontWeight.Bold
        )
    }
}
