package me.mondiversi.uvir

import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import java.util.UUID
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Mocked preview callbacks: no real audio, hardware commands or user settings changed. */
class UvirVolumePreviewTest {
    @get:Rule val compose = createComposeRule()
    private val original = InstrumentationRegistry.getInstrumentation().targetContext
    private val namespace = "volume-test-${UUID.randomUUID()}"
    private val preferenceNames = mutableSetOf<String>()
    private val context = object : ContextWrapper(original) {
        override fun getSharedPreferences(name: String, mode: Int) =
            super.getSharedPreferences("$namespace-$name", mode).also {
                preferenceNames += "$namespace-$name"
            }
    }
    private val sliderMatcher = SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)

    @After fun cleanUp() { preferenceNames.forEach(original::deleteSharedPreferences) }

    @Test fun acquisitionAndAlertSlidersPreviewEveryGestureIncludingUnchangedTaps() {
        val acquisition = mutableStateOf(AcquisitionFeedbackSettings(sound = ThresholdAlertSound.SINGLE_BEEP, volume = 30))
        val alertVolume = mutableFloatStateOf(30f)
        val alertSound = mutableStateOf(ThresholdAlertSound.TRIPLE_BEEP.name)
        val night = mutableStateOf(false)
        val acquisitionPreviews = mutableListOf<Pair<ThresholdAlertSound, Int>>()
        val alertPreviews = mutableListOf<Pair<ThresholdAlertSound, Int>>()
        compose.setContent {
            val view = LocalView.current
            DisposableEffect(view) {
                val previous = view.keepScreenOn; view.keepScreenOn = true
                onDispose { view.keepScreenOn = previous }
            }
            MaterialTheme(colorScheme = if (night.value) darkColorScheme() else lightColorScheme()) {
                val colors = MaterialTheme.colorScheme
                CompositionLocalProvider(LocalUvirSettingsNavigation provides
                    UvirSettingsNavigation(UvirSettingsPage.ALERTS, {})) {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                        UvirSamplingAndAlertSettings(context, false, true, true,
                            "5", {}, "150", {}, true, {}, null, true, {},
                            alertSound.value, { alertSound.value = it }, alertVolume.floatValue,
                            { alertVolume.floatValue = it },
                            { sound, volume -> alertPreviews += sound to volume },
                            acquisition.value, { acquisition.value = it },
                            { sound, volume -> acquisitionPreviews += sound to volume },
                            colors.surface, colors.onSurface, colors.onSurfaceVariant)
                    }
                }
            }
        }
        for (dark in listOf(false, true)) {
            compose.runOnIdle { night.value = dark }
            for (index in 0..1) {
                val slider = compose.onAllNodes(sliderMatcher)[index].performScrollTo()
                repeat(2) {
                    slider.performTouchInput { click(Offset(width * 0.8f, height / 2f)) }
                }
                // Keyboard/TalkBack adjustments follow the same finish/preview path.
                slider.performSemanticsAction(SemanticsActions.SetProgress) { it(50f) }
            }
            // Acquisition and alert islands both expose one beep, with independent choices.
            compose.onAllNodesWithText(context.getString(R.string.threshold_sound_single_beep))
                .onLast().performScrollTo().performClick().assertIsSelected()
            compose.runOnIdle {
                assertEquals(ThresholdAlertSound.SINGLE_BEEP.name, alertSound.value)
                assertEquals(ThresholdAlertSound.SINGLE_BEEP to 50, alertPreviews.last())
                assertEquals(ThresholdAlertSound.SINGLE_BEEP, acquisition.value.sound)
                alertSound.value = ThresholdAlertSound.TRIPLE_BEEP.name
            }
        }
        compose.runOnIdle {
            assertEquals(6, acquisitionPreviews.size)
            assertEquals(8, alertPreviews.size)
            assertTrue(acquisitionPreviews.all { it.first == ThresholdAlertSound.SINGLE_BEEP })
            assertEquals(6, alertPreviews.count { it.first == ThresholdAlertSound.TRIPLE_BEEP })
            assertEquals(2, alertPreviews.count { it.first == ThresholdAlertSound.SINGLE_BEEP })
            assertEquals(acquisition.value.volume, acquisitionPreviews.last().second)
            assertEquals(alertVolume.floatValue.toInt(), alertPreviews.last().second)
            assertEquals(50, acquisitionPreviews.last().second)
            assertEquals(50, alertPreviews.last().second)
        }
    }

    @Test fun buzzerPreviewsOnlyAtGestureEndAndDisabledOrLedSlidersStaySilent() {
        val buzzerVolume = mutableFloatStateOf(30f)
        val ledBrightness = mutableFloatStateOf(30f)
        val enabled = mutableStateOf(true)
        val previews = mutableListOf<Int>()
        compose.setContent {
            MaterialTheme {
                val colors = MaterialTheme.colorScheme
                Column {
                    SensorParameterSlider("LED", ledBrightness.floatValue, true,
                        colors.onSurface, colors.onSurfaceVariant, { ledBrightness.floatValue = it })
                    SensorParameterSlider("Buzzer", buzzerVolume.floatValue, enabled.value,
                        colors.onSurface, colors.onSurfaceVariant, { buzzerVolume.floatValue = it },
                        onPreviewVolume = { previews += it })
                }
            }
        }
        compose.onAllNodes(sliderMatcher)[0].performTouchInput { click(center) }
        compose.runOnIdle { assertTrue(previews.isEmpty()) }
        val buzzer = compose.onAllNodes(sliderMatcher)[1]
        buzzer.performTouchInput { down(center); moveTo(Offset(width * 0.8f, height / 2f)) }
        compose.runOnIdle { assertTrue(previews.isEmpty()) }
        buzzer.performTouchInput { up() }
        compose.runOnIdle { assertEquals(listOf(buzzerVolume.floatValue.toInt()), previews) }
        buzzer.performSemanticsAction(SemanticsActions.SetProgress) { it(0f) }
        compose.runOnIdle { assertEquals(1, previews.last()); enabled.value = false }
        buzzer.assertIsNotEnabled().performTouchInput { click(center) }
        compose.runOnIdle { assertEquals(2, previews.size) }
    }

    @Test fun buzzerPreviewCapabilityIsExplicitAndSurvivesPartialStatusFrames() {
        val legacy = UvirSensorRuntimeInfo().updatedFrom(JSONObject("{\"status_buzzer_volume\":30}"))
        assertNull(legacy.statusBuzzerPreviewAvailable)
        val supported = legacy.updatedFrom(JSONObject("{\"buzzer_preview_supported\":true}"))
        assertTrue(supported.statusBuzzerPreviewAvailable == true)
        assertTrue(supported.updatedFrom(JSONObject("{\"buzzer_preview\":\"started\"}"))
            .statusBuzzerPreviewAvailable == true)
        assertEquals(30, supported.statusBuzzerVolume)
    }
}
