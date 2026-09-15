package me.mondiversi.uvir

import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class UvirTitleBarPaddingTest {
    @Test fun trailingActionsUseTheBaseEdgeMargin() {
        assertEquals(10.dp, UvirTitleBarContentPadding.calculateLeftPadding(LayoutDirection.Ltr))
        assertEquals(10.dp, UvirTitleBarContentPadding.calculateRightPadding(LayoutDirection.Ltr))
        assertEquals(4.dp, UvirTitleBarContentPadding.calculateTopPadding())
        assertEquals(4.dp, UvirTitleBarContentPadding.calculateBottomPadding())
    }

    @Test fun trailingMarginFollowsRightToLeftLayouts() {
        assertEquals(10.dp, UvirTitleBarContentPadding.calculateLeftPadding(LayoutDirection.Rtl))
        assertEquals(10.dp, UvirTitleBarContentPadding.calculateRightPadding(LayoutDirection.Rtl))
    }
}
