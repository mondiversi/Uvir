package me.mondiversi.uvir

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** One row per displayed channel; presentation never writes or converts legacy settings. */
@Composable
internal fun UvirSensorCalibrationSettings(
    context: Context,
    enabled: Boolean,
    uvSensorAvailable: Boolean,
    visibleFactorText: String,
    onVisibleFactorTextChange: (String) -> Unit,
    uvFactorText: String,
    onUvFactorTextChange: (String) -> Unit,
    cardColor: Color,
    primaryText: Color,
    secondaryText: Color,
    offsets: CalibrationOffsets? = null,
    offsetsSupported: Boolean = false,
    onOffsetsChange: (CalibrationOffsets) -> Unit = {},
    channelFactors: CalibrationChannelFactors? = null,
    channelFactorsSupported: Boolean = false,
    onChannelFactorsChange: (CalibrationChannelFactors) -> Unit = {},
    sensorDeviceId: String = "",
    darkMeasurementSupported: Boolean = false,
    onMeasureDarkOffsets: (suspend (CalibrationGroup, (Int) -> Unit) -> DarkOffsetReading)? = null,
    onFirmwareUpdateRequired: (() -> Unit)? = null
) {
    val numericFormat = LocalUvirNumericFormat.current
    val unit = LocalUvirIrradianceUnit.current
    val visible = visibleFactorText.replace(',', '.').toFloatOrNull()
        ?.takeIf { it.isFinite() && it in .1f..10f } ?: 1f
    val uv = uvFactorText.replace(',', '.').toFloatOrNull()
        ?.takeIf { it.isFinite() && it in .1f..10f } ?: 1f
    val factors = channelFactors ?: CalibrationChannelFactors.fromModuleFactors(visible, uv)
    val colorOffsets = offsets?.forColorEditor() ?: CalibrationOffsets()
    val names = listOf("UVC", "UVB", "UVA", stringResource(R.string.violet), stringResource(R.string.blue),
        stringResource(R.string.green), stringResource(R.string.yellow), stringResource(R.string.orange),
        stringResource(R.string.red), stringResource(R.string.session_chart_series_far_red), "NIR")
    SettingsStaticIsland(
        title = stringResource(R.string.sensor_calibration_title), titleIcon = ConnectivityIconType.CALIBRATION,
        containerColor = cardColor, titleColor = primaryText, contentEnabled = enabled
    ) {
        SettingsPageDescription(stringResource(R.string.sensor_calibration_description), secondaryText,
            Modifier.testTag("calibration_description"))
        Text(stringResource(R.string.sensor_calibration_factor_hint,
            formatUvirNumber(MIN_SENSOR_CALIBRATION_FACTOR.toDouble(), 1, numericFormat, grouping = false),
            formatUvirNumber(MAX_SENSOR_CALIBRATION_FACTOR.toDouble(), 1, numericFormat, grouping = false),
            formatUvirNumber(DEFAULT_SENSOR_CALIBRATION_FACTOR.toDouble(), 1, numericFormat, grouping = false)),
            modifier = Modifier.testTag("calibration_factors_description"),
            color = secondaryText, fontSize = 11.sp, lineHeight = 16.sp)
        Text(stringResource(R.string.calibration_offsets_description,
            formatUvirNumber(0.0, 0, numericFormat),
            formatUvirNumber(unit.fromCanonicalWm2(MAX_CALIBRATION_OFFSET_W_M2.toDouble()), 0, numericFormat),
            unit.symbol),
            modifier = Modifier.testTag("calibration_offsets_description"),
            color = secondaryText, fontSize = 11.sp, lineHeight = 16.sp)
        if (offsets?.isLegacyPhysical == true && offsets.values.any { it != 0f })
            Text(stringResource(R.string.calibration_offsets_legacy_notice), color = secondaryText, fontSize = 11.sp)
        listOf(0 until 3, 3 until 11).forEachIndexed { group, indices ->
            if (group > 0) SettingsGroupDivider(secondaryText)
            Text(if (group == 0) stringResource(R.string.uv_radiation) + " · AS7331"
                else stringResource(R.string.visible_light) + " / " + stringResource(R.string.far_red_nir) + " · AS7343",
                color = primaryText, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            indices.forEach { index ->
                key(index) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            UvirCalibrationChannelDot(index, enabled && (index >= 3 || uvSensorAvailable),
                                Modifier.testTag("calibration_dot_$index"))
                            Spacer(Modifier.width(8.dp))
                            Text(names[index], Modifier.weight(1f), color = primaryText, fontSize = 12.sp)
                            SettingsChannelReference(calibrationOffsetReferences[index], secondaryText,
                                Modifier.testTag("calibration_reference_$index"))
                        }
                        Row(Modifier.fillMaxWidth().testTag("calibration_row_$index"),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            val channelAvailable = enabled && (index >= 3 || uvSensorAvailable)
                            UvirFirmwareGate(channelAvailable && !channelFactorsSupported,
                                names[index] + " · " + stringResource(R.string.calibration_factor_short),
                                "calibration_channel_factor_$index", onFirmwareUpdateRequired, Modifier.weight(1f)) {
                                CalibrationFactorField(context, factors.values[index], names[index],
                                    channelAvailable && channelFactorsSupported,
                                    Modifier.fillMaxWidth().testTag("calibration_channel_factor_$index")) {
                                    onChannelFactorsChange(factors.withChannel(index, it))
                                }
                            }
                            UvirFirmwareGate(channelAvailable && !offsetsSupported,
                                names[index] + " · " + stringResource(R.string.calibration_offset_short, unit.symbol),
                                "calibration_offset_$index", onFirmwareUpdateRequired, Modifier.weight(1f)) {
                                CalibrationOffsetField(context, colorOffsets.values[index], names[index],
                                    channelAvailable && offsetsSupported && offsets != null,
                                    Modifier.fillMaxWidth().testTag("calibration_offset_$index")) {
                                    onOffsetsChange(colorOffsets.withChannel(index, it))
                                }
                            }
                        }
                    }
                }
            }
            key(sensorDeviceId, group) {
                val calibrationGroup = if (group == 0) CalibrationGroup.UV else CalibrationGroup.VISIBLE
                UvirDarkOffsetControls(calibrationGroup,
                    enabled = enabled && channelFactorsSupported && offsetsSupported && offsets != null &&
                        (group != 0 || uvSensorAvailable),
                    supported = darkMeasurementSupported, names = names,
                    cardColor = cardColor, primaryText = primaryText, secondaryText = secondaryText,
                    measure = onMeasureDarkOffsets,
                    firmwareUpdateRequired = enabled && (group != 0 || uvSensorAvailable) &&
                        (!channelFactorsSupported || !offsetsSupported),
                    onFirmwareUpdateRequired = onFirmwareUpdateRequired,
                    onApply = { reading -> onOffsetsChange((offsets ?: CalibrationOffsets()).withDarkReading(reading)) },
                    onReset = {
                        val reset = SensorCalibrationSettings(visible, uv, offsets = offsets, channelFactors = channelFactors)
                            .resetGroup(calibrationGroup)
                        onChannelFactorsChange(reset.channelFactors!!)
                        onOffsetsChange(reset.offsets!!)
                        if (group == 0) onUvFactorTextChange("1.0") else onVisibleFactorTextChange("1.0")
                    })
            }
        }
    }
}

