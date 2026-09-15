package me.mondiversi.uvir

import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun UvirManualAcquisitionDialog(
    note: String,
    selectedMode: ManualSaveMode,
    recentManualSessionId: Long?,
    recentManualSessionCount: Int,
    primaryText: Color,
    secondaryText: Color,
    cardColor: Color,
    onNoteChanged: (String) -> Unit,
    onModeSelected: (ManualSaveMode) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    onDismissRequest: () -> Unit
) {
    val darkMode = isSystemInDarkTheme()
    val dialogScrollbar =
        rememberUvirDialogScrollbar(
            color = secondaryText.copy(alpha = 0.58f)
        )

    AlertDialog(
        onDismissRequest = onDismissRequest,
        modifier = dialogScrollbar.dialogModifier,
        title = {
            Text(stringResource(R.string.save_measurement))
        },
        text = {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 460.dp)
                        .then(dialogScrollbar.viewportModifier)
            ) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .verticalScroll(dialogScrollbar.scrollState),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    UvirLimitedNoteField(
                        value = note,
                        onValueChange = onNoteChanged,
                        modifier = Modifier.fillMaxWidth(),
                        label = stringResource(R.string.optional_note),
                        placeholder = stringResource(R.string.note_example),
                        supportingText =
                            stringResource(R.string.add_note_or_leave_empty),
                        secondaryText = secondaryText,
                        minLines = 3
                    )

                    Spacer(Modifier.height(4.dp))

                    listOf(
                        ManualSaveMode.SINGLE to R.string.single_acquisition,
                        ManualSaveMode.LAST_MANUAL_SESSION to
                            R.string.last_manual_session,
                        ManualSaveMode.NEW_MANUAL_SESSION to
                            R.string.new_manual_session
                    ).forEach { (mode, labelResource) ->
                        val enabled =
                            mode != ManualSaveMode.LAST_MANUAL_SESSION ||
                                recentManualSessionId != null
                        val optionLabel = stringResource(labelResource)

                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = enabled) {
                                        onModeSelected(mode)
                                    }
                                    .padding(vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CompositionLocalProvider(
                                LocalMinimumInteractiveComponentSize provides 0.dp
                            ) {
                                RadioButton(
                                    selected = selectedMode == mode,
                                    onClick = {
                                        onModeSelected(mode)
                                    },
                                    enabled = enabled,
                                    modifier = Modifier.size(24.dp),
                                    colors =
                                        RadioButtonDefaults.colors(
                                            selectedColor =
                                                MaterialTheme.colorScheme.primary,
                                            unselectedColor =
                                                if (darkMode) {
                                                    primaryText.copy(alpha = 0.82f)
                                                } else {
                                                    secondaryText
                                                },
                                            disabledSelectedColor =
                                                secondaryText.copy(alpha = 0.45f),
                                            disabledUnselectedColor =
                                                secondaryText.copy(alpha = 0.38f)
                                        )
                                )
                            }

                            Spacer(Modifier.width(10.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text =
                                        if (
                                            mode ==
                                                ManualSaveMode.LAST_MANUAL_SESSION &&
                                            recentManualSessionId != null
                                        ) {
                                            "$optionLabel ($recentManualSessionCount)"
                                        } else {
                                            optionLabel
                                        },
                                    color =
                                        if (enabled) {
                                            primaryText
                                        } else {
                                            secondaryText.copy(alpha = 0.55f)
                                        },
                                    fontSize = 14.sp
                                )

                                if (
                                    mode == ManualSaveMode.SINGLE &&
                                    recentManualSessionId != null
                                ) {
                                    UvirAttentionMessage(
                                        text =
                                            stringResource(
                                                R.string.single_acquisition_ends_manual_session
                                            ),
                                        fontSize = 11.sp,
                                        lineHeight = 14.sp,
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onSave) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onCancel,
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
