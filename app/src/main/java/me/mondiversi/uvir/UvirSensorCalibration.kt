package me.mondiversi.uvir

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp

internal const val DEFAULT_SENSOR_CALIBRATION_FACTOR = 1f
internal const val MIN_SENSOR_CALIBRATION_FACTOR = 0.1f
internal const val MAX_SENSOR_CALIBRATION_FACTOR = 10f
private val SENSOR_CALIBRATION_MINIMUM_FIRMWARE = listOf(0, 5, 11)

data class SensorCalibrationSettings(
    val visibleFactor: Float = DEFAULT_SENSOR_CALIBRATION_FACTOR,
    val uvFactor: Float = DEFAULT_SENSOR_CALIBRATION_FACTOR
)

internal fun sensorCalibrationCommand(settings: SensorCalibrationSettings): String =
    "CALIBRATION_CONFIG " +
        "${settings.visibleFactor.coerceIn(MIN_SENSOR_CALIBRATION_FACTOR, MAX_SENSOR_CALIBRATION_FACTOR)} " +
        settings.uvFactor.coerceIn(MIN_SENSOR_CALIBRATION_FACTOR, MAX_SENSOR_CALIBRATION_FACTOR)

internal fun firmwareSupportsSensorCalibration(version: String): Boolean {
    val parts =
        version.substringBefore('-')
            .split('.')
            .mapNotNull(String::toIntOrNull)
    if (parts.size < 3) return false
    return (0..2)
        .map { parts.getOrElse(it) { 0 } }
        .zip(SENSOR_CALIBRATION_MINIMUM_FIRMWARE)
        .firstOrNull { (actual, required) -> actual != required }
        ?.let { (actual, required) -> actual > required }
        ?: true
}

@Composable
internal fun UvirSensorCalibrationSettings(
    context: Context,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    enabled: Boolean,
    onUnsupportedInteraction: () -> Unit,
    uvSensorAvailable: Boolean,
    visibleFactorText: String,
    onVisibleFactorTextChange: (String) -> Unit,
    uvFactorText: String,
    onUvFactorTextChange: (String) -> Unit,
    cardColor: Color,
    primaryText: Color,
    secondaryText: Color
) {
    val valueCorrectedText =
        stringResource(R.string.value_out_of_limits_corrected)
    SettingsSection(
        settingsPage = UvirSettingsPage.SENSOR_CALIBRATION,
        title = stringResource(R.string.sensor_calibration_title),
        titleIcon = ConnectivityIconType.CALIBRATION,
        expanded = expanded,
        enabled = enabled,
        onExpandedChange = { value ->
            if (!enabled && value) {
                onUnsupportedInteraction()
            }
            onExpandedChange(value)
            saveSettingsSectionExpanded(
                context,
                KEY_SETTINGS_SENSOR_CALIBRATION_EXPANDED,
                value
            )
        },
        containerColor = cardColor,
        titleColor = primaryText,
        chevronColor = secondaryText,
        dividerColor = secondaryText.copy(alpha = 0.28f)
    ) {
        androidx.compose.material3.Text(
            text = stringResource(R.string.sensor_calibration_description),
            color = secondaryText,
            fontSize = 12.sp,
            lineHeight = 16.sp
        )

        Column(
            verticalArrangement = Arrangement.spacedBy(UvirSettingsRelatedGap)
        ) {
            DecimalField(
                value = visibleFactorText,
                onValueChange = onVisibleFactorTextChange,
                label = stringResource(R.string.sensor_calibration_visible_factor),
                modifier = Modifier.fillMaxWidth(),
                enabled = enabled,
                onEditingComplete = {
                    val normalized =
                        normalizeBoundedDecimal(
                            visibleFactorText,
                            MIN_SENSOR_CALIBRATION_FACTOR,
                            MAX_SENSOR_CALIBRATION_FACTOR
                        )
                    onVisibleFactorTextChange(normalized.text)
                    if (normalized.corrected) {
                        showUvirBottomMessage(context, valueCorrectedText)
                    }
                }
            )
            androidx.compose.material3.Text(
                text = stringResource(R.string.sensor_calibration_factor_hint),
                color = secondaryText,
                fontSize = 11.sp
            )
        }

        Column(
            verticalArrangement = Arrangement.spacedBy(UvirSettingsRelatedGap)
        ) {
            DecimalField(
                value = uvFactorText,
                onValueChange = onUvFactorTextChange,
                label = stringResource(R.string.sensor_calibration_uv_factor),
                modifier = Modifier.fillMaxWidth(),
                enabled = enabled && uvSensorAvailable,
                onEditingComplete = {
                    val normalized =
                        normalizeBoundedDecimal(
                            uvFactorText,
                            MIN_SENSOR_CALIBRATION_FACTOR,
                            MAX_SENSOR_CALIBRATION_FACTOR
                        )
                    onUvFactorTextChange(normalized.text)
                    if (normalized.corrected) {
                        showUvirBottomMessage(context, valueCorrectedText)
                    }
                }
            )
            if (!uvSensorAvailable) {
                androidx.compose.material3.Text(
                    text = stringResource(R.string.sensor_calibration_uv_unavailable),
                    color = secondaryText,
                    fontSize = 11.sp
                )
            }
        }

    }
}
