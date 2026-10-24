package me.mondiversi.uvir

import android.app.Activity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

@Composable
internal fun UvirEncryptedDatabaseExportDialog(
    cardColor: Color,
    primaryText: Color,
    secondaryText: Color,
    onDismiss: () -> Unit,
    onExport: suspend (CharArray, UvirExportDestination) -> Unit
) {
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var validationAttempted by remember { mutableStateOf(false) }
    var inProgress by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val invalidPassword = validationAttempted &&
        !isValidUvirSettingsExportPassword(password)
    val requestExport: (UvirExportDestination) -> Unit = { destination ->
        if (!inProgress) {
            validationAttempted = true
            if (isValidUvirSettingsExportPassword(password)) {
                inProgress = true
                val secret = password.toCharArray()
                scope.launch {
                    try {
                        onExport(secret, destination)
                    } finally {
                        secret.fill('\u0000')
                        inProgress = false
                    }
                }
            }
        }
    }

    UvirAlertDialog(
        onDismissRequest = { if (!inProgress) onDismiss() },
        title = {
            UvirClosableDialogTitle(
                title = stringResource(R.string.export_database_dialog_title),
                onDismiss = { if (!inProgress) onDismiss() }
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.export_database_dialog_description),
                    color = secondaryText
                )
                UvirSettingsPasswordField(
                    value = password,
                    onValueChange = {
                        password = it
                        validationAttempted = false
                    },
                    label = stringResource(R.string.settings_encryption_password),
                    visible = passwordVisible,
                    onVisibilityChange = { passwordVisible = !passwordVisible },
                    primaryText = primaryText,
                    isError = invalidPassword
                )
                Text(
                    text = stringResource(
                        R.string.settings_encryption_password_too_short,
                        UVIR_SETTINGS_MINIMUM_PASSWORD_LENGTH
                    ),
                    color = if (invalidPassword) MaterialTheme.colorScheme.error else secondaryText,
                    fontSize = 11.sp
                )
                UvirExportFileCountText(
                    format = UvirExportFileFormat.ZIP,
                    count = 1,
                    secondaryText = secondaryText
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = password.length >= UVIR_SETTINGS_MINIMUM_PASSWORD_LENGTH && !inProgress,
                onClick = { requestExport(UvirExportDestination.SAVE) }
            ) { Text(stringResource(R.string.save)) }
            TextButton(
                enabled = password.length >= UVIR_SETTINGS_MINIMUM_PASSWORD_LENGTH && !inProgress,
                onClick = { requestExport(UvirExportDestination.SHARE) }
            ) { Text(stringResource(R.string.share)) }
        },
        dismissButton = {
            UvirDialogCancelButton(onDismiss, enabled = !inProgress,
                modifier = Modifier.testTag("database_export_close"))
        },
        containerColor = cardColor,
        titleContentColor = primaryText,
        textContentColor = secondaryText
    )
}

@Composable
internal fun SettingsExportOptionRow(
    selected: Boolean,
    enabled: Boolean,
    title: String,
    description: String,
    iconType: ConnectivityIconType,
    primaryText: Color,
    secondaryText: Color,
    onClick: () -> Unit
) {
    val toggle: () -> Unit = onClick
    val contentColor =
        if (enabled) primaryText else secondaryText.copy(alpha = 0.46f)

    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(enabled = enabled, onClick = toggle),
        shape = RoundedCornerShape(10.dp),
        color =
            if (selected && enabled) {
                primaryText.copy(alpha = 0.08f)
            } else {
                Color.Transparent
            }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ConnectivitySectionIcon(
                type = iconType,
                modifier = Modifier.size(22.dp),
                tint = contentColor
            )

            Spacer(Modifier.width(10.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = title,
                    color = contentColor,
                    fontSize = 14.sp,
                    lineHeight = UvirSettingsCheckboxLineHeight,
                    fontWeight = FontWeight.Medium
                )
                if (description.isNotBlank()) {
                    Text(
                        text = description,
                        color =
                            if (enabled) secondaryText
                            else secondaryText.copy(alpha = 0.46f),
                        fontSize = 11.sp,
                        lineHeight = 14.sp
                    )
                }
            }

            Spacer(Modifier.width(10.dp))

            CompositionLocalProvider(
                LocalMinimumInteractiveComponentSize provides 0.dp
            ) {
                Checkbox(
                    checked = selected,
                    onCheckedChange = { toggle() },
                    enabled = enabled,
                    modifier = Modifier.size(24.dp),
                    colors =
                        uvirCheckboxColors(
                            uncheckedColor = secondaryText,
                            disabledCheckedColor = secondaryText.copy(alpha = 0.34f),
                            disabledUncheckedColor = secondaryText.copy(alpha = 0.34f)
                        )
                )
            }
        }
    }
}

