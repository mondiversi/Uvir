package me.mondiversi.uvir

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource

/** Uses the original registration form; the USB edition only changes who executes the job. */
@Composable
internal fun UsbPhoneAlertRegistrationPage(
    state: UsbPhoneState, background: Color, card: Color, text: Color, secondary: Color,
    onBack: () -> Unit, onStart: (ThresholdAlertSettings, String) -> Unit, onStop: () -> Unit
) {
    val context = LocalContext.current
    val preferences = remember { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }
    var settings by remember { mutableStateOf(loadThresholdAlertSettings(preferences).withMonitoringStopped()) }
    val repeat = rememberUsbAlertDuration(settings.repeatSeconds.toLong())
    val delay = rememberUsbAlertDuration(settings.startDelaySeconds)
    val duration = rememberUsbAlertDuration(settings.durationSeconds)
    var useDelay by rememberSaveable { mutableStateOf(settings.startDelaySeconds > 0) }
    var useDuration by rememberSaveable { mutableStateOf(settings.durationSeconds > 0) }
    var useLimit by rememberSaveable { mutableStateOf(settings.maxRegistrations > 0) }
    var limit by rememberSaveable { mutableStateOf(settings.maxRegistrations.coerceAtLeast(1).toString()) }
    var doNotRecord by rememberSaveable { mutableStateOf(!settings.recordEvents) }
    var note by rememberSaveable { mutableStateOf(preferences.getString("usb_alert_note", "").orEmpty()) }
    var showRules by rememberSaveable { mutableStateOf(false) }
    var showStop by remember { mutableStateOf(false) }
    val active = state.alertSession != null
    val repeatSeconds = repeat.seconds()?.takeIf { it in 1..MAX_ALERT_REPEAT_SECONDS }
    val delaySeconds = if (useDelay) delay.seconds()?.takeIf { it > 0 } else 0L
    val durationSeconds = if (useDuration) duration.seconds()?.takeIf { it > 0 } else 0L
    val maximum = if (useLimit && !doNotRecord) limit.toIntOrNull()?.takeIf { it > 0 } else 0
    val valid = repeatSeconds != null && delaySeconds != null && durationSeconds != null && maximum != null
    val configured = if (valid) settings.copy(enabled = false, repeatSeconds = repeatSeconds!!.toInt(),
        startDelaySeconds = delaySeconds!!, durationSeconds = durationSeconds!!, maxRegistrations = maximum!!,
        recordEvents = !doNotRecord) else settings
    LaunchedEffect(configured, valid, note, active) {
        if (valid && !active) {
            saveThresholdAlertSettings(preferences, configured)
            preferences.edit().putString("usb_alert_note", note).apply()
        }
    }
    if (showStop) UvirStopAllAlertsDialog(card, text, secondary, true,
        onStop = { onStop(); showStop = false }, onContinue = { showStop = false }, onDismissRequest = { showStop = false })
    val listState = rememberLazyListState()
    UvirFullScreenPage(onDismissRequest = onBack,
        title = { UvirMenuTitle(stringResource(R.string.alert_registration_title)) },
        containerColor = background, contentColor = text, lazyListState = listState,
        text = {
            CompositionLocalProvider(LocalUvirSettingsActionButtons provides true) {
                UvirAlertRegistrationForm(listState = listState,
                    repeatHours = repeat.hours, repeatMinutes = repeat.minutes, repeatSeconds = repeat.seconds,
                    useStartDelay = useDelay, startHours = delay.hours, startMinutes = delay.minutes, startSeconds = delay.seconds,
                    useDuration = useDuration, durationHours = duration.hours, durationMinutes = duration.minutes, durationSeconds = duration.seconds,
                    limitEnabled = useLimit, maxRegistrations = limit, doNotRecord = doNotRecord, note = note,
                    active = active, configuredCount = settings.rules.count { it.enabled },
                    cardColor = card, primaryText = text, secondaryText = secondary,
                    onRepeatHours = { repeat.hours = it }, onRepeatMinutes = { repeat.minutes = it }, onRepeatSeconds = { repeat.seconds = it },
                    onStartDelayEnabled = { useDelay = it }, onStartHours = { delay.hours = it },
                    onStartMinutes = { delay.minutes = it }, onStartSeconds = { delay.seconds = it },
                    onDurationEnabled = { useDuration = it }, onDurationHours = { duration.hours = it },
                    onDurationMinutes = { duration.minutes = it }, onDurationSeconds = { duration.seconds = it },
                    onLimitEnabled = { useLimit = it }, onMaxRegistrations = { limit = it },
                    onDoNotRecord = { doNotRecord = it }, onNote = { note = limitUvirNote(it) },
                    onConfigureValues = { showRules = true })
            }
        }, floatingActionButton = {
            Box(Modifier.testTag("usb_alert_registration_action")) {
                UvirAlertRegistrationActionButton(active, state.alertCount,
                    active || (valid && state.ready && state.automaticSession == null && settings.rules.any { it.enabled }),
                    onClick = { if (active) showStop = true else onStart(configured, note) })
            }
        })
    if (showRules) UvirAlertValuesScreen(settings.rules, state.sample, !active, card, text, secondary,
        onSave = { rules ->
            settings = configured.copy(rules = rules)
            saveThresholdAlertSettings(preferences, settings)
            showRules = false
        }, onBack = { showRules = false })
}

private class UsbAlertDuration(initial: List<String>) {
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
}

@Composable
private fun rememberUsbAlertDuration(seconds: Long): UsbAlertDuration = rememberSaveable(
    saver = listSaver(save = { it.values() }, restore = { UsbAlertDuration(it) })
) { UsbAlertDuration(listOf((seconds / 3600).toString(), (seconds / 60 % 60).toString(), (seconds % 60).toString())) }
