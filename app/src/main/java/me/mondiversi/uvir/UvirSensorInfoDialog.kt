package me.mondiversi.uvir

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun UvirSensorInfoDialog(
    connectionMode: SensorConnectionMode,
    sensorInfo: UvirSensorRuntimeInfo,
    primaryText: Color,
    secondaryText: Color,
    cardColor: Color,
    onDismissRequest: () -> Unit
) {
    val dialogScrollbar =
        rememberUvirDialogScrollbar(
            color = secondaryText.copy(alpha = 0.58f)
        )
    val unavailable = stringResource(R.string.sensor_info_unavailable)
    val numericFormat = LocalUvirNumericFormat.current
    val powerEstimate =
        estimateUvirSensorPower(
            selectedConnectionMode = connectionMode,
            sensorInfo = sensorInfo
        )
    fun formatWhole(value: Number): String =
        formatUvirNumber(
            value = value.toDouble(),
            fractionDigits = 0,
            format = numericFormat
        )
    fun formatDuration(totalSeconds: Int): String {
        val safeSeconds = totalSeconds.coerceAtLeast(0)
        val hours = safeSeconds / 3_600
        val minutes = (safeSeconds % 3_600) / 60
        val seconds = safeSeconds % 60

        return buildList {
            if (hours > 0) add("${formatWhole(hours)} h")
            if (minutes > 0) add("${formatWhole(minutes)} min")
            if (seconds > 0 || isEmpty()) add("${formatWhole(seconds)} s")
        }.joinToString(" ")
    }
    val sensorNames =
        sensorInfo.sensorName
            .let { visibleSensor ->
                if (
                    sensorInfo.uvAvailable == true &&
                        !visibleSensor.contains("AS7331", ignoreCase = true)
                ) {
                    listOf(visibleSensor, "AS7331")
                        .filter { it.isNotBlank() }
                        .joinToString(" + ")
                } else {
                    visibleSensor
                }
            }
            .orUnavailable(unavailable)
    val source =
        stringResource(
            when (connectionMode) {
                SensorConnectionMode.USB -> R.string.sensor_connection_usb
                SensorConnectionMode.WIFI -> R.string.sensor_connection_wifi
                SensorConnectionMode.BLUETOOTH ->
                    R.string.sensor_connection_bluetooth
                SensorConnectionMode.INTERNET ->
                    R.string.sensor_connection_internet
            }
        )
    val signalPercentage =
        when (connectionMode) {
            SensorConnectionMode.WIFI ->
                sensorInfo.wifiRssiDbm?.let(::wifiSignalPercentage)

            SensorConnectionMode.BLUETOOTH ->
                sensorInfo.bluetoothRssiDelta?.let(
                    ::bluetoothSignalPercentage
                )

            SensorConnectionMode.INTERNET ->
                sensorInfo.wifiRssiDbm?.let(::wifiSignalPercentage)

            SensorConnectionMode.USB -> null
        }
    val signalDetails =
        when (connectionMode) {
            SensorConnectionMode.WIFI ->
                sensorInfo.wifiRssiDbm?.let {
                    "${formatWhole(it)} dBm"
                }

            SensorConnectionMode.BLUETOOTH ->
                sensorInfo.bluetoothRssiDelta?.let {
                    stringResource(
                        R.string.sensor_info_relative_signal,
                        formatWhole(it)
                    )
                }

            SensorConnectionMode.INTERNET ->
                sensorInfo.wifiRssiDbm?.let { "${formatWhole(it)} dBm" }

            SensorConnectionMode.USB -> null
        }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        modifier = dialogScrollbar.dialogModifier,
        title = {
            Text(stringResource(R.string.sensor_info_title))
        },
        text = {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 460.dp)
                        .then(dialogScrollbar.viewportModifier)
            ) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .verticalScroll(dialogScrollbar.scrollState),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    SensorInfoSection(
                        title = stringResource(R.string.sensor_info_device),
                        primaryText = primaryText,
                        secondaryText = secondaryText,
                        rows =
                            listOf(
                                stringResource(R.string.sensor_info_sensor) to
                                    sensorNames,
                                stringResource(R.string.sensor_info_board) to
                                    sensorInfo.boardName.orUnavailable(unavailable),
                                stringResource(R.string.sensor_info_chip) to
                                    sensorInfo.chipModel.orUnavailable(unavailable),
                                stringResource(R.string.sensor_info_cpu) to
                                    sensorInfo.cpuFrequencyMhz?.let { frequency ->
                                        val cores = sensorInfo.chipCores
                                        if (cores == null) {
                                            "$frequency MHz"
                                        } else {
                                            stringResource(
                                                R.string.sensor_info_cpu_value,
                                                frequency,
                                                cores
                                            )
                                        }
                                    }.orUnavailable(unavailable),
                                stringResource(R.string.sensor_info_firmware) to
                                    sensorInfo.firmwareVersion.orUnavailable(unavailable),
                                stringResource(R.string.sensor_info_identifier) to
                                    sensorInfo.deviceId.orUnavailable(unavailable)
                            )
                    )

                    SensorInfoSection(
                        title = stringResource(R.string.sensor_info_connection),
                        primaryText = primaryText,
                        secondaryText = secondaryText,
                        rows =
                            buildList {
                                add(
                                    stringResource(R.string.sensor_info_source) to
                                        source
                                )
                                if (signalPercentage != null) {
                                    add(
                                        stringResource(R.string.sensor_info_signal) to
                                            "${formatWhole(signalPercentage)}%${
                                                signalDetails?.let { " · $it" }.orEmpty()
                                            }"
                                    )
                                }
                                if (
                                    connectionMode == SensorConnectionMode.WIFI ||
                                    connectionMode == SensorConnectionMode.INTERNET
                                ) {
                                    add(
                                        stringResource(R.string.sensor_info_network) to
                                            sensorInfo.wifiNetwork.orUnavailable(unavailable)
                                    )
                                    add(
                                        stringResource(R.string.sensor_info_ip_address) to
                                            sensorInfo.wifiAddress.orUnavailable(unavailable)
                                    )
                                }
                                if (connectionMode == SensorConnectionMode.BLUETOOTH) {
                                    add(
                                        stringResource(R.string.sensor_info_device_name) to
                                            sensorInfo.bluetoothName.orUnavailable(unavailable)
                                    )
                                }
                                if (sensorInfo.internetBrokerHost.isNotBlank()) {
                                    add(
                                        stringResource(R.string.sensor_info_mqtt_broker) to
                                            sensorInfo.internetBrokerHost
                                    )
                                    add(
                                        stringResource(R.string.sensor_info_tls_port) to
                                            sensorInfo.internetBrokerPort?.let(::formatWhole)
                                                .orUnavailable(unavailable)
                                    )
                                    add(
                                        stringResource(R.string.sensor_info_mqtt_status) to
                                            stringResource(
                                                if (sensorInfo.internetBrokerConnected == true) {
                                                    R.string.sensor_info_connected
                                                } else {
                                                    R.string.sensor_info_not_connected
                                                }
                                            )
                                    )
                                }
                            }
                    )

                    SensorInfoGroupedSection(
                        title = stringResource(R.string.sensor_info_measurement),
                        primaryText = primaryText,
                        secondaryText = secondaryText,
                        nestedColor = cardColor,
                        groups =
                            listOf(
                                SensorInfoGroup(
                                    title = stringResource(
                                        R.string.sensor_info_sensors_and_calibration
                                    ),
                                    rows =
                                        buildList {
                                            add(
                                                stringResource(R.string.sensor_info_visible_sensor) to
                                                    sensorInfo.sensorAvailable.statusText()
                                            )
                                            add(
                                                stringResource(R.string.sensor_info_uv_sensor) to
                                                    sensorInfo.uvAvailable.statusText()
                                            )
                                            add(
                                                stringResource(R.string.sensor_info_unit) to
                                                    sensorInfo.unit.displayUnit(unavailable)
                                            )
                                            add(
                                                stringResource(R.string.sensor_info_calibration) to
                                                    sensorInfo.calibrationKind.calibrationText(unavailable)
                                            )
                                            sensorInfo.visibleCalibrationFactor?.let { factor ->
                                                add(
                                                    stringResource(
                                                        R.string.sensor_info_visible_calibration_factor
                                                    ) to formatUvirNumber(factor, 3, numericFormat)
                                                )
                                            }
                                            sensorInfo.uvCalibrationFactor?.let { factor ->
                                                add(
                                                    stringResource(
                                                        R.string.sensor_info_uv_calibration_factor
                                                    ) to formatUvirNumber(factor, 3, numericFormat)
                                                )
                                            }
                                        }
                                ),
                                SensorInfoGroup(
                                    title = stringResource(R.string.sensor_info_sampling),
                                    rows =
                                        listOf(
                                            stringResource(R.string.sensor_info_stream_interval) to
                                                sensorInfo.streamIntervalMs?.let {
                                                    "${formatWhole(it)} ms"
                                                }.orUnavailable(unavailable),
                                            stringResource(R.string.sensor_info_integration) to
                                                sensorInfo.integrationMs?.let {
                                                    "${formatUvirNumber(it, 1, numericFormat)} ms"
                                                }.orUnavailable(unavailable),
                                            stringResource(R.string.sensor_info_gain) to
                                                sensorInfo.gain?.let {
                                                    "${formatUvirNumber(it, 1, numericFormat)}×"
                                                }.orUnavailable(unavailable),
                                            stringResource(R.string.sensor_info_samples_sent) to
                                                sensorInfo.sampleSequence?.let(::formatWhole)
                                                    .orUnavailable(unavailable)
                                        )
                                ),
                                SensorInfoGroup(
                                    title = stringResource(R.string.sensor_info_synchronization),
                                    rows =
                                        buildList {
                                            sensorInfo.autonomousRecordingEnabled?.let { enabled ->
                                                add(
                                                    stringResource(
                                                        R.string.sensor_autonomous_recording
                                                    ) to enabled.statusText()
                                                )
                                            }
                                            sensorInfo.automaticShutdownEnabled?.let { enabled ->
                                                add(
                                                    stringResource(
                                                        R.string.sensor_automatic_shutdown
                                                    ) to enabled.statusText()
                                                )
                                            }
                                            if (sensorInfo.automaticShutdownEnabled == true) {
                                                sensorInfo.automaticShutdownSeconds?.let { seconds ->
                                                    add(
                                                        stringResource(
                                                            R.string.sensor_automatic_shutdown_after
                                                        ) to formatDuration(seconds)
                                                    )
                                                }
                                            }
                                            sensorInfo.offlineAlertRepeatSeconds?.let { seconds ->
                                                add(
                                                    stringResource(
                                                        R.string.sensor_info_offline_alert_interval
                                                    ) to formatDuration(seconds)
                                                )
                                            }
                                            add(
                                                stringResource(R.string.sensor_pending_acquisitions) to
                                                    formatWhole(sensorInfo.offlineAcquisitions ?: 0)
                                            )
                                            add(
                                                stringResource(R.string.sensor_pending_alerts) to
                                                    formatWhole(sensorInfo.offlineAlerts ?: 0)
                                            )
                                            add(
                                                stringResource(R.string.sensor_pending_errors) to
                                                    formatWhole(sensorInfo.offlineErrors ?: 0)
                                            )
                                        }
                                )
                            )
                    )

                    SensorInfoGroupedSection(
                        title = stringResource(R.string.sensor_info_scale_sensitivity),
                        primaryText = primaryText,
                        secondaryText = secondaryText,
                        nestedColor = cardColor,
                        groups =
                            listOf(
                                SensorInfoGroup(
                                    title = stringResource(R.string.sensor_info_display_scale),
                                    rows =
                                        listOf(
                                            stringResource(R.string.sensor_info_selected_unit) to
                                                LocalUvirIrradianceUnit.current.symbol,
                                            stringResource(R.string.sensor_info_scale_equivalence) to
                                                "1 W/m² = 0.1 mW/cm² = 100 µW/cm²"
                                        )
                                )
                            )
                    )

                    SensorInfoGroupedSection(
                        title = stringResource(R.string.sensor_info_memory),
                        primaryText = primaryText,
                        secondaryText = secondaryText,
                        nestedColor = cardColor,
                        groups =
                            listOf(
                                SensorInfoGroup(
                                    title = stringResource(R.string.sensor_info_flash_memory),
                                    rows =
                                        listOf(
                                            stringResource(R.string.sensor_info_flash_capacity) to
                                                sensorInfo.flashSizeBytes.memoryText(
                                                    unavailable,
                                                    numericFormat
                                                ),
                                            stringResource(R.string.sensor_info_program_capacity) to
                                                sensorInfo.appPartitionSizeBytes.memoryText(
                                                    unavailable,
                                                    numericFormat
                                                ),
                                            stringResource(R.string.sensor_info_firmware_storage) to
                                                sensorInfo.firmwareSizeBytes.memoryTextWithPercentage(
                                                    total = sensorInfo.appPartitionSizeBytes,
                                                    unavailable = unavailable,
                                                    numericFormat = numericFormat
                                                ),
                                            stringResource(R.string.sensor_info_program_space_free) to
                                                sensorInfo.freeAppPartitionBytes.memoryTextWithPercentage(
                                                    total = sensorInfo.appPartitionSizeBytes,
                                                    unavailable = unavailable,
                                                    numericFormat = numericFormat
                                                )
                                        )
                                ),
                                SensorInfoGroup(
                                    title = stringResource(R.string.sensor_info_ram_memory),
                                    rows =
                                        listOf(
                                            stringResource(R.string.sensor_info_ram_capacity) to
                                                sensorInfo.heapSizeBytes.memoryText(
                                                    unavailable,
                                                    numericFormat
                                                ),
                                            stringResource(R.string.sensor_info_ram_used) to
                                                differenceBytes(
                                                    total = sensorInfo.heapSizeBytes,
                                                    subtract = sensorInfo.freeHeapBytes
                                                ).memoryTextWithPercentage(
                                                    total = sensorInfo.heapSizeBytes,
                                                    unavailable = unavailable,
                                                    numericFormat = numericFormat
                                                ),
                                            stringResource(R.string.sensor_info_ram_free) to
                                                sensorInfo.freeHeapBytes.memoryTextWithPercentage(
                                                    total = sensorInfo.heapSizeBytes,
                                                    unavailable = unavailable,
                                                    numericFormat = numericFormat
                                                )
                                        )
                                ),
                                SensorInfoGroup(
                                    title = stringResource(R.string.sensor_info_offline_storage),
                                    rows =
                                        listOf(
                                            stringResource(R.string.sensor_info_offline_storage_capacity) to
                                                sensorInfo.filesystemTotalBytes.memoryText(
                                                    unavailable,
                                                    numericFormat
                                                ),
                                            stringResource(R.string.sensor_info_offline_storage_used) to
                                                sensorInfo.filesystemUsedBytes.memoryTextWithPercentage(
                                                    total = sensorInfo.filesystemTotalBytes,
                                                    unavailable = unavailable,
                                                    numericFormat = numericFormat
                                                ),
                                            stringResource(R.string.sensor_info_offline_storage_free) to
                                                differenceBytes(
                                                    total = sensorInfo.filesystemTotalBytes,
                                                    subtract = sensorInfo.filesystemUsedBytes
                                                ).memoryTextWithPercentage(
                                                    total = sensorInfo.filesystemTotalBytes,
                                                    unavailable = unavailable,
                                                    numericFormat = numericFormat
                                                )
                                        )
                                )
                            )
                    )

                    SensorInfoGroupedSection(
                        title = stringResource(
                            R.string.sensor_info_power_consumption
                        ),
                        primaryText = primaryText,
                        secondaryText = secondaryText,
                        nestedColor = cardColor,
                        groups =
                            listOf(
                                SensorInfoGroup(
                                    title = stringResource(
                                        R.string.sensor_info_power_current_estimate
                                    ),
                                    rows =
                                        listOf(
                                            stringResource(
                                                R.string.sensor_info_estimated_current
                                            ) to powerEstimate.currentMilliAmps
                                                .approximateUnitText(
                                                    unit = "mA",
                                                    numericFormat = numericFormat
                                                ),
                                            stringResource(
                                                R.string.sensor_info_estimated_power
                                            ) to powerEstimate.powerMilliWatts
                                                .approximatePowerText(numericFormat),
                                            stringResource(
                                                R.string.sensor_info_energy_one_hour
                                            ) to powerEstimate
                                                .energyForOneHourMilliWattHours
                                                .approximateEnergyText(numericFormat)
                                        )
                                ),
                                SensorInfoGroup(
                                    title = stringResource(
                                        R.string.sensor_info_power_operating_range
                                    ),
                                    rows =
                                        listOf(
                                            stringResource(
                                                R.string.sensor_info_minimum_current
                                            ) to powerEstimate.minimumCurrentMilliAmps
                                                .approximateUnitText(
                                                    unit = "mA",
                                                    numericFormat = numericFormat
                                                ),
                                            stringResource(
                                                R.string.sensor_info_minimum_power
                                            ) to powerEstimate.minimumPowerMilliWatts
                                                .approximatePowerText(numericFormat),
                                            stringResource(
                                                R.string.sensor_info_peak_current
                                            ) to powerEstimate.peakCurrentMilliAmps
                                                .approximateUnitText(
                                                    unit = "mA",
                                                    numericFormat = numericFormat
                                                ),
                                            stringResource(
                                                R.string.sensor_info_peak_power
                                            ) to powerEstimate.peakPowerMilliWatts
                                                .approximatePowerText(numericFormat),
                                            stringResource(
                                                R.string.sensor_info_reference_voltage
                                            ) to "${formatUvirNumber(
                                                powerEstimate.referenceVoltageVolts,
                                                1,
                                                numericFormat
                                            )} V (USB/VIN)"
                                        )
                                )
                            ),
                        footer = stringResource(
                            R.string.sensor_info_power_estimate_note
                        )
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(R.string.close))
            }
        },
        containerColor = cardColor,
        titleContentColor = primaryText,
        textContentColor = primaryText
    )
}

