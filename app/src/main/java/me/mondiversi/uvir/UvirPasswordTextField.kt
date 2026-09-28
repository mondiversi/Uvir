package me.mondiversi.uvir

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

/** Password field shared by Wi-Fi and MQTT connection settings. */
@Composable
internal fun UvirPasswordTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    enabled: Boolean,
    primaryText: Color,
    secondaryText: Color,
    modifier: Modifier = Modifier
) {
    var passwordVisible by remember { mutableStateOf(false) }
    val revealPassword = enabled && passwordVisible
    val visibilityDescription =
        stringResource(if (revealPassword) R.string.hide_password else R.string.show_password)

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        visualTransformation =
            if (revealPassword) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            UvirAccessibleIconButton(
                contentDescription = visibilityDescription,
                onClick = { passwordVisible = !passwordVisible },
                enabled = enabled,
                modifier = Modifier.size(48.dp)
            ) {
                UvirPasswordVisibilityIcon(
                    visible = revealPassword,
                    modifier = Modifier.size(22.dp),
                    tint = if (enabled) primaryText else secondaryText
                )
            }
        },
        colors = UvirOutlinedTextFieldColors()
    )
}
