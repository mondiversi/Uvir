package me.mondiversi.uvir

internal data class AutomaticAcquisitionInput(
    val intervalHours: String,
    val intervalMinutes: String,
    val intervalSeconds: String,
    val note: String,
    val useStartDelay: Boolean,
    val startDelayHours: String,
    val startDelayMinutes: String,
    val startDelaySeconds: String,
    val useDuration: Boolean,
    val durationHours: String,
    val durationMinutes: String,
    val durationSeconds: String,
    val limitEnabled: Boolean,
    val maxAcquisitions: String,
    val externalCommand: Boolean = false
)

internal sealed interface AutomaticAcquisitionValidation {
    data class Valid(
        val request: AutomaticAcquisitionRequest
    ) : AutomaticAcquisitionValidation

    data object InvalidInterval : AutomaticAcquisitionValidation

    data object InvalidSchedule : AutomaticAcquisitionValidation
}

internal fun validateAutomaticAcquisitionInput(
    input: AutomaticAcquisitionInput
): AutomaticAcquisitionValidation {
    if (input.externalCommand) {
        val interval = parseAutomaticDuration(
            input.intervalHours, input.intervalMinutes, input.intervalSeconds
        )
        val startDelay = parseAutomaticDuration(
            input.startDelayHours, input.startDelayMinutes, input.startDelaySeconds
        )
        val duration = parseAutomaticDuration(
            input.durationHours, input.durationMinutes, input.durationSeconds
        )
        return AutomaticAcquisitionValidation.Valid(
            AutomaticAcquisitionRequest(
                intervalSeconds = interval.totalSeconds.coerceAtLeast(1L),
                note = input.note.trim(),
                useStartDelay = input.useStartDelay,
                startDelaySeconds = startDelay.totalSeconds.coerceAtLeast(0L),
                useDuration = input.useDuration,
                durationSeconds = duration.totalSeconds.coerceAtLeast(0L),
                limitEnabled = input.limitEnabled,
                maxAcquisitions =
                    (input.maxAcquisitions.toIntOrNull() ?: 1).coerceAtLeast(1),
                conditionalPlan = null,
                externalCommand = true
            )
        )
    }
    val interval =
        parseAutomaticDuration(
            hoursText = input.intervalHours,
            minutesText = input.intervalMinutes,
            secondsText = input.intervalSeconds
        )

    if (!interval.valid || interval.totalSeconds <= 0L) {
        return AutomaticAcquisitionValidation.InvalidInterval
    }

    val startDelay =
        parseAutomaticDuration(
            hoursText = input.startDelayHours,
            minutesText = input.startDelayMinutes,
            secondsText = input.startDelaySeconds
        )
    val duration =
        parseAutomaticDuration(
            hoursText = input.durationHours,
            minutesText = input.durationMinutes,
            secondsText = input.durationSeconds
        )
    val maxAcquisitions = input.maxAcquisitions.toIntOrNull() ?: 0

    val validStart =
        !input.useStartDelay ||
            (startDelay.valid && startDelay.totalSeconds > 0L)
    val validEnd =
        !input.useDuration ||
            (duration.valid && duration.totalSeconds > 0L)
    val validLimit =
        !input.limitEnabled || maxAcquisitions > 0

    if (!validStart || !validEnd || !validLimit) {
        return AutomaticAcquisitionValidation.InvalidSchedule
    }

    return AutomaticAcquisitionValidation.Valid(
        AutomaticAcquisitionRequest(
            intervalSeconds = interval.totalSeconds,
            note = input.note.trim(),
            useStartDelay = input.useStartDelay,
            startDelaySeconds = startDelay.totalSeconds,
            useDuration = input.useDuration,
            durationSeconds = duration.totalSeconds,
            limitEnabled = input.limitEnabled,
            maxAcquisitions = maxAcquisitions.coerceAtLeast(1)
        )
    )
}

private data class ParsedAutomaticDuration(
    val valid: Boolean,
    val totalSeconds: Long
)

private fun parseAutomaticDuration(
    hoursText: String,
    minutesText: String,
    secondsText: String
): ParsedAutomaticDuration {
    val hours = hoursText.toLongOrNull() ?: 0L
    val minutes = minutesText.toLongOrNull() ?: 0L
    val seconds = secondsText.toLongOrNull() ?: 0L
    val valid = minutes in 0L..60L && seconds in 0L..60L
    val totalSeconds =
        if (valid) {
            hours.coerceAtMost(100_000L) * 3_600L +
                minutes * 60L +
                seconds
        } else {
            0L
        }

    return ParsedAutomaticDuration(
        valid = valid,
        totalSeconds = totalSeconds
    )
}