@Composable
internal fun UvirSettingsExportDialog(
    cardColor: Color,
    primaryText: Color,
    secondaryText: Color,
    sensorProfiles: List<UvirSensorProfile>,
    onDismiss: () -> Unit,
    onExport: (
        includeAppSettings: Boolean,
        sensorHardwareUids: List<String>,
        encryptionPassword: CharArray,
        destination: UvirExportDestination
    ) -> Unit
) {
    var appSelected by rememberSaveable { mutableStateOf(false) }
    var selectedSensorHardwareUids by remember { mutableStateOf(emptySet<String>()) }
    // Passwords must never enter the Activity saved-state bundle.
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var validationAttempted by remember { mutableStateOf(false) }
    var pendingEncryptionPassword by remember {
        mutableStateOf<CharArray?>(null)
    }
    val hasExportSelection = appSelected || selectedSensorHardwareUids.isNotEmpty()
    val passwordTooShort =
        validationAttempted && password.length < UVIR_SETTINGS_MINIMUM_PASSWORD_LENGTH
    val validationMessage =
        when {
            passwordTooShort ->
                stringResource(
                    R.string.settings_encryption_password_too_short,
                    UVIR_SETTINGS_MINIMUM_PASSWORD_LENGTH
                )
            else -> null
        }
    val exportFileCount =
        (if (appSelected) 1 else 0) + selectedSensorHardwareUids.size
    val exportRequest =
        rememberUvirExportRequestGate { destination ->
            val password = pendingEncryptionPassword
            try {
                onExport(
                    appSelected,
                    sensorProfiles.map { it.hardwareUid }
                        .filter { it in selectedSensorHardwareUids },
                    requireNotNull(password),
                    destination
                )
            } finally {
                password?.fill('\u0000')
                pendingEncryptionPassword = null
            }
        }

    val requestExport: (UvirExportDestination) -> Unit = { destination ->
        validationAttempted = true
        if (isValidUvirSettingsExportPassword(password)) {
            pendingEncryptionPassword?.fill('\u0000')
            pendingEncryptionPassword = password.toCharArray()
            exportRequest.request(destination)
        }
    }

    UvirAlertDialog(
        onDismissRequest = {
            if (!exportRequest.inProgress) onDismiss()
        },
        title = {
            UvirClosableDialogTitle(
                title = stringResource(R.string.export_settings_title),
                onDismiss = {
                    if (!exportRequest.inProgress) onDismiss()
                }
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = stringResource(R.string.export_settings_description),
                    color = secondaryText,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                SettingsExportOptionRow(
                    selected = appSelected,
                    enabled = true,
                    title = stringResource(R.string.export_app_settings),
                    description = "",
                    iconType = ConnectivityIconType.PHONE,
                    primaryText = primaryText,
                    secondaryText = secondaryText,
                    onClick = { appSelected = !appSelected }
                )
                if (sensorProfiles.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.export_sensor_settings),
                        color = secondaryText,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(start = 10.dp, top = 4.dp)
                    )
                    Column {
                        sensorProfiles.forEach { profile ->
                            SettingsExportOptionRow(
                                selected = profile.hardwareUid in selectedSensorHardwareUids,
                                enabled = true,
                                title = profile.displayName,
                                description = profile.hardwareUid,
                                iconType = ConnectivityIconType.SENSOR,
                                primaryText = primaryText,
                                secondaryText = secondaryText,
                                onClick = {
                                    selectedSensorHardwareUids =
                                        if (profile.hardwareUid in selectedSensorHardwareUids) {
                                            selectedSensorHardwareUids - profile.hardwareUid
                                        } else {
                                            selectedSensorHardwareUids + profile.hardwareUid
                                        }
                                }
                            )
                        }
                    }
                }
                UvirSettingsPasswordField(
                    value = password,
                    onValueChange = {
                        password = it
                        validationAttempted = false
                    },
                    label = stringResource(R.string.settings_encryption_password),
                    visible = passwordVisible,
                    onVisibilityChange = { passwordVisible = !passwordVisible },
                    primaryText = primaryText,
                    isError = validationMessage != null
                )
                Text(
                    text = stringResource(R.string.settings_encryption_password_description),
                    color = secondaryText,
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    modifier = Modifier.padding(horizontal = 2.dp)
                )
                validationMessage?.let { message ->
                    Text(
                        text = message,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 11.sp,
                        lineHeight = 14.sp
                    )
                }
                if (exportFileCount > 0) {
                    UvirExportFileCountText(
                        format = UvirExportFileFormat.SETTINGS,
                        count = exportFileCount,
                        secondaryText = secondaryText,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = hasExportSelection &&
                    password.length >= UVIR_SETTINGS_MINIMUM_PASSWORD_LENGTH &&
                    !exportRequest.inProgress,
                onClick = { requestExport(UvirExportDestination.SAVE) }
            ) { Text(stringResource(R.string.save)) }
            TextButton(
                enabled = hasExportSelection &&
                    password.length >= UVIR_SETTINGS_MINIMUM_PASSWORD_LENGTH &&
                    !exportRequest.inProgress,
                onClick = { requestExport(UvirExportDestination.SHARE) }
            ) { Text(stringResource(R.string.share)) }
        },
        dismissButton = {
            UvirDialogCancelButton(onDismiss, enabled = !exportRequest.inProgress,
                modifier = Modifier.testTag("settings_export_close"))
        },
        containerColor = cardColor,
        titleContentColor = primaryText,
        textContentColor = secondaryText
    )
}

