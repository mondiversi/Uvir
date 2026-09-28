package me.mondiversi.uvir

internal fun listNoteDisplayText(
    note: String,
    emptyNote: String,
    sessionSequence: Int? = null
): String = buildString {
    if (sessionSequence != null) {
        append('#')
        append(sessionSequence)
        append(" · ")
        append(note.ifBlank { emptyNote })
    } else {
        append(note.ifBlank { emptyNote })
    }
}

/**
 * Builds the note shown for an acquisition. The session-relative sequence is
 * presentation data and never changes the note stored in the database.
 */
internal fun acquisitionDisplayNote(
    note: String,
    automatic: Boolean,
    sessionSequence: Int?,
    emptyNote: String
): String =
    if (automatic && sessionSequence != null) {
        buildString {
            append('#')
            append(sessionSequence)
            append(" · ")
            append(note.ifBlank { emptyNote })
        }
    } else {
        note.ifBlank { emptyNote }
    }

internal fun alertDisplayNote(
    note: String,
    sessionSequence: Int?,
    emptyNote: String
): String =
    if (sessionSequence != null) {
        buildString {
            append('#')
            append(sessionSequence)
            append(" · ")
            append(note.ifBlank { emptyNote })
        }
    } else {
        note.ifBlank { emptyNote }
    }
