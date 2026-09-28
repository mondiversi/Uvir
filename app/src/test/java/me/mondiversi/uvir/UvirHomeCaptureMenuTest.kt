package me.mondiversi.uvir

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UvirHomeCaptureMenuTest {
    @Test fun idleCaptureShadeLightensInBothThemesAtTheSizePeak() {
        for (base in listOf(Color(0xFF6650A4), Color(0xFFD0BCFF))) {
            val peak = uvirIdleCaptureColor(base, scale = 1.10f)
            assertEquals(lerp(base, Color.White, 0.30f), peak)
            assertTrue(peak.luminance() > base.luminance())
            assertEquals(base.alpha, peak.alpha, 0f)
        }
    }

    @Test fun idleCaptureShadeReturnsToItsBaseAndStaysWithinTheGentleRange() {
        for (base in listOf(Color(0xFF6650A4), Color(0xFFD0BCFF))) {
            assertEquals(base, uvirIdleCaptureColor(base, 1f))
            assertEquals(base, uvirIdleCaptureColor(base, 0.9f))
            assertEquals(uvirIdleCaptureColor(base, 1.10f), uvirIdleCaptureColor(base, 1.2f))
            val middle = uvirIdleCaptureColor(base, 1.05f).luminance()
            val peak = uvirIdleCaptureColor(base, 1.10f).luminance()
            assertTrue(middle in minOf(base.luminance(), peak)..maxOf(base.luminance(), peak))
        }
    }

    @Test fun shortcutsRequireBothConnectionAndReadyLiveData() {
        assertFalse(uvirIdleCaptureActionsAvailable(false, false))
        assertFalse(uvirIdleCaptureActionsAvailable(false, true))
        assertFalse(uvirIdleCaptureActionsAvailable(true, false))
        assertTrue(uvirIdleCaptureActionsAvailable(true, true))
    }

    private fun targetAt(x: Float, y: Float) = uvirCaptureMenuTarget(
        position = Offset(x, y),
        primarySize = 56f,
        secondarySize = 56f,
        spacing = 10f,
        extraTouchRadius = 7f
    )

    @Test fun slidingLeftSelectsAutomaticAcquisition() {
        assertEquals(UvirCaptureMenuTarget.AUTOMATIC, targetAt(-31f, 28f))
    }

    @Test fun slidingUpSelectsValueAlerts() {
        assertEquals(UvirCaptureMenuTarget.ALERT, targetAt(28f, -31f))
    }

    @Test fun expandedShortcutsAcceptTouchesNearTheirOuterEdges() {
        assertEquals(UvirCaptureMenuTarget.AUTOMATIC, targetAt(-65f, 28f))
        assertEquals(UvirCaptureMenuTarget.ALERT, targetAt(28f, -65f))
    }

    @Test fun releasingOnCaptureSelectsPrimaryAction() {
        assertEquals(UvirCaptureMenuTarget.PRIMARY, targetAt(28f, 28f))
    }

    @Test fun releasingOutsideOptionsSelectsNothing() {
        assertNull(targetAt(-80f, -80f))
    }

    @Test fun onlyTheCurrentlySelectedCaptureActionIsEmphasized() {
        assertTrue(uvirCaptureMenuPrimarySelected(holding = true, hovered = null))
        assertTrue(uvirCaptureMenuPrimarySelected(holding = false, hovered = UvirCaptureMenuTarget.PRIMARY))
        assertFalse(uvirCaptureMenuPrimarySelected(holding = false, hovered = UvirCaptureMenuTarget.AUTOMATIC))
        assertFalse(uvirCaptureMenuPrimarySelected(holding = false, hovered = UvirCaptureMenuTarget.ALERT))
        assertFalse(uvirCaptureMenuPrimarySelected(holding = false, hovered = null))
    }

}
