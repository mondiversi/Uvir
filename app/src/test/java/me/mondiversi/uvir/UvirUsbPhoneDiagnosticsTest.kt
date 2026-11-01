package me.mondiversi.uvir

import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test

class UvirUsbPhoneDiagnosticsTest {
    @Test fun partialOrFailedHeadCanBeDiagnosedWithoutClaimingItIsReadyForMeasurements() {
        val partial=UsbPhoneState(status=UsbPhoneStatus.ERROR,adapterName="head",usbPermission=true,uvAvailable=true)
        assertTrue(usbHeadDiagnosticAvailable(partial))
        assertFalse(usbPhoneDiagnosticAvailable(partial));assertFalse(partial.ready)
        for(changed in listOf(partial.copy(adapterName=""),partial.copy(usbPermission=false),partial.copy(simulation=true),
            partial.copy(diagnosticRunning=true),partial.copy(measuringReference=true),partial.copy(status=UsbPhoneStatus.INITIALIZING)))
            assertFalse(usbHeadDiagnosticAvailable(changed))
    }
    private class Fixture {
        var epoch = 7L
        val state = MutableStateFlow(UsbPhoneState(status = UsbPhoneStatus.READY,
            identity = "FT232H:DIAG", uvAvailable = true, visibleAvailable = true,
            sample = SensorSample(uva = 3.0), automaticSession = 41, alertSession = 42))
        val frames = MutableSharedFlow<Pair<Long, DoubleArray>>()
        suspend fun probe(timeoutMs: Long = 2_000) = probeUsbPhoneDiagnostic(
            "FT232H:DIAG", state, frames, { epoch }, timeoutMs)
        suspend fun subscribed() = withTimeout(2_000) { frames.subscriptionCount.first { it > 0 } }
        suspend fun fresh() = frames.emit(epoch to DoubleArray(11) { .001 })
    }

    @Test fun cachedDisplayAndPreviousFramesNeverPassButFreshPairDoes() = runBlocking {
        val f = Fixture(); val before = f.state.value
        f.fresh() // No replay: this older frame must not complete a later diagnostic.
        val pending = async { f.probe() }; f.subscribed()
        assertFalse(pending.isCompleted)
        f.fresh()
        val result = pending.await()
        assertEquals(UvirDiagnosticOutcome.SUCCESS, result.outcome)
        assertTrue(requireNotNull(result.responseMs) >= 0)
        assertTrue(requireNotNull(result.info).diagnosticHardwareHealthy())
        assertEquals(before, f.state.value) // Includes ongoing sessions, sample and phone configuration.
        assertEquals(0, f.frames.subscriptionCount.value)
    }

    @Test fun staleAttachmentAndInvalidFramesCannotPassWhileGenuineZerosCan() = runBlocking {
        val f = Fixture(); val pending = async { f.probe() }; f.subscribed()
        f.frames.emit((f.epoch - 1) to DoubleArray(11))
        f.frames.emit(f.epoch to DoubleArray(10))
        f.frames.emit(f.epoch to DoubleArray(11) { Double.NaN })
        f.frames.emit(f.epoch to DoubleArray(11) { -1.0 })
        assertFalse(pending.isCompleted)
        f.frames.emit(f.epoch to DoubleArray(11))
        assertEquals(UvirDiagnosticOutcome.SUCCESS, pending.await().outcome)
    }

    @Test fun unpluggingStopsTheProbeWithoutWaitingForTimeout() = runBlocking {
        val f = Fixture(); val pending = async { f.probe() }; f.subscribed()
        f.state.value = f.state.value.copy(status = UsbPhoneStatus.DETACHED, identity = "")
        assertEquals(UvirDiagnosticOutcome.CONNECTION_CHANGED, pending.await().outcome)
        assertEquals(0, f.frames.subscriptionCount.value)
    }

    @Test fun reconnectingTheSameSerialCannotCompleteAnOlderProbe() = runBlocking {
        val f = Fixture(); val pending = async { f.probe() }; f.subscribed()
        f.epoch++
        f.state.value = f.state.value.copy(sequence = 1)
        assertEquals(UvirDiagnosticOutcome.CONNECTION_CHANGED, pending.await().outcome)
    }

    @Test fun simulationMissingModuleAndWrongIdentityCannotStartAProbe() = runBlocking {
        for (change in listOf<(UsbPhoneState) -> UsbPhoneState>(
            { it.copy(simulation = true) }, { it.copy(uvAvailable = false) },
            { it.copy(visibleAvailable = false) }, { it.copy(identity = "OTHER") },
            { it.copy(status = UsbPhoneStatus.DETACHED) })) {
            val f = Fixture(); f.state.value = change(f.state.value)
            assertEquals(UvirDiagnosticOutcome.CONNECTION_CHANGED, f.probe().outcome)
            assertEquals(0, f.frames.subscriptionCount.value)
        }
    }

    @Test fun noFreshReadingTimesOutInsteadOfReusingTheDisplayedSample() = runBlocking {
        val f = Fixture(); val before = f.state.value
        assertEquals(UvirDiagnosticOutcome.TIMEOUT, f.probe(30).outcome)
        assertEquals(before, f.state.value)
        assertEquals(0, f.frames.subscriptionCount.value)
    }

    @Test fun cancellationRemovesObserversAndDoesNotChangeSessionsOrSettings() = runBlocking {
        val f = Fixture(); val before = f.state.value
        val pending = async { f.probe() }; f.subscribed(); pending.cancelAndJoin()
        assertEquals(0, f.frames.subscriptionCount.value)
        assertEquals(before, f.state.value)
    }

    @Test fun usbHealthRequiresBothFreshOpticalReadsAndNeverInventsLegacyPeripherals() = runBlocking {
        val f = Fixture()
        val initial = usbPhoneDiagnosticInfo(f.state.value)
        assertFalse(initial.diagnosticHardwareHealthy())
        val fresh = usbPhoneDiagnosticInfo(f.state.value, true)
        assertTrue(fresh.diagnosticHardwareHealthy())
        assertFalse(fresh.copy(uvSensorReadOk = false).diagnosticHardwareHealthy())
        assertFalse(fresh.copy(appConnected = false).diagnosticHardwareHealthy())
        assertFalse(fresh.copy(protocol = "").diagnosticHardwareHealthy())
        assertNull(fresh.rtcReadOk); assertNull(fresh.sdReadWriteOk)
        assertEquals("", fresh.firmwareVersion)
        val report = runUvirSensorDiagnostics("Uvir", SensorConnectionMode.USB, initial, {},
            probe = { UvirDiagnosticProbe(UvirDiagnosticOutcome.SUCCESS, 1.0, fresh) }, spacingMs = 0)
        assertTrue(report.successful)
    }
}
