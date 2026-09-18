package me.mondiversi.uvir

import android.Manifest
import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
import android.content.pm.PackageManager
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.graphics.BitmapFactory
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.view.WindowCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference
import java.io.File
import org.json.JSONObject
import kotlin.math.roundToInt
import kotlin.random.Random

@Composable
private fun ShareFormatIcon(
    format: MeasurementShareFormat,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current
) {
    Box(
        modifier = modifier.size(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier.fillMaxSize()
        ) {
            val strokeWidth =
                maxOf(
                    1.5.dp.toPx(),
                    size.minDimension * 0.075f
                )

            when (format) {
                MeasurementShareFormat.CSV -> {
                    val documentPath = Path().apply {
                        moveTo(
                            size.width * 0.22f,
                            size.height * 0.08f
                        )
                        lineTo(
                            size.width * 0.62f,
                            size.height * 0.08f
                        )
                        lineTo(
                            size.width * 0.80f,
                            size.height * 0.27f
                        )
                        lineTo(
                            size.width * 0.80f,
                            size.height * 0.92f
                        )
                        lineTo(
                            size.width * 0.22f,
                            size.height * 0.92f
                        )
                        close()
                    }
                    drawPath(
                        path = documentPath,
                        color = tint,
                        style = Stroke(
                            width = strokeWidth,
                            join = androidx.compose.ui.graphics.StrokeJoin.Round
                        )
                    )
                    drawLine(
                        color = tint,
                        start = Offset(
                            size.width * 0.62f,
                            size.height * 0.08f
                        ),
                        end = Offset(
                            size.width * 0.62f,
                            size.height * 0.27f
                        ),
                        strokeWidth = strokeWidth
                    )
                    drawLine(
                        color = tint,
                        start = Offset(
                            size.width * 0.62f,
                            size.height * 0.27f
                        ),
                        end = Offset(
                            size.width * 0.80f,
                            size.height * 0.27f
                        ),
                        strokeWidth = strokeWidth
                    )
                }

                MeasurementShareFormat.READABLE_TABLE,
                MeasurementShareFormat.BOTH -> {
                    drawRoundRect(
                        color = tint,
                        topLeft = Offset(
                            size.width * 0.08f,
                            size.height * 0.16f
                        ),
                        size = Size(
                            size.width * 0.84f,
                            size.height * 0.68f
                        ),
                        cornerRadius = CornerRadius(
                            strokeWidth,
                            strokeWidth
                        ),
                        style = Stroke(width = strokeWidth)
                    )
                    drawRect(
                        color = tint.copy(alpha = 0.18f),
                        topLeft = Offset(
                            size.width * 0.08f,
                            size.height * 0.16f
                        ),
                        size = Size(
                            size.width * 0.84f,
                            size.height * 0.20f
                        )
                    )
                    listOf(0.36f, 0.60f).forEach { y ->
                        drawLine(
                            color = tint,
                            start = Offset(
                                size.width * 0.08f,
                                size.height * y
                            ),
                            end = Offset(
                                size.width * 0.92f,
                                size.height * y
                            ),
                            strokeWidth = strokeWidth
                        )
                    }
                    drawLine(
                        color = tint,
                        start = Offset(
                            size.width * 0.44f,
                            size.height * 0.16f
                        ),
                        end = Offset(
                            size.width * 0.44f,
                            size.height * 0.84f
                        ),
                        strokeWidth = strokeWidth
                    )
                }
            }
        }

        if (format == MeasurementShareFormat.CSV) {
            Text(
                text = "CSV",
                modifier = Modifier.padding(top = 5.dp),
                color = tint,
                fontSize = 6.5.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun ShareFormatOptionRow(
    format: MeasurementShareFormat,
    selected: Boolean,
    label: String,
    primaryText: Color,
    secondaryText: Color,
    onClick: () -> Unit
) {
    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        color =
            if (selected) {
                primaryText.copy(alpha = 0.08f)
            } else {
                Color.Transparent
            }
    ) {
        Row(
            modifier =
                Modifier.padding(
                    horizontal = 10.dp,
                    vertical = 9.dp
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ShareFormatIcon(
                format = format,
                modifier = Modifier.size(22.dp),
                tint = primaryText
            )

            Spacer(Modifier.width(10.dp))

            Text(
                text = label,
                modifier = Modifier.weight(1f),
                color = primaryText,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )

            CompositionLocalProvider(
                LocalMinimumInteractiveComponentSize provides 0.dp
            ) {
                Checkbox(
                    checked = selected,
                    onCheckedChange = {
                        onClick()
                    },
                    modifier = Modifier.size(24.dp),
                    colors =
                        CheckboxDefaults.colors(
                            checkedColor =
                                MaterialTheme.colorScheme.primary,
                            uncheckedColor = secondaryText,
                            checkmarkColor =
                                MaterialTheme.colorScheme.onPrimary
                        )
                )
            }
        }
    }
}

@Composable
private fun ShareChartsOptionRow(
    selected: Boolean,
    primaryText: Color,
    secondaryText: Color,
    onClick: () -> Unit
) {
    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        color =
            if (selected) {
                primaryText.copy(alpha = 0.08f)
            } else {
                Color.Transparent
            }
    ) {
        Row(
            modifier =
                Modifier.padding(
                    horizontal = 10.dp,
                    vertical = 9.dp
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SessionChartIcon(
                modifier = Modifier.size(22.dp),
                tint = primaryText
            )

            Spacer(Modifier.width(10.dp))

            Text(
                text = stringResource(R.string.share_as_charts),
                modifier = Modifier.weight(1f),
                color = primaryText,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )

            CompositionLocalProvider(
                LocalMinimumInteractiveComponentSize provides 0.dp
            ) {
                Checkbox(
                    checked = selected,
                    onCheckedChange = {
                        onClick()
                    },
                    modifier = Modifier.size(24.dp),
                    colors =
                        CheckboxDefaults.colors(
                            checkedColor =
                                MaterialTheme.colorScheme.primary,
                            uncheckedColor = secondaryText,
                            checkmarkColor =
                                MaterialTheme.colorScheme.onPrimary
                        )
                )
            }
        }
    }
}

@Composable
private fun ChartExportModeRow(
    selected: Boolean,
    label: String,
    primaryText: Color,
    secondaryText: Color,
    onClick: () -> Unit
) {
    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        color =
            if (selected) {
                primaryText.copy(alpha = 0.06f)
            } else {
                Color.Transparent
            }
    ) {
        Row(
            modifier =
                Modifier.padding(
                    start = 38.dp,
                    end = 10.dp,
                    top = 5.dp,
                    bottom = 5.dp
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CompositionLocalProvider(
                LocalMinimumInteractiveComponentSize provides 0.dp
            ) {
                RadioButton(
                    selected = selected,
                    onClick = onClick,
                    modifier = Modifier.size(22.dp),
                    colors =
                        RadioButtonDefaults.colors(
                            selectedColor = MaterialTheme.colorScheme.primary,
                            unselectedColor = secondaryText
                        )
                )
            }

            Spacer(Modifier.width(8.dp))

            Text(
                text = label,
                modifier = Modifier.weight(1f),
                color = primaryText,
                fontSize = 12.5.sp,
                lineHeight = 15.sp
            )
        }
    }
}

@Composable
internal fun MeasurementShareFormatDialog(
    cardColor: Color,
    primaryText: Color,
    secondaryText: Color,
    onDismiss: () -> Unit,
    onFormatSelected: (MeasurementShareFormat, UvirExportDestination) -> Unit
) {
    var csvSelected by rememberSaveable {
        mutableStateOf(false)
    }
    var readableTableSelected by rememberSaveable {
        mutableStateOf(false)
    }

    val selectedFormat =
        when {
            csvSelected && readableTableSelected ->
                MeasurementShareFormat.BOTH

            csvSelected ->
                MeasurementShareFormat.CSV

            readableTableSelected ->
                MeasurementShareFormat.READABLE_TABLE

            else -> null
        }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    R.string.choose_share_format
                )
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement =
                    Arrangement.spacedBy(6.dp)
            ) {
                ShareFormatOptionRow(
                    format = MeasurementShareFormat.CSV,
                    selected = csvSelected,
                    label =
                        stringResource(
                            R.string.share_as_csv
                        ),
                    primaryText = primaryText,
                    secondaryText = secondaryText,
                    onClick = {
                        csvSelected = !csvSelected
                    }
                )

                ShareFormatOptionRow(
                    format =
                        MeasurementShareFormat.READABLE_TABLE,
                    selected = readableTableSelected,
                    label =
                        stringResource(
                            R.string.share_as_readable_table
                        ),
                    primaryText = primaryText,
                    secondaryText = secondaryText,
                    onClick = {
                        readableTableSelected =
                            !readableTableSelected
                    }
                )
            }
        },
        confirmButton = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        enabled = selectedFormat != null,
                        onClick = {
                            selectedFormat?.let {
                                onFormatSelected(it, UvirExportDestination.SAVE)
                            }
                        }
                    ) {
                        Text(stringResource(R.string.save))
                    }

                    TextButton(
                        enabled = selectedFormat != null,
                        onClick = {
                            selectedFormat?.let {
                                onFormatSelected(it, UvirExportDestination.SHARE)
                            }
                        }
                    ) {
                        Text(stringResource(R.string.share))
                    }
                }

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End),
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = UvirDestructiveActionColor
                    )
                ) {
                    Text(stringResource(R.string.cancel))
                }
            }
        },
        containerColor = cardColor,
        titleContentColor = primaryText,
        textContentColor = secondaryText
    )
}

