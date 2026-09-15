package me.mondiversi.uvir

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun UvirCollapsibleChartCard(
    title: String,
    subtitle: String? = null,
    iconGroup: SensorGroup? = null,
    iconViewMode: ViewMode? = null,
    expanded: Boolean,
    onToggle: () -> Unit,
    cardColor: Color,
    primaryText: Color,
    secondaryText: Color,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onToggle)
                        .padding(UvirIslandContentPadding),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (iconViewMode != null || iconGroup != null) {
                    when {
                        iconViewMode == ViewMode.BIOLOGICAL_EFFECTS || iconGroup == SensorGroup.BIOLOGICAL ->
                            DnaIcon(color = primaryText, iconSize = UvirDetailSpectrumGroupIconSize)
                        iconViewMode == ViewMode.IRRADIANCE ->
                            ElectromagneticWaveIcon(color = primaryText, iconSize = UvirDetailSpectrumGroupIconSize)
                        iconGroup != null -> UvirSpectrumGroupIcon(
                            group = iconGroup,
                            tint = primaryText,
                            iconSize = UvirDetailSpectrumGroupIconSize
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        color = primaryText,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    subtitle?.let { text ->
                        Text(
                            text = text,
                            color = secondaryText,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                ExpansionChevron(
                    expanded = expanded,
                    tint = secondaryText,
                    modifier = Modifier.size(20.dp)
                )
            }

            UvirVerticalReveal(expanded) {
                HorizontalDivider(
                    modifier =
                        Modifier.padding(
                            horizontal = UvirIslandContentPadding
                        ),
                    color = secondaryText.copy(alpha = 0.20f)
                )

                content()
            }
        }
    }
}
