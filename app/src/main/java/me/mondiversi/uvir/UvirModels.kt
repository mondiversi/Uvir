package me.mondiversi.uvir

import androidx.compose.runtime.saveable.listSaver

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
    val conditionalPlan: ConditionalAcquisitionPlan? = null,
    val externalCommand: Boolean = false
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
    val qualityFlags: Int = 0,
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
    val nir: Double = 0.0,
    val qualityFlags: Int = 0
)

internal const val UVIR_QUALITY_UV_OUT_OF_RANGE = 1
internal const val UVIR_QUALITY_VISIBLE_NIR_OUT_OF_RANGE = 1 shl 1

internal fun SensorSample.hasAnyOutOfRangeValue(): Boolean = qualityFlags != 0

internal fun SensorSample.isOutOfRange(group: SensorGroup): Boolean =
    when (group) {
        SensorGroup.UV -> qualityFlags and UVIR_QUALITY_UV_OUT_OF_RANGE != 0
        SensorGroup.VISIBLE,
        SensorGroup.NIR -> qualityFlags and UVIR_QUALITY_VISIBLE_NIR_OUT_OF_RANGE != 0
        SensorGroup.BIOLOGICAL -> qualityFlags != 0
    }

internal fun SensorSample.isOutOfRange(metric: ThresholdAlertMetric): Boolean =
    qualityFlags.isOutOfRange(metric)

internal fun Int.isOutOfRange(metric: ThresholdAlertMetric): Boolean =
    when (metric) {
        ThresholdAlertMetric.UV_TOTAL,
        ThresholdAlertMetric.UVC,
        ThresholdAlertMetric.UVB,
        ThresholdAlertMetric.UVA,
        ThresholdAlertMetric.BIO_DNA_UV,
        ThresholdAlertMetric.BIO_UVA_PHOTOAGING ->
            this and UVIR_QUALITY_UV_OUT_OF_RANGE != 0

        else -> this and UVIR_QUALITY_VISIBLE_NIR_OUT_OF_RANGE != 0
    }

private const val SENSOR_SAMPLE_VALUE_COUNT = 12

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
        nir,
        qualityFlags.toDouble()
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
        nir = values[10],
        qualityFlags = values[11].toInt()
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
    val sensorId: Long? = null,
    val externalCommand: Boolean = false,
    val positionIndex: Int? = null,
    val variantIndex: Int? = null
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
    val sensorDisplayName: String = "",
    val externalCommand: Boolean = false,
    val positionIndex: Int? = null,
    val variantIndex: Int? = null
)
