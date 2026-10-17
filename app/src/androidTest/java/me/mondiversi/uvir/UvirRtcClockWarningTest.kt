package me.mondiversi.uvir

import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.platform.app.InstrumentationRegistry
import java.util.Locale
import java.io.File
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Isolated clock snapshots/popups; never sends TIME or modifies real records/settings. */
class UvirRtcClockWarningTest {
    @get:Rule val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun hello() = JSONObject().put("type", "hello").put("protocol", UVIR_SENSOR_PROTOCOL)
        .put("device_id", "UVIR-A").put("rtc_available", true)
        .put("rtc_oscillator_stopped", true).put("rtc_read_ok", false)

    @Test fun parserOnlyAcceptsFreshAcceptedClockSnapshotsWithTheCorrectIdentity() {
        val stopped = requireNotNull(hello().toUvirRtcClockObservation("uvir-a", 123L))
        assertEquals("UVIR-A", stopped.deviceId)
        assertTrue(stopped.oscillatorStopped)
        assertEquals(123L, stopped.receivedAtMs)
        assertNull(hello().toUvirRtcClockObservation("UVIR-B", 123L))
        assertNull(hello().put("device_id", "").toUvirRtcClockObservation("UVIR-A", 123L))
        assertNull(hello().put("protocol", "wrong").toUvirRtcClockObservation("UVIR-A", 123L))
        assertNull(hello().put("type", "diagnostic").toUvirRtcClockObservation("UVIR-A", 123L))
        assertNull(hello().put("rtc_available", false).toUvirRtcClockObservation("UVIR-A", 123L))
        assertNull(hello().apply { remove("rtc_oscillator_stopped") }
            .toUvirRtcClockObservation("UVIR-A", 123L))
        assertNull(hello().put("rtc_oscillator_stopped", "true")
            .toUvirRtcClockObservation("UVIR-A", 123L))
        assertNull(hello().put("rtc_oscillator_stopped", false)
            .toUvirRtcClockObservation("UVIR-A", 123L)) // failed read is not a healthy check
        val healthy = requireNotNull(hello().put("rtc_oscillator_stopped", false)
            .put("rtc_read_ok", true).toUvirRtcClockObservation("UVIR-A", 123L))
        assertFalse(healthy.oscillatorStopped)
    }

    @Test fun allLanguagesUseGenericBatteriesAndPreserveTheOriginPlaceholder() {
        for (language in listOf("en", "it", "ar", "de", "el", "es", "fa", "fr", "hi",
            "he", "ja", "ko-KR", "pt", "ru", "sw", "pl", "nl", "id", "tr", "zh-CN")) {
            val resources = context.createConfigurationContext(Configuration(context.resources.configuration)
                .apply { setLocale(Locale.forLanguageTag(language)) }).resources
            for (key in listOf(R.string.rtc_clock_warning_title_single, R.string.rtc_clock_warning_title_multiple,
                R.string.rtc_clock_warning_message_single, R.string.rtc_clock_warning_message_multiple,
                R.string.rtc_clock_warning_batteries_guidance)) {
                val text = resources.getString(key)
                assertTrue("Empty translation: $language", text.isNotBlank())
                assertFalse("Battery chemistry/model must stay generic", text.contains("2032"))
            }
            val text = resources.getString(R.string.rtc_clock_warning_message_single)
            assertEquals("Origin placeholder: $language", 1, Regex("%1\\\$s").findAll(text).count())
            assertTrue(resources.getString(R.string.rtc_clock_warning_message_single, "Portatile")
                .contains("Portatile"))
        }
    }

