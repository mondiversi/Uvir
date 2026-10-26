package me.mondiversi.uvir

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Shared with the screen's app-session state, without preferences or saved-instance storage. */
@Composable
internal fun rememberUvirSavedValueDisplay(key: String): MutableState<UvirSavedValueDisplay> =
    rememberUvirSavedScreenPresentation(key).valueDisplay

internal val UvirSavedValueHalfGap = 7.5.dp

/** Padding belongs to the row, so its feedback and hit target span the entire island. */
@Composable
internal fun UvirSavedValueRow(key: String, included: Boolean, primaryText: Color,
    onToggle: (() -> Unit)?, content: @Composable ColumnScope.() -> Unit) {
    val interaction = if (onToggle == null) Modifier else Modifier.toggleable(
        value = included, role = Role.Checkbox, interactionSource = remember { MutableInteractionSource() },
        indication = ripple(color = primaryText), onValueChange = { onToggle() })
    Column(Modifier.fillMaxWidth().then(interaction).testTag("saved_row_$key")
        .padding(horizontal = 16.dp, vertical = UvirSavedValueHalfGap),
        verticalArrangement = Arrangement.spacedBy(3.dp), content = content)
}

@Composable
internal fun UvirSavedValueMark(key: String, included: Boolean, tint: Color) {
    UvirSpectrumVisibilityMark(included, tint, Modifier.size(12.dp)
        .testTag("saved_${if (included) "check" else "cross"}_$key"))
}

@Composable
internal fun UvirSavedPercentageBase(group: SessionChartGroup, display: UvirSavedValueDisplay,
    onDisplayChange: (UvirSavedValueDisplay) -> Unit, color: Color) {
    val sensorGroup = group.spectrumIconGroup()
    Text(stringResource(if (sensorGroup in display.spectrum.groupPercentages) R.string.live_percent_group else R.string.live_percent_total),
        modifier = Modifier.testTag("saved_percentage_base_${group.name}")
            .clickable(role = Role.Button) { onDisplayChange(display.copy(spectrum = display.spectrum.togglePercentageBase(sensorGroup))) }
            .padding(start = 8.dp, top = 2.dp, bottom = 2.dp),
        color = color, fontSize = 12.sp, lineHeight = 19.sp, maxLines = 1, textDecoration = TextDecoration.Underline)
}
