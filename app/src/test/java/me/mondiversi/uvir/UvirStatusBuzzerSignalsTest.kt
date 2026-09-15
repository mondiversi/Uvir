package me.mondiversi.uvir

import org.junit.Assert.*
import org.junit.Test

class UvirStatusBuzzerSignalsTest {
    @Test fun acquisitionHasOneDotAndAlertHasThreeDotsImmediatelyAfterIt() {
        assertEquals(6, statusBuzzerSignals.size)
        assertEquals(R.string.sensor_buzzer_signal_saved, statusBuzzerSignals[4].title)
        assertEquals(StatusBuzzerGraphic.SHORT_BEEP, statusBuzzerSignals[4].graphic)
        assertEquals(R.string.sensor_buzzer_signal_alert_saved, statusBuzzerSignals[5].title)
        assertEquals(StatusBuzzerGraphic.TRIPLE_BEEP, statusBuzzerSignals[5].graphic)
    }

    @Test fun allSixSignalsMatchTheFirmwareSelfTestTimeline() {
        assertEquals(listOf(280L, 280L, 340L, 340L, 90L, 290L),
            statusBuzzerSignals.map { it.testDurationMs })
        assertEquals(5620L, statusBuzzerSignals.sumOf { it.testDurationMs } + 5 * 800L)
    }

    @Test fun oldFirmwareCannotRunTheSixSignalTestWithOnlyFivePhysicalSignals() {
        assertFalse(firmwareSupportsStatusBuzzerTest("0.5.35"))
        assertFalse(firmwareSupportsStatusBuzzerTest("0.5.73"))
    }

    @Test fun currentAndFutureFirmwareSupportTheCompleteTest() {
        assertTrue(firmwareSupportsStatusBuzzerTest("0.5.74"))
        assertTrue(firmwareSupportsStatusBuzzerTest("0.5.74-debug"))
        assertTrue(firmwareSupportsStatusBuzzerTest("0.6.0"))
        assertFalse(firmwareSupportsStatusBuzzerTest(""))
    }
}
