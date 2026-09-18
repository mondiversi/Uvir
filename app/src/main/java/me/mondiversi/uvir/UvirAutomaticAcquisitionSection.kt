package me.mondiversi.uvir

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp

/** A draft option: its checkbox expands/enables it, using the shared full-width
 * settings header and bring-into-view behavior. It never starts a sensor job. */
@Composable
internal fun UvirAutomaticAcquisitionSection(
    title: String,
    icon: AutomaticSettingIconType,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    cardColor: Color,
    primaryText: Color,
    secondaryText: Color,
    controlsEnabled: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    SettingsSection(
        title = title, containerColor = cardColor, titleColor = primaryText,
        dividerColor = secondaryText.copy(alpha = 0.28f),
        titleIconContent = { tint -> AutomaticSettingIcon(type = icon, tint = tint) },
        expanded = expanded, onExpandedChange = onExpandedChange,
        enabled = controlsEnabled,
        headerEnabled = controlsEnabled,
        highlightExpandedHeader = false,
        headerControl = UvirSettingsHeaderControl.CHECKBOX,
        showExpandedDivider = true,
        titleFontSize = 14.sp,
        content = content
    )
}
