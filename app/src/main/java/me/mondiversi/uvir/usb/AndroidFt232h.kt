package me.mondiversi.uvir.usb

import android.hardware.usb.*
import android.os.SystemClock
import java.io.IOException

internal fun isFt232h(device: UsbDevice): Boolean = device.vendorId == 0x0403 && device.productId == 0x6014

/** Talks to the chip's MPSSE, NOT to an FT232R UART or the old ESP32 serial protocol. */
internal class AndroidFt232h(manager: UsbManager, device: UsbDevice) : MpsseIo {
    private val connection = manager.openDevice(device) ?: throw IOException("USB permission or device unavailable")
    private val intf = (0 until device.interfaceCount).map(device::getInterface).firstOrNull { intf ->
        (0 until intf.endpointCount).count { intf.getEndpoint(it).type == UsbConstants.USB_ENDPOINT_XFER_BULK } >= 2
    } ?: run { connection.close(); throw IOException("FT232H bulk interface missing") }
    private val endpoints = (0 until intf.endpointCount).map(intf::getEndpoint)
    private val input = endpoints.first { it.type == UsbConstants.USB_ENDPOINT_XFER_BULK && it.direction == UsbConstants.USB_DIR_IN }
    private val output = endpoints.first { it.type == UsbConstants.USB_ENDPOINT_XFER_BULK && it.direction == UsbConstants.USB_DIR_OUT }
    private val pending = ArrayDeque<Byte>()
    private var closed = false

    init {
        try {
            if (!connection.claimInterface(intf, true)) throw IOException("Cannot claim FT232H")
            control(0, 0) // Reset.
            control(0, 1); control(0, 2) // Purge RX and TX.
            control(9, 2) // Latency timer, ms.
            control(11, 0) // Reset bit mode before enabling MPSSE.
            control(11, 0x0203) // MPSSE; AD0/AD1 outputs, AD2 input.
            SystemClock.sleep(50)
        } catch (error: Exception) { close(); throw error }
    }

    private fun control(request: Int, value: Int) {
        if (connection.controlTransfer(0x40, request, value, intf.id + 1, null, 0, 1000) < 0)
            throw IOException("FT232H control transfer failed ($request)")
    }
    override fun write(bytes: ByteArray) {
        check(!closed)
        var offset = 0
        while (offset < bytes.size) {
            val count = connection.bulkTransfer(output, bytes, offset, bytes.size - offset, 1000)
            if (count <= 0) throw IOException("FT232H USB write failed")
            offset += count
        }
    }
    override fun readExactly(count: Int): ByteArray {
        check(!closed)
        val deadline = SystemClock.elapsedRealtime() + 1500
        val buffer = ByteArray(input.maxPacketSize * 16)
        while (pending.size < count && SystemClock.elapsedRealtime() < deadline) {
            val received = connection.bulkTransfer(input, buffer, buffer.size, 100)
            if (received > 0) stripFtdiStatus(buffer, received, input.maxPacketSize).forEach(pending::addLast)
            else if (received < 0) SystemClock.sleep(2)
        }
        if (pending.size < count) throw IOException("FT232H USB response timed out")
        return ByteArray(count) { pending.removeFirst() }
    }
    override fun close() {
        if (closed) return
        closed = true
        runCatching { connection.releaseInterface(intf) }
        connection.close()
        pending.clear()
    }
}
