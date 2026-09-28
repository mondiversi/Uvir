package me.mondiversi.uvir

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp

/** One-line list note, including the session sequence when available. */
@Composable
internal fun UvirListRecordDescription(
    note: String,
    emptyNote: String,
    sessionSequence: Int?,
    color: Color
) {
    Text(
        text = listNoteDisplayText(note, emptyNote, sessionSequence),
        modifier = Modifier.fillMaxWidth(),
        color = color,
        fontSize = 12.sp,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis
    )
}
