package me.mondiversi.uvir

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class UvirDarkOffsetsTest {
    private fun frame(command: String, group: CalibrationGroup = CalibrationGroup.UV, id: String = "A") =
        DarkOffsetFrame(command.substringAfterLast(' '), id, group.protocolName, "ready",
            samples = 30, unit = "uW/cm2", values = group.indices.map { it.toFloat() + 1 })

    @Test fun onlyTheRequestedGroupChangesAndWindowIsNeverReset() {
        val original = SensorCalibrationSettings(2f, 3f,
            OpticalWindowSettings(true, List(11) { 1.5f }), CalibrationOffsets(List(11) { 5f }))
        for (group in CalibrationGroup.entries) {
            val result = original.resetGroup(group)
            assertEquals(original.opticalWindow, result.opticalWindow)
            for (i in 0..10) {
                assertEquals(if (i in group.indices) 0f else 5f, result.offsets!!.values[i], 0f)
                assertEquals(if (i in group.indices) 1f else if (i < 3) 3f else 2f,
                    result.channelFactors!!.values[i], 0f)
            }
        }
    }

    @Test fun resettingUvPreservesLegacyVisibleOffsetsLosslessly() {
        val original = SensorCalibrationSettings(offsets = CalibrationOffsets(List(15) { it.toFloat() }))
        val uv = original.resetGroup(CalibrationGroup.UV)
        assertEquals(15, uv.offsets!!.values.size)
        assertEquals(original.offsets!!.values.drop(3), uv.offsets.values.drop(3))
        assertEquals(listOf(0f, 0f, 0f), uv.offsets.values.take(3))
        val visible = original.resetGroup(CalibrationGroup.VISIBLE)
        assertEquals(original.offsets.values.take(3), visible.offsets!!.values.take(3))
        assertTrue(visible.offsets.values.drop(3).all { it == 0f })
    }

    @Test fun applyingAProposalDoesNotTouchGainsOrTheOtherOffsets() {
        val original = SensorCalibrationSettings(2f, 3f, offsets = CalibrationOffsets(List(11) { 5f }))
        val reading = DarkOffsetReading(CalibrationGroup.UV, listOf(.1f, .2f, .3f), 30)
        val changed = original.copy(offsets = original.offsets!!.withDarkReading(reading))
        assertEquals(original.copy(offsets = changed.offsets), changed)
        assertEquals(original.offsets.values.drop(3), changed.offsets!!.values.drop(3))
        assertEquals(reading.values, changed.offsets.values.take(3))
    }

    @Test fun proposalValidationRejectsShortOrInvalidData() {
        for (values in listOf(listOf(1f), listOf(-1f, 0f, 0f), listOf(Float.NaN, 0f, 0f))) {
            assertTrue(runCatching { DarkOffsetReading(CalibrationGroup.UV, values, 30) }.isFailure)
        }
        assertTrue(runCatching { DarkOffsetReading(CalibrationGroup.UV, List(3) { 0f }, 19) }.isFailure)
    }

    @Test fun clientIgnoresWrongSensorGroupAndTokenWithoutSavingAnything() = runBlocking {
        val client = UvirDarkOffsetClient()
        val commands = mutableListOf<String>()
        val result = client.measure("A", CalibrationGroup.UV, { true }, { command ->
            commands += command
            client.receive(frame(command, id = "B"))
            client.receive(frame(command, CalibrationGroup.VISIBLE))
            client.receive(frame(command).copy(requestId = "old"))
            client.receive(frame(command))
            true
        }, {})
        assertEquals(listOf(.01f, .02f, .03f), result.values)
        assertEquals(1, commands.size)
        assertTrue(commands.single().matches(Regex("DARK_OFFSETS START UV [0-9A-F]{32}")))
    }

    @Test fun cancellationSendsCancelToTheSameRequestAndLateRepliesCannotCompleteNextOne() = runBlocking {
        val client = UvirDarkOffsetClient()
        val started = CompletableDeferred<String>()
        val commands = mutableListOf<String>()
        val pending = async {
            client.measure("A", CalibrationGroup.UV, { true }, { command ->
                synchronized(commands) { commands += command }
                started.complete(command)
                true
            }, {})
        }
        val oldCommand = started.await()
        pending.cancelAndJoin()
        assertEquals("DARK_OFFSETS CANCEL ${oldCommand.substringAfterLast(' ')}", commands.last())
        val next = client.measure("A", CalibrationGroup.UV, { true }, { command ->
            client.receive(frame(oldCommand))
            client.receive(frame(command))
            true
        }, {})
        assertEquals(30, next.samples)
    }

    @Test fun reconnectDuringReceiptInvalidatesTheResultAndNeverCancelsAnotherTransport() = runBlocking {
        var valid = true
        val commands = mutableListOf<String>()
        val client = UvirDarkOffsetClient()
        val failed = runCatching {
            client.measure("A", CalibrationGroup.UV, { valid }, { command ->
                commands += command
                client.receive(frame(command))
                valid = false
                true
            }, {})
        }
        assertTrue(failed.isFailure)
        assertEquals(1, commands.size)
    }

    @Test fun allTwentyLocalesHaveTheSameKeysAndFormattingArguments() {
        val root = listOf(File("src/main/res"), File("app/src/main/res")).first { it.isDirectory }
        val files = root.listFiles()!!.map { File(it, "dark_calibration.xml") }.filter { it.isFile }
        assertEquals(20, files.size)
        val parser = DocumentBuilderFactory.newInstance().newDocumentBuilder()
        fun read(file: File): Map<String, String> {
            val nodes = parser.parse(file).getElementsByTagName("string")
            return (0 until nodes.length).associate { i ->
                nodes.item(i).attributes.getNamedItem("name").nodeValue to nodes.item(i).textContent
            }
        }
        val base = read(File(root, "values/dark_calibration.xml"))
        assertEquals("Calculate offsets", base.getValue("dark_offsets_calculate"))
        assertEquals("Calcola offset", read(File(root, "values-it/dark_calibration.xml")).getValue("dark_offsets_calculate"))
        for (file in files) {
            val localized = read(file)
            assertEquals(file.path, base.keys, localized.keys)
            for (key in base.keys) {
                assertTrue(file.path, localized.getValue(key).isNotBlank())
                assertEquals(file.path, Regex("%[0-9]+\\\$[sd]").findAll(base.getValue(key)).map { it.value }.toList(),
                    Regex("%[0-9]+\\\$[sd]").findAll(localized.getValue(key)).map { it.value }.toList())
            }
        }
    }
}
