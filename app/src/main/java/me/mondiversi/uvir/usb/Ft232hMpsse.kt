package me.mondiversi.uvir.usb

import java.io.Closeable
import java.io.IOException

internal interface MpsseIo : Closeable {
    fun write(bytes: ByteArray)
    fun readExactly(count: Int): ByteArray
}

/** FTDI AN_108/AN_255. AD0=SCL, AD1=SDA out, AD2=SDA in (AD1/AD2 must be joined).
 * Drive-only-zero releases both lines to EXTERNAL 3.3 V pull-ups; never drive SDA high.
 * 100 kHz, three-phase clocking; clock stretching is not supported by this implementation.
 */
internal class Ft232hMpsse(private val io: MpsseIo) : I2cBus {
    // 64 × 12 + SEND_IMMEDIATE fits the FT232H's 1 KiB command FIFO;
    // the response also stays well below its 1 KiB receive FIFO.
    private val readChunkSize = 64
    private val readAndAck = bytes(0x80, 2, 3, 0x20, 0, 0, 0x13, 0, 0, 0x80, 2, 3)
    fun initialize() {
        io.write(bytes(0xaa, 0x87))
        check(io.readExactly(2).contentEquals(bytes(0xfa, 0xaa))) { "FT232H MPSSE synchronization failed" }
        // Disable /5 and adaptive clock; enable three-phase, disable loopback.
        // 60 MHz / (3 * (199 + 1)) = 100 kHz.
        io.write(bytes(0x8a, 0x97, 0x8c, 0x85, 0x86, 199, 0, 0x9e, 0x03, 0, 0x80, 0x03, 0x03))
        io.write(bytes(0x81, 0x87))
        if ((io.readExactly(1)[0].toInt() and 0x05) != 0x05) {
            throw I2cLinesHeldLow()
        }
    }

    @Synchronized
    override fun transfer(address: Int, write: ByteArray, readCount: Int): ByteArray {
        require(address in 0x08..0x77 && readCount in 0..4096 && (write.isNotEmpty() || readCount > 0))
        try {
            start()
            if (write.isNotEmpty()) {
                send(address shl 1, address)
                write.forEach { send(it.toInt() and 255, address) }
            }
            if (readCount > 0) {
                if (write.isNotEmpty()) start()
                send((address shl 1) or 1, address)
                return receive(readCount)
            }
            return byteArrayOf()
        } finally {
            // A failed ACK must still release the bus. Preserve the original transport exception.
            runCatching { stop() }
        }
    }

    private fun start() {
        io.write(bytes(0x80, 3, 3, 0x80, 3, 3, 0x80, 1, 3, 0x80, 0, 3))
    }
    private fun stop() { io.write(bytes(0x80, 0, 3, 0x80, 1, 3, 0x80, 3, 3)) }
    private fun send(value: Int, address: Int) {
        // Clock byte on negative edge. Release SDA, clock one ACK bit on positive edge.
        io.write(bytes(0x11, 0, 0, value, 0x80, 2, 3, 0x22, 0, 0x87))
        if (io.readExactly(1)[0].toInt() and 1 != 0) throw I2cNack(address)
    }
    private fun receive(count: Int): ByteArray {
        val result = ByteArray(count)
        var offset = 0
        while (offset < count) {
            val size = minOf(readChunkSize, count - offset)
            val commands = ByteArray(size * readAndAck.size + 1)
            repeat(size) { index ->
                val position = index * readAndAck.size
                readAndAck.copyInto(commands, position)
                // ACK every byte across chunks; NACK only the final byte of the
                // entire I²C read. GPIO/clock commands match the old bytewise path.
                if (offset + index == count - 1) commands[position + 8] = 0xff.toByte()
            }
            commands[commands.lastIndex] = 0x87.toByte() // Flush after the complete chunk.
            io.write(commands)
            val chunk = io.readExactly(size)
            if (chunk.size != size) throw IOException("FT232H incomplete I2C block (${chunk.size}/$size)")
            chunk.copyInto(result, offset)
            offset += size
        }
        return result
    }
    override fun close() { runCatching { stop() }; io.close() }
    private fun bytes(vararg values: Int) = values.map(Int::toByte).toByteArray()
}
