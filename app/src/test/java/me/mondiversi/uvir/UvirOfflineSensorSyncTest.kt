package me.mondiversi.uvir

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UvirOfflineSensorSyncTest {

    @Test
    fun synchronizationAcceptsOnlyTheSelectedConnectedSource() {
        assertTrue(
            sensorSyncSourceMatchesSelection(
                SensorSyncSource.USB,
                SensorConnectionMode.USB,
                true
            )
        )
        assertTrue(
            sensorSyncSourceMatchesSelection(
                SensorSyncSource.WIRELESS,
                SensorConnectionMode.WIFI,
                true
            )
        )
        assertTrue(
            sensorSyncSourceMatchesSelection(
                SensorSyncSource.WIRELESS,
                SensorConnectionMode.BLUETOOTH,
                true
            )
        )
        assertFalse(
            sensorSyncSourceMatchesSelection(
                SensorSyncSource.WIRELESS,
                SensorConnectionMode.USB,
                true
            )
        )
        assertFalse(
            sensorSyncSourceMatchesSelection(
                SensorSyncSource.WIRELESS,
                SensorConnectionMode.WIFI,
                false
            )
        )
        assertFalse(
            sensorSyncSourceMatchesSelection(
                SensorSyncSource.USB,
                SensorConnectionMode.WIFI,
                true
            )
        )
    }

    @Test
    fun samplingCommandCarriesTheCompleteSensorSidePipeline() {
        val command = sensorSamplingCommand(
            AcquisitionParameters(
                samplesPerMeasurement = 7,
                sampleSpacingMs = 250L,
                discardExtremes = true
            )
        )

        assertEquals("SAMPLING_CONFIG 7 250 1", command)
    }

    @Test
    fun samplingCommandClampsValuesToFirmwareLimits() {
        val command = sensorSamplingCommand(
            AcquisitionParameters(
                samplesPerMeasurement = 99,
                sampleSpacingMs = 20_000L,
                discardExtremes = false
            )
        )

        assertEquals("SAMPLING_CONFIG 21 5000 0", command)
    }
}
