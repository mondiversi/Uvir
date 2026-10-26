package me.mondiversi.uvir

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test

class UvirOpticalWindowMeasurementTest {
    private val window = OpticalWindowSettings(true, List(11) { 2f }).withMasterFactor(1.5f)
    private fun context(value: OpticalWindowSettings = window) = OpticalWindowMeasurementContext(
        "A", SensorConnectionMode.WIFI, 1, "0.5.112", 10, 1.0, 1.0,
        CalibrationOffsets(), null, value, 5, 150L, true, true, true)
    private fun sample(v: Double, value: OpticalWindowSettings = window, flags: Int = 0) =
        SensorSample(v, v, v, v, v, v, v, v, v, v, v, flags, value)
    private fun reading(v: Double, value: OpticalWindowSettings = window,
        transform: (Int, SensorSample) -> SensorSample = { _, s -> s }): OpticalWindowReading {
        val accumulator = OpticalWindowMeasurementAccumulator(context(value))
        repeat(30) { accumulator.add(it.toLong(), transform(it, sample(v, value))) }
        return accumulator.finish()
    }

    @Test fun comparativeFactorsRemoveOldWindowCorrectionsAndReplaceChannelsWithoutChangingMaster() {
        val before = reading(3.0)
        val after = reading(2.4)
        assertEquals(1.0, before.channels[0].mean, 1e-9)
        assertEquals(.8, after.channels[0].mean, 1e-9)
        val proposal = opticalWindowProposal(before, after)
        assertEquals(11, proposal.validChannels)
        val applied = proposal.applyTo(window)
        assertEquals(List(11) { 1.25f }, applied.factors)
        assertEquals(window.masterFactor, applied.masterFactor)
        assertEquals(List(11) { 1.875f }, applied.effectiveFactors)
        assertEquals(List(11) { 2f }, window.factors)
    }

    @Test fun unusableChannelsArePreservedAndNeverGetInfinityOrClampedFactors() {
        val before = reading(3.0) { _, s -> s.copy(uvc = 0.0) }
        val after = reading(2.4) { _, s -> s.copy(uvc = 0.0, uvb = 6.0, uva = .03) }
        val proposal = opticalWindowProposal(before, after)
        assertEquals(OpticalWindowChannelIssue.INSUFFICIENT, proposal.channels[0].issue)
        assertEquals(OpticalWindowChannelIssue.BRIGHTER, proposal.channels[1].issue)
        assertEquals(OpticalWindowChannelIssue.OUT_OF_RANGE, proposal.channels[2].issue)
        assertEquals(8, proposal.validChannels)
        assertEquals(List(3) { 2f } + List(8) { 1.25f }, proposal.applyTo(window).factors)
        assertEquals(window.masterMultiplier, proposal.applyTo(window).masterMultiplier, 0f)
    }

    @Test fun driftAndNoiseFailStabilityEvenIfTheMeanLooksReasonable() {
        val noisy = reading(3.0) { i, s -> s.copy(uvc = if (i % 2 == 0) 2.0 else 4.0,
            uvb = if (i < 15) 2.8 else 3.2) }
        assertEquals(OpticalWindowChannelIssue.UNSTABLE, noisy.channels[0].issue)
        assertEquals(OpticalWindowChannelIssue.UNSTABLE, noisy.channels[1].issue)
        assertNull(noisy.channels[2].issue)
    }

    @Test fun saturationAffectsOnlyTheRelevantGroupAndMissingUvDoesNotBlockVisible() {
        val saturated = reading(3.0) { i, s -> if (i == 2) s.copy(qualityFlags = UVIR_QUALITY_UV_OUT_OF_RANGE) else s }
        assertTrue(saturated.channels.take(3).all { it.issue == OpticalWindowChannelIssue.SATURATED })
        assertTrue(saturated.channels.drop(3).all { it.issue == null })
        val accumulator = OpticalWindowMeasurementAccumulator(context().copy(uvAvailable = false))
        repeat(30) { accumulator.add(it.toLong(), sample(3.0)) }
        assertTrue(accumulator.finish().channels.take(3).all { it.issue == OpticalWindowChannelIssue.UNAVAILABLE })
        assertTrue(accumulator.finish().channels.drop(3).all { it.issue == null })
    }

    @Test fun duplicatesDoNotCountAndRestartedSequenceOrChangedOpticsAreRejected() {
        val accumulator = OpticalWindowMeasurementAccumulator(context())
        repeat(30) { accumulator.add(1, sample(3.0)) }
        assertEquals(1, accumulator.finish().channels[0].samples)
        assertEquals(OpticalWindowChannelIssue.INSUFFICIENT, accumulator.finish().channels[0].issue)
        assertTrue(runCatching { accumulator.add(0, sample(3.0)) }.isFailure)
        assertTrue(runCatching { accumulator.add(2, sample(3.0, window.withMasterFactor(2f))) }.isFailure)
    }

