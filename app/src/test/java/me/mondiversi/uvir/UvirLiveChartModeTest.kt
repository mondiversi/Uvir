package me.mondiversi.uvir

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UvirLiveChartModeTest {
    @Test
    fun newInstallationStartsWithValues() {
        assertFalse(readLiveChartMode(memoryPreferences(mutableMapOf())))
    }

    @Test
    fun existingFirstIslandChoiceIsRetained() {
        assertTrue(
            readLiveChartMode(
                memoryPreferences(mutableMapOf("sensor_group_chart_UV" to true))
            )
        )
    }

    @Test
    fun globalChoiceOverridesDifferentIslandChoices() {
        assertTrue(
            readLiveChartMode(
                memoryPreferences(
                    mutableMapOf(
                        "live_show_chart" to true,
                        "sensor_group_chart_UV" to false,
                        "sensor_group_chart_VISIBLE" to false,
                        "biological_effect_chart_DNA_UV" to false
                    )
                )
            )
        )
    }

    @Test
    fun globalValuesChoiceOverridesFormerChartChoice() {
        assertFalse(
            readLiveChartMode(
                memoryPreferences(
                    mutableMapOf(
                        "live_show_chart" to false,
                        "sensor_group_chart_UV" to true
                    )
                )
            )
        )
    }

    @Test
    fun switchingPersistsWithoutChangingOtherSettings() {
        val values = mutableMapOf<String, Any?>(
            "expanded_UV" to false,
            "sensor_group_chart_UV" to true,
            "samples" to 10
        )
        writeLiveChartMode(memoryPreferences(values), true)
        assertTrue(readLiveChartMode(memoryPreferences(values)))
        writeLiveChartMode(memoryPreferences(values), false)
        assertFalse(readLiveChartMode(memoryPreferences(values)))
        assertEquals(false, values["expanded_UV"])
        assertEquals(true, values["sensor_group_chart_UV"])
        assertEquals(10, values["samples"])
    }
}
