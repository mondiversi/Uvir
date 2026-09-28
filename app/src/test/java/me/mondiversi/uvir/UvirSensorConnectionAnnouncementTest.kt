package me.mondiversi.uvir

import org.junit.Assert.assertEquals
import org.junit.Test

class UvirSensorConnectionAnnouncementTest {
    private val profiles = listOf(
        UvirSensorProfile(1, "UVIR-A", "Portatile", 0, 0),
        UvirSensorProfile(2, "UVIR-B", "Esterno", 0, 0)
    )

    @Test fun connectedUsesTheCurrentSensor() {
        val id = uvirConnectionAnnouncementDeviceId(true, " UVIR-B ", "UVIR-A")
        assertEquals("Esterno", uvirConnectionAnnouncementSensorName(id, profiles))
    }

    @Test fun disconnectedUsesThePreviousSensorEvenAfterSelectionChanges() {
        val id = uvirConnectionAnnouncementDeviceId(false, "UVIR-B", "UVIR-A")
        assertEquals("Portatile", uvirConnectionAnnouncementSensorName(id, profiles))
    }

    @Test fun disconnectedRetainsIdentityWhenTheTransportClearsItsId() {
        assertEquals("UVIR-A", uvirConnectionAnnouncementDeviceId(false, "", "UVIR-A"))
    }

    @Test fun profileLookupNormalizesIdentityAndReadsTheLatestName() {
        assertEquals("Portatile", uvirConnectionAnnouncementSensorName(" uvir-a ", profiles))
        assertEquals("Nuovo nome", uvirConnectionAnnouncementSensorName("UVIR-A",
            listOf(profiles[0].copy(displayName = "Nuovo nome"))))
    }

    @Test fun missingOrBlankNameFallsBackToTheHardwareId() {
        assertEquals("UVIR-C", uvirConnectionAnnouncementSensorName(" UVIR-C ", profiles))
        assertEquals("UVIR-A", uvirConnectionAnnouncementSensorName("UVIR-A",
            listOf(profiles[0].copy(displayName = "  "))))
    }

    @Test fun noIdentityDoesNotBorrowAnotherSensorsName() {
        assertEquals("", uvirConnectionAnnouncementSensorName(" ", profiles))
    }
}
