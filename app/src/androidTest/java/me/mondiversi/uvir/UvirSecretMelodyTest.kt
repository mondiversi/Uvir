package me.mondiversi.uvir

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Isolated concert UI: fake callbacks, never accesses the database or sensor. */
class UvirSecretMelodyTest {
    @get:Rule val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val title = context.getString(R.string.debug_performance_title)
    private val commands = CopyOnWriteArrayList<String>()

    private fun showContent(
        connected: Boolean = true,
        enabled: Boolean = true,
        firmwareSupported: Boolean = true
    ) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            MaterialTheme {
                val colors = MaterialTheme.colorScheme
                UvirDebugPerformanceContent(
                    context, connected, enabled, firmwareSupported, 0L,
                    colors.onSurface, colors.onSurfaceVariant,
                    onStart = { commands.add(it); true },
                    onStop = { true }
                )
            }
        }
        compose.mainClock.advanceTimeByFrame()
    }

    private fun down() {
        compose.onNodeWithText(title).performTouchInput { down(center) }
        compose.mainClock.advanceTimeByFrame()
    }

    @Test fun ordinaryListHidesHappyBirthday() {
        showContent()
        compose.onNodeWithText("Happy Birthday").assertDoesNotExist()
        for (name in listOf("Indiana Jones", "Jurassic Park", "Star Wars")) {
            compose.onNodeWithText(name).assertIsDisplayed()
        }
    }

    @Test fun shortClickDoesNotPlay() {
        showContent()
        compose.onNodeWithText(title).performClick()
        compose.mainClock.advanceTimeBy(3_000L)
        assertEquals(emptyList<String>(), commands.toList())
    }

    @Test fun earlyReleaseDoesNotPlay() {
        showContent()
        down()
        compose.mainClock.advanceTimeBy(800L)
        compose.onNodeWithText(title).performTouchInput { up() }
        compose.mainClock.advanceTimeBy(3_000L)
        assertEquals(emptyList<String>(), commands.toList())
    }

    @Test fun cancelledTouchDoesNotPlay() {
        showContent()
        down()
        compose.mainClock.advanceTimeBy(800L)
        compose.onNodeWithText(title).performTouchInput { cancel() }
        compose.mainClock.advanceTimeBy(3_000L)
        assertEquals(emptyList<String>(), commands.toList())
    }

    @Test fun fullHoldPlaysOnceWithoutOpeningADialog() {
        showContent()
        down()
        compose.mainClock.advanceTimeBy(1_800L)
        assertEquals(emptyList<String>(), commands.toList())
        compose.mainClock.advanceTimeBy(400L)
        compose.waitUntil(2_000L) { commands.size == 1 }
        compose.mainClock.advanceTimeBy(1_000L)
        assertEquals(listOf("HAPPY_BIRTHDAY"), commands.toList())
        compose.onNodeWithText(context.getString(R.string.debug_performance_stop)).assertIsDisplayed()
        // Discovery is a bottom toast only, not a confirmation dialog in Compose.
        compose.onNodeWithText(context.getString(R.string.debug_secret_melody_found)).assertDoesNotExist()
        compose.onNodeWithText(title).performTouchInput { up() }
    }

    @Test fun disconnectedSensorCannotPlay() {
        showContent(connected = false)
        compose.onNodeWithText(title).assertIsNotEnabled()
        down()
        compose.mainClock.advanceTimeBy(2_500L)
        compose.onNodeWithText(title).performTouchInput { up() }
        assertEquals(emptyList<String>(), commands.toList())
    }

    @Test fun busySensorCannotPlay() {
        showContent(enabled = false)
        compose.onNodeWithText(title).assertIsNotEnabled()
        down()
        compose.mainClock.advanceTimeBy(2_500L)
        compose.onNodeWithText(title).performTouchInput { up() }
        assertEquals(emptyList<String>(), commands.toList())
    }

    @Test fun unsupportedFirmwareCannotPlay() {
        showContent(firmwareSupported = false)
        compose.onNodeWithText(title).assertIsNotEnabled()
        down()
        compose.mainClock.advanceTimeBy(2_500L)
        compose.onNodeWithText(title).performTouchInput { up() }
        assertEquals(emptyList<String>(), commands.toList())
    }

    @Test fun ordinaryPlaybackStillUsesTheSelectedTheme() {
        showContent()
        compose.onNodeWithText("Star Wars").performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithText(context.getString(R.string.debug_performance_play)).performClick()
        compose.waitUntil(2_000L) { commands.size == 1 }
        assertEquals(listOf("STAR_WARS"), commands.toList())
    }
}
