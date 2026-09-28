package me.mondiversi.uvir

import android.content.SharedPreferences

/** Serialize badge updates from the UI and the retained multisensor receiver. */
internal object UvirUnreadCounters {
    @Synchronized
    fun increment(preferences: SharedPreferences, key: String, amount: Int = 1): Int {
        val value = (preferences.getInt(key, 0).toLong() + amount.coerceAtLeast(0))
            .coerceAtMost(9_999L).toInt()
        preferences.edit().putInt(key, value).apply()
        return value
    }

    @Synchronized
    fun clear(preferences: SharedPreferences, key: String) {
        preferences.edit().putInt(key, 0).apply()
    }
}
