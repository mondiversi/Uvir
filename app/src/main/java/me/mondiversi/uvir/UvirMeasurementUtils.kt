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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
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

fun formatInterval(totalSeconds: Long): String {

    val safeSeconds = totalSeconds.coerceAtLeast(0L)
    val hours = safeSeconds / 3600L
    val minutes = (safeSeconds % 3600L) / 60L
    val seconds = safeSeconds % 60L

    return "%02d:%02d:%02d".format(
        Locale.US,
        hours,
        minutes,
        seconds
    )
}

fun generateRandomSample(): SensorSample {
    return SensorSample(
        uvc = Random.nextDouble(0.0, 80.0),
        uvb = Random.nextDouble(10.0, 500.0),
        uva = Random.nextDouble(100.0, 2500.0),

        violetto = Random.nextDouble(20.0, 250.0),
        blu = Random.nextDouble(50.0, 500.0),
        verde = Random.nextDouble(100.0, 650.0),
        giallo = Random.nextDouble(50.0, 400.0),
        arancione = Random.nextDouble(30.0, 350.0),
        rosso = Random.nextDouble(80.0, 600.0),

        f8 = Random.nextDouble(50.0, 700.0),
        nir = Random.nextDouble(50.0, 900.0)
    )
}

fun averageValues(
    values: List<Double>,
    discardExtremes: Boolean
): Double {

    if (values.isEmpty()) {
        return 0.0
    }

    if (!discardExtremes || values.size < 3) {
        return values.average()
    }

    val sorted = values.sorted()

    return sorted
        .drop(1)
        .dropLast(1)
        .average()
}

fun combineSamples(
    samples: List<SensorSample>,
    discardExtremes: Boolean
): SensorSample {

    return SensorSample(
        uvc = averageValues(samples.map { it.uvc }, discardExtremes),
        uvb = averageValues(samples.map { it.uvb }, discardExtremes),
        uva = averageValues(samples.map { it.uva }, discardExtremes),

        violetto = averageValues(samples.map { it.violetto }, discardExtremes),
        blu = averageValues(samples.map { it.blu }, discardExtremes),
        verde = averageValues(samples.map { it.verde }, discardExtremes),
        giallo = averageValues(samples.map { it.giallo }, discardExtremes),
        arancione = averageValues(samples.map { it.arancione }, discardExtremes),
        rosso = averageValues(samples.map { it.rosso }, discardExtremes),

        f8 = averageValues(samples.map { it.f8 }, discardExtremes),
        nir = averageValues(samples.map { it.nir }, discardExtremes)
    )
}

fun nextOccurrenceMillis(
    hour: Int,
    minute: Int,
    nowMillis: Long = System.currentTimeMillis()
): Long {

    val zone = ZoneId.systemDefault()
    val now = Instant.ofEpochMilli(nowMillis)
        .atZone(zone)
        .toLocalDateTime()

    var candidate = LocalDateTime.of(
        now.toLocalDate(),
        LocalTime.of(hour, minute)
    )

    if (!candidate.isAfter(now)) {
        candidate = candidate.plusDays(1)
    }

    return candidate
        .atZone(zone)
        .toInstant()
        .toEpochMilli()
}

fun occurrenceAtOrAfterMillis(
    referenceMillis: Long,
    hour: Int,
    minute: Int
): Long {

    val zone = ZoneId.systemDefault()
    val reference = Instant.ofEpochMilli(referenceMillis)
        .atZone(zone)
        .toLocalDateTime()

    var candidate = LocalDateTime.of(
        reference.toLocalDate(),
        LocalTime.of(hour, minute)
    )

    if (!candidate.isAfter(reference)) {
        candidate = candidate.plusDays(1)
    }

    return candidate
        .atZone(zone)
        .toInstant()
        .toEpochMilli()
}

fun formatClockTime(timestamp: Long): String {
    return SimpleDateFormat(
        "HH:mm",
        Locale.getDefault()
    ).format(Date(timestamp))
}

fun biologicalEffects(
    sample: SensorSample
): BiologicalEffectEstimate {

    // Broad-band exploratory weighted estimates only.
    // The weighted signals preserve irradiance-like units because the
    // prototype weights are dimensionless.
    //
    // The 0–100 scores describe relative spectral weighting within the
    // relevant measured band. They are NOT percentages of biological damage
    // and are NOT safety thresholds.

    val uvTotal =
        sample.uvc +
                sample.uvb +
                sample.uva

    val visibleTotal =
        sample.violetto +
                sample.blu +
                sample.verde +
                sample.giallo +
                sample.arancione +
                sample.rosso

    val dnaUv =
        sample.uvc * 1.00 +
                sample.uvb * 0.60 +
                sample.uva * 0.01

    val dnaUvScore =
        if (uvTotal > 0.0)
            (dnaUv / uvTotal)
                .toFloat()
                .coerceIn(0f, 1f)
        else
            0f

    val uvaPhotoaging =
        sample.uva +
                sample.uvb * 0.05

    val uvaPhotoagingScore =
        if (uvTotal > 0.0)
            (uvaPhotoaging / uvTotal)
                .toFloat()
                .coerceIn(0f, 1f)
        else
            0f

    val hev =
        sample.violetto +
                sample.blu

    val hevOxidativeScore =
        if (visibleTotal > 0.0)
            (hev / visibleTotal)
                .toFloat()
                .coerceIn(0f, 1f)
        else
            0f

    return BiologicalEffectEstimate(
        dnaUvProxy = dnaUv,
        dnaUvScore = dnaUvScore,

        uvaPhotoagingProxy = uvaPhotoaging,
        uvaPhotoagingScore = uvaPhotoagingScore,

        hevOxidativeProxy = hev,
        hevOxidativeScore = hevOxidativeScore
    )
}

fun percentage(
    value: Double,
    total: Double
): Float {

    return if (total > 0.0) {
        (value / total)
            .toFloat()
            .coerceIn(0f, 1f)
    } else {
        0f
    }
}

fun isAutomaticBackgroundUnrestricted(
    context: Context
): Boolean {
    val powerManager =
        context.getSystemService(
            Context.POWER_SERVICE
        ) as PowerManager

    return powerManager
        .isIgnoringBatteryOptimizations(
            context.packageName
        )
}

fun openAutomaticBackgroundSettings(
    context: Context
) {
    val packageUri =
        Uri.parse(
            "package:${context.packageName}"
        )

    val directRequest =
        Intent(
            Settings
                .ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            packageUri
        ).addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK
        )

    runCatching {
        context.startActivity(
            directRequest
        )
    }.getOrElse {
        context.startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                packageUri
            ).addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK
            )
        )
    }
}


// =====================================================
// APP
// =====================================================
