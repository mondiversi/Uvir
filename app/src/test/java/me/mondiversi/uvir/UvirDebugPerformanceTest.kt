package me.mondiversi.uvir

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UvirDebugPerformanceTest {
    @Test
    fun ordinaryChoicesHideTheSecretMelodyAndKeepEnglishAlphabeticalOrder() {
        assertEquals(
            listOf("Indiana Jones", "Jurassic Park", "Star Wars"),
            UvirVisibleDebugPerformances.map { it.displayName }
        )
        assertFalse(UvirDebugPerformance.HAPPY_BIRTHDAY in UvirVisibleDebugPerformances)
        assertEquals(
            "DEBUG_PERFORMANCE HAPPY_BIRTHDAY",
            debugPerformanceCommand(UvirDebugPerformance.HAPPY_BIRTHDAY.protocolValue)
        )
    }

    @Test
    fun performanceUsesSingleTransportCommand() {
        assertEquals(
            "DEBUG_PERFORMANCE INDIANA_JONES",
            debugPerformanceCommand("INDIANA_JONES")
        )
    }

    @Test
    fun performanceRequiresItsFirmwareVersion() {
        assertFalse(firmwareSupportsDebugPerformance("0.5.54"))
        assertFalse(firmwareSupportsDebugPerformance("0.5.55"))
        assertFalse(firmwareSupportsDebugPerformance("0.5.56"))
        assertFalse(firmwareSupportsDebugPerformance("0.5.59"))
        assertFalse(firmwareSupportsDebugPerformance("0.5.60"))
        assertFalse(firmwareSupportsDebugPerformance("0.5.61"))
        assertFalse(firmwareSupportsDebugPerformance("0.5.62"))
        assertFalse(firmwareSupportsDebugPerformance("0.5.63"))
        assertTrue(firmwareSupportsDebugPerformance("0.5.64"))
        assertTrue(firmwareSupportsDebugPerformance("0.6.0"))
    }
}
