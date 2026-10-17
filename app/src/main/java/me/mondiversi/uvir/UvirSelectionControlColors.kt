package me.mondiversi.uvir

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.CheckboxColors
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.SelectableChipColors
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButtonColors
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.SliderColors
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Navigation glyphs stay neutral, independently of the purple form controls. */
internal fun uvirNeutralContentColor(darkTheme: Boolean): Color =
    if (darkTheme) Color.White else Color(0xFF101418)

@Composable
internal fun uvirNeutralContentColor(): Color =
    uvirNeutralContentColor(isSystemInDarkTheme())

/** Use the existing theme accent for form choices, with muted disabled states. */
@Composable
internal fun uvirCheckboxColors(
    uncheckedColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    disabledCheckedColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
    disabledUncheckedColor: Color = disabledCheckedColor
): CheckboxColors = CheckboxDefaults.colors(
    checkedColor = MaterialTheme.colorScheme.primary,
    uncheckedColor = uncheckedColor,
    checkmarkColor = MaterialTheme.colorScheme.onPrimary,
    disabledCheckedColor = disabledCheckedColor,
    disabledUncheckedColor = disabledUncheckedColor,
    disabledIndeterminateColor = disabledCheckedColor
)

@Composable
internal fun uvirRadioButtonColors(
    unselectedColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    disabledSelectedColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
    disabledUnselectedColor: Color = disabledSelectedColor
): RadioButtonColors = RadioButtonDefaults.colors(
    selectedColor = MaterialTheme.colorScheme.primary,
    unselectedColor = unselectedColor,
    disabledSelectedColor = disabledSelectedColor,
    disabledUnselectedColor = disabledUnselectedColor
)

@Composable
internal fun uvirSliderColors(): SliderColors = SliderDefaults.colors()

@Composable
internal fun uvirSelectionChipColors(labelColor: Color): SelectableChipColors =
    FilterChipDefaults.filterChipColors(
        selectedContainerColor = MaterialTheme.colorScheme.primary,
        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
        labelColor = labelColor
    )
