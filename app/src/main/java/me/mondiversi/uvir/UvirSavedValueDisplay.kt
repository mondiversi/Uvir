package me.mondiversi.uvir

/** Presentation state only. Never pass it to storage, acquisition, alert rules or exports. */
internal data class UvirSavedValueDisplay(
    val spectrum: UvirLiveSpectrumDisplay = UvirLiveSpectrumDisplay(),
    val excludedValues: Set<ThresholdAlertMetric> = emptySet()
) {
    fun acquisitionVisible(metric: ThresholdAlertMetric): Boolean =
        if (metric.isBiologicalEffect()) metric !in excludedValues else spectrum.visible(metric)

    fun toggleAcquisition(metric: ThresholdAlertMetric): UvirSavedValueDisplay =
        if (metric.isBiologicalEffect()) copy(
            excludedValues = if (metric in excludedValues) excludedValues - metric else excludedValues + metric
        ) else copy(spectrum = spectrum.toggle(metric))

    fun acquisitionValue(metric: ThresholdAlertMetric, sample: SensorSample): Double =
        if (metric.isBiologicalEffect()) sample.thresholdMetricValue(metric) else spectrum.value(metric, sample)

    fun acquisitionOutOfRange(metric: ThresholdAlertMetric, sample: SensorSample): Boolean =
        acquisitionVisible(metric) && if (metric.isBiologicalEffect()) sample.isOutOfRange(
            if (metric == ThresholdAlertMetric.BIO_HEV_OXIDATIVE) SensorGroup.VISIBLE else SensorGroup.UV
        ) else spectrum.outOfRange(metric.alertSessionGroup().spectrumIconGroup(), sample)

    fun share(group: SessionChartGroup, totalRow: Boolean, value: Double, sample: SensorSample,
        digits: Int, unit: UvirIrradianceUnit): Float? =
        if (totalRow) spectrum.groupShare(group.spectrumIconGroup(), sample, digits, unit)
        else spectrum.share(group.spectrumIconGroup(), value, sample, digits, unit)
}

/** Kept in the same order as the existing table and chart series, including group totals. */
internal fun savedAcquisitionMetrics(group: SessionChartGroup): List<ThresholdAlertMetric> = when (group) {
    SessionChartGroup.UV -> listOf(ThresholdAlertMetric.UV_TOTAL) + UvirLiveSpectrumDisplay.channels(SensorGroup.UV)
    SessionChartGroup.VISIBLE -> listOf(ThresholdAlertMetric.VISIBLE_TOTAL, ThresholdAlertMetric.HEV) +
        UvirLiveSpectrumDisplay.channels(SensorGroup.VISIBLE)
    SessionChartGroup.FAR_RED_NIR -> listOf(ThresholdAlertMetric.NIR_TOTAL) + UvirLiveSpectrumDisplay.channels(SensorGroup.NIR)
    SessionChartGroup.BIOLOGICAL -> listOf(ThresholdAlertMetric.BIO_DNA_UV,
        ThresholdAlertMetric.BIO_UVA_PHOTOAGING, ThresholdAlertMetric.BIO_HEV_OXIDATIVE)
}

private val savedAcquisitionTotals = setOf(ThresholdAlertMetric.UV_TOTAL, ThresholdAlertMetric.VISIBLE_TOTAL, ThresholdAlertMetric.NIR_TOTAL)

internal fun savedAcquisitionChartBars(sample: SensorSample, group: AcquisitionChartGroup,
    display: UvirSavedValueDisplay): List<AcquisitionChartBar> {
    val metrics = if (group == AcquisitionChartGroup.BIOLOGICAL) savedAcquisitionMetrics(SessionChartGroup.BIOLOGICAL)
        else listOf(SessionChartGroup.UV, SessionChartGroup.VISIBLE, SessionChartGroup.FAR_RED_NIR).flatMap(::savedAcquisitionMetrics)
    return acquisitionChartBars(sample, group).mapIndexedNotNull { index, bar ->
        val metric = metrics[index]
        if (metric !in savedAcquisitionTotals && !display.acquisitionVisible(metric)) null
        else if (metric.isBiologicalEffect()) bar else bar.copy(
            value = display.acquisitionValue(metric, sample), outOfRange = display.acquisitionOutOfRange(metric, sample))
    }
}

internal fun savedSessionChartSeries(records: List<SavedRecordDetail>, group: SessionChartGroup,
    display: UvirSavedValueDisplay): List<SessionChartSeries> {
    val metrics = savedAcquisitionMetrics(group)
    return sessionChartSeries(records, group).mapIndexedNotNull { index, series ->
        val metric = metrics[index]
        if (metric !in savedAcquisitionTotals && !display.acquisitionVisible(metric)) null
        else if (group.biological) series else series.copy(
            values = records.map { display.acquisitionValue(metric, it.sample) },
            outOfRange = records.map { display.acquisitionOutOfRange(metric, it.sample) })
    }
}
