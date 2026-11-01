package me.mondiversi.uvir

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import me.mondiversi.uvir.usb.*
import org.junit.Assert.*
import org.junit.Test

class UvirUsbPhoneReferencesTest {
    @Test fun recordedLampTransitionsDoNotExpireValidDataOrRestartTheAverage() {
        val window = PhoneSamplingWindow()
        var now = 0L
        var state = UsbPhoneState(status = UsbPhoneStatus.READY)
        repeat(160) { cycle ->
            val started = now + 1
            state = state.beginOpticalCapture(started)
            val duration = if (cycle % 20 == 19) 3247L else 199L
            val midpoint = started + duration / 2
            if (cycle >= 5) assertTrue("No flicker while a valid gain adaptation is in flight",
                state.expireOpticalReading(midpoint).ready)
            now = started + duration
            val rows = window.add(DoubleArray(11) { cycle.toDouble() }, 1, 5, now, 699, started)
            if (rows != null) state = state.copy(sample = processPhoneMeasurement(rows, PhoneCalibration()),
                sampleAtMs = now, freshnessMs = 699, captureDeadlineMs = 0)
            if (cycle >= 4) assertTrue(state.expireOpticalReading(now).ready)
        }
    }

    @Test fun captureGraceCannotExtendAnExpiredOrFailedReadingForever() {
        val base = UsbPhoneState(status = UsbPhoneStatus.READY, sample = SensorSample(), sampleAtMs = 0, freshnessMs = 500)
        val pending = base.beginOpticalCapture(100)
        assertTrue(pending.expireOpticalReading(3300).ready)
        assertFalse(pending.expireOpticalReading(10101).ready)
        assertEquals(0L, base.beginOpticalCapture(501).captureDeadlineMs)
        assertFalse(base.beginOpticalCapture(501).expireOpticalReading(501).ready)
        assertEquals(0L, base.copy(error = "invalid conversion").beginOpticalCapture(100).captureDeadlineMs)
        val failed = pending.copy(captureDeadlineMs = 0, error = "invalid conversion").expireOpticalReading(3300)
        assertFalse(failed.ready)
        assertEquals(0L, failed.beginOpticalCapture(3301).captureDeadlineMs)
    }
    @Test fun oldLiveDataExpiresWithoutDisconnectingOrEndingPhoneSessions() {
        val state = UsbPhoneState(status = UsbPhoneStatus.READY, sample = SensorSample(), sampleAtMs = 100,
            freshnessMs = 500, automaticSession = 7, alertSession = 8)
        assertTrue(state.expireOpticalReading(600).ready)
        val expired = state.expireOpticalReading(601)
        assertFalse(expired.ready); assertNull(expired.sample)
        assertEquals(UsbPhoneStatus.READY, expired.status)
        assertEquals(7L, expired.automaticSession); assertEquals(8L, expired.alertSession)
        assertTrue(state.copy(simulation = true).expireOpticalReading(90000).ready)
    }

    private fun frame(at: Long, generation: Long = 1, revision: Long = 2, uv: Boolean = true) =
        PhoneOpticalFrame(generation, revision, at,
            if (uv) RawChannels(DoubleArray(3) { 1.0 }, 64.0) else null,
            RawChannels(DoubleArray(8) { 2.0 }, 256.0))

    @Test fun referencesCoverThirtySecondsOfFreshFramesWithIndependentGroups() = runBlocking {
        val progress = mutableListOf<Int>()
        val readings = collectPhoneOpticalReference(flow {
            repeat(32) { emit(frame(1000 + it * 1000L, uv = false)) }
        }, 1, 2, { true }, { progress.add(it) })
        assertEquals(31, readings.size)
        assertEquals(0, progress.first()); assertEquals(30000, progress.last())
        assertTrue(readings.all { it.referenceValues().take(3).all(Double::isNaN) })
        assertEquals(2.0, readings.map { it.referenceValues()[3] }.average(), 0.0)
    }

    @Test fun interruptedIntervalsOldAttachmentsAndSettingsCannotProduceAReference() = runBlocking {
        for (frames in listOf(flowOf(frame(0)), flowOf(frame(0, generation = 9)),
            flowOf(frame(0, revision = 3)), flowOf(frame(10), frame(0)))) {
            assertTrue(runCatching { collectPhoneOpticalReference(frames, 1, 2, { true }, {}) }.isFailure)
        }
        assertTrue(runCatching { collectPhoneOpticalReference(flowOf(frame(0)), 1, 2, { false }, {}) }.isFailure)
    }

    @Test fun cancellationReleasesTheObserverWithoutLeavingAReferenceSubscription() = runBlocking {
        val frames = MutableSharedFlow<PhoneOpticalFrame>()
        val job = launch { collectPhoneOpticalReference(frames, 1, 2, { true }, {}) }
        frames.subscriptionCount.first { it > 0 }
        job.cancelAndJoin()
        assertEquals(0, frames.subscriptionCount.value)
    }

    @Test fun phoneReferencesUseTheSameNoiseDriftAndSignalChecksAsTheWindowWizard() {
        val stable = opticalWindowChannelStatistics(List(30) { 1.0 })
        assertNull(stable.issue); assertEquals(30, stable.samples)
        assertEquals(OpticalWindowChannelIssue.UNSTABLE,
            opticalWindowChannelStatistics(List(30) { if (it % 2 == 0) 1.0 else 2.0 }).issue)
        assertEquals(OpticalWindowChannelIssue.UNSTABLE,
            opticalWindowChannelStatistics(List(30) { if (it < 15) .96 else 1.04 }).issue)
        assertEquals(OpticalWindowChannelIssue.INSUFFICIENT, opticalWindowChannelStatistics(List(30) { 0.0 }).issue)
        assertEquals(OpticalWindowChannelIssue.INSUFFICIENT, opticalWindowChannelStatistics(List(19) { 1.0 }).issue)
        assertEquals(OpticalWindowChannelIssue.SATURATED,
            opticalWindowChannelStatistics(List(30) { 1.0 }, saturated = true).issue)
        assertEquals(OpticalWindowChannelIssue.UNAVAILABLE,
            opticalWindowChannelStatistics(emptyList(), available = false).issue)
    }
}