@Composable
private fun CalibrationFactorField(context: Context, current: Float, channel: String, enabled: Boolean,
    modifier: Modifier, onChange: (Float) -> Unit) {
    var text by remember { mutableStateOf(current.toString()) }
    var editing by remember { mutableStateOf(false) }
    val correctedText = stringResource(R.string.value_out_of_limits_corrected)
    val label = stringResource(R.string.calibration_factor_short)
    LaunchedEffect(current) { if (!editing) text = current.toString() }
    DecimalField(text, { text = it; editing = true }, label, enabled, modifier = modifier.semantics {
        contentDescription = "$channel · $label"
    }, onEditingComplete = {
        val normalized = normalizeBoundedDecimal(text, MIN_SENSOR_CALIBRATION_FACTOR, MAX_SENSOR_CALIBRATION_FACTOR)
        text = normalized.text
        editing = false
        if (normalized.corrected) showUvirBottomMessage(context, correctedText)
        if (normalized.value != current) onChange(normalized.value)
    })
}

@Composable
private fun CalibrationOffsetField(context: Context, current: Float, channel: String, enabled: Boolean,
    modifier: Modifier, onChange: (Float) -> Unit) {
    val unit = LocalUvirIrradianceUnit.current
    key(unit) {
        var text by remember { mutableStateOf(calibrationOffsetInput(current, unit)) }
        var editing by remember { mutableStateOf(false) }
        val correctedText = stringResource(R.string.value_out_of_limits_corrected)
        val label = stringResource(R.string.calibration_offset_short, unit.symbol)
        LaunchedEffect(current) { if (!editing) text = calibrationOffsetInput(current, unit) }
        DecimalField(text, { text = it; editing = true }, label, enabled, maxChars = 64,
            modifier = modifier.semantics { contentDescription = "$channel · $label" },
            onEditingComplete = {
                val parsed = calibrationOffsetFromInput(text, unit)
                text = calibrationOffsetInput(parsed ?: current, unit)
                editing = false
                if (parsed == null) showUvirBottomMessage(context, correctedText)
                else if (parsed != current) onChange(parsed)
            })
    }
}
