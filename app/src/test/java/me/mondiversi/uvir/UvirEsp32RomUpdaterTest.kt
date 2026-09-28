package me.mondiversi.uvir

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import org.junit.Assert.*
import org.junit.Test

class UvirEsp32RomUpdaterTest {
    private class Serial(val wrongUid: Boolean = false, val secure: Boolean = false, val wrongMd5: Boolean = false) : UvirRomSerial {
        val commands = mutableListOf<Int>()
        var image = ByteArray(0)
        var offset = 0
        private val replies = ArrayDeque<Byte>()
        override fun control(dtr: Boolean, rts: Boolean) {}
        override fun read(bytes: ByteArray, timeoutMs: Int): Int {
            var count = 0
            while (replies.isNotEmpty() && count < minOf(bytes.size, 7)) bytes[count++] = replies.removeFirst()
            return count
        }
        override fun write(bytes: ByteArray) {
            val decoded = mutableListOf<Byte>(); var escaped = false
            bytes.drop(1).dropLast(1).forEach { byte ->
                if (escaped) { decoded.add(if (byte.toInt() and 255 == 0xDC) 0xC0.toByte() else 0xDB.toByte()); escaped = false }
                else if (byte.toInt() and 255 == 0xDB) escaped = true else decoded.add(byte)
            }
            val packet = decoded.toByteArray(); val opcode = packet[1].toInt() and 255
            commands.add(opcode)
            fun word(at: Int) = ByteBuffer.wrap(packet, at, 4).order(ByteOrder.LITTLE_ENDIAN).int
            val value = if (opcode == 0x0A) when(word(8)) {
                0x40001000 -> 0x00F01D83
                0x3FF5A008 -> if (wrongUid) 0x1234 else 0x20E7
                0x3FF5A004 -> 0xC8AC9F68.toInt()
                0x3FF5A018 -> if (secure) 0x10 else 0
                else -> 0
            } else 0
            if (opcode == 2) offset = word(20)
            if (opcode == 3) image += packet.copyOfRange(24, packet.size)
            val data = if (opcode == 0x13) {
                val length = word(12)
                (if (word(8) == 0x8000) "a".repeat(32) else if (wrongMd5) "0".repeat(32) else MessageDigest.getInstance("MD5").digest(image.copyOf(length)).toHex()).toByteArray()
            } else byteArrayOf()
            val response = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).put(1).put(opcode.toByte()).putShort((data.size + 4).toShort()).putInt(value).array() + data + ByteArray(4)
            UvirSlip.encode(response).forEach(replies::addLast)
        }
    }
    private fun image() = ByteArray(1025).apply { this[0] = 0xE9.toByte() }
    @Test fun fragmentedTransferOnlyWritesApplication() {
        val serial = Serial()
        UvirEsp32RomUpdater(serial).flash(image(), "689FACC8E720", 0x400000, "a".repeat(32)) {}
        assertEquals(0x10000, serial.offset)
        assertEquals(2, serial.commands.count { it == 3 })
        assertFalse(serial.commands.contains(0xD0))
        assertArrayEquals(image(), serial.image.copyOf(1025))
        assertTrue(serial.image.drop(1025).all { it == 0xFF.toByte() })
    }
    @Test fun foreignIdentityNeverErases() {
        val serial = Serial(wrongUid = true)
        assertThrows(IllegalArgumentException::class.java) { UvirEsp32RomUpdater(serial).flash(image(), "689FACC8E720", 0x400000, "a".repeat(32)) {} }
        assertFalse(serial.commands.contains(2))
    }
    @Test fun secureBootNeverErases() {
        val serial = Serial(secure = true)
        assertThrows(IllegalArgumentException::class.java) { UvirEsp32RomUpdater(serial).flash(image(), "689FACC8E720", 0x400000, "a".repeat(32)) {} }
        assertFalse(serial.commands.contains(2))
    }
    @Test fun badFlashDigestNeverReportsSuccess() {
        val serial = Serial(wrongMd5 = true)
        assertThrows(IllegalArgumentException::class.java) { UvirEsp32RomUpdater(serial).flash(image(), "689FACC8E720", 0x400000, "a".repeat(32)) {} }
        assertFalse(serial.commands.contains(4))
    }
    @Test fun unknownPartitionTableNeverErases() {
        val serial = Serial()
        assertThrows(IllegalArgumentException::class.java) { UvirEsp32RomUpdater(serial).flash(image(), "689FACC8E720", 0x400000, "b".repeat(32)) {} }
        assertFalse(serial.commands.contains(2))
    }
}
