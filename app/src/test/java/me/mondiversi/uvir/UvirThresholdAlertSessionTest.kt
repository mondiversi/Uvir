package me.mondiversi.uvir

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UvirThresholdAlertSessionTest {
    @Test
    fun idleSensorKeepsTheLastAlertNoteWithoutReadingASession() {
        assertEquals(
            "Balcony alert",
            restoreAlertSessionNote(
                activeSessionId = 0L,
                currentNote = "Balcony alert",
                readSessionNote = { error("An idle sensor has no session to read") }
            )
        )
    }

    @Test
    fun runningSensorRestoresOnlyItsOwnAlertSessionNote() {
        var readId = 0L
        val note = restoreAlertSessionNote(
            activeSessionId = 8L,
            currentNote = "Another alert session",
            readSessionNote = { sessionId ->
                readId = sessionId
                "Session eight"
            }
        )
        assertEquals(8L, readId)
        assertEquals("Session eight", note)
    }

    @Test
    fun runningSessionWithoutANoteDoesNotBorrowThePreviousNote() {
        assertEquals(
            "",
            restoreAlertSessionNote(
                activeSessionId = 8L,
                currentNote = "Previous session note",
                readSessionNote = { "" }
            )
        )
    }

    @Test
    fun addingAnotherRuleKeepsTheCurrentSession() {
        val previous = settings(ThresholdAlertMetric.UVA)
        val next =
            settings(
                ThresholdAlertMetric.UVA,
                ThresholdAlertMetric.BLUE
            )

        assertFalse(
            shouldStartNewThresholdAlertSession(
                previous = previous,
                next = next,
                currentSessionId = 8L
            )
        )
    }

    @Test
    fun restartingAfterAllRulesWereDisabledCreatesANewSession() {
        assertTrue(
            shouldStartNewThresholdAlertSession(
                previous = settings(monitoring = false),
                next = settings(ThresholdAlertMetric.UVA),
                currentSessionId = 0L
            )
        )
    }

    @Test
    fun savingConfiguredRulesDoesNotStartMonitoring() {
        val configured =
            settings(
                ThresholdAlertMetric.UVA,
                monitoring = false
            )

        assertTrue(configured.hasConfiguredRules())
        assertFalse(configured.hasActiveMonitoring())
        assertFalse(
            shouldStartNewThresholdAlertSession(
                previous = settings(monitoring = false),
                next = configured,
                currentSessionId = 0L
            )
        )
    }

    @Test
    fun stoppingMonitoringKeepsConfiguredThresholds() {
        val original =
            ThresholdAlertSettings(
                enabled = true,
                rules =
                    listOf(
                        ThresholdAlertRule(
                            metric = ThresholdAlertMetric.UVA,
                            enabled = true,
                            direction = ThresholdAlertDirection.BELOW,
                            threshold = 12.5f
                        )
                    ),
                repeatSeconds = 45,
                sound = ThresholdAlertSound.SILENT,
                volume = 10
            )

        val stopped = original.withMonitoringStopped()

        assertFalse(stopped.enabled)
        assertTrue(stopped.rules.single().enabled)
        assertEquals(
            ThresholdAlertDirection.BELOW,
            stopped.rules.single().direction
        )
        assertEquals(12.5f, stopped.rules.single().threshold)
        assertEquals(45, stopped.repeatSeconds)
    }

    private fun settings(
        vararg activeMetrics: ThresholdAlertMetric,
        monitoring: Boolean = true
    ): ThresholdAlertSettings =
        ThresholdAlertSettings(
            enabled = monitoring && activeMetrics.isNotEmpty(),
            rules =
                activeMetrics.map { metric ->
                    ThresholdAlertRule(
                        metric = metric,
                        enabled = true,
                        direction = ThresholdAlertDirection.ABOVE,
                        threshold = 1f
                    )
                },
            repeatSeconds = 30,
            sound = ThresholdAlertSound.SILENT,
            volume = 10
        )
}
