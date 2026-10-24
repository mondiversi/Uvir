package me.mondiversi.uvir

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp

@Composable
internal fun SensorGroupContent(
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
    trackColor: Color,
    display: UvirLiveSpectrumDisplay = UvirLiveSpectrumDisplay(),
    onDisplayChange: (UvirLiveSpectrumDisplay) -> Unit = {},
    reorderControls: (@Composable (String) -> Unit)? = null,
    collapsible: Boolean = true
) {
    val displayedDigits = currentUvirLiveIrradianceDecimals()
    val displayedUnit = LocalUvirIrradianceUnit.current
    fun displayShare(value: Double) = display.share(group, value, sample, displayedDigits, displayedUnit)
    val groupShare = display.groupShare(group, sample, displayedDigits, displayedUnit)
    val groupTotal = if (display.excludedChannels.isEmpty()) when (group) {
        SensorGroup.UV -> uvTotal
        SensorGroup.VISIBLE -> visibleTotal
        SensorGroup.NIR -> nirTotal
        SensorGroup.BIOLOGICAL -> 0.0
    } else display.total(group, sample)
    val displayedHev = if (display.excludedChannels.isEmpty()) hev else display.value(ThresholdAlertMetric.HEV, sample)
    val percentageLabel = stringResource(
        if (group in display.groupPercentages) R.string.live_percent_group else R.string.live_percent_total
    )

    val allLiveChartSeries =
        when (group) {
            SensorGroup.UV ->
                listOf(
                    LiveChartSeries(
                        stringResource(R.string.uv_radiation),
                        uvirSpectralColor(ThresholdAlertMetric.UV_TOTAL)
                    ) { it.uvc + it.uvb + it.uva },
                    LiveChartSeries("UVC", uvirSpectralColor(ThresholdAlertMetric.UVC)) { it.uvc },
                    LiveChartSeries("UVB", uvirSpectralColor(ThresholdAlertMetric.UVB)) { it.uvb },
                    LiveChartSeries("UVA", uvirSpectralColor(ThresholdAlertMetric.UVA)) { it.uva }
                )

            SensorGroup.VISIBLE ->
                listOf(
                    LiveChartSeries(
                        stringResource(R.string.visible_light),
                        uvirSpectralColor(ThresholdAlertMetric.VISIBLE_TOTAL)
                    ) {
                        it.violetto + it.blu + it.verde +
                            it.giallo + it.arancione + it.rosso
                    },
                    LiveChartSeries(
                        stringResource(R.string.threshold_channel_hev),
                        uvirSpectralColor(ThresholdAlertMetric.HEV)
                    ) { it.violetto + it.blu },
                    LiveChartSeries(
                        stringResource(R.string.violet),
                        uvirSpectralColor(ThresholdAlertMetric.VIOLET)
                    ) { it.violetto },
                    LiveChartSeries(
                        stringResource(R.string.blue),
                        uvirSpectralColor(ThresholdAlertMetric.BLUE)
                    ) { it.blu },
                    LiveChartSeries(
                        stringResource(R.string.green),
                        uvirSpectralColor(ThresholdAlertMetric.GREEN)
                    ) { it.verde },
                    LiveChartSeries(
                        stringResource(R.string.yellow),
                        uvirSpectralColor(ThresholdAlertMetric.YELLOW)
                    ) { it.giallo },
                    LiveChartSeries(
                        stringResource(R.string.orange),
                        uvirSpectralColor(ThresholdAlertMetric.ORANGE)
                    ) { it.arancione },
                    LiveChartSeries(
                        stringResource(R.string.red),
                        uvirSpectralColor(ThresholdAlertMetric.RED)
                    ) { it.rosso }
                )

            SensorGroup.NIR ->
                listOf(
                    LiveChartSeries(
                        stringResource(R.string.far_red_nir),
                        uvirSpectralColor(ThresholdAlertMetric.NIR_TOTAL)
                    ) { it.f8 + it.nir },
                    LiveChartSeries(
                        stringResource(R.string.session_chart_series_far_red),
                        uvirSpectralColor(ThresholdAlertMetric.FAR_RED)
                    ) { it.f8 },
                    LiveChartSeries("NIR", uvirSpectralColor(ThresholdAlertMetric.NIR)) { it.nir }
                )

            SensorGroup.BIOLOGICAL -> emptyList()
        }

    // Filter/select only the plotted values; do not mutate the rolling raw history.
    val chartMetrics = when (group) {
        SensorGroup.UV -> listOf(ThresholdAlertMetric.UV_TOTAL) + UvirLiveSpectrumDisplay.channels(group)
        SensorGroup.VISIBLE -> listOf(ThresholdAlertMetric.VISIBLE_TOTAL, ThresholdAlertMetric.HEV) + UvirLiveSpectrumDisplay.channels(group)
        SensorGroup.NIR -> listOf(ThresholdAlertMetric.NIR_TOTAL) + UvirLiveSpectrumDisplay.channels(group)
        SensorGroup.BIOLOGICAL -> emptyList()
    }
    val liveChartSeries = allLiveChartSeries.mapIndexedNotNull { index, series ->
        val metric = chartMetrics[index]
        if (index > 0 && !display.visible(metric)) null
        else LiveChartSeries(series.label, series.color) { display.value(metric, it) }
    }

    when (group) {

        SensorGroup.UV -> {

            SpectrumCard(
                group = group,
                title = stringResource(R.string.uv_radiation),
                total = groupTotal,
                bandLabel = percentageLabel,
                onPercentageBaseToggle = { onDisplayChange(display.togglePercentageBase(group)) },
                relativePercent = groupShare,
                trackColor = trackColor,
                barColor = uvirSpectralColor(ThresholdAlertMetric.UV_TOTAL),
                outOfRange = display.outOfRange(group, sample),
                chartOutOfRange = { display.outOfRange(group, it) },
                expanded = expanded,
                onToggle = onToggle,
                reorderControls = reorderControls,
                collapsible = collapsible,
                groupVisible = display.groupVisible(group),
                onGroupVisibilityToggle = { onDisplayChange(display.toggleGroup(group)) },
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
                    },
            ) {

                SpectrumRow(
                    name = "UVC",
                    band = UvirSensorBands.UVC,
                    value = sample.uvc,
                    percent =
                        displayShare(
                            sample.uvc
                        ),
                    primaryText = primaryText,
                    secondaryText = secondaryText,
                    trackColor = trackColor,
                    barColor = uvirSpectralColor(ThresholdAlertMetric.UVC),
                    metric = ThresholdAlertMetric.UVC,
                    visible = display.visible(ThresholdAlertMetric.UVC),
                    onVisibilityToggle = { onDisplayChange(display.toggle(ThresholdAlertMetric.UVC)) },
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
                    band = UvirSensorBands.UVB,
                    value = sample.uvb,
                    percent =
                        displayShare(
                            sample.uvb
                        ),
                    primaryText = primaryText,
                    secondaryText = secondaryText,
                    trackColor = trackColor,
                    barColor = uvirSpectralColor(ThresholdAlertMetric.UVB),
                    metric = ThresholdAlertMetric.UVB,
                    visible = display.visible(ThresholdAlertMetric.UVB),
                    onVisibilityToggle = { onDisplayChange(display.toggle(ThresholdAlertMetric.UVB)) },
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
                    band = UvirSensorBands.UVA,
                    value = sample.uva,
                    percent =
                        displayShare(
                            sample.uva
                        ),
                    primaryText = primaryText,
                    secondaryText = secondaryText,
                    trackColor = trackColor,
                    barColor = uvirSpectralColor(ThresholdAlertMetric.UVA),
                    metric = ThresholdAlertMetric.UVA,
                    visible = display.visible(ThresholdAlertMetric.UVA),
                    onVisibilityToggle = { onDisplayChange(display.toggle(ThresholdAlertMetric.UVA)) },
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
                total = groupTotal,
                bandLabel = percentageLabel,
                onPercentageBaseToggle = { onDisplayChange(display.togglePercentageBase(group)) },
                relativePercent = groupShare,
                trackColor = trackColor,
                barColor = uvirSpectralColor(ThresholdAlertMetric.VISIBLE_TOTAL),
                outOfRange = display.outOfRange(group, sample),
                chartOutOfRange = { display.outOfRange(group, it) },
                expanded = expanded,
                onToggle = onToggle,
                reorderControls = reorderControls,
                collapsible = collapsible,
                groupVisible = display.groupVisible(group),
                onGroupVisibilityToggle = { onDisplayChange(display.toggleGroup(group)) },
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
                    },
            ) {

                SpectrumRow(
                    name = "HEV¹",
                    band = "",
                    value = displayedHev,
                    percent = displayShare(displayedHev),
                    primaryText = primaryText,
                    secondaryText = secondaryText,
                    trackColor = trackColor,
                    barColor = uvirSpectralColor(ThresholdAlertMetric.HEV),
                    metric = ThresholdAlertMetric.HEV,
                    visible = display.visible(ThresholdAlertMetric.HEV),
                    onVisibilityToggle = { onDisplayChange(display.toggle(ThresholdAlertMetric.HEV)) },
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
                    UvirSensorBands.VIOLET_CHANNELS,
                    sample.violetto,
                    displayShare(
                        sample.violetto
                    ),
                    primaryText,
                    secondaryText,
                    trackColor,
                    uvirSpectralColor(ThresholdAlertMetric.VIOLET),
                    metric = ThresholdAlertMetric.VIOLET,
                    visible = display.visible(ThresholdAlertMetric.VIOLET),
                    onVisibilityToggle = { onDisplayChange(display.toggle(ThresholdAlertMetric.VIOLET)) },
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
                    UvirSensorBands.BLUE_CHANNELS,
                    sample.blu,
                    displayShare(
                        sample.blu
                    ),
                    primaryText,
                    secondaryText,
                    trackColor,
                    uvirSpectralColor(ThresholdAlertMetric.BLUE),
                    metric = ThresholdAlertMetric.BLUE,
                    visible = display.visible(ThresholdAlertMetric.BLUE),
                    onVisibilityToggle = { onDisplayChange(display.toggle(ThresholdAlertMetric.BLUE)) },
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
                    UvirSensorBands.GREEN_CHANNEL,
                    sample.verde,
                    displayShare(
                        sample.verde
                    ),
                    primaryText,
                    secondaryText,
                    trackColor,
                    uvirSpectralColor(ThresholdAlertMetric.GREEN),
                    metric = ThresholdAlertMetric.GREEN,
                    visible = display.visible(ThresholdAlertMetric.GREEN),
                    onVisibilityToggle = { onDisplayChange(display.toggle(ThresholdAlertMetric.GREEN)) },
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
                    UvirSensorBands.GREEN_YELLOW_CHANNELS,
                    sample.giallo,
                    displayShare(
                        sample.giallo
                    ),
                    primaryText,
                    secondaryText,
                    trackColor,
                    uvirSpectralColor(ThresholdAlertMetric.YELLOW),
                    metric = ThresholdAlertMetric.YELLOW,
                    visible = display.visible(ThresholdAlertMetric.YELLOW),
                    onVisibilityToggle = { onDisplayChange(display.toggle(ThresholdAlertMetric.YELLOW)) },
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
                    UvirSensorBands.ORANGE_CHANNEL,
                    sample.arancione,
                    displayShare(
                        sample.arancione
                    ),
                    primaryText,
                    secondaryText,
                    trackColor,
                    uvirSpectralColor(ThresholdAlertMetric.ORANGE),
                    metric = ThresholdAlertMetric.ORANGE,
                    visible = display.visible(ThresholdAlertMetric.ORANGE),
                    onVisibilityToggle = { onDisplayChange(display.toggle(ThresholdAlertMetric.ORANGE)) },
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
                    UvirSensorBands.RED_CHANNEL,
                    sample.rosso,
                    displayShare(
                        sample.rosso
                    ),
                    primaryText,
                    secondaryText,
                    trackColor,
                    uvirSpectralColor(ThresholdAlertMetric.RED),
                    metric = ThresholdAlertMetric.RED,
                    visible = display.visible(ThresholdAlertMetric.RED),
                    onVisibilityToggle = { onDisplayChange(display.toggle(ThresholdAlertMetric.RED)) },
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
                    modifier = Modifier.fillMaxWidth().padding(start = UvirIslandContentPadding,
                        end = UvirIslandContentPadding, bottom = UvirIslandContentPadding),
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
                total = groupTotal,
                bandLabel = percentageLabel,
                onPercentageBaseToggle = { onDisplayChange(display.togglePercentageBase(group)) },
                relativePercent = groupShare,
                trackColor = trackColor,
                barColor = uvirSpectralColor(ThresholdAlertMetric.NIR_TOTAL),
                outOfRange = display.outOfRange(group, sample),
                chartOutOfRange = { display.outOfRange(group, it) },
                expanded = expanded,
                onToggle = onToggle,
                reorderControls = reorderControls,
                collapsible = collapsible,
                groupVisible = display.groupVisible(group),
                onGroupVisibilityToggle = { onDisplayChange(display.toggleGroup(group)) },
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
                    },
            ) {

                SpectrumRow(
                    name = "Far-red",
                    band = UvirSensorBands.FAR_RED_CHANNELS,
                    value = sample.f8,
                    percent =
                        displayShare(
                            sample.f8
                        ),
                    primaryText = primaryText,
                    secondaryText = secondaryText,
                    trackColor = trackColor,
                    barColor = uvirSpectralColor(ThresholdAlertMetric.FAR_RED),
                    metric = ThresholdAlertMetric.FAR_RED,
                    visible = display.visible(ThresholdAlertMetric.FAR_RED),
                    onVisibilityToggle = { onDisplayChange(display.toggle(ThresholdAlertMetric.FAR_RED)) },
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
                    band = UvirSensorBands.NIR_CHANNEL,
                    value = sample.nir,
                    percent =
                        displayShare(
                            sample.nir
                        ),
                    primaryText = primaryText,
                    secondaryText = secondaryText,
                    trackColor = trackColor,
                    barColor = uvirSpectralColor(ThresholdAlertMetric.NIR),
                    metric = ThresholdAlertMetric.NIR,
                    visible = display.visible(ThresholdAlertMetric.NIR),
                    onVisibilityToggle = { onDisplayChange(display.toggle(ThresholdAlertMetric.NIR)) },
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

internal fun uvirIrradianceGroupShare(
    groupTotal: Double,
    uvTotal: Double,
    visibleTotal: Double,
    infraredTotal: Double
): Float = percentage(groupTotal, uvTotal + visibleTotal + infraredTotal)
