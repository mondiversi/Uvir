package me.mondiversi.uvir

import org.junit.Assert.*
import org.junit.Test

class UvirStatusBuzzerSignalsTest {
    @Test fun acquisitionHasOneDotAndAlertHasThreeDotsImmediatelyAfterIt() {
        assertEquals(7, statusBuzzerSignals.size)
        assertEquals(R.string.sensor_buzzer_signal_saved, statusBuzzerSignals[4].title)
        assertEquals(StatusBuzzerGraphic.SHORT_BEEP, statusBuzzerSignals[4].graphic)
        assertEquals(R.string.sensor_buzzer_signal_alert_saved, statusBuzzerSignals[5].title)
        assertEquals(StatusBuzzerGraphic.TRIPLE_BEEP, statusBuzzerSignals[5].graphic)
        assertEquals(R.string.sensor_buzzer_signal_time_unavailable, statusBuzzerSignals[6].title)
        assertEquals(StatusBuzzerGraphic.LONG_BEEP, statusBuzzerSignals[6].graphic)
    }

    @Test fun allSevenSignalsMatchTheFirmwareSelfTestTimeline() {
        assertEquals(listOf(280L, 280L, 340L, 340L, 90L, 290L, 800L),
            statusBuzzerSignals.map { it.testDurationMs })
        assertEquals(7220L, statusBuzzerSignals.sumOf { it.testDurationMs } + 6 * 800L)
    }

    @Test fun oldFirmwareCannotRunTheSevenSignalTest() {
        assertFalse(firmwareSupportsStatusBuzzerTest("0.5.35"))
        assertFalse(firmwareSupportsStatusBuzzerTest("0.5.73"))
        assertFalse(firmwareSupportsStatusBuzzerTest("0.5.80"))
    }

    @Test fun currentAndFutureFirmwareSupportTheCompleteTest() {
        assertTrue(firmwareSupportsStatusBuzzerTest("0.5.81"))
        assertTrue(firmwareSupportsStatusBuzzerTest("0.5.81-debug"))
        assertTrue(firmwareSupportsStatusBuzzerTest("0.6.0"))
        assertFalse(firmwareSupportsStatusBuzzerTest(""))
    }
}
