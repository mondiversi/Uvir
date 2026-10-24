package me.mondiversi.uvir

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class UvirDetailDisplayLifetimeTest {
    private val root = listOf(File("src/main/java/me/mondiversi/uvir"), File("app/src/main/java/me/mondiversi/uvir"))
        .first { it.isDirectory }

    @Test fun detailsNeverRestorePresentationChoicesAfterActivityOrProcessRecreation() {
        val fields = mapOf(
            "UvirRecordDetailScreen.kt" to listOf("showChart", "viewMode"),
            "UvirAcquisitionChartScreen.kt" to listOf("selectedGroup"),
            "UvirSessionChartScreen.kt" to listOf("showChart", "viewMode", "sequenceFilterPosition", "showSequenceFilterPanel"),
            "UvirAlertChartScreen.kt" to listOf("showChart", "viewMode", "chartExpanded", "dataExpanded"),
            "UvirAlertSessionChartScreen.kt" to listOf("showChart", "viewMode"))
        for ((file, names) in fields) for (name in names) {
            val source = File(root, file).readText()
            assertTrue("$file/$name is visit-only", Regex("var\\s+$name\\s+by\\s+remember\\(").containsMatchIn(source))
            assertFalse("$file/$name must not restore", Regex("var\\s+$name\\s+by\\s+rememberSaveable").containsMatchIn(source))
        }
        assertTrue(File(root, "UvirAppRoot.kt").readText().contains("var sessionChartNavigationState by remember {"))
    }

    @Test fun mainVisibilityAndOrderRemainDurableAppPreferencesOutsideSensorConfiguration() {
        for (key in listOf(KEY_MAIN_ISLAND_ORDER, KEY_MAIN_HIDDEN_SPECTRUM_CHANNELS)) {
            assertTrue(isTransferableAppPreference(key))
            assertFalse(key in sensorSelectionPreferenceKeys)
        }
        assertTrue(File(root, "UvirLiveSpectrumControls.kt").readText().contains("putStringSet(KEY_MAIN_HIDDEN_SPECTRUM_CHANNELS"))
        assertTrue(File(root, "UvirMainIslandControls.kt").readText().contains("putString(KEY_MAIN_ISLAND_ORDER"))
    }
}
