package me.mondiversi.uvir

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import kotlin.math.roundToInt

/** Preserve the phone layout while enlarging all Compose dimensions on wider devices. */
internal fun uvirAutomaticScreenScale(shortestWidthDp: Int): Float =
    (shortestWidthDp / 400f).coerceIn(1f, 2.5f)

/** Preserve Android's non-linear accessibility font conversion when scaling dp. */
private class UvirScaledDensity(source: Density, scale: Float) : Density {
    private val fontSource = source
    override val density: Float = source.density * scale
    override val fontScale: Float = source.fontScale

    override fun Dp.toSp(): TextUnit = with(fontSource) { this@toSp.toSp() }
    override fun TextUnit.toDp(): Dp = with(fontSource) { this@toDp.toDp() }
}

@Composable
internal fun UvirAdaptiveDisplay(content: @Composable () -> Unit) {
    val deviceConfiguration = LocalConfiguration.current
    val deviceDensity = LocalDensity.current
    val shortestWidthDp = deviceConfiguration.smallestScreenWidthDp.takeIf { it > 0 }
        ?: minOf(deviceConfiguration.screenWidthDp, deviceConfiguration.screenHeightDp)
    val scale = uvirAutomaticScreenScale(shortestWidthDp)

    if (scale == 1f) {
        content()
        return
    }

    val adaptiveDensity = remember(deviceDensity, scale) {
        UvirScaledDensity(deviceDensity, scale)
    }
    val adaptiveConfiguration = remember(deviceConfiguration, scale) {
        Configuration(deviceConfiguration).apply {
            // Code which sizes popups from Configuration must see the same
            // logical viewport as the enlarged density. Keep the physical
            // smallest width so a future tablet layout can detect it.
            screenWidthDp = (deviceConfiguration.screenWidthDp / scale).roundToInt().coerceAtLeast(1)
            screenHeightDp = (deviceConfiguration.screenHeightDp / scale).roundToInt().coerceAtLeast(1)
        }
    }
    CompositionLocalProvider(
        LocalDensity provides adaptiveDensity,
        LocalConfiguration provides adaptiveConfiguration,
        content = content
    )
}
