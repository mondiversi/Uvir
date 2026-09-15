package me.mondiversi.uvir

internal const val MAX_AUTOMATIC_DURATION_HOURS = 99_999L
internal const val MAX_AUTOMATIC_ACQUISITIONS = 999_999
internal const val MAX_ALERT_REPEAT_SECONDS = 86_400L

internal data class NormalizedIntegerInput(
    val value: Int,
    val text: String,
    val corrected: Boolean
)

internal data class NormalizedLongInput(
    val value: Long,
    val text: String,
    val corrected: Boolean
)

internal data class NormalizedDecimalInput(
    val value: Float,
    val text: String,
    val corrected: Boolean
)

internal data class NormalizedDurationInput(
    val hoursText: String,
    val minutesText: String,
    val secondsText: String,
    val totalSeconds: Long,
    val corrected: Boolean
)

internal fun normalizeBoundedInteger(
    text: String,
    minimum: Int,
    maximum: Int
): NormalizedIntegerInput {
    val parsed = text.trim().toLongOrNull()
    val value =
        (parsed ?: minimum.toLong())
            .coerceIn(minimum.toLong(), maximum.toLong())
            .toInt()
    val canonical = value.toString()
    return NormalizedIntegerInput(
        value = value,
        text = canonical,
        corrected = parsed == null || parsed != value.toLong() || text.trim() != canonical
    )
}

internal fun normalizeBoundedLong(
    text: String,
    minimum: Long,
    maximum: Long
): NormalizedLongInput {
    val parsed = text.trim().toLongOrNull()
    val value = (parsed ?: minimum).coerceIn(minimum, maximum)
    val canonical = value.toString()
    return NormalizedLongInput(
        value = value,
        text = canonical,
        corrected = parsed == null || parsed != value || text.trim() != canonical
    )
}

internal fun normalizeBoundedDecimal(
    text: String,
    minimum: Float,
    maximum: Float
): NormalizedDecimalInput {
    val parsed = text.trim().replace(',', '.').toFloatOrNull()
    val value =
        parsed
            ?.takeIf(Float::isFinite)
            ?.coerceIn(minimum, maximum)
            ?: minimum
    val canonical = value.toString()
    return NormalizedDecimalInput(
        value = value,
        text = canonical,
        corrected = parsed == null || parsed != value || text.trim().replace(',', '.') != canonical
    )
}

internal fun normalizeNonNegativeDecimal(text: String): NormalizedDecimalInput {
    val parsed = text.trim().replace(',', '.').toFloatOrNull()
    val value = parsed?.takeIf(Float::isFinite)?.coerceAtLeast(0f) ?: 0f
    val canonical = value.toString()
    return NormalizedDecimalInput(
        value = value,
        text = canonical,
        corrected = parsed == null || parsed != value || text.trim().replace(',', '.') != canonical
    )
}

internal fun normalizeDuration(
    hoursText: String,
    minutesText: String,
    secondsText: String,
    minimumTotalSeconds: Long = 0L,
    maximumTotalSeconds: Long = MAX_AUTOMATIC_DURATION_HOURS * 3_600L + 3_599L
): NormalizedDurationInput {
    fun component(text: String): Long =
        text.trim().toLongOrNull()?.coerceAtLeast(0L) ?: 0L

    val hours = component(hoursText)
    val minutes = component(minutesText)
    val seconds = component(secondsText)
    val rawTotal =
        runCatching {
            Math.addExact(
                Math.addExact(Math.multiplyExact(hours, 3_600L), Math.multiplyExact(minutes, 60L)),
                seconds
            )
        }.getOrDefault(Long.MAX_VALUE)
    val total = rawTotal.coerceIn(minimumTotalSeconds, maximumTotalSeconds)
    val canonicalHours = (total / 3_600L).toString()
    val canonicalMinutes = ((total % 3_600L) / 60L).toString()
    val canonicalSeconds = (total % 60L).toString()

    return NormalizedDurationInput(
        hoursText = canonicalHours,
        minutesText = canonicalMinutes,
        secondsText = canonicalSeconds,
        totalSeconds = total,
        corrected =
            hoursText.trim() != canonicalHours ||
                minutesText.trim() != canonicalMinutes ||
                secondsText.trim() != canonicalSeconds
    )
}
