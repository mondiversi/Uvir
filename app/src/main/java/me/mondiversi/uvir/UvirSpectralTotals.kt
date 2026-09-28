package me.mondiversi.uvir

/** Three mutually exclusive spectral areas, in the sensor's base irradiance unit. */
internal data class UvirSpectralTotals(
    val ultraviolet: Double,
    val visible: Double,
    val infrared: Double,
    val outOfRange: Boolean = false
) {
    fun total(group: SensorGroup): Double = when (group) {
        SensorGroup.UV -> ultraviolet
        SensorGroup.VISIBLE -> visible
        SensorGroup.NIR -> infrared
        SensorGroup.BIOLOGICAL -> 0.0
    }

    fun share(group: SensorGroup): Float? =
        if (outOfRange || group == SensorGroup.BIOLOGICAL) null
        else uvirIrradianceGroupShare(total(group), ultraviolet, visible, infrared)
}

internal fun SensorSample.spectralTotals(): UvirSpectralTotals =
    UvirSpectralTotals(
        ultraviolet = uvc + uvb + uva,
        visible = violetto + blu + verde + giallo + arancione + rosso,
        infrared = f8 + nir,
        outOfRange = hasAnyOutOfRangeValue()
    )
