package me.mondiversi.uvir

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp

@Composable
fun SensorGroupContent(
    group: SensorGroup,
    expanded: Boolean,
    onToggle: () -> Unit,
    showChart: Boolean = false,
    liveHistory: List<LiveSamplePoint> = emptyList(),

    sample: SensorSample,

    uvTotal: Double,
    visibleTotal: Double,
    nirTotal: Double,

    hev: Double,

    alertedMetrics: Set<ThresholdAlertMetric> =
        emptySet(),
    configuredAlertMetrics: Set<ThresholdAlertMetric> =
        emptySet(),
    monitoringAlertMetrics: Set<ThresholdAlertMetric> =
        emptySet(),
    onConfigureAlert:
        ((ThresholdAlertMetric) -> Unit)? = null,

    cardColor: Color,
    primaryText: Color,
    secondaryText: Color,
    trackColor: Color
) {

    val liveChartSeries =
        when (group) {
            SensorGroup.UV ->
                listOf(
                    LiveChartSeries(
                        stringResource(R.string.uv_radiation),
                        Color(0xFF6A1B9A)
                    ) { it.uvc + it.uvb + it.uva },
                    LiveChartSeries("UVC", Color(0xFF9C27B0)) { it.uvc },
                    LiveChartSeries("UVB", Color(0xFF673AB7)) { it.uvb },
                    LiveChartSeries("UVA", Color(0xFF3F51B5)) { it.uva }
                )

            SensorGroup.VISIBLE ->
                listOf(
                    LiveChartSeries(
                        stringResource(R.string.visible_light),
                        Color(0xFF00897B)
                    ) {
                        it.violetto + it.blu + it.verde +
                            it.giallo + it.arancione + it.rosso
                    },
                    LiveChartSeries(
                        stringResource(R.string.threshold_channel_hev),
                        Color(0xFF3949AB)
                    ) { it.violetto + it.blu },
                    LiveChartSeries(
                        stringResource(R.string.violet),
                        Color(0xFF8E24AA)
                    ) { it.violetto },
                    LiveChartSeries(
                        stringResource(R.string.blue),
                        Color(0xFF1E88E5)
                    ) { it.blu },
                    LiveChartSeries(
                        stringResource(R.string.green),
                        Color(0xFF43A047)
                    ) { it.verde },
                    LiveChartSeries(
                        stringResource(R.string.yellow),
                        Color(0xFFFDD835)
                    ) { it.giallo },
                    LiveChartSeries(
                        stringResource(R.string.orange),
                        Color(0xFFFB8C00)
                    ) { it.arancione },
                    LiveChartSeries(
                        stringResource(R.string.red),
                        Color(0xFFE53935)
                    ) { it.rosso }
                )

            SensorGroup.NIR ->
                listOf(
                    LiveChartSeries(
                        stringResource(R.string.far_red_nir),
                        Color(0xFF6D4C41)
                    ) { it.f8 + it.nir },
                    LiveChartSeries(
                        stringResource(R.string.session_chart_series_far_red),
                        Color(0xFFD32F2F)
                    ) { it.f8 },
                    LiveChartSeries("NIR", Color(0xFF8D6E63)) { it.nir }
                )

            SensorGroup.BIOLOGICAL -> emptyList()
        }

    when (group) {

        SensorGroup.UV -> {

            SpectrumCard(
                group = group,
                title = stringResource(R.string.uv_radiation),
                total = uvTotal,
                unit = "µW/cm²",
                expanded = expanded,
                onToggle = onToggle,
                showChart = showChart,
                liveHistory = liveHistory,
                liveChartSeries = liveChartSeries,
                cardColor = cardColor,
                primaryText = primaryText,
                secondaryText = secondaryText,
                alerted =
                    ThresholdAlertMetric.UV_TOTAL in
                            alertedMetrics,
                alertConfigured =
                    configuredAlertMetrics.any {
                        it.sensorGroup() ==
                                SensorGroup.UV
                    },
                alertMonitoringActive =
                    monitoringAlertMetrics.any {
                        it.sensorGroup() == SensorGroup.UV
                    },
                onAlertClick =
                    onConfigureAlert?.let { configure ->
                        {
                            configure(
                                ThresholdAlertMetric.UV_TOTAL
                            )
                        }
                    }
            ) {

                SpectrumRow(
                    name = "UVC",
                    band = "100–280 nm",
                    value = sample.uvc,
                    percent =
                        percentage(
                            sample.uvc,
                            uvTotal
                        ),
                    primaryText = primaryText,
                    secondaryText = secondaryText,
                    trackColor = trackColor,
                    barColor = Color(0xFF9C27B0),
                    alerted =
                        ThresholdAlertMetric.UVC in
                                alertedMetrics,
                    alertConfigured =
                        ThresholdAlertMetric.UVC in
                                configuredAlertMetrics,
                    onAlertClick =
                        onConfigureAlert?.let { configure ->
                            {
                                configure(
                                    ThresholdAlertMetric.UVC
                                )
                            }
                        }
                )

                SpectrumRow(
                    name = "UVB",
                    band = "280–315 nm",
                    value = sample.uvb,
                    percent =
                        percentage(
                            sample.uvb,
                            uvTotal
                        ),
                    primaryText = primaryText,
                    secondaryText = secondaryText,
                    trackColor = trackColor,
                    barColor = Color(0xFF673AB7),
                    alerted =
                        ThresholdAlertMetric.UVB in
                                alertedMetrics,
                    alertConfigured =
                        ThresholdAlertMetric.UVB in
                                configuredAlertMetrics,
                    onAlertClick =
                        onConfigureAlert?.let { configure ->
                            {
                                configure(
                                    ThresholdAlertMetric.UVB
                                )
                            }
                        }
                )

                SpectrumRow(
                    name = "UVA",
                    band = "315–400 nm",
                    value = sample.uva,
                    percent =
                        percentage(
                            sample.uva,
                            uvTotal
                        ),
                    primaryText = primaryText,
                    secondaryText = secondaryText,
                    trackColor = trackColor,
                    barColor = Color(0xFF3F51B5),
                    alerted =
                        ThresholdAlertMetric.UVA in
                                alertedMetrics,
                    alertConfigured =
                        ThresholdAlertMetric.UVA in
                                configuredAlertMetrics,
                    onAlertClick =
                        onConfigureAlert?.let { configure ->
                            {
                                configure(
                                    ThresholdAlertMetric.UVA
                                )
                            }
                        }
                )

            }
        }

        SensorGroup.VISIBLE -> {

            SpectrumCard(
                group = group,
                title = stringResource(R.string.visible_light),
                total = visibleTotal,
                unit = "µW/cm²",
                expanded = expanded,
                onToggle = onToggle,
                showChart = showChart,
                liveHistory = liveHistory,
                liveChartSeries = liveChartSeries,
                cardColor = cardColor,
                primaryText = primaryText,
                secondaryText = secondaryText,
                alerted =
                    ThresholdAlertMetric.VISIBLE_TOTAL in
                            alertedMetrics,
                alertConfigured =
                    configuredAlertMetrics.any {
                        it.sensorGroup() ==
                                SensorGroup.VISIBLE
                    },
                alertMonitoringActive =
                    monitoringAlertMetrics.any {
                        it.sensorGroup() == SensorGroup.VISIBLE
                    },
                onAlertClick =
                    onConfigureAlert?.let { configure ->
                        {
                            configure(
                                ThresholdAlertMetric.VISIBLE_TOTAL
                            )
                        }
                    }
            ) {

                SpectrumRow(
                    name = "HEV¹",
                    band = "400–500 nm",
                    value = hev,
                    percent = percentage(hev, visibleTotal),
                    primaryText = primaryText,
                    secondaryText = secondaryText,
                    trackColor = trackColor,
                    barColor = Color(0xFF3949AB),
                    alerted = ThresholdAlertMetric.HEV in alertedMetrics,
                    alertConfigured =
                        ThresholdAlertMetric.HEV in configuredAlertMetrics,
                    onAlertClick =
                        onConfigureAlert?.let { configure ->
                            { configure(ThresholdAlertMetric.HEV) }
                        }
                )

                SpectrumRow(
                    stringResource(R.string.violet),
                    "400–450 nm",
                    sample.violetto,
                    percentage(
                        sample.violetto,
                        visibleTotal
                    ),
                    primaryText,
                    secondaryText,
                    trackColor,
                    Color(0xFF8E24AA),
                    alerted =
                        ThresholdAlertMetric.VIOLET in
                                alertedMetrics,
                    alertConfigured =
                        ThresholdAlertMetric.VIOLET in
                                configuredAlertMetrics,
                    onAlertClick =
                        onConfigureAlert?.let { configure ->
                            {
                                configure(
                                    ThresholdAlertMetric.VIOLET
                                )
                            }
                        }
                )

                SpectrumRow(
                    stringResource(R.string.blue),
                    "450–495 nm",
                    sample.blu,
                    percentage(
                        sample.blu,
                        visibleTotal
                    ),
                    primaryText,
                    secondaryText,
                    trackColor,
                    Color(0xFF1E88E5),
                    alerted =
                        ThresholdAlertMetric.BLUE in
                                alertedMetrics,
                    alertConfigured =
                        ThresholdAlertMetric.BLUE in
                                configuredAlertMetrics,
                    onAlertClick =
                        onConfigureAlert?.let { configure ->
                            {
                                configure(
                                    ThresholdAlertMetric.BLUE
                                )
                            }
                        }
                )

                SpectrumRow(
                    stringResource(R.string.green),
                    "495–570 nm",
                    sample.verde,
                    percentage(
                        sample.verde,
                        visibleTotal
                    ),
                    primaryText,
                    secondaryText,
                    trackColor,
                    Color(0xFF43A047),
                    alerted =
                        ThresholdAlertMetric.GREEN in
                                alertedMetrics,
                    alertConfigured =
                        ThresholdAlertMetric.GREEN in
                                configuredAlertMetrics,
                    onAlertClick =
                        onConfigureAlert?.let { configure ->
                            {
                                configure(
                                    ThresholdAlertMetric.GREEN
                                )
                            }
                        }
                )

                SpectrumRow(
                    stringResource(R.string.yellow),
                    "570–590 nm",
                    sample.giallo,
                    percentage(
                        sample.giallo,
                        visibleTotal
                    ),
                    primaryText,
                    secondaryText,
                    trackColor,
                    Color(0xFFFDD835),
                    alerted =
                        ThresholdAlertMetric.YELLOW in
                                alertedMetrics,
                    alertConfigured =
                        ThresholdAlertMetric.YELLOW in
                                configuredAlertMetrics,
                    onAlertClick =
                        onConfigureAlert?.let { configure ->
                            {
                                configure(
                                    ThresholdAlertMetric.YELLOW
                                )
                            }
                        }
                )

                SpectrumRow(
                    stringResource(R.string.orange),
                    "590–620 nm",
                    sample.arancione,
                    percentage(
                        sample.arancione,
                        visibleTotal
                    ),
                    primaryText,
                    secondaryText,
                    trackColor,
                    Color(0xFFFB8C00),
                    alerted =
                        ThresholdAlertMetric.ORANGE in
                                alertedMetrics,
                    alertConfigured =
                        ThresholdAlertMetric.ORANGE in
                                configuredAlertMetrics,
                    onAlertClick =
                        onConfigureAlert?.let { configure ->
                            {
                                configure(
                                    ThresholdAlertMetric.ORANGE
                                )
                            }
                        }
                )

                SpectrumRow(
                    stringResource(R.string.red),
                    "620–700 nm",
                    sample.rosso,
                    percentage(
                        sample.rosso,
                        visibleTotal
                    ),
                    primaryText,
                    secondaryText,
                    trackColor,
                    Color(0xFFE53935),
                    alerted =
                        ThresholdAlertMetric.RED in
                                alertedMetrics,
                    alertConfigured =
                        ThresholdAlertMetric.RED in
                                configuredAlertMetrics,
                    onAlertClick =
                        onConfigureAlert?.let { configure ->
                            {
                                configure(
                                    ThresholdAlertMetric.RED
                                )
                            }
                        }
                )

                Text(
                    text = stringResource(R.string.hev_visible_footnote),
                    modifier = Modifier.fillMaxWidth(),
                    color = secondaryText,
                    fontSize = 10.sp,
                    lineHeight = 13.sp
                )
            }
        }

        SensorGroup.NIR -> {

            SpectrumCard(
                group = group,
                title = stringResource(R.string.far_red_nir),
                footnote = stringResource(R.string.infrared_sensor_footnote),
                total = nirTotal,
                unit = "µW/cm²",
                expanded = expanded,
                onToggle = onToggle,
                showChart = showChart,
                liveHistory = liveHistory,
                liveChartSeries = liveChartSeries,
                cardColor = cardColor,
                primaryText = primaryText,
                secondaryText = secondaryText,
                alerted =
                    ThresholdAlertMetric.NIR_TOTAL in
                            alertedMetrics,
                alertConfigured =
                    configuredAlertMetrics.any {
                        it.sensorGroup() ==
                                SensorGroup.NIR
                    },
                alertMonitoringActive =
                    monitoringAlertMetrics.any {
                        it.sensorGroup() == SensorGroup.NIR
                    },
                onAlertClick =
                    onConfigureAlert?.let { configure ->
                        {
                            configure(
                                ThresholdAlertMetric.NIR_TOTAL
                            )
                        }
                    }
            ) {

                SpectrumRow(
                    name = "Far-red",
                    band = stringResource(R.string.infrared_peak_band, 745),
                    value = sample.f8,
                    percent =
                        percentage(
                            sample.f8,
                            nirTotal
                        ),
                    primaryText = primaryText,
                    secondaryText = secondaryText,
                    trackColor = trackColor,
                    barColor = Color(0xFFD32F2F),
                    alerted =
                        ThresholdAlertMetric.FAR_RED in
                                alertedMetrics,
                    alertConfigured =
                        ThresholdAlertMetric.FAR_RED in
                                configuredAlertMetrics,
                    onAlertClick =
                        onConfigureAlert?.let { configure ->
                            {
                                configure(
                                    ThresholdAlertMetric.FAR_RED
                                )
                            }
                        }
                )

                SpectrumRow(
                    name = "NIR",
                    band = stringResource(R.string.infrared_peak_band, 855),
                    value = sample.nir,
                    percent =
                        percentage(
                            sample.nir,
                            nirTotal
                        ),
                    primaryText = primaryText,
                    secondaryText = secondaryText,
                    trackColor = trackColor,
                    barColor = Color(0xFF8D6E63),
                    alerted =
                        ThresholdAlertMetric.NIR in
                                alertedMetrics,
                    alertConfigured =
                        ThresholdAlertMetric.NIR in
                                configuredAlertMetrics,
                    onAlertClick =
                        onConfigureAlert?.let { configure ->
                            {
                                configure(
                                    ThresholdAlertMetric.NIR
                                )
                            }
                        }
                )
            }
        }

        SensorGroup.BIOLOGICAL -> Unit
    }
}
