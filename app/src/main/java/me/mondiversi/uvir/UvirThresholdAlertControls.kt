package me.mondiversi.uvir

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

@Composable
internal fun ThresholdAlertMetricLabel(
    metric: ThresholdAlertMetric,
    primaryText: Color,
    secondaryText: Color,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(10.dp)
                .testTag("threshold_metric_dot_${metric.name}")
                .background(
                    if (enabled) thresholdAlertMetricDisplayColor(metric) else secondaryText,
                    CircleShape
                )
        )
        Text(
            text = stringResource(thresholdAlertMetricLabelResource(metric)),
            modifier = Modifier.weight(1f).testTag("threshold_metric_label_${metric.name}"),
            color = if (enabled) primaryText else secondaryText,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
internal fun ThresholdDirectionSelector(
    selectedDirection: ThresholdAlertDirection,
    onDirectionSelected: (ThresholdAlertDirection) -> Unit,
    cardColor: Color,
    primaryText: Color,
    secondaryText: Color,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    Row(
        modifier =
            modifier
                .background(
                    cardColor,
                    RoundedCornerShape(14.dp)
                )
                .border(
                    width = 1.dp,
                    color = secondaryText.copy(alpha = 0.34f),
                    shape = RoundedCornerShape(14.dp)
                )
                .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        ViewModeButton(
            contentDescription =
                stringResource(R.string.threshold_direction_below),
            selected =
                selectedDirection == ThresholdAlertDirection.BELOW,
            alerted = false,
            enabled = enabled,
            onClick = {
                onDirectionSelected(ThresholdAlertDirection.BELOW)
            },
            primaryText = primaryText,
            secondaryText = secondaryText,
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = "\u2264",
                color =
                    if (enabled) {
                        if (selectedDirection == ThresholdAlertDirection.BELOW) {
                            primaryText
                        } else {
                            secondaryText
                        }
                    } else {
                        secondaryText.copy(alpha = 0.45f)
                    },
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold
            )
        }

        ViewModeButton(
            contentDescription =
                stringResource(R.string.threshold_direction_above),
            selected =
                selectedDirection == ThresholdAlertDirection.ABOVE,
            alerted = false,
            enabled = enabled,
            onClick = {
                onDirectionSelected(ThresholdAlertDirection.ABOVE)
            },
            primaryText = primaryText,
            secondaryText = secondaryText,
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = "\u2265",
                color =
                    if (enabled) {
                        if (selectedDirection == ThresholdAlertDirection.ABOVE) {
                            primaryText
                        } else {
                            secondaryText
                        }
                    } else {
                        secondaryText.copy(alpha = 0.45f)
                    },
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
internal fun ThresholdConditionControls(
    selectedDirection: ThresholdAlertDirection,
    onDirectionSelected: (ThresholdAlertDirection) -> Unit,
    currentValueEnabled: Boolean,
    onUseCurrentValue: () -> Unit,
    cardColor: Color,
    primaryText: Color,
    secondaryText: Color,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ThresholdDirectionSelector(
            selectedDirection = selectedDirection,
            onDirectionSelected = onDirectionSelected,
            cardColor = cardColor,
            primaryText = primaryText,
            secondaryText = secondaryText,
            enabled = enabled,
            modifier = Modifier.weight(1f)
        )

        CurrentThresholdValueButton(
            enabled = enabled && currentValueEnabled,
            onClick = onUseCurrentValue,
            primaryText = primaryText,
            secondaryText = secondaryText,
            modifier = Modifier.widthIn(min = 104.dp)
        )
    }
}

@Composable
internal fun CurrentThresholdValueButton(
    enabled: Boolean,
    onClick: () -> Unit,
    primaryText: Color,
    secondaryText: Color,
    modifier: Modifier = Modifier
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(38.dp),
        contentPadding = PaddingValues(horizontal = 14.dp),
        colors = uvirOutlinedActionColors(primaryText),
        border = uvirOutlinedActionBorder(enabled, secondaryText)
    ) {
        CaptureMeasurementIcon(
            modifier = Modifier.size(17.dp),
            useCompactStroke = true
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = stringResource(R.string.threshold_use_current_value),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}

internal fun thresholdEditableValue(value: Double): String {
    if (!value.isFinite()) return "0"

    return String.format(Locale.US, "%.6f", value)
        .trimEnd('0')
        .trimEnd('.')
        .ifBlank { "0" }
}
