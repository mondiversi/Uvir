package me.mondiversi.uvir

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.atomic.AtomicInteger
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Isolated callbacks: never records data or sends a command to a real sensor. */
class UvirFloatingActionHintTest {
    @get:Rule val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    // Popup is a separate Android window: compare screen, not window-local coordinates.
    private fun SemanticsNode.screenBounds() = Rect(
        positionOnScreen, Size(size.width.toFloat(), size.height.toFloat())
    )

    @Test fun commandHintsUseTheRequestedItalianAndEnglishWording() {
        val keys = listOf(R.string.capture_hint_start, R.string.capture_hint_stop,
            R.string.alert_hint_start, R.string.alert_hint_stop)
        val translations = mapOf(
            "it" to listOf("Avvia acquisizione", "Ferma acquisizione", "Avvia registrazione", "Ferma registrazione"),
            "en" to listOf("Start acquisition", "Stop acquisition", "Start recording", "Stop recording")
        )
        for ((language, expected) in translations) {
            val configuration = Configuration(context.resources.configuration).apply {
                setLocale(Locale.forLanguageTag(language))
            }
            val localized = context.createConfigurationContext(configuration)
            assertEquals(expected, keys.map { localized.getString(it) })
        }
    }

    private fun setFixture(content: @androidx.compose.runtime.Composable () -> Unit) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            val view = LocalView.current
            DisposableEffect(view) {
                val previous = view.keepScreenOn
                view.keepScreenOn = true
                onDispose { view.keepScreenOn = previous }
            }
            Box(Modifier.fillMaxSize().testTag("hint_fixture").padding(16.dp), contentAlignment = Alignment.BottomEnd) {
                content()
            }
        }
    }

    @Test fun homeHintFollowsTheFingerAndReleaseInvokesOnlyTheSelectedAction() {
        val manual = AtomicInteger()
        val automatic = AtomicInteger()
        val alerts = AtomicInteger()
        setFixture {
            MaterialTheme {
                UvirHomeCaptureMenu(false, { manual.incrementAndGet() },
                    { automatic.incrementAndGet() }, { alerts.incrementAndGet() })
            }
        }
        val capture = compose.onNodeWithContentDescription(context.getString(R.string.save_measurement))
        capture.performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(100)
        compose.onNodeWithTag("floating_action_hint").assertDoesNotExist()
        compose.mainClock.advanceTimeBy(180)
        compose.onNodeWithText(context.getString(R.string.save_measurement)).assertIsDisplayed()
        val initialHint = compose.onNodeWithTag("floating_action_hint").fetchSemanticsNode()
        val hintBounds = initialHint.screenBounds()
        val screenBounds = compose.onNodeWithTag("hint_fixture").fetchSemanticsNode().screenBounds()
        assertEquals(screenBounds.center.x, hintBounds.center.x, 1f)
        // The gesture's original long-press threshold is unchanged.
        compose.mainClock.advanceTimeBy(420)
        capture.performTouchInput {
            moveTo(Offset(center.x - width * 66f / 56f, center.y))
        }
        compose.mainClock.advanceTimeBy(300)
        compose.onNodeWithText(context.getString(R.string.automatic_acquisition)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.save_measurement)).assertDoesNotExist()
        val automaticHint = compose.onNodeWithTag("floating_action_hint").fetchSemanticsNode()
        assertEquals(initialHint.id, automaticHint.id)
        assertEquals(hintBounds, automaticHint.screenBounds())
        capture.performTouchInput {
            moveTo(Offset(center.x, center.y - height * 66f / 56f))
        }
        compose.mainClock.advanceTimeBy(300)
        compose.onNodeWithText(context.getString(R.string.alert_registration_title)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.automatic_acquisition)).assertDoesNotExist()
        val alertHint = compose.onNodeWithTag("floating_action_hint").fetchSemanticsNode()
        assertEquals(initialHint.id, alertHint.id)
        assertEquals(hintBounds, alertHint.screenBounds())
        compose.runOnIdle {
            assertEquals(0, manual.get()); assertEquals(0, automatic.get()); assertEquals(0, alerts.get())
        }
        capture.performTouchInput { up() }
        compose.mainClock.advanceTimeBy(200)
        compose.onNodeWithTag("floating_action_hint").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(0, manual.get()); assertEquals(0, automatic.get()); assertEquals(1, alerts.get())
        }
    }

    @Test fun shortTapDoesNotLeaveALabelBehind() {
        val clicks = AtomicInteger()
        setFixture {
            MaterialTheme { UvirHomeCaptureMenu(false, { clicks.incrementAndGet() }, {}, {}) }
        }
        compose.onNodeWithContentDescription(context.getString(R.string.save_measurement))
            .performTouchInput { down(center); advanceEventTime(80); up() }
        compose.mainClock.advanceTimeBy(700)
        compose.onNodeWithTag("floating_action_hint").assertDoesNotExist()
        compose.runOnIdle { assertEquals(1, clicks.get()) }
    }

    @Test fun recAndStopHintsUseTheRightCommandInBothThemesAndDirections() {
        val active = mutableStateOf(false)
        val alert = mutableStateOf(false)
        val dark = mutableStateOf(false)
        val direction = mutableStateOf(LayoutDirection.Ltr)
        val clicks = AtomicInteger()
        setFixture {
            CompositionLocalProvider(LocalLayoutDirection provides direction.value) {
                MaterialTheme(colorScheme = if (dark.value) darkColorScheme() else lightColorScheme()) {
                    if (alert.value) UvirAlertRegistrationActionButton(active.value, 2, true) { clicks.incrementAndGet() }
                    else UvirAutomaticAcquisitionFloatingAction(active.value, 2, true, false) { clicks.incrementAndGet() }
                }
            }
        }
        var expectedClicks = 0
        for (darkTheme in listOf(false, true)) for (layoutDirection in listOf(LayoutDirection.Ltr, LayoutDirection.Rtl)) {
            for (alerts in listOf(false, true)) for (running in listOf(false, true)) {
                compose.runOnIdle {
                    dark.value = darkTheme; direction.value = layoutDirection
                    active.value = running; alert.value = alerts
                }
                compose.mainClock.advanceTimeBy(200)
                val description = when {
                    alerts && running -> context.getString(R.string.stop_all_value_alerts_accessibility)
                    alerts -> context.getString(R.string.start_value_alert_session_accessibility)
                    running -> context.getString(R.string.stop_with_count, 2)
                    else -> context.getString(R.string.start)
                }
                val label = when {
                    alerts && running -> R.string.alert_hint_stop
                    alerts -> R.string.alert_hint_start
                    running -> R.string.capture_hint_stop
                    else -> R.string.capture_hint_start
                }
                val button = compose.onNodeWithContentDescription(description)
                button.performTouchInput { down(center) }
                compose.mainClock.advanceTimeBy(100)
                compose.onNodeWithTag("floating_action_hint").assertDoesNotExist()
                compose.mainClock.advanceTimeBy(180)
                compose.onNodeWithText(context.getString(label)).assertIsDisplayed()
                val hintBounds = compose.onNodeWithTag("floating_action_hint").fetchSemanticsNode().screenBounds()
                val screenBounds = compose.onNodeWithTag("hint_fixture").fetchSemanticsNode().screenBounds()
                assertEquals(screenBounds.center.x, hintBounds.center.x, 1f)
                compose.runOnIdle { assertEquals(expectedClicks, clicks.get()) }
                button.performTouchInput { up() }
                compose.mainClock.advanceTimeBy(200)
                compose.onNodeWithTag("floating_action_hint").assertDoesNotExist()
                expectedClicks++
                compose.runOnIdle { assertEquals(expectedClicks, clicks.get()) }
            }
        }
    }

    @Test fun disabledRecNeverShowsAHintOrRunsAnAction() {
        val clicks = AtomicInteger()
        setFixture {
            MaterialTheme { UvirAutomaticAcquisitionFloatingAction(false, 0, false, false) { clicks.incrementAndGet() } }
        }
        compose.onNodeWithContentDescription(context.getString(R.string.start)).performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(700)
        compose.onNodeWithTag("floating_action_hint").assertDoesNotExist()
        compose.onNodeWithContentDescription(context.getString(R.string.start)).performTouchInput { up() }
        compose.runOnIdle { assertEquals(0, clicks.get()) }
    }
}
