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
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
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
import androidx.compose.ui.unit.LayoutDirection
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
            modifier =
                Modifier
                    .fillMaxSize()
                    .graphicsLayer(alpha = tint.alpha)
        ) {
            val tint = tint.copy(alpha = 1f)
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
    val toggle: () -> Unit = onClick
    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = toggle),
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
                        toggle()
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
    val toggle: () -> Unit = onClick
    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = toggle),
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
                        toggle()
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
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val select: () -> Unit = onClick
    Surface(
        modifier =
            modifier
                .fillMaxWidth()
                .clickable(onClick = select),
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
                    onClick = select,
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

internal data class UvirExportRequestGate(
    val inProgress: Boolean,
    val request: (UvirExportDestination) -> Unit
)

@Composable
internal fun rememberUvirExportRequestGate(
    onExport: (UvirExportDestination) -> Unit
): UvirExportRequestGate {
    var pendingDestination by remember {
        mutableStateOf<UvirExportDestination?>(null)
    }
    val currentOnExport by rememberUpdatedState(onExport)

    LaunchedEffect(pendingDestination) {
        val destination = pendingDestination ?: return@LaunchedEffect
        withFrameNanos { }
        try {
            currentOnExport(destination)
        } finally {
            pendingDestination = null
        }
    }

    return UvirExportRequestGate(
        inProgress = pendingDestination != null,
        request = { destination ->
            if (pendingDestination == null) {
                pendingDestination = destination
            }
        }
    )
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
    val exportRequest =
        rememberUvirExportRequestGate { destination ->
            selectedFormat?.let { format ->
                onFormatSelected(format, destination)
            }
        }

    UvirAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            UvirClosableDialogTitle(
                title = stringResource(R.string.choose_share_format),
                onDismiss = onDismiss
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
                if (csvSelected) {
                    UvirExportFileCountText(
                        format = UvirExportFileFormat.CSV,
                        count = 1,
                        secondaryText = secondaryText,
                        modifier = Modifier.padding(start = 38.dp)
                    )
                }

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
                if (readableTableSelected) {
                    UvirExportFileCountText(
                        format = UvirExportFileFormat.TXT,
                        count = 1,
                        secondaryText = secondaryText,
                        modifier = Modifier.padding(start = 38.dp)
                    )
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
                        enabled = selectedFormat != null && !exportRequest.inProgress,
                        onClick = {
                            exportRequest.request(UvirExportDestination.SAVE)
                        }
                    ) {
                        Text(stringResource(R.string.save))
                    }

                    TextButton(
                        enabled = selectedFormat != null && !exportRequest.inProgress,
                        onClick = {
                            exportRequest.request(UvirExportDestination.SHARE)
                        }
                    ) {
                        Text(stringResource(R.string.share))
                    }
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
    fileFormat: UvirExportFileFormat,
    fileCount: Int = 1,
    onDismiss: () -> Unit,
    onExport: (UvirExportDestination) -> Unit
) {
    val exportRequest = rememberUvirExportRequestGate(onExport)
    UvirAlertDialog(
        onDismissRequest = {
            if (!exportRequest.inProgress) onDismiss()
        },
        title = {
            UvirClosableDialogTitle(
                title = title,
                onDismiss = {
                    if (!exportRequest.inProgress) onDismiss()
                }
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = description,
                    color = secondaryText
                )
                UvirExportFileCountText(
                    format = fileFormat,
                    count = fileCount,
                    secondaryText = secondaryText
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
                        enabled = !exportRequest.inProgress,
                        onClick = {
                            exportRequest.request(UvirExportDestination.SAVE)
                        }
                    ) {
                        Text(stringResource(R.string.save))
                    }
                    TextButton(
                        enabled = !exportRequest.inProgress,
                        onClick = {
                            exportRequest.request(UvirExportDestination.SHARE)
                        }
                    ) {
                        Text(stringResource(R.string.share))
                    }
                }
            }
        },
        containerColor = cardColor,
        titleContentColor = primaryText,
        textContentColor = secondaryText
    )
}

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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End)
            ) {
                TextButton(
                    enabled = password.length >= UVIR_SETTINGS_MINIMUM_PASSWORD_LENGTH && !inProgress,
                    onClick = { requestExport(UvirExportDestination.SAVE) }
                ) { Text(stringResource(R.string.save)) }
                TextButton(
                    enabled = password.length >= UVIR_SETTINGS_MINIMUM_PASSWORD_LENGTH && !inProgress,
                    onClick = { requestExport(UvirExportDestination.SHARE) }
                ) { Text(stringResource(R.string.share)) }
            }
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically
            ) {
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
            }
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
        if (busy) return
        validationAttempted = true
        if (
            password.length >= UVIR_SETTINGS_MINIMUM_PASSWORD_LENGTH &&
            (!confirmationRequired || password == confirmation)
        ) {
            onConfirm(password.toCharArray())
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
                UvirSettingsPasswordField(
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
                if (confirmationRequired) {
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
            }
        },
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = ::submit, enabled = !busy) {
                    Text(confirmLabel)
                }
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

