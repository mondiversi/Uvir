package me.mondiversi.uvir

import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.core.content.edit
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

internal const val UVIR_DATE_FORMAT_KEY = "date_format"
internal const val UVIR_TIME_FORMAT_KEY = "time_format"

enum class UvirDateFormat(
    val storedValue: String
) {
    SYSTEM("system"),
    INTERNATIONAL("international"),
    EUROPEAN("european"),
    AMERICAN("american");

    companion object {
        fun fromStoredValue(value: String?): UvirDateFormat =
            entries.firstOrNull {
                it.storedValue == value
            } ?: SYSTEM
    }
}

enum class UvirTimeFormat(
    val storedValue: String
) {
    SYSTEM("system"),
    H24("24_hour"),
    H12("12_hour");

    companion object {
        fun fromStoredValue(value: String?): UvirTimeFormat =
            entries.firstOrNull {
                it.storedValue == value
            } ?: SYSTEM
    }
}

internal val LocalUvirDateFormat =
    staticCompositionLocalOf {
        UvirDateFormat.SYSTEM
    }

internal val LocalUvirTimeFormat =
    staticCompositionLocalOf {
        UvirTimeFormat.H24
    }

internal fun loadUvirDateFormat(
    context: Context
): UvirDateFormat =
    UvirDateFormat.fromStoredValue(
        context.getSharedPreferences(
            UVIR_PREFERENCES_NAME,
            Context.MODE_PRIVATE
        ).getString(
            UVIR_DATE_FORMAT_KEY,
            UvirDateFormat.SYSTEM.storedValue
        )
    )

internal fun saveUvirDateFormat(
    context: Context,
    format: UvirDateFormat
) {
    context.getSharedPreferences(
        UVIR_PREFERENCES_NAME,
        Context.MODE_PRIVATE
    ).edit {
        putString(
            UVIR_DATE_FORMAT_KEY,
            format.storedValue
        )
    }
}

internal fun loadUvirTimeFormat(
    context: Context
): UvirTimeFormat =
    UvirTimeFormat.fromStoredValue(
        context.getSharedPreferences(
            UVIR_PREFERENCES_NAME,
            Context.MODE_PRIVATE
        ).getString(
            UVIR_TIME_FORMAT_KEY,
            UvirTimeFormat.SYSTEM.storedValue
        )
    )

internal fun saveUvirTimeFormat(
    context: Context,
    format: UvirTimeFormat
) {
    context.getSharedPreferences(
        UVIR_PREFERENCES_NAME,
        Context.MODE_PRIVATE
    ).edit {
        putString(
            UVIR_TIME_FORMAT_KEY,
            format.storedValue
        )
    }
}

internal fun resolveUvirTimeFormat(
    context: Context,
    format: UvirTimeFormat
): UvirTimeFormat =
    if (format == UvirTimeFormat.SYSTEM) {
        if (android.text.format.DateFormat.is24HourFormat(context)) {
            UvirTimeFormat.H24
        } else {
            UvirTimeFormat.H12
        }
    } else {
        format
    }

private fun uvirDateFormatter(
    format: UvirDateFormat,
    locale: Locale,
    timeZone: TimeZone
): DateFormat =
    when (format) {
        UvirDateFormat.SYSTEM ->
            DateFormat.getDateInstance(
                DateFormat.SHORT,
                locale
            )

        UvirDateFormat.INTERNATIONAL ->
            SimpleDateFormat(
                "yyyy-MM-dd",
                Locale.US
            )

        UvirDateFormat.EUROPEAN ->
            SimpleDateFormat(
                "dd/MM/yyyy",
                Locale.US
            )

        UvirDateFormat.AMERICAN ->
            SimpleDateFormat(
                "MM/dd/yyyy",
                Locale.US
            )
    }.apply {
        this.timeZone = timeZone
    }

internal fun formatUvirDateOnly(
    timestamp: Long,
    format: UvirDateFormat,
    locale: Locale = Locale.getDefault(),
    timeZone: TimeZone = TimeZone.getDefault()
): String =
    if (timestamp <= 0L) {
        "—"
    } else {
        uvirDateFormatter(
            format,
            locale,
            timeZone
        ).format(Date(timestamp))
    }

internal fun formatUvirTimeOnly(
    timestamp: Long,
    format: UvirTimeFormat,
    locale: Locale = Locale.getDefault(),
    timeZone: TimeZone = TimeZone.getDefault()
): String =
    if (timestamp <= 0L) {
        "—"
    } else when (format) {
        UvirTimeFormat.SYSTEM ->
            DateFormat.getTimeInstance(
                DateFormat.MEDIUM,
                locale
            )

        UvirTimeFormat.H24 ->
            SimpleDateFormat(
                "HH:mm:ss",
                locale
            )

        UvirTimeFormat.H12 ->
            SimpleDateFormat(
                "h:mm:ss a",
                locale
            )
    }.apply {
        this.timeZone = timeZone
    }.format(Date(timestamp))

internal fun formatUvirDateTime(
    timestamp: Long,
    format: UvirDateFormat,
    locale: Locale = Locale.getDefault(),
    timeZone: TimeZone = TimeZone.getDefault(),
    separator: String = "  ",
    timeFormat: UvirTimeFormat = UvirTimeFormat.H24
): String {
    if (timestamp <= 0L) return "—"
    val date =
        formatUvirDateOnly(
            timestamp,
            format,
            locale,
            timeZone
        )
    val time =
        formatUvirTimeOnly(
            timestamp = timestamp,
            format = timeFormat,
            locale = locale,
            timeZone = timeZone
        )
    return "$date$separator$time"
}

internal fun formatUvirInternationalDateTime(
    timestamp: Long
): String =
    formatUvirDateTime(
        timestamp = timestamp,
        format = UvirDateFormat.INTERNATIONAL,
        locale = Locale.US,
        separator = " ",
        timeFormat = UvirTimeFormat.H24
    )

fun formatDateTime(
    timestamp: Long,
    format: UvirDateFormat = UvirDateFormat.SYSTEM,
    timeFormat: UvirTimeFormat = UvirTimeFormat.H24
): String =
    formatUvirDateTime(
        timestamp,
        format,
        timeFormat = timeFormat
    )

fun formatDetailDateTime(
    timestamp: Long,
    format: UvirDateFormat = UvirDateFormat.SYSTEM,
    timeFormat: UvirTimeFormat = UvirTimeFormat.H24
): String =
    formatUvirDateTime(
        timestamp,
        format,
        timeFormat = timeFormat
    )

internal fun formatSensorListLastActivity(
    timestamp: Long,
    format: UvirDateFormat = UvirDateFormat.SYSTEM,
    timeFormat: UvirTimeFormat = UvirTimeFormat.H24
): String =
    if (timestamp > 0L) {
        formatDetailDateTime(timestamp, format, timeFormat)
            .replace("  ", "\n")
    } else {
        "—"
    }

fun formatAutomaticSessionDateTime(
    timestamp: Long,
    format: UvirDateFormat = UvirDateFormat.SYSTEM,
    timeFormat: UvirTimeFormat = UvirTimeFormat.H24
): String =
    formatUvirDateTime(
        timestamp,
        format,
        timeFormat = timeFormat
    )
