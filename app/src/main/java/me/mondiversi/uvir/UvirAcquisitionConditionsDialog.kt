package me.mondiversi.uvir

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Edits an independent phone draft; saving never starts monitoring or sends sensor commands. */
@Composable
internal fun UvirAcquisitionConditionsDialog(
    initialRules: List<ThresholdAlertRule>,
    cardColor: androidx.compose.ui.graphics.Color,
    primaryText: androidx.compose.ui.graphics.Color,
    secondaryText: androidx.compose.ui.graphics.Color,
    currentSample: SensorSample? = null,
    onSave: (List<ThresholdAlertRule>) -> Unit,
    onDismissRequest: () -> Unit
) {
    val context = LocalContext.current
    val focus = LocalFocusManager.current
    val metrics = remember { acquisitionConditionMetrics() }
    val enabled = remember(initialRules) {
        mutableStateMapOf<ThresholdAlertMetric, Boolean>().apply {
            metrics.forEach { metric -> put(metric, initialRules.firstOrNull { it.metric == metric }?.enabled == true) }
        }
    }
    val directions = remember(initialRules) {
        mutableStateMapOf<ThresholdAlertMetric, ThresholdAlertDirection>().apply {
            metrics.forEach { metric -> put(metric,
                initialRules.firstOrNull { it.metric == metric }?.direction ?: ThresholdAlertDirection.ABOVE) }
        }
    }
    val thresholds = remember(initialRules) {
        mutableStateMapOf<ThresholdAlertMetric, String>().apply {
            metrics.forEach { metric -> put(metric,
                (initialRules.firstOrNull { it.metric == metric }?.threshold ?: 1f).toString()) }
        }
    }
    val scrollbar = rememberUvirDialogScrollbar(secondaryText.copy(alpha = 0.58f))
    fun normalize(metric: ThresholdAlertMetric): Float {
        val normalized = normalizeNonNegativeDecimal(thresholds[metric] ?: "1.0")
        thresholds[metric] = normalized.text
        if (normalized.corrected) {
            showUvirBottomMessage(context, context.getString(R.string.value_out_of_limits_corrected))
        }
        return normalized.value
    }
    AlertDialog(
        onDismissRequest = onDismissRequest,
        modifier = scrollbar.dialogModifier,
        title = { Text(stringResource(R.string.conditional_editor_title)) },
        text = {
            Box(Modifier.fillMaxWidth().heightIn(max = 460.dp).then(scrollbar.viewportModifier)) {
                Column(Modifier.fillMaxWidth().verticalScroll(scrollbar.scrollState)) {
                    Text(stringResource(R.string.threshold_group_choose_value),
                        color = secondaryText, fontSize = 13.sp)
                    Spacer(Modifier.height(UvirSettingsControlGap))
                    metrics.forEach { metric ->
                        Row(
                            Modifier.fillMaxWidth()
                                .testTag("acquisition_condition_row_${metric.name}")
                                .clickable { focus.clearFocus(); enabled[metric] = enabled[metric] != true }
                                .padding(vertical = 10.dp, horizontal = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = enabled[metric] == true,
                                onCheckedChange = { checked -> focus.clearFocus(); enabled[metric] = checked },
                                modifier = Modifier.size(40.dp).testTag("acquisition_condition_check_${metric.name}")
                            )
                            ThresholdAlertMetricLabel(metric, primaryText, secondaryText,
                                enabled = true, modifier = Modifier.weight(1f))
                        }
                        if (enabled[metric] == true) {
                            Surface(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                shape = RoundedCornerShape(12.dp),
                                color = cardColor,
                                border = BorderStroke(1.dp, secondaryText.copy(alpha = 0.18f))
                            ) {
                                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    ThresholdConditionControls(
                                        selectedDirection = directions[metric] ?: ThresholdAlertDirection.ABOVE,
                                        onDirectionSelected = { focus.clearFocus(); directions[metric] = it },
                                        currentValueEnabled = currentSample != null,
                                        onUseCurrentValue = {
                                            focus.clearFocus()
                                            currentSample?.let { thresholds[metric] = thresholdEditableValue(it.thresholdMetricValue(metric)) }
                                        },
                                        cardColor = cardColor, primaryText = primaryText, secondaryText = secondaryText
                                    )
                                    DecimalField(
                                        value = thresholds[metric] ?: "1.0",
                                        onValueChange = { thresholds[metric] = it },
                                        label = stringResource(if (metric.name.startsWith("BIO_"))
                                            R.string.threshold_value_biological_label else R.string.threshold_value_label),
                                        onEditingComplete = { normalize(metric) },
                                        modifier = Modifier.fillMaxWidth().testTag("acquisition_condition_threshold_${metric.name}")
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                focus.clearFocus()
                onSave(metrics.map { metric ->
                    ThresholdAlertRule(metric, enabled[metric] == true,
                        directions[metric] ?: ThresholdAlertDirection.ABOVE, normalize(metric))
                })
            }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(R.string.cancel), color = UvirDestructiveActionColor)
            }
        },
        containerColor = cardColor,
        titleContentColor = primaryText,
        textContentColor = primaryText
    )
}
