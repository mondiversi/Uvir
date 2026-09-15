package me.mondiversi.uvir

import android.graphics.Paint

internal fun wrapChartExportText(
    text: String,
    paint: Paint,
    maxWidth: Float
): List<String> {
    val normalized =
        text
            .replace('\r', ' ')
            .trim()

    if (normalized.isEmpty()) {
        return listOf("—")
    }

    val lines = mutableListOf<String>()
    normalized.split('\n').forEach { paragraph ->
        val words = paragraph.trim().split(Regex("\\s+"))
        var current = ""

        words.forEach { word ->
            val candidate =
                if (current.isEmpty()) word else "$current $word"

            if (paint.measureText(candidate) <= maxWidth) {
                current = candidate
            } else {
                if (current.isNotEmpty()) {
                    lines += current
                }

                if (paint.measureText(word) <= maxWidth) {
                    current = word
                } else {
                    var fragment = ""
                    word.forEach { character ->
                        val extended = fragment + character
                        if (
                            fragment.isNotEmpty() &&
                            paint.measureText(extended) > maxWidth
                        ) {
                            lines += fragment
                            fragment = character.toString()
                        } else {
                            fragment = extended
                        }
                    }
                    current = fragment
                }
            }
        }

        if (current.isNotEmpty()) {
            lines += current
        }
    }

    return lines.ifEmpty { listOf("—") }
}

internal fun sessionChartNote(
    records: List<SavedRecordDetail>
): String {
    val notes =
        records
            .map { it.note.trim() }
            .filter { it.isNotEmpty() }
            .distinct()

    return if (notes.isEmpty()) {
        "—"
    } else {
        notes.joinToString(" · ")
    }
}

internal fun chartExportIdentifierLine(
    recordLabel: String,
    recordId: Long?,
    sessionId: Long?
): String {
    val recordValue = recordId?.toString() ?: "—"
    val sessionValue = sessionId?.toString() ?: "—"
    return "$recordLabel / session ID: $recordValue / $sessionValue"
}
