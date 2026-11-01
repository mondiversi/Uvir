package me.mondiversi.uvir

import android.content.res.Resources
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.withTimeoutOrNull

internal const val UVIR_USB_DIAGNOSTIC_PROTOCOL = "uvir-ft232h-i2c"

internal fun usbHeadDiagnosticAvailable(state: UsbPhoneState): Boolean =
    state.adapterName.isNotBlank() && state.usbPermission && !state.simulation &&
        !state.measuringReference && !state.diagnosticRunning && !state.busy &&
        state.status in setOf(UsbPhoneStatus.READY, UsbPhoneStatus.ERROR)

internal fun usbPhoneDiagnosticAvailable(state: UsbPhoneState): Boolean =
    state.status == UsbPhoneStatus.READY && !state.simulation &&
        state.identity.isNotBlank() && state.uvAvailable && state.visibleAvailable

/** Only the two optical modules exist on this head: no invented ESP32 peripherals or firmware. */
internal fun usbPhoneDiagnosticInfo(state: UsbPhoneState, freshReading: Boolean = false) =
    UvirSensorRuntimeInfo(
        protocol = UVIR_USB_DIAGNOSTIC_PROTOCOL,
        deviceId = state.identity,
        boardName = "FT232H",
        appConnected = usbPhoneDiagnosticAvailable(state),
        sensorAvailable = state.visibleAvailable,
        uvAvailable = state.uvAvailable,
        visibleSensorModel = "AS7343",
        visibleSensorI2cAddress = 0x39,
        visibleSensorBusOk = state.visibleAvailable,
        visibleSensorDriverOk = state.visibleAvailable,
        visibleSensorReadOk = true.takeIf { freshReading },
        uvSensorModel = "AS7331",
        uvSensorI2cAddress = 0x74,
        uvSensorBusOk = state.uvAvailable,
        uvSensorDriverOk = state.uvAvailable,
        uvSensorReadOk = true.takeIf { freshReading }
    )

/** Observe a NEW pair from the existing serialized reader. Never reconnect, change gain,
 * alter calibration, create records or interrupt a running session to run diagnostics.
 * The runtime's raw-frame SharedFlow has no replay, so a cached display sample cannot pass.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal suspend fun probeUsbPhoneDiagnostic(
    hardwareId: String,
    state: StateFlow<UsbPhoneState>,
    frames: SharedFlow<Pair<Long, DoubleArray>>,
    currentGeneration: () -> Long,
    timeoutMs: Long = 5_000L
): UvirDiagnosticProbe {
    val epoch = currentGeneration()
    fun sameHead() = currentGeneration() == epoch &&
        hardwareId.isNotBlank() && state.value.identity == hardwareId && usbPhoneDiagnosticAvailable(state.value)
    fun changed() = UvirDiagnosticProbe(UvirDiagnosticOutcome.CONNECTION_CHANGED)
    if (!sameHead()) return changed()
    val started = System.nanoTime()
    val result = withTimeoutOrNull(timeoutMs) {
        merge(
            frames.mapNotNull { (generation, channels) ->
                when {
                    !sameHead() -> changed()
                    generation != epoch || channels.size != 11 || channels.any { !it.isFinite() || it < 0.0 } -> null
                    else -> UvirDiagnosticProbe(UvirDiagnosticOutcome.SUCCESS,
                        (System.nanoTime() - started) / 1_000_000.0,
                        usbPhoneDiagnosticInfo(state.value, freshReading = true))
                }
            },
            state.mapNotNull { if (sameHead()) null else changed() }
        ).first()
    } ?: UvirDiagnosticProbe(UvirDiagnosticOutcome.TIMEOUT)
    return if (sameHead()) result else changed()
}

/** Uses the same report/dialog actions as the legacy diagnostic, with only USB-head fields. */
internal fun formatUsbPhoneDiagnosticReport(
    resources: Resources, report: UvirSensorDiagnosticReport, technicalFormat: Boolean
): String {
    if (report.usbChecks != null) return formatUsbHeadDiagnosticReport(resources,report,technicalFormat)
    val locale = resources.configuration.locales[0]
    val info = report.latestInfo ?: report.initialInfo
    fun millis(value: Double) = resources.getString(R.string.sensor_info_milliseconds, value)
    return buildString {
        fun field(label: Int, value: String) { appendLine("${resources.getString(label)}: $value") }
        appendLine(resources.getString(R.string.diagnostic_title))
        field(R.string.diagnostic_generated, if (technicalFormat)
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.US).format(Date(report.startedAtMs))
            else DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM, locale).format(Date(report.startedAtMs)))
        appendLine("Uvir ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        field(R.string.sensor_info_sensor, report.sensorName)
        field(R.string.sensor_info_identifier, report.initialInfo.deviceId)
        field(R.string.sensor_info_source, resources.getString(R.string.sensor_connection_usb))
        field(R.string.sensor_info_board, "FT232H")
        field(R.string.diagnostic_duration, millis(report.durationMs.toDouble()))
        appendLine()
        appendLine(resources.getString(R.string.diagnostic_summary, report.responses, UVIR_DIAGNOSTIC_REQUESTS))
        appendLine(resources.getString(if (report.successful) R.string.diagnostic_success else R.string.diagnostic_warning))
        if (report.responseTimes.isNotEmpty()) field(R.string.diagnostic_latency,
            listOf(report.responseTimes.min(), report.responseTimes.average(), report.responseTimes.max())
                .joinToString(" / ") { millis(it) })
        report.probes.forEachIndexed { index, probe ->
            val value = when (probe.outcome) {
                UvirDiagnosticOutcome.SUCCESS -> millis(requireNotNull(probe.responseMs))
                UvirDiagnosticOutcome.TIMEOUT -> resources.getString(R.string.diagnostic_timeout)
                UvirDiagnosticOutcome.WRITE_FAILED -> resources.getString(R.string.diagnostic_write_failed)
                UvirDiagnosticOutcome.CONNECTION_CHANGED -> resources.getString(R.string.diagnostic_connection_changed)
            }
            appendLine("${resources.getString(R.string.diagnostic_request)} ${index + 1}: $value")
        }
        appendLine()
        appendLine(resources.getString(R.string.sensor_info_components))
        for ((name, passed) in listOf("AS7331 · I²C 0x74" to info.uvSensorReadOk,
            "AS7343 · I²C 0x39" to info.visibleSensorReadOk)) {
            appendLine("$name: ${resources.getString(when (passed) {
                true -> R.string.sensor_info_test_passed
                false -> R.string.sensor_info_test_failed
                null -> R.string.sensor_info_unavailable
            })}")
        }
        appendLine()
        appendLine(resources.getString(R.string.diagnostic_notes))
    }
}
