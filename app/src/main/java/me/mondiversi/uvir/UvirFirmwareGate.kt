package me.mondiversi.uvir

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription

/** Keep the real editor disabled; only an explicit tap may request an update. */
@Composable
internal fun UvirFirmwareGate(
    required: Boolean,
    label: String,
    tag: String,
    onUpdateRequired: (() -> Unit)?,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val updateNotice = stringResource(R.string.sensor_firmware_update_required_title)
    Box(modifier) {
        content()
        if (required && onUpdateRequired != null) {
            Box(Modifier.matchParentSize().testTag("${tag}_firmware")
                .clickable(role = Role.Button, onClick = onUpdateRequired)
                .semantics {
                    contentDescription = label
                    stateDescription = updateNotice
                })
        }
    }
}
