package me.mondiversi.uvir

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UvirSettingsImportRouteTest {
    @Test
    fun encryptedAssociationTargetsBackupSensorWithoutReplacingActiveSensor() {
        val route =
            resolveUvirSensorSettingsImportRoute(
                activeHardwareUid = "sensor-a",
                backupHardwareUid = " sensor-b ",
                backupAuthToken = "secret-b",
                encrypted = true
            )

        assertEquals("sensor-b", route.hardwareUid)
        assertTrue(route.restoresAssociation)
    }

    @Test
    fun plainBackupRemainsTemplateForActiveSensor() {
        val route =
            resolveUvirSensorSettingsImportRoute(
                activeHardwareUid = " sensor-a ",
                backupHardwareUid = "sensor-b",
                backupAuthToken = "secret-b",
                encrypted = false
            )

        assertEquals("sensor-a", route.hardwareUid)
        assertFalse(route.restoresAssociation)
    }

    @Test
    fun oldEncryptedBackupWithoutAssociationTokenTargetsActiveSensor() {
        val route =
            resolveUvirSensorSettingsImportRoute(
                activeHardwareUid = "sensor-a",
                backupHardwareUid = "sensor-b",
                backupAuthToken = "",
                encrypted = true
            )

        assertEquals("sensor-a", route.hardwareUid)
        assertFalse(route.restoresAssociation)
    }

    @Test
    fun encryptedAssociationCanBeRestoredWhenNoSensorIsActive() {
        val route =
            resolveUvirSensorSettingsImportRoute(
                activeHardwareUid = "",
                backupHardwareUid = "sensor-b",
                backupAuthToken = "secret-b",
                encrypted = true
            )

        assertEquals("sensor-b", route.hardwareUid)
        assertTrue(route.restoresAssociation)
    }
}