@Composable
private fun UvirSettingsPasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    visible: Boolean,
    onVisibilityChange: () -> Unit,
    primaryText: Color,
    isError: Boolean
) {
    val visibilityDescription =
        stringResource(if (visible) R.string.hide_password else R.string.show_password)
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        visualTransformation =
            if (visible) VisualTransformation.None
            else PasswordVisualTransformation(),
        trailingIcon = {
            UvirAccessibleIconButton(
                contentDescription = visibilityDescription,
                onClick = onVisibilityChange,
                modifier = Modifier.size(48.dp)
            ) {
                UvirPasswordVisibilityIcon(
                    visible = visible,
                    modifier = Modifier,
                    tint = primaryText
                )
            }
        },
        isError = isError,
        colors = UvirOutlinedTextFieldColors()
    )
}

@Composable
internal fun UvirSettingsPasswordDialog(
    title: String,
    description: String,
    confirmationRequired: Boolean,
    confirmLabel: String,
    cardColor: Color,
    primaryText: Color,
    secondaryText: Color,
    passwordRequired: Boolean = true,
    busy: Boolean = false,
    errorMessage: String? = null,
    onInputChanged: () -> Unit = {},
    onDismiss: () -> Unit,
    onConfirm: (CharArray) -> Unit
) {
    // Passwords must never enter the Activity saved-state bundle.
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var confirmationVisible by remember { mutableStateOf(false) }
    var validationAttempted by remember { mutableStateOf(false) }
    val passwordTooShort =
        passwordRequired && validationAttempted && password.length < UVIR_SETTINGS_MINIMUM_PASSWORD_LENGTH
    val passwordsDoNotMatch =
        passwordRequired && validationAttempted && confirmationRequired && password != confirmation
    val validationMessage =
        when {
            passwordTooShort ->
                stringResource(
                    R.string.settings_encryption_password_too_short,
                    UVIR_SETTINGS_MINIMUM_PASSWORD_LENGTH
                )
            passwordsDoNotMatch ->
                stringResource(R.string.settings_encryption_password_mismatch)
            else -> errorMessage
        }

    fun submit() {
        if (busy) return
        validationAttempted = true
        if (
            !passwordRequired || (password.length >= UVIR_SETTINGS_MINIMUM_PASSWORD_LENGTH &&
            (!confirmationRequired || password == confirmation))
        ) {
            onConfirm(if (passwordRequired) password.toCharArray() else charArrayOf())
        }
    }

    UvirAlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = description,
                    color = secondaryText,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
                if (passwordRequired) UvirSettingsPasswordField(
                    value = password,
                    onValueChange = {
                        password = it
                        validationAttempted = false
                        onInputChanged()
                    },
                    label = stringResource(R.string.settings_encryption_password),
                    visible = passwordVisible,
                    onVisibilityChange = { passwordVisible = !passwordVisible },
                    primaryText = primaryText,
                    isError = validationMessage != null
                )
                if (passwordRequired && confirmationRequired) {
                    UvirSettingsPasswordField(
                        value = confirmation,
                        onValueChange = {
                            confirmation = it
                            validationAttempted = false
                            onInputChanged()
                        },
                        label = stringResource(R.string.settings_encryption_password_confirm),
                        visible = confirmationVisible,
                        onVisibilityChange = {
                            confirmationVisible = !confirmationVisible
                        },
                        primaryText = primaryText,
                        isError = validationMessage != null
                    )
                }
                validationMessage?.let { message ->
                    Text(
                        text = message,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 11.sp,
                        lineHeight = 14.sp
                    )
                }
                UvirHoldConfirmationInstruction(uvirActionLabel(confirmLabel), UvirImportHoldSeconds)
            }
        },
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically
            ) {
                UvirHoldToImportButton(label = uvirActionLabel(confirmLabel), enabled = !busy,
                    modifier = Modifier.testTag("encrypted_import_confirm"), onConfirmed = ::submit)
                TextButton(
                    onClick = onDismiss,
                    enabled = !busy,
                    colors =
                        ButtonDefaults.textButtonColors(
                            contentColor = UvirDestructiveActionColor
                        )
                ) { Text(stringResource(R.string.cancel)) }
            }
        },
        containerColor = cardColor,
        titleContentColor = primaryText,
        textContentColor = secondaryText
    )
}
