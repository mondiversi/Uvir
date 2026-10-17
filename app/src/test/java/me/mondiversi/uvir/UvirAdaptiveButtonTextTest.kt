package me.mondiversi.uvir

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UvirAdaptiveButtonTextTest {
    @Test fun startsAtMaximumAndStopsAtFirstFittingSize() {
        val attempts = mutableListOf<Float>()
        val size = fitUvirButtonFontSize(14f, 10f) { attempts += it; it <= 12.5f }
        assertEquals(12.5f, size)
        assertEquals(listOf(14f, 13.5f, 13f, 12.5f), attempts)
    }

    @Test fun keepsReadableMinimumEvenWhenLabelIsTooLong() {
        var attempts = 0
        assertEquals(10f, fitUvirButtonFontSize(14f, 10f) { attempts++; false })
        assertTrue(attempts <= 8)
    }

    @Test fun largerSpaceRestoresMaximumInsteadOfKeepingAnOldShrunkenSize() {
        assertEquals(11f, fitUvirButtonFontSize(14f, 10f) { it <= 11f })
        assertEquals(14f, fitUvirButtonFontSize(14f, 10f) { true })
    }
}
