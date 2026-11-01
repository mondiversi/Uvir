package me.mondiversi.uvir

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

internal val UvirCompactFieldContentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
internal val UvirCompactFieldMinHeight = 44.dp
internal val UvirCompactFieldIconSize = 40.dp

/** Compact input geometry shared by settings, acquisition forms, notes and password dialogs.
 * Filter controls own their separate geometry and do not use this field.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun UvirCompactTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    label: (@Composable () -> Unit)? = null,
    placeholder: (@Composable () -> Unit)? = null,
    supportingText: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    isError: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = false,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    colors: TextFieldColors = UvirOutlinedTextFieldColors()
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val textColor = when {
        !enabled -> colors.disabledTextColor
        isError -> colors.errorTextColor
        focused -> colors.focusedTextColor
        else -> colors.unfocusedTextColor
    }
    BasicTextField(value, onValueChange,
        modifier.semantics(mergeDescendants = true) { }
            .padding(top = if (label == null) 0.dp else 8.dp)
            .defaultMinSize(minHeight = UvirCompactFieldMinHeight),
        enabled = enabled, singleLine = singleLine, minLines = minLines, maxLines = maxLines,
        interactionSource = interaction,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = textColor),
        cursorBrush = SolidColor(if (isError) colors.errorCursorColor else colors.cursorColor),
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions, keyboardActions = keyboardActions,
        decorationBox = { input ->
            // Reduce the icon layout reservation, preserving Foundation's expanded touch targets.
            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides UvirCompactFieldIconSize) {
                OutlinedTextFieldDefaults.DecorationBox(value, input, enabled = enabled,
                    singleLine = singleLine, visualTransformation = visualTransformation,
                    interactionSource = interaction, isError = isError,
                    label = label, placeholder = placeholder, supportingText = supportingText,
                    trailingIcon = trailingIcon, colors = colors,
                    contentPadding = UvirCompactFieldContentPadding)
            }
        })
}
