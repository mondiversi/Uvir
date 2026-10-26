package me.mondiversi.uvir

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlinx.coroutines.awaitCancellation
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Callback-only optical wizard: no real source, archive, settings or sensor commands. */
class UvirOpticalWindowMeasurementUiTest {
    @get:Rule val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val window = mutableStateOf(OpticalWindowSettings(true, List(11) { 1.8f }).withMasterFactor(1.2f))
    private val enabled = mutableStateOf(true)
    private val dark = mutableStateOf(false)
    private val rtl = mutableStateOf(false)
    private val sensorId = mutableStateOf("WINDOW_TRIAL_A")
    private var reads = 0
    private var writes = 0
    private var waitForever = false
    private var cancelled = false
    private var current = true
    private var expectedCancel = Color.Unspecified

    private fun fixture() {
        compose.setContent {
            KeepUvirVerificationScreenOn()
            CompositionLocalProvider(LocalLayoutDirection provides if (rtl.value) LayoutDirection.Rtl else LayoutDirection.Ltr,
                LocalUvirNumericFormat provides UvirNumericFormat.EUROPEAN) {
                MaterialTheme(colorScheme = if (dark.value) darkColorScheme() else lightColorScheme()) {
                    expectedCancel = UvirDestructiveActionColor
                    val colors = MaterialTheme.colorScheme
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        UvirOpticalWindowSettings(window.value, { writes++; window.value = it }, enabled.value, true,
                            colors.surface, colors.onSurface, colors.onSurfaceVariant, sensorId.value,
                            onMeasureWindow = { previous, progress ->
                                reads++
                                progress(2_000)
                                if (waitForever) try { awaitCancellation() } finally { cancelled = true }
                                val snapshot = previous ?: OpticalWindowMeasurementContext(sensorId.value,
                                    SensorConnectionMode.WIFI, 1, "0.5.112", 1, 1.0, 1.0,
                                    CalibrationOffsets(), null, window.value, 5, 150, true, true, true)
                                assertEquals(sensorId.value, snapshot.deviceId)
                                OpticalWindowReading(snapshot, List(11) { index -> OpticalWindowChannelReading(
                                    if (previous == null) 1.0 else .8, 0.0, 30,
                                    if (index == 0) OpticalWindowChannelIssue.INSUFFICIENT else null) }, { current })
                            })
                    }
                }
            }
        }
    }

    private fun open() = compose.onNodeWithTag("optical_window_calculate").performScrollTo().performClick()
    private fun start() {
        compose.onNodeWithTag("optical_window_measure_start").performClick()
        compose.waitForIdle()
    }

    @Test fun twoExplicitMeasurementsAreRequiredAndOnlyValidChannelsChangeOnApply() {
        fixture()
        open()
        assertEquals(0, reads)
        assertEquals(0, writes)
        compose.onNodeWithTag("optical_window_measure_apply").assertDoesNotExist()
        start()
        assertEquals(1, reads)
        compose.onNodeWithText(context.getString(R.string.window_measure_filtered_instructions))
            .performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("optical_window_measure_apply").assertDoesNotExist()
        assertEquals(0, writes)
        start()
        assertEquals(2, reads)
        assertEquals(0, writes)
        compose.onNodeWithTag("optical_window_measure_apply").assertIsEnabled().performClick()
        assertEquals(1, writes)
        assertEquals(listOf(1.8f) + List(10) { 1.25f }, window.value.factors)
        assertEquals(1.2f, window.value.masterMultiplier, 0f)
        compose.onNodeWithTag("optical_window_measure_dialog").assertDoesNotExist()
    }

    @Test fun cancelAndStopAreRedAndLeaveFactorsUntouchedInBothThemes() {
        fixture()
        for (night in listOf(false, true)) {
            compose.runOnIdle { dark.value = night; cancelled = false; waitForever = false }
            open()
            compose.onNodeWithText(context.getString(R.string.cancel), useUnmergedTree = true)
                .assertUvirActionTextColor(expectedCancel)
            compose.onNodeWithTag("optical_window_measure_cancel").performClick()
            assertEquals(0, writes)
            open()
            compose.runOnIdle { waitForever = true }
            start()
            compose.onNodeWithText(context.getString(R.string.stop), useUnmergedTree = true)
                .assertUvirActionTextColor(expectedCancel)
            compose.onNodeWithTag("optical_window_measure_cancel").performClick()
            compose.waitUntil(5_000) { cancelled }
            compose.onNodeWithTag("optical_window_measure_start").assertIsEnabled()
            compose.onNodeWithTag("optical_window_measure_cancel").performClick()
        }
        assertEquals(0, writes)
        assertEquals(List(11) { 1.8f }, window.value.factors)
    }

    @Test fun resultsUseLocalizedNumbersAndSharedScrollingInDayNightAndRtl() {
        fixture()
        for (night in listOf(false, true)) for (direction in listOf(false, true)) {
            compose.runOnIdle { dark.value = night; rtl.value = direction }
            open(); start(); start()
            compose.onNodeWithTag("optical_window_proposal_1").performScrollTo()
            compose.onNode(hasText("×1,25") and hasAnyAncestor(hasTestTag("optical_window_proposal_1")))
                .assertIsDisplayed()
            compose.onNodeWithTag("optical_window_proposal_10").performScrollTo().assertIsDisplayed()
            compose.onNodeWithTag("optical_window_measure_apply").assertIsDisplayed().assertIsEnabled()
            File(context.getExternalFilesDir(null), "window-proposal-${night}-${direction}.png").outputStream().use {
                assertTrue(compose.onNodeWithTag("optical_window_measure_dialog").captureToImage()
                    .asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it))
            }
            compose.onNodeWithTag("optical_window_measure_cancel").performClick()
        }
        assertEquals(0, writes)
    }

    @Test fun changingSensorCancelsMeasurementAndUnavailableControlsRemainDisabled() {
        waitForever = true
        fixture()
        open(); start()
        compose.runOnIdle { sensorId.value = "WINDOW_TRIAL_B" }
        compose.waitUntil(5_000) { cancelled }
        compose.onNodeWithTag("optical_window_measure_dialog").assertDoesNotExist()
        compose.runOnIdle { enabled.value = false }
        compose.onNodeWithTag("optical_window_calculate").performScrollTo().assertIsNotEnabled()
        assertEquals(0, writes)
    }

    @Test fun staleResultCannotBeAppliedAndReopeningStartsAgainWithoutWritingSettings() {
        fixture()
        open(); start(); start()
        compose.runOnIdle { current = false; window.value = window.value.withMasterFactor(1.3f) }
        compose.onNodeWithTag("optical_window_measure_apply").assertIsNotEnabled()
        compose.onNodeWithTag("optical_window_measure_cancel").performClick()
        open()
        compose.onNodeWithTag("optical_window_measure_apply").assertDoesNotExist()
        compose.onNodeWithTag("optical_window_measure_start").assertIsEnabled()
        compose.onNodeWithTag("optical_window_measure_cancel").performClick()
        assertEquals(0, writes)
    }
}