private data class SensorInfoGroup(
    val title: String,
    val rows: List<Pair<String, String>>,
    val paragraphLayout: Boolean = false
)

@Composable
private fun SensorInfoGroupedSection(
    title: String,
    primaryText: Color,
    secondaryText: Color,
    nestedColor: Color,
    groups: List<SensorInfoGroup>,
    footer: String? = null
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = primaryText.copy(alpha = 0.055f)
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                modifier = Modifier.fillMaxWidth(),
                color = primaryText,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )

            groups.forEach { group ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    color = nestedColor
                ) {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        Text(
                            text = group.title,
                            modifier = Modifier.fillMaxWidth(),
                            color = primaryText,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        SensorInfoRows(
                            rows = group.rows,
                            primaryText = primaryText,
                            secondaryText = secondaryText,
                            paragraphLayout = group.paragraphLayout
                        )
                    }
                }
            }

            footer?.let { text ->
                Text(
                    text = text,
                    modifier = Modifier.fillMaxWidth(),
                    color = secondaryText,
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )
            }
        }
    }
}

@Composable
private fun SensorInfoSection(
    title: String,
    primaryText: Color,
    secondaryText: Color,
    rows: List<Pair<String, String>>
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = primaryText.copy(alpha = 0.055f)
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                modifier = Modifier.fillMaxWidth(),
                color = primaryText,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
            SensorInfoRows(
                rows = rows,
                primaryText = primaryText,
                secondaryText = secondaryText
            )
        }
    }
}

