package me.mondiversi.uvir

/** Presentation only: never use these rounded values for storage, calibration or alerts. */
internal fun uvirLiveIrradianceFraction(
    value: Double,
    total: Double,
    digits: Int,
    unit: UvirIrradianceUnit,
    percentDigits: Int = 1
): Float = uvirLiveDisplayFraction(
    roundUvirLiveDisplayValue(unit.fromCanonicalWm2(value), digits),
    roundUvirLiveDisplayValue(unit.fromCanonicalWm2(total), digits),
    percentDigits
)

/** Sum the displayed group totals, so hidden sub-resolution groups cannot move the bars. */
internal fun uvirLiveIrradianceGroupShare(
    groupTotal: Double,
    uvTotal: Double,
    visibleTotal: Double,
    infraredTotal: Double,
    digits: Int,
    unit: UvirIrradianceUnit,
    percentDigits: Int = 1
): Float {
    fun displayed(value: Double) = roundUvirLiveDisplayValue(unit.fromCanonicalWm2(value), digits)
    return uvirLiveDisplayFraction(displayed(groupTotal),
        displayed(uvTotal) + displayed(visibleTotal) + displayed(infraredTotal), percentDigits)
}

/** A channel (including overlapping HEV) is a portion of the complete spectral measurement. */
internal fun UvirSpectralTotals.displayShare(
    value: Double,
    digits: Int,
    unit: UvirIrradianceUnit,
    percentDigits: Int = 1
): Float? = if (outOfRange) null else
    uvirLiveIrradianceGroupShare(value, ultraviolet, visible, infrared, digits, unit, percentDigits)

private fun uvirLiveDisplayFraction(value: Double, total: Double, percentDigits: Int): Float {
    if (!value.isFinite() || !total.isFinite()) return 0f
    // The fill also respects the precision of the percentage written beside it.
    val percent = percentage(value, total).toDouble() * 100.0
    return (roundUvirLiveDisplayValue(percent, percentDigits) / 100.0).toFloat()
}
