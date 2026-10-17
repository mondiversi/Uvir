package me.mondiversi.uvir

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class UvirRtcStartupCheckTest {
    private fun observation(id: String, stopped: Boolean = true, at: Long = 100L) =
        UvirRtcClockObservation(id, stopped, at)

    @Test fun failedSensorsShareOneOrderedListAndHealthySensorsAreExcluded() {
        val check = UvirRtcStartupCheck(100L)
        assertTrue(check.observe(observation("UVIR-A")))
        assertFalse(check.observe(observation("UVIR-B", false)))
        assertTrue(check.observe(observation("UVIR-C")))
        assertEquals(listOf("UVIR-A", "UVIR-C"), check.pendingDeviceIds())
    }

    @Test fun reconnectsAndDifferentTransportsCannotDuplicateASensor() {
        val check = UvirRtcStartupCheck(100L)
        check.observe(observation("UVIR-A"))
        assertFalse(check.observe(observation(" uvir-a ", at = 200L)))
        assertEquals(listOf("UVIR-A"), check.pendingDeviceIds())
    }

    @Test fun timeSynchronizationCannotEraseTheCapturedStop() {
        val check = UvirRtcStartupCheck(100L)
        check.observe(observation("UVIR-A"))
        check.observe(observation("UVIR-A", false, 200L))
        assertEquals(listOf("UVIR-A"), check.pendingDeviceIds())
    }

    @Test fun firstSuccessfulHealthyCheckCompletesThatDevicesStartupCheck() {
        val check = UvirRtcStartupCheck(100L)
        check.observe(observation("UVIR-A", false))
        assertFalse(check.observe(observation("UVIR-A", true, 200L)))
        assertTrue(check.pendingDeviceIds().isEmpty())
    }

    @Test fun dismissedWarningDoesNotReturnDuringTheSameLaunch() {
        val check = UvirRtcStartupCheck(100L)
        check.observe(observation("UVIR-A"))
        check.dismiss(check.pendingDeviceIds())
        assertFalse(check.observe(observation("UVIR-A", at = 200L)))
        assertTrue(check.pendingDeviceIds().isEmpty())
    }

    @Test fun newLaunchChecksAgainWithoutPermanentSuppression() {
        val first = UvirRtcStartupCheck(100L)
        first.observe(observation("UVIR-A"))
        first.dismiss(first.pendingDeviceIds())
        val second = UvirRtcStartupCheck(200L)
        assertTrue(second.observe(observation("UVIR-A", at = 200L)))
        assertEquals(listOf("UVIR-A"), second.pendingDeviceIds())
    }

    @Test fun previousLaunchQueuedFramesAndBlankIdentitiesAreIgnored() {
        val check = UvirRtcStartupCheck(100L)
        assertFalse(check.observe(observation("UVIR-A", at = 99L)))
        assertFalse(check.observe(observation(" ")))
        assertTrue(check.pendingDeviceIds().isEmpty())
        assertTrue(check.observe(observation("UVIR-A", at = 101L)))
    }

    @Test fun dismissalOnlyAcknowledgesTheDisplayedSensors() {
        val check = UvirRtcStartupCheck(100L)
        check.observe(observation("UVIR-A"))
        val displayed = check.pendingDeviceIds()
        check.observe(observation("UVIR-B", at = 200L))
        check.dismiss(displayed)
        assertEquals(listOf("UVIR-B"), check.pendingDeviceIds())
    }

    @Test fun namesComeFromEachOriginNotFromTheSelectedSensor() {
        val profiles = listOf(UvirSensorProfile(1, "UVIR-A", "Portatile", 0L, 0L),
            UvirSensorProfile(2, "UVIR-B", "Laboratorio", 0L, 0L))
        assertEquals("Laboratorio", uvirConnectionAnnouncementSensorName("uvir-b", profiles))
        assertEquals("UVIR-C", uvirConnectionAnnouncementSensorName("UVIR-C", profiles))
    }

    @Test fun bothTransportsCaptureClockStopsBeforeIssuingTheTimeCommand() {
        val directory = File("src/main/java/me/mondiversi/uvir")
        for (filename in listOf("UvirUsbSensorManager.kt", "UvirWirelessSensorManager.kt")) {
            val source = File(directory, filename).readText()
            val capture = source.indexOf("UvirSensorEventHub.emitRtcClock")
            assertTrue("Missing RTC capture: $filename", capture >= 0)
            assertTrue("No TIME after accepted HELLO: $filename",
                source.indexOf("sensorTimeCommand()", capture) > capture)
            assertFalse("Standby HELLO must also be captured before later activation: $filename",
                source.substring((capture - 28).coerceAtLeast(0), capture).contains("if (desired"))
        }
    }
}
