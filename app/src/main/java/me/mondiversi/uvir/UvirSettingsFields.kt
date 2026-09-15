package me.mondiversi.uvir

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun DecimalField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    enabled: Boolean = true,
    onEditingComplete: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = { newValue ->
            val normalized =
                newValue.replace(',', '.')
            val filtered =
                buildString {
                    var decimalSeparatorFound = false

                    normalized.forEachIndexed { index, character ->
                        when {
                            character == '-' && index == 0 ->
                                append(character)

                            character.isDigit() ->
                                append(character)

                            character == '.' &&
                                    !decimalSeparatorFound -> {
                                append(character)
                                decimalSeparatorFound = true
                            }
                        }
                    }
                }.take(13)

            onValueChange(filtered)
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

    OutlinedTextField(
        value = value,

        onValueChange = { newValue ->
            val normalized =
                buildString {
                    newValue.forEachIndexed { index, character ->
                        if (character.isDigit() || (character == '-' && index == 0)) {
                            append(character)
                        }
                    }
                }
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

        keyboardOptions =
            KeyboardOptions(
                keyboardType =
                    KeyboardType.Number
            )
    )
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
