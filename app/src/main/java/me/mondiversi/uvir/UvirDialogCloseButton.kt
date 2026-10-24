package me.mondiversi.uvir

import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Neutral dismissal actions share the theme's day/night purple. */
@Composable
internal fun UvirDialogCloseButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    UvirDialogDismissButton(R.string.close, onClick, modifier, enabled)
}

/** Export cancellation uses day/night red; reset dialogs keep their purple secondary action. */
@Composable
internal fun UvirDialogCancelButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    TextButton(onClick = onClick, modifier = modifier, enabled = enabled,
        colors = uvirCancelTextButtonColors()) {
        Text(uvirActionLabel(R.string.cancel))
    }
}

@Composable
internal fun UvirDialogDismissButton(
    labelRes: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    TextButton(onClick = onClick, modifier = modifier, enabled = enabled,
        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary)) {
        Text(uvirActionLabel(labelRes))
    }
}
