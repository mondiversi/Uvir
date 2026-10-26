package me.mondiversi.uvir

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.espresso.Espresso.pressBack
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class UvirDarkOffsetsUiTest {
    @get:Rule val compose = createComposeRule()
    private val dark = mutableStateOf(false)
    private val supported = mutableStateOf(true)
    private val editable = mutableStateOf(true)
    private val window = mutableStateOf(OpticalWindowSettings(true, List(11) { 1.5f }))
    private val offsets = mutableStateOf(CalibrationOffsets(List(11) { 2f }))
    private val gains = mutableStateOf(CalibrationChannelFactors(List(11) { 1.5f }))
    private var updates = 0
    private var measures = 0
    private var waitForever = false
    private var cancelled = false
    private var cleanupGate: CompletableDeferred<Unit>? = null
    private val windowEditorId = "DARK-UI-${System.nanoTime()}"
    private var expectedCancelColor = Color.Unspecified
    private var expectedPrimaryColor = Color.Unspecified

    private fun fixture() {
        compose.setContent {
            KeepUvirVerificationScreenOn()
            val config = uvirThemeConfiguration(LocalConfiguration.current, if (dark.value) UvirAppTheme.DARK else UvirAppTheme.LIGHT)
            CompositionLocalProvider(LocalConfiguration provides config,
                LocalUvirSettingsActionButtons provides true,
                LocalUvirNumericFormat provides UvirNumericFormat.EUROPEAN,
                LocalUvirIrradianceUnit provides UvirIrradianceUnit.W_M2) {
                MaterialTheme(colorScheme = if (dark.value) darkColorScheme() else lightColorScheme()) {
                    val colors = MaterialTheme.colorScheme
                    val cancelColor = UvirDestructiveActionColor
                    SideEffect { expectedCancelColor = cancelColor; expectedPrimaryColor = colors.primary }
                    Column(Modifier.width(340.dp).height(620.dp).verticalScroll(rememberScrollState())) {
                        UvirSensorCalibrationSettings(LocalContext.current, editable.value, true,
                            "1.0", {}, "1.0", {}, colors.surface, colors.onSurface, colors.onSurfaceVariant,
                            offsets.value, true, { offsets.value = it; updates++ }, gains.value, true,
                            { gains.value = it }, sensorDeviceId = "FIXTURE", darkMeasurementSupported = supported.value,
                            onMeasureDarkOffsets = { group, progress ->
                                measures++
                                progress(15_000)
                                if (waitForever) try { awaitCancellation() } finally {
                                    withContext(NonCancellable) { cleanupGate?.await() }
                                    cancelled = true
                                }
                                DarkOffsetReading(group, List(group.indices.count()) { 3f }, 30)
                            })
                        UvirOpticalWindowSettings(window.value, { window.value = it }, true, true,
                            colors.surface, colors.onSurface, colors.onSurfaceVariant, windowEditorId)
                    }
                }
            }
        }
    }

    @Test fun genericOffsetPreviewExplainsReferenceMeasurementsAndOpensWithoutMeasuring() {
        fixture()
        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        for (night in listOf(false, true)) {
            compose.runOnIdle { dark.value = night }
            compose.onNodeWithTag("dark_offsets_uv").performScrollTo().performClick()
            compose.onNodeWithText(context.getString(R.string.dark_offsets_calculate)).assertIsDisplayed()
            compose.onNodeWithText(context.getString(R.string.dark_offsets_instructions))
                .performScrollTo().assertIsDisplayed()
            compose.onNodeWithText(context.getString(R.string.dark_offsets_uv_method))
                .performScrollTo().assertIsDisplayed()
            compose.onNodeWithTag("dark_offsets_start").assertIsDisplayed().assertIsEnabled()
            File(context.getExternalFilesDir(null), "offset-reference-${if (night) "night" else "day"}.png")
                .outputStream().use {
                    assertTrue(compose.onNodeWithTag("dark_offsets_dialog_uv").captureToImage()
                        .asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it))
                }
            compose.onNodeWithTag("dark_offsets_cancel").performClick()
        }
        assertEquals(0, measures)
        assertEquals(0, updates)
        assertEquals(List(11) { 2f }, offsets.value.values)
    }

    @Test fun previewWritesNothingUntilExplicitApplyAndPreservesTheOtherGroupAndFactors() {
        fixture()
        compose.onNodeWithTag("dark_offsets_uv").performScrollTo().performClick()
        assertEquals(0, measures)
        assertEquals(0, updates)
        compose.onNodeWithTag("dark_offsets_start").performClick()
        compose.waitUntil(5_000) { measures == 1 }
        compose.onNodeWithTag("dark_offsets_apply").assertIsEnabled()
        assertEquals(List(11) { 2f }, offsets.value.values)
        compose.onNodeWithTag("dark_offsets_apply").performClick()
        compose.waitUntil(5_000) { updates == 1 }
        assertEquals(List(3) { 3f } + List(8) { 2f }, offsets.value.values)
        assertEquals(List(11) { 1.5f }, gains.value.values)
        assertEquals(List(11) { 1.5f }, window.value.factors)
    }

    @Test fun closingDuringMeasurementCancelsWithoutChangingOffsets() {
        waitForever = true
        fixture()
        compose.onNodeWithTag("dark_offsets_uv").performScrollTo().performClick()
        compose.onNodeWithTag("dark_offsets_start").performClick()
        compose.waitUntil(5_000) { measures == 1 }
        pressBack()
        compose.waitUntil(5_000) { cancelled }
        compose.onNodeWithTag("dark_offsets_dialog_uv").assertDoesNotExist()
        assertEquals(0, updates)
        assertEquals(List(11) { 2f }, offsets.value.values)
    }

    @Test fun cancelAndStopStayRedBeforeDuringAndAfterMeasurementInBothThemes() {
        fixture()
        val resources = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext.resources
        for (night in listOf(false, true)) {
            compose.runOnIdle { dark.value = night; waitForever = true; cancelled = false }
            compose.onNodeWithTag(if (night) "dark_offsets_vis" else "dark_offsets_uv").performScrollTo().performClick()
            compose.onNodeWithText(resources.getString(R.string.cancel), useUnmergedTree = true)
                .assertUvirActionTextColor(expectedCancelColor)
            compose.onNodeWithTag("dark_offsets_start").performClick()
            compose.onNodeWithText(resources.getString(R.string.stop), useUnmergedTree = true)
                .assertUvirActionTextColor(expectedCancelColor)
            compose.onNodeWithTag("dark_offsets_cancel").performClick()
            compose.waitUntil(5_000) { cancelled }
            compose.runOnIdle { waitForever = false }
            compose.onNodeWithTag("dark_offsets_start").performClick()
            compose.onNodeWithTag("dark_offsets_apply").assertIsEnabled()
            compose.onNodeWithText(resources.getString(R.string.cancel), useUnmergedTree = true)
                .assertUvirActionTextColor(expectedCancelColor)
            compose.onNodeWithTag("dark_offsets_cancel").performClick()
        }
        assertEquals(0, updates)
        assertEquals(List(11) { 2f }, offsets.value.values)
    }

    @Test fun cancelBeforeMeasurementAndAfterProposalNeverAppliesOffsets() {
        fixture()
        compose.onNodeWithTag("dark_offsets_uv").performScrollTo().performClick()
        compose.onNodeWithTag("dark_offsets_cancel").assertIsEnabled().performClick()
        compose.onNodeWithTag("dark_offsets_dialog_uv").assertDoesNotExist()
        assertEquals(0, measures)
        compose.onNodeWithTag("dark_offsets_vis").performScrollTo().performClick()
        compose.onNodeWithTag("dark_offsets_start").performClick()
        compose.onNodeWithTag("dark_offsets_apply").assertIsEnabled()
        compose.onNodeWithTag("dark_offsets_cancel").performClick()
        compose.onNodeWithTag("dark_offsets_dialog_vis").assertDoesNotExist()
        assertEquals(0, updates)
        assertEquals(List(11) { 2f }, offsets.value.values)
    }

    @Test fun stopWaitsForCleanupThenAllowsRestartWithoutClosingOrApplying() {
        waitForever = true
        cleanupGate = CompletableDeferred()
        fixture()
        compose.onNodeWithTag("dark_offsets_uv").performScrollTo().performClick()
        compose.onNodeWithTag("dark_offsets_start").performClick()
        compose.waitUntil(5_000) { measures == 1 }
        compose.onNodeWithTag("dark_offsets_cancel").performClick()
        compose.onNodeWithTag("dark_offsets_start").assertDoesNotExist()
        compose.onNodeWithTag("dark_offsets_cancel").assertIsNotEnabled()
        compose.onNodeWithTag("dark_offsets_dialog_uv").assertIsDisplayed()
        compose.runOnIdle { cleanupGate!!.complete(Unit) }
        compose.waitUntil(5_000) { cancelled }
        compose.onNodeWithTag("dark_offsets_start").assertIsEnabled()
        compose.runOnIdle { waitForever = false }
        compose.onNodeWithTag("dark_offsets_start").performClick()
        compose.onNodeWithTag("dark_offsets_apply").assertIsEnabled()
        compose.onNodeWithTag("dark_offsets_cancel").performClick()
        assertEquals(2, measures)
        assertEquals(0, updates)
        assertEquals(List(11) { 2f }, offsets.value.values)
    }

    @Test fun unavailableFirmwareAndActiveSensorAreDisabledInBothThemes() {
        fixture()
        for (night in listOf(false, true)) {
            compose.runOnIdle { dark.value = night; supported.value = false }
            compose.onNodeWithTag("dark_offsets_uv").performScrollTo().assertIsNotEnabled()
            compose.runOnIdle { supported.value = true; editable.value = false }
            compose.onNodeWithTag("dark_offsets_uv").assertIsNotEnabled()
            compose.onNodeWithTag("calibration_reset_uv").assertIsNotEnabled()
        }
        assertEquals(0, measures)
        assertEquals(0, updates)
    }

    @Test fun resetNeedsTwoSecondsAndFiresWhileStillHeldOnlyForItsGroup() {
        fixture()
        compose.onNodeWithTag("calibration_reset_uv").performScrollTo().performClick()
        assertEquals(0, updates)
        compose.onNodeWithTag("calibration_reset_dialog").assertIsDisplayed()
        val button = compose.onNodeWithTag("calibration_reset_confirm")
        compose.mainClock.autoAdvance = false
        button.performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(1_500)
        assertEquals(0, updates)
        compose.mainClock.advanceTimeBy(650)
        compose.runOnIdle { assertEquals(1, updates) }
        assertEquals(List(3) { 0f } + List(8) { 2f }, offsets.value.values)
        assertEquals(List(3) { 1f } + List(8) { 1.5f }, gains.value.values)
        compose.mainClock.autoAdvance = true
        compose.onNodeWithTag("calibration_reset_dialog").assertDoesNotExist()
        assertEquals(1, updates)
    }

    @Test fun releasingTooSoonCancelsTheReset() {
        fixture()
        compose.onNodeWithTag("calibration_reset_vis").performScrollTo().performClick()
        compose.onNodeWithTag("calibration_reset_dialog").assertIsDisplayed()
        val button = compose.onNodeWithTag("calibration_reset_confirm")
        compose.mainClock.autoAdvance = false
        button.performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(1_000)
        button.performTouchInput { up() }
        compose.mainClock.advanceTimeBy(2_000)
        assertEquals(0, updates)
        compose.mainClock.autoAdvance = true
        compose.onNodeWithTag("calibration_reset_cancel").performClick()
        compose.onNodeWithTag("calibration_reset_dialog").assertDoesNotExist()
    }

    @Test fun windowResetKeepsAllChannelsVisibleAndNeverChangesCalibration() {
        window.value = window.value.withMasterFactor(1.2f)
        fixture()
        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        compose.onNodeWithTag("optical_window_reset").performScrollTo().performClick()
        compose.onNodeWithTag("calibration_reset_dialog").assertIsDisplayed()
        assertEquals(List(11) { 1.5f }, window.value.factors)
        val button = compose.onNodeWithTag("calibration_reset_confirm")
        compose.mainClock.autoAdvance = false
        button.performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(2_150)
        compose.mainClock.autoAdvance = true
        compose.waitUntil(5_000) { window.value.factors.all { it == 1f } }
        assertEquals(1f, window.value.masterMultiplier, 0f)
        compose.onNodeWithTag("optical_channel_10").performScrollTo().assertExists()
        compose.onNodeWithTag("optical_common_factor").performScrollTo().assertIsEnabled()
        assertEquals(List(11) { 2f }, offsets.value.values)
        assertEquals(List(11) { 1.5f }, gains.value.values)
        assertEquals(0, updates)
    }

    @Test fun allThreeNewResetsPlaceRedResetBeforePurpleCancelInBothThemes() {
        fixture()
        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        for (night in listOf(false, true)) for (tag in listOf("calibration_reset_uv", "calibration_reset_vis", "optical_window_reset")) {
            compose.runOnIdle { dark.value = night }
            compose.onNodeWithTag(tag).performScrollTo().performClick()
            compose.onNodeWithTag("calibration_reset_dialog").assertIsDisplayed()
            compose.onNodeWithText(context.getString(R.string.calibration_reset_confirm_action), useUnmergedTree = true)
                .assertUvirActionTextColor(expectedCancelColor)
            compose.onNodeWithText(context.getString(R.string.cancel), useUnmergedTree = true)
                .assertUvirActionTextColor(expectedPrimaryColor)
            val reset = compose.onNodeWithTag("calibration_reset_confirm").fetchSemanticsNode().boundsInRoot
            val cancel = compose.onNodeWithTag("calibration_reset_cancel").fetchSemanticsNode().boundsInRoot
            assertEquals(reset.center.y, cancel.center.y, 1f)
            assertTrue("REIMPOSTA stays to the left of ANNULLA", reset.right < cancel.left)
            compose.onNodeWithTag("calibration_reset_cancel").performClick()
        }
        assertEquals(0, updates)
        assertEquals(List(11) { 2f }, offsets.value.values)
        assertEquals(List(11) { 1.5f }, gains.value.values)
        assertEquals(List(11) { 1.5f }, window.value.factors)
    }

    @Test fun everyCalibrationAndIndividualWindowChannelHasItsOwnDotWithoutChangingValues() {
        fixture()
        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        fun assertDot(tag: String, index: Int) {
            val dot = compose.onNodeWithTag(tag, useUnmergedTree = true).performScrollTo().assertIsDisplayed()
            val bounds = dot.fetchSemanticsNode().boundsInRoot
            assertEquals(with(compose.density) { 10.dp.toPx() }, bounds.width, 1f)
            assertEquals(bounds.width, bounds.height, 1f)
            val pixels = dot.captureToImage().toPixelMap()
            assertEquals("Channel $index dot color", calibrationChannelColor(index).toArgb(),
                pixels[pixels.width / 2, pixels.height / 2].toArgb())
        }
        for (night in listOf(false, true)) {
            compose.runOnIdle { dark.value = night }
            for (index in 0..10) assertDot("calibration_dot_$index", index)
            compose.onNodeWithTag("calibration_dot_0", useUnmergedTree = true).performScrollTo()
            File(context.getExternalFilesDir(null), "channel-calibration-${if (night) "night" else "day"}.png").outputStream().use {
                compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            for (index in 0..10) assertDot("optical_channel_dot_$index", index)
            compose.onNodeWithTag("optical_channel_0").performScrollTo()
            File(context.getExternalFilesDir(null), "channel-window-${if (night) "night" else "day"}.png").outputStream().use {
                compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        }
        assertEquals(0, updates)
        assertEquals(0, measures)
        assertEquals(List(11) { 2f }, offsets.value.values)
        assertEquals(List(11) { 1.5f }, window.value.factors)
    }

    @Test fun openingOrCancellingAnyResetNeverChangesValuesAndUsesTheStandardDialog() {
        fixture()
        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        for (button in listOf("calibration_reset_uv", "calibration_reset_vis")) {
            compose.onNodeWithTag(button).performScrollTo().performClick()
            compose.onNodeWithTag("calibration_reset_dialog").assertIsDisplayed()
            compose.onNodeWithText(context.getString(R.string.calibration_reset_confirmation_title)).assertIsDisplayed()
            compose.onNodeWithTag("calibration_reset_confirm").performTouchInput { click() }
            assertEquals(0, updates)
            compose.onNodeWithTag("calibration_reset_dialog").assertIsDisplayed()
            compose.onNodeWithTag("calibration_reset_cancel").performClick()
        }
        assertEquals(List(11) { 2f }, offsets.value.values)
        assertEquals(List(11) { 1.5f }, gains.value.values)
        assertEquals(List(11) { 1.5f }, window.value.factors)
    }

    @Test fun actionButtonsHaveIdenticalGeometryAndResetDialogMatchesBothThemes() {
        fixture()
        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        for (night in listOf(false, true)) {
            compose.runOnIdle { dark.value = night }
            compose.onNodeWithTag("calibration_reset_uv").performScrollTo()
            val calculate = compose.onNodeWithTag("dark_offsets_uv").fetchSemanticsNode().boundsInRoot
            val reset = compose.onNodeWithTag("calibration_reset_uv").fetchSemanticsNode().boundsInRoot
            assertEquals(calculate.left, reset.left, 1f)
            assertEquals(calculate.width, reset.width, 1f)
            assertEquals(calculate.height, reset.height, 1f)
            // Material keeps 48dp touch targets around its 40dp visible button surfaces.
            val surfaceGap = UvirActionButtonGap + (48.dp - ButtonDefaults.MinHeight)
            assertEquals(with(compose.density) { surfaceGap.toPx() }, reset.top - calculate.bottom, 1f)
            compose.onNodeWithTag("calibration_reset_uv").performClick()
            compose.onNodeWithText(context.getString(R.string.cancel), useUnmergedTree = true)
                .assertUvirActionTextColor(expectedPrimaryColor)
            val file = File(context.getExternalFilesDir(null), "reset-dialog-${if (night) "night" else "day"}.png")
            file.outputStream().use {
                assertTrue(compose.onNodeWithTag("calibration_reset_dialog").captureToImage().asAndroidBitmap()
                    .compress(Bitmap.CompressFormat.PNG, 100, it))
            }
            compose.onNodeWithTag("calibration_reset_cancel").performClick()
        }
        assertEquals(0, updates)
    }

    @Test fun anActivityStartingWhileConfirmationIsOpenDisablesTheReset() {
        fixture()
        compose.onNodeWithTag("calibration_reset_uv").performScrollTo().performClick()
        compose.runOnIdle { editable.value = false }
        compose.onNodeWithTag("calibration_reset_confirm").assertIsNotEnabled()
        pressBack()
        assertEquals(0, updates)
    }

    @Test fun proposalKeepsStandardDayAndNightLayoutAndCannotApplyDuringAnotherActivity() {
        fixture()
        compose.onNodeWithTag("dark_offsets_uv").performScrollTo().performClick()
        compose.onNodeWithTag("dark_offsets_start").performClick()
        compose.onNodeWithTag("dark_offsets_apply").assertIsEnabled()
        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        for (night in listOf(false, true)) {
            compose.runOnIdle { dark.value = night }
            val file = File(context.getExternalFilesDir(null), "dark-offsets-${if (night) "night" else "day"}.png")
            file.outputStream().use {
                assertTrue(compose.onNodeWithTag("dark_offsets_dialog_uv").captureToImage().asAndroidBitmap()
                    .compress(Bitmap.CompressFormat.PNG, 100, it))
            }
        }
        compose.runOnIdle { editable.value = false }
        compose.onNodeWithTag("dark_offsets_apply").assertIsNotEnabled()
        pressBack()
        assertEquals(0, updates)
    }
}