@Composable
private fun SensorInfoRows(
    rows: List<Pair<String, String>>,
    primaryText: Color,
    secondaryText: Color,
    paragraphLayout: Boolean = false
) {
    rows.forEach { (label, value) ->
        if (paragraphLayout) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = label,
                    modifier = Modifier.fillMaxWidth(),
                    color = secondaryText,
                    fontSize = 11.sp,
                    lineHeight = 14.sp
                )
                Text(
                    text = value,
                    modifier = Modifier.fillMaxWidth(),
                    color = primaryText,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
            Text(
                text = label,
                modifier = Modifier.weight(0.60f),
                color = secondaryText,
                fontSize = 12.sp
            )
            Row(
                modifier = Modifier.weight(0.40f),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = value,
                    color = primaryText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.End
                )
            }
            }
        }
    }
}

private fun String?.orUnavailable(unavailable: String): String =
    if (isNullOrBlank()) unavailable else this

private fun differenceBytes(total: Long?, subtract: Long?): Long? =
    if (total == null || subtract == null) {
        null
    } else {
        (total - subtract).coerceAtLeast(0L)
    }

private fun Long?.memoryText(
    unavailable: String,
    numericFormat: UvirNumericFormat
): String =
    this?.let { bytes ->
        if (bytes >= 1024L * 1024L) {
            "${formatUvirNumber(bytes / (1024.0 * 1024.0), 2, numericFormat)} MB"
        } else {
            "${formatUvirNumber(bytes / 1024.0, 1, numericFormat)} KB"
        }
    } ?: unavailable

