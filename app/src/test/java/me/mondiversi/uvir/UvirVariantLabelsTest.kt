package me.mondiversi.uvir

import org.junit.Assert.assertEquals
import org.junit.Test

class UvirVariantLabelsTest {
    @Test
    fun convertsOneBasedIndexesToSpreadsheetStyleLetters() {
        assertEquals("A", uvirVariantLabel(1))
        assertEquals("B", uvirVariantLabel(2))
        assertEquals("Z", uvirVariantLabel(26))
        assertEquals("AA", uvirVariantLabel(27))
        assertEquals("AZ", uvirVariantLabel(52))
        assertEquals("BA", uvirVariantLabel(53))
    }

    @Test
    fun compactLabelSeparatesVariantFromMeasurementPosition() {
        assertEquals("1-A", uvirCompactVariantPositionLabel(1, 1))
        assertEquals("1-B", uvirCompactVariantPositionLabel(2, 1))
        assertEquals("2-A", uvirCompactVariantPositionLabel(1, 2))
    }

}
