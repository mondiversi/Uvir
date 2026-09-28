package me.mondiversi.uvir

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Exercises the same enabled click path used by the real REC/STOP controls. */
class UvirFloatingActionHapticTest {
    @get:Rule val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun automaticRecIsClickableWhenEnabled() {
        val clicks = AtomicInteger()
        compose.setContent {
            MaterialTheme {
                UvirAutomaticAcquisitionFloatingAction(
                    automaticActive = false,
                    completedCount = 0,
                    enabled = true,
                    syncInProgress = false,
                    onClick = { clicks.incrementAndGet() }
                )
            }
        }
        compose.onNodeWithContentDescription(context.getString(R.string.start))
            .assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(1, clicks.get()) }
    }

    @Test fun alertStopIsClickableWhenEnabled() {
        val clicks = AtomicInteger()
        compose.setContent {
            MaterialTheme {
                UvirAlertRegistrationActionButton(
                    active = true,
                    completedCount = 2,
                    enabled = true,
                    onClick = { clicks.incrementAndGet() }
                )
            }
        }
        compose.onNodeWithContentDescription(
            context.getString(R.string.stop_all_value_alerts_accessibility)
        ).assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(1, clicks.get()) }
    }
}
