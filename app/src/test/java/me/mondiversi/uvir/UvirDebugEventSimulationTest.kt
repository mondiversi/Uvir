package me.mondiversi.uvir

import org.junit.Assert.*
import org.junit.Test

class UvirDebugEventSimulationTest {
    @Test fun simulationIsAlwaysAvailableWhenNotPreparingFilesOrRecords() {
        assertTrue(uvirDebugEventSimulationAvailable(preparing = false))
        assertFalse(uvirDebugEventSimulationAvailable(preparing = true))
    }

    @Test fun eventsAlwaysUseTheUnattributedMockIdentity() {
        assertEquals("", UVIR_SIMULATED_EVENT_SENSOR_ID)
    }
}
