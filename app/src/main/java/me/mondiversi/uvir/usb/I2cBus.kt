package me.mondiversi.uvir.usb

import java.io.Closeable
import java.io.IOException

/** One owner serializes all bus access. Addresses are seven-bit, without the R/W bit. */
internal interface I2cBus : Closeable {
    fun transfer(address: Int, write: ByteArray, readCount: Int = 0): ByteArray
    fun read(address: Int, register: Int, count: Int = 1): ByteArray =
        transfer(address, byteArrayOf(register.toByte()), count)
    fun write(address: Int, register: Int, vararg values: Int) {
        transfer(address, byteArrayOf(register.toByte()) + values.map(Int::toByte).toByteArray())
    }
    fun byte(address: Int, register: Int): Int = read(address, register)[0].toInt() and 255
    fun update(address: Int, register: Int, mask: Int, value: Int) =
        write(address, register, (byte(address, register) and mask.inv()) or (value and mask))
}

internal class I2cNack(val address: Int) : IOException("I2C device 0x${address.toString(16)} did not acknowledge")
internal class I2cLinesHeldLow : IOException("I2C lines held low: check pull-ups, power and AD1/AD2 jumper")
internal class InvalidSensorReading(message: String, val retryable: Boolean = false,
    val saturated: Boolean = false, val rangeExhausted: Boolean = false) : IOException(message)
internal fun littleEndianWord(data: ByteArray, index: Int = 0): Int =
    (data[index].toInt() and 255) or ((data[index + 1].toInt() and 255) shl 8)

/** USB FTDI packets carry a two-byte modem/line status header in EVERY USB packet. */
internal fun stripFtdiStatus(bytes: ByteArray, count: Int, packetSize: Int): ByteArray {
    require(packetSize >= 3 && count in 0..bytes.size)
    val result = ArrayList<Byte>()
    var offset = 0
    while (offset < count) {
        val end = minOf(offset + packetSize, count)
        if (end - offset < 2) throw InvalidSensorReading("Truncated FTDI status packet")
        // Keep OE, PE, FE and receive-FIFO errors fatal. UART BREAK is not an
        // MPSSE transfer error (the same 0x8e error mask is used by PyFtdi).
        val lineStatus = bytes[offset + 1].toInt() and 255
        if (lineStatus and 0x8e != 0) throw InvalidSensorReading("FTDI line error: 0x${lineStatus.toString(16)}")
        for (i in offset + 2 until end) result.add(bytes[i])
        offset = end
    }
    return result.toByteArray()
}
