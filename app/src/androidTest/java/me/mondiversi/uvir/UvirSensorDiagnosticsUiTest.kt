package me.mondiversi.uvir

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.awaitCancellation
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Isolated diagnostics with synthetic replies, never opens the database or hardware. */
class UvirSensorDiagnosticsUiTest {
    @get:Rule val compose = createComposeRule()
    private val resources = InstrumentationRegistry.getInstrumentation().targetContext.resources
    private val info = UvirSensorRuntimeInfo(
        deviceId = "TEST_SENSOR", firmwareVersion = "0.5.69", boardName = "ESP32",
        heapSizeBytes = 240_000, freeHeapBytes = 110_000, flashSizeBytes = 4_194_304,
        offlineUsed = 0, offlineCapacity = 1800, sensorAvailable = true, uvAvailable = false,
        offlineRecording = false, alertMonitoringEnabled = true,
        wifiNetwork = "PRIVATE_SSID", wifiAddress = "PRIVATE_IP",
        internetBrokerHost = "PRIVATE_BROKER", bluetoothName = "PRIVATE_PAIRING"
    )

    @Test fun disconnectedButtonIsDisabledAndCannotSend() {
        val calls = AtomicInteger()
        compose.setContent {
            MaterialTheme {
                val colors = MaterialTheme.colorScheme
                UvirSensorDiagnosticsContent(false, info, SensorConnectionMode.WIFI, "Test",
                    colors.surface, colors.onSurface, colors.onSurfaceVariant,
                    onProbe = { _, _ -> calls.incrementAndGet(); UvirDiagnosticProbe(UvirDiagnosticOutcome.TIMEOUT) })
            }
        }
        compose.onNodeWithText(resources.getString(R.string.debug_diagnostic)).assertIsNotEnabled()
        assertEquals(0, calls.get())
    }

    @Test fun unsupportedFirmwareShowsUpdateDialogWithoutSending() {
        val calls = AtomicInteger()
        compose.setContent {
            MaterialTheme {
                val colors = MaterialTheme.colorScheme
                UvirSensorDiagnosticsContent(true, info.copy(firmwareVersion = "0.5.68"),
                    SensorConnectionMode.USB, "Test", colors.surface, colors.onSurface, colors.onSurfaceVariant,
                    onProbe = { _, _ -> calls.incrementAndGet(); UvirDiagnosticProbe(UvirDiagnosticOutcome.TIMEOUT) })
            }
        }
        compose.onNodeWithText(resources.getString(R.string.debug_diagnostic)).performClick()
        compose.onNodeWithText(resources.getString(R.string.sensor_firmware_update_required_title)).assertIsDisplayed()
        assertEquals(0, calls.get())
    }

    @Test fun completedReportEnablesShareAndUsesBothThemes() {
        val dark = mutableStateOf(false)
        val calls = AtomicInteger()
        compose.setContent {
            MaterialTheme(colorScheme = if (dark.value) darkColorScheme() else lightColorScheme()) {
                val colors = MaterialTheme.colorScheme
                UvirSensorDiagnosticsContent(true, info, SensorConnectionMode.WIFI, "Test",
                    colors.surface, colors.onSurface, colors.onSurfaceVariant,
                    onProbe = { hardwareId, mode ->
                        assertEquals("TEST_SENSOR", hardwareId)
                        assertEquals(SensorConnectionMode.WIFI, mode)
                        calls.incrementAndGet()
                        UvirDiagnosticProbe(UvirDiagnosticOutcome.SUCCESS, 12.5, info)
                    })
            }
        }
        compose.onNodeWithText(resources.getString(R.string.debug_diagnostic)).performClick()
        compose.waitUntil(10_000L) {
            compose.onAllNodesWithText(resources.getString(R.string.diagnostic_summary, 6, 6), substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals(6, calls.get())
        for (darkTheme in listOf(false, true)) {
            compose.runOnIdle { dark.value = darkTheme }
            compose.onNodeWithText(resources.getString(R.string.export)).assertIsEnabled().assertIsDisplayed()
            compose.onNodeWithText(resources.getString(R.string.close)).assertIsDisplayed()
            val share = compose.onNodeWithText(resources.getString(R.string.export)).fetchSemanticsNode().boundsInRoot
            val close = compose.onNodeWithText(resources.getString(R.string.close)).fetchSemanticsNode().boundsInRoot
            assertTrue(share.center.x < close.center.x)
        }
    }

    @Test fun closingInProgressCancelsPendingProbe() {
        val waiting = AtomicBoolean()
        val cancelled = AtomicBoolean()
        compose.setContent {
            MaterialTheme {
                val colors = MaterialTheme.colorScheme
                UvirSensorDiagnosticsContent(true, info, SensorConnectionMode.WIFI, "Test",
                    colors.surface, colors.onSurface, colors.onSurfaceVariant,
                    onProbe = { _, _ ->
                        waiting.set(true)
                        try { awaitCancellation() } finally { cancelled.set(true) }
                    })
            }
        }
        compose.onNodeWithText(resources.getString(R.string.debug_diagnostic)).performClick()
        compose.waitUntil(2_000L) { waiting.get() }
        compose.onNodeWithText(resources.getString(R.string.export)).assertIsNotEnabled()
        compose.onNodeWithText(resources.getString(R.string.close)).performClick()
        compose.waitUntil(2_000L) { cancelled.get() }
    }

    @Test fun popupAndTxtUseTheSameReportWithoutNetworkCredentials() {
        val report = UvirSensorDiagnosticReport(1_000L, 80L, "Sensor שלום", SensorConnectionMode.INTERNET,
            info, List(6) { UvirDiagnosticProbe(UvirDiagnosticOutcome.SUCCESS, 12.5, info) })
        val text = formatUvirSensorDiagnosticReport(resources, report)
        assertTrue(text.contains("Sensor שלום"))
        assertTrue(text.contains("TEST_SENSOR"))
        assertTrue(text.contains(resources.getString(R.string.diagnostic_summary, 6, 6)))
        assertTrue(text.contains("0/1800"))
        for (privateValue in listOf("PRIVATE_SSID", "PRIVATE_IP", "PRIVATE_BROKER", "PRIVATE_PAIRING")) {
            assertFalse("Private value must be excluded: $privateValue", text.contains(privateValue))
        }
    }
}
