package me.mondiversi.uvir

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlin.math.roundToInt

/** Immutable values captured at a user commit, not when a queued send starts. */
internal class UvirSettingsSaveInput(
    val group: SettingsSaveGroup,
    val context: Context,
    val samplesText: String,
    val onSamplesTextChange: (String) -> Unit,
    val spacingText: String,
    val onSpacingTextChange: (String) -> Unit,
    val trimEnabled: Boolean,
    val automaticShutdownHoursText: String,
    val onAutomaticShutdownHoursTextChange: (String) -> Unit,
    val automaticShutdownMinutesText: String,
    val onAutomaticShutdownMinutesTextChange: (String) -> Unit,
    val automaticShutdownSecondsText: String,
    val onAutomaticShutdownSecondsTextChange: (String) -> Unit,
    val alertRepeatHoursText: String,
    val onAlertRepeatHoursTextChange: (String) -> Unit,
    val alertRepeatMinutesText: String,
    val onAlertRepeatMinutesTextChange: (String) -> Unit,
    val alertRepeatSecondsText: String,
    val onAlertRepeatSecondsTextChange: (String) -> Unit,
    val visibleCalibrationFactorText: String,
    val onVisibleCalibrationFactorTextChange: (String) -> Unit,
    val uvCalibrationFactorText: String,
    val onUvCalibrationFactorTextChange: (String) -> Unit,
    val sensorCalibrationSettings: SensorCalibrationSettings,
    val alertSoundValue: String,
    val alertVolume: Float,
    val thresholdAlertSettings: ThresholdAlertSettings,
    val sensorHardwareUid: String,
    val sensorNameText: String,
    val appliedSensorDisplayName: String,
    val onSensorNameTextChange: (String) -> Unit,
    val onSaveSensorDisplayName: suspend (String) -> Boolean,
    val sensorSettingsEnabled: Boolean,
    val sensorCalibrationEnabled: Boolean,
    val sensorFirmwareCurrent: Boolean,
    val sensorWifiRadioEnabled: Boolean,
    val sensorBluetoothRadioEnabled: Boolean,
    val sensorInternetEnabled: Boolean,
    val sensorInternetUsePrimaryWifi: Boolean,
    val sensorInternetWifiSsid: String,
    val sensorInternetWifiPassword: String,
    val sensorInternetRelayHost: String,
    val sensorInternetRelayPortText: String,
    val sensorInternetMqttUsername: String,
    val sensorInternetMqttPassword: String,
    val appliedSensorInternetConfiguration: SensorInternetConfiguration,
    val onAppliedSensorInternetConfigurationChange:
        (SensorInternetConfiguration) -> Unit,
    val appliedSensorWifiRadioEnabled: Boolean,
    val onAppliedSensorWifiRadioEnabledChange: (Boolean) -> Unit,
    val appliedSensorBluetoothRadioEnabled: Boolean,
    val onAppliedSensorBluetoothRadioEnabledChange: (Boolean) -> Unit,
    val sensorConnectionMode: SensorConnectionMode,
    val sensorParameters: SensorParameters,
    val appliedSensorParameters: SensorParameters,
    val appliedAcquisitionParameters: AcquisitionParameters,
    val fakeSensorDataEnabled: Boolean,
    val numericFormatValue: String,
    val sensorRadioConnectionRequiredText: String,
    val onFirmwareUpdateRequired: () -> Unit,
    val valueCorrectedText: String,
    val parametersSavedText: String,
    val onParametersErrorChange: (String?) -> Unit,
    val onApplySensorRadioSettings: suspend (SensorRadioSettings) -> Boolean,
    val onApplySensorInternetConfiguration:
        suspend (SensorInternetConfiguration) -> Boolean,
    val onApplySensorParameters: suspend (SensorParameters) -> Boolean,
    val onApplySensorCalibration: suspend (SensorCalibrationSettings) -> Boolean,
    val onSensorConnectionModeChanged: (SensorConnectionMode) -> Unit,
    val onApplyAcquisitionParameters: suspend (AcquisitionParameters) -> Boolean,
    val onApplyThresholdAlertSettings: suspend (ThresholdAlertSettings) -> Boolean,
    val onUseFakeSensorDataChanged: (Boolean) -> Unit,
    val onCommitAppLanguage: () -> Unit,
    val onApplyNumericFormat: (UvirNumericFormat) -> Unit,
    val sensorWifiSsid: String,
    val sensorWifiPassword: String,
    val appliedSensorWifiSsid: String,
    val appliedSensorWifiPassword: String,
    val onConfigureSensorWifi: suspend (String, String) -> Boolean
)

