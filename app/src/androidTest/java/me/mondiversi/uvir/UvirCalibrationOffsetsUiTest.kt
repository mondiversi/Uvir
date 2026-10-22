package me.mondiversi.uvir

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Isolated Compose fixture; no real settings, database or hardware commands. */
class UvirCalibrationOffsetsUiTest {
    @get:Rule val compose = createComposeRule()
    private val unit = mutableStateOf(UvirIrradianceUnit.UW_CM2)
    private val night = mutableStateOf(false)
    private val supported = mutableStateOf(true)
    private val uvAvailable = mutableStateOf(true)
    private val value = mutableStateOf(CalibrationOffsets().withChannel(2, 3f))
    private val gains = mutableStateOf<CalibrationChannelFactors?>(null)
    private var gainUpdates = 0
    private var updates = 0

    private fun fixture() {
        compose.setContent {
            KeepUvirVerificationScreenOn()
            val configuration = uvirThemeConfiguration(LocalConfiguration.current,
                if (night.value) UvirAppTheme.DARK else UvirAppTheme.LIGHT)
            CompositionLocalProvider(LocalUvirIrradianceUnit provides unit.value,
                LocalConfiguration provides configuration,
                LocalUvirNumericFormat provides UvirNumericFormat.EUROPEAN) {
                MaterialTheme(colorScheme = if (night.value) darkColorScheme() else lightColorScheme()) {
                    val colors = MaterialTheme.colorScheme
                    Column(Modifier.width(340.dp).height(620.dp).verticalScroll(rememberScrollState())) {
                        UvirSensorCalibrationSettings(LocalContext.current, true, uvAvailable.value,
                            "1.0", {}, "1.0", {}, colors.surface, colors.onSurface, colors.onSurfaceVariant,
                            value.value, supported.value, { value.value = it; updates++ },
                            gains.value, supported.value, { gains.value = it; gainUpdates++ })
                    }
                }
            }
        }
    }

    @Test fun missingUvHardwareOnlyDisablesItsFieldsWithoutChangingCalibration() {
        uvAvailable.value = false
        fixture()
        for (index in 0..2) {
            compose.onNodeWithTag("calibration_channel_factor_$index").performScrollTo().assertIsNotEnabled()
            compose.onNodeWithTag("calibration_offset_$index").performScrollTo().assertIsNotEnabled()
        }
        compose.onNodeWithTag("calibration_offset_3").performScrollTo().assertIsEnabled()
        assertEquals(0, updates)
        assertEquals(0, gainUpdates)
        assertEquals(3f, value.value.values[2], 0f)
    }