@Composable
internal fun UvirExportBottomActions(
    enabled: Boolean,
    primaryText: Color,
    secondaryText: Color,
    onSave: () -> Unit,
    onShare: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(UvirCompactActionButtonGap)
    ) {
        val buttonColors = uvirOutlinedActionColors(primaryText)
        HorizontalDivider(color = secondaryText.copy(alpha = 0.22f))
        Spacer(Modifier.height(2.dp))
        OutlinedButton(
            onClick = onSave,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
            colors = buttonColors,
            border = uvirOutlinedActionBorder(enabled, secondaryText)
        ) {
            UvirLabeledButtonContent(
                text = stringResource(R.string.save),
                maxFontSize = 14.sp,
                minFontSize = 10.sp
            ) {
                UvirTitleSaveToFolderIcon(
                    modifier = Modifier.size(20.dp),
                    tint = LocalContentColor.current
                )
            }
        }
        OutlinedButton(
            onClick = onShare,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
            colors = buttonColors,
            border = uvirOutlinedActionBorder(enabled, secondaryText)
        ) {
            UvirLabeledButtonContent(
                text = stringResource(R.string.share),
                maxFontSize = 14.sp,
                minFontSize = 10.sp
            ) {
                UvirTitleActionIcon(
                    type = MenuIconType.SHARE,
                    modifier = Modifier.size(20.dp),
                    tint = LocalContentColor.current
                )
            }
        }
    }
}

@Composable
internal fun UvirExportFileCountText(
    format: UvirExportFileFormat,
    count: Int,
    secondaryText: Color,
    modifier: Modifier = Modifier
) {
    Text(
        text = stringResource(
            R.string.export_file_count,
            format.displayName,
            count.coerceAtLeast(0)
        ),
        modifier = modifier,
        color = secondaryText,
        fontSize = 11.sp,
        lineHeight = 14.sp
    )
}

