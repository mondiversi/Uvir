package me.mondiversi.uvir

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class UvirScrollbarGeometryTest {
    @Test fun exactReportedViewportDoesNotHaveAnEmptyClampRange() {
        assertEquals(71f, uvirScrollbarThumbHeight(71f, 0.1f, 94.5f), 0f)
    }

    @Test fun subMinimumTracksUseOnlyTheAvailableHeight() {
        for (height in listOf(0.1f, 1f, 20f, 28f, 70f, 94.49f, 94.5f)) {
            assertEquals(height, uvirScrollbarThumbHeight(height, 0.01f, 94.5f), 0f)
        }
    }

    @Test fun ordinaryTracksKeepTheOriginalMinimumAndProportionalHeight() {
        assertEquals(94.5f, uvirScrollbarThumbHeight(600f, 0.01f, 94.5f), 0f)
        assertEquals(300f, uvirScrollbarThumbHeight(600f, 0.5f, 94.5f), 0f)
        assertEquals(600f, uvirScrollbarThumbHeight(600f, 1f, 94.5f), 0f)
    }

    @Test fun missingOrInvalidTracksHaveNoThumb() {
        for (height in listOf(0f, -1f, Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
            assertEquals(0f, uvirScrollbarThumbHeight(height, 0.5f, 94.5f), 0f)
        }
    }

    @Test fun fractionsOutsideTheirLogicalBoundsStayInsideTheTrack() {
        assertEquals(28f, uvirScrollbarThumbHeight(400f, -1f, 28f), 0f)
        assertEquals(400f, uvirScrollbarThumbHeight(400f, 2f, 28f), 0f)
        assertEquals(28f, uvirScrollbarThumbHeight(400f, Float.NaN, 28f), 0f)
        assertEquals(28f, uvirScrollbarThumbHeight(400f, Float.POSITIVE_INFINITY, 28f), 0f)
    }

    @Test fun invalidPreferredMinimumDoesNotCreateAnInvalidRange() {
        assertEquals(200f, uvirScrollbarThumbHeight(400f, 0.5f, -28f), 0f)
        assertEquals(200f, uvirScrollbarThumbHeight(400f, 0.5f, Float.NaN), 0f)
        assertEquals(200f, uvirScrollbarThumbHeight(400f, 0.5f, Float.POSITIVE_INFINITY), 0f)
    }

    @Test fun growingAndShrinkingAnimationFramesAreAllSafe() {
        for (height in (0..700).map(Int::toFloat) + (700 downTo 0).map(Int::toFloat)) {
            val thumb = uvirScrollbarThumbHeight(height, 0.1f, 94.5f)
            assertTrue(thumb.isFinite() && thumb >= 0f && thumb <= height)
        }
    }

    @Test fun manyViewportAndDensityCombinationsRemainBounded() {
        val random = Random(71)
        repeat(5000) {
            val height = random.nextFloat() * 3000f
            val minimum = 28f * (0.5f + random.nextFloat() * 5f)
            val thumb = uvirScrollbarThumbHeight(height, random.nextFloat(), minimum)
            assertTrue(thumb.isFinite() && thumb >= 0f && thumb <= height)
        }
    }
}
