package me.mondiversi.uvir

import org.junit.Assert.assertEquals
import org.junit.Test

class UvirHapticsTest {
    @Test fun everyTouchCueHasTheSameModeratePulse() {
        val choice = uvirHapticPulse(UvirHapticCue.CHOICE)
        val on = uvirHapticPulse(UvirHapticCue.TOGGLE_ON)
        val off = uvirHapticPulse(UvirHapticCue.TOGGLE_OFF)
        val command = uvirHapticPulse(UvirHapticCue.COMMAND)
        val hold = uvirHapticPulse(UvirHapticCue.HOLD_COMPLETE)

        assertEquals(26L, choice.durationMs)
        assertEquals(130, choice.amplitude)
        listOf(choice, on, off, command, hold).forEach { pulse ->
            assertEquals(choice, pulse)
        }
    }
}
