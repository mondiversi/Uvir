package me.mondiversi.uvir.usb

/** Register sequences follow the ams OSRAM datasheets. No ESP32 protocol/firmware is involved. */
internal class As7331(private val bus: I2cBus, private val waitMs: (Long) -> Unit = Thread::sleep) {
    val address = 0x74
    var gain = 64
        private set
    fun initialize() {
        bus.write(address, 0, 0x42) // Power down, configuration state.
        waitMs(2)
        if (bus.byte(address, 2) and 0xf0 != 0x20) throw InvalidSensorReading("AS7331 identity mismatch")
        bus.write(address, 7, 0) // No digital divider.
        bus.write(address, 8, 0x40) // One-shot, 1.024 MHz, no standby.
        bus.write(address, 0x0b, 1) // Register auto-increment for repeated-start block reads.
    }
    fun capture(lockGain: Boolean = false): RawChannels {
        repeat(3) { attempt ->
            bus.write(address, 0, 0x42)
            waitMs(2)
            val gainCode = 11 - Integer.numberOfTrailingZeros(gain)
            bus.write(address, 6, (gainCode shl 4) or 6) // 64 ms integration.
            bus.write(address, 0, 3)
            bus.write(address, 0, 0x83)
            waitMs(66)
            var status = littleEndianWord(bus.read(address, 0, 2))
            var polls = 0
            while (status and 0x400 != 0 && polls++ < 5) {
                waitMs(2); status = littleEndianWord(bus.read(address, 0, 2))
            }
            if (status and 0x400 != 0 || status and 0x800 == 0 || status and 0x8000 != 0)
                throw InvalidSensorReading("AS7331 conversion not ready or invalid", retryable = true)
            val data = bus.read(address, 2, 6)
            val counts = intArrayOf(littleEndianWord(data, 4), littleEndianWord(data, 2), littleEndianWord(data, 0))
            val saturated = status and 0x6000 != 0 || counts.any { it >= 65534 }
            val peak = counts.max()
            val next = nextUvGain(gain, peak, saturated)
            if (!lockGain && next != gain && attempt < 2) { gain = next; return@repeat }
            if (saturated) {
                if (!lockGain) gain = next
                throw InvalidSensorReading("AS7331 saturated", retryable = true)
            }
            val fsr = doubleArrayOf(169984.0, 387072.0, 348160.0)
            return RawChannels(DoubleArray(3) { counts[it] * fsr[it] / (gain * 64.0 * 1024.0 * 100.0) }, gain.toDouble())
        }
        error("Unreachable")
    }
}

internal data class RawChannels(val wm2: DoubleArray, val gain: Double)

internal fun nextUvGain(current: Int, peak: Int, overflow: Boolean): Int {
    if (overflow) return maxOf(1, current / 16)
    var gain = current
    if (peak > 50000) while (gain > 1 && peak.toDouble() * gain / current > 24000) gain /= 2
    else if (peak < 8000) while (gain < 2048 && peak.toDouble() * gain * 2 / current <= 24000) gain *= 2
    return gain
}

internal class As7343(private val bus: I2cBus, private val waitMs: (Long) -> Unit = Thread::sleep) {
    val address = 0x39
    var gainIndex = 9
        private set
    private fun bank(high: Boolean) = bus.update(address, 0xbf, 0x10, if (high) 0x10 else 0)
    fun initialize() {
        bank(true)
        val id = bus.byte(address, 0x5a)
        bank(false)
        if (id != 0x81) throw InvalidSensorReading("AS7343 identity mismatch: 0x${id.toString(16)}")
        bus.write(address, 0x80, 1)
        waitMs(3)
        bus.update(address, 0xcd, 0x80, 0) // Illumination LED off.
        bus.update(address, 0xd6, 0x60, 0x60) // All 18 channels, three SMUX cycles.
        bus.write(address, 0x81, 9)
        bus.write(address, 0xd4, 0xe7, 3) // ASTEP=999, 27.8 ms per cycle.
        bus.write(address, 0x80, 0)
    }
    fun capture(lockGain: Boolean = false): RawChannels {
        repeat(3) { attempt ->
            val measuredIndex = gainIndex
            bus.write(address, 0x80, 1)
            waitMs(3)
            bus.update(address, 0xc6, 0x1f, measuredIndex)
            bus.write(address, 0x80, 3)
            val data: ByteArray
            val status: Int
            try {
                waitMs(100)
                var ready = bus.byte(address, 0x90)
                var polls = 0
                while (ready and 0x40 == 0 && polls++ < 10) { waitMs(5); ready = bus.byte(address, 0x90) }
                if (ready and 0x40 == 0) throw InvalidSensorReading("AS7343 conversion not ready", retryable = true)
                status = ready
                data = bus.read(address, 0x95, 36)
            } finally { bus.write(address, 0x80, 0) }
            val raw = IntArray(18) { littleEndianWord(data, it * 2) }
            val spectral = intArrayOf(12, 6, 0, 7, 8, 15, 1, 2, 9, 13, 14, 3).map { raw[it] }
            val peak = spectral.max()
            val saturated = status and 0x18 != 0 || peak >= 10000
            val next = when { saturated || peak >= 9200 -> maxOf(0, measuredIndex - 1)
                peak < 250 -> minOf(11, measuredIndex + 1); else -> measuredIndex }
            if (!lockGain && next != measuredIndex && attempt < 2) { gainIndex = next; return@repeat }
            if (saturated) {
                if (!lockGain) gainIndex = next
                throw InvalidSensorReading("AS7343 saturated", retryable = true)
            }
            return RawChannels(visibleWm2(spectral.toIntArray(), measuredIndex), visibleGains[measuredIndex])
        }
        error("Unreachable")
    }
}

internal val visibleGains = doubleArrayOf(.5, 1.0, 2.0, 4.0, 8.0, 16.0, 32.0, 64.0, 128.0, 256.0, 512.0, 1024.0)
private val visibleGainRatios = doubleArrayOf(.0079, .0158, .0316, .064, .124, .247, .5, 1.0, 2.0, 4.1, 8.6, 16.9)
private val visibleReferenceCounts = doubleArrayOf(5749.0, 1756.0, 2169.0, 770.0, 3141.0, 1574.0, 3747.0, 4776.0, 3336.0, 5435.0, 864.0, 10581.0)

/** Datasheet-based estimate retained from the legacy project, NOT a traceable absolute calibration. */
internal fun visibleWm2(counts: IntArray, gainIndex: Int): DoubleArray {
    require(counts.size == 12 && gainIndex in visibleGains.indices)
    val physical = DoubleArray(12) { counts[it] / visibleReferenceCounts[it] * .155 * 16.9 / visibleGainRatios[gainIndex] }
    return doubleArrayOf((physical[0] + physical[1]) / 2, (physical[2] + physical[3]) / 2,
        physical[4], (physical[5] + physical[6]) / 2, physical[7], physical[8],
        (physical[9] + physical[10]) / 2, physical[11])
}
