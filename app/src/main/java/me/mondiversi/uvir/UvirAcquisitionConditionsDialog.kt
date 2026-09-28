package me.mondiversi.uvir

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
    metrics: List<ThresholdAlertMetric> = acquisitionConditionMetrics(),
    titleRes: Int = R.string.conditional_editor_title,
    descriptionRes: Int = R.string.threshold_group_choose_value,
    controlsEnabled: Boolean = true,
    onSave: (List<ThresholdAlertRule>) -> Unit,
    onDismissRequest: () -> Unit
) {
    val context = LocalContext.current
    val resources = androidx.compose.ui.platform.LocalResources.current
    val irradianceUnit = LocalUvirIrradianceUnit.current
    val focus = LocalFocusManager.current
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
                thresholdEditableValue(
                    irradianceUnit.fromCanonicalUwCm2(
                        (initialRules.firstOrNull { it.metric == metric }?.threshold ?: 1f).toDouble()
                    )
                )) }
        }
    }
    fun setConditionEnabled(metric: ThresholdAlertMetric, value: Boolean) {
        focus.clearFocus()
        if (enabled[metric] != value) {
            enabled[metric] = value
        }
    }
    fun normalize(metric: ThresholdAlertMetric): Float {
        val normalized = normalizeNonNegativeDecimal(thresholds[metric] ?: "1.0")
        thresholds[metric] = normalized.text
        if (normalized.corrected) {
            showUvirBottomMessage(context, resources.getString(R.string.value_out_of_limits_corrected))
        }
        return irradianceUnit.toCanonicalUwCm2(normalized.value.toDouble()).toFloat()
    }
    UvirAlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(titleRes)) },
        text = {
            Box(Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth()) {
                    Text(stringResource(descriptionRes),
                        color = secondaryText, fontSize = 13.sp)
                    Spacer(Modifier.height(UvirSettingsControlGap))
                    metrics.forEach { metric ->
                        Row(
                            Modifier.fillMaxWidth()
                                .testTag("acquisition_condition_row_${metric.name}")
                                .clickable(enabled = controlsEnabled) {
                                    setConditionEnabled(metric, enabled[metric] != true)
                                }
                                .padding(vertical = 10.dp, horizontal = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = enabled[metric] == true,
                                onCheckedChange = { checked -> setConditionEnabled(metric, checked) },
                                enabled = controlsEnabled,
                                modifier = Modifier.size(40.dp).testTag("acquisition_condition_check_${metric.name}")
                            )
                            ThresholdAlertMetricLabel(metric, primaryText, secondaryText,
                                enabled = controlsEnabled, modifier = Modifier.weight(1f))
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
                                            currentSample?.let {
                                                thresholds[metric] = thresholdEditableValue(
                                                    irradianceUnit.fromCanonicalUwCm2(
                                                        it.thresholdMetricValue(metric)
                                                    )
                                                )
                                            }
                                        },
                                        cardColor = cardColor, primaryText = primaryText, secondaryText = secondaryText,
                                        enabled = controlsEnabled
                                    )
                                    DecimalField(
                                        value = thresholds[metric] ?: "1.0",
                                        onValueChange = { thresholds[metric] = it },
                                        label = stringResource(if (metric.name.startsWith("BIO_"))
                                            R.string.threshold_value_biological_label else R.string.threshold_value_label)
                                            .withUvirIrradianceUnit(irradianceUnit),
                                        onEditingComplete = { normalize(metric) },
                                        enabled = controlsEnabled,
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
            TextButton(enabled = controlsEnabled, onClick = {
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
