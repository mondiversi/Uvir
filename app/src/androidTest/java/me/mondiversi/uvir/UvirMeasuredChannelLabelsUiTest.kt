package me.mondiversi.uvir

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Isolated cards only: no live sensor, archive, settings or preference writes. */
class UvirMeasuredChannelLabelsUiTest {
    @get:Rule val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val dark = mutableStateOf(false)
    private val direction = mutableStateOf(LayoutDirection.Ltr)
    private val sample = SensorSample(violetto = 1.0, blu = 2.0, verde = 3.0,
        giallo = 4.0, arancione = 5.0, rosso = 6.0)

    private fun fixture(content: @Composable (androidx.compose.material3.ColorScheme) -> Unit) {
        compose.setContent {
            KeepUvirVerificationScreenOn()
            val configuration = uvirThemeConfiguration(LocalConfiguration.current,
                if (dark.value) UvirAppTheme.DARK else UvirAppTheme.LIGHT)
            CompositionLocalProvider(LocalLayoutDirection provides direction.value,
                LocalConfiguration provides configuration) {
                MaterialTheme(colorScheme = if (dark.value) darkColorScheme() else lightColorScheme()) {
                    Box(Modifier.width(320.dp).height(600.dp)) {
                        Column(Modifier.verticalScroll(rememberScrollState())) {
                            content(MaterialTheme.colorScheme)
                        }
                    }
                }
            }
        }
    }

    @Test fun liveGroupHeadersShowCompactPercentageControlsInBothThemesAndDirections() {
        val groups = listOf(SensorGroup.UV, SensorGroup.VISIBLE, SensorGroup.NIR)
        fixture { colors ->
            for (group in groups) Box(Modifier.testTag("model_header_$group")) {
                SensorGroupContent(group, false, {}, sample = sample,
                    uvTotal = 0.0, visibleTotal = 21.0, nirTotal = 0.0, hev = 3.0,
                    cardColor = colors.surface, primaryText = colors.onSurface,
                    secondaryText = colors.onSurfaceVariant, trackColor = colors.surfaceVariant)
            }
        }
        for (night in listOf(false, true)) for (layout in listOf(LayoutDirection.Ltr, LayoutDirection.Rtl)) {
            compose.runOnIdle { dark.value = night; direction.value = layout }
            for (group in groups) {
                compose.onNodeWithTag("model_header_$group").performScrollTo()
                val model = compose.onNodeWithTag("live_percentage_base_$group", useUnmergedTree = true)
                    .assertIsDisplayed().assertHasClickAction()
                val layouts = mutableListOf<TextLayoutResult>()
                model.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
                org.junit.Assert.assertEquals(FontWeight.Normal, layouts.single().layoutInput.style.fontWeight)
                val percentageLayouts = mutableListOf<TextLayoutResult>()
                compose.onNodeWithTag("live_group_percentage_$group", useUnmergedTree = true)
                    .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(percentageLayouts) }
                org.junit.Assert.assertEquals(FontWeight.Bold, percentageLayouts.single().layoutInput.style.fontWeight)
            }
            compose.onNodeWithText(UvirSensorBands.UV_CHANNELS).assertDoesNotExist()
            compose.onNodeWithText(UvirSensorBands.INFRARED_CHANNELS).assertDoesNotExist()
            if (layout == LayoutDirection.Ltr) {
                compose.onNodeWithTag("model_header_UV").performScrollTo()
                File(context.getExternalFilesDir(null), "live-models-${if (night) "night" else "day"}.png")
                    .outputStream().use {
                        org.junit.Assert.assertTrue(compose.onRoot().captureToImage().asAndroidBitmap()
                            .compress(Bitmap.CompressFormat.PNG, 100, it))
                    }
            }
        }
    }

    @Test fun liveVisibleCardUsesCompactPeaksWithoutAnExtraTotalLabelInBothThemesAndDirections() {
        fixture { colors ->
            SensorGroupContent(SensorGroup.VISIBLE, true, {}, sample = sample,
                uvTotal = 0.0, visibleTotal = 21.0, nirTotal = 0.0, hev = 3.0,
                cardColor = colors.surface, primaryText = colors.onSurface,
                secondaryText = colors.onSurfaceVariant, trackColor = colors.surfaceVariant)
        }
        for (night in listOf(false, true)) for (layout in listOf(LayoutDirection.Ltr, LayoutDirection.Rtl)) {
            compose.runOnIdle { dark.value = night; direction.value = layout }
            compose.onNodeWithText(context.getString(R.string.measured_channel_sum)).assertDoesNotExist()
            for (reference in listOf(UvirSensorBands.VIOLET_CHANNELS,
                UvirSensorBands.BLUE_CHANNELS, UvirSensorBands.GREEN_CHANNEL,
                UvirSensorBands.GREEN_YELLOW_CHANNELS, UvirSensorBands.ORANGE_CHANNEL, UvirSensorBands.RED_CHANNEL)) {
                compose.onNodeWithText(reference).performScrollTo().assertIsDisplayed()
            }
            compose.onNodeWithText(context.getString(R.string.yellow)).performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("≈570–590 nm").assertDoesNotExist()
            compose.onNodeWithText("400–500 nm").assertDoesNotExist()
            compose.onNodeWithText("405 / 425 / 450 / 475 nm").assertDoesNotExist()
        }
    }

    @Test fun savedAcquisitionValuesUsePlainNamesWithoutChangingReadings() {
        val records = listOf(SavedRecordDetail(1L, 1L, "", false, sample))
        fixture { colors ->
            var display by rememberUvirSavedValueDisplay("measured-fixture")
            SessionAcquisitionValuesCard(records, SessionChartGroup.VISIBLE,
                showRecordHeader = false, expanded = true, onToggle = {},
                cardColor = colors.surface, primaryText = colors.onSurface,
                secondaryText = colors.onSurfaceVariant, display = display, onDisplayChange = { display = it })
        }
        compose.onNodeWithText(context.getString(R.string.yellow))
            .performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.green))
            .performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.threshold_channel_hev))
            .performScrollTo().assertIsDisplayed()
        compose.onAllNodes(hasText("nm", substring = true)).assertCountEquals(0)
    }

    @Test fun thresholdEditorsShowGreenYellowAndItsReferenceWithoutExtraDescriptions() {
        fixture { colors ->
            ThresholdAlertMetricLabel(ThresholdAlertMetric.YELLOW, colors.onSurface,
                colors.onSurfaceVariant, true)
            ThresholdAlertMetricLabel(ThresholdAlertMetric.HEV, colors.onSurface,
                colors.onSurfaceVariant, true)
        }
        compose.onNodeWithText(context.getString(R.string.yellow)).assertIsDisplayed()
        compose.onNodeWithText(UvirSensorBands.GREEN_YELLOW_CHANNELS).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.threshold_channel_hev)).assertIsDisplayed()
        compose.onNodeWithText("405 / 425 / 450 / 475 nm").assertDoesNotExist()
    }
}
