package me.mondiversi.uvir

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private const val UVIR_CHART_AXIS_DIVISIONS = 4

@Composable
internal fun UvirChartYAxis(
    maximum: Double,
    fractionDigits: Int,
    secondaryText: Color,
    modifier: Modifier = Modifier,
    suffix: String = "",
    labels: List<String>? = null,
    chart: @Composable (Modifier) -> Unit
) {
    val numericFormat = LocalUvirNumericFormat.current
    val resolvedLabels =
        labels ?: (UVIR_CHART_AXIS_DIVISIONS downTo 0).map { step ->
                formatUvirNumber(
                    value = maximum * step / UVIR_CHART_AXIS_DIVISIONS,
                    fractionDigits = fractionDigits,
                    format = numericFormat
                ) + suffix
            }

    Box(modifier = modifier) {
        chart(Modifier.fillMaxSize())

        Column(
            modifier =
                Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .padding(vertical = 2.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.End
        ) {
            resolvedLabels.forEach { label ->
                Text(
                    text = label,
                    modifier =
                        Modifier
                            .background(
                                color = secondaryText.copy(alpha = 0.10f),
                                shape = RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 3.dp, vertical = 1.dp),
                    color = secondaryText,
                    fontSize = 9.sp,
                    lineHeight = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                    textAlign = TextAlign.End
                )
            }
        }
    }
}
