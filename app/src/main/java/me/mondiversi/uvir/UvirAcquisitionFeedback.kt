package me.mondiversi.uvir

import android.content.SharedPreferences

internal const val KEY_ACQUISITION_FEEDBACK_SOUND =
    "acquisition_feedback_sound"
internal const val KEY_ACQUISITION_FEEDBACK_VOLUME =
    "acquisition_feedback_volume"

internal data class AcquisitionFeedbackSettings(
    val sound: ThresholdAlertSound = ThresholdAlertSound.SINGLE_BEEP,
    val volume: Int = 80
)

internal fun loadAcquisitionFeedbackSettings(
    preferences: SharedPreferences
): AcquisitionFeedbackSettings {
    val sound =
        runCatching {
            ThresholdAlertSound.valueOf(
                preferences.getString(
                    KEY_ACQUISITION_FEEDBACK_SOUND,
                    ThresholdAlertSound.SINGLE_BEEP.name
                ) ?: ThresholdAlertSound.SINGLE_BEEP.name
            )
        }.getOrDefault(ThresholdAlertSound.SINGLE_BEEP)
            .takeIf {
                it == ThresholdAlertSound.SILENT ||
                    it == ThresholdAlertSound.VIBRATION ||
                    it == ThresholdAlertSound.SINGLE_BEEP
            } ?: ThresholdAlertSound.SINGLE_BEEP

    return AcquisitionFeedbackSettings(
        sound = sound,
        volume =
            preferences.getInt(
                KEY_ACQUISITION_FEEDBACK_VOLUME,
                80
            ).coerceIn(0, 100)
    )
}

internal fun saveAcquisitionFeedbackSettings(
    preferences: SharedPreferences,
    settings: AcquisitionFeedbackSettings
) {
    val normalizedSound =
        settings.sound.takeIf {
            it == ThresholdAlertSound.SILENT ||
                it == ThresholdAlertSound.VIBRATION ||
                it == ThresholdAlertSound.SINGLE_BEEP
        } ?: ThresholdAlertSound.SINGLE_BEEP

    preferences.edit()
        .putString(
            KEY_ACQUISITION_FEEDBACK_SOUND,
            normalizedSound.name
        )
        .putInt(
            KEY_ACQUISITION_FEEDBACK_VOLUME,
            settings.volume.coerceIn(0, 100)
        )
        .apply()
}
