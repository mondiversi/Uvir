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

enum class SensorGroup {
    UV,
    VISIBLE,
    NIR,
    BIOLOGICAL
}

enum class BiologicalEffectGroup {
    DNA_UV,
    UVA_PHOTOAGING,
    HEV_OXIDATIVE
}

enum class SensorConnectionState {
    CONNECTING,
    CONNECTED,
    DISCONNECTED,
    ERROR
}

enum class ViewMode {
    IRRADIANCE,
    BIOLOGICAL_EFFECTS
}

data class AutomaticAcquisitionRequest(
    val intervalSeconds: Long,
    val note: String,
    val useStartDelay: Boolean,
    val startDelaySeconds: Long,
    val useDuration: Boolean,
    val durationSeconds: Long,
    val limitEnabled: Boolean,
    val maxAcquisitions: Int,
    val conditionalPlan: ConditionalAcquisitionPlan? = null
)

data class AcquisitionParameters(
    val samplesPerMeasurement: Int,
    val sampleSpacingMs: Long,
    val discardExtremes: Boolean
)

enum class ThresholdAlertMetric {
    UV_TOTAL,
    UVC,
    UVB,
    UVA,
    HEV,
    HEB,
    VISIBLE_TOTAL,
    VIOLET,
    BLUE,
    GREEN,
    YELLOW,
    ORANGE,
    RED,
    NIR_TOTAL,
    FAR_RED,
    NIR,
    BIO_DNA_UV,
    BIO_UVA_PHOTOAGING,
    BIO_HEV_OXIDATIVE
}

enum class ThresholdAlertDirection {
    ABOVE,
    BELOW
}

enum class ThresholdAlertSound {
    SILENT,
    VIBRATION,
    SINGLE_BEEP,
    DOUBLE_BEEP,
    TRIPLE_BEEP,
    LONG_BEEP
}

data class ThresholdAlertSettings(
    val enabled: Boolean,
    val rules: List<ThresholdAlertRule>,
    val repeatSeconds: Int,
    val sound: ThresholdAlertSound,
    val volume: Int
)

data class ThresholdAlertRule(
    val metric: ThresholdAlertMetric,
    val enabled: Boolean,
    val direction: ThresholdAlertDirection,
    val threshold: Float
)

data class ThresholdAlertViolation(
    val rule: ThresholdAlertRule,
    val value: Double
)

data class ThresholdAlertLogEntry(
    val id: Long,
    val timestamp: Long,
    val details: String,
    val sessionId: Long? = null,
    val sessionSequence: Int? = null,
    val note: String = "",
    val sensorId: Long? = null,
    // Resolved from the sensor profile when read; not duplicated in record storage.
    val sensorDisplayName: String = ""
)

data class SensorLiveAlertEvent(
    val receiptSequence: Long,
    val timestampMs: Long,
    val details: String,
    val sessionId: Long = 0L
)

internal data class ThresholdNotificationAlert(
    val metric: ThresholdAlertMetric,
    val value: Double,
    val timestamp: Long
)

internal fun formatAutomaticMeasurementNote(
    defaultNote: String
): String {
    return defaultNote.trim()
}

data class BiologicalEffectEstimate(
    val dnaUvProxy: Double,
    val dnaUvScore: Float,

    val uvaPhotoagingProxy: Double,
    val uvaPhotoagingScore: Float,

    val hevOxidativeProxy: Double,
    val hevOxidativeScore: Float
)

data class SensorSample(
    val uvc: Double = 0.0,
    val uvb: Double = 0.0,
    val uva: Double = 0.0,

    val violetto: Double = 0.0,
    val blu: Double = 0.0,
    val verde: Double = 0.0,
    val giallo: Double = 0.0,
    val arancione: Double = 0.0,
    val rosso: Double = 0.0,

    val f8: Double = 0.0,
    val nir: Double = 0.0
)

private const val SENSOR_SAMPLE_VALUE_COUNT = 11

private fun SensorSample.toSaveableValues(): List<Double> =
    listOf(
        uvc,
        uvb,
        uva,
        violetto,
        blu,
        verde,
        giallo,
        arancione,
        rosso,
        f8,
        nir
    )

private fun sensorSampleFromValues(
    values: List<Double>
): SensorSample =
    SensorSample(
        uvc = values[0],
        uvb = values[1],
        uva = values[2],
        violetto = values[3],
        blu = values[4],
        verde = values[5],
        giallo = values[6],
        arancione = values[7],
        rosso = values[8],
        f8 = values[9],
        nir = values[10]
    )

internal val SensorSampleSaver =
    listSaver<SensorSample, Double>(
        save = {
            it.toSaveableValues()
        },
        restore = {
            sensorSampleFromValues(it)
        }
    )

internal val SensorSampleListSaver =
    listSaver<List<SensorSample>, Double>(
        save = { samples ->
            samples.flatMap {
                it.toSaveableValues()
            }
        },
        restore = { values ->
            values
                .chunked(
                    SENSOR_SAMPLE_VALUE_COUNT
                )
                .filter {
                    it.size ==
                            SENSOR_SAMPLE_VALUE_COUNT
                }
                .map {
                    sensorSampleFromValues(it)
                }
        }
    )

data class SavedRecordSummary(
    val id: Long,
    val timestamp: Long,
    val note: String,
    val automatic: Boolean,
    val sessionId: Long? = null,
    val sessionSequence: Int? = null,
    val sensorId: Long? = null
)

data class SavedRecordDetail(
    val id: Long,
    val timestamp: Long,
    val note: String,
    val automatic: Boolean,
    val sample: SensorSample,
    val sessionId: Long? = null,
    val sessionSequence: Int? = null,
    val sensorId: Long? = null,
    // Resolved from the sensor profile when read; not duplicated in record storage.
    val sensorDisplayName: String = ""
)
