package me.mondiversi.uvir

import androidx.compose.ui.graphics.Color

internal enum class AcquisitionChartGroup(
    val titleResource: Int,
    val unitResource: Int,
    val exportTitle: String,
    val exportUnit: String
) {
    IRRADIANCE(
        R.string.irradiance_view,
        R.string.acquisition_chart_unit,
        "Irradiance",
        "µW/cm²"
    ),
    BIOLOGICAL(
        R.string.biological_effects_group_name,
        R.string.acquisition_chart_unit_biological,
        "Estimated biological effects",
        "µW/cm² equiv."
    )
}

internal data class AcquisitionChartBar(
    val shortLabel: String,
    val exportLabel: String,
    val displayLabelResource: Int?,
    val color: Color,
    val value: Double,
    val section: AcquisitionChartSection
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
        AcquisitionChartGroup.IRRADIANCE ->
            listOf(
                AcquisitionChartBar(
                    "UV",
                    "Ultraviolet",
                    R.string.uv_radiation,
                    Color(0xFF512DA8),
                    sample.uvc + sample.uvb + sample.uva,
                    AcquisitionChartSection.UV
                ),
                AcquisitionChartBar("UVC", "UVC", null, Color(0xFF9C27B0), sample.uvc, AcquisitionChartSection.UV),
                AcquisitionChartBar("UVB", "UVB", null, Color(0xFF673AB7), sample.uvb, AcquisitionChartSection.UV),
                AcquisitionChartBar("UVA", "UVA", null, Color(0xFF3F51B5), sample.uva, AcquisitionChartSection.UV),
                AcquisitionChartBar(
                    "VIS",
                    "Visible light",
                    R.string.visible_light,
                    Color(0xFF00897B),
                    sample.violetto + sample.blu + sample.verde +
                        sample.giallo + sample.arancione + sample.rosso,
                    AcquisitionChartSection.VISIBLE
                ),
                AcquisitionChartBar(
                    "HEV",
                    "HEV",
                    R.string.threshold_channel_hev,
                    Color(0xFF3949AB),
                    sample.violetto + sample.blu,
                    AcquisitionChartSection.VISIBLE
                ),
                AcquisitionChartBar("V", "Violet", R.string.violet, Color(0xFF8E24AA), sample.violetto, AcquisitionChartSection.VISIBLE),
                AcquisitionChartBar("B", "Blue", R.string.blue, Color(0xFF1E88E5), sample.blu, AcquisitionChartSection.VISIBLE),
                AcquisitionChartBar("G", "Green", R.string.green, Color(0xFF43A047), sample.verde, AcquisitionChartSection.VISIBLE),
                AcquisitionChartBar("Y", "Yellow", R.string.yellow, Color(0xFFF9A825), sample.giallo, AcquisitionChartSection.VISIBLE),
                AcquisitionChartBar("O", "Orange", R.string.orange, Color(0xFFEF6C00), sample.arancione, AcquisitionChartSection.VISIBLE),
                AcquisitionChartBar("R", "Red", R.string.red, Color(0xFFE53935), sample.rosso, AcquisitionChartSection.VISIBLE),
                AcquisitionChartBar(
                    "IR",
                    "Infrared",
                    R.string.far_red_nir,
                    Color(0xFF6D4C41),
                    sample.f8 + sample.nir,
                    AcquisitionChartSection.FAR_RED_NIR
                ),
                AcquisitionChartBar(
                    "FR",
                    "Far-red",
                    R.string.session_chart_series_far_red,
                    Color(0xFFD32F2F),
                    sample.f8,
                    AcquisitionChartSection.FAR_RED_NIR
                ),
                AcquisitionChartBar("NIR", "NIR", null, Color(0xFF8D6E63), sample.nir, AcquisitionChartSection.FAR_RED_NIR),
            )

        AcquisitionChartGroup.BIOLOGICAL -> {
            val effects = biologicalEffects(sample)
            listOf(
                AcquisitionChartBar(
                    "DNA",
                    "UV DNA effect/damage",
                    R.string.dna_uv_proxy,
                    thresholdAlertMetricDisplayColor(ThresholdAlertMetric.BIO_DNA_UV),
                    effects.dnaUvProxy,
                    AcquisitionChartSection.BIOLOGICAL
                ),
                AcquisitionChartBar(
                    "UVA",
                    "UVA photoaging",
                    R.string.uva_photoaging_proxy,
                    thresholdAlertMetricDisplayColor(ThresholdAlertMetric.BIO_UVA_PHOTOAGING),
                    effects.uvaPhotoagingProxy,
                    AcquisitionChartSection.BIOLOGICAL
                ),
                AcquisitionChartBar(
                    "HEV",
                    "HEV oxidative stress",
                    R.string.hev_oxidative_proxy,
                    thresholdAlertMetricDisplayColor(ThresholdAlertMetric.BIO_HEV_OXIDATIVE),
                    effects.hevOxidativeProxy,
                    AcquisitionChartSection.BIOLOGICAL
                )
            )
        }
    }
