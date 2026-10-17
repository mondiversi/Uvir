package me.mondiversi.uvir

import android.media.AudioAttributes
import android.media.AudioManager
import android.media.AudioTrack
import org.junit.Assert.*
import org.junit.Test

/** Prepares both audio engines without playing sound or changing Android volume. */
class UvirFeedbackAudioDeviceTest {
    @Test fun originalAcquisitionToneGeneratorStillInitializes() {
        val tone = createUvirAcquisitionToneGenerator(0)
        try {
            assertEquals(android.media.ToneGenerator.TONE_SUP_ERROR, UvirAcquisitionToneType)
        } finally { tone.release() }
    }

    @Test fun newAlertTimbreInitializesOnTheAlarmStreamForEveryAudibleMode() {
        for (sound in ThresholdAlertSound.entries) {
            val pattern = uvirFeedbackTonePattern(UvirFeedbackToneProfile.ALERT, sound)
            if (pattern.beepCount == 0) continue
            val track = createUvirFeedbackAudioTrack(uvirFeedbackTonePcm(pattern))
            try {
                assertEquals(AudioTrack.STATE_INITIALIZED, track.state)
                assertEquals(AudioAttributes.USAGE_ALARM, track.audioAttributes.usage)
                assertEquals(AudioManager.STREAM_ALARM, track.audioAttributes.volumeControlStream)
                assertEquals(AudioTrack.SUCCESS, track.setVolume(.2f))
            } finally { track.release() }
        }
    }
}