internal suspend fun applyUvirSettingsGroup(input: UvirSettingsSaveInput) {
    with(input) {
        fun failed() = showUvirBottomMessage(context, sensorRadioConnectionRequiredText)
        fun saved() = showUvirBottomMessage(context, parametersSavedText)
        fun corrected() = showUvirBottomMessage(context, valueCorrectedText)
        fun firmwareAllowed(): Boolean {
            if (sensorFirmwareCurrent) return true
            onFirmwareUpdateRequired()
            return false
        }
        suspend fun send(action: suspend () -> Boolean): Boolean {
            val accepted = try { action() }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { false }
            if (!accepted) failed()
            return accepted
        }

        when (group) {
            SettingsSaveGroup.NAME -> {
                val name = normalizeSensorDisplayName(sensorNameText, sensorHardwareUid)
                onSensorNameTextChange(name)
                if (sensorHardwareUid.isNotBlank() && name != appliedSensorDisplayName &&
                    send { onSaveSensorDisplayName(name) }) saved()
            }
            SettingsSaveGroup.NUMERIC_FORMAT ->
                onApplyNumericFormat(UvirNumericFormat.fromStoredValue(numericFormatValue))
            SettingsSaveGroup.LANGUAGE -> onCommitAppLanguage()
            SettingsSaveGroup.FAKE_DATA -> onUseFakeSensorDataChanged(fakeSensorDataEnabled)
            SettingsSaveGroup.PARAMETERS -> {
                if (!sensorSettingsEnabled) return
                val duration = normalizeDuration(
                    automaticShutdownHoursText, automaticShutdownMinutesText,
                    automaticShutdownSecondsText, minimumTotalSeconds = 60L,
                    maximumTotalSeconds = 86_400L
                )
                onAutomaticShutdownHoursTextChange(duration.hoursText)
                onAutomaticShutdownMinutesTextChange(duration.minutesText)
                onAutomaticShutdownSecondsTextChange(duration.secondsText)
                if (duration.corrected) corrected()
                val target = sensorParameters.copy(automaticShutdownSeconds = duration.totalSeconds.toInt())
                if (target != appliedSensorParameters && firmwareAllowed() &&
                    send { onApplySensorParameters(target) }) saved()
            }
            SettingsSaveGroup.CALIBRATION -> {
                if (!sensorCalibrationEnabled) return
                val visible = normalizeBoundedDecimal(
                    visibleCalibrationFactorText, MIN_SENSOR_CALIBRATION_FACTOR, MAX_SENSOR_CALIBRATION_FACTOR)
                val uv = normalizeBoundedDecimal(
                    uvCalibrationFactorText, MIN_SENSOR_CALIBRATION_FACTOR, MAX_SENSOR_CALIBRATION_FACTOR)
                onVisibleCalibrationFactorTextChange(visible.text)
                onUvCalibrationFactorTextChange(uv.text)
                if (visible.corrected || uv.corrected) corrected()
                val target = SensorCalibrationSettings(visible.value, uv.value)
                if (target != sensorCalibrationSettings && firmwareAllowed() &&
                    send { onApplySensorCalibration(target) }) saved()
            }
            SettingsSaveGroup.SAMPLING -> {
                if (!sensorSettingsEnabled) return
                val samples = normalizeBoundedInteger(samplesText, 1, 21)
                val spacing = normalizeBoundedLong(spacingText, 150L, 5_000L)
                onSamplesTextChange(samples.text)
                onSpacingTextChange(spacing.text)
                if (samples.corrected || spacing.corrected) corrected()
                val target = AcquisitionParameters(samples.value, spacing.value, trimEnabled)
                if (target != appliedAcquisitionParameters && firmwareAllowed() &&
                    send { onApplyAcquisitionParameters(target) }) saved()
                onParametersErrorChange(null)
            }
            SettingsSaveGroup.ALERTS -> {
                val repeat = normalizeDuration(
                    alertRepeatHoursText, alertRepeatMinutesText, alertRepeatSecondsText,
                    minimumTotalSeconds = 1L, maximumTotalSeconds = MAX_ALERT_REPEAT_SECONDS)
                onAlertRepeatHoursTextChange(repeat.hoursText)
                onAlertRepeatMinutesTextChange(repeat.minutesText)
                onAlertRepeatSecondsTextChange(repeat.secondsText)
                if (repeat.corrected) corrected()
                // Rules belong to the bell editors, not this settings section.
                val target = thresholdAlertSettings.copy(
                    repeatSeconds = if (sensorSettingsEnabled) repeat.totalSeconds.toInt()
                                    else thresholdAlertSettings.repeatSeconds,
                    sound = runCatching { ThresholdAlertSound.valueOf(alertSoundValue) }
                        .getOrDefault(ThresholdAlertSound.TRIPLE_BEEP),
                    volume = alertVolume.roundToInt().coerceIn(0, 100)
                )
                if (target != thresholdAlertSettings &&
                    (!target.firmwareConfigurationDiffersFrom(thresholdAlertSettings) || firmwareAllowed())) {
                    if (send { onApplyThresholdAlertSettings(target) }) saved()
                }
            }
            SettingsSaveGroup.RADIO -> {
                if (!sensorSettingsEnabled) return
                val target = SensorRadioSettings(sensorWifiRadioEnabled, sensorBluetoothRadioEnabled)
                if (sensorWifiRadioEnabled == appliedSensorWifiRadioEnabled &&
                    sensorBluetoothRadioEnabled == appliedSensorBluetoothRadioEnabled) return
                if (!firmwareAllowed() || !send { onApplySensorRadioSettings(target) }) return
                onAppliedSensorWifiRadioEnabledChange(sensorWifiRadioEnabled)
                onAppliedSensorBluetoothRadioEnabledChange(sensorBluetoothRadioEnabled)
                val disabledCurrent =
                    (sensorConnectionMode == SensorConnectionMode.WIFI && !sensorWifiRadioEnabled) ||
                    (sensorConnectionMode == SensorConnectionMode.BLUETOOTH && !sensorBluetoothRadioEnabled) ||
                    (sensorConnectionMode == SensorConnectionMode.INTERNET && !sensorWifiRadioEnabled)
                if (disabledCurrent) onSensorConnectionModeChanged(
                    if (sensorWifiRadioEnabled) SensorConnectionMode.WIFI
                    else if (sensorBluetoothRadioEnabled) SensorConnectionMode.BLUETOOTH
                    else SensorConnectionMode.USB)
                saved()
            }
            SettingsSaveGroup.WIFI -> {
                if (!sensorSettingsEnabled) return
                val network = validatedSensorWifiConfiguration(sensorWifiSsid, sensorWifiPassword)
                if (network == null) {
                    failed()
                    return
                }
                if (network.ssid == appliedSensorWifiSsid && network.password == appliedSensorWifiPassword) return
                if (firmwareAllowed() && send { onConfigureSensorWifi(network.ssid, network.password) }) saved()
            }
            SettingsSaveGroup.INTERNET -> {
                if (!sensorSettingsEnabled) return
                val target = validatedSensorInternetConfiguration(
                    sensorInternetEnabled, sensorInternetUsePrimaryWifi, sensorInternetWifiSsid,
                    sensorInternetWifiPassword, sensorInternetRelayHost, sensorInternetRelayPortText,
                    sensorInternetMqttUsername, sensorInternetMqttPassword)
                if (target == null) {
                    showUvirBottomMessage(context, context.getString(R.string.sensor_internet_invalid), true)
                    return
                }
                if (target == appliedSensorInternetConfiguration) return
                if (!firmwareAllowed() || !send { onApplySensorInternetConfiguration(target) }) return
                onAppliedSensorInternetConfigurationChange(target)
                if (sensorConnectionMode == SensorConnectionMode.INTERNET && !target.enabled)
                    onSensorConnectionModeChanged(
                        if (sensorWifiRadioEnabled) SensorConnectionMode.WIFI
                        else if (sensorBluetoothRadioEnabled) SensorConnectionMode.BLUETOOTH
                        else SensorConnectionMode.USB)
                saved()
            }
        }
    }
}

internal fun ThresholdAlertSettings.firmwareConfigurationDiffersFrom(other: ThresholdAlertSettings): Boolean =
    enabled != other.enabled || repeatSeconds != other.repeatSeconds || rules != other.rules
