package me.mondiversi.uvir

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource

/** Original acquisition form, without external triggering; execution stays on the phone. */
@Composable
internal fun UsbPhoneAutomaticPage(
    state: UsbPhoneState, background: Color, card: Color, text: Color, secondary: Color,
    onBack: () -> Unit, onStart: (AutomaticAcquisitionRequest) -> Unit, onStop: () -> Unit
) {
    val context = LocalContext.current
    val preferences = remember { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }
    fun storedSeconds(key: String, fallback: Long = 0) =
        preferences.getString(key, fallback.toString())?.toLongOrNull()?.coerceAtLeast(0) ?: fallback
    val interval = rememberUsbPhoneDuration(storedSeconds("usb_auto_interval", 10))
    val start = rememberUsbPhoneDuration(storedSeconds("usb_auto_delay_value", storedSeconds("usb_auto_delay")))
    val duration = rememberUsbPhoneDuration(storedSeconds("usb_auto_duration_value", storedSeconds("usb_auto_duration")))
    var useDelay by rememberSaveable { mutableStateOf(preferences.getBoolean("usb_auto_delay_enabled", storedSeconds("usb_auto_delay") > 0)) }
    var useDuration by rememberSaveable { mutableStateOf(preferences.getBoolean("usb_auto_duration_enabled", storedSeconds("usb_auto_duration") > 0)) }
    var useLimit by rememberSaveable { mutableStateOf(preferences.getBoolean("usb_auto_limit_enabled", storedSeconds("usb_auto_limit") > 0)) }
    var maximum by rememberSaveable { mutableStateOf(storedSeconds("usb_auto_limit_value", storedSeconds("usb_auto_limit", 1)).coerceAtLeast(1).toString()) }
    var note by rememberSaveable { mutableStateOf(preferences.getString("usb_auto_note", "").orEmpty()) }
    var conditional by rememberSaveable { mutableStateOf(preferences.getBoolean("usb_auto_conditional", false)) }
    var match by rememberSaveable { mutableStateOf(runCatching {
        AcquisitionConditionMatch.valueOf(preferences.getString("usb_auto_match", AcquisitionConditionMatch.ANY.name)!!)
    }.getOrDefault(AcquisitionConditionMatch.ANY)) }
    var action by rememberSaveable { mutableStateOf(runCatching {
        AcquisitionConditionAction.valueOf(preferences.getString("usb_auto_action", AcquisitionConditionAction.ACQUIRE.name)!!)
    }.getOrDefault(AcquisitionConditionAction.ACQUIRE)) }
    var rules by remember { mutableStateOf(loadAcquisitionConditions(preferences)) }
    var showRules by rememberSaveable { mutableStateOf(false) }
    var showStop by remember { mutableStateOf(false) }
    val active = state.automaticSession != null
    LaunchedEffect(interval.values(), start.values(), duration.values(), useDelay, useDuration,
        useLimit, maximum, note, conditional, match, action, active) {
        if (!active) {
            val edit = preferences.edit().putString("usb_auto_note", note)
                .putBoolean("usb_auto_delay_enabled", useDelay).putBoolean("usb_auto_duration_enabled", useDuration)
                .putBoolean("usb_auto_limit_enabled", useLimit).putBoolean("usb_auto_conditional", conditional)
                .putString("usb_auto_match", match.name).putString("usb_auto_action", action.name)
            interval.seconds()?.takeIf { it in 1..86400 }?.let { edit.putString("usb_auto_interval", it.toString()) }
            start.seconds()?.let { edit.putString("usb_auto_delay_value", it.toString()).putString("usb_auto_delay", if (useDelay) it.toString() else "0") }
            duration.seconds()?.let { edit.putString("usb_auto_duration_value", it.toString()).putString("usb_auto_duration", if (useDuration) it.toString() else "0") }
            maximum.toIntOrNull()?.takeIf { it in 1..MAX_AUTOMATIC_ACQUISITIONS }?.let {
                edit.putString("usb_auto_limit_value", it.toString()).putString("usb_auto_limit", if (useLimit) it.toString() else "0")
            }
            edit.apply()
        }
    }
    fun normalize(value: UsbPhoneDuration, minimum: Long, maximum: Long = MAX_AUTOMATIC_DURATION_HOURS * 3600 + 3599) {
        if (value.normalize(minimum, maximum)) showUvirBottomMessage(context, context.getString(R.string.value_out_of_limits_corrected))
    }
    val validation = validateAutomaticAcquisitionInput(AutomaticAcquisitionInput(
        interval.hours, interval.minutes, interval.seconds, note,
        useDelay, start.hours, start.minutes, start.seconds,
        useDuration, duration.hours, duration.minutes, duration.seconds, useLimit, maximum))
    val request = (validation as? AutomaticAcquisitionValidation.Valid)?.request
        ?.takeIf { it.intervalSeconds <= 86400 }
        ?.copy(conditionalPlan = if (conditional) conditionalPlanFromRules(rules, match, action) else null)
    val valid = request != null && (!conditional || request.conditionalPlan?.isValid() == true)
    if (showRules && !active) UvirAcquisitionConditionsDialog(rules, card, text, secondary, state.sample,
        onSave = { rules = it; saveAcquisitionConditions(preferences, it); showRules = false },
        onDismissRequest = { showRules = false })
    if (showStop) UvirStopAutomaticDialog(card, text, secondary, true,
        onStop = { onStop(); showStop = false }, onContinue = { showStop = false }, onDismissRequest = { showStop = false })
    val listState = rememberLazyListState()
    UvirFullScreenPage(onDismissRequest = onBack,
        title = { UvirMenuTitle(stringResource(R.string.automatic_acquisition_title)) },
        containerColor = background, contentColor = text, lazyListState = listState,
        text = {
            CompositionLocalProvider(LocalUvirSettingsActionButtons provides true) {
                Box(Modifier.testTag("usb_auto_form")) {
                    UvirAutomaticAcquisitionForm(
                        listState = listState,
                        timerHours = interval.hours, timerMinutes = interval.minutes, timerSeconds = interval.seconds,
                        useStartDelay = useDelay, startDelayHours = start.hours, startDelayMinutes = start.minutes, startDelaySeconds = start.seconds,
                        useDuration = useDuration, durationHours = duration.hours, durationMinutes = duration.minutes, durationSeconds = duration.seconds,
                        limitEnabled = useLimit, maxCount = maximum, note = note, error = null,
                        conditionalEnabled = conditional, conditionalMatch = match, conditionalAction = action,
                        conditionalRules = rules, conditionalWaiting = active && state.automaticWaitingForCondition,
                        conditionalLocked = active, externalCommand = false, showExternalCommand = false,
                        numericFormat = loadUvirNumericFormat(context),
                        onConditionalEnabled = { conditional = it }, onConditionalMatch = { match = it }, onConditionalAction = { action = it },
                        onExternalCommandChanged = {}, onConfigureConditions = { showRules = true },
                        cardColor = card, primaryText = text, secondaryText = secondary,
                        onTimerHoursChanged = { interval.hours = it }, onTimerMinutesChanged = { interval.minutes = it }, onTimerSecondsChanged = { interval.seconds = it },
                        onIntervalEditingComplete = { normalize(interval, 1, 86400) },
                        onUseStartDelayChanged = { useDelay = it }, onStartDelayHoursChanged = { start.hours = it },
                        onStartDelayMinutesChanged = { start.minutes = it }, onStartDelaySecondsChanged = { start.seconds = it },
                        onStartDelayEditingComplete = { normalize(start, 1) },
                        onUseDurationChanged = { useDuration = it }, onDurationHoursChanged = { duration.hours = it },
                        onDurationMinutesChanged = { duration.minutes = it }, onDurationSecondsChanged = { duration.seconds = it },
                        onDurationEditingComplete = { normalize(duration, 1) },
                        onLimitEnabledChanged = { useLimit = it }, onMaxCountChanged = { maximum = it },
                        onMaxCountEditingComplete = {
                            val normalized = normalizeBoundedInteger(maximum, 1, MAX_AUTOMATIC_ACQUISITIONS)
                            maximum = normalized.text
                            if (normalized.corrected) showUvirBottomMessage(context, context.getString(R.string.value_out_of_limits_corrected))
                        }, onNoteChanged = { note = limitUvirNote(it) })
                }
            }
        }, floatingActionButton = {
            Box(Modifier.testTag("usb_automatic_action")) {
                UvirAutomaticAcquisitionFloatingAction(active, state.automaticCount,
                    active || (valid && state.ready && !state.busy && !state.measuringReference && state.alertSession == null), false) {
                    if (active) showStop = true else request?.let(onStart)
                }
            }
        })
}