@Composable
internal fun UvirExportProfileSection(
    exportMode: UvirExportMode,
    onExportModeChange: (UvirExportMode) -> Unit,
    primaryText: Color,
    secondaryText: Color,
    showPreview: Boolean = false
) {
    Text(
        text = stringResource(R.string.export_profile_heading),
        color = primaryText,
        fontSize = 15.sp,
        fontWeight = FontWeight.Bold
    )
    UvirExportMode.entries.forEach { mode ->
        SettingsRadioChoiceRow(
            selected = exportMode == mode,
            primaryText = primaryText,
            secondaryText = secondaryText,
            onClick = { onExportModeChange(mode) }
        ) {
            Text(
                text =
                    stringResource(
                        when (mode) {
                            UvirExportMode.SELECTED ->
                                R.string.export_format_selected
                            UvirExportMode.INTERNATIONAL ->
                                R.string.export_format_international
                        }
                    ),
                modifier = Modifier.weight(1f),
                color = primaryText,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }

    if (showPreview) {
        val context = LocalContext.current
        val preview = uvirExportFormatting(context, exportMode)
        val previewTimestamp = 1_789_499_742_000L
        val previewDateTime =
            "${formatUvirDateOnly(previewTimestamp, preview.dateFormat)} · " +
                formatUvirTimeOnly(previewTimestamp, preview.timeFormat)
        val previewTotal =
            formatUvirIrradianceNumber(
                canonicalUwCm2 = 123456.78,
                fractionDigits = 3,
                numericFormat = preview.numericFormat,
                unit = preview.irradianceUnit
            )
        val previewUva =
            formatUvirIrradianceNumber(
                canonicalUwCm2 = 89214.32,
                fractionDigits = 3,
                numericFormat = preview.numericFormat,
                unit = preview.irradianceUnit
            )

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = secondaryText.copy(alpha = 0.08f),
            contentColor = primaryText
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text =
                        "${preview.context.getString(R.string.share_acquisition_label)} #12",
                    color = primaryText,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = previewDateTime,
                    color = secondaryText,
                    fontSize = 11.sp,
                    lineHeight = 14.sp
                )
                HorizontalDivider(color = secondaryText.copy(alpha = 0.20f))
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = preview.context.getString(R.string.uv_radiation),
                        modifier = Modifier.weight(1f),
                        color = primaryText,
                        fontSize = 12.sp
                    )
                    Text(
                        text = "$previewTotal ${preview.irradianceUnit.symbol}",
                        color = primaryText,
                        fontSize = 12.sp
                    )
                }
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "UVA",
                        modifier = Modifier.weight(1f),
                        color = primaryText,
                        fontSize = 12.sp
                    )
                    Text(
                        text = "$previewUva ${preview.irradianceUnit.symbol}",
                        color = primaryText,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

@Composable
internal fun MeasurementDataExportScreen(
    backgroundColor: Color,
    cardColor: Color,
    primaryText: Color,
    secondaryText: Color,
    partialSessionWarning: Boolean = false,
    partialSessionWarningMessage: String? = null,
    readableTableFileCount: Int? = null,
    readableTableGroupingAvailable: Boolean = false,
    showReadableTableGroupingScopeNote: Boolean = false,
    combinedChartFileCount: Int? = null,
    separateChartFileCount: Int? = null,
    variantChartGroupingAvailable: Boolean = false,
    groupedVariantChartFileCount: Int? = null,
    showVariantChartGroupingScopeNote: Boolean = false,
    onDismiss: () -> Unit,
    onSelectionConfirmed: (
        MeasurementDetailShareSelection,
        UvirExportDestination
    ) -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val savedExportPreferences =
        remember(context) {
            loadUvirDataExportPreferences(context)
        }
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
        mutableStateOf(savedExportPreferences.chartExportMode)
    }
    var readableTableGrouping by rememberSaveable {
        mutableStateOf(savedExportPreferences.readableTableGrouping)
    }
    var variantChartGrouping by rememberSaveable {
        mutableStateOf(savedExportPreferences.variantChartGrouping)
    }
    var exportMode by rememberSaveable {
        mutableStateOf(loadUvirExportMode(context))
    }

    val dataFormat =
        when {
            csvSelected && readableTableSelected ->
                MeasurementShareFormat.BOTH

            csvSelected -> MeasurementShareFormat.CSV
            readableTableSelected -> MeasurementShareFormat.READABLE_TABLE
            else -> null
        }
    val canExport = dataFormat != null || chartsSelected
    val selection =
        MeasurementDetailShareSelection(
            dataFormat = dataFormat,
            includeCharts = chartsSelected,
            chartExportMode = chartExportMode,
            variantChartGrouping = variantChartGrouping,
            readableTableGrouping = readableTableGrouping
        )
    val exportRequest =
        rememberUvirExportRequestGate { destination ->
            saveUvirExportMode(context, exportMode)
            saveUvirDataExportPreferences(
                context = context,
                preferences =
                    UvirDataExportPreferences(
                        chartExportMode = chartExportMode,
                        readableTableGrouping = readableTableGrouping,
                        variantChartGrouping = variantChartGrouping
                    )
            )
            onSelectionConfirmed(selection, destination)
        }

    UvirFullScreenPage(
        onDismissRequest = {
            if (!exportRequest.inProgress) onDismiss()
        },
        title = {
            UvirMenuTitle(
                text = stringResource(R.string.data_export_title),
                color = primaryText
            )
        },
        containerColor = backgroundColor,
        contentColor = primaryText,
        scrollState = scrollState,
        scrollbarColor = secondaryText.copy(alpha = 0.46f),
        text = {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = cardColor,
                    contentColor = primaryText
                ) {
                    Column(
                        modifier = Modifier.padding(UvirIslandContentPadding),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                SettingsPageDescription(
                    text = stringResource(R.string.data_export_description),
                    color = secondaryText
                )
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
                if (csvSelected) {
                    UvirExportFileCountText(
                        format = UvirExportFileFormat.CSV,
                        count = 1,
                        secondaryText = secondaryText,
                        modifier =
                            Modifier.padding(
                                start = 38.dp,
                                top = 2.dp,
                                end = 10.dp
                            )
                    )
                }

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

                if (readableTableSelected && readableTableGroupingAvailable) {
                    ChartExportModeRow(
                        selected =
                            readableTableGrouping ==
                                UvirReadableTableGrouping.BY_ACQUISITION,
                        label =
                            stringResource(
                                R.string.share_readable_table_by_acquisition
                            ),
                        primaryText = primaryText,
                        secondaryText = secondaryText,
                        modifier = Modifier.padding(start = 22.dp),
                        onClick = {
                            readableTableGrouping =
                                UvirReadableTableGrouping.BY_ACQUISITION
                        }
                    )
                    ChartExportModeRow(
                        selected =
                            readableTableGrouping ==
                                UvirReadableTableGrouping.BY_SPECTRAL_AREA,
                        label =
                            stringResource(
                                R.string.share_readable_table_by_spectral_area
                            ),
                        primaryText = primaryText,
                        secondaryText = secondaryText,
                        modifier = Modifier.padding(start = 22.dp),
                        onClick = {
                            readableTableGrouping =
                                UvirReadableTableGrouping.BY_SPECTRAL_AREA
                        }
                    )
                    if (showReadableTableGroupingScopeNote) {
                        Text(
                            text =
                                stringResource(
                                    R.string.share_readable_grouping_complete_sessions_only
                                ),
                            modifier =
                                Modifier.padding(
                                    start = 38.dp,
                                    top = 1.dp,
                                    end = 10.dp,
                                    bottom = 1.dp
                                ),
                            color = secondaryText,
                            fontSize = 11.sp,
                            lineHeight = 14.sp
                        )
                    }
                }

                if (readableTableSelected && readableTableFileCount != null) {
                    UvirExportFileCountText(
                        format = UvirExportFileFormat.TXT,
                        count = readableTableFileCount,
                        secondaryText = secondaryText,
                        modifier =
                            Modifier.padding(
                                start = 38.dp,
                                top = 2.dp,
                                end = 10.dp
                            )
                    )
                }

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
                    if (
                        chartExportMode == UvirChartExportMode.COMBINED &&
                        variantChartGroupingAvailable
                    ) {
                        ChartExportModeRow(
                            selected =
                                variantChartGrouping ==
                                    UvirVariantChartGrouping.BY_VARIANT,
                            label =
                                stringResource(
                                    R.string.share_variant_charts_by_variant
                                ),
                            primaryText = primaryText,
                            secondaryText = secondaryText,
                            modifier = Modifier.padding(start = 22.dp),
                            onClick = {
                                variantChartGrouping =
                                    UvirVariantChartGrouping.BY_VARIANT
                            }
                        )
                        ChartExportModeRow(
                            selected =
                                variantChartGrouping ==
                                    UvirVariantChartGrouping.BY_GROUP,
                            label =
                                stringResource(
                                    R.string.share_variant_charts_by_group
                                ),
                            primaryText = primaryText,
                            secondaryText = secondaryText,
                            modifier = Modifier.padding(start = 22.dp),
                            onClick = {
                                variantChartGrouping =
                                    UvirVariantChartGrouping.BY_GROUP
                            }
                        )
                        if (showVariantChartGroupingScopeNote) {
                            Text(
                                text =
                                    stringResource(
                                        R.string.share_variant_grouping_complete_sessions_only
                                    ),
                                modifier =
                                    Modifier.padding(
                                        start = 38.dp,
                                        top = 1.dp,
                                        end = 10.dp,
                                        bottom = 1.dp
                                    ),
                                color = secondaryText,
                                fontSize = 11.sp,
                                lineHeight = 14.sp
                            )
                        }
                    }
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
                            UvirChartExportMode.COMBINED ->
                                if (
                                    variantChartGroupingAvailable &&
                                    variantChartGrouping ==
                                        UvirVariantChartGrouping.BY_GROUP
                                ) {
                                    groupedVariantChartFileCount
                                } else {
                                    combinedChartFileCount
                                }
                            UvirChartExportMode.SEPARATE -> separateChartFileCount
                        }
                    if (chartFileCount != null) {
                        UvirExportFileCountText(
                            format = UvirExportFileFormat.PNG,
                            count = chartFileCount,
                            secondaryText = secondaryText,
                            modifier =
                                Modifier.padding(
                                    start = 38.dp,
                                    top = 2.dp,
                                    end = 10.dp
                                )
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

                Spacer(Modifier.height(8.dp))
                HorizontalDivider(color = secondaryText.copy(alpha = 0.22f))
                Spacer(Modifier.height(2.dp))
                UvirExportProfileSection(
                    exportMode = exportMode,
                    onExportModeChange = { exportMode = it },
                    primaryText = primaryText,
                    secondaryText = secondaryText
                )
                Spacer(Modifier.height(8.dp))
                UvirExportBottomActions(
                    enabled = canExport && !exportRequest.inProgress,
                    primaryText = primaryText,
                    secondaryText = secondaryText,
                    onSave = {
                        exportRequest.request(UvirExportDestination.SAVE)
                    },
                    onShare = {
                        exportRequest.request(UvirExportDestination.SHARE)
                    }
                )
                    }
                }
            }
        }
    )
}

