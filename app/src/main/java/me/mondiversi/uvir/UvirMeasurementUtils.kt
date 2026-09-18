package me.mondiversi.uvir

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.Date
import java.util.Locale
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

fun generateRandomSample(
    simulateOutOfRange: Boolean = false
): SensorSample {
    val qualityFlags =
        if (simulateOutOfRange && Random.nextInt(8) == 0) {
            if (Random.nextBoolean()) {
                UVIR_QUALITY_UV_OUT_OF_RANGE
            } else {
                UVIR_QUALITY_VISIBLE_NIR_OUT_OF_RANGE
            }
        } else {
            0
        }
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
        nir = Random.nextDouble(50.0, 900.0),
        qualityFlags = qualityFlags
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
        nir = averageValues(samples.map { it.nir }, discardExtremes),
        qualityFlags = samples.fold(0) { flags, sample -> flags or sample.qualityFlags }
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
    if (timestamp <= 0L) return "—"
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
