package me.mondiversi.uvir.usb

/** Register sequences follow the ams OSRAM datasheets. No ESP32 protocol/firmware is involved. */
internal class As7331(private val bus: I2cBus, private val waitMs: (Long) -> Unit = Thread::sleep) {
    val address = 0x74
    var deviceId: Int? = null
        private set
    var gain = 64
        private set
    private var startedAtNanos: Long? = null
    fun initialize() {
        bus.write(address, 0, 0x42) // Power down, configuration state.
        waitMs(2)
        val id = bus.byte(address, 2)
        deviceId = id
        if (id and 0xf0 != 0x20) throw InvalidSensorReading("AS7331 identity mismatch: 0x${id.toString(16)}")
        bus.write(address, 7, 0) // No digital divider.
        bus.write(address, 8, 0x40) // One-shot, 1.024 MHz, no standby.
        bus.write(address, 0x0b, 1) // Reset the register index on STOP; block reads use repeated START.
    }
    fun capture(lockGain: Boolean = false): RawChannels {
        startCapture()
        return finishCapture(lockGain)
    }
    /** Start UV integration before reading VIS/IR; all bus calls still have one owner. */
    fun startCapture() {
        bus.write(address, 0, 0x42)
        waitMs(2)
        val gainCode = 11 - Integer.numberOfTrailingZeros(gain)
        bus.write(address, 6, (gainCode shl 4) or 6) // 64 ms integration.
        bus.write(address, 0, 3)
        bus.write(address, 0, 0x83)
        startedAtNanos = System.nanoTime()
    }
    fun finishCapture(lockGain: Boolean = false): RawChannels {
        repeat(3) { attempt ->
            val started = checkNotNull(startedAtNanos) { "UV conversion was not started" }
            waitMs((66 - (System.nanoTime() - started) / 1_000_000).coerceAtLeast(0))
            startedAtNanos = null
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
            if (!lockGain && next != gain && attempt < 2) { gain = next; startCapture(); return@repeat }
            if (saturated) {
                val exhausted = lockGain || gain == 1
                if (!lockGain) gain = next
                throw InvalidSensorReading("AS7331 saturated", retryable = true, saturated = true, rangeExhausted = exhausted)
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
    var onCaptureTrace: ((Int, Int, Int, Boolean, Long) -> Unit)? = null
    val address = 0x39
    var deviceId: Int? = null
        private set
    var gainIndex = 9
        private set
    private fun bank(high: Boolean) = bus.update(address, 0xbf, 0x10, if (high) 0x10 else 0)
    fun initialize() {
        bank(true)
        val id = bus.byte(address, 0x5a)
        deviceId = id
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
            val started = System.nanoTime()
            val requestedIndex = gainIndex
            bus.write(address, 0x80, 1)
            waitMs(3)
            bus.update(address, 0xc6, 0x1f, requestedIndex)
            bus.write(address, 0x80, 3)
            val data: ByteArray
            try {
                data = awaitSnapshot(requestedIndex)
            } finally { bus.write(address, 0x80, 0) }
            val status = data[0].toInt() and 255
            val measuredIndex = status and 15
            val raw = IntArray(18) { littleEndianWord(data, 1 + it * 2) }
            val spectral = intArrayOf(12, 6, 0, 7, 8, 15, 1, 2, 9, 13, 14, 3).map { raw[it] }
            // ASTATUS saturation covers all ADCs, including the clear channels.
            // Looking only at narrow spectral channels can repeatedly raise gain
            // into clear-channel overload and fall back again on every capture.
            val peak = raw.max()
            val saturated = status and 0x80 != 0 || peak >= 10000
            val next = nextVisibleGain(measuredIndex, peak, saturated)
            onCaptureTrace?.invoke(measuredIndex, next, peak, saturated, (System.nanoTime() - started) / 1_000_000)
            if (!lockGain && next != measuredIndex && attempt < 2) { gainIndex = next; return@repeat }
            if (saturated) {
                if (!lockGain) gainIndex = next
                throw InvalidSensorReading("AS7343 saturated", retryable = true, saturated = true,
                    rangeExhausted = lockGain || measuredIndex == 0)
            }
            return RawChannels(visibleWm2(spectral.toIntArray(), measuredIndex), visibleGains[measuredIndex])
        }
        error("Unreachable")
    }

    private fun awaitSnapshot(requestedIndex: Int): ByteArray {
        // Three 27.8 ms cycles plus autozero/startup margin. AVALID can still
        // describe the previous conversion; do not power-cycle on its old gain.
        waitMs(120)
        var lastStatus: Int? = null
        repeat(40) { poll ->
            if (bus.byte(address, 0x90) and 0x40 != 0) {
                // Datasheet §10.2.7: ASTATUS and all data must be one coherent read.
                val snapshot = bus.read(address, 0x94, 37)
                if (snapshot.size != 37) throw InvalidSensorReading("AS7343 incomplete frame", retryable = true)
                lastStatus = snapshot[0].toInt() and 255
                if (lastStatus!! and 15 == requestedIndex) return snapshot
            }
            if (poll < 39) waitMs(10)
        }
        throw InvalidSensorReading("AS7343 conversion did not settle: requested=$requestedIndex, " +
            "ASTATUS=${lastStatus?.let { "0x${it.toString(16)}" } ?: "not ready"}, " +
            "CFG1=0x${bus.byte(address, 0xc6).toString(16)}", retryable = true)
    }
}

internal val visibleGains = doubleArrayOf(.5, 1.0, 2.0, 4.0, 8.0, 16.0, 32.0, 64.0, 128.0, 256.0, 512.0, 1024.0)

/** Target the upper safe range using ALL ADCs. A clipped frame cannot tell how
 * far above range the light is, so reduce quickly; valid frames refine the gain
 * with headroom and hysteresis rather than walking every step back and forth. */
internal fun nextVisibleGain(current: Int, peak: Int, saturated: Boolean): Int {
    require(current in visibleGains.indices && peak >= 0)
    if (saturated) return maxOf(0, current - 2)
    if (peak >= 9200) return maxOf(0, current - 1)
    var next = current
    if (peak < 4000) {
        while (next < visibleGains.lastIndex && peak.toDouble() * (1 shl (next - current + 1)) <= 8000) next++
    }
    return next
}
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
