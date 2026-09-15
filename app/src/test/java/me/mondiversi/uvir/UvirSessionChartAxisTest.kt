package me.mondiversi.uvir

import org.junit.Assert.assertEquals
import org.junit.Test

class UvirSessionChartAxisTest {
    @Test
    fun narrowChartUsesOneIntermediateTick() {
        assertEquals(
            listOf(0f, 0.5f, 1f),
            sessionChartTimeTickFractions(
                availableWidth = 280f,
                minimumTickSpacing = 74f
            )
        )
    }

    @Test
    fun wideChartUsesThreeIntermediateTicks() {
        assertEquals(
            listOf(0f, 0.25f, 0.5f, 0.75f, 1f),
            sessionChartTimeTickFractions(
                availableWidth = 400f,
                minimumTickSpacing = 74f
            )
        )
    }
}
