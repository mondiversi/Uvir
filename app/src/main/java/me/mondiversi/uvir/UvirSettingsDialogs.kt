package me.mondiversi.uvir

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

@Composable
internal fun UvirFirmwareUpdateRequiredDialog(
    cardColor: Color,
    primaryText: Color,
    secondaryText: Color,
    onCheckUpdates: (() -> Unit)? = null,
    onDismissRequest: () -> Unit
) {
    val context = LocalContext.current
    UvirAlertDialog(
        modifier = Modifier.testTag("firmware_update_required_dialog"),
        onDismissRequest = onDismissRequest,
        title = {
            UvirClosableDialogTitle(
                title =
                    stringResource(
                        R.string.sensor_firmware_update_required_title
                    ),
                onDismiss = onDismissRequest
            )
        },
        text = {
            Text(
                stringResource(
                    R.string.sensor_firmware_update_required
                )
            )
        },
        confirmButton = {
            TextButton(modifier = Modifier.testTag("firmware_update_check"), onClick = {
                onDismissRequest()
                if (onCheckUpdates != null) onCheckUpdates()
                else UvirUpdates.check(context, manual = true)
            }) { Text(uvirActionLabel(R.string.check_for_updates)) }
        },
        dismissButton = {
            UvirDialogCloseButton(onDismissRequest)
        },
        containerColor = cardColor,
        titleContentColor = primaryText,
        textContentColor = secondaryText
    )
}

@Composable
internal fun UvirResetAlertRulesConfirmationDialog(
    thresholdAlertSettings: ThresholdAlertSettings,
    alertRuleEnabledStates: MutableMap<ThresholdAlertMetric, Boolean>,
    alertRuleDirectionValues: MutableMap<ThresholdAlertMetric, String>,
    alertRuleThresholdTexts: MutableMap<ThresholdAlertMetric, String>,
    cardColor: Color,
    primaryText: Color,
    secondaryText: Color,
    onApplyThresholdAlertSettings: (ThresholdAlertSettings) -> Unit,
    onDismissRequest: () -> Unit
) {
    val context = LocalContext.current
    val irradianceUnit = LocalUvirIrradianceUnit.current
    val rulesResetCompleteMessage =
        stringResource(R.string.threshold_rules_reset_complete)

    UvirAlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Text(
                stringResource(
                    R.string.threshold_rules_reset_confirmation_title
                )
            )
        },
        text = {
            UvirHoldConfirmationMessage(
                message = stringResource(
                    R.string.threshold_rules_reset_confirmation_message
                ),
                actionLabel =
                    stringResource(
                        R.string.threshold_rules_reset_confirm_action
                    ),
                holdDurationSeconds = 2
            )
        },
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                HoldToConfirmDeleteButton(
                    label =
                        stringResource(
                            R.string.threshold_rules_reset_confirm_action
                        ),
                    onConfirmed = {
                        val resetRules =
                            thresholdAlertSettings.rules.map { rule ->
                                rule.copy(
                                    enabled = false,
                                    direction =
                                        ThresholdAlertDirection.ABOVE,
                                    threshold = DEFAULT_IRRADIANCE_THRESHOLD_W_M2
                                )
                            }

                        resetRules.forEach { rule ->
                            alertRuleEnabledStates[rule.metric] = false
                            alertRuleDirectionValues[rule.metric] =
                                rule.direction.name
                            alertRuleThresholdTexts[rule.metric] =
                                thresholdEditableValue(
                                    irradianceUnit.fromCanonicalWm2(
                                        rule.threshold.toDouble()
                                    )
                                )
                        }

                        onApplyThresholdAlertSettings(
                            thresholdAlertSettings.copy(
                                rules = resetRules
                            )
                        )
                        onDismissRequest()

                        showUvirBottomMessage(
                            context,
                            rulesResetCompleteMessage,
                            longDuration = false
                        )
                    }
                )

                Spacer(Modifier.width(4.dp))

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
