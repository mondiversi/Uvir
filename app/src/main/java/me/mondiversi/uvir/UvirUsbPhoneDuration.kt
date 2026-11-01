package me.mondiversi.uvir

import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable

/** Shared hours/minutes/seconds editor for phone-owned acquisition and alert jobs. */
@Stable
internal class UsbPhoneDuration(initial: List<String>) {
    var hours by mutableStateOf(initial[0])
    var minutes by mutableStateOf(initial[1])
    var seconds by mutableStateOf(initial[2])
    fun values() = listOf(hours, minutes, seconds)
    fun seconds(): Long? {
        val h = hours.toLongOrNull()?.takeIf { it in 0..999999 } ?: return null
        val m = minutes.toLongOrNull()?.takeIf { it in 0..59 } ?: return null
        val s = seconds.toLongOrNull()?.takeIf { it in 0..59 } ?: return null
        return h * 3600 + m * 60 + s
    }
    fun normalize(minimum: Long = 0, maximum: Long = MAX_AUTOMATIC_DURATION_HOURS * 3600 + 3599): Boolean {
        val normalized = normalizeDuration(hours, minutes, seconds, minimum, maximum)
        hours = normalized.hoursText; minutes = normalized.minutesText; seconds = normalized.secondsText
        return normalized.corrected
    }
}

@Composable
internal fun rememberUsbPhoneDuration(seconds: Long): UsbPhoneDuration = rememberSaveable(
    saver = listSaver(save = { it.values() }, restore = { UsbPhoneDuration(it) })
) { UsbPhoneDuration(listOf((seconds / 3600).toString(), (seconds / 60 % 60).toString(), (seconds % 60).toString())) }
