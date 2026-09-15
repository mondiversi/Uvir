package me.mondiversi.uvir

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/** Isolated UI: callbacks only increment a test counter, never change app/sensor data. */
class UvirStandardPressTest {
    @get:Rule val compose = createComposeRule()
    private val confirmations = AtomicInteger()

    private fun showButton() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            MaterialTheme {
                HoldToConfirmDeleteButton(
                    label = "Hold test",
                    holdDurationMillis = 2_000L,
                    onConfirmed = { confirmations.incrementAndGet() }
                )
            }
        }
        compose.mainClock.advanceTimeByFrame()
    }

    private fun down() {
        compose.onNodeWithText("Hold test").performTouchInput { down(center) }
        compose.mainClock.advanceTimeByFrame()
    }

    @Test fun shortClickDoesNotConfirm() {
        showButton()
        compose.onNodeWithText("Hold test").performClick()
        compose.mainClock.advanceTimeBy(3_000L)
        compose.runOnIdle { assertEquals(0, confirmations.get()) }
    }

    @Test fun releasingBeforeTheDeadlineCancelsConfirmation() {
        showButton()
        down()
        compose.mainClock.advanceTimeBy(800L)
        compose.onNodeWithText("Hold test").performTouchInput { up() }
        compose.mainClock.advanceTimeBy(3_000L)
        compose.runOnIdle { assertEquals(0, confirmations.get()) }
    }

    @Test fun cancelledTouchDoesNotConfirm() {
        showButton()
        down()
        compose.mainClock.advanceTimeBy(800L)
        compose.onNodeWithText("Hold test").performTouchInput { cancel() }
        compose.mainClock.advanceTimeBy(3_000L)
        compose.runOnIdle { assertEquals(0, confirmations.get()) }
    }

    @Test fun fullHoldConfirmsOnceEvenWhenKeptPressedLonger() {
        showButton()
        down()
        compose.mainClock.advanceTimeBy(2_200L)
        compose.runOnIdle { assertEquals(1, confirmations.get()) }
        compose.mainClock.advanceTimeBy(3_000L)
        compose.runOnIdle { assertEquals(1, confirmations.get()) }
        compose.onNodeWithText("Hold test").performTouchInput { up() }
    }
}