@Composable
internal fun UvirHoldConfirmationMessage(
    message: String,
    actionLabel: String,
    holdDurationSeconds: Int,
    replaceEmbeddedInstruction: Boolean = true
) {
    val mainMessage =
        remember(message, replaceEmbeddedInstruction) {
            if (replaceEmbeddedInstruction) {
                splitUvirHoldConfirmationMessage(message).first
            } else {
                message.trim()
            }
        }
    Column {
        Text(mainMessage)
        Spacer(Modifier.height(8.dp))
        UvirHoldConfirmationInstruction(actionLabel, holdDurationSeconds)
    }
}

@Composable
internal fun UvirHoldConfirmationInstruction(actionLabel: String, holdDurationSeconds: Int) {
    Text(
        text = stringResource(R.string.hold_action_seconds_confirmation, actionLabel, holdDurationSeconds),
        fontWeight = FontWeight.Medium
    )
}

internal fun splitUvirHoldConfirmationMessage(
    message: String
): Pair<String, String?> {
    val normalized = message.trim()
    val match =
        Regex("(?s)^(.+[.!?。！？؟।])\\s*(.+)$")
            .matchEntire(normalized)
    return if (match != null) {
        match.groupValues[1].trim() to match.groupValues[2].trim()
    } else {
        normalized to null
    }
}

