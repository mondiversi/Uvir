package me.mondiversi.uvir

import android.content.Context
import android.content.res.Resources
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import java.util.Date
import java.util.TimeZone
import java.text.SimpleDateFormat

private const val UvirVisibleSensorIdentity = "AS7343 · I²C 0x39"
private const val UvirUvSensorIdentity = "AS7331 · I²C 0x74"
private const val UvirRtcIdentity = "DS3231 · I²C 0x68"
private const val UvirMicroSdAdapterIdentity =
    "SN74HC125 + 1117C33 · SPI CS GPIO13"
private const val UvirStatusLedIdentity = "RGB R/G GPIO25/26 · LED B GPIO27"
private const val UvirStatusBuzzerIdentity = "1× piezo · GPIO32"
private const val UvirExternalInputIdentity = "LOW · GPIO33/GND"

@Composable
internal fun UvirSensorInfoScreen(
    connectionMode: SensorConnectionMode,
    sensorInfo: UvirSensorRuntimeInfo,
    backgroundColor: Color,
    primaryText: Color,
    secondaryText: Color,
    cardColor: Color,
    onDismissRequest: () -> Unit
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val listState = rememberLazyListState()
    var showExportDialog by rememberSaveable {
        mutableStateOf(false)
    }
    val unavailable = stringResource(R.string.sensor_info_unavailable)
    val numericFormat = LocalUvirNumericFormat.current
    val irradianceUnit = LocalUvirIrradianceUnit.current
    val dateFormat = LocalUvirDateFormat.current
    val timeFormat = LocalUvirTimeFormat.current
    val lastConnectionAtMs =
        UvirSensorConnectionHistory.lastConnectedAt(context, sensorInfo.deviceId, connectionMode)
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
    val connectionStatusRows =
        buildList {
            add(stringResource(R.string.sensor_info_source) to source)
            add(stringResource(R.string.sensor_info_last_connection) to
                lastConnectionAtMs?.let {
                    formatUvirDateTime(it, dateFormat, Locale.getDefault(), timeFormat = timeFormat)
                }.orUnavailable(unavailable))
            if (signalPercentage != null) {
                add(
                    stringResource(R.string.sensor_info_signal) to
                        "${formatWhole(signalPercentage)}%${
                            signalDetails?.let { " · $it" }.orEmpty()
                        }"
                )
            }
        }
    val connectionDetailRows =
        buildList {
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
    val clockRows =
        listOf(
            stringResource(R.string.sensor_info_clock_status) to
                when (sensorInfo.rtcValid) {
                    true -> stringResource(R.string.sensor_info_valid)
                    false -> stringResource(R.string.sensor_info_invalid)
                    null -> unavailable
                },
            stringResource(R.string.sensor_info_time_source) to
                when (sensorInfo.timeSource.lowercase(Locale.ROOT)) {
                    "rtc" -> "RTC"
                    "phone" -> stringResource(R.string.sensor_info_time_source_phone)
                    else -> unavailable
                },
            stringResource(R.string.sensor_info_rtc_time) to
                sensorInfo.rtcCurrentTimeMs
                    ?.takeIf { it > 0L }
                    ?.let {
                        formatUvirDateTime(
                            timestamp = it,
                            format = dateFormat,
                            locale = Locale.getDefault(),
                            timeFormat = timeFormat
                        )
                    }
                    .orUnavailable(unavailable)
        )

    if (showExportDialog) {
        UvirSaveOrShareDialog(
            cardColor = cardColor,
            primaryText = primaryText,
            secondaryText = secondaryText,
            title = stringResource(R.string.export_sensor_info_dialog_title),
            description = stringResource(R.string.export_sensor_info_dialog_description),
            fileFormat = UvirExportFileFormat.TXT,
            onDismiss = {
                showExportDialog = false
            },
            onExport = { destination ->
                runCatching {
                    val exportFormatting =
                        uvirExportFormatting(
                            context = context,
                            mode = UvirExportMode.INTERNATIONAL
                        )
                    val report =
                        formatUvirSensorInformation(
                            resources = exportFormatting.context.resources,
                            connectionMode = connectionMode,
                            sensorInfo = sensorInfo,
                            numericFormat = exportFormatting.numericFormat,
                            irradianceUnit = exportFormatting.irradianceUnit,
                            lastConnectionAtMs = lastConnectionAtMs
                        )
                    val file =
                        prepareUvirTextExport(
                            context = context,
                            fileName =
                                uvirSensorInformationExportFileName(
                                    sensorInfo.deviceId
                                ),
                            text = report,
                        )
                    deliverUvirExportFiles(
                        context = context,
                        files = listOf(file),
                        destination = destination,
                        chooserTitle =
                            resources.getString(R.string.sensor_info_title)
                    )
                }.onFailure { error ->
                    UvirErrorLog.record(context, "export_sensor_information", error)
                    showUvirBottomMessage(
                        context,
                        resources.getString(R.string.save_error),
                        longDuration = false
                    )
                }
                showExportDialog = false
            }
        )
    }

    UvirFullScreenPage(
        onDismissRequest = onDismissRequest,
        title = {
            UvirMenuTitle(
                text = stringResource(R.string.sensor_info_title),
                color = primaryText
            )
        },
        containerColor = backgroundColor,
        contentColor = primaryText,
        lazyListState = listState,
        scrollbarColor = secondaryText.copy(alpha = 0.46f),
        topActionButton = {
            UvirTitleActionButton(
                iconColor = MaterialTheme.colorScheme.primary,
                contentDescription = resources.getString(R.string.export),
                onClick = {
                    showExportDialog = true
                },
                modifier =
                    Modifier
                        .size(UvirTitleActionButtonSize)
            ) {
                UvirTitleActionIcon(
                    type = MenuIconType.EXPORT,
                    modifier = Modifier.size(UvirTitleActionIconSize),
                    tint = LocalContentColor.current
                )
            }
        },
        text = {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(UvirIslandSpacing)
            ) {
                item(key = "device") {
                    SensorInfoGroupedSection(
                        title = stringResource(R.string.sensor_info_device),
                        primaryText = primaryText,
                        secondaryText = secondaryText,
                        nestedColor = backgroundColor,
                        groups = uvirSensorDeviceInfoGroups(resources, sensorInfo, clockRows)
                    )
                }

                item(key = "connection") {
                    SensorInfoGroupedSection(
                        title = stringResource(R.string.sensor_info_connection),
                        primaryText = primaryText,
                        secondaryText = secondaryText,
                        nestedColor = backgroundColor,
                        groups =
                            buildList {
                                add(
                                    SensorInfoGroup(
                                        title = stringResource(R.string.sensor_info_status),
                                        rows = connectionStatusRows
                                    )
                                )
                                if (connectionDetailRows.isNotEmpty()) {
                                    add(
                                        SensorInfoGroup(
                                            title = stringResource(R.string.sensor_info_network),
                                            rows = connectionDetailRows
                                        )
                                    )
                                }
                            }
                    )
                }

                item(key = "measurement") {
                    SensorInfoGroupedSection(
                        title = stringResource(R.string.sensor_info_measurement),
                        primaryText = primaryText,
                        secondaryText = secondaryText,
                        nestedColor = backgroundColor,
                        groups =
                            listOf(
                                SensorInfoGroup(
                                    title = stringResource(
                                        R.string.sensor_info_calibration
                                    ),
                                    rows =
                                        buildList {
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
                }

                item(key = "scale") {
                    SensorInfoGroupedSection(
                        title = stringResource(R.string.sensor_info_scale_sensitivity),
                        primaryText = primaryText,
                        secondaryText = secondaryText,
                        nestedColor = backgroundColor,
                        groups =
                            listOf(
                                SensorInfoGroup(
                                    title = stringResource(R.string.sensor_info_display_scale),
                                    rows =
                                        listOf(
                                            stringResource(R.string.sensor_info_selected_unit) to
                                                irradianceUnit.symbol,
                                            stringResource(R.string.sensor_info_scale_equivalence) to
                                                "1 W/m² = 0.1 mW/cm² = 100 µW/cm²"
                                        )
                                )
                            )
                    )
                }

                item(key = "memory") {
                    SensorInfoGroupedSection(
                        title = stringResource(R.string.sensor_info_memory),
                        primaryText = primaryText,
                        secondaryText = secondaryText,
                        nestedColor = backgroundColor,
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
                                    title = "FRAM",
                                    rows =
                                        listOf(
                                            stringResource(R.string.sensor_info_status) to
                                                sensorInfo.framAvailable.statusText(),
                                            stringResource(R.string.sensor_info_offline_storage_capacity) to
                                                sensorInfo.framCapacityBytes.memoryText(
                                                    unavailable,
                                                    numericFormat
                                                ),
                                            stringResource(R.string.sensor_info_record_capacity_total) to
                                                sensorInfo.framRecordCapacity
                                                    ?.let(::formatWhole)
                                                    .orUnavailable(unavailable),
                                            stringResource(R.string.sensor_info_pending_records) to
                                                sensorInfo.framRecordsUsed
                                                    ?.let(::formatWhole)
                                                    .orUnavailable(unavailable),
                                            stringResource(R.string.sensor_info_record_capacity_free) to
                                                sensorInfo.framRecordCapacity?.let { capacity ->
                                                    sensorInfo.framRecordsUsed?.let { used ->
                                                        formatWhole((capacity - used).coerceAtLeast(0))
                                                    }
                                                }.orUnavailable(unavailable)
                                        )
                                ),
                                SensorInfoGroup(
                                    title = stringResource(R.string.sensor_info_micro_sd),
                                    rows =
                                        listOf(
                                            stringResource(R.string.sensor_info_micro_sd_status) to
                                                sensorInfo.sdAvailable.statusText(),
                                            stringResource(R.string.sensor_info_storage_backend) to
                                                sensorInfo.storageBackend.storageBackendText(unavailable),
                                            stringResource(R.string.sensor_info_micro_sd_type) to
                                                sensorInfo.sdType.orUnavailable(unavailable),
                                            stringResource(R.string.sensor_info_file_system) to
                                                if (sensorInfo.sdAvailable == true) "FAT32" else unavailable,
                                            stringResource(R.string.sensor_info_offline_storage_capacity) to
                                                sensorInfo.sdTotalBytes.memoryText(
                                                    unavailable,
                                                    numericFormat
                                                ),
                                            stringResource(R.string.sensor_info_offline_storage_used) to
                                                sensorInfo.sdUsedBytes.memoryTextWithPercentage(
                                                    total = sensorInfo.sdTotalBytes,
                                                    unavailable = unavailable,
                                                    numericFormat = numericFormat
                                                ),
                                            stringResource(R.string.sensor_info_offline_storage_free) to
                                                sensorInfo.sdFreeBytes.memoryTextWithPercentage(
                                                    total = sensorInfo.sdTotalBytes,
                                                    unavailable = unavailable,
                                                    numericFormat = numericFormat
                                                ),
                                            stringResource(R.string.sensor_info_record_capacity_total) to
                                                sensorInfo.sdRecordCapacityTotal
                                                    ?.let(::formatWhole)
                                                    .orUnavailable(unavailable),
                                            stringResource(R.string.sensor_info_record_capacity_free) to
                                                sensorInfo.sdRecordCapacityFree
                                                    ?.let(::formatWhole)
                                                    .orUnavailable(unavailable),
                                            stringResource(R.string.sensor_info_record_size) to
                                                sensorInfo.storageRecordSizeBytes
                                                    ?.let { "${formatWhole(it)} B" }
                                                    .orUnavailable(unavailable),
                                            stringResource(R.string.sensor_info_pending_records) to
                                                sensorInfo.offlineUsed
                                                    ?.let(::formatWhole)
                                                    .orUnavailable(unavailable)
                                        )
                                )
                            )
                    )
                }

                item(key = "power") {
                    SensorInfoGroupedSection(
                        title = stringResource(
                            R.string.sensor_info_power_consumption
                        ),
                        primaryText = primaryText,
                        secondaryText = secondaryText,
                        nestedColor = backgroundColor,
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
        }
    )
}

internal data class SensorInfoGroup(
    val title: String,
    val rows: List<Pair<String, String>>,
    val paragraphLayout: Boolean = false
)

/** Same device categories for the screen and its report, without duplicate sensor models. */
internal fun uvirSensorDeviceInfoGroups(
    resources: Resources,
    sensorInfo: UvirSensorRuntimeInfo,
    clockRows: List<Pair<String, String>>
): List<SensorInfoGroup> {
    val unavailable = resources.getString(R.string.sensor_info_unavailable)
    fun status(value: Boolean?): String = resources.getString(
        when (value) {
            true -> R.string.sensor_info_detected
            false -> R.string.sensor_info_not_detected
            null -> R.string.sensor_info_unavailable
        }
    )
    return listOf(
        SensorInfoGroup(
            title = resources.getString(R.string.sensor_info_general_information),
            rows = listOf(
                resources.getString(R.string.sensor_info_identifier) to
                    sensorInfo.deviceId.orUnavailable(unavailable),
                resources.getString(R.string.sensor_info_firmware) to
                    sensorInfo.firmwareVersion.orUnavailable(unavailable)
            )
        ),
        SensorInfoGroup(
            title = resources.getString(R.string.sensor_info_hardware),
            rows = listOf(
                resources.getString(R.string.sensor_info_board) to
                    sensorInfo.boardName.orUnavailable(unavailable),
                resources.getString(R.string.sensor_info_chip) to
                    sensorInfo.chipModel.orUnavailable(unavailable),
                resources.getString(R.string.sensor_info_cpu) to
                    sensorInfo.cpuFrequencyMhz?.let { frequency ->
                        sensorInfo.chipCores?.let { cores ->
                            resources.getString(R.string.sensor_info_cpu_value, frequency, cores)
                        } ?: "$frequency MHz"
                    }.orUnavailable(unavailable)
            )
        ),
        SensorInfoGroup(
            title = resources.getString(R.string.sensor_info_components),
            rows = listOf(
                resources.getString(R.string.sensor_info_visible_sensor) to
                    "$UvirVisibleSensorIdentity · ${status(sensorInfo.sensorAvailable)}",
                resources.getString(R.string.sensor_info_uv_sensor) to
                    "$UvirUvSensorIdentity · ${status(sensorInfo.uvAvailable)}",
                "RTC" to "$UvirRtcIdentity · ${status(sensorInfo.rtcAvailable)}",
                "FRAM" to "MB85RC256V · ${status(sensorInfo.framAvailable)}",
                resources.getString(R.string.sensor_info_micro_sd) to
                    "$UvirMicroSdAdapterIdentity · ${status(sensorInfo.sdAvailable)}",
                resources.getString(R.string.sensor_status_led) to UvirStatusLedIdentity,
                resources.getString(R.string.sensor_status_buzzer) to UvirStatusBuzzerIdentity,
                resources.getString(R.string.external_command) to UvirExternalInputIdentity
            ),
            paragraphLayout = true
        ),
        SensorInfoGroup(
            title = resources.getString(R.string.sensor_info_date_time),
            rows = clockRows
        )
    )
}

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
        if (bytes >= 1024L * 1024L * 1024L) {
            "${formatUvirNumber(bytes / (1024.0 * 1024.0 * 1024.0), 2, numericFormat)} GB"
        } else if (bytes >= 1024L * 1024L) {
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
private fun String.storageBackendText(unavailable: String): String =
    when (this) {
        "micro_sd" -> stringResource(R.string.sensor_info_storage_backend_micro_sd)
        "internal_emergency" ->
            stringResource(R.string.sensor_info_storage_backend_internal)
        "none" -> stringResource(R.string.sensor_info_storage_backend_none)
        else -> orUnavailable(unavailable)
    }

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

internal fun formatUvirSensorInformation(
    resources: Resources,
    connectionMode: SensorConnectionMode,
    sensorInfo: UvirSensorRuntimeInfo,
    numericFormat: UvirNumericFormat,
    irradianceUnit: UvirIrradianceUnit,
    lastConnectionAtMs: Long? = null
): String {
    val unavailable = resources.getString(R.string.sensor_info_unavailable)
    val locale = resources.configuration.locales[0] ?: Locale.getDefault()
    val powerEstimate =
        estimateUvirSensorPower(
            selectedConnectionMode = connectionMode,
            sensorInfo = sensorInfo
        )
    fun whole(value: Number): String =
        formatUvirNumber(value.toDouble(), 0, numericFormat)
    fun status(value: Boolean?): String =
        resources.getString(
            when (value) {
                true -> R.string.sensor_info_detected
                false -> R.string.sensor_info_not_detected
                null -> R.string.sensor_info_unavailable
            }
        )
    fun duration(totalSeconds: Int): String {
        val safeSeconds = totalSeconds.coerceAtLeast(0)
        val hours = safeSeconds / 3_600
        val minutes = (safeSeconds % 3_600) / 60
        val seconds = safeSeconds % 60
        return buildList {
            if (hours > 0) add("${whole(hours)} h")
            if (minutes > 0) add("${whole(minutes)} min")
            if (seconds > 0 || isEmpty()) add("${whole(seconds)} s")
        }.joinToString(" ")
    }
    fun displayUnit(value: String): String =
        when (value.lowercase()) {
            "uw/cm2", "µw/cm²" -> "µW/cm²"
            else -> value.orUnavailable(unavailable)
        }
    fun calibration(value: String): String =
        when {
            value.contains("estimate", ignoreCase = true) ->
                resources.getString(R.string.sensor_info_datasheet_estimate)

            value.contains("user_adjusted", ignoreCase = true) ->
                resources.getString(R.string.sensor_info_user_adjusted_calibration)

            else -> value.orUnavailable(unavailable)
        }
    val source =
        resources.getString(
            when (connectionMode) {
                SensorConnectionMode.USB -> R.string.sensor_connection_usb
                SensorConnectionMode.WIFI -> R.string.sensor_connection_wifi
                SensorConnectionMode.BLUETOOTH -> R.string.sensor_connection_bluetooth
                SensorConnectionMode.INTERNET -> R.string.sensor_connection_internet
            }
        )
    val signalPercentage =
        when (connectionMode) {
            SensorConnectionMode.WIFI,
            SensorConnectionMode.INTERNET ->
                sensorInfo.wifiRssiDbm?.let(::wifiSignalPercentage)

            SensorConnectionMode.BLUETOOTH ->
                sensorInfo.bluetoothRssiDelta?.let(::bluetoothSignalPercentage)

            SensorConnectionMode.USB -> null
        }
    val signalDetails =
        when (connectionMode) {
            SensorConnectionMode.WIFI,
            SensorConnectionMode.INTERNET ->
                sensorInfo.wifiRssiDbm?.let { "${whole(it)} dBm" }

            SensorConnectionMode.BLUETOOTH ->
                sensorInfo.bluetoothRssiDelta?.let {
                    resources.getString(R.string.sensor_info_relative_signal, whole(it))
                }

            SensorConnectionMode.USB -> null
        }
    val clockRows =
        listOf(
            resources.getString(R.string.sensor_info_clock_status) to
                resources.getString(
                    when (sensorInfo.rtcValid) {
                        true -> R.string.sensor_info_valid
                        false -> R.string.sensor_info_invalid
                        null -> R.string.sensor_info_unavailable
                    }
                ),
            resources.getString(R.string.sensor_info_time_source) to
                when (sensorInfo.timeSource.lowercase(Locale.ROOT)) {
                    "rtc" -> "RTC"
                    "phone" -> resources.getString(R.string.sensor_info_time_source_phone)
                    else -> unavailable
                },
            resources.getString(R.string.sensor_info_rtc_time) to
                sensorInfo.rtcCurrentTimeMs
                    ?.takeIf { it > 0L }
                    ?.let {
                        SimpleDateFormat(
                            "yyyy-MM-dd HH:mm:ss 'UTC'",
                            Locale.US
                        ).apply {
                            timeZone = TimeZone.getTimeZone("UTC")
                        }.format(Date(it))
                    }
                    .orUnavailable(unavailable)
        )
    val deviceGroups = uvirSensorDeviceInfoGroups(resources, sensorInfo, clockRows)
    val connectionRows =
        buildList {
            add(resources.getString(R.string.sensor_info_source) to source)
            add(resources.getString(R.string.sensor_info_last_connection) to
                lastConnectionAtMs?.takeIf { it > 0L }?.let {
                    SimpleDateFormat("yyyy-MM-dd HH:mm:ss 'UTC'", Locale.US).apply {
                        timeZone = TimeZone.getTimeZone("UTC")
                    }.format(Date(it))
                }.orUnavailable(unavailable))
            signalPercentage?.let {
                add(
                    resources.getString(R.string.sensor_info_signal) to
                        "${whole(it)}%${signalDetails?.let { detail -> " · $detail" }.orEmpty()}"
                )
            }
            if (
                connectionMode == SensorConnectionMode.WIFI ||
                    connectionMode == SensorConnectionMode.INTERNET
            ) {
                add(
                    resources.getString(R.string.sensor_info_network) to
                        sensorInfo.wifiNetwork.orUnavailable(unavailable)
                )
                add(
                    resources.getString(R.string.sensor_info_ip_address) to
                        sensorInfo.wifiAddress.orUnavailable(unavailable)
                )
            }
            if (connectionMode == SensorConnectionMode.BLUETOOTH) {
                add(
                    resources.getString(R.string.sensor_info_device_name) to
                        sensorInfo.bluetoothName.orUnavailable(unavailable)
                )
            }
            if (sensorInfo.internetBrokerHost.isNotBlank()) {
                add(
                    resources.getString(R.string.sensor_info_mqtt_broker) to
                        sensorInfo.internetBrokerHost
                )
                add(
                    resources.getString(R.string.sensor_info_tls_port) to
                        sensorInfo.internetBrokerPort?.let(::whole)
                            .orUnavailable(unavailable)
                )
                add(
                    resources.getString(R.string.sensor_info_mqtt_status) to
                        resources.getString(
                            if (sensorInfo.internetBrokerConnected == true) {
                                R.string.sensor_info_connected
                            } else {
                                R.string.sensor_info_not_connected
                            }
                        )
                )
            }
        }
    val measurementGroups =
        listOf(
            resources.getString(R.string.sensor_info_calibration) to
                buildList {
                    add(
                        resources.getString(R.string.sensor_info_unit) to
                            displayUnit(sensorInfo.unit)
                    )
                    add(
                        resources.getString(R.string.sensor_info_calibration) to
                            calibration(sensorInfo.calibrationKind)
                    )
                    sensorInfo.visibleCalibrationFactor?.let {
                        add(
                            resources.getString(
                                R.string.sensor_info_visible_calibration_factor
                            ) to formatUvirNumber(it, 3, numericFormat)
                        )
                    }
                    sensorInfo.uvCalibrationFactor?.let {
                        add(
                            resources.getString(
                                R.string.sensor_info_uv_calibration_factor
                            ) to formatUvirNumber(it, 3, numericFormat)
                        )
                    }
                },
            resources.getString(R.string.sensor_info_sampling) to
                listOf(
                    resources.getString(R.string.sensor_info_stream_interval) to
                        sensorInfo.streamIntervalMs?.let { "${whole(it)} ms" }
                            .orUnavailable(unavailable),
                    resources.getString(R.string.sensor_info_integration) to
                        sensorInfo.integrationMs?.let {
                            "${formatUvirNumber(it, 1, numericFormat)} ms"
                        }.orUnavailable(unavailable),
                    resources.getString(R.string.sensor_info_gain) to
                        sensorInfo.gain?.let {
                            "${formatUvirNumber(it, 1, numericFormat)}×"
                        }.orUnavailable(unavailable),
                    resources.getString(R.string.sensor_info_samples_sent) to
                        sensorInfo.sampleSequence?.let(::whole).orUnavailable(unavailable)
                ),
            resources.getString(R.string.sensor_info_synchronization) to
                buildList {
                    sensorInfo.autonomousRecordingEnabled?.let {
                        add(
                            resources.getString(R.string.sensor_autonomous_recording) to
                                status(it)
                        )
                    }
                    sensorInfo.automaticShutdownEnabled?.let {
                        add(
                            resources.getString(R.string.sensor_automatic_shutdown) to
                                status(it)
                        )
                    }
                    if (sensorInfo.automaticShutdownEnabled == true) {
                        sensorInfo.automaticShutdownSeconds?.let {
                            add(
                                resources.getString(
                                    R.string.sensor_automatic_shutdown_after
                                ) to duration(it)
                            )
                        }
                    }
                    sensorInfo.offlineAlertRepeatSeconds?.let {
                        add(
                            resources.getString(
                                R.string.sensor_info_offline_alert_interval
                            ) to duration(it)
                        )
                    }
                    add(
                        resources.getString(R.string.sensor_pending_acquisitions) to
                            whole(sensorInfo.offlineAcquisitions ?: 0)
                    )
                    add(
                        resources.getString(R.string.sensor_pending_alerts) to
                            whole(sensorInfo.offlineAlerts ?: 0)
                    )
                    add(
                        resources.getString(R.string.sensor_pending_errors) to
                            whole(sensorInfo.offlineErrors ?: 0)
                    )
                }
        )
    val scaleGroups =
        listOf(
            resources.getString(R.string.sensor_info_display_scale) to
                listOf(
                    resources.getString(R.string.sensor_info_selected_unit) to
                        irradianceUnit.symbol,
                    resources.getString(R.string.sensor_info_scale_equivalence) to
                        "1 W/m² = 0.1 mW/cm² = 100 µW/cm²"
                )
        )
    val memoryGroups =
        listOf(
            resources.getString(R.string.sensor_info_flash_memory) to
                listOf(
                    resources.getString(R.string.sensor_info_flash_capacity) to
                        sensorInfo.flashSizeBytes.memoryText(unavailable, numericFormat),
                    resources.getString(R.string.sensor_info_program_capacity) to
                        sensorInfo.appPartitionSizeBytes.memoryText(unavailable, numericFormat),
                    resources.getString(R.string.sensor_info_firmware_storage) to
                        sensorInfo.firmwareSizeBytes.memoryTextWithPercentage(
                            sensorInfo.appPartitionSizeBytes,
                            unavailable,
                            numericFormat
                        ),
                    resources.getString(R.string.sensor_info_program_space_free) to
                        sensorInfo.freeAppPartitionBytes.memoryTextWithPercentage(
                            sensorInfo.appPartitionSizeBytes,
                            unavailable,
                            numericFormat
                        )
                ),
            resources.getString(R.string.sensor_info_ram_memory) to
                listOf(
                    resources.getString(R.string.sensor_info_ram_capacity) to
                        sensorInfo.heapSizeBytes.memoryText(unavailable, numericFormat),
                    resources.getString(R.string.sensor_info_ram_used) to
                        differenceBytes(
                            sensorInfo.heapSizeBytes,
                            sensorInfo.freeHeapBytes
                        ).memoryTextWithPercentage(
                            sensorInfo.heapSizeBytes,
                            unavailable,
                            numericFormat
                        ),
                    resources.getString(R.string.sensor_info_ram_free) to
                        sensorInfo.freeHeapBytes.memoryTextWithPercentage(
                            sensorInfo.heapSizeBytes,
                            unavailable,
                            numericFormat
                        )
                ),
            resources.getString(R.string.sensor_info_micro_sd) to
                listOf(
                    resources.getString(R.string.sensor_info_micro_sd_status) to
                        status(sensorInfo.sdAvailable),
                    resources.getString(R.string.sensor_info_storage_backend) to
                        when (sensorInfo.storageBackend) {
                            "micro_sd" -> resources.getString(
                                R.string.sensor_info_storage_backend_micro_sd
                            )
                            "internal_emergency" -> resources.getString(
                                R.string.sensor_info_storage_backend_internal
                            )
                            "none" -> resources.getString(
                                R.string.sensor_info_storage_backend_none
                            )
                            else -> unavailable
                        },
                    resources.getString(R.string.sensor_info_micro_sd_type) to
                        sensorInfo.sdType.orUnavailable(unavailable),
                    resources.getString(R.string.sensor_info_file_system) to
                        if (sensorInfo.sdAvailable == true) "FAT32" else unavailable,
                    resources.getString(R.string.sensor_info_offline_storage_capacity) to
                        sensorInfo.sdTotalBytes.memoryText(unavailable, numericFormat),
                    resources.getString(R.string.sensor_info_offline_storage_used) to
                        sensorInfo.sdUsedBytes.memoryTextWithPercentage(
                            sensorInfo.sdTotalBytes,
                            unavailable,
                            numericFormat
                        ),
                    resources.getString(R.string.sensor_info_offline_storage_free) to
                        sensorInfo.sdFreeBytes.memoryTextWithPercentage(
                            sensorInfo.sdTotalBytes,
                            unavailable,
                            numericFormat
                        ),
                    resources.getString(R.string.sensor_info_record_capacity_total) to
                        sensorInfo.sdRecordCapacityTotal?.let(::whole)
                            .orUnavailable(unavailable),
                    resources.getString(R.string.sensor_info_record_capacity_free) to
                        sensorInfo.sdRecordCapacityFree?.let(::whole)
                            .orUnavailable(unavailable),
                    resources.getString(R.string.sensor_info_record_size) to
                        sensorInfo.storageRecordSizeBytes?.let { "${whole(it)} B" }
                            .orUnavailable(unavailable),
                    resources.getString(R.string.sensor_info_pending_records) to
                        sensorInfo.offlineUsed?.let(::whole).orUnavailable(unavailable)
                ),
            "FRAM" to
                listOf(
                    resources.getString(R.string.sensor_info_status) to
                        status(sensorInfo.framAvailable),
                    resources.getString(R.string.sensor_info_offline_storage_capacity) to
                        sensorInfo.framCapacityBytes.memoryText(unavailable, numericFormat),
                    resources.getString(R.string.sensor_info_record_capacity_total) to
                        sensorInfo.framRecordCapacity?.let(::whole)
                            .orUnavailable(unavailable),
                    resources.getString(R.string.sensor_info_pending_records) to
                        sensorInfo.framRecordsUsed?.let(::whole)
                            .orUnavailable(unavailable)
                )
        )
    val powerGroups =
        listOf(
            resources.getString(R.string.sensor_info_power_current_estimate) to
                listOf(
                    resources.getString(R.string.sensor_info_estimated_current) to
                        powerEstimate.currentMilliAmps.approximateUnitText("mA", numericFormat),
                    resources.getString(R.string.sensor_info_estimated_power) to
                        powerEstimate.powerMilliWatts.approximatePowerText(numericFormat),
                    resources.getString(R.string.sensor_info_energy_one_hour) to
                        powerEstimate.energyForOneHourMilliWattHours
                            .approximateEnergyText(numericFormat)
                ),
            resources.getString(R.string.sensor_info_power_operating_range) to
                listOf(
                    resources.getString(R.string.sensor_info_minimum_current) to
                        powerEstimate.minimumCurrentMilliAmps
                            .approximateUnitText("mA", numericFormat),
                    resources.getString(R.string.sensor_info_minimum_power) to
                        powerEstimate.minimumPowerMilliWatts
                            .approximatePowerText(numericFormat),
                    resources.getString(R.string.sensor_info_peak_current) to
                        powerEstimate.peakCurrentMilliAmps
                            .approximateUnitText("mA", numericFormat),
                    resources.getString(R.string.sensor_info_peak_power) to
                        powerEstimate.peakPowerMilliWatts
                            .approximatePowerText(numericFormat),
                    resources.getString(R.string.sensor_info_reference_voltage) to
                        "${formatUvirNumber(
                            powerEstimate.referenceVoltageVolts,
                            1,
                            numericFormat
                        )} V (USB/VIN)"
                )
        )

    return buildString {
        fun rows(values: List<Pair<String, String>>) {
            values.forEach { (label, value) ->
                appendLine("$label: $value")
            }
        }
        fun section(title: String, values: List<Pair<String, String>>) {
            if (isNotEmpty()) appendLine()
            appendLine(title.uppercase(locale))
            rows(values)
        }
        fun groupedSection(
            title: String,
            groups: List<Pair<String, List<Pair<String, String>>>>,
            footer: String? = null
        ) {
            if (isNotEmpty()) appendLine()
            appendLine(title.uppercase(locale))
            groups.forEach { (groupTitle, values) ->
                appendLine(groupTitle)
                rows(values)
            }
            footer?.let {
                appendLine()
                appendLine(it)
            }
        }

        appendLine(resources.getString(R.string.sensor_info_title))
        appendLine("Uvir ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        groupedSection(
            resources.getString(R.string.sensor_info_device),
            deviceGroups.map { it.title to it.rows }
        )
        section(resources.getString(R.string.sensor_info_connection), connectionRows)
        groupedSection(
            resources.getString(R.string.sensor_info_measurement),
            measurementGroups
        )
        groupedSection(
            resources.getString(R.string.sensor_info_scale_sensitivity),
            scaleGroups
        )
        groupedSection(
            resources.getString(R.string.sensor_info_memory),
            memoryGroups
        )
        groupedSection(
            resources.getString(R.string.sensor_info_power_consumption),
            powerGroups,
            resources.getString(R.string.sensor_info_power_estimate_note)
        )
    }.trimEnd()
}
