package me.mondiversi.uvir

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.os.VibrationAttributes
import android.os.SystemClock
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext

/** Short feedback for the fixed capture, record and stop controls only. */
internal enum class UvirHapticCue { CHOICE, TOGGLE_ON, TOGGLE_OFF, COMMAND, HOLD_COMPLETE }

internal data class UvirHapticPulse(val durationMs: Long, val amplitude: Int)

internal fun uvirHapticPulse(cue: UvirHapticCue): UvirHapticPulse = when (cue) {
    UvirHapticCue.CHOICE,
    UvirHapticCue.TOGGLE_ON,
    UvirHapticCue.TOGGLE_OFF,
    UvirHapticCue.COMMAND,
    UvirHapticCue.HOLD_COMPLETE -> UvirHapticPulse(26L, 130)
}

internal class UvirHapticController(private val context: Context) {
    private var lastPulseAtMs = 0L
    private val vibrator: Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }

    private val touchAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
        .build()

    @Suppress("DEPRECATION")
    fun uvirHaptic(cue: UvirHapticCue) {
        if (Settings.System.getInt(
                context.contentResolver,
                Settings.System.HAPTIC_FEEDBACK_ENABLED,
                1
            ) == 0
        ) return
        val motor = vibrator?.takeIf { it.hasVibrator() } ?: return
        val now = SystemClock.elapsedRealtime()
        // A long press followed by a menu selection must not feel like two strong clicks.
        if (now - lastPulseAtMs < 80L) return
        lastPulseAtMs = now
        val pulse = uvirHapticPulse(cue)
        val effect = VibrationEffect.createOneShot(pulse.durationMs, pulse.amplitude)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            motor.vibrate(
                effect,
                VibrationAttributes.Builder()
                    .setUsage(VibrationAttributes.USAGE_TOUCH)
                    .build()
            )
        } else {
            motor.vibrate(effect, touchAttributes)
        }
    }
}

/** One controller per process also coalesces a touch seen by nested clickable components. */
private object UvirHapticRegistry {
    @Volatile private var controller: UvirHapticController? = null

    fun get(context: Context): UvirHapticController =
        controller ?: synchronized(this) {
            controller ?: UvirHapticController(context).also { controller = it }
        }
}

@Composable
internal fun rememberUvirHapticController(): UvirHapticController {
    val applicationContext = LocalContext.current.applicationContext
    return remember(applicationContext) { UvirHapticRegistry.get(applicationContext) }
}

/** Immediate, non-consuming touch feedback; the control still decides on release whether to act. */
@Composable
internal fun Modifier.uvirHapticOnPress(enabled: Boolean = true): Modifier {
    val haptics = rememberUvirHapticController()
    return if (!enabled) this else this.pointerInput(haptics) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            haptics.uvirHaptic(UvirHapticCue.CHOICE)
            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
            } while (event.changes.any { it.pressed })
        }
    }
}