@Composable
internal fun UvirDeleteConfirmationMessage(warning: String) {
    UvirHoldConfirmationMessage(
        message = warning,
        actionLabel = stringResource(R.string.delete),
        holdDurationSeconds = 2
    )
}

@Composable
internal fun HoldToConfirmDeleteButton(
    label: String,
    onConfirmed: () -> Unit,
    holdDurationMillis: Long = 2_000L,
    compactLabel: Boolean = false
) {
    HoldToConfirmActionButton(
        label = label,
        onConfirmed = onConfirmed,
        holdDurationMillis = holdDurationMillis,
        compactLabel = compactLabel
    )
}

@Composable
internal fun HoldToConfirmActionButton(
    label: String,
    onConfirmed: () -> Unit,
    enabled: Boolean = true,
    holdDurationMillis: Long = 2_000L,
    compactLabel: Boolean = false,
    actionColor: Color = UvirDestructiveActionColor
) {
    var isHolding by remember { mutableStateOf(false) }
    val holdProgress = remember { Animatable(0f) }
    val accessibilityConfirmationArmed = remember { mutableStateOf(false) }
    val accessibilityConfirmationMessage =
        stringResource(R.string.accessibility_activate_again_to_confirm)

    val currentOnConfirmed by
        rememberUpdatedState(onConfirmed)

    LaunchedEffect(
        isHolding,
        enabled,
        holdDurationMillis
    ) {
        if (!enabled || !isHolding) {
            holdProgress.snapTo(0f)
            return@LaunchedEffect
        }

        holdProgress.snapTo(0f)
        holdProgress.animateTo(
            targetValue = 1f,
            animationSpec =
                tween(
                    durationMillis =
                        holdDurationMillis
                            .coerceIn(1L, Int.MAX_VALUE.toLong())
                            .toInt(),
                    easing = LinearEasing
                )
        )

        if (isHolding) {
            accessibilityConfirmationArmed.value = false
            currentOnConfirmed()
        }
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier =
            Modifier
                .heightIn(min = 48.dp)
                .clip(RoundedCornerShape(50))
                .drawBehind {
                    val progressWidth = size.width * holdProgress.value
                    if (progressWidth > 0f) {
                        drawRect(
                            color = actionColor.copy(alpha = 0.12f),
                            topLeft =
                                Offset(
                                    x =
                                        if (layoutDirection == LayoutDirection.Rtl) {
                                            size.width - progressWidth
                                        } else {
                                            0f
                                        },
                                    y = 0f
                                ),
                            size = Size(progressWidth, size.height)
                        )
                    }
                }
                // Pointer gestures keep the physical press-and-hold behavior without publishing a
                // competing semantic click action. Assistive technologies use the action below.
                .pointerInput(enabled) {
                    if (enabled) {
                        detectTapGestures(
                            onPress = {
                                isHolding = true
                                try {
                                    awaitRelease()
                                } finally {
                                    isHolding = false
                                }
                            }
                        )
                    }
                }
                .uvirAccessibleAction(
                    label = label,
                    enabled = enabled,
                    stateText =
                        if (accessibilityConfirmationArmed.value) {
                            accessibilityConfirmationMessage
                        } else {
                            null
                        },
                    onClick = {
                        if (accessibilityConfirmationArmed.value) {
                            accessibilityConfirmationArmed.value = false
                            currentOnConfirmed()
                        } else {
                            accessibilityConfirmationArmed.value = true
                        }
                    }
                )
                .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(
            text = label,
            color = if (enabled) actionColor else actionColor.copy(alpha = 0.38f),
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
