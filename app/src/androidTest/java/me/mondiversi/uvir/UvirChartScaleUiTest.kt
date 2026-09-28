package me.mondiversi.uvir

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Empty host, synthetic chart samples only: never writes app records/preferences or sensor data. */
class UvirChartScaleUiTest {
    @get:Rule val compose = createComposeRule()

    @Composable private fun KeepAwake() {
        val view = LocalView.current
        DisposableEffect(view) {
            val previous = view.keepScreenOn
            view.keepScreenOn = true
            onDispose { view.keepScreenOn = previous }
        }
    }

    @Test fun liveTracePreservesItsHeightAcrossUnitsAndThemesAndIgnoresOldAndOlPeaks() {
        val unit = mutableStateOf(UvirIrradianceUnit.UW_CM2)
        val dark = mutableStateOf(false)
        val history = listOf(
            LiveSamplePoint(-1L, SensorSample(uva = 100_000.0)), // Outside the visible minute.
            LiveSamplePoint(0L, SensorSample(uva = 10.0)),
            LiveSamplePoint(30_000L, SensorSample(uva = 20.0)),
            LiveSamplePoint(40_000L, SensorSample(uva = 200_000.0, qualityFlags = UVIR_QUALITY_UV_OUT_OF_RANGE)),
            LiveSamplePoint(50_000L, SensorSample(uva = 15.0)),
            LiveSamplePoint(60_000L, SensorSample(uva = 5.0))
        )
        compose.setContent {
            KeepAwake()
            MaterialTheme(colorScheme = if (dark.value) darkColorScheme() else lightColorScheme()) {
                Box(Modifier.fillMaxWidth().testTag("live")) {
                    LiveRollingChart(history, listOf(LiveChartSeries("UVA", Color.Blue) { it.uva }),
                        unit.value.symbol, unit.value::fromCanonicalUwCm2,
                        { it.isOutOfRange(SensorGroup.UV) }, Color.Black, Color.Gray)
                }
            }
        }
        var baseline: Pair<Int, Int>? = null
        for (night in listOf(false, true)) for (selected in UvirIrradianceUnit.entries) {
            compose.runOnIdle { dark.value = night; unit.value = selected }
            val pixels = compose.onNodeWithTag("live").captureToImage().toPixelMap()
            val ys = mutableListOf<Int>()
            // Exclude the left-edge legend swatch, keeping only the blue trace.
            for (y in 0 until pixels.height) for (x in pixels.width / 3 until pixels.width) {
                val color = pixels[x, y]
                if (color.blue > .8f && color.red < .15f && color.green < .15f) ys += y
            }
            assertTrue("The trace must remain visible", ys.isNotEmpty())
            val bounds = ys.min() to ys.max()
            assertTrue("Subunit/OL/old samples flattened the trace: $bounds", bounds.second - bounds.first > 80)
            baseline?.let {
                assertEquals(it.first.toDouble(), bounds.first.toDouble(), 1.0)
                assertEquals(it.second.toDouble(), bounds.second.toDouble(), 1.0)
            }
            baseline = bounds
        }
    }

    @Test fun axisLabelsDoNotRoundSubunitTicksToZero() {
        compose.setContent {
            KeepAwake()
            CompositionLocalProvider(LocalUvirNumericFormat provides UvirNumericFormat.INTERNATIONAL) {
                MaterialTheme {
                    UvirChartYAxis(0.022, 1, Color.Gray) { Box(it) }
                }
            }
        }
        for (label in listOf("0.0220", "0.0165", "0.0110", "0.0055", "0")) {
            compose.onNodeWithText(label).assertExists()
        }
    }
}
