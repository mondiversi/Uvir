package me.mondiversi.uvir

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Local UI trial: false restores the original icon, settings index and Back route.
internal const val UVIR_HOME_SETTINGS_DROPDOWN_ENABLED = true

internal data class UvirHomeSettingsMenuEntry(
    val page: UvirSettingsPage?,
    val titleRes: Int,
    val icon: ConnectivityIconType?
)

internal data class UvirHomeSettingsMenuGroup(
    val titleRes: Int?,
    val entries: List<UvirHomeSettingsMenuEntry>
)

/** Only active settings pages are exposed; legacy/unfinished routes stay hidden. */
internal val uvirHomeSettingsMenuGroups = listOf(
    UvirHomeSettingsMenuGroup(R.string.settings_category_sensor, listOf(
        UvirHomeSettingsMenuEntry(UvirSettingsPage.SENSOR_PARAMETERS, R.string.settings_section_sensor_parameters, ConnectivityIconType.GEAR),
        UvirHomeSettingsMenuEntry(UvirSettingsPage.SENSOR_CONNECTION, R.string.settings_section_sensor_connection, ConnectivityIconType.SENSOR_CONNECTION),
        UvirHomeSettingsMenuEntry(UvirSettingsPage.SENSOR_MEASUREMENT, R.string.sensor_info_measurement, ConnectivityIconType.MEASUREMENT),
        UvirHomeSettingsMenuEntry(UvirSettingsPage.DEBUG, R.string.settings_section_debug, ConnectivityIconType.DEBUG)
    )),
    UvirHomeSettingsMenuGroup(R.string.data_management_app_button, listOf(
        UvirHomeSettingsMenuEntry(UvirSettingsPage.APPEARANCE, R.string.settings_section_appearance, ConnectivityIconType.PALETTE),
        UvirHomeSettingsMenuEntry(UvirSettingsPage.ALERTS, R.string.settings_section_notifications, ConnectivityIconType.NOTIFICATIONS),
        UvirHomeSettingsMenuEntry(UvirSettingsPage.LANGUAGE, R.string.settings_section_language, ConnectivityIconType.LANGUAGE),
        UvirHomeSettingsMenuEntry(UvirSettingsPage.LANGUAGE_AND_FORMATS, R.string.settings_section_language_formats, ConnectivityIconType.NUMERIC_FORMAT)
    )),
    UvirHomeSettingsMenuGroup(null, listOf(
        UvirHomeSettingsMenuEntry(UvirSettingsPage.DATA_RESTORE, R.string.data_and_restore_title, ConnectivityIconType.DATA),
        UvirHomeSettingsMenuEntry(null, R.string.version_info, null)
    ))
)

internal fun uvirSettingsBackReturnsHome(page: UvirSettingsPage?, dropdownEnabled: Boolean): Boolean =
    dropdownEnabled || page == null

@Composable
internal fun UvirHomeSettingsDropdown(
    primaryText: Color,
    secondaryText: Color,
    containerColor: Color,
    onOpenPage: (UvirSettingsPage) -> Unit,
    onOpenInfo: () -> Unit,
    onOpened: () -> Unit = {}
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val maxHeight = (LocalConfiguration.current.screenHeightDp.dp * .75f).coerceAtMost(480.dp)
    Box {
        UvirHomeHeaderActionButton(
            contentDescription = stringResource(R.string.acquisition_parameters),
            modifier = Modifier.testTag("home_settings_menu_button"),
            onClick = { onOpened(); expanded = true }
        ) {
            UvirHomeSettingsIcon(tint = primaryText)
        }
        UvirAdaptiveDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = containerColor,
            tonalElevation = 0.dp,
            shape = RoundedCornerShape(16.dp),
            dimAmount = .32f,
            scrollbarColor = secondaryText.copy(alpha = .46f),
            modifier = Modifier.widthIn(max = 320.dp)
                .heightIn(max = maxHeight).testTag("home_settings_menu")
        ) {
            uvirHomeSettingsMenuGroups.forEachIndexed { index, group ->
                if (index > 0) HorizontalDivider(
                    Modifier.padding(vertical = 4.dp), color = primaryText.copy(alpha = .10f))
                group.titleRes?.let { titleRes ->
                    Text(
                        text = stringResource(titleRes),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                        color = secondaryText,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                group.entries.forEach { entry ->
                    DropdownMenuItem(
                        modifier = Modifier.testTag("home_settings_item_${entry.page?.name ?: "INFO"}"),
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (entry.icon != null) {
                                    ConnectivitySectionIcon(entry.icon, Modifier.size(20.dp), primaryText)
                                } else {
                                    UvirMenuIcon(MenuIconType.VERSION_INFO, Modifier.size(20.dp), primaryText)
                                }
                                Text(stringResource(entry.titleRes), color = primaryText)
                            }
                        },
                        onClick = {
                            expanded = false
                            entry.page?.let(onOpenPage) ?: onOpenInfo()
                        }
                    )
                }
            }
        }
    }
}
