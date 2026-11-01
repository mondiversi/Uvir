package me.mondiversi.uvir.usb

import java.io.IOException
import org.junit.Assert.*
import org.junit.Test

class Ft232hBatchReadTest {
    private class Io(val responses: ArrayDeque<ByteArray>) : MpsseIo {
        val writes = mutableListOf<ByteArray>()
        val readSizes = mutableListOf<Int>()
        var failOnData = false
        override fun write(bytes: ByteArray) { writes.add(bytes.copyOf()) }
        override fun readExactly(count: Int): ByteArray {
            readSizes.add(count)
            if (failOnData && readSizes.size > 3) throw IOException("Disconnected")
            return responses.removeFirst()
        }
        override fun close() {}
    }

    private fun io(data: ByteArray): Io = Io(ArrayDeque<ByteArray>().apply {
        repeat(3) { add(byteArrayOf(0)) } // Write address, register, repeated-start read address.
        data.toList().chunked(64).forEach { add(it.toByteArray()) }
    })

    /** Decode documented MPSSE commands into electrical operations, ignoring USB flushes. */
    private fun wire(commands: ByteArray): List<String> {
        val events = mutableListOf<String>()
        var i = 0
        fun byte() = commands[i++].toInt() and 255
        while (i < commands.size) when (val command = byte()) {
            0x80 -> events.add("gpio:${byte()}:${byte()}")
            0x20 -> {
                val count = byte() + (byte() shl 8) + 1
                repeat(count) { events.add("read8") }
            }
            0x13 -> {
                assertEquals("Exactly one ACK bit", 0, byte())
                events.add(if (byte() and 128 == 0) "ack" else "nack")
            }
            0x87 -> Unit
            else -> fail("Unexpected command 0x${command.toString(16)}")
        }
        return events
    }

    @Test fun visibleSnapshotUsesFourHostReadsAndSevenWritesInsteadOfFortyAndEighty() {
        val expected = ByteArray(37) { (it * 17).toByte() }
        val io = io(expected)
        assertArrayEquals(expected, Ft232hMpsse(io).transfer(0x39, byteArrayOf(0x94.toByte()), expected.size))
        assertEquals(listOf(1, 1, 1, 37), io.readSizes)
        assertEquals(7, io.writes.size)
    }

    @Test fun batchAndChunkBoundariesPreserveTheOriginalWireSequenceAndOneFinalNack() {
        for (count in listOf(1, 2, 6, 37, 63, 64, 65, 128, 129, 4096)) {
            val expected = ByteArray(count) { (it * 31).toByte() }
            val io = io(expected)
            assertArrayEquals(expected, Ft232hMpsse(io).transfer(0x39, byteArrayOf(0x94.toByte()), count))
            val scripts = io.writes.filter { it.size >= 13 && it[3] == 0x20.toByte() }
            assertTrue(scripts.all { it.size < 1024 && it.last() == 0x87.toByte() })
            val actual = scripts.flatMap(::wire)
            val original = buildList {
                repeat(count) { index ->
                    add("gpio:2:3"); add("read8")
                    add(if (index == count - 1) "nack" else "ack")
                    add("gpio:2:3")
                }
            }
            assertEquals("Same I²C wave sequence for $count bytes", original, actual)
            assertEquals(1, actual.count { it == "nack" })
            assertEquals(count - 1, actual.count { it == "ack" })
            assertTrue(io.readSizes.drop(3).all { it <= 64 })
            assertEquals(count, io.readSizes.drop(3).sum())
        }
    }

    @Test fun aNackAtAnyAddressPhasePreventsQueuingDataAndStillSendsStop() {
        for (phase in 0..2) {
            val responses = ArrayDeque<ByteArray>().apply { repeat(phase) { add(byteArrayOf(0)) }; add(byteArrayOf(1)) }
            val io = Io(responses)
            try { Ft232hMpsse(io).transfer(0x74, byteArrayOf(2), 6); fail("Missing NACK") }
            catch (expected: I2cNack) { assertEquals(0x74, expected.address) }
            assertEquals(phase + 1, io.readSizes.size)
            assertFalse(io.writes.any { it.size >= 13 && it[3] == 0x20.toByte() })
            assertArrayEquals(byteArrayOf(0x80.toByte(), 0, 3, 0x80.toByte(), 1, 3, 0x80.toByte(), 3, 3), io.writes.last())
        }
    }

    @Test fun shortBlocksAndTransportFailureCannotBecomeZeroPaddedMeasurements() {
        for (disconnected in listOf(false, true)) {
            val io = io(ByteArray(37) { 1 })
            io.failOnData = disconnected
            if (!disconnected) { io.responses.removeLast(); io.responses.add(ByteArray(36)) }
            try { Ft232hMpsse(io).transfer(0x39, byteArrayOf(0x94.toByte()), 37); fail("Missing transfer failure") }
            catch (expected: IOException) {
                assertEquals(0x80, io.writes.last()[0].toInt() and 255)
                assertEquals(9, io.writes.last().size) // STOP even after a partial/error response.
            }
        }
    }
}
