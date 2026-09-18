package me.mondiversi.uvir

import androidx.compose.ui.graphics.Color

internal enum class AcquisitionChartGroup(
    val titleResource: Int,
    val unitResource: Int,
    val exportTitle: String
) {
    IRRADIANCE(
        R.string.irradiance_view,
        R.string.acquisition_chart_unit,
        "Irradiance"
    ),
    BIOLOGICAL(
        R.string.biological_effects_group_name,
        R.string.acquisition_chart_unit_biological,
        "Estimated biological effects"
    )
}

internal data class AcquisitionChartBar(
    val shortLabel: String,
    val exportLabel: String,
    val displayLabelResource: Int?,
    val color: Color,
    val value: Double,
    val section: AcquisitionChartSection,
    val outOfRange: Boolean = false
)

internal enum class AcquisitionChartSection(
    val titleResource: Int,
    val exportTitle: String
) {
    UV(R.string.uv_radiation, "Ultraviolet"),
    VISIBLE(R.string.visible_light, "Visible light"),
    FAR_RED_NIR(R.string.far_red_nir, "Infrared"),
    BIOLOGICAL(R.string.biological_effects_group_name, "Biological effects")
}

internal enum class AcquisitionChartShareScope {
    CURRENT,
    ALL
}

internal fun acquisitionChartBars(
    sample: SensorSample,
    group: AcquisitionChartGroup = AcquisitionChartGroup.IRRADIANCE
): List<AcquisitionChartBar> =
    when (group) {
        AcquisitionChartGroup.IRRADIANCE -> {
            val uvOutOfRange = sample.isOutOfRange(SensorGroup.UV)
            val visibleOutOfRange = sample.isOutOfRange(SensorGroup.VISIBLE)
            val infraredOutOfRange = sample.isOutOfRange(SensorGroup.NIR)
            listOf(
                AcquisitionChartBar(
                    "UV",
                    "Ultraviolet",
                    R.string.uv_radiation,
                    Color(0xFF512DA8),
                    sample.uvc + sample.uvb + sample.uva,
                    AcquisitionChartSection.UV,
                    uvOutOfRange
                ),
                AcquisitionChartBar("UVC", "UVC", null, Color(0xFF9C27B0), sample.uvc, AcquisitionChartSection.UV, uvOutOfRange),
                AcquisitionChartBar("UVB", "UVB", null, Color(0xFF673AB7), sample.uvb, AcquisitionChartSection.UV, uvOutOfRange),
                AcquisitionChartBar("UVA", "UVA", null, Color(0xFF3F51B5), sample.uva, AcquisitionChartSection.UV, uvOutOfRange),
                AcquisitionChartBar(
                    "VIS",
                    "Visible light",
                    R.string.visible_light,
                    Color(0xFF00897B),
                    sample.violetto + sample.blu + sample.verde +
                        sample.giallo + sample.arancione + sample.rosso,
                    AcquisitionChartSection.VISIBLE,
                    visibleOutOfRange
                ),
                AcquisitionChartBar(
                    "HEV",
                    "HEV",
                    R.string.threshold_channel_hev,
                    Color(0xFF3949AB),
                    sample.violetto + sample.blu,
                    AcquisitionChartSection.VISIBLE,
                    visibleOutOfRange
                ),
                AcquisitionChartBar("V", "Violet", R.string.violet, Color(0xFF8E24AA), sample.violetto, AcquisitionChartSection.VISIBLE, visibleOutOfRange),
                AcquisitionChartBar("B", "Blue", R.string.blue, Color(0xFF1E88E5), sample.blu, AcquisitionChartSection.VISIBLE, visibleOutOfRange),
                AcquisitionChartBar("G", "Green", R.string.green, Color(0xFF43A047), sample.verde, AcquisitionChartSection.VISIBLE, visibleOutOfRange),
                AcquisitionChartBar("Y", "Yellow", R.string.yellow, Color(0xFFF9A825), sample.giallo, AcquisitionChartSection.VISIBLE, visibleOutOfRange),
                AcquisitionChartBar("O", "Orange", R.string.orange, Color(0xFFEF6C00), sample.arancione, AcquisitionChartSection.VISIBLE, visibleOutOfRange),
                AcquisitionChartBar("R", "Red", R.string.red, Color(0xFFE53935), sample.rosso, AcquisitionChartSection.VISIBLE, visibleOutOfRange),
                AcquisitionChartBar(
                    "IR",
                    "Infrared",
                    R.string.far_red_nir,
                    Color(0xFF6D4C41),
                    sample.f8 + sample.nir,
                    AcquisitionChartSection.FAR_RED_NIR,
                    infraredOutOfRange
                ),
                AcquisitionChartBar(
                    "FR",
                    "Far-red",
                    R.string.session_chart_series_far_red,
                    Color(0xFFD32F2F),
                    sample.f8,
                    AcquisitionChartSection.FAR_RED_NIR,
                    infraredOutOfRange
                ),
                AcquisitionChartBar("NIR", "NIR", null, Color(0xFF8D6E63), sample.nir, AcquisitionChartSection.FAR_RED_NIR, infraredOutOfRange),
            )
        }

        AcquisitionChartGroup.BIOLOGICAL -> {
            val effects = biologicalEffects(sample)
            listOf(
                AcquisitionChartBar(
                    "DNA",
                    "UV DNA effect/damage",
                    R.string.dna_uv_proxy,
                    thresholdAlertMetricDisplayColor(ThresholdAlertMetric.BIO_DNA_UV),
                    effects.dnaUvProxy,
                    AcquisitionChartSection.BIOLOGICAL,
                    sample.isOutOfRange(SensorGroup.UV)
                ),
                AcquisitionChartBar(
                    "UVA",
                    "UVA photoaging",
                    R.string.uva_photoaging_proxy,
                    thresholdAlertMetricDisplayColor(ThresholdAlertMetric.BIO_UVA_PHOTOAGING),
                    effects.uvaPhotoagingProxy,
                    AcquisitionChartSection.BIOLOGICAL,
                    sample.isOutOfRange(SensorGroup.UV)
                ),
                AcquisitionChartBar(
                    "HEV",
                    "HEV oxidative stress",
                    R.string.hev_oxidative_proxy,
                    thresholdAlertMetricDisplayColor(ThresholdAlertMetric.BIO_HEV_OXIDATIVE),
                    effects.hevOxidativeProxy,
                    AcquisitionChartSection.BIOLOGICAL,
                    sample.isOutOfRange(SensorGroup.VISIBLE)
                )
            )
        }
    }
