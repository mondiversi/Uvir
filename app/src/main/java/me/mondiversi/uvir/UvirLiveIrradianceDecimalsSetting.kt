package me.mondiversi.uvir

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun UvirLiveIrradianceDecimalsSetting(
    digits: Int,
    onDigitsChange: (Int) -> Unit,
    primaryText: Color,
    secondaryText: Color
) {
    val label = stringResource(R.string.live_irradiance_decimals)
    val numericFormat = LocalUvirNumericFormat.current
    var expanded by remember { mutableStateOf(false) }
    val night = isSystemInDarkTheme()
    Column(modifier = Modifier.testTag("live_irradiance_decimals_section").padding(top = UvirSettingsControlGap),
        verticalArrangement = Arrangement.spacedBy(UvirSettingsRelatedGap)) {
        Box {
            Surface(
                onClick = { expanded = true },
                modifier = Modifier.fillMaxWidth().testTag("live_irradiance_decimals")
                    .semantics { contentDescription = label },
                color = Color.Transparent,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, secondaryText.copy(alpha = 0.5f))
            ) {
                Row(Modifier.heightIn(min = 48.dp).padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(label, Modifier.weight(1f), color = primaryText, fontSize = 14.sp)
                    Text(formatUvirNumber(digits.toDouble(), 0, numericFormat, grouping = false),
                        color = primaryText, fontSize = 14.sp)
                    ExpansionChevron(expanded, secondaryText, iconSize = 18.dp)
                }
            }
            UvirAdaptiveDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.testTag("live_irradiance_decimals_menu"),
                alignToEnd = true,
                containerColor = if (night) UvirDropdownNightContainerColor else UvirDropdownDayContainerColor,
                tonalElevation = UvirDropdownTonalElevation
            ) {
                (0..UVIR_LIVE_IRRADIANCE_MAX_DECIMALS).forEach { choice ->
                    Box(Modifier.width(IntrinsicSize.Max).widthIn(min = 48.dp).heightIn(min = 48.dp)
                        .testTag("live_irradiance_decimals_$choice")
                        .selectable(selected = choice == digits, role = Role.DropdownList,
                            onClick = { expanded = false; onDigitsChange(choice) })
                        .padding(horizontal = 16.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
                        Text(formatUvirNumber(choice.toDouble(), 0, numericFormat, grouping = false),
                            color = primaryText, style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (choice == digits) FontWeight.Bold else FontWeight.Normal)
                    }
                }
            }
        }
        UvirFormatDescription(stringResource(R.string.live_irradiance_decimals_description), secondaryText)
    }
}
