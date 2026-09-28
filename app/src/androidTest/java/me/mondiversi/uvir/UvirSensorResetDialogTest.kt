package me.mondiversi.uvir

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.platform.app.InstrumentationRegistry
import java.util.Collections
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class UvirSensorResetDialogTest {
    @get:Rule val compose = createComposeRule()

    @Test fun checkboxSelectionExcludesOfflineBusyAndDisconnectingSensorsInBothThemes() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val resetLabel = context.getString(R.string.sensor_restore_confirm)
        val instruction = context.getString(R.string.hold_action_seconds_confirmation, resetLabel, 5)
        val night = mutableStateOf(false)
        val rtl = mutableStateOf(false)
        val bConnected = mutableStateOf(true)
        val epoch = mutableStateOf(0)
        val requests = Collections.synchronizedList(mutableListOf<List<String>>())
        val profiles = listOf("A", "B", "Offline", "Busy").mapIndexed { i, id ->
            UvirSensorProfile(i.toLong(), id, "Sensor $id", 0L, 0L)
        }
        compose.setContent {
            MaterialTheme(colorScheme = if (night.value) darkColorScheme() else lightColorScheme()) {
                CompositionLocalProvider(LocalLayoutDirection provides
                    if (rtl.value) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                    key(epoch.value) {
                        var busy by remember { mutableStateOf(false) }
                        val colors = MaterialTheme.colorScheme
                        SensorRestoreConfirmation(profiles,
                            mapOf(
                                normalizeSensorDeviceId("A") to UvirStatusIndicator(UvirStatusDot.GREEN, false),
                                normalizeSensorDeviceId("B") to UvirStatusIndicator(
                                    if (bConnected.value) UvirStatusDot.GREEN else UvirStatusDot.RED, false),
                                normalizeSensorDeviceId("Offline") to UvirStatusIndicator(UvirStatusDot.RED, false),
                                normalizeSensorDeviceId("Busy") to UvirStatusIndicator(UvirStatusDot.GREEN, true)
                            ), busy, { busy = it }, rememberCoroutineScope(),
                            onRestoreSensor = { requests += it; it.toSet() },
                            onDismissRequest = {}, colors.surface, colors.onSurface, colors.onSurfaceVariant)
                    }
                }
            }
        }
        for (dark in listOf(false, true)) for (rightToLeft in listOf(false, true)) {
            compose.runOnIdle { night.value = dark; rtl.value = rightToLeft; epoch.value++; bConnected.value = true }
            val reset = compose.onNodeWithContentDescription(resetLabel)
            reset.assertIsNotEnabled()
            compose.onNodeWithText("Sensor Offline").performScrollTo().assertIsNotEnabled()
            compose.onNodeWithText("Sensor Busy").performScrollTo().assertIsNotEnabled()
            compose.onNodeWithText(instruction).performScrollTo().assertIsDisplayed()
            val lastSensorBounds = compose.onNodeWithText("Sensor Busy").getUnclippedBoundsInRoot()
            val instructionBounds = compose.onNodeWithText(instruction).getUnclippedBoundsInRoot()
            assertTrue("The hold instruction must follow the sensor list", lastSensorBounds.bottom < instructionBounds.top)
            compose.onNodeWithText("Sensor A").performScrollTo().performClick()
            compose.onNodeWithText("Sensor B").performScrollTo().performClick()
            reset.assertIsEnabled()
            // If a selected peer disconnects before confirmation, it is no longer a target.
            if (rightToLeft) {
                compose.runOnIdle { bConnected.value = false }
                compose.onNodeWithText("Sensor B").assertIsNotEnabled()
            }
            val previous = requests.size
            reset.performTouchInput { click(center) }
            compose.runOnIdle { assertEquals(previous, requests.size) }
            // Accessibility confirmation retains the same two-step destructive action safeguard.
            reset.performSemanticsAction(SemanticsActions.OnClick) { it() }
            compose.runOnIdle { assertEquals(previous, requests.size) }
            reset.performSemanticsAction(SemanticsActions.OnClick) { it() }
            compose.waitUntil(3_000L) { requests.size == previous + 1 }
            assertEquals(if (rightToLeft) listOf("A") else listOf("A", "B"), requests.last())
            reset.assertIsNotEnabled()
        }
        assertEquals(4, requests.size)
    }
}
