package me.mondiversi.uvir

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun formatDateTime(timestamp: Long): String {
    return SimpleDateFormat(
        "dd/MM/yyyy  HH:mm:ss",
        Locale.getDefault()
    ).format(Date(timestamp))
}

fun formatDetailDateTime(timestamp: Long): String {
    return SimpleDateFormat(
        "dd/MM/yyyy  HH:mm:ss",
        Locale.getDefault()
    ).format(Date(timestamp))
}

internal fun formatSensorListLastActivity(timestamp: Long): String =
    if (timestamp > 0L) formatDetailDateTime(timestamp).replace("  ", "\n") else "—"

fun formatAutomaticSessionDateTime(
    timestamp: Long
): String {
    return SimpleDateFormat(
        "dd/MM/yyyy  HH:mm:ss",
        Locale.getDefault()
    ).format(Date(timestamp))
}
