package me.mondiversi.uvir

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Keep the selector implemented but hidden until both layouts are ready.
// This does not disable the automatic scaling used on larger screens.
internal const val UVIR_SETTINGS_MODE_SELECTOR_VISIBLE = false

/** Screen-size modes are distinct from orientation; the large-screen layout is still a preview. */
@Composable
internal fun UvirSettingsModeSelector(
    primaryText: Color,
    secondaryText: Color,
    onPhoneClick: () -> Unit,
    onTabletClick: () -> Unit
) {
    val phoneLabel = stringResource(R.string.settings_mode_small_screen)
    Row(
        modifier = Modifier.background(primaryText.copy(alpha = 0.05f), RoundedCornerShape(12.dp)),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        ViewModeButton(
            selected = true,
            alerted = false,
            onClick = onPhoneClick,
            contentDescription = phoneLabel,
            modifier = Modifier.width(40.dp).testTag("settings_mode_phone"),
            primaryText = primaryText,
            secondaryText = secondaryText
        ) {
            ConnectivitySectionIcon(
                type = ConnectivityIconType.PHONE,
                tint = primaryText,
                modifier = Modifier.testTag("settings_mode_phone_icon"),
                iconSize = 18.dp
            )
        }
        ViewModeButton(
            selected = false,
            alerted = false,
            onClick = onTabletClick,
            contentDescription = stringResource(R.string.settings_mode_large_screen_unavailable),
            modifier = Modifier.width(40.dp).testTag("settings_mode_tablet"),
            primaryText = primaryText,
            secondaryText = secondaryText
        ) {
            ConnectivitySectionIcon(
                type = ConnectivityIconType.DISPLAY,
                tint = secondaryText.copy(alpha = 0.55f),
                modifier = Modifier.testTag("settings_mode_tablet_icon"),
                iconSize = 18.dp
            )
        }
    }
}

/** Future layout choice: both options stay disabled and never change preferences. */
@Composable
internal fun UvirDisplaySettingsIsland(
    cardColor: Color,
    primaryText: Color,
    secondaryText: Color
) {
    SettingsIsland(containerColor = cardColor, contentColor = primaryText) {
        SettingsIslandHeader(
            title = stringResource(R.string.settings_section_display),
            titleColor = primaryText,
            titleIcon = ConnectivityIconType.DISPLAY
        )
        Column(
            modifier = Modifier.selectableGroup(),
            verticalArrangement = Arrangement.spacedBy(UvirSettingsChoiceSpacing)
        ) {
            UvirFormatDescription(
                stringResource(R.string.settings_display_description),
                secondaryText
            )
            Spacer(Modifier.height(4.dp))
            listOf(
                R.string.settings_mode_small_screen to true,
                R.string.settings_mode_large_screen to false
            ).forEach { (label, selected) ->
                UvirSettingsRadioOption(
                    selected = selected,
                    title = stringResource(label),
                    primaryText = primaryText,
                    secondaryText = secondaryText,
                    enabled = false,
                    onClick = {}
                )
            }
        }
        UvirFormatDescription(
            stringResource(R.string.settings_display_unavailable),
            secondaryText
        )
    }
}

/** A single accessible checkbox action, including its label, with a 48 dp target. */
@Composable
internal fun UvirDisplayCheckbox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    title: String,
    primaryText: Color,
    description: String? = null,
    secondaryText: Color = primaryText,
    enabled: Boolean = true
) {
    Column(
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .toggleable(value = checked, enabled = enabled, role = Role.Checkbox,
                onValueChange = onCheckedChange),
        verticalArrangement = Arrangement.spacedBy(UvirSettingsRelatedGap)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = if (description == null) 48.dp else 24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                Checkbox(checked = checked, onCheckedChange = null, enabled = enabled,
                    modifier = Modifier.size(24.dp), colors = uvirCheckboxColors())
            }
            Spacer(Modifier.width(6.dp))
            Text(title, modifier = Modifier.weight(1f), fontSize = 14.sp, lineHeight = UvirSettingsCheckboxLineHeight,
                color = if (enabled) primaryText else secondaryText.copy(alpha = 0.62f))
        }
        description?.let {
            Text(it, fontSize = 11.sp, lineHeight = 14.sp,
                color = if (enabled) secondaryText else secondaryText.copy(alpha = 0.62f))
        }
    }
}
