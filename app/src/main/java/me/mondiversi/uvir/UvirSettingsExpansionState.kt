package me.mondiversi.uvir

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

private data class UvirSettingsSection(
    val preferenceKey: String,
    val defaultExpanded: Boolean = false
)

private val uvirSettingsSections =
    listOf(
        UvirSettingsSection(KEY_SETTINGS_SENSOR_CONNECTION_EXPANDED),
        UvirSettingsSection(KEY_SETTINGS_SENSOR_PARAMETERS_EXPANDED),
        UvirSettingsSection(KEY_SETTINGS_SENSOR_CALIBRATION_EXPANDED),
        UvirSettingsSection(KEY_SETTINGS_SAMPLING_EXPANDED),
        UvirSettingsSection(KEY_SETTINGS_ALERTS_EXPANDED),
        UvirSettingsSection(UVIR_NUMERIC_FORMAT_EXPANDED_KEY),
        UvirSettingsSection(KEY_SETTINGS_LANGUAGE_EXPANDED),
        UvirSettingsSection(KEY_SETTINGS_COUNTERS_EXPANDED),
        UvirSettingsSection(KEY_SETTINGS_DEBUG_EXPANDED),
        UvirSettingsSection(KEY_SETTINGS_USB_EXPANDED),
        UvirSettingsSection(KEY_SETTINGS_WIFI_EXPANDED),
        UvirSettingsSection(KEY_SETTINGS_BLUETOOTH_EXPANDED),
        UvirSettingsSection(KEY_SETTINGS_INTERNET_EXPANDED)
    )

@Stable
internal class UvirSettingsExpansionState internal constructor(
    initialValues: List<Boolean>
) {
    private val values =
        initialValues
            .takeIf { it.size == uvirSettingsSections.size }
            ?: List(uvirSettingsSections.size) { false }

    var sensorConnection by mutableStateOf(values[0])
    var sensorParameters by mutableStateOf(values[1])
    var sensorCalibration by mutableStateOf(values[2])
    var sampling by mutableStateOf(values[3])
    var alerts by mutableStateOf(values[4])
    var numericFormat by mutableStateOf(values[5])
    var language by mutableStateOf(values[6])
    var counters by mutableStateOf(values[7])
    var debug by mutableStateOf(values[8])
    var usb by mutableStateOf(values[9])
    var wifi by mutableStateOf(values[10])
    var bluetooth by mutableStateOf(values[11])
    var internet by mutableStateOf(values[12])

    internal fun asList(): List<Boolean> =
        listOf(
            sensorConnection,
            sensorParameters,
            sensorCalibration,
            sampling,
            alerts,
            numericFormat,
            language,
            counters,
            debug,
            usb,
            wifi,
            bluetooth,
            internet
        )

    internal fun expandAll(context: Context) {
        setAll(
            context = context,
            expanded = true
        )
    }

    internal fun collapseAll(context: Context) {
        setAll(
            context = context,
            expanded = false
        )
    }

    private fun setAll(
        context: Context,
        expanded: Boolean
    ) {
        sensorConnection = expanded
        sensorParameters = expanded
        sensorCalibration = expanded
        sampling = expanded
        alerts = expanded
        numericFormat = expanded
        language = expanded
        counters = expanded
        debug = expanded
        usb = expanded
        wifi = expanded
        bluetooth = expanded
        internet = expanded

        val editor =
            context
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
        uvirSettingsSections.forEach { section ->
            editor.putBoolean(section.preferenceKey, expanded)
        }
        editor.apply()
    }
}

private val UvirSettingsExpansionStateSaver =
    listSaver<UvirSettingsExpansionState, Boolean>(
        save = { state -> state.asList() },
        restore = { values -> UvirSettingsExpansionState(values) }
    )

@Composable
internal fun rememberUvirSettingsExpansionState(
    context: Context
): UvirSettingsExpansionState =
    rememberSaveable(saver = UvirSettingsExpansionStateSaver) {
        UvirSettingsExpansionState(
            uvirSettingsSections.map { section ->
                loadSettingsSectionExpanded(
                    context = context,
                    key = section.preferenceKey,
                    defaultValue = section.defaultExpanded
                )
            }
        )
    }
