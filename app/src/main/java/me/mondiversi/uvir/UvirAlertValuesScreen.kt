package me.mondiversi.uvir

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color

/** Alert thresholds share the editor layout, but never the acquisition rule data. */
@Composable
internal fun UvirAlertValuesScreen(
    rules: List<ThresholdAlertRule>,
    sample: SensorSample?,
    enabled: Boolean,
    cardColor: Color,
    primaryText: Color,
    secondaryText: Color,
    onSave: (List<ThresholdAlertRule>) -> Unit,
    onBack: () -> Unit
) {
    val metrics = remember { SensorGroup.entries.flatMap(::thresholdAlertMetricsForGroup) }
    UvirAcquisitionConditionsDialog(
        initialRules = rules,
        cardColor = cardColor,
        primaryText = primaryText,
        secondaryText = secondaryText,
        currentSample = sample,
        metrics = metrics,
        titleRes = R.string.alert_configure_values,
        controlsEnabled = enabled,
        onSave = onSave,
        onDismissRequest = onBack
    )
}
