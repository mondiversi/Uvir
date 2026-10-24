package me.mondiversi.uvir

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/** One full-width open-record target, with a decorative chevron at the content edge. */
@Composable
internal fun UvirSessionRecordHeader(
    key: String,
    primaryText: Color,
    secondaryText: Color,
    onOpen: (() -> Unit)?,
    content: @Composable RowScope.() -> Unit
) {
    val interaction = if (onOpen == null) Modifier else Modifier.clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = ripple(color = primaryText), role = Role.Button, onClick = onOpen)
    Row(Modifier.fillMaxWidth().heightIn(min = 32.dp).then(interaction)
        .testTag("session_record_header_$key")
        .padding(horizontal = 16.dp, vertical = UvirSavedValueHalfGap),
        horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        content()
        if (onOpen != null) UvirDisclosureChevron(
            Modifier.testTag("session_record_chevron_$key"), secondaryText, edgeAligned = true)
    }
}
