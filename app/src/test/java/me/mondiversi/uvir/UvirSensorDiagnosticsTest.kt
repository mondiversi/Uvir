package me.mondiversi.uvir

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class UvirSensorDiagnosticsTest {
    private val info = UvirSensorRuntimeInfo(deviceId = "ABC123", firmwareVersion = "0.5.69")

    @Test fun correlatedReplyProvidesLatencyAndFreshInfo() = runBlocking {
        val client = UvirSensorDiagnosticClient()
        val result = client.request("abc123", { true }, { command ->
            assertTrue(command.matches(Regex("DIAGNOSTIC [0-9A-F]{32}")))
            client.receive(command.substringAfter(' '), info)
            true
        })
        assertEquals(UvirDiagnosticOutcome.SUCCESS, result.outcome)
        assertTrue(requireNotNull(result.responseMs) >= 0)
        assertEquals(info, result.info)
    }

    @Test fun unrelatedAndTimedOutRepliesCannotCompleteNextRequest() = runBlocking {
        val client = UvirSensorDiagnosticClient()
        var previousId = ""
        val first = client.request("ABC123", { true }, { command ->
            previousId = command.substringAfter(' ')
            client.receive("unrelated", info)
            true
        }, timeoutMs = 20L)
        assertEquals(UvirDiagnosticOutcome.TIMEOUT, first.outcome)
        val next = client.request("ABC123", { true }, { command ->
            client.receive(previousId, info)
            client.receive(command.substringAfter(' '), info)
            true
        })
        assertEquals(UvirDiagnosticOutcome.SUCCESS, next.outcome)
    }

    @Test fun replyFromDifferentSensorIsNotAccepted() = runBlocking {
        val client = UvirSensorDiagnosticClient()
        val result = client.request("ABC123", { true }, { command ->
            client.receive(command.substringAfter(' '), info.copy(deviceId = "OTHER"))
            true
        })
        assertEquals(UvirDiagnosticOutcome.CONNECTION_CHANGED, result.outcome)
        assertNull(result.responseMs)
        assertNull(result.info)
    }

    @Test fun disconnectedPeerDoesNotSendAnyCommand() = runBlocking {
        val result = UvirSensorDiagnosticClient().request("ABC123", { false }, {
            fail("Must not send to a disconnected peer")
            true
        })
        assertEquals(UvirDiagnosticOutcome.CONNECTION_CHANGED, result.outcome)
    }

    @Test fun failedWriteIsReportedWithoutWaiting() = runBlocking {
        val result = UvirSensorDiagnosticClient().request("ABC123", { true }, { false })
        assertEquals(UvirDiagnosticOutcome.WRITE_FAILED, result.outcome)
    }

    @Test fun connectionChangeAfterReplyInvalidatesTheResult() = runBlocking {
        val client = UvirSensorDiagnosticClient()
        var valid = true
        val result = client.request("ABC123", { valid }, { command ->
            client.receive(command.substringAfter(' '), info)
            valid = false
            true
        })
        assertEquals(UvirDiagnosticOutcome.CONNECTION_CHANGED, result.outcome)
    }

    @Test fun closingTheTestCleansUpPendingRequestAndUnlocksTheNextOne() = runBlocking {
        val client = UvirSensorDiagnosticClient()
        val sent = CompletableDeferred<String>()
        val pending = async {
            client.request("ABC123", { true }, { sent.complete(it); true })
        }
        val cancelledId = sent.await().substringAfter(' ')
        pending.cancelAndJoin()
        val next = client.request("ABC123", { true }, { command ->
            client.receive(cancelledId, info)
            client.receive(command.substringAfter(' '), info)
            true
        })
        assertEquals(UvirDiagnosticOutcome.SUCCESS, next.outcome)
    }

    @Test fun reportContainsSixProbesAndProgressDoesNotCreateRecords() = runBlocking {
        val progress = mutableListOf<Int>()
        val report = runUvirSensorDiagnostics(
            "Sensor", SensorConnectionMode.WIFI, info, progress::add,
            probe = { UvirDiagnosticProbe(UvirDiagnosticOutcome.SUCCESS, 10.0, info) },
            spacingMs = 0L
        )
        assertEquals((1..6).toList(), progress)
        assertEquals(6, report.responses)
        assertTrue(report.successful)
        assertEquals(10.0, report.responseTimes.average(), 0.0)
        assertEquals(info, report.latestInfo)
    }

    @Test fun reportStopsOnConnectionChangeAndPreservesPartialResults() = runBlocking {
        var count = 0
        val report = runUvirSensorDiagnostics(
            "Sensor", SensorConnectionMode.USB, info, {}, probe = {
                count++
                if (count == 1) UvirDiagnosticProbe(UvirDiagnosticOutcome.SUCCESS, 15.0, info)
                else UvirDiagnosticProbe(UvirDiagnosticOutcome.CONNECTION_CHANGED)
            }, spacingMs = 0L
        )
        assertEquals(2, report.probes.size)
        assertEquals(1, report.responses)
        assertFalse(report.successful)
        assertEquals(info, report.latestInfo)
    }

    @Test fun diagnosticFeatureRequiresItsOwnMinimumFirmware() {
        assertTrue(compareFirmwareVersions("0.5.68", UVIR_DIAGNOSTIC_MINIMUM_FIRMWARE) < 0)
        assertTrue(compareFirmwareVersions("0.5.69", UVIR_DIAGNOSTIC_MINIMUM_FIRMWARE) >= 0)
        assertTrue(compareFirmwareVersions("0.6.0", UVIR_DIAGNOSTIC_MINIMUM_FIRMWARE) >= 0)
    }
}
