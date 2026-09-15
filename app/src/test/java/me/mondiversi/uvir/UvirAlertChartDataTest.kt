package me.mondiversi.uvir

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UvirAlertChartDataTest {
    @Test
    fun reportsSignedDifferenceFromThreshold() {
        val above = chartBar(thresholdPercent = 125.0)
        val below = chartBar(thresholdPercent = 82.5)

        assertEquals(25.0, above.thresholdDeltaPercent, 0.0)
        assertEquals(-17.5, below.thresholdDeltaPercent, 0.0)
    }

    @Test
    fun calculatesSignedThresholdDeviationForAlertValues() {
        assertEquals(
            25.0,
            alertThresholdDeltaPercent(
                value = 1.25,
                threshold = 1.0
            )!!,
            0.000_001
        )
        assertEquals(
            -17.5,
            alertThresholdDeltaPercent(
                value = 0.825,
                threshold = 1.0
            )!!,
            0.000_001
        )
        assertNull(
            alertThresholdDeltaPercent(
                value = 1.0,
                threshold = 0.0
            )
        )
    }

    private fun chartBar(thresholdPercent: Double) =
        AlertChartBar(
            metric = ThresholdAlertMetric.UVA,
            direction = ThresholdAlertDirection.ABOVE,
            shortLabel = "UVA",
            color = Color.Red,
            value = 1.0,
            threshold = 1.0,
            thresholdPercent = thresholdPercent
        )
}
