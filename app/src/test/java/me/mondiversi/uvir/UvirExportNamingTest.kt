package me.mondiversi.uvir

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Date

class UvirExportNamingTest {
    private val timestamp = 1_700_000_000_000L

    @Test
    fun `general exports use one portable structure`() {
        assertEquals(
            "Uvir_Error_Log_${uvirExportTimestamp(timestamp)}",
            uvirExportBaseName(UvirExportContent.ERRORS, Date(timestamp))
        )
        assertEquals(
            "Uvir_Database_${uvirExportTimestamp(timestamp)}",
            uvirExportBaseName(UvirExportContent.DATABASE, Date(timestamp))
        )
    }

    @Test
    fun `settings names identify scope sensor and encryption`() {
        assertEquals(
            "Uvir_Settings_App_${uvirExportTimestamp(timestamp)}.uvirsettings",
            uvirSettingsExportFileName(
                sensorHardwareUid = null,
                encrypted = false,
                timestamp = timestamp
            )
        )
        assertEquals(
            "Uvir_Settings_Sensor_ABC_12_Encrypted_${uvirExportTimestamp(timestamp)}.uvirsettings",
            uvirSettingsExportFileName(
                sensorHardwareUid = " ABC:12 ",
                encrypted = true,
                timestamp = timestamp
            )
        )
    }

    @Test
    fun `sensor reports use safe device identifiers`() {
        assertEquals(
            "Uvir_Sensor_Information_A_B_${uvirExportTimestamp(timestamp)}.txt",
            uvirSensorInformationExportFileName("A/B", timestamp)
        )
        assertEquals(
            "Uvir_Sensor_Diagnostic_Sensor_${uvirExportTimestamp(timestamp)}.txt",
            uvirSensorDiagnosticExportFileName("///", timestamp)
        )
    }
}
