package me.mondiversi.uvir

/** Persist only actual measured channels, never overlapping aggregates or biological proxies. */
internal fun decodeUvirMainHiddenChannels(names: Set<String>): Set<ThresholdAlertMetric> {
    val channels = UvirLiveSpectrumDisplay.irradianceGroups.flatMap { UvirLiveSpectrumDisplay.channels(it) }.toSet()
    return names.mapNotNull { runCatching { ThresholdAlertMetric.valueOf(it) }.getOrNull() }
        .filter { it in channels }.toSet()
}

/** UI-only inclusion and percentage bases. Never pass these values to recording or alerts. */
internal data class UvirLiveSpectrumDisplay(
    val excludedChannels: Set<ThresholdAlertMetric> = emptySet(),
    val groupPercentages: Set<SensorGroup> = emptySet()
) {
    fun visible(metric: ThresholdAlertMetric): Boolean =
        components(metric).any { it !in excludedChannels }

    fun toggle(metric: ThresholdAlertMetric): UvirLiveSpectrumDisplay {
        val channels = components(metric).toSet()
        return copy(excludedChannels = if (visible(metric)) excludedChannels + channels else excludedChannels - channels)
    }

    fun groupVisible(group: SensorGroup): Boolean = channels(group).any { it !in excludedChannels }

    /** Same rule as overlapping aggregates: hide any visible children, otherwise restore them all. */
    fun toggleGroup(group: SensorGroup): UvirLiveSpectrumDisplay {
        val children = channels(group).toSet()
        return copy(excludedChannels = if (groupVisible(group)) excludedChannels + children else excludedChannels - children)
    }

    fun togglePercentageBase(group: SensorGroup): UvirLiveSpectrumDisplay = copy(
        groupPercentages = if (group in groupPercentages) groupPercentages - group else groupPercentages + group
    )

    fun value(metric: ThresholdAlertMetric, sample: SensorSample): Double =
        components(metric).filter { it !in excludedChannels }.sumOf { sample.thresholdMetricValue(it) }

    fun total(group: SensorGroup, sample: SensorSample): Double =
        channels(group).filter { it !in excludedChannels }.sumOf { sample.thresholdMetricValue(it) }

    fun outOfRange(group: SensorGroup, sample: SensorSample): Boolean =
        channels(group).any { it !in excludedChannels } && sample.isOutOfRange(group)

    fun share(group: SensorGroup, value: Double, sample: SensorSample,
        digits: Int, unit: UvirIrradianceUnit): Float? {
        return if (group in groupPercentages) {
            if (outOfRange(group, sample)) null
            else uvirLiveIrradianceFraction(value, total(group, sample), digits, unit)
        } else {
            shareOfTotal(value, sample, digits, unit)
        }
    }

    /** Island headers always describe their share of the entire visible spectrum. */
    fun groupShare(group: SensorGroup, sample: SensorSample, digits: Int, unit: UvirIrradianceUnit): Float? =
        shareOfTotal(total(group, sample), sample, digits, unit)

    private fun shareOfTotal(value: Double, sample: SensorSample, digits: Int, unit: UvirIrradianceUnit): Float? =
        if (irradianceGroups.any { outOfRange(it, sample) }) null
        else uvirLiveIrradianceGroupShare(value, total(SensorGroup.UV, sample),
            total(SensorGroup.VISIBLE, sample), total(SensorGroup.NIR, sample), digits, unit)

    companion object {
        val irradianceGroups = listOf(SensorGroup.UV, SensorGroup.VISIBLE, SensorGroup.NIR)

        fun channels(group: SensorGroup): List<ThresholdAlertMetric> = when (group) {
            SensorGroup.UV -> listOf(ThresholdAlertMetric.UVC, ThresholdAlertMetric.UVB, ThresholdAlertMetric.UVA)
            SensorGroup.VISIBLE -> listOf(ThresholdAlertMetric.VIOLET, ThresholdAlertMetric.BLUE,
                ThresholdAlertMetric.GREEN, ThresholdAlertMetric.YELLOW, ThresholdAlertMetric.ORANGE, ThresholdAlertMetric.RED)
            SensorGroup.NIR -> listOf(ThresholdAlertMetric.FAR_RED, ThresholdAlertMetric.NIR)
            SensorGroup.BIOLOGICAL -> emptyList()
        }

        /** HEV overlaps violet/blue: it is a toggle for its components, not an extra contribution. */
        private fun components(metric: ThresholdAlertMetric): List<ThresholdAlertMetric> = when (metric) {
            ThresholdAlertMetric.HEV -> listOf(ThresholdAlertMetric.VIOLET, ThresholdAlertMetric.BLUE)
            ThresholdAlertMetric.UV_TOTAL -> channels(SensorGroup.UV)
            ThresholdAlertMetric.VISIBLE_TOTAL -> channels(SensorGroup.VISIBLE)
            ThresholdAlertMetric.NIR_TOTAL -> channels(SensorGroup.NIR)
            else -> if (irradianceGroups.any { metric in channels(it) }) listOf(metric) else emptyList()
        }
    }
}
