package me.mondiversi.uvir

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.ToneGenerator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.sin

internal enum class UvirFeedbackToneProfile { ACQUISITION, ALERT }

internal data class UvirFeedbackTonePattern(
    val profile: UvirFeedbackToneProfile,
    val beepCount: Int,
    val beepMs: Int,
    val pauseMs: Int = 170
) {
    val durationMs: Int get() = beepCount * beepMs + (beepCount - 1).coerceAtLeast(0) * pauseMs
}

internal fun uvirFeedbackTonePattern(
    profile: UvirFeedbackToneProfile,
    sound: ThresholdAlertSound
): UvirFeedbackTonePattern {
    val count = when (sound) {
        ThresholdAlertSound.SILENT, ThresholdAlertSound.VIBRATION -> 0
        ThresholdAlertSound.SINGLE_BEEP -> 1
        ThresholdAlertSound.DOUBLE_BEEP -> 2
        ThresholdAlertSound.TRIPLE_BEEP -> 3
        // Preserve the existing prolonged-warning cadence and duration.
        ThresholdAlertSound.LONG_BEEP -> 8
    }
    return UvirFeedbackTonePattern(profile, count, if (sound == ThresholdAlertSound.LONG_BEEP) 620 else 430)
}

internal const val UvirFeedbackSampleRate = 44100
internal const val UvirAcquisitionToneType = ToneGenerator.TONE_SUP_ERROR

internal fun createUvirAcquisitionToneGenerator(volume: Int): ToneGenerator =
    ToneGenerator(AudioManager.STREAM_ALARM, volume.coerceIn(0, 100))

/** Only alerts use the new PCM timbre; acquisitions retain their original Android tone. */
internal fun uvirFeedbackTonePcm(pattern: UvirFeedbackTonePattern): ShortArray {
    require(pattern.profile == UvirFeedbackToneProfile.ALERT)
    if (pattern.beepCount == 0) return ShortArray(0)
    val samples = ShortArray(pattern.durationMs * UvirFeedbackSampleRate / 1000)
    val beepSamples = pattern.beepMs * UvirFeedbackSampleRate / 1000
    val fadeSamples = 8 * UvirFeedbackSampleRate / 1000
    for (beep in 0 until pattern.beepCount) {
        val start = beep * (pattern.beepMs + pattern.pauseMs) * UvirFeedbackSampleRate / 1000
        var phase = 0.0
        for (index in 0 until beepSamples) {
            val frequency = when {
                index < beepSamples / 2 -> 1800
                else -> 2400
            }
            val envelope = minOf(1.0, index.toDouble() / fadeSamples,
                (beepSamples - 1 - index).toDouble() / fadeSamples)
            samples[start + index] = (sin(phase) * envelope * Short.MAX_VALUE * 0.4).toInt().toShort()
            phase = (phase + 2 * PI * frequency / UvirFeedbackSampleRate) % (2 * PI)
        }
    }
    return samples
}

internal fun uvirFeedbackAudioAttributes(): AudioAttributes = AudioAttributes.Builder()
    .setUsage(AudioAttributes.USAGE_ALARM)
    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
    .build()

/** Allocation/write happen off the UI thread; the caller owns and releases the track. */
internal fun createUvirFeedbackAudioTrack(pcm: ShortArray): AudioTrack {
    require(pcm.isNotEmpty())
    val track = AudioTrack.Builder()
        .setAudioAttributes(uvirFeedbackAudioAttributes())
        .setAudioFormat(AudioFormat.Builder().setSampleRate(UvirFeedbackSampleRate)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
        .setBufferSizeInBytes(pcm.size * 2)
        .setTransferMode(AudioTrack.MODE_STATIC)
        .build()
    try {
        check(track.write(pcm, 0, pcm.size, AudioTrack.WRITE_BLOCKING) == pcm.size)
        check(track.state == AudioTrack.STATE_INITIALIZED)
        return track
    } catch (error: Exception) {
        track.release()
        throw error
    }
}

internal suspend fun playUvirFeedbackTone(
    context: Context,
    profile: UvirFeedbackToneProfile,
    sound: ThresholdAlertSound,
    volume: Int
) = withContext(Dispatchers.IO) {
    val pattern = uvirFeedbackTonePattern(profile, sound)
    if (pattern.beepCount == 0 || volume <= 0) return@withContext
    var track: AudioTrack? = null
    var acquisitionTone: ToneGenerator? = null
    try {
        if (profile == UvirFeedbackToneProfile.ACQUISITION) {
            val tone = createUvirAcquisitionToneGenerator(volume)
            acquisitionTone = tone
            // Original acquisition timbre, intensity, durations and pauses.
            repeat(pattern.beepCount) { index ->
                tone.startTone(UvirAcquisitionToneType, pattern.beepMs)
                delay(pattern.beepMs.toLong())
                tone.stopTone()
                if (index + 1 < pattern.beepCount) delay(pattern.pauseMs.toLong())
            }
        } else {
            track = createUvirFeedbackAudioTrack(uvirFeedbackTonePcm(pattern))
            check(track.setVolume(volume.coerceIn(0, 100) / 100f) == AudioTrack.SUCCESS)
            track.play()
            delay(pattern.durationMs.toLong() + 40L)
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        UvirErrorLog.record(context.applicationContext, source = "feedback_audio",
            message = "Cannot play ${profile.name.lowercase()} feedback: ${error.javaClass.simpleName}")
    } finally {
        acquisitionTone?.let {
            runCatching { it.stopTone() }
            it.release()
        }
        track?.let {
            runCatching { it.stop() }
            it.release()
        }
    }
}
