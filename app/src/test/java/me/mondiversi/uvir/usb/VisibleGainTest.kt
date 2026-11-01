package me.mondiversi.uvir.usb

import kotlin.math.roundToInt
import org.junit.Assert.*
import org.junit.Test

class VisibleGainTest {
    @Test fun lampTraceSettlesBackToTheHighestSafeGainWithoutRepeatingTheClippedGain() {
        assertEquals(6, nextVisibleGain(6, 5400, false))
        assertEquals(5, nextVisibleGain(7, 9993, true))
        assertEquals(6, nextVisibleGain(5, 2700, false))
        assertEquals(6, nextVisibleGain(6, 5400, false))
        assertEquals(6, nextVisibleGain(6, 5404, false))
    }

    @Test fun darknessAndBrightStepsConvergeWithinTheBoundedAttempts() {
        for (initial in visibleGains.indices) for (light in listOf(0.0, .01, .2, 1.0, 5.0, 20.0, 100.0, 1000.0, 10000.0)) {
            var current = initial
            var settled = false
            repeat(9) {
                val unclipped = light * visibleGains[current]
                val peak = minOf(10000, unclipped.roundToInt())
                val saturated = unclipped >= 10000
                val next = nextVisibleGain(current, peak, saturated)
                assertTrue(next in visibleGains.indices)
                if (next == current) {
                    assertFalse("Measurable light must settle unsaturated", saturated)
                    settled = true
                }
                current = next
            }
            assertTrue("Range must settle within nine conversions: initial=$initial light=$light", settled)
        }
    }

    @Test fun hysteresisLeavesHeadroomAndNeverOscillatesAcrossTheSameThreshold() {
        for (current in visibleGains.indices) {
            assertEquals(current, nextVisibleGain(current, 5400, false))
            if (current < visibleGains.lastIndex) {
                val next = nextVisibleGain(current, 3999, false)
                assertEquals(current + 1, next)
                assertEquals(next, nextVisibleGain(next, 7998, false))
            }
            if (current > 0) {
                val next = nextVisibleGain(current, 9200, false)
                assertEquals(current - 1, next)
                assertEquals(next, nextVisibleGain(next, 4600, false))
            }
        }
        assertEquals(0, nextVisibleGain(0, 10000, true)) // Real over-range remains over-range.
        assertEquals(11, nextVisibleGain(0, 0, false))
    }
}
