package me.mondiversi.uvir

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

@Composable
internal fun UvirSamplingAndAlertSettings(
    context: Context,
    samplingSectionExpanded: Boolean,
    onSamplingSectionExpandedChange: (Boolean) -> Unit,
    sensorSettingsEnabled: Boolean,
    samplesText: String,
    onSamplesTextChange: (String) -> Unit,
    spacingText: String,
    onSpacingTextChange: (String) -> Unit,
    trimEnabled: Boolean,
    onTrimEnabledChange: (Boolean) -> Unit,
    parametersError: String?,
    alertsSectionExpanded: Boolean,
    onAlertsSectionExpandedChange: (Boolean) -> Unit,
    alertRepeatHoursText: String,
    onAlertRepeatHoursTextChange: (String) -> Unit,
    alertRepeatMinutesText: String,
    onAlertRepeatMinutesTextChange: (String) -> Unit,
    alertRepeatSecondsText: String,
    onAlertRepeatSecondsTextChange: (String) -> Unit,
    alertSoundValue: String,
    onAlertSoundValueChange: (String) -> Unit,
    alertVolume: Float,
    onAlertVolumeChange: (Float) -> Unit,
    onPreviewThresholdAlertSound: (ThresholdAlertSound, Int) -> Unit,
    cardColor: Color,
    primaryText: Color,
    secondaryText: Color
) {
    val commit = LocalSettingsCommit.current
    val valueCorrectedText =
        stringResource(R.string.value_out_of_limits_corrected)

    SettingsAutoSaveGroup(SettingsSaveGroup.SAMPLING) {
    SettingsSection(
        settingsPage = UvirSettingsPage.SAMPLING,
        title =
            stringResource(
                R.string.settings_section_acquisition
            ),
        titleIcon =
            ConnectivityIconType.SAMPLING,
        expanded =
            samplingSectionExpanded,
        enabled = sensorSettingsEnabled,
        onExpandedChange = { expanded ->
            onSamplingSectionExpandedChange(expanded)
            saveSettingsSectionExpanded(
                context,
                KEY_SETTINGS_SAMPLING_EXPANDED,
                expanded
            )
        },
        containerColor =
            cardColor,
        titleColor =
            primaryText,
        chevronColor =
            secondaryText,
        dividerColor =
            secondaryText.copy(
                alpha = 0.28f
            )
    ) {
    Text(
        text =
            stringResource(
                R.string.sampling_sensor_processing_description
            ),
        color =
            secondaryText,
        fontSize =
            12.sp
    )

    Column(
        verticalArrangement =
            Arrangement.spacedBy(UvirSettingsRelatedGap)
    ) {
        NumberField(
            value =
                samplesText,
            onValueChange = {
                onSamplesTextChange(it)
            },
            label =
                stringResource(
                    R.string.samples_per_measurement
                ),
            modifier =
                Modifier.fillMaxWidth(),
            maxChars = 2,
            enabled = sensorSettingsEnabled,
            onEditingComplete = {
                val normalized = normalizeBoundedInteger(samplesText, 1, 21)
                onSamplesTextChange(normalized.text)
                if (normalized.corrected) {
                    showUvirBottomMessage(context, valueCorrectedText)
                }
            }
        )

        Text(
            text =
                stringResource(
                    R.string.samples_per_measurement_hint
                ),
            color =
                secondaryText,
            fontSize =
                12.sp
        )
    }

    SettingsCheckboxWithDescription(
        checked =
            trimEnabled,
        onCheckedChange = {
            onTrimEnabledChange(it)
        },
        title =
            stringResource(
                R.string.discard_extremes
            ),
        description =
            stringResource(
                R.string.discard_extremes_description
            ),
        primaryText = primaryText,
        secondaryText = secondaryText,
        enabled = sensorSettingsEnabled
    )

    Column(
        verticalArrangement =
            Arrangement.spacedBy(UvirSettingsRelatedGap)
    ) {
        NumberField(
            value =
                spacingText,
            onValueChange = {
                onSpacingTextChange(it)
            },
            label =
                stringResource(
                    R.string.sample_spacing_ms
                ),
            modifier =
                Modifier.fillMaxWidth(),
            maxChars = 5,
            enabled = sensorSettingsEnabled,
            onEditingComplete = {
                val normalized = normalizeBoundedLong(spacingText, 150L, 5_000L)
                onSpacingTextChange(normalized.text)
                if (normalized.corrected) {
                    showUvirBottomMessage(context, valueCorrectedText)
                }
            }
        )

        Text(
            text =
                stringResource(
                    R.string.sample_spacing_hint
                ),
            color =
                secondaryText,
            fontSize =
                12.sp
        )
    }

    Column(
        verticalArrangement =
            Arrangement.spacedBy(UvirSettingsRelatedGap)
    ) {
        Text(
            text =
                stringResource(
                    R.string.parameters_hint
                ),
            color =
                secondaryText,
            fontSize =
                12.sp
        )

        parametersError?.let { error ->
            Text(
                text = error,
                color =
                    MaterialTheme
                        .colorScheme
                        .error,
                fontSize =
                    12.sp
            )
        }
    }
    }

    }
    SettingsSection(
        settingsPage = UvirSettingsPage.ALERTS,
        title =
            stringResource(
                R.string.threshold_alerts_title
            ),
        titleIcon =
            ConnectivityIconType.ALERT,
        expanded =
            alertsSectionExpanded,
        onExpandedChange = { expanded ->
            onAlertsSectionExpandedChange(expanded)
            saveSettingsSectionExpanded(
                context,
                KEY_SETTINGS_ALERTS_EXPANDED,
                expanded
            )
        },
        containerColor = cardColor,
        titleColor = primaryText,
        chevronColor = secondaryText,
        dividerColor =
            secondaryText.copy(
                alpha = 0.28f
            )
    ) {
        Text(
            text = stringResource(R.string.threshold_alerts_description),
            color = secondaryText,
            fontSize = 12.sp,
            lineHeight = 16.sp
        )

        Column(
                verticalArrangement =
                    Arrangement.spacedBy(UvirSettingsRelatedGap)
            ) {
                Text(
                    text = stringResource(R.string.threshold_repeat_label),
                    color =
                        if (sensorSettingsEnabled) {
                            primaryText
                        } else {
                            secondaryText.copy(alpha = 0.62f)
                        },
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )

                DurationFields(
                    hoursText = alertRepeatHoursText,
                    minutesText = alertRepeatMinutesText,
                    secondsText = alertRepeatSecondsText,
                    onHoursChange = onAlertRepeatHoursTextChange,
                    onMinutesChange = onAlertRepeatMinutesTextChange,
                    onSecondsChange = onAlertRepeatSecondsTextChange,
                    enabled = sensorSettingsEnabled,
                    maxHours = 24,
                    onEditingComplete = {
                        val normalized =
                            normalizeDuration(
                                alertRepeatHoursText,
                                alertRepeatMinutesText,
                                alertRepeatSecondsText,
                                minimumTotalSeconds = 1L,
                                maximumTotalSeconds = MAX_ALERT_REPEAT_SECONDS
                            )
                        onAlertRepeatHoursTextChange(normalized.hoursText)
                        onAlertRepeatMinutesTextChange(normalized.minutesText)
                        onAlertRepeatSecondsTextChange(normalized.secondsText)
                        if (normalized.corrected) {
                            showUvirBottomMessage(context, valueCorrectedText)
                        }
                    }
                )

                Text(
                    text = stringResource(
                        R.string.threshold_repeat_sensor_description
                    ),
                    color =
                        if (sensorSettingsEnabled) {
                            secondaryText
                        } else {
                            secondaryText.copy(alpha = 0.62f)
                        },
                    fontSize = 11.sp,
                    lineHeight = 14.sp
                )
            }

        Column(
                verticalArrangement =
                    Arrangement.spacedBy(UvirSettingsRelatedGap)
            ) {
                Text(
                    text =
                        stringResource(
                            R.string.threshold_sound_label
                        ),
                    color = primaryText,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )

                SettingsChoiceGroup {
                    ThresholdAlertSound.entries
                        .forEach { sound ->
                            ThresholdAlertRadioRow(
                            selected =
                                alertSoundValue ==
                                        sound.name,
                            onClick = {
                                onAlertSoundValueChange(
                                    sound.name
                                )
                                onPreviewThresholdAlertSound(
                                    sound,
                                    alertVolume.roundToInt()
                                )
                            },
                            label =
                                stringResource(
                                    when (sound) {
                                        ThresholdAlertSound.SILENT ->
                                            R.string.threshold_sound_silent

                                        ThresholdAlertSound.VIBRATION ->
                                            R.string.threshold_sound_vibration

                                        ThresholdAlertSound.SINGLE_BEEP ->
                                            R.string.threshold_sound_single_beep

                                        ThresholdAlertSound.DOUBLE_BEEP ->
                                            R.string.threshold_sound_double_beep

                                        ThresholdAlertSound.TRIPLE_BEEP ->
                                            R.string.threshold_sound_triple_beep

                                        ThresholdAlertSound.LONG_BEEP ->
                                            R.string.threshold_sound_long_beep
                                    }
                                )
                            )
                        }
                    }

                if (
                    alertSoundValue !=
                        ThresholdAlertSound.SILENT.name &&
                    alertSoundValue !=
                        ThresholdAlertSound.VIBRATION.name
                ) {
                    Column(
                        verticalArrangement =
                            Arrangement.spacedBy(UvirSettingsRelatedGap)
                    ) {
                        Text(
                            text =
                                stringResource(
                                    R.string.threshold_volume_label,
                                    alertVolume.roundToInt()
                                ),
                            color = primaryText,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )

                        Slider(
                            value = alertVolume,
                            onValueChange = {
                                onAlertVolumeChange(it)
                            },
                            onValueChangeFinished = { commit?.commit() },
                            valueRange = 0f..100f,
                            steps = 9
                        )
                    }
                }

                Text(
                    text =
                        stringResource(
                            R.string.threshold_sound_preview_hint
                        ),
                    color = secondaryText,
                    fontSize = 11.sp
                )
        }

    }

}
