package me.mondiversi.uvir

/** Names are resolved from each record's profile, never from the current sensor. */
internal fun exportSensorName(name: String): String = name.ifBlank { "—" }

internal fun exportSensorNames(names: Collection<String>): String =
    names.filter { it.isNotBlank() && it != "—" }
        .distinct()
        .joinToString(" · ")
        .ifBlank { "—" }

internal fun chartExportContextText(
    note: String,
    sensorName: String,
    noteLabel: String = "Note",
    sensorLabel: String = "Sensor",
    emptyNote: String = "No note"
): String = "$noteLabel: ${note.ifBlank { emptyNote }}\n$sensorLabel: ${exportSensorName(sensorName)}"
