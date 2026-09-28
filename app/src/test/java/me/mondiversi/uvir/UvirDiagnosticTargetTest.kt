package me.mondiversi.uvir

import org.junit.Assert.*
import org.junit.Test

class UvirDiagnosticTargetTest {
    @Test fun onlySelectedConfirmedConnectedPeerAcceptsDiagnosticCommands() {
        assertTrue(uvirDiagnosticPeerMatches(" A ", "a", true, true))
        assertFalse(uvirDiagnosticPeerMatches("A", "B", true, true))
        assertFalse(uvirDiagnosticPeerMatches("A", "A", false, true))
        assertFalse(uvirDiagnosticPeerMatches("A", "A", true, false))
        assertFalse(uvirDiagnosticPeerMatches("", "A", true, true))
        assertFalse(uvirDiagnosticPeerMatches("A", "", true, true))
        assertFalse(uvirDiagnosticPeerMatches("", "", true, true))
    }

    @Test fun switchingSelectionNeverMakesOldCommandsValidForNewPeer() {
        val originalTarget = "A"
        assertTrue(uvirDiagnosticPeerMatches(originalTarget, "A", true, true))
        assertFalse(uvirDiagnosticPeerMatches(originalTarget, "B", true, true))
        assertTrue(uvirDiagnosticPeerMatches("B", "B", true, true))
    }
}
