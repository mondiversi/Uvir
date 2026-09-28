package me.mondiversi.uvir

/** Keeps routine unanswered Wi-Fi searches from filling the diagnostic log. */
internal class UvirWifiDiscoveryLogEpisode {
    private var unansweredCycles = 0

    fun onMiss(): String? {
        unansweredCycles++
        return if (unansweredCycles == 1) {
            "Wi-Fi sensor not found; automatic discovery continues."
        } else {
            null
        }
    }

    fun onDiscovered(): String? {
        if (unansweredCycles == 0) {
            return null
        }
        val previousCycles = unansweredCycles
        unansweredCycles = 0
        return "Wi-Fi sensor discovered after $previousCycles unanswered search cycles."
    }
}
