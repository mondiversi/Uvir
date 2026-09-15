package me.mondiversi.uvir

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UvirSensorIdentityTest {
    @Test
    fun firstSensorCanBeDiscoveredWithoutAnExistingAssociation() {
        assertTrue(
            isUsbSensorIdentityAllowed(
                associatedDeviceId = "",
                candidateDeviceId = "UVIR-001"
            )
        )
    }

    @Test
    fun associatedSensorIsAcceptedAcrossHarmlessFormattingDifferences() {
        assertTrue(
            isUsbSensorIdentityAllowed(
                associatedDeviceId = " UVIR-A1B2 ",
                candidateDeviceId = "uvir-a1b2"
            )
        )
    }

    @Test
    fun differentSensorCannotReplaceTheAssociatedSensor() {
        assertFalse(
            isUsbSensorIdentityAllowed(
                associatedDeviceId = "UVIR-001",
                candidateDeviceId = "UVIR-002"
            )
        )
    }

    @Test
    fun missingIdentityCannotBypassAnExistingAssociation() {
        assertFalse(
            isUsbSensorIdentityAllowed(
                associatedDeviceId = "UVIR-001",
                candidateDeviceId = ""
            )
        )
    }
}
