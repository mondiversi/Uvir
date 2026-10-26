package me.mondiversi.uvir

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class UvirDetailDisplayLifetimeTest {
    private val root = listOf(File("src/main/java/me/mondiversi/uvir"), File("app/src/main/java/me/mondiversi/uvir"))
        .first { it.isDirectory }

    @Test fun mainVisibilityAndOrderRemainDurableAppPreferencesOutsideSensorConfiguration() {
        for (key in listOf(KEY_MAIN_ISLAND_ORDER, KEY_MAIN_HIDDEN_SPECTRUM_CHANNELS)) {
            assertTrue(isTransferableAppPreference(key))
            assertFalse(key in sensorSelectionPreferenceKeys)
        }
        assertTrue(File(root, "UvirLiveSpectrumControls.kt").readText().contains("putStringSet(KEY_MAIN_HIDDEN_SPECTRUM_CHANNELS"))
        assertTrue(File(root, "UvirMainIslandControls.kt").readText().contains("putString(KEY_MAIN_ISLAND_ORDER"))
    }
}
