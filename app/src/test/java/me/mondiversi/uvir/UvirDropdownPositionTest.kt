package me.mondiversi.uvir

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import org.junit.Assert.assertEquals
import org.junit.Test

class UvirDropdownPositionTest {
    private val anchor = IntRect(24, 100, 336, 148)
    private val phone = IntSize(360, 760)
    private val menu = IntSize(112, 352)

    @Test fun decimalMenuAlignsToTheArrowEdge() {
        val provider = UvirDropdownPositionProvider(8, alignToEnd = true)
        assertEquals(IntOffset(224, 148), provider.calculatePosition(anchor, phone, LayoutDirection.Ltr, menu))
        assertEquals(IntOffset(24, 148), provider.calculatePosition(anchor, phone, LayoutDirection.Rtl, menu))
    }

    @Test fun existingMenusStillPreferTheStartEdge() {
        val provider = UvirDropdownPositionProvider(8)
        assertEquals(IntOffset(24, 148), provider.calculatePosition(anchor, phone, LayoutDirection.Ltr, menu))
        assertEquals(IntOffset(224, 148), provider.calculatePosition(anchor, phone, LayoutDirection.Rtl, menu))
    }

    @Test fun preferredEdgeFallsBackWhenItWouldLeaveTheWindow() {
        val provider = UvirDropdownPositionProvider(8, alignToEnd = true)
        val narrowAnchor = IntRect(20, 100, 60, 148)
        assertEquals(IntOffset(20, 148), provider.calculatePosition(narrowAnchor, phone, LayoutDirection.Ltr, menu))
    }

    @Test fun neitherEdgeFitsSoThePreferredPositionIsClamped() {
        val provider = UvirDropdownPositionProvider(8, alignToEnd = true)
        val partlyOutside = IntRect(-60, 100, 60, 148)
        assertEquals(IntOffset(8, 148), provider.calculatePosition(partlyOutside, phone, LayoutDirection.Ltr, menu))
    }

    @Test fun landscapeUsesTheSameEdgeAndOpensAboveWhenNeeded() {
        val provider = UvirDropdownPositionProvider(8, alignToEnd = true)
        assertEquals(IntOffset(580, 92), provider.calculatePosition(IntRect(420, 300, 740, 348),
            IntSize(800, 360), LayoutDirection.Ltr, IntSize(160, 208)))
    }

    @Test fun tabletAndScaledPopupStillAlignToTheSameAnchorEdge() {
        for (scale in listOf(1, 2, 3)) {
            val provider = UvirDropdownPositionProvider(8 * scale, alignToEnd = true)
            assertEquals(IntOffset(720 * scale, 148 * scale), provider.calculatePosition(
                IntRect(420 * scale, 100 * scale, 880 * scale, 148 * scale),
                IntSize(1200 * scale, 800 * scale), LayoutDirection.Ltr, IntSize(160 * scale, 352 * scale)))
        }
    }
}