private fun Long?.memoryTextWithPercentage(
    total: Long?,
    unavailable: String,
    numericFormat: UvirNumericFormat
): String {
    val memory = memoryText(unavailable, numericFormat)
    if (this == null || total == null || total <= 0L) return memory

    val percentage =
        (toDouble() * 100.0 / total.toDouble())
            .coerceIn(0.0, 100.0)
    return "$memory · ${formatUvirNumber(percentage, 0, numericFormat)}%"
}

private fun Double.approximateUnitText(
    unit: String,
    numericFormat: UvirNumericFormat
): String =
    "≈ ${formatUvirNumber(this, 0, numericFormat)} $unit"

private fun Double.approximatePowerText(
    numericFormat: UvirNumericFormat
): String =
    if (this >= 1_000.0) {
        "≈ ${formatUvirNumber(this / 1_000.0, 2, numericFormat)} W"
    } else {
        approximateUnitText("mW", numericFormat)
    }

private fun Double.approximateEnergyText(
    numericFormat: UvirNumericFormat
): String =
    if (this >= 1_000.0) {
        "≈ ${formatUvirNumber(this / 1_000.0, 2, numericFormat)} Wh"
    } else {
        approximateUnitText("mWh", numericFormat)
    }

@Composable
private fun Boolean?.statusText(): String =
    stringResource(
        when (this) {
            true -> R.string.sensor_info_detected
            false -> R.string.sensor_info_not_detected
            null -> R.string.sensor_info_unavailable
        }
    )

@Composable
private fun String.displayUnit(unavailable: String): String =
    when (lowercase()) {
        "uw/cm2", "µw/cm²" -> "µW/cm²"
        else -> orUnavailable(unavailable)
    }

@Composable
private fun String.calibrationText(unavailable: String): String =
    when {
        contains("estimate", ignoreCase = true) ->
            stringResource(R.string.sensor_info_datasheet_estimate)
        contains("user_adjusted", ignoreCase = true) ->
            stringResource(R.string.sensor_info_user_adjusted_calibration)
        else -> orUnavailable(unavailable)
    }
