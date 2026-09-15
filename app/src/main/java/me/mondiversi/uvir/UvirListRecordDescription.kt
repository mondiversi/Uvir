package me.mondiversi.uvir

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal const val UvirListNoteMaximumWidthFraction = 0.5f

/** The note (including its sequence) uses at most half the row; the sensor gets the rest. */
@Composable
internal fun UvirListRecordDescription(
    sensorName: String,
    note: String,
    emptyNote: String,
    sessionSequence: Int?,
    color: Color
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val noteMaximumWidth = maxWidth * UvirListNoteMaximumWidthFraction
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = listNoteDisplayText(note, emptyNote, sessionSequence),
                modifier = Modifier.widthIn(max = noteMaximumWidth),
                color = color,
                fontSize = 12.sp,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
            Text("·", color = color, fontSize = 12.sp, maxLines = 1)
            Text(
                text = sensorName.ifBlank { "—" },
                modifier = Modifier.weight(1f, fill = false),
                color = color,
                fontSize = 12.sp,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
