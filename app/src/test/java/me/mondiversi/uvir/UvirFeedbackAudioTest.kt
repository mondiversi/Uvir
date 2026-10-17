package me.mondiversi.uvir

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import org.junit.Assert.*
import org.junit.Test

class UvirFeedbackAudioTest {
    @Test fun selectedBeepCountsTimingAndSilentModesDoNotChange() {
        val counts = mapOf(ThresholdAlertSound.SILENT to 0, ThresholdAlertSound.VIBRATION to 0,
            ThresholdAlertSound.SINGLE_BEEP to 1, ThresholdAlertSound.DOUBLE_BEEP to 2,
            ThresholdAlertSound.TRIPLE_BEEP to 3, ThresholdAlertSound.LONG_BEEP to 8)
        for (profile in UvirFeedbackToneProfile.entries) for ((sound, count) in counts) {
            val pattern = uvirFeedbackTonePattern(profile, sound)
            assertEquals(count, pattern.beepCount)
            val duration = if (count == 0) 0 else count * (if (sound == ThresholdAlertSound.LONG_BEEP) 620 else 430) + (count - 1) * 170
            assertEquals(duration, pattern.durationMs)
            if (profile == UvirFeedbackToneProfile.ALERT) {
                val pcm = uvirFeedbackTonePcm(pattern)
                assertEquals(duration * UvirFeedbackSampleRate / 1000, pcm.size)
                if (count > 0) { assertEquals(0.toShort(), pcm.first()); assertEquals(0.toShort(), pcm.last()) }
            }
        }
    }

    @Test fun acquisitionKeepsItsOriginalAndroidToneAndCannotUseTheNewPcm() {
        assertEquals(android.media.ToneGenerator.TONE_SUP_ERROR, UvirAcquisitionToneType)
        val original = uvirFeedbackTonePattern(UvirFeedbackToneProfile.ACQUISITION,
            ThresholdAlertSound.SINGLE_BEEP)
        assertTrue(runCatching { uvirFeedbackTonePcm(original) }.exceptionOrNull() is IllegalArgumentException)
    }

    @Test fun alertHasTwoPitchesWithoutAddingExtraBeeps() {
        fun pcm(profile: UvirFeedbackToneProfile) = uvirFeedbackTonePcm(
            uvirFeedbackTonePattern(profile, ThresholdAlertSound.SINGLE_BEEP))
        fun energy(pcm: ShortArray, hz: Int, startMs: Int, endMs: Int): Double {
            var real = 0.0; var imaginary = 0.0
            for (index in startMs * UvirFeedbackSampleRate / 1000 until endMs * UvirFeedbackSampleRate / 1000) {
                val phase = 2 * PI * hz * index / UvirFeedbackSampleRate
                real += pcm[index] * cos(phase); imaginary += pcm[index] * sin(phase)
            }
            return real * real + imaginary * imaginary
        }
        val alert = pcm(UvirFeedbackToneProfile.ALERT)
        assertTrue(energy(alert, 1800, 20, 180) > 100 * energy(alert, 1400, 20, 180))
        assertTrue(energy(alert, 2400, 240, 400) > 100 * energy(alert, 1800, 240, 400))
        assertEquals(430 * UvirFeedbackSampleRate / 1000, alert.size)
        assertTrue(alert.all { kotlin.math.abs(it.toInt()) <= Short.MAX_VALUE * .401 })
    }

    @Test fun pausesRemainSilentAndEveryBeepHasSmoothEdges() {
        for (profile in listOf(UvirFeedbackToneProfile.ALERT)) {
            val pattern = uvirFeedbackTonePattern(profile, ThresholdAlertSound.TRIPLE_BEEP)
            val pcm = uvirFeedbackTonePcm(pattern)
            for (beep in 0 until pattern.beepCount - 1) {
                val start = (beep * (pattern.beepMs + pattern.pauseMs) + pattern.beepMs) * UvirFeedbackSampleRate / 1000
                val end = (beep + 1) * (pattern.beepMs + pattern.pauseMs) * UvirFeedbackSampleRate / 1000
                assertTrue(pcm.sliceArray(start until end).all { it == 0.toShort() })
            }
        }
    }
}
