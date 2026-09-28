package me.mondiversi.uvir

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UvirLiveUncertaintyTest {
    @Test
    fun requiresEnoughRecentSamples() {
        val history =
            List(4) { index ->
                LiveSamplePoint(index.toLong(), SensorSample(rosso = 10.0))
            }

        assertNull(
            estimateLiveGroupUncertaintyPercent(history, SensorGroup.VISIBLE)
        )
    }

    @Test
    fun stableReadingsHaveZeroShortTermDispersion() {
        val history =
            List(8) { index ->
                LiveSamplePoint(index.toLong(), SensorSample(rosso = 10.0))
            }

        assertEquals(
            0.0,
            estimateLiveGroupUncertaintyPercent(history, SensorGroup.VISIBLE)!!,
            0.0001
        )
    }

    @Test
    fun varyingReadingsProduceAVisiblePercentage() {
        val values = listOf(8.0, 9.0, 10.0, 11.0, 12.0, 10.0)
        val history =
            values.mapIndexed { index, value ->
                LiveSamplePoint(index.toLong(), SensorSample(f8 = value))
            }

        val result =
            estimateLiveGroupUncertaintyPercent(history, SensorGroup.NIR)

        assertTrue(result != null && result > 0.0)
    }

    @Test
    fun outOfRangeSamplesDoNotCreateFalsePrecision() {
        val history =
            List(8) { index ->
                LiveSamplePoint(
                    index.toLong(),
                    SensorSample(
                        rosso = 10.0,
                        qualityFlags = UVIR_QUALITY_VISIBLE_NIR_OUT_OF_RANGE
                    )
                )
            }

        assertNull(
            estimateLiveGroupUncertaintyPercent(history, SensorGroup.VISIBLE)
        )
    }
}
