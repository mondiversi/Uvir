package me.mondiversi.uvir

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun UvirCounterResetConfirmationDialog(
    cardColor: Color,
    primaryText: Color,
    secondaryText: Color,
    onResetAllCounters: () -> Unit,
    onDismissRequest: () -> Unit
) {
    val context = LocalContext.current
    val confirmationTitle =
        stringResource(R.string.reset_all_records_confirmation_title)
    val confirmationMessage =
        stringResource(R.string.reset_all_records_confirmation_message)
    val confirmationAction =
        stringResource(R.string.reset_confirmation_action)
    val resetCompleteMessage =
        stringResource(R.string.all_counters_reset_complete)

    UvirAlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Text(confirmationTitle)
        },
        text = {
            UvirHoldConfirmationMessage(
                message = confirmationMessage,
                actionLabel = confirmationAction,
                holdDurationSeconds = 5
            )
        },
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                HoldToConfirmDeleteButton(
                    label = confirmationAction,
                    holdDurationMillis = 5_000L,
                    onConfirmed = {
                        onResetAllCounters()
                        onDismissRequest()
                        showUvirBottomMessage(
                            context,
                            resetCompleteMessage,
                            longDuration = false
                        )
                    }
                )

                TextButton(onClick = onDismissRequest) {
                    Text(stringResource(R.string.cancel))
                }
            }
        },
        containerColor = cardColor,
        titleContentColor = primaryText,
        textContentColor = secondaryText
    )
}

@Composable
internal fun UvirDataAndRestoreContent(
    autoEnabled: Boolean,
    sensorRestoreEnabled: Boolean,
    secondaryText: Color,
    onResetRequested: () -> Unit,
    onRestoreDefaultsRequested: () -> Unit,
    onSensorRestoreRequested: () -> Unit
) {
    val locale = LocalContext.current.resources.configuration.locales[0]
    val sensorResetDescription = stringResource(R.string.sensor_restore_action)
    val appResetDescription = stringResource(R.string.restore_app_settings_action)
    val countersResetDescription = stringResource(R.string.reset_all_records_action)
    val configuration = LocalConfiguration.current
    val resetTargetFontSize = remember(
        locale,
        configuration.screenWidthDp,
        configuration.fontScale
    ) { mutableStateOf(14.sp) }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement =
            Arrangement.spacedBy(UvirActionButtonGap)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(UvirSettingsRelatedGap)
        ) {
            OutlinedButton(
                onClick = onResetRequested,
                enabled = !autoEnabled,
                modifier = Modifier.fillMaxWidth().semantics {
                    contentDescription = countersResetDescription
                },
                colors = uvirDestructiveOutlinedButtonColors(),
                border = uvirDestructiveOutlinedButtonBorder(!autoEnabled, secondaryText)
            ) {
                UvirLabeledButtonContent(
                    text = stringResource(R.string.data_management_counters_button).uppercase(locale),
                    fontWeight = FontWeight.Bold
                ) {
                    ConnectivitySectionIcon(
                        type = ConnectivityIconType.TALLY,
                        modifier = Modifier.size(20.dp),
                        tint = androidx.compose.material3.LocalContentColor.current,
                        strokeScale = UvirDestructiveOutlinedIconStrokeScale
                    )
                }
            }

            if (autoEnabled) {
                Text(
                    text = stringResource(R.string.reset_counters_stop_auto),
                    color = secondaryText,
                    fontSize = 11.sp
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(UvirActionButtonGap)
        ) {
            OutlinedButton(
                onClick = onSensorRestoreRequested,
                enabled = sensorRestoreEnabled,
                modifier = Modifier.weight(1f).semantics {
                    contentDescription = sensorResetDescription
                },
                colors = uvirDestructiveOutlinedButtonColors(),
                border = uvirDestructiveOutlinedButtonBorder(sensorRestoreEnabled, secondaryText)
            ) {
                UvirLabeledButtonContent(
                    text = stringResource(R.string.settings_category_sensor).uppercase(locale),
                    sharedFontSize = resetTargetFontSize,
                    fontWeight = FontWeight.Bold
                ) {
                    ConnectivitySectionIcon(
                        type = ConnectivityIconType.SENSOR,
                        modifier = Modifier.size(20.dp),
                        tint = androidx.compose.material3.LocalContentColor.current,
                        strokeScale = UvirDestructiveOutlinedIconStrokeScale
                    )
                }
            }

            OutlinedButton(
                onClick = onRestoreDefaultsRequested,
                modifier = Modifier.weight(1f).semantics {
                    contentDescription = appResetDescription
                },
                colors = uvirDestructiveOutlinedButtonColors(),
                border = uvirDestructiveOutlinedButtonBorder(true, secondaryText)
            ) {
                UvirLabeledButtonContent(
                    text = stringResource(R.string.data_management_app_button).uppercase(locale),
                    sharedFontSize = resetTargetFontSize,
                    fontWeight = FontWeight.Bold
                ) {
                    ConnectivitySectionIcon(
                        type = ConnectivityIconType.PHONE,
                        modifier = Modifier.size(20.dp),
                        tint = androidx.compose.material3.LocalContentColor.current,
                        strokeScale = UvirDestructiveOutlinedIconStrokeScale
                    )
                }
            }
        }
    }
}
