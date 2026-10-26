package me.mondiversi.uvir

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

@Composable
internal fun UvirOpticalWindowSettings(
    value: OpticalWindowSettings,
    onChange: (OpticalWindowSettings) -> Unit,
    enabled: Boolean,
    supported: Boolean,
    cardColor: Color,
    primaryText: Color,
    secondaryText: Color,
    sensorDeviceId: String = "",
    onFirmwareUpdateRequired: (() -> Unit)? = null,
    masterSupported: Boolean = supported,
    onMeasureWindow: (suspend (OpticalWindowMeasurementContext?, (Int) -> Unit) -> OpticalWindowReading)? = null
) {
    val context = LocalContext.current
    val commit = LocalSettingsCommit.current
    val numericFormat = LocalUvirNumericFormat.current
    val editorValue = value.forWindowEditor()
    val channelsSupported = supported && (editorValue.masterFactor == null || masterSupported)
    val editable = enabled && channelsSupported
    key(normalizeSensorDeviceId(sensorDeviceId)) {
        SettingsStaticIsland(title = stringResource(R.string.optical_window_title),
            titleIcon = ConnectivityIconType.CALIBRATION, containerColor = cardColor,
            titleColor = primaryText, contentEnabled = enabled && supported) {
            SettingsPageDescription(stringResource(R.string.optical_window_description), secondaryText)
            Text(stringResource(R.string.optical_window_factor_limits,
                formatUvirNumber(1.0, 1, numericFormat, grouping = false),
                formatUvirNumber(10.0, 1, numericFormat, grouping = false)),
                modifier = Modifier.testTag("optical_window_factor_limits"),
                color = secondaryText, fontSize = 11.sp, lineHeight = 16.sp)
            Column(verticalArrangement = Arrangement.spacedBy(UvirSettingsRelatedGap)) {
                UvirFirmwareGate(enabled && !masterSupported,
                    stringResource(R.string.optical_window_common_factor), "optical_common_factor", onFirmwareUpdateRequired) {
                    OpticalWindowFactorField(
                        factor = editorValue.masterMultiplier,
                        label = stringResource(R.string.optical_window_common_factor),
                        reference = "UV · VIS · NIR",
                        enabled = enabled && masterSupported,
                        secondaryText = secondaryText,
                        modifier = Modifier.testTag("optical_common_factor"),
                        master = true,
                        onFactorChange = { onChange(editorValue.withMasterFactor(it)) }
                    )
                }
                Text(stringResource(R.string.optical_window_hint,
                    formatUvirNumber(1.0, 1, numericFormat, grouping = false),
                    formatUvirNumber(1.0 / .9, 3, numericFormat, grouping = false)),
                    color = secondaryText, fontSize = 11.sp)
            }
            SettingsGroupDivider(secondaryText)
            Text(stringResource(R.string.optical_window_individual_factors),
                color = primaryText, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            val labels = listOf("UVC", "UVB", "UVA", stringResource(R.string.violet),
                stringResource(R.string.blue), stringResource(R.string.green),
                stringResource(R.string.yellow), stringResource(R.string.orange),
                stringResource(R.string.red), stringResource(R.string.session_chart_series_far_red), "NIR")
            labels.forEachIndexed { index, label ->
                if (index == 3) SettingsGroupDivider(secondaryText)
                key(index) {
                    UvirFirmwareGate(enabled && !channelsSupported,
                        stringResource(R.string.optical_window_channel_factor, label), "optical_channel_$index",
                        onFirmwareUpdateRequired) {
                        OpticalWindowFactorField(
                            factor = editorValue.factors[index],
                            label = stringResource(R.string.optical_window_channel_factor, label),
                            reference = opticalWindowChannelReferences[index],
                            enabled = editable,
                            secondaryText = secondaryText,
                            modifier = Modifier.testTag("optical_channel_$index"),
                            channelIndex = index,
                            onFactorChange = { onChange(editorValue.withChannelFactor(index, it)) }
                        )
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(UvirActionButtonGap)) {
            UvirFirmwareGate(enabled && !channelsSupported, stringResource(R.string.window_measure_calculate),
                "optical_window_calculate", onFirmwareUpdateRequired) {
                UvirOpticalWindowMeasurementControls(editorValue, editable, labels,
                    cardColor, primaryText, secondaryText, onMeasureWindow, onChange)
            }
            UvirFirmwareGate(enabled && !channelsSupported, stringResource(R.string.calibration_reset_values),
                "optical_window_reset", onFirmwareUpdateRequired) {
                UvirCalibrationResetButton(editable,
                    message = stringResource(R.string.optical_window_reset_confirmation_message),
                    cardColor = cardColor, primaryText = primaryText, secondaryText = secondaryText,
                    modifier = Modifier.testTag("optical_window_reset")) {
                    onChange(editorValue.resetMultipliers())
                    commit?.commit()
                }
            }
            }
        }
    }
}

/** Keep unfinished input stable across remote readback; never save from a render. */
@Composable
private fun OpticalWindowFactorField(
    factor: Float,
    label: String,
    reference: String,
    enabled: Boolean,
    secondaryText: Color,
    modifier: Modifier = Modifier,
    channelIndex: Int? = null,
    master: Boolean = false,
    onFactorChange: (Float) -> Unit
) {
    val context = LocalContext.current
    val correctedText = stringResource(R.string.value_out_of_limits_corrected)
    var text by remember { mutableStateOf(factor.toString()) }
    var edited by remember { mutableStateOf(false) }
    LaunchedEffect(factor) { if (!edited) text = factor.toString() }
    DecimalField(value = text, onValueChange = { text = it; edited = true }, label = label,
        modifier = modifier.fillMaxWidth(), enabled = enabled,
        labelLeadingContent = if (channelIndex == null) null else {
            { UvirCalibrationChannelDot(channelIndex, enabled,
                Modifier.testTag("optical_channel_dot_$channelIndex")) }
        },
        trailingContent = { SettingsChannelReference(reference, secondaryText) },
        onEditingComplete = {
            val normalized = normalizeBoundedDecimal(text, 1f, 10f)
            val committed = if (master) normalizeOpticalMasterFactor(normalized.value) else normalized.value
            text = committed.toString()
            edited = false
            if (normalized.corrected || committed != normalized.value) showUvirBottomMessage(context, correctedText)
            if (committed != factor) onFactorChange(committed)
        })
}
