package me.mondiversi.uvir

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.Locale
import java.util.UUID
import java.io.File

/** Frozen UI and captured callbacks only: no runtime, USB or archive is opened. */
class UvirUsbPhoneAutomaticPageTest {
    @get:Rule val compose = createComposeRule()
    private val base = InstrumentationRegistry.getInstrumentation().targetContext
    private val prefix = "automatic-page-${UUID.randomUUID()}-"
    private val localized = base.createConfigurationContext(Configuration(base.resources.configuration).apply {
        setLocale(Locale.ITALY)
    })
    private val context = object : ContextWrapper(localized) {
        override fun getApplicationContext(): Context = this
        override fun getSharedPreferences(name: String, mode: Int) = base.getSharedPreferences(prefix + name, mode)
    }
    private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    @After fun cleanup() { base.deleteSharedPreferences(prefix + PREFS_NAME) }
    private fun scrollTo(index: Int) = compose.onNodeWithTag("automatic_form_list").performScrollToIndex(index)
    private fun screenshot(name: String) {
        File(base.filesDir, name).outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private fun seed(conditional: Boolean = false) {
        assertTrue(base.packageName.endsWith(".verification"))
        preferences.edit().putString("usb_auto_interval", "90").putString("usb_auto_delay", "120")
            .putString("usb_auto_duration", "3600").putString("usb_auto_limit", "12")
            .putString("usb_auto_note", "Existing note").putBoolean("usb_auto_conditional", conditional)
            .putString("usb_auto_action", AcquisitionConditionAction.START.name).commit()
        saveAcquisitionConditions(preferences, listOf(
            ThresholdAlertRule(ThresholdAlertMetric.UVA, true, ThresholdAlertDirection.ABOVE, .1f)))
    }

    @Test fun originalIslandsAndTimeFieldsPreserveExistingConfigurationAndDisabledDrafts() {
        seed()
        var visible by mutableStateOf(true)
        var dark by mutableStateOf(false)
        var request: AutomaticAcquisitionRequest? = null
        compose.setContent {
            KeepUvirVerificationScreenOn()
            val configuration = Configuration(context.resources.configuration).apply {
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                    if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            }
            CompositionLocalProvider(LocalContext provides context, LocalConfiguration provides configuration) {
                MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
                    UvirActionTheme {
                        val colors = MaterialTheme.colorScheme
                        if (visible) UsbPhoneAutomaticPage(UsbPhoneState(status = UsbPhoneStatus.READY, sample = SensorSample()),
                            if (dark) androidx.compose.ui.graphics.Color(0xff101418) else androidx.compose.ui.graphics.Color(0xfff4f7f9),
                            if (dark) androidx.compose.ui.graphics.Color(0xff1c242b) else androidx.compose.ui.graphics.Color.White,
                            colors.onSurface, colors.onSurfaceVariant,
                            {}, { request = it }, {})
                    }
                }
            }
        }
        for (night in listOf(false, true)) {
            compose.runOnIdle { dark = night }
            scrollTo(0)
            compose.onNodeWithTag("automatic_interval_hours").performScrollTo().assertTextContains("0")
            compose.onNodeWithTag("automatic_interval_minutes").assertTextContains("1")
            compose.onNodeWithTag("automatic_interval_seconds").assertTextContains("30")
            screenshot(if (night) "automatic-restored-night.png" else "automatic-restored-day.png")
            scrollTo(1)
            compose.onNodeWithText(context.getString(R.string.scheduled_start), useUnmergedTree = true).assertExists()
            scrollTo(2)
            compose.onNodeWithText(context.getString(R.string.scheduled_end), useUnmergedTree = true).performScrollTo().assertIsDisplayed()
            scrollTo(3)
            compose.onNodeWithText(context.getString(R.string.limit_acquisitions), useUnmergedTree = true).performScrollTo().assertIsDisplayed()
            scrollTo(4)
            compose.onNodeWithText(context.getString(R.string.conditional_acquisition), useUnmergedTree = true).performScrollTo().assertIsDisplayed()
            scrollTo(5)
            compose.onNodeWithTag("automatic_note").performScrollTo()
            compose.onNodeWithText("Existing note").assertIsDisplayed()
            compose.onNodeWithText(context.getString(R.string.external_command)).assertDoesNotExist()
            compose.onNodeWithText(context.getString(R.string.usb_phone_note)).assertDoesNotExist()
        }
        compose.onNodeWithTag("usb_automatic_action").performClick()
        compose.runOnIdle {
            assertNotNull(request)
            assertEquals(90L, request!!.intervalSeconds)
            assertEquals(120L, request!!.startDelaySeconds)
            assertEquals(3600L, request!!.durationSeconds)
            assertEquals(12, request!!.maxAcquisitions)
            assertEquals("Existing note", request!!.note)
            assertFalse(request!!.externalCommand)
        }
        scrollTo(1)
        compose.onNodeWithText(context.getString(R.string.scheduled_start), useUnmergedTree = true).performScrollTo().performClick()
        compose.waitForIdle()
        compose.runOnIdle { visible = false }
        compose.runOnIdle {
            assertEquals("0", preferences.getString("usb_auto_delay", null))
            assertEquals("120", preferences.getString("usb_auto_delay_value", null))
            visible = true
        }
        scrollTo(1)
        compose.onNodeWithText(context.getString(R.string.scheduled_start), useUnmergedTree = true).performScrollTo().performClick()
        compose.onNodeWithTag("usb_automatic_action").performClick()
        compose.runOnIdle { assertEquals(120L, request!!.startDelaySeconds) }
    }

    @Test fun runningSessionKeepsScheduleAndNoteReadOnlyAndShowsTheOriginalStopDialog() {
        seed(conditional = true)
        var stopped = false
        compose.setContent {
            KeepUvirVerificationScreenOn()
            CompositionLocalProvider(LocalContext provides context) {
                MaterialTheme {
                    UvirActionTheme {
                        val colors = MaterialTheme.colorScheme
                        UsbPhoneAutomaticPage(UsbPhoneState(automaticSession = 9, automaticWaitingForCondition = true),
                            colors.background, colors.surface, colors.onSurface, colors.onSurfaceVariant,
                            {}, { error("Must not start another session") }, { stopped = true })
                    }
                }
            }
        }
        compose.onNodeWithTag("automatic_interval_seconds").assertIsNotEnabled()
        scrollTo(4)
        compose.onNodeWithText(context.getString(R.string.conditional_waiting)).performScrollTo().assertIsDisplayed()
        scrollTo(5)
        compose.onNodeWithTag("automatic_note").performScrollTo()
        compose.onNode(hasText("Existing note") and hasAnyAncestor(hasTestTag("automatic_note"))).assertIsNotEnabled()
        compose.onNodeWithTag("usb_automatic_action").performClick()
        compose.onNodeWithText(context.getString(R.string.stop_automatic_confirmation_title)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.continue_acquisition)).performClick()
        compose.runOnIdle { assertFalse(stopped) }
    }
}
