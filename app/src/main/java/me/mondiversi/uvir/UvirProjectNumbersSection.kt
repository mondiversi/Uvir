package me.mondiversi.uvir

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
private fun ProjectNumbersIcon(
    tint: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.size(20.dp)) {
        val strokeWidth =
            maxOf(
                1.6.dp.toPx(),
                size.minDimension * 0.08f
            )
        val baseline = size.height * 0.84f

        drawLine(
            color = tint,
            start = Offset(size.width * 0.14f, baseline),
            end = Offset(size.width * 0.88f, baseline),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )

        listOf(
            0.26f to 0.66f,
            0.50f to 0.46f,
            0.74f to 0.24f
        ).forEach { (x, top) ->
            drawLine(
                color = tint,
                start = Offset(size.width * x, size.height * top),
                end = Offset(size.width * x, baseline),
                strokeWidth = size.width * 0.13f,
                cap = StrokeCap.Round
            )
        }
    }
}

@Composable
internal fun UvirProjectNumbersSection(
    cardColor: Color,
    primaryText: Color,
    secondaryText: Color
) {
    val projectFacts =
        listOf(
            R.string.project_numbers_development_time to
                R.string.project_numbers_development_time_value,
            R.string.project_numbers_ai_use to
                R.string.project_numbers_ai_use_value,
            R.string.project_numbers_prototype_cost to
                R.string.project_numbers_prototype_cost_value,
            R.string.project_numbers_traditional_work to
                R.string.project_numbers_traditional_work_value,
            R.string.project_numbers_time_saved to
                R.string.project_numbers_time_saved_value
        )

    SettingsSection(
        title = stringResource(R.string.project_numbers_title),
        containerColor = cardColor,
        titleColor = primaryText,
        dividerColor = secondaryText.copy(alpha = 0.28f),
        titleIconContent = { tint ->
            ProjectNumbersIcon(tint = tint)
        },
        contentSpacing = 8.dp
    ) {
        Text(
            text = stringResource(R.string.project_numbers_description),
            color = secondaryText,
            fontSize = 12.sp
        )

        projectFacts.forEach { (labelResource, valueResource) ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = stringResource(labelResource),
                    modifier = Modifier.weight(0.60f),
                    color = secondaryText,
                    fontSize = 12.sp
                )
                Text(
                    text = stringResource(valueResource),
                    modifier = Modifier.weight(0.40f),
                    color = primaryText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.End
                )
            }
        }

        Text(
            text = stringResource(R.string.project_numbers_estimate_note),
            color = secondaryText,
            fontSize = 11.sp
        )
    }
}
