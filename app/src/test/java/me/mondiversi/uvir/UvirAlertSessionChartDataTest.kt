package me.mondiversi.uvir

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UvirAlertSessionChartDataTest {
    @Test
    fun buildsSeriesFromRecordedAlertsOnly() {
        val entries =
            listOf(
                ThresholdAlertLogEntry(
                    id = 2L,
                    timestamp = 2_000L,
                    details = "UVA|15.5|ABOVE|10.0",
                    sessionId = 7L
                ),
                ThresholdAlertLogEntry(
                    id = 1L,
                    timestamp = 1_000L,
                    details = "UVA|12.5|ABOVE|10.0;BLUE|4.0|BELOW|5.0",
                    sessionId = 7L
                )
            )

        val series = alertSessionChartSeries(entries)

        assertEquals(2, series.size)
        val uva = series.first { it.metric == ThresholdAlertMetric.UVA }
        assertEquals(ThresholdAlertDirection.ABOVE, uva.direction)
        assertEquals(listOf(1_000L, 2_000L), uva.points.map { it.timestamp })
        assertEquals(listOf(12.5, 15.5), uva.points.map { it.value })
        assertTrue(uva.points.all { it.threshold == 10.0 })
    }

    @Test
    fun ignoresMalformedAndNonFiniteValues() {
        val entries =
            listOf(
                ThresholdAlertLogEntry(
                    id = 1L,
                    timestamp = 1_000L,
                    details = "invalid;UVA|NaN|ABOVE|10.0;UVB|8.0|ABOVE|7.0",
                    sessionId = 3L
                )
            )

        val series = alertSessionChartSeries(entries)

        assertEquals(1, series.size)
        assertEquals(ThresholdAlertMetric.UVB, series.single().metric)
    }

    @Test
    fun normalizesEveryPointAgainstItsOwnThreshold() {
        val series =
            AlertSessionChartSeries(
                metric = ThresholdAlertMetric.UVA,
                direction = ThresholdAlertDirection.ABOVE,
                color = androidx.compose.ui.graphics.Color.Red,
                points =
                    listOf(
                        AlertSessionChartPoint(1L, value = 15.0, threshold = 10.0),
                        AlertSessionChartPoint(2L, value = 10.0, threshold = 20.0)
                    )
            )

        assertTrue(series.usesPercentageScale())
        assertEquals(150.0, series.points[0].chartValue(true), 0.0)
        assertEquals(50.0, series.points[1].chartValue(true), 0.0)
        assertEquals(100.0, series.points[1].chartThreshold(true), 0.0)
    }

    @Test
    fun keepsAbsoluteScaleWhenAThresholdIsZero() {
        val series =
            AlertSessionChartSeries(
                metric = ThresholdAlertMetric.UVB,
                direction = ThresholdAlertDirection.ABOVE,
                color = androidx.compose.ui.graphics.Color.Blue,
                points =
                    listOf(
                        AlertSessionChartPoint(1L, value = 3.0, threshold = 0.0)
                    )
            )

        assertTrue(!series.usesPercentageScale())
        assertEquals(3.0, series.points.single().chartValue(false), 0.0)
        assertEquals(0.0, series.points.single().chartThreshold(false), 0.0)
    }

    @Test
    fun groupsSessionValuesByRecordedEventInsteadOfMetric() {
        val entries =
            listOf(
                ThresholdAlertLogEntry(
                    id = 11L,
                    timestamp = 2_000L,
                    details = "UVA|15.0|ABOVE|10.0",
                    sessionId = 4L,
                    sessionSequence = 2
                ),
                ThresholdAlertLogEntry(
                    id = 10L,
                    timestamp = 1_000L,
                    details =
                        "UVB|8.0|ABOVE|7.0;UVA|12.0|ABOVE|10.0;" +
                            "BIO_DNA_UV|2.0|ABOVE|1.0",
                    sessionId = 4L,
                    sessionSequence = 1
                )
            )

        val irradianceEvents =
            alertSessionValueEvents(
                entries = entries,
                biologicalEffects = false
            )
        val biologicalEvents =
            alertSessionValueEvents(
                entries = entries,
                biologicalEffects = true
            )

        assertEquals(listOf(10L, 11L), irradianceEvents.map { it.entry.id })
        assertEquals(
            listOf(ThresholdAlertMetric.UVB, ThresholdAlertMetric.UVA),
            irradianceEvents.first().violations.map { it.rule.metric }
        )
        assertEquals(listOf(10L), biologicalEvents.map { it.entry.id })
        assertEquals(
            ThresholdAlertMetric.BIO_DNA_UV,
            biologicalEvents.single().violations.single().rule.metric
        )
    }
}
