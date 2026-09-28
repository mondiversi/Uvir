package me.mondiversi.uvir

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class UvirSensorPowerOffHoldTest {
    @get:Rule val compose = createComposeRule()

    @Test fun actualShutdownDialogRequiresFiveSecondsAndOnlyConfirmsOnce() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val label = context.getString(R.string.sensor_power_off_confirm)
        val confirmations = AtomicInteger()
        compose.mainClock.autoAdvance = false
        compose.setContent {
            MaterialTheme {
                val colors = MaterialTheme.colorScheme
                var busy by remember { mutableStateOf(false) }
                SensorPowerOffConfirmation(busy, { busy = it }, rememberCoroutineScope(),
                    onPowerOffSensor = { confirmations.incrementAndGet(); true },
                    onDismissRequest = {}, colors.surface, colors.onSurface, colors.onSurfaceVariant)
            }
        }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithText(context.getString(R.string.hold_action_seconds_confirmation, label, 5))
            .assertIsDisplayed()
        val button = compose.onNodeWithContentDescription(label)
        button.performTouchInput { down(center) }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(2_200L)
        compose.runOnIdle { assertEquals(0, confirmations.get()) }
        button.performTouchInput { up() }
        compose.mainClock.advanceTimeBy(5_500L)
        compose.runOnIdle { assertEquals(0, confirmations.get()) }
        button.performTouchInput { down(center) }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(4_800L)
        compose.runOnIdle { assertEquals(0, confirmations.get()) }
        compose.mainClock.advanceTimeBy(400L)
        compose.runOnIdle { assertEquals(1, confirmations.get()) }
        compose.mainClock.advanceTimeBy(5_500L)
        compose.runOnIdle { assertEquals(1, confirmations.get()) }
        button.performTouchInput { up() }
    }
}
