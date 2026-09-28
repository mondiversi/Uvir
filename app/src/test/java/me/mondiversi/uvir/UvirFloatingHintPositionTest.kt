package me.mondiversi.uvir

import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UvirFloatingHintPositionTest {
    private val provider = UvirFloatingHintPositionProvider(12, 122)

    @Test fun labelStaysInsidePortraitAndLandscapeWindows() {
        for (window in listOf(IntSize(360, 760), IntSize(760, 360))) {
            for (direction in listOf(LayoutDirection.Ltr, LayoutDirection.Rtl)) {
                for (anchor in listOf(
                    IntRect(12, 12, 68, 68),
                    IntRect(window.width - 68, window.height - 68, window.width - 12, window.height - 12)
                )) {
                    val popup = IntSize(240, 64)
                    val position = provider.calculatePosition(anchor, window, direction, popup)
                    assertTrue(position.x >= 12)
                    assertTrue(position.y >= 12)
                    assertTrue(position.x + popup.width <= window.width - 12)
                    assertTrue(position.y + popup.height <= window.height - 12)
                }
            }
        }
    }

    @Test fun ordinaryBottomRightLabelDoesNotCoverTheFinger() {
        val anchor = IntRect(292, 692, 348, 748)
        val popup = IntSize(160, 36)
        val position = provider.calculatePosition(anchor, IntSize(360, 760), LayoutDirection.Ltr, popup)
        assertEquals(180, position.x + popup.width / 2)
        assertEquals(anchor.bottom - 122 - 12, position.y + popup.height)
    }

    @Test fun sameBottomEdgeKeepsHomeRecAndStopLabelsInTheSamePlaceInEitherDirection() {
        val window = IntSize(360, 760)
        val popup = IntSize(280, 56)
        val home = IntRect(226, 626, 348, 748)
        val recOrStop = IntRect(292, 692, 348, 748)
        val expected = provider.calculatePosition(home, window, LayoutDirection.Ltr, popup)
        for (direction in LayoutDirection.values()) {
            assertEquals(expected, provider.calculatePosition(home, window, direction, popup))
            assertEquals(expected, provider.calculatePosition(recOrStop, window, direction, popup))
        }
    }
}
