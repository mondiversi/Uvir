package me.mondiversi.uvir

import kotlinx.coroutines.*
import kotlin.coroutines.CoroutineContext
import org.junit.Assert.*
import org.junit.Test

class UvirSettingsAutoSaveTest {
    private class Dispatcher : CoroutineDispatcher() {
        val tasks = ArrayDeque<Runnable>()
        override fun dispatch(context: CoroutineContext, block: Runnable) { tasks.add(block) }
        fun drain() { while (tasks.isNotEmpty()) tasks.removeFirst().run() }
    }

    @Test fun openingAndReadbackNeverTriggerSaving() {
        val dispatcher = Dispatcher()
        val queue = UvirSettingsSaveQueue(CoroutineScope(Job() + dispatcher), {})
        var calls = 0
        queue.save = { calls++ }
        dispatcher.drain()
        assertEquals(0, calls)
    }

    @Test fun changesAreCoalescedAndReadAtCommitTime() {
        val dispatcher = Dispatcher()
        val busy = mutableListOf<Boolean>()
        val queue = UvirSettingsSaveQueue(CoroutineScope(Job() + dispatcher), { busy.add(it) })
        var value = 10
        val values = mutableListOf<Int>()
        queue.save = { values.add(value) }
        repeat(100) { value = it; queue.request(SettingsSaveGroup.PARAMETERS) }
        dispatcher.drain()
        assertEquals(listOf(99), values)
        assertEquals(listOf(true, false), busy)
    }

    @Test fun changesDuringAnInFlightSaveAreNotDropped() {
        val dispatcher = Dispatcher()
        val queue = UvirSettingsSaveQueue(CoroutineScope(Job() + dispatcher), {})
        val gate = CompletableDeferred<Unit>()
        val calls = mutableListOf<SettingsSaveGroup>()
        queue.save = {
            calls.add(it)
            if (calls.size == 1) gate.await()
        }
        queue.request(SettingsSaveGroup.PARAMETERS)
        dispatcher.drain()
        queue.request(SettingsSaveGroup.PARAMETERS)
        queue.request(SettingsSaveGroup.LANGUAGE)
        dispatcher.drain()
        assertEquals(1, calls.size)
        gate.complete(Unit)
        dispatcher.drain()
        assertEquals(listOf(SettingsSaveGroup.PARAMETERS, SettingsSaveGroup.PARAMETERS,
            SettingsSaveGroup.LANGUAGE), calls)
    }

    @Test fun changingFocusCommitsTheOldEditorExactlyOnce() {
        val dispatcher = Dispatcher()
        val queue = UvirSettingsSaveQueue(CoroutineScope(Job() + dispatcher), {})
        val first = Any()
        val second = Any()
        var calls = 0
        queue.editing(first) { calls++ }
        queue.editing(second) { calls++ }
        queue.finishEditing(first)
        assertEquals(1, calls)
        queue.finishEditing()
        queue.finishEditing(second)
        assertEquals(2, calls)
    }

    @Test fun cancellationDropsTheOldSensorQueueWithoutFinishingItsEditor() {
        val dispatcher = Dispatcher()
        val queue = UvirSettingsSaveQueue(CoroutineScope(Job() + dispatcher), {})
        var calls = 0
        queue.save = { calls++ }
        queue.editing(Any()) { calls++ }
        queue.request(SettingsSaveGroup.PARAMETERS)
        queue.cancel()
        dispatcher.drain()
        queue.finishEditing()
        assertEquals(0, calls)
    }

    @Test fun oneFailedGroupDoesNotLoseTheFollowingGroups() {
        val dispatcher = Dispatcher()
        var failures = 0
        val queue = UvirSettingsSaveQueue(CoroutineScope(Job() + dispatcher), {}, { failures++ })
        val calls = mutableListOf<SettingsSaveGroup>()
        queue.save = { if (it == SettingsSaveGroup.PARAMETERS) error("failure") else calls.add(it) }
        queue.request(SettingsSaveGroup.PARAMETERS)
        queue.request(SettingsSaveGroup.NUMERIC_FORMAT)
        dispatcher.drain()
        assertEquals(1, failures)
        assertEquals(listOf(SettingsSaveGroup.NUMERIC_FORMAT), calls)
    }

    @Test fun anUncommittedTextEditIsNotIncludedInAnOlderQueuedSave() {
        val dispatcher = Dispatcher()
        val queue = UvirSettingsSaveQueue(CoroutineScope(Job() + dispatcher), {})
        var text = "5"
        val values = mutableListOf<String>()
        queue.prepare = { val committed = text; suspend { values.add(committed) } }
        queue.request(SettingsSaveGroup.SAMPLING)
        text = "99"
        dispatcher.drain()
        assertEquals(listOf("5"), values)
    }

    @Test fun aLateNormalizationDoesNotOverwriteNewTyping() {
        var text = "-1"
        val correction = settingsTextCorrection(text, { text }) { text = it }
        text = "12"
        correction("0")
        assertEquals("12", text)
    }

    @Test fun pendingEditsAreNotOverwrittenByReadbackOfThePreviousSave() {
        assertEquals(70, refreshUneditedSetting(70, 10, 30))
        assertEquals(30, refreshUneditedSetting(10, 10, 30))
        assertEquals("new note", refreshUneditedSetting("new note", "old", "received"))
    }

    @Test fun languageIsCommittedAfterTheOtherPendingEdits() {
        val dispatcher = Dispatcher()
        val queue = UvirSettingsSaveQueue(CoroutineScope(Job() + dispatcher), {})
        val calls = mutableListOf<SettingsSaveGroup>()
        queue.save = { calls.add(it) }
        queue.request(SettingsSaveGroup.LANGUAGE)
        queue.request(SettingsSaveGroup.PARAMETERS)
        dispatcher.drain()
        assertEquals(listOf(SettingsSaveGroup.PARAMETERS, SettingsSaveGroup.LANGUAGE), calls)
    }

    @Test fun phoneSoundAndVolumeDoNotChangeSensorConfiguration() {
        val original = ThresholdAlertSettings(false, emptyList(), 30, ThresholdAlertSound.TRIPLE_BEEP, 10)
        assertFalse(original.copy(sound = ThresholdAlertSound.SILENT, volume = 50)
            .firmwareConfigurationDiffersFrom(original))
        assertTrue(original.copy(repeatSeconds = original.repeatSeconds + 1)
            .firmwareConfigurationDiffersFrom(original))
    }
}
