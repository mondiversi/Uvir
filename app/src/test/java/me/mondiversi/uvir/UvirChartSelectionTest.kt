package me.mondiversi.uvir

import org.junit.Assert.*
import org.junit.Test

class UvirChartSelectionTest {
    @Test fun emptyOrInvalidPlotHasNoSelection() {
        assertNull(selectSavedChartPoint(emptyList(), 0f, 0f, 100f, 100f))
        assertNull(selectSavedChartPoint(listOf(UvirChartCoordinate(0f, 0f)), 0f, 0f, 0f, 100f))
        assertNull(selectSavedChartPoint(listOf(UvirChartCoordinate(0f, 0f)), Float.NaN, 0f, 100f, 100f))
    }
    @Test fun aSingleRecordIsSelectableAtThePlotCenter() {
        assertEquals(0, selectSavedChartPoint(listOf(UvirChartCoordinate(.5f, .6f)), 50f, 60f, 100f, 100f))
    }
    @Test fun closestPointUsesBothAxesInScreenSpace() {
        val points = listOf(UvirChartCoordinate(.5f, .1f), UvirChartCoordinate(.5f, .8f))
        assertEquals(1, selectSavedChartPoint(points, 50f, 79f, 100f, 100f))
        assertEquals(0, selectSavedChartPoint(points, 50f, 12f, 100f, 100f))
    }
    @Test fun barsAreSelectedBySlotIncludingZeroValues() {
        val points = listOf(UvirChartCoordinate(.25f, 1f), UvirChartCoordinate(.75f, .1f))
        assertEquals(0, selectSavedChartPoint(points, 24f, 1f, 100f, 100f, bars = true))
    }
    @Test fun overlappingSeriesCanBeCycledThenDismissed() {
        val points = List(3) { UvirChartCoordinate(.5f, .5f) }
        assertEquals(0, selectSavedChartPoint(points, 50f, 50f, 100f, 100f))
        assertEquals(1, selectSavedChartPoint(points, 50f, 50f, 100f, 100f, 0))
        assertEquals(2, selectSavedChartPoint(points, 50f, 50f, 100f, 100f, 1))
        assertNull(selectSavedChartPoint(points, 50f, 50f, 100f, 100f, 2))
    }
    @Test fun tappingANewPointDoesNotDismissTheOldOneInstead() {
        val points = listOf(UvirChartCoordinate(.1f, .1f), UvirChartCoordinate(.9f, .9f))
        assertEquals(1, selectSavedChartPoint(points, 90f, 90f, 100f, 100f, 0))
    }
    @Test fun logarithmicCoordinatesSelectTheActualRenderedPoint() {
        val extent = alertThresholdCenteredLogExtent(listOf(1.0, 100.0, 10_000.0))
        val points = listOf(1.0, 100.0, 10_000.0).map {
            UvirChartCoordinate(.5f, 1f - alertThresholdCenteredLogFraction(it, extent))
        }
        assertEquals(1, selectSavedChartPoint(points, 50f, 50f, 100f, 100f))
    }
    @Test fun invalidPointsAreSkippedWithoutChangingTheirOriginalIndexes() {
        val points = listOf(UvirChartCoordinate(Float.NaN, .5f), UvirChartCoordinate(.5f, .5f))
        assertEquals(1, selectSavedChartPoint(points, 50f, 50f, 100f, 100f))
    }
}
