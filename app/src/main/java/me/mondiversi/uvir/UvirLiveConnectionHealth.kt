package me.mondiversi.uvir

internal enum class UvirBootObservation { INITIAL, SAME, RESTARTED, RETIRED }

/** Ephemeral boot identity, independent of the sensor's permanent hardware ID. */
internal class UvirBootSession {
    private var current = ""
    private val retired = linkedSetOf<String>()
    @Synchronized fun observe(id: String): UvirBootObservation {
        if (id.isBlank()) return UvirBootObservation.SAME // Legacy firmware.
        if (id in retired) return UvirBootObservation.RETIRED
        if (id == current) return UvirBootObservation.SAME
        val initial = current.isBlank()
        if (!initial) {
            retired += current
            if (retired.size > 8) retired.remove(retired.first())
        }
        current = id
        return if (initial) UvirBootObservation.INITIAL else UvirBootObservation.RESTARTED
    }
    @Synchronized fun accepts(id: String): Boolean = id.isBlank() || current.isBlank() || id == current
    @Synchronized fun reset() { current = ""; retired.clear() }
}

internal fun uvirLiveFrameFresh(valid: Boolean, ageMs: Long?, maximumAgeMs: Long?): Boolean =
    valid && (ageMs == null || (maximumAgeMs != null && maximumAgeMs in 500L..15_000L && ageMs in 0L..maximumAgeMs))

/** Only valid protocol frames renew liveness; partial/foreign bytes do not. */
internal class UvirFrameLiveness(private val localWifi: Boolean, startedAt: Long) {
    val pingIntervalMs = if (localWifi) 2_000L else 4_000L
    private var receivedAt = startedAt
    fun received(now: Long) { receivedAt = now }
    fun expired(now: Long, discovery: Boolean = false): Boolean =
        now - receivedAt >= if (discovery) 30_000L else if (localWifi) 6_000L else 10_000L
}
