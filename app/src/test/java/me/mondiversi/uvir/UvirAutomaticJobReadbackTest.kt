package me.mondiversi.uvir

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UvirAutomaticJobReadbackTest {
    private fun resolves(
        readback: UvirAutomaticJobReadback? = UvirAutomaticJobReadback("A", 0, false, 1),
        guard: UvirAutomaticJobReadbackGuard = UvirAutomaticJobReadbackGuard(),
        deviceId: String = "A",
        connected: Boolean = true,
        simulated: Boolean = false,
        active: Boolean = true,
        sessionId: Long = 12
    ) = shouldResolveMissingAutomaticJob(readback, guard, deviceId, connected, simulated, active, sessionId)

    @Test fun aFreshIdleSensorResolvesTheRememberedSession() {
        assertTrue(resolves())
        assertTrue(resolves(deviceId = " a "))
    }

    @Test fun disconnectionAndUnconfirmedConnectionsKeepTheRememberedSession() {
        assertFalse(resolves(connected = false))
        assertFalse(resolves(readback = null))
    }

    @Test fun anotherSensorCannotEndTheSelectedSensorsSession() {
        assertFalse(resolves(deviceId = "B"))
        assertFalse(resolves(deviceId = ""))
        assertFalse(resolves(readback = UvirAutomaticJobReadback("", 0, false, 1)))
    }

    @Test fun activeJobsAndOtherSessionIdsKeepTheExistingBehavior() {
        assertFalse(resolves(readback = UvirAutomaticJobReadback("A", 12, true, 1)))
        assertFalse(resolves(readback = UvirAutomaticJobReadback("A", 13, true, 1)))
        assertFalse(resolves(readback = UvirAutomaticJobReadback("A", 12, false, 1)))
        assertFalse(resolves(readback = UvirAutomaticJobReadback("A", 13, false, 1)))
    }

    @Test fun cachedReportsAndSimulatedOrIdleAppContextsAreNotClosed() {
        assertFalse(resolves(readback = UvirAutomaticJobReadback("A", 0, false, 0)))
        assertFalse(resolves(simulated = true))
        assertFalse(resolves(active = false))
        assertFalse(resolves(sessionId = 0))
        assertFalse(resolves(sessionId = -1))
    }

    @Test fun idleRepliesCannotCancelANewStartThatHasNotBeenConfirmed() {
        val starting = UvirAutomaticJobReadbackGuard().afterStart()
        assertFalse(resolves(guard = starting, readback = UvirAutomaticJobReadback("A", 0, false, starting.minimumRevision + 1)))
    }

    @Test fun afterActiveConfirmationOnlyNewerReportsCanResolveAMissingJob() {
        val confirmed = UvirAutomaticJobReadbackGuard().afterStart().afterActiveConfirmation()
        assertFalse(resolves(guard = confirmed, readback = UvirAutomaticJobReadback("A", 0, false, confirmed.minimumRevision)))
        assertTrue(resolves(guard = confirmed, readback = UvirAutomaticJobReadback("A", 0, false, confirmed.minimumRevision + 1)))
    }

    @Test fun reconnectingAfterAnUnconfirmedStartStillDetectsAPowerLoss() {
        val reconnecting = UvirAutomaticJobReadbackGuard().afterStart().afterDisconnection()
        assertFalse(resolves(guard = reconnecting, readback = UvirAutomaticJobReadback("A", 0, false, reconnecting.minimumRevision)))
        assertTrue(resolves(guard = reconnecting, readback = UvirAutomaticJobReadback("A", 0, false, reconnecting.minimumRevision + 1)))
    }
}
