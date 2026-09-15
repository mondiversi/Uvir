package me.mondiversi.uvir

import android.icu.text.BreakIterator
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

internal const val UVIR_NOTE_MAX_CHARACTERS = 96

/**
 * Counts user-visible Unicode characters rather than UTF-8 bytes or UTF-16
 * code units. This keeps combining marks and joined emoji intact and gives the
 * same limit to Latin, RTL and CJK notes.
 */
internal fun uvirNoteCharacterCount(text: String): Int {
    if (text.isEmpty()) return 0
    val iterator =
        BreakIterator.getCharacterInstance(Locale.ROOT).apply {
            setText(text)
        }
    var count = 0
    var boundary = iterator.first()
    while (boundary != BreakIterator.DONE) {
        val next = iterator.next()
        if (next == BreakIterator.DONE) break
        count += 1
        boundary = next
    }
    return count
}

internal fun limitUvirNote(
    text: String,
    maximumCharacters: Int = UVIR_NOTE_MAX_CHARACTERS
): String {
    if (text.isEmpty() || maximumCharacters <= 0) {
        return if (maximumCharacters <= 0) "" else text
    }
    val iterator =
        BreakIterator.getCharacterInstance(Locale.ROOT).apply {
            setText(text)
        }
    var count = 0
    var end = iterator.first()
    while (count < maximumCharacters) {
        val next = iterator.next()
        if (next == BreakIterator.DONE) return text
        end = next
        count += 1
    }
    return if (iterator.next() == BreakIterator.DONE) text else text.substring(0, end)
}

@Composable
internal fun UvirLimitedNoteField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    secondaryText: Color,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    supportingText: String? = null,
    minLines: Int = 2
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = { onValueChange(limitUvirNote(it)) },
            modifier = Modifier.fillMaxWidth(),
            label = { AdaptiveFieldLabel(label) },
            placeholder = placeholder?.let { text ->
                { Text(text) }
            },
            colors = UvirOutlinedTextFieldColors(),
            minLines = minLines,
            maxLines = 4
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            if (supportingText != null) {
                Text(
                    text = supportingText,
                    modifier = Modifier.weight(1f),
                    color = secondaryText,
                    fontSize = 12.sp,
                    lineHeight = 15.sp
                )
            }
            Text(
                text =
                    stringResource(
                        R.string.note_length_counter,
                        uvirNoteCharacterCount(value),
                        UVIR_NOTE_MAX_CHARACTERS
                    ),
                color = secondaryText,
                fontSize = 12.sp,
                lineHeight = 15.sp
            )
        }
    }
}