    @Test fun introductionExplainsBothControlsAndLimitsFollowTheSelectedUnitWithoutSaving() {
        fixture()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        for (dark in listOf(false, true)) {
            compose.runOnIdle { night.value = dark }
            compose.onNodeWithTag("calibration_description").performScrollTo()
                .assertTextEquals(context.getString(R.string.sensor_calibration_description))
            val expectedFactors = context.getString(R.string.sensor_calibration_factor_hint, "0,1", "10,0", "1,0")
            compose.onNodeWithTag("calibration_factors_description").assertTextEquals(expectedFactors)
            for (selectedUnit in UvirIrradianceUnit.entries) {
                compose.runOnIdle { unit.value = selectedUnit }
                val maximum = formatUvirNumber(
                    selectedUnit.fromCanonicalWm2(MAX_CALIBRATION_OFFSET_W_M2.toDouble()),
                    0, UvirNumericFormat.EUROPEAN)
                compose.onNodeWithTag("calibration_offsets_description").assertTextEquals(
                    context.getString(R.string.calibration_offsets_description, "0", maximum, selectedUnit.symbol))
            }
            val screenshot = File(context.getExternalFilesDir(null), "calibration-introduction-${if (dark) "night" else "day"}.png")
            screenshot.outputStream().use {
                assertTrue(compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it))
            }
        }
        assertEquals(0, updates)
        assertEquals(0, gainUpdates)
        assertEquals(3f, value.value.values[2], 0f)
    }

    @Test fun unitsAndThemesOnlyChangePresentationWithoutSavingOrRoundingOffsets() {
        fixture()
        for (dark in listOf(false, true)) {
            compose.runOnIdle { night.value = dark }
            compose.onNodeWithTag("calibration_offset_2").performScrollTo().assertTextContains("3")
            compose.runOnIdle { unit.value = UvirIrradianceUnit.W_M2 }
            compose.onNodeWithTag("calibration_offset_2").performScrollTo().assertTextContains("0,03")
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val preview = File(context.getExternalFilesDir(null), "offsets-${if (dark) "night" else "day"}.png")
            preview.outputStream().use {
                assertTrue(compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it))
            }
            compose.runOnIdle { unit.value = UvirIrradianceUnit.MW_CM2 }
            compose.onNodeWithTag("calibration_offset_2").performScrollTo().assertTextContains("0,003")
            compose.onNodeWithTag("calibration_offset_10").performScrollTo().assertIsEnabled()
            compose.onNodeWithTag("calibration_offset_11").assertDoesNotExist()
            compose.runOnIdle { unit.value = UvirIrradianceUnit.UW_CM2 }
        }
        assertEquals(0, updates)
        assertEquals(3f, value.value.values[2], 0f)
    }

    @Test fun anOffsetEditConvertsFromTheSelectedUnitAndUnsupportedFirmwareIsReadOnly() {
        unit.value = UvirIrradianceUnit.W_M2
        fixture()
        compose.onNodeWithTag("calibration_offset_2").performScrollTo().performTextReplacement("0,04")
        compose.onNodeWithTag("calibration_offset_1").performScrollTo().performClick()
        compose.waitUntil(5000) { updates == 1 }
        assertEquals(4f, value.value.values[2], 0f)
        assertEquals(0f, value.value.values[1], 0f)
        compose.runOnIdle { supported.value = false }
        compose.onNodeWithTag("calibration_offset_2").performScrollTo().assertIsNotEnabled()
    }

    @Test fun wavelengthReferencesHaveTheSameEndPaddingAsOpticalCompensationAndColorsAreNotDuplicated() {
        fixture()
        for (index in listOf(0, 1, 2, 3, 9, 10)) {
            val field = compose.onNodeWithTag("calibration_offset_$index").performScrollTo().fetchSemanticsNode().boundsInRoot
            val reference = compose.onNodeWithTag("calibration_reference_$index", useUnmergedTree = true)
                .fetchSemanticsNode().boundsInRoot
            val expectedInset = with(compose.density) { 12.dp.toPx() }
            assertTrue("Reference $index must retain its 12dp end padding", field.right - reference.right >= expectedInset - 1f)
        }
        compose.onNodeWithTag("calibration_offset_11").assertDoesNotExist()
        assertEquals(11, value.value.values.size)
        assertEquals(0, updates)
    }

    @Test fun factorsAndOffsetsShareEqualColumnsAndAChangeAffectsOnlyOneFactor() {
        fixture()
        val factor = compose.onNodeWithTag("calibration_channel_factor_2").performScrollTo().fetchSemanticsNode().boundsInRoot
        val offset = compose.onNodeWithTag("calibration_offset_2").fetchSemanticsNode().boundsInRoot
        assertEquals(factor.top, offset.top, 1f)
        assertEquals(factor.width, offset.width, 1f)
        assertTrue(factor.right < offset.left)
        assertEquals(0, gainUpdates)
        compose.onNodeWithTag("calibration_channel_factor_2").performTextReplacement("1,5")
        compose.onNodeWithTag("calibration_offset_2").performClick()
        compose.waitUntil(5000) { gainUpdates == 1 }
        assertEquals(1.5f, gains.value!!.values[2], 0f)
        assertTrue(gains.value!!.values.filterIndexed { index, _ -> index != 2 }.all { it == 1f })
        assertEquals(3f, value.value.values[2], 0f)
        assertEquals(0, updates)
    }

    @Test fun aLegacyConfigurationIsRetainedUntilAColorIsExplicitlyEdited() {
        value.value = CalibrationOffsets(List(15) { it.toFloat() })
        fixture()
        assertEquals(0, updates)
        assertEquals(15, value.value.values.size)
        compose.onNodeWithTag("calibration_offset_3").performScrollTo().performTextReplacement("2")
        compose.onNodeWithTag("calibration_offset_4").performScrollTo().performClick()
        compose.waitUntil(5000) { updates == 1 }
        assertEquals(11, value.value.values.size)
        assertEquals(2f, value.value.values[3], 0f)
        assertEquals(5.5f, value.value.values[4], 0f)
    }
}
