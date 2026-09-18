package me.mondiversi.uvir

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

@Composable
internal fun UvirNoteEditDialog(
    initialNote: String,
    cardColor: Color,
    primaryText: Color,
    secondaryText: Color,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var note by rememberSaveable(initialNote) {
        mutableStateOf(initialNote)
    }
    val scrollbar =
        rememberUvirDialogScrollbar(
            color = secondaryText.copy(alpha = 0.58f)
        )

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = scrollbar.dialogModifier,
        title = {
            Text(stringResource(R.string.edit_note))
        },
        text = {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 460.dp)
                        .then(scrollbar.viewportModifier)
            ) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .verticalScroll(scrollbar.scrollState),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    UvirLimitedNoteField(
                        value = note,
                        onValueChange = { note = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = stringResource(R.string.note),
                        placeholder = stringResource(R.string.note_example),
                        secondaryText = secondaryText,
                        minLines = 3
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(limitUvirNote(note).trim())
                }
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors =
                    ButtonDefaults.textButtonColors(
                        contentColor = UvirDestructiveActionColor
                    )
            ) {
                Text(stringResource(R.string.cancel))
            }
        },
        containerColor = cardColor,
        titleContentColor = primaryText,
        textContentColor = secondaryText
    )
}
