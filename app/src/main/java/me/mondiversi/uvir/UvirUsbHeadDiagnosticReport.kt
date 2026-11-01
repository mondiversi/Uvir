package me.mondiversi.uvir

import android.content.res.Resources
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import me.mondiversi.uvir.usb.UsbOpticalCheck

internal fun formatUsbHeadDiagnosticReport(
    resources: Resources, report: UvirSensorDiagnosticReport, technicalFormat: Boolean
): String {
    val checks = requireNotNull(report.usbChecks)
    val locale = resources.configuration.locales[0]
    fun passed(value: Boolean?) = resources.getString(when(value) {
        true -> R.string.sensor_info_test_passed
        false -> R.string.sensor_info_test_failed
        null -> R.string.usb_diagnostic_not_tested
    })
    fun recognized(value: Boolean?) = resources.getString(when(value) {
        true -> R.string.sensor_info_detected
        false -> R.string.sensor_info_not_detected
        null -> R.string.usb_diagnostic_not_tested
    })
    return buildString {
        fun field(label: Int,value: String) { appendLine("${resources.getString(label)}: $value") }
        appendLine(resources.getString(R.string.diagnostic_title))
        field(R.string.diagnostic_generated, if(technicalFormat)
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z",Locale.US).format(Date(report.startedAtMs))
            else DateFormat.getDateTimeInstance(DateFormat.MEDIUM,DateFormat.MEDIUM,locale).format(Date(report.startedAtMs)))
        appendLine("Uvir ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        field(R.string.diagnostic_duration,resources.getString(R.string.sensor_info_milliseconds,report.durationMs.toDouble()))
        appendLine(resources.getString(if(report.successful) R.string.diagnostic_success else R.string.diagnostic_warning))
        appendLine()
        appendLine("FT232H · USB")
        appendLine("USB: ${passed(checks.usbOpened)}")
        appendLine("MPSSE: ${passed(checks.mpsseReady)}")
        appendLine("I²C: ${passed(checks.i2cReady)}")
        checks.error?.let { field(R.string.usb_diagnostic_technical,it) }

        fun component(label: Int,model: String,address: String,check: UsbOpticalCheck) {
            appendLine()
            appendLine("${resources.getString(label)} · $model · I²C $address")
            field(R.string.usb_diagnostic_recognition,recognized(check.recognized) +
                (check.chipId?.let { " (ID ${String.format(Locale.US,"0x%02X",it)})" } ?: ""))
            field(R.string.usb_diagnostic_initialization,passed(check.initialized))
            field(R.string.usb_diagnostic_reading,passed(check.readable))
            check.error?.let { field(R.string.usb_diagnostic_technical,it) }
        }
        component(R.string.sensor_info_uv_sensor,"AS7331","0x74",checks.uv)
        component(R.string.sensor_info_visible_sensor,"AS7343 · VIS/IR","0x39",checks.visible)
        if(report.probes.isNotEmpty()) {
            appendLine()
            appendLine(resources.getString(R.string.usb_diagnostic_readings,report.responses,UVIR_DIAGNOSTIC_REQUESTS))
            report.probes.forEachIndexed { index,probe ->
                val value = when(probe.outcome) {
                    UvirDiagnosticOutcome.SUCCESS -> resources.getString(R.string.sensor_info_milliseconds,requireNotNull(probe.responseMs))
                    UvirDiagnosticOutcome.TIMEOUT -> resources.getString(R.string.diagnostic_timeout)
                    UvirDiagnosticOutcome.WRITE_FAILED -> resources.getString(R.string.diagnostic_write_failed)
                    UvirDiagnosticOutcome.CONNECTION_CHANGED -> resources.getString(R.string.diagnostic_connection_changed)
                }
                appendLine("${resources.getString(R.string.usb_diagnostic_reading)} ${index+1}: $value")
            }
        }
        appendLine()
        if(!report.successful) appendLine(resources.getString(R.string.usb_diagnostic_wiring))
        appendLine(resources.getString(R.string.usb_diagnostic_notes))
    }
}
