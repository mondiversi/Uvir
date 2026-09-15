package me.mondiversi.uvir

/** A preferred thumb minimum must never exceed the track left by the IME/layout. */
internal fun uvirScrollbarThumbHeight(
    trackHeight: Float,
    visibleFraction: Float,
    minimumThumbHeight: Float
): Float {
    if (!trackHeight.isFinite() || trackHeight <= 0f) return 0f
    val fraction = if (visibleFraction.isFinite()) visibleFraction.coerceIn(0f, 1f) else 0f
    val minimum = if (minimumThumbHeight.isFinite())
        minimumThumbHeight.coerceIn(0f, trackHeight) else 0f
    return (trackHeight * fraction).coerceIn(minimum, trackHeight)
}
