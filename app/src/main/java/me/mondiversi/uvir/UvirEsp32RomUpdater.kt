package me.mondiversi.uvir

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest

/** Classic ESP32 ROM protocol only. No full erase, partition-table writes or eFuse writes. */
internal interface UvirRomSerial {
    fun write(bytes: ByteArray)
    fun read(bytes: ByteArray, timeoutMs: Int): Int
    fun control(dtr: Boolean, rts: Boolean)
}

internal object UvirSlip {
    fun encode(bytes: ByteArray): ByteArray = ByteArrayOutputStream().apply {
        write(0xC0)
        bytes.forEach { when (it.toInt() and 255) {
            0xC0 -> { write(0xDB); write(0xDC) }
            0xDB -> { write(0xDB); write(0xDD) }
            else -> write(it.toInt() and 255)
        } }
        write(0xC0)
    }.toByteArray()
}

internal class UvirEsp32RomUpdater(private val serial: UvirRomSerial) {
    private val incoming = ArrayDeque<Int>()
    private fun words(vararg values: Int): ByteArray = ByteBuffer.allocate(values.size * 4)
        .order(ByteOrder.LITTLE_ENDIAN).apply { values.forEach(::putInt) }.array()
    private fun response(timeout: Int): ByteArray {
        val deadline = System.nanoTime() + timeout * 1_000_000L
        val packet = ByteArrayOutputStream()
        var started = false; var escaped = false
        while (System.nanoTime() < deadline) {
            if (incoming.isEmpty()) {
                val buffer = ByteArray(4096)
                val count = serial.read(buffer, 200)
                for (i in 0 until count.coerceAtLeast(0)) incoming.addLast(buffer[i].toInt() and 255)
                continue
            }
            val byte = incoming.removeFirst()
            if (byte == 0xC0) {
                if (started && packet.size() > 0 && !escaped) return packet.toByteArray()
                packet.reset(); started = true; escaped = false
            } else if (started) {
                if (escaped) {
                    require(byte == 0xDC || byte == 0xDD) { "Invalid ROM SLIP escape" }
                    packet.write(if (byte == 0xDC) 0xC0 else 0xDB); escaped = false
                } else if (byte == 0xDB) escaped = true else packet.write(byte)
                require(packet.size() <= 65544) { "ROM response too large" }
            }
        }
        error("ESP32 ROM timeout")
    }
    private fun command(opcode: Int, payload: ByteArray, checksum: Int = 0, timeout: Int = 5000): ByteArray {
        val header = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
            .put(0).put(opcode.toByte()).putShort(payload.size.toShort()).putInt(checksum).array()
        serial.write(UvirSlip.encode(header + payload))
        val deadline = System.nanoTime() + timeout * 1_000_000L
        while (System.nanoTime() < deadline) {
            val packet = response(((deadline - System.nanoTime()) / 1_000_000).toInt().coerceAtLeast(1))
            if (packet.size < 12 || packet[0].toInt() != 1 || packet[1].toInt() and 255 != opcode) continue
            val length = ByteBuffer.wrap(packet, 2, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt() and 65535
            require(packet.size == length + 8 && length >= 4) { "Invalid ROM response length" }
            require(packet[packet.size - 4].toInt() == 0 && packet[packet.size - 3].toInt() == 0) { "ESP32 ROM rejected command $opcode" }
            return packet
        }
        error("ESP32 ROM command timeout")
    }
    private fun register(address: Int): Int = ByteBuffer.wrap(command(0x0A, words(address)), 4, 4).order(ByteOrder.LITTLE_ENDIAN).int
    fun reboot() {
        serial.control(false, true); Thread.sleep(100); serial.control(false, false)
    }
    fun flash(image: ByteArray, expectedUid: String, flashSize: Int, partitionMd5: String, progress: (Float) -> Unit) {
        require(UvirUpdatePolicy.validFirmwareImage(image)) { "Not a supported ESP32 application image" }
        require(expectedUid.matches(Regex("[0-9A-Fa-f]{12}")) && flashSize >= 0x400000)
        serial.control(false, true); Thread.sleep(100)
        serial.control(true, false); Thread.sleep(100); serial.control(false, false)
        val sync = byteArrayOf(7, 7, 0x12, 0x20) + ByteArray(32) { 0x55 }
        var synced = false
        repeat(12) { if (!synced) synced = runCatching { command(8, sync, timeout = 1000); true }.getOrDefault(false) }
        check(synced) { "ESP32 bootloader unavailable; hold BOOT and retry" }
        require(register(0x40001000) == 0x00F01D83) { "Unsupported ESP32 chip" }
        val high = register(0x3FF5A008); val low = register(0x3FF5A004)
        // Uvir formats ESP.getEfuseMac() as a hexadecimal integer; this is the
        // reverse byte order of the conventional MAC printed by esptool.
        val uid = ByteBuffer.allocate(8).putInt(high).putInt(low).array().copyOfRange(2, 8).reversedArray().toHex()
        require(uid.equals(expectedUid, true)) { "Firmware target identity mismatch" }
        val security = register(0x3FF5A018)
        require(security and 0x30 == 0 && Integer.bitCount(register(0x3FF5A000) and (0x7F shl 20)) % 2 == 0) {
            "Secure boot or encrypted flash is not supported by this updater"
        }
        require(register(0x3FF5A014) and 0xFFFFF == 0 && register(0x3FF5A00C) and 0x1F0 == 0) { "Custom flash wiring not supported" }
        command(0x0D, words(0, 0)); command(0x0B, words(0, flashSize, 65536, 4096, 256, 65535))
        val partition = command(0x13, words(0x8000, 3072, 0, 0), timeout = 10000)
        require(partition.copyOfRange(8, partition.size - 4).toString(Charsets.US_ASCII).equals(partitionMd5, true)) { "Sensor partition table differs from the signed release layout" }
        val blocks = (image.size + 1023) / 1024
        require(blocks * 1024 <= UvirUpdatePolicy.APP_CAPACITY)
        // All identity and security checks finish before this first destructive command.
        command(2, words(image.size, blocks, 1024, UvirUpdatePolicy.APP_OFFSET), timeout = 120000)
        repeat(blocks) { sequence ->
            val block = ByteArray(1024) { 0xFF.toByte() }
            image.copyInto(block, 0, sequence * 1024, minOf(image.size, (sequence + 1) * 1024))
            val checksum = block.fold(0xEF) { acc, byte -> acc xor (byte.toInt() and 255) }
            // Abort on uncertain ACK rather than accepting an ACK for another block.
            command(3, words(1024, sequence, 0, 0) + block, checksum, 10000)
            progress((sequence + 1).toFloat() / blocks)
        }
        val verified = command(0x13, words(UvirUpdatePolicy.APP_OFFSET, image.size, 0, 0), timeout = 30000)
        require(verified.copyOfRange(8, verified.size - 4).toString(Charsets.US_ASCII)
            .equals(MessageDigest.getInstance("MD5").digest(image).toHex(), true)) { "Firmware flash verification failed" }
        command(4, words(1))
        reboot()
    }
}