    @Test fun changedSettingsOrConnectionCannotBeAppliedEvenAfterAResultWasShown() {
        val before = reading(3.0)
        val after = reading(2.4)
        assertTrue(runCatching { opticalWindowProposal(before,
            after.copy(context = after.context.copy(generation = 2))) }.isFailure)
        val proposal = opticalWindowProposal(before, after)
        assertFalse(proposal.canApplyTo(window.withMasterFactor(2f)))
        assertTrue(runCatching { proposal.applyTo(window.withMasterFactor(2f)) }.isFailure)
        var connected = true
        val guarded = opticalWindowProposal(before.copy(isCurrent = { connected }), after)
        connected = false
        assertFalse(guarded.canApplyTo(window))
        assertTrue(runCatching { guarded.applyTo(window) }.isFailure)
    }

    @Test fun disabledLegacyFactorsAreNotUsedToUndoMeasurements() {
        val off = OpticalWindowSettings(false, List(11) { 2f }).withMasterFactor(1.5f)
        val result = opticalWindowProposal(reading(1.0, off), reading(.8, off)).applyTo(off)
        assertEquals(List(11) { 1.25f }, result.factors)
        assertEquals(1f, result.masterMultiplier, 0f)
    }

    @Test fun liveCollectorRequiresNewFramesAndReleasesItsSubscriptionOnCancellation() = runBlocking {
        val frame = OpticalWindowMeasurementFrame(true, false, context(), 0, sample(3.0))
        val frames = MutableStateFlow(frame)
        val producer = launch { repeat(60) { delay(2); frames.value = frame.copy(sequence = it + 1L) } }
        val result = measureOpticalWindowReading("A", null, frames, { frames.value }, {},
            durationMs = 250, staleMs = 2_000)
        producer.join()
        assertTrue(result.channels[0].samples >= 20)
        assertEquals(1.0, result.channels[0].mean, 1e-9)
        assertEquals(0, frames.subscriptionCount.value)
        val pending = launch { measureOpticalWindowReading("A", null, frames, { frames.value }, {}) }
        yield()
        pending.cancelAndJoin()
        assertEquals(0, frames.subscriptionCount.value)
    }

    @Test fun collectorRejectsAnotherSensorBusyStateAndReconnectBetweenPhases() = runBlocking {
        val frame = OpticalWindowMeasurementFrame(true, false, context(), 0, sample(3.0))
        for (invalid in listOf(frame.copy(busy = true), frame.copy(connected = false),
            frame.copy(context = context().copy(deviceId = "B")),
            frame.copy(context = context().copy(generation = 2)))) {
            val frames = MutableStateFlow(invalid)
            assertTrue(runCatching { measureOpticalWindowReading("A", context(), frames,
                { frames.value }, {}, durationMs = 1) }.isFailure)
            assertEquals(0, frames.subscriptionCount.value)
        }
        val stale = MutableStateFlow(frame)
        assertTrue(runCatching { measureOpticalWindowReading("A", null, stale,
            { stale.value }, {}, durationMs = 20, staleMs = 1) }.isFailure)
        assertEquals(0, stale.subscriptionCount.value)
    }

    @Test fun transportOrActivityChangesDuringTheIntervalAbortAndReleaseTheObserver() = runBlocking {
        supervisorScope {
            val frame = OpticalWindowMeasurementFrame(true, false, context(), 0, sample(3.0))
            for (changed in listOf(frame.copy(connected = false), frame.copy(busy = true),
                frame.copy(context = context().copy(generation = 2)),
                frame.copy(context = context().copy(settingsRevision = 11)))) {
                val frames = MutableStateFlow(frame)
                val pending = async { measureOpticalWindowReading("A", null, frames, { frames.value }, {}) }
                frames.subscriptionCount.first { it > 0 }
                frames.value = changed
                assertTrue(runCatching { pending.await() }.isFailure)
                assertEquals(0, frames.subscriptionCount.value)
            }
        }
    }

    @Test fun allTwentyTranslationsHaveMatchingKeysAndArguments() {
        val root = listOf(File("src/main/res"), File("app/src/main/res")).first { it.isDirectory }
        val files = root.listFiles()!!.map { File(it, "optical_window_measurement.xml") }.filter { it.isFile }
        assertEquals(20, files.size)
        val parser = DocumentBuilderFactory.newInstance().newDocumentBuilder()
        fun read(file: File): Map<String, String> {
            val nodes = parser.parse(file).getElementsByTagName("string")
            return (0 until nodes.length).associate { i ->
                nodes.item(i).attributes.getNamedItem("name").nodeValue to nodes.item(i).textContent }
        }
        val base = read(File(root, "values/optical_window_measurement.xml"))
        for (file in files) {
            val translated = read(file)
            assertEquals(file.path, base.keys, translated.keys)
            for ((key, value) in base) {
                assertTrue(file.path, translated.getValue(key).isNotBlank())
                val args = Regex("%[0-9]+\\\$[sd]")
                assertEquals(file.path, args.findAll(value).map { it.value }.toList(),
                    args.findAll(translated.getValue(key)).map { it.value }.toList())
            }
        }
    }
}
