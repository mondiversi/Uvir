package me.mondiversi.uvir

import android.content.res.Configuration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class UvirHomeCaptureMenuTest {
    @get:Rule val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun idleCaptureColorBreathesWithItsSizeAndResetsWhenTheMenuOpens() {
        val dark = mutableStateOf(false)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            val configuration = Configuration(LocalConfiguration.current).apply {
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                    if (dark.value) Configuration.UI_MODE_NIGHT_YES
                    else Configuration.UI_MODE_NIGHT_NO
            }
            CompositionLocalProvider(LocalConfiguration provides configuration) {
                key(dark.value) {
                    MaterialTheme(colorScheme = if (dark.value) darkColorScheme() else lightColorScheme()) {
                        UvirHomeCaptureMenu(false, {}, {}, {})
                    }
                }
            }
        }
        val capture = compose.onNodeWithContentDescription(context.getString(R.string.save_measurement))
        fun containerPixel(): Color {
            val pixels = capture.captureToImage().toPixelMap()
            // Inside the circle, to the left of the shutter glyph.
            return pixels[(pixels.width * 0.18f).toInt(), pixels.height / 2]
        }
        for (night in listOf(false, true)) {
            compose.runOnIdle { dark.value = night }
            compose.mainClock.advanceTimeBy(32)
            compose.waitForIdle()
            val base = containerPixel()
            val startWidth = capture.fetchSemanticsNode().boundsInRoot.width
            compose.mainClock.advanceTimeBy(1_050)
            compose.waitForIdle()
            val peak = containerPixel()
            assertTrue("The color peak accompanies the expanded button",
                capture.fetchSemanticsNode().boundsInRoot.width > startWidth * 1.08f)
            assertTrue("Idle shade lightens in either theme: night=$night",
                peak.luminance() > base.luminance() + 0.005f)
            val expectedBase = if (night) darkColorScheme().primary else lightColorScheme().primary
            val expectedPeak = lerp(expectedBase, Color.White, 0.30f)
            assertEquals("Visible brightening at the size peak", expectedPeak.red, peak.red, 0.015f)
            assertEquals(expectedPeak.green, peak.green, 0.015f)
            assertEquals(expectedPeak.blue, peak.blue, 0.015f)
            capture.performSemanticsAction(SemanticsActions.OnLongClick) { it() }
            compose.mainClock.advanceTimeBy(400)
            compose.waitForIdle()
            val reset = containerPixel()
            val expected = if (night) darkColorScheme().primary else lightColorScheme().primary
            assertEquals(expected.red, reset.red, 0.01f)
            assertEquals(expected.green, reset.green, 0.01f)
            assertEquals(expected.blue, reset.blue, 0.01f)
        }
    }

    @Test fun tapCapturesAndAccessibleLongPressOpensBothChoices() {
        val manual = AtomicInteger()
        val automatic = AtomicInteger()
        val alert = AtomicInteger()
        compose.setContent {
            MaterialTheme {
                UvirHomeCaptureMenu(
                    alertActive = false,
                    onManual = { manual.incrementAndGet() },
                    onAutomatic = { automatic.incrementAndGet() },
                    onAlert = { alert.incrementAndGet() }
                )
            }
        }

        val capture = compose.onNodeWithContentDescription(context.getString(R.string.save_measurement))
        val automaticOption = context.getString(R.string.automatic_acquisition)
        val alertOption = context.getString(R.string.start_value_alert_session_accessibility)
        compose.onNodeWithContentDescription(automaticOption).assertDoesNotExist()
        capture.performClick()
        compose.runOnIdle { assertEquals(1, manual.get()) }

        capture.performSemanticsAction(SemanticsActions.OnLongClick) { it() }
        compose.onNodeWithContentDescription(automaticOption).performClick()
        compose.runOnIdle {
            assertEquals(1, automatic.get())
            assertEquals(1, manual.get())
        }

        capture.performSemanticsAction(SemanticsActions.OnLongClick) { it() }
        compose.onNodeWithContentDescription(alertOption).performClick()
        compose.runOnIdle { assertEquals(1, alert.get()) }
    }

    @Test fun holdAndSlideChoosesTheOptionWithoutManualCapture() {
        val manual = AtomicInteger()
        val automatic = AtomicInteger()
        val alert = AtomicInteger()
        compose.setContent {
            MaterialTheme {
                UvirHomeCaptureMenu(
                    alertActive = false,
                    onManual = { manual.incrementAndGet() },
                    onAutomatic = { automatic.incrementAndGet() },
                    onAlert = { alert.incrementAndGet() }
                )
            }
        }
        val capture = compose.onNodeWithContentDescription(context.getString(R.string.save_measurement))
        capture.performTouchInput {
            down(center)
            advanceEventTime(700)
            moveTo(Offset(center.x - width * 59f / 56f, center.y))
            up()
        }
        compose.runOnIdle {
            assertEquals(0, manual.get())
            assertEquals(1, automatic.get())
        }
        capture.performTouchInput {
            down(center)
            advanceEventTime(700)
            moveTo(Offset(center.x, center.y - height * 59f / 56f))
            up()
        }
        compose.runOnIdle {
            assertEquals(0, manual.get())
            assertEquals(1, alert.get())
        }
    }

    @Test fun releasingAfterHoldOnCaptureOpensManualAcquisition() {
        val manual = AtomicInteger()
        compose.setContent {
            MaterialTheme {
                UvirHomeCaptureMenu(
                    alertActive = false,
                    onManual = { manual.incrementAndGet() },
                    onAutomatic = {},
                    onAlert = {}
                )
            }
        }
        compose.onNodeWithContentDescription(context.getString(R.string.save_measurement))
            .performTouchInput {
                down(center)
                advanceEventTime(700)
                up()
            }
        compose.runOnIdle { assertEquals(1, manual.get()) }
    }

    @Test fun releasingOutsideAllControlsAfterHoldDoesNotCapture() {
        val manual = AtomicInteger()
        val automatic = AtomicInteger()
        val alert = AtomicInteger()
        compose.setContent {
            MaterialTheme {
                UvirHomeCaptureMenu(
                    alertActive = false,
                    onManual = { manual.incrementAndGet() },
                    onAutomatic = { automatic.incrementAndGet() },
                    onAlert = { alert.incrementAndGet() }
                )
            }
        }
        compose.onNodeWithContentDescription(context.getString(R.string.save_measurement))
            .performTouchInput {
                down(center)
                advanceEventTime(700)
                moveTo(Offset(-width * 2f, -height * 2f))
                up()
            }
        compose.runOnIdle {
            assertEquals(0, manual.get())
            assertEquals(0, automatic.get())
            assertEquals(0, alert.get())
        }
    }

    @Test fun immediateDirectionalDragOpensTheChosenView() {
        val manual = AtomicInteger()
        val automatic = AtomicInteger()
        val alert = AtomicInteger()
        compose.setContent {
            MaterialTheme {
                UvirHomeCaptureMenu(
                    alertActive = false,
                    onManual = { manual.incrementAndGet() },
                    onAutomatic = { automatic.incrementAndGet() },
                    onAlert = { alert.incrementAndGet() }
                )
            }
        }
        val capture = compose.onNodeWithContentDescription(context.getString(R.string.save_measurement))
        capture.performTouchInput {
            down(center)
            advanceEventTime(60)
            moveTo(Offset(center.x - width * 59f / 56f, center.y))
            up()
        }
        capture.performTouchInput {
            down(center)
            advanceEventTime(60)
            moveTo(Offset(center.x, center.y - height * 59f / 56f))
            up()
        }
        compose.runOnIdle {
            assertEquals(0, manual.get())
            assertEquals(1, automatic.get())
            assertEquals(1, alert.get())
        }
    }

    @Test fun automaticSessionCountOpensOnlyAutomaticAcquisition() {
        val manual = AtomicInteger()
        val automatic = AtomicInteger()
        val alert = AtomicInteger()
        compose.setContent {
            MaterialTheme {
                UvirHomeCaptureMenu(
                    alertActive = false,
                    onManual = { manual.incrementAndGet() },
                    onAutomatic = { automatic.incrementAndGet() },
                    onAlert = { alert.incrementAndGet() },
                    automaticActive = true,
                    completedCount = 3,
                    automaticIndicatorColor = Color.Blue,
                    automaticContainerColor = Color.White
                )
            }
        }
        val count = compose.onNodeWithContentDescription(
            context.getString(R.string.automatic_completed_count, 3)
        )
        count.performClick()
        compose.onNodeWithContentDescription(context.getString(R.string.automatic_acquisition))
            .assertDoesNotExist()
        compose.onNodeWithContentDescription(
            context.getString(R.string.start_value_alert_session_accessibility)
        ).assertDoesNotExist()
        count.performTouchInput {
            down(center)
            advanceEventTime(700)
            up()
        }
        count.performTouchInput {
            down(center)
            advanceEventTime(700)
            moveTo(Offset(center.x, center.y - height * 59f / 56f))
            up()
        }
        compose.runOnIdle {
            assertEquals(0, manual.get())
            assertEquals(2, automatic.get())
            assertEquals(0, alert.get())
        }
    }

    @Test fun alertSessionCountOpensOnlyAlertRegistration() {
        val manual = AtomicInteger()
        val automatic = AtomicInteger()
        val alert = AtomicInteger()
        compose.setContent {
            MaterialTheme {
                UvirHomeCaptureMenu(
                    alertActive = true,
                    onManual = { manual.incrementAndGet() },
                    onAutomatic = { automatic.incrementAndGet() },
                    onAlert = { alert.incrementAndGet() },
                    alertCompletedCount = 2
                )
            }
        }
        val count = compose.onNodeWithContentDescription(
            "${context.getString(R.string.alert_registration_count)}: 2"
        )
        count.performClick()
        count.performTouchInput {
            down(center)
            advanceEventTime(700)
            up()
        }
        compose.onNodeWithContentDescription(context.getString(R.string.automatic_acquisition))
            .assertDoesNotExist()
        compose.onNodeWithContentDescription(context.getString(R.string.save_measurement))
            .assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(0, manual.get())
            assertEquals(0, automatic.get())
            assertEquals(2, alert.get())
        }
    }

    @Test fun radialChoicesAreCenteredOnTheirRespectiveButtonAxes() {
        compose.setContent {
            MaterialTheme {
                UvirHomeCaptureMenu(false, {}, {}, {})
            }
        }
        val capture = compose.onNodeWithContentDescription(context.getString(R.string.save_measurement))
        capture.performSemanticsAction(SemanticsActions.OnLongClick) { it() }
        compose.waitForIdle()
        val captureBounds = capture.fetchSemanticsNode().boundsInRoot
        val automaticBounds = compose.onNodeWithContentDescription(
            context.getString(R.string.automatic_acquisition)
        ).fetchSemanticsNode().boundsInRoot
        val alertBounds = compose.onNodeWithContentDescription(
            context.getString(R.string.start_value_alert_session_accessibility)
        ).fetchSemanticsNode().boundsInRoot
        assertEquals(
            (captureBounds.top + captureBounds.bottom) / 2f,
            (automaticBounds.top + automaticBounds.bottom) / 2f,
            1f
        )
        assertEquals(
            (captureBounds.left + captureBounds.right) / 2f,
            (alertBounds.left + alertBounds.right) / 2f,
            1f
        )
    }

    @Test fun automaticChoiceUsesTheSessionGreenInBothThemes() {
        val dark = mutableStateOf(false)
        compose.setContent {
            val configuration = Configuration(LocalConfiguration.current).apply {
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                    if (dark.value) Configuration.UI_MODE_NIGHT_YES
                    else Configuration.UI_MODE_NIGHT_NO
            }
            CompositionLocalProvider(LocalConfiguration provides configuration) {
                MaterialTheme { UvirHomeCaptureMenu(false, {}, {}, {}) }
            }
        }
        val capture = compose.onNodeWithContentDescription(context.getString(R.string.save_measurement))
        val automatic = context.getString(R.string.automatic_acquisition)
        for (night in listOf(false, true)) {
            compose.runOnIdle { dark.value = night }
            capture.performSemanticsAction(SemanticsActions.OnLongClick) { it() }
            compose.waitForIdle()
            val expected = uvirSessionIndicatorColor(night)
            val pixels = compose.onNodeWithContentDescription(automatic)
                .captureToImage().toPixelMap()
            var matching = 0
            for (y in 0 until pixels.height) for (x in 0 until pixels.width) {
                val pixel = pixels[x, y]
                if (kotlin.math.abs(pixel.red - expected.red) < 0.025f &&
                    kotlin.math.abs(pixel.green - expected.green) < 0.025f &&
                    kotlin.math.abs(pixel.blue - expected.blue) < 0.025f) matching++
            }
            assertTrue("Automatic shortcut uses session green: night=$night", matching > 80)
        }
    }

    @Test fun alertChoiceUsesTheSessionOrangeInBothThemes() {
        val dark = mutableStateOf(false)
        compose.setContent {
            val configuration = Configuration(LocalConfiguration.current).apply {
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                    if (dark.value) Configuration.UI_MODE_NIGHT_YES
                    else Configuration.UI_MODE_NIGHT_NO
            }
            CompositionLocalProvider(LocalConfiguration provides configuration) {
                MaterialTheme { UvirHomeCaptureMenu(false, {}, {}, {}) }
            }
        }
        val capture = compose.onNodeWithContentDescription(context.getString(R.string.save_measurement))
        val alert = context.getString(R.string.start_value_alert_session_accessibility)
        for (night in listOf(false, true)) {
            compose.runOnIdle { dark.value = night }
            capture.performSemanticsAction(SemanticsActions.OnLongClick) { it() }
            compose.waitForIdle()
            val expected = uvirAlertSessionIndicatorColor(night)
            val pixels = compose.onNodeWithContentDescription(alert)
                .captureToImage().toPixelMap()
            var matching = 0
            for (y in 0 until pixels.height) for (x in 0 until pixels.width) {
                val pixel = pixels[x, y]
                if (kotlin.math.abs(pixel.red - expected.red) < 0.025f &&
                    kotlin.math.abs(pixel.green - expected.green) < 0.025f &&
                    kotlin.math.abs(pixel.blue - expected.blue) < 0.025f) matching++
            }
            assertTrue("Alert shortcut uses session orange: night=$night", matching > 80)
        }
    }
}
