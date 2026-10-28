package me.mondiversi.uvir

import org.junit.Assert.*
import org.junit.Test

class UvirSettingsExitConfirmationTest {
    @Test fun replacingTheSettingsPageClosesThePreviousVisitOnlyAfterRendering() {
        var shown = 0; val navigation = UvirSettingsExitTransition()
        val appearance = UvirSettingsExitConfirmation()
        navigation.updatePage("appearance", appearance) { shown++ }
        appearance.saved()
        navigation.updatePage("appearance", appearance) { shown++ }
        assertEquals(0, shown)
        navigation.updatePage(null, UvirSettingsExitConfirmation()) { shown++ }
        appearance.saved()
        assertEquals(1, shown)
    }
    @Test fun openingAndLeavingWithoutASuccessfulWriteIsSilent() {
        var shown = 0
        UvirSettingsExitConfirmation().leave { shown++ }
        assertEquals(0, shown)
    }
    @Test fun manySavedControlsAreConfirmedOnlyOnceWhenLeaving() {
        var shown = 0; val state = UvirSettingsExitConfirmation()
        repeat(10) { state.saved() }
        assertEquals(0, shown)
        state.leave { shown++ }; state.leave { shown++ }; state.saved()
        assertEquals(1, shown)
    }
    @Test fun aLateHardwareAcknowledgementIsConfirmedAfterLeavingWithoutDuplicates() {
        var shown = 0; val state = UvirSettingsExitConfirmation()
        state.leave { shown++ }; assertEquals(0, shown)
        repeat(3) { state.saved() }
        assertEquals(1, shown)
    }
    @Test fun recreatingAnOpenPageKeepsItsPendingConfirmationSeparateFromTheNextVisit() {
        var shown = 0; val state = UvirSettingsExitConfirmation(); state.saved()
        val restored = UvirSettingsExitConfirmation(state.changed)
        restored.leave { shown++ }
        UvirSettingsExitConfirmation().leave { shown++ }
        assertEquals(1, shown)
    }
}
