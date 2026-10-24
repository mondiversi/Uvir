package me.mondiversi.uvir

import androidx.compose.ui.graphics.Color
import java.util.Calendar
import java.util.TimeZone
import kotlin.math.roundToLong

internal enum class SessionChartGroup(
    val titleResource: Int,
    val exportTitle: String,
    val biological: Boolean = false
) {
    UV(
        R.string.uv_radiation,
        "Ultraviolet"
    ),
    VISIBLE(
        R.string.visible_light,
        "Visible light"
    ),
    FAR_RED_NIR(
        R.string.far_red_nir,
        "Infrared"
    ),
    BIOLOGICAL(
        R.string.biological_effects_group_name,
        "Estimated biological effects",
        biological = true
    )
}

internal data class SessionChartSeries(
    val label: String,
    val color: Color,
    val values: List<Double>,
    val displayLabelResource: Int? = null,
    val outOfRange: List<Boolean> = emptyList()
)

internal enum class SessionChartShareScope {
    CURRENT,
    ALL
}

internal fun sessionChartTimeTickFractions(
    availableWidth: Float,
    minimumTickSpacing: Float
): List<Float> {
    val intervalCount =
        if (
            availableWidth >=
                minimumTickSpacing * 4f
        ) {
            4
        } else {
            2
        }

    return (0..intervalCount).map { index ->
        index.toFloat() / intervalCount.toFloat()
    }
}

internal fun sessionChartTimestampAt(
    startTimestamp: Long,
    endTimestamp: Long,
    fraction: Float
): Long =
    startTimestamp +
        (
            (endTimestamp - startTimestamp) *
                fraction.coerceIn(0f, 1f)
            ).roundToLong()

internal fun sessionChartSpansMultipleDays(
    startTimestamp: Long,
    endTimestamp: Long,
    timeZone: TimeZone = TimeZone.getDefault()
): Boolean {
    if (startTimestamp <= 0L || endTimestamp <= 0L) return false
    val start = Calendar.getInstance(timeZone).apply { timeInMillis = startTimestamp }
    val end = Calendar.getInstance(timeZone).apply { timeInMillis = endTimestamp }
    return start.get(Calendar.ERA) != end.get(Calendar.ERA) ||
        start.get(Calendar.YEAR) != end.get(Calendar.YEAR) ||
        start.get(Calendar.DAY_OF_YEAR) != end.get(Calendar.DAY_OF_YEAR)
}

internal fun sessionChartSeries(
    records: List<SavedRecordDetail>,
    group: SessionChartGroup
): List<SessionChartSeries> {
    fun values(
        selector: (SensorSample) -> Double
    ) = records.map { selector(it.sample) }

    val series = when (group) {
        SessionChartGroup.UV ->
            listOf(
                SessionChartSeries(
                    "Ultraviolet",
                    uvirSpectralColor(ThresholdAlertMetric.UV_TOTAL),
                    values { it.uvc + it.uvb + it.uva },
                    R.string.uv_radiation
                ),
                SessionChartSeries(
                    "UVC",
                    uvirSpectralColor(ThresholdAlertMetric.UVC),
                    values { it.uvc }
                ),
                SessionChartSeries(
                    "UVB",
                    uvirSpectralColor(ThresholdAlertMetric.UVB),
                    values { it.uvb }
                ),
                SessionChartSeries(
                    "UVA",
                    uvirSpectralColor(ThresholdAlertMetric.UVA),
                    values { it.uva }
                )
            )

        SessionChartGroup.VISIBLE ->
            listOf(
                SessionChartSeries(
                    "Visible light",
                    uvirSpectralColor(ThresholdAlertMetric.VISIBLE_TOTAL),
                    values {
                        it.violetto + it.blu + it.verde +
                            it.giallo + it.arancione + it.rosso
                    },
                    R.string.visible_light
                ),
                SessionChartSeries(
                    "HEV",
                    uvirSpectralColor(ThresholdAlertMetric.HEV),
                    values { it.violetto + it.blu },
                    R.string.threshold_channel_hev
                ),
                SessionChartSeries(
                    "Violet",
                    uvirSpectralColor(ThresholdAlertMetric.VIOLET),
                    values { it.violetto },
                    R.string.violet
                ),
                SessionChartSeries(
                    "Blue",
                    uvirSpectralColor(ThresholdAlertMetric.BLUE),
                    values { it.blu },
                    R.string.blue
                ),
                SessionChartSeries(
                    "Green",
                    uvirSpectralColor(ThresholdAlertMetric.GREEN),
                    values { it.verde },
                    R.string.green
                ),
                SessionChartSeries(
                    "Green-yellow",
                    uvirSpectralColor(ThresholdAlertMetric.YELLOW),
                    values { it.giallo },
                    R.string.yellow
                ),
                SessionChartSeries(
                    "Orange",
                    uvirSpectralColor(ThresholdAlertMetric.ORANGE),
                    values { it.arancione },
                    R.string.orange
                ),
                SessionChartSeries(
                    "Red",
                    uvirSpectralColor(ThresholdAlertMetric.RED),
                    values { it.rosso },
                    R.string.red
                )
            )

        SessionChartGroup.FAR_RED_NIR ->
            listOf(
                SessionChartSeries(
                    "Infrared",
                    uvirSpectralColor(ThresholdAlertMetric.NIR_TOTAL),
                    values { it.f8 + it.nir },
                    R.string.far_red_nir
                ),
                SessionChartSeries(
                    "Far-red",
                    uvirSpectralColor(ThresholdAlertMetric.FAR_RED),
                    values { it.f8 },
                    R.string.session_chart_series_far_red
                ),
                SessionChartSeries(
                    "NIR",
                    uvirSpectralColor(ThresholdAlertMetric.NIR),
                    values { it.nir }
                )
            )

        SessionChartGroup.BIOLOGICAL -> {
            val effects = records.map { biologicalEffects(it.sample) }
            listOf(
                SessionChartSeries(
                    "UV DNA effect/damage",
                    thresholdAlertMetricDisplayColor(
                        ThresholdAlertMetric.BIO_DNA_UV
                    ),
                    effects.map { it.dnaUvProxy },
                    R.string.dna_uv_proxy
                ),
                SessionChartSeries(
                    "UVA photoaging",
                    thresholdAlertMetricDisplayColor(
                        ThresholdAlertMetric.BIO_UVA_PHOTOAGING
                    ),
                    effects.map { it.uvaPhotoagingProxy },
                    R.string.uva_photoaging_proxy
                ),
                SessionChartSeries(
                    "HEV oxidative stress",
                    thresholdAlertMetricDisplayColor(
                        ThresholdAlertMetric.BIO_HEV_OXIDATIVE
                    ),
                    effects.map { it.hevOxidativeProxy },
                    R.string.hev_oxidative_proxy
                )
            )
        }
    }
    return series.mapIndexed { index, item ->
        val outOfRange = records.map { record ->
            when (group) {
                SessionChartGroup.UV -> record.sample.isOutOfRange(SensorGroup.UV)
                SessionChartGroup.VISIBLE -> record.sample.isOutOfRange(SensorGroup.VISIBLE)
                SessionChartGroup.FAR_RED_NIR -> record.sample.isOutOfRange(SensorGroup.NIR)
                SessionChartGroup.BIOLOGICAL ->
                    if (index < 2) {
                        record.sample.isOutOfRange(SensorGroup.UV)
                    } else {
                        record.sample.isOutOfRange(SensorGroup.VISIBLE)
                    }
            }
        }
        item.copy(outOfRange = outOfRange)
    }
}
