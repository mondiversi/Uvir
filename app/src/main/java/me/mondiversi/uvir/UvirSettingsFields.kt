package me.mondiversi.uvir

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Shared trailing reference for calibration and optical-window fields. */
@Composable
internal fun SettingsChannelReference(text: String, color: Color, modifier: Modifier = Modifier) {
    Text(text, color = color, fontSize = 11.sp, maxLines = 1,
        modifier = Modifier.padding(horizontal = 12.dp).then(modifier))
}

@Composable
internal fun DecimalField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    enabled: Boolean = true,
    onEditingComplete: () -> Unit = {},
    modifier: Modifier = Modifier,
    trailingContent: (@Composable () -> Unit)? = null,
    maxChars: Int = 13,
    labelLeadingContent: (@Composable () -> Unit)? = null
) {
    val numericFormat = LocalUvirNumericFormat.current
    val localizedInput = uvirNumericInputTransformation(numericFormat)
    OutlinedTextField(
        value = value,
        onValueChange = { newValue ->
            onValueChange(filterUvirNumericInput(newValue, numericFormat, decimal = true).take(maxChars))
        },
        modifier =
            modifier.settingsCommitOnBlur(onEditingComplete = onEditingComplete),
        enabled = enabled,
        label = {
            if (labelLeadingContent == null) AdaptiveFieldLabel(label)
            else Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                labelLeadingContent()
                Box(Modifier.weight(1f, fill = false)) { AdaptiveFieldLabel(label) }
            }
        },
        trailingIcon = trailingContent,
        colors =
            UvirOutlinedTextFieldColors(),
        singleLine = true,
        visualTransformation = localizedInput,
        keyboardOptions =
            KeyboardOptions(
                keyboardType =
                    KeyboardType.Decimal
            )
    )
}

@Composable
fun NumberField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    maxChars: Int,
    enabled: Boolean = true,
    onEditingComplete: () -> Unit = {}
) {
    val numericFormat = LocalUvirNumericFormat.current
    OutlinedTextField(
        value = value,

        onValueChange = { newValue ->
            val normalized = filterUvirNumericInput(newValue, numericFormat, decimal = false)
            val allowedLength = maxChars + if (normalized.startsWith('-')) 1 else 0
            onValueChange(normalized.take(allowedLength))
        },

        modifier =
            modifier.settingsCommitOnBlur(onEditingComplete = onEditingComplete),

        enabled = enabled,

        label = {
            AdaptiveFieldLabel(label)
        },

        colors =
            UvirOutlinedTextFieldColors(),

        singleLine = true,
        visualTransformation = uvirNumericInputTransformation(numericFormat),

        keyboardOptions =
            KeyboardOptions(
                keyboardType =
                    KeyboardType.Number
            )
    )
}

@Composable
private fun uvirNumericInputTransformation(format: UvirNumericFormat): VisualTransformation =
    remember(format, java.util.Locale.getDefault()) {
        VisualTransformation { original ->
            TransformedText(
                AnnotatedString(formatUvirDecimalInputText(original.text, format)),
                OffsetMapping.Identity
            )
        }
    }

@Composable
fun AdaptiveFieldLabel(
    text: String
) {

    BoxWithConstraints {

        val inheritedStyle =
            LocalTextStyle.current

        val baseFontSize =
            if (
                inheritedStyle.fontSize ==
                TextUnit.Unspecified
            ) {
                12.sp
            } else {
                inheritedStyle.fontSize
            }

        val textMeasurer =
            rememberTextMeasurer()

        val measuredWidth =
            textMeasurer.measure(
                text = AnnotatedString(text),
                style =
                    inheritedStyle.copy(
                        fontSize = baseFontSize
                    ),
                maxLines = 1,
                softWrap = false
            ).size.width.toFloat()

        val availableWidth =
            constraints.maxWidth.toFloat()

        val scale =
            if (
                availableWidth > 0f &&
                measuredWidth > availableWidth
            ) {
                (availableWidth / measuredWidth)
                    .coerceIn(0.72f, 1f)
            } else {
                1f
            }

        Text(
            text = text,
            fontSize =
                (baseFontSize.value * scale).sp,
            maxLines = 1,
            softWrap = false
        )
    }
}

@Composable
fun DurationFields(
    hoursText: String,
    minutesText: String,
    secondsText: String,
    onHoursChange: (String) -> Unit,
    onMinutesChange: (String) -> Unit,
    onSecondsChange: (String) -> Unit,
    enabled: Boolean = true,
    maxHours: Int? = null,
    onEditingComplete: () -> Unit = {}
) {

    Column {

        Row(
            modifier =
                Modifier.fillMaxWidth(),

            verticalAlignment =
                Alignment.CenterVertically,

            horizontalArrangement =
                Arrangement.spacedBy(
                    8.dp
                )
        ) {

            NumberField(
                value =
                    hoursText,
                onValueChange =
                    onHoursChange,
                label =
                    stringResource(
                        R.string.hours
                    ),
                modifier =
                    Modifier.weight(1f),
                maxChars =
                    maxHours?.toString()?.length ?: 5,
                enabled = enabled,
                onEditingComplete = onEditingComplete
            )

            NumberField(
                value =
                    minutesText,
                onValueChange =
                    onMinutesChange,
                label =
                    stringResource(
                        R.string.minutes
                    ),
                modifier =
                    Modifier.weight(1f),
                maxChars = 5,
                enabled = enabled,
                onEditingComplete = onEditingComplete
            )

            NumberField(
                value =
                    secondsText,
                onValueChange =
                    onSecondsChange,
                label =
                    stringResource(
                        R.string.seconds
                    ),
                modifier =
                    Modifier.weight(1f),
                maxChars = 5,
                enabled = enabled,
                onEditingComplete = onEditingComplete
            )
        }
    }
}
