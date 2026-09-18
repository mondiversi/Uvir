package me.mondiversi.uvir

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
    alertSoundValue: String,
    onAlertSoundValueChange: (String) -> Unit,
    alertVolume: Float,
    onAlertVolumeChange: (Float) -> Unit,
    onPreviewThresholdAlertSound: (ThresholdAlertSound, Int) -> Unit,
    acquisitionFeedbackSettings: AcquisitionFeedbackSettings,
    onAcquisitionFeedbackSettingsChange: (AcquisitionFeedbackSettings) -> Unit,
    onPreviewAcquisitionFeedback: (ThresholdAlertSound, Int) -> Unit,
    cardColor: Color,
    primaryText: Color,
    secondaryText: Color
) {
    val commit = LocalSettingsCommit.current
    var acquisitionVolumeDraft by
        remember(acquisitionFeedbackSettings.volume) {
            mutableFloatStateOf(acquisitionFeedbackSettings.volume.toFloat())
        }
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
    SettingsPageDescription(
        text = stringResource(
            R.string.sampling_sensor_processing_description
        ),
        color = secondaryText
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

    SettingsGroupDivider(secondaryText)

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
    val settingsNavigation = LocalUvirSettingsNavigation.current
    if (settingsNavigation != null && settingsNavigation.selectedPage == null) {
        UvirSettingsListGroupHeader(
            title = stringResource(R.string.settings_category_phone),
            icon = ConnectivityIconType.PHONE,
            secondaryText = secondaryText
        )
    }
    SettingsSection(
        settingsPage = UvirSettingsPage.ALERTS,
        title =
            stringResource(
                R.string.settings_section_sounds_and_alerts
            ),
        titleIcon =
            ConnectivityIconType.SOUND,
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
            ),
        contentSpacing = UvirIslandSpacing,
        wrapDetailContent = false
    ) {
        CompositionLocalProvider(LocalSettingsCommit provides null) {
            SettingsIsland(
                containerColor = cardColor,
                contentColor = primaryText
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CaptureMeasurementIcon(
                        modifier = Modifier.size(20.dp),
                        tint = primaryText
                    )
                    Text(
                        text = stringResource(R.string.share_acquisition_label),
                        modifier = Modifier.padding(start = 10.dp),
                        color = primaryText,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                SettingsGroupDivider(secondaryText)

                SettingsPageDescription(
                    text = stringResource(R.string.acquisition_feedback_description),
                    color = secondaryText
                )

                Text(
                    text = stringResource(R.string.threshold_sound_label),
                    color = primaryText,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )

                SettingsChoiceGroup {
                    listOf(
                        ThresholdAlertSound.SILENT,
                        ThresholdAlertSound.VIBRATION,
                        ThresholdAlertSound.SINGLE_BEEP
                    ).forEach { sound ->
                        ThresholdAlertRadioRow(
                            selected = acquisitionFeedbackSettings.sound == sound,
                            onClick = {
                                val updated = acquisitionFeedbackSettings.copy(sound = sound)
                                onAcquisitionFeedbackSettingsChange(updated)
                                onPreviewAcquisitionFeedback(sound, updated.volume)
                            },
                            label = stringResource(
                                when (sound) {
                                    ThresholdAlertSound.SILENT -> R.string.threshold_sound_silent
                                    ThresholdAlertSound.VIBRATION -> R.string.threshold_sound_vibration
                                    else -> R.string.threshold_sound_single_beep
                                }
                            )
                        )
                    }
                }

                if (acquisitionFeedbackSettings.sound == ThresholdAlertSound.SINGLE_BEEP) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(UvirSettingsRelatedGap)
                    ) {
                        Text(
                            text = stringResource(
                                R.string.threshold_volume_label,
                                acquisitionFeedbackSettings.volume
                            ),
                            color = primaryText,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )

                        Slider(
                            value = acquisitionVolumeDraft,
                            onValueChange = { value ->
                                acquisitionVolumeDraft = value
                            },
                            onValueChangeFinished = {
                                val volume =
                                    acquisitionVolumeDraft.roundToInt().coerceIn(0, 100)
                                onAcquisitionFeedbackSettingsChange(
                                    acquisitionFeedbackSettings.copy(volume = volume)
                                )
                                onPreviewAcquisitionFeedback(
                                    acquisitionFeedbackSettings.sound,
                                    volume
                                )
                            },
                            valueRange = 0f..100f,
                            steps = 9
                        )
                    }
                }

                Text(
                    text = stringResource(R.string.acquisition_feedback_preview_hint),
                    color = secondaryText,
                    fontSize = 11.sp
                )
            }
        }

        SettingsIsland(
            containerColor = cardColor,
            contentColor = primaryText
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                ConnectivitySectionIcon(
                    type = ConnectivityIconType.ALERT,
                    modifier = Modifier.size(20.dp),
                    tint = primaryText
                )
                Text(
                    text = stringResource(R.string.threshold_alerts_title),
                    modifier = Modifier.padding(start = 10.dp),
                    color = primaryText,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            SettingsGroupDivider(secondaryText)

            SettingsPageDescription(
                    text = stringResource(R.string.threshold_alerts_sound_description_v2),
                color = secondaryText
            )

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
                    listOf(
                        ThresholdAlertSound.SILENT,
                        ThresholdAlertSound.VIBRATION,
                        ThresholdAlertSound.TRIPLE_BEEP,
                        ThresholdAlertSound.LONG_BEEP
                    )
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

}