    @Test fun singleAndMultipleSensorsShareOnePopupWithOnlyTheCloseIconInBothThemes() {
        val ids = mutableStateOf(listOf("UVIR-A"))
        val dark = mutableStateOf(false)
        val direction = mutableStateOf(LayoutDirection.Ltr)
        val resources = context.createConfigurationContext(Configuration(context.resources.configuration)
            .apply { setLocale(Locale.ITALIAN) }).resources
        val profiles = listOf(UvirSensorProfile(1L, "UVIR-A", "Portatile", 0L, 0L),
            UvirSensorProfile(2L, "UVIR-B", "Laboratorio", 0L, 0L))
        var dismissed = false
        compose.setContent {
            val view = LocalView.current
            DisposableEffect(view) {
                val old = view.keepScreenOn
                view.keepScreenOn = true
                onDispose { view.keepScreenOn = old }
            }
            CompositionLocalProvider(LocalResources provides resources, LocalLayoutDirection provides direction.value) {
                MaterialTheme(colorScheme = if (dark.value) darkColorScheme() else lightColorScheme()) {
                    UvirRtcClockWarningDialog(ids.value, profiles,
                        if (dark.value) Color(0xFF1C242B) else Color.White,
                        MaterialTheme.colorScheme.onSurface, { dismissed = true })
                }
            }
        }
        for (night in listOf(false, true)) for (layout in listOf(LayoutDirection.Ltr, LayoutDirection.Rtl)) {
            compose.runOnIdle { dark.value = night; direction.value = layout; ids.value = listOf("UVIR-A") }
            compose.onNodeWithText(resources.getString(R.string.rtc_clock_warning_title_single)).assertIsDisplayed()
            compose.onNodeWithText(resources.getString(R.string.rtc_clock_warning_message_single, "Portatile"))
                .assertIsDisplayed()
            compose.runOnIdle { ids.value = listOf("UVIR-A", "UVIR-B") }
            compose.onAllNodesWithTag("rtc_clock_warning_dialog").assertCountEquals(1)
            compose.onNodeWithText(resources.getString(R.string.rtc_clock_warning_title_multiple)).assertIsDisplayed()
            compose.onNodeWithText("• Portatile").assertIsDisplayed()
            compose.onNodeWithText("• Laboratorio").assertIsDisplayed()
            compose.onNodeWithText(resources.getString(R.string.close)).assertDoesNotExist()
            compose.onAllNodes(hasClickAction()).assertCountEquals(2)
            compose.onNodeWithText(resources.getString(R.string.rtc_clock_warning_show_each_launch))
                .assertIsOn().assertIsDisplayed()
            if (layout == LayoutDirection.Ltr) {
                File(context.externalCacheDir, "rtc-clock-warning-${if (night) "night" else "day"}.png")
                    .outputStream().use {
                        compose.onNodeWithTag("rtc_clock_warning_dialog").captureToImage()
                            .asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
                    }
            }
        }
        compose.onNodeWithContentDescription(resources.getString(R.string.close)).performClick()
        compose.runOnIdle { assertTrue(dismissed) }
    }

    @Test fun longSensorListScrollsButTheCloseIconStaysVisibleInLandscape() {
        val count = mutableStateOf(1)
        val height = mutableStateOf(320)
        compose.setContent {
            val view = LocalView.current
            DisposableEffect(view) {
                val old = view.keepScreenOn
                view.keepScreenOn = true
                onDispose { view.keepScreenOn = old }
            }
            val compact = Configuration(LocalConfiguration.current).apply { screenHeightDp = height.value }
            CompositionLocalProvider(LocalConfiguration provides compact) {
                MaterialTheme {
                    UvirRtcClockWarningDialog((1..count.value).map { "UVIR-$it" }, emptyList(),
                        Color.White, Color.Black, {})
                }
            }
        }
        val scroll = compose.onNodeWithTag("uvir_dialog_scroll")
        compose.runOnIdle { count.value = 40 }
        assertTrue(scroll.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].maxValue() > 0f)
        compose.onNodeWithText("• UVIR-40").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.rtc_clock_warning_show_each_launch))
            .performScrollTo().assertIsDisplayed().assertIsOn()
        compose.onNodeWithContentDescription(context.getString(R.string.close)).assertIsDisplayed()
        compose.runOnIdle { count.value = 1 }
        // With the new accessible checkbox, even one sensor may legitimately
        // overflow a short landscape viewport. It must remain fully reachable.
        compose.onNodeWithText(context.getString(R.string.rtc_clock_warning_show_each_launch))
            .performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription(context.getString(R.string.close)).assertIsDisplayed()
        compose.runOnIdle { height.value = 640 }
        assertEquals(0f, scroll.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].maxValue())
    }
}
