package me.mondiversi.uvir

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

internal val UvirTitleActionActiveDotSize = 5.dp
internal val UvirTitleActionActiveDotInset = 3.5.dp

/** Kept outside the badge's clipped/ripple layer, but inside the 40dp action. */
@Composable
internal fun UvirTitleActionActiveDot(color: Color, modifier: Modifier = Modifier) {
    Box(modifier.size(UvirTitleActionActiveDotSize).background(color, CircleShape))
}
