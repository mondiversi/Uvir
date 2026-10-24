package me.mondiversi.uvir

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

internal const val UvirImportHoldSeconds = 5

/** The existing deletion gesture and progress, using the normal purple action color. */
@Composable
internal fun UvirHoldToImportButton(
    label: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onConfirmed: () -> Unit
) {
    HoldToConfirmActionButton(
        label = label,
        enabled = enabled,
        holdDurationMillis = UvirImportHoldSeconds * 1_000L,
        actionColor = MaterialTheme.colorScheme.primary,
        modifier = modifier,
        onConfirmed = onConfirmed
    )
}