@Composable
internal fun UvirSaveOrShareDialog(
    cardColor: Color,
    primaryText: Color,
    secondaryText: Color,
    title: String,
    description: String,
    onDismiss: () -> Unit,
    onExport: (UvirExportDestination) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                title + stringResource(R.string.confirmation_question_suffix)
            )
        },
        text = {
            Text(
                text = description,
                color = secondaryText
            )
        },
        confirmButton = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = { onExport(UvirExportDestination.SAVE) }) {
                        Text(stringResource(R.string.save))
                    }
                    TextButton(onClick = { onExport(UvirExportDestination.SHARE) }) {
                        Text(stringResource(R.string.share))
                    }
                }
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End),
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = UvirDestructiveActionColor
                    )
                ) {
                    Text(stringResource(R.string.cancel))
                }
            }
        },
        containerColor = cardColor,
        titleContentColor = primaryText,
        textContentColor = secondaryText
    )
}

@Composable
private fun SettingsExportOptionRow(
    selected: Boolean,
    enabled: Boolean,
    title: String,
    description: String,
    iconType: ConnectivityIconType,
    primaryText: Color,
    secondaryText: Color,
    onClick: () -> Unit
) {
    val contentColor =
        if (enabled) primaryText else secondaryText.copy(alpha = 0.46f)

    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(enabled = enabled, onClick = onClick),
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
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = description,
                    color =
                        if (enabled) secondaryText
                        else secondaryText.copy(alpha = 0.46f),
                    fontSize = 11.sp,
                    lineHeight = 14.sp
                )
            }

            Spacer(Modifier.width(10.dp))

            CompositionLocalProvider(
                LocalMinimumInteractiveComponentSize provides 0.dp
            ) {
                Checkbox(
                    checked = selected,
                    onCheckedChange = { onClick() },
                    enabled = enabled,
                    modifier = Modifier.size(24.dp),
                    colors =
                        CheckboxDefaults.colors(
                            checkedColor = MaterialTheme.colorScheme.primary,
                            uncheckedColor = secondaryText,
                            checkmarkColor = MaterialTheme.colorScheme.onPrimary,
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
    sensorSettingsAvailable: Boolean,
    onDismiss: () -> Unit,
    onExport: (
        includeAppSettings: Boolean,
        includeSensorSettings: Boolean,
        encryptionPassword: CharArray?
    ) -> Unit
) {
    var appSelected by rememberSaveable { mutableStateOf(false) }
    var sensorSelected by rememberSaveable { mutableStateOf(false) }
    var sensitiveInformationSelected by rememberSaveable { mutableStateOf(false) }
    var showEncryptionPasswordDialog by rememberSaveable { mutableStateOf(false) }
    val canExport = appSelected || (sensorSelected && sensorSettingsAvailable)

    if (showEncryptionPasswordDialog) {
        UvirSettingsPasswordDialog(
            title = stringResource(R.string.settings_encryption_password_title),
            description = stringResource(R.string.settings_encryption_password_description),
            confirmationRequired = true,
            confirmLabel = stringResource(R.string.settings_encrypt),
            cardColor = cardColor,
            primaryText = primaryText,
            secondaryText = secondaryText,
            onDismiss = {
                showEncryptionPasswordDialog = false
                onDismiss()
            },
            onConfirm = { password ->
                showEncryptionPasswordDialog = false
                onExport(appSelected, sensorSelected, password)
            }
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.export_settings_title)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                SettingsExportOptionRow(
                    selected = appSelected,
                    enabled = true,
                    title = stringResource(R.string.export_app_settings),
                    description = stringResource(R.string.export_app_settings_description),
                    iconType = ConnectivityIconType.PHONE,
                    primaryText = primaryText,
                    secondaryText = secondaryText,
                    onClick = { appSelected = !appSelected }
                )
                SettingsExportOptionRow(
                    selected = sensorSelected,
                    enabled = sensorSettingsAvailable,
                    title = stringResource(R.string.export_sensor_settings),
                    description =
                        stringResource(
                            R.string.export_sensor_settings_description_complete
                        ),
                    iconType = ConnectivityIconType.SENSOR,
                    primaryText = primaryText,
                    secondaryText = secondaryText,
                    onClick = {
                        sensorSelected = !sensorSelected
                        if (!sensorSelected) sensitiveInformationSelected = false
                    }
                )
                SettingsExportOptionRow(
                    selected = sensitiveInformationSelected,
                    enabled = sensorSelected && sensorSettingsAvailable,
                    title = stringResource(R.string.settings_include_sensitive_information),
                    description =
                        stringResource(
                            R.string.settings_include_sensitive_information_description
                        ),
                    iconType = ConnectivityIconType.SECURITY,
                    primaryText = primaryText,
                    secondaryText = secondaryText,
                    onClick = {
                        sensitiveInformationSelected = !sensitiveInformationSelected
                    }
                )
            }
        },
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    enabled = canExport,
                    onClick = {
                        if (sensitiveInformationSelected) {
                            showEncryptionPasswordDialog = true
                        } else {
                            onExport(appSelected, sensorSelected, null)
                        }
                    }
                ) { Text(stringResource(R.string.export)) }
                TextButton(
                    onClick = onDismiss,
                    colors = ButtonDefaults.textButtonColors(
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

@Composable
internal fun UvirSettingsPasswordDialog(
    title: String,
    description: String,
    confirmationRequired: Boolean,
    confirmLabel: String,
    cardColor: Color,
    primaryText: Color,
    secondaryText: Color,
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
        validationAttempted && password.length < UVIR_SETTINGS_MINIMUM_PASSWORD_LENGTH
    val passwordsDoNotMatch =
        validationAttempted && confirmationRequired && password != confirmation
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
        validationAttempted = true
        if (
            password.length >= UVIR_SETTINGS_MINIMUM_PASSWORD_LENGTH &&
            (!confirmationRequired || password == confirmation)
        ) {
            onConfirm(password.toCharArray())
        }
    }

    @Composable
    fun PasswordField(
        value: String,
        onValueChange: (String) -> Unit,
        label: String,
        visible: Boolean,
        onVisibilityChange: () -> Unit
    ) {
        val visibilityDescription =
            stringResource(if (visible) R.string.hide_password else R.string.show_password)
        OutlinedTextField(
            value = value,
            onValueChange = {
                onValueChange(it)
                validationAttempted = false
                onInputChanged()
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(label) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            visualTransformation =
                if (visible) VisualTransformation.None
                else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = onVisibilityChange) {
                    UvirPasswordVisibilityIcon(
                        visible = visible,
                        modifier = Modifier.semantics {
                            contentDescription = visibilityDescription
                        },
                        tint = primaryText
                    )
                }
            },
            isError = validationMessage != null,
            colors = UvirOutlinedTextFieldColors()
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
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
                PasswordField(
                    value = password,
                    onValueChange = { password = it },
                    label = stringResource(R.string.settings_encryption_password),
                    visible = passwordVisible,
                    onVisibilityChange = { passwordVisible = !passwordVisible }
                )
                if (confirmationRequired) {
                    PasswordField(
                        value = confirmation,
                        onValueChange = { confirmation = it },
                        label = stringResource(R.string.settings_encryption_password_confirm),
                        visible = confirmationVisible,
                        onVisibilityChange = {
                            confirmationVisible = !confirmationVisible
                        }
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
            }
        },
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = ::submit) {
                    Text(confirmLabel)
                }
                TextButton(
                    onClick = onDismiss,
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

@Composable
internal fun MeasurementDetailShareDialog(
    cardColor: Color,
    primaryText: Color,
    secondaryText: Color,
    partialSessionWarning: Boolean = false,
    partialSessionWarningMessage: String? = null,
    combinedChartFileCount: Int? = null,
    separateChartFileCount: Int? = null,
    onDismiss: () -> Unit,
    onSelectionConfirmed: (
        MeasurementDetailShareSelection,
        UvirExportDestination
    ) -> Unit
) {
    var csvSelected by rememberSaveable {
        mutableStateOf(false)
    }
    var readableTableSelected by rememberSaveable {
        mutableStateOf(false)
    }
    var chartsSelected by rememberSaveable {
        mutableStateOf(false)
    }
    var chartExportMode by rememberSaveable {
        mutableStateOf(UvirChartExportMode.COMBINED)
    }
    val scrollbar =
        rememberUvirDialogScrollbar(
            secondaryText.copy(alpha = 0.58f)
        )

    val dataFormat =
        when {
            csvSelected && readableTableSelected ->
                MeasurementShareFormat.BOTH

            csvSelected -> MeasurementShareFormat.CSV
            readableTableSelected -> MeasurementShareFormat.READABLE_TABLE
            else -> null
        }
    val canShare = dataFormat != null || chartsSelected

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = scrollbar.dialogModifier,
        title = {
            Text(stringResource(R.string.choose_share_format))
        },
        text = {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 460.dp)
                        .then(scrollbar.viewportModifier)
            ) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .verticalScroll(scrollbar.scrollState),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                ShareFormatOptionRow(
                    format = MeasurementShareFormat.CSV,
                    selected = csvSelected,
                    label = stringResource(R.string.share_as_csv),
                    primaryText = primaryText,
                    secondaryText = secondaryText,
                    onClick = {
                        csvSelected = !csvSelected
                    }
                )

                ShareFormatOptionRow(
                    format = MeasurementShareFormat.READABLE_TABLE,
                    selected = readableTableSelected,
                    label =
                        stringResource(
                            R.string.share_as_readable_table
                        ),
                    primaryText = primaryText,
                    secondaryText = secondaryText,
                    onClick = {
                        readableTableSelected =
                            !readableTableSelected
                    }
                )

                ShareChartsOptionRow(
                    selected = chartsSelected,
                    primaryText = primaryText,
                    secondaryText = secondaryText,
                    onClick = {
                        chartsSelected = !chartsSelected
                    }
                )

                if (chartsSelected) {
                    ChartExportModeRow(
                        selected = chartExportMode == UvirChartExportMode.COMBINED,
                        label = stringResource(R.string.share_charts_combined_groups),
                        primaryText = primaryText,
                        secondaryText = secondaryText,
                        onClick = {
                            chartExportMode = UvirChartExportMode.COMBINED
                        }
                    )
                    ChartExportModeRow(
                        selected = chartExportMode == UvirChartExportMode.SEPARATE,
                        label = stringResource(R.string.share_charts_separate_groups),
                        primaryText = primaryText,
                        secondaryText = secondaryText,
                        onClick = {
                            chartExportMode = UvirChartExportMode.SEPARATE
                        }
                    )

                    val chartFileCount =
                        when (chartExportMode) {
                            UvirChartExportMode.COMBINED -> combinedChartFileCount
                            UvirChartExportMode.SEPARATE -> separateChartFileCount
                        }
                    if (chartFileCount != null) {
                        Text(
                            text =
                                stringResource(
                                    R.string.share_chart_file_count,
                                    chartFileCount
                                ),
                            modifier =
                                Modifier.padding(
                                    start = 38.dp,
                                    top = 2.dp,
                                    end = 10.dp
                                ),
                            color = secondaryText,
                            fontSize = 11.sp,
                            lineHeight = 14.sp
                        )
                    }
                }

                if (
                    partialSessionWarning ||
                    partialSessionWarningMessage != null
                ) {
                    UvirAttentionMessage(
                        text =
                            partialSessionWarningMessage
                                ?: stringResource(
                                    R.string.share_partial_session_warning
                                ),
                        modifier = Modifier.padding(top = 4.dp),
                        fontSize = 11.sp,
                        lineHeight = 14.sp
                    )
                }
                }
            }
        },
        confirmButton = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        enabled = canShare,
                        onClick = {
                            onSelectionConfirmed(
                                MeasurementDetailShareSelection(
                                    dataFormat = dataFormat,
                                    includeCharts = chartsSelected,
                                    chartExportMode = chartExportMode
                                ),
                                UvirExportDestination.SAVE
                            )
                        }
                    ) {
                        Text(stringResource(R.string.save))
                    }

                    TextButton(
                        enabled = canShare,
                        onClick = {
                            onSelectionConfirmed(
                                MeasurementDetailShareSelection(
                                    dataFormat = dataFormat,
                                    includeCharts = chartsSelected,
                                    chartExportMode = chartExportMode
                                ),
                                UvirExportDestination.SHARE
                            )
                        }
                    ) {
                        Text(stringResource(R.string.share))
                    }
                }

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End),
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = UvirDestructiveActionColor
                    )
                ) {
                    Text(stringResource(R.string.cancel))
                }
            }
        },
        containerColor = cardColor,
        titleContentColor = primaryText,
        textContentColor = secondaryText
    )
}

@Composable
internal fun HoldToConfirmDeleteButton(
    label: String,
    onConfirmed: () -> Unit,
    holdDurationMillis: Long = 2_000L,
    compactLabel: Boolean = false
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHolding by interactionSource.collectIsPressedAsState()

    val currentOnConfirmed by
        rememberUpdatedState(onConfirmed)

    LaunchedEffect(
        isHolding,
        holdDurationMillis
    ) {
        if (!isHolding) {
            return@LaunchedEffect
        }

        delay(holdDurationMillis)

        if (isHolding) {
            currentOnConfirmed()
        }
    }

    val deleteColor = UvirDestructiveActionColor

    TextButton(
        // A short click intentionally does nothing: confirmation requires a full hold.
        onClick = {},
        interactionSource = interactionSource,
        modifier = Modifier.heightIn(min = 48.dp),
        shape = RoundedCornerShape(50),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        colors = ButtonDefaults.textButtonColors(contentColor = deleteColor)
    ) {
        Text(
            text = label,
            color = deleteColor,
            fontSize =
                if (compactLabel) {
                    11.sp
                } else {
                    14.sp
                },
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            softWrap = false
        )
    }
}
