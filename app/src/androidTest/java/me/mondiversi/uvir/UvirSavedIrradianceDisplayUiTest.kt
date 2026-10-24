package me.mondiversi.uvir

import android.graphics.Bitmap
import android.os.LocaleList
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.Locale
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Synthetic records only: no database access, connections, measurements or calibration writes. */
class UvirSavedIrradianceDisplayUiTest {
    @get:Rule val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val digits = mutableStateOf(2)
    private val unit = mutableStateOf(UvirIrradianceUnit.W_M2)
    private val page = mutableStateOf("saved")
    private val dark = mutableStateOf(false)
    private val direction = mutableStateOf(LayoutDirection.Ltr)
    private val group = mutableStateOf(SessionChartGroup.UV)
    private val session = mutableStateOf(false)
    private var currentPrimaryText = Color.Unspecified
    private val sample = SensorSample(uvc = 100.0, uvb = 100.0, uva = 200.0,
        violetto = 200.0, blu = 200.0, verde = 200.0, giallo = 200.0,
        arancione = 100.0, rosso = 100.0, f8 = 300.0, nir = 100.0).scaledIrradiance(.01)
    private val records = listOf(SavedRecordDetail(1L, 1L, "", false, sample))
    private val violation = ThresholdAlertViolation(
        ThresholdAlertRule(ThresholdAlertMetric.UVC, true, ThresholdAlertDirection.ABOVE, .5f), 1.0)
    private val alertViolations = mutableStateOf(listOf(violation))

    private fun fixture() {
        compose.setContent {
            KeepUvirVerificationScreenOn()
            val configuration = uvirThemeConfiguration(LocalConfiguration.current,
                if (dark.value) UvirAppTheme.DARK else UvirAppTheme.LIGHT).apply { setLocales(LocaleList(Locale.ITALY)) }
            CompositionLocalProvider(LocalConfiguration provides configuration,
                LocalLayoutDirection provides direction.value,
                LocalUvirNumericFormat provides UvirNumericFormat.EUROPEAN,
                LocalUvirIrradianceUnit provides unit.value,
                LocalUvirLiveIrradianceDecimals provides digits.value) {
                MaterialTheme(colorScheme = if (dark.value) darkColorScheme() else lightColorScheme()) {
                    val colors = MaterialTheme.colorScheme
                    currentPrimaryText = colors.onSurface
                    var display by rememberUvirSavedValueDisplay("saved-fixture")
                    Surface {
                        Column(Modifier.width(340.dp).height(640.dp).padding(12.dp)
                            .verticalScroll(rememberScrollState())) {
                            when (page.value) {
                                "saved" -> SessionAcquisitionValuesCard(records, group.value,
                                    showRecordHeader = session.value, showRelativeBreakdown = true,
                                    expanded = true, onToggle = {}, cardColor = colors.surfaceVariant,
                                    primaryText = colors.onSurface, secondaryText = colors.onSurfaceVariant,
                                    display = display, onDisplayChange = { display = it })
                                "live" -> SensorGroupContent(group.value.spectrumIconGroup(), true, {},
                                    sample = sample, uvTotal = 4.0, visibleTotal = 10.0, nirTotal = 4.0, hev = 4.0,
                                    cardColor = colors.surfaceVariant, primaryText = colors.onSurface,
                                    secondaryText = colors.onSurfaceVariant, trackColor = colors.outlineVariant)
                                "alert" -> AlertViolationDataCard(false, alertViolations.value, expanded = true,
                                    onToggle = {}, cardColor = colors.surfaceVariant,
                                    primaryText = colors.onSurface, secondaryText = colors.onSurfaceVariant)
                                "session_alert" -> AlertSessionValuesCard(listOf(AlertSessionValueEvent(
                                    ThresholdAlertLogEntry(1L, 1L, "", sessionId = 1, sessionSequence = 1), alertViolations.value)),
                                    false, true, {}, colors.surfaceVariant, colors.onSurface, colors.onSurfaceVariant)
                                "alert_summary" -> alertViolations.value.forEach {
                                    AlertViolationSummary(it, primaryText = colors.onSurface, secondaryText = colors.onSurfaceVariant)
                                }
                                "chart" -> AcquisitionVerticalBarChart(acquisitionChartBars(sample),
                                    AcquisitionChartGroup.IRRADIANCE, colors.surfaceVariant, colors.onSurface, colors.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }

    @Test fun singleAndSessionAcquisitionsKeepPlainChannelNamesInBothThemes() {
        fixture()
        for (night in listOf(false, true)) for (showHeader in listOf(false, true)) {
            for (spectralGroup in listOf(SessionChartGroup.UV, SessionChartGroup.VISIBLE, SessionChartGroup.FAR_RED_NIR)) {
                compose.runOnIdle { dark.value = night; session.value = showHeader; group.value = spectralGroup }
                for (series in sessionChartSeries(records, spectralGroup)) {
                    val label = series.displayLabelResource?.let(context::getString) ?: series.label
                    compose.onAllNodesWithText(label).onLast().performScrollTo().assertIsDisplayed()
                }
                compose.onAllNodes(hasText("nm", substring = true)).assertCountEquals(0)
            }
        }
    }

    @Test fun alertListSummaryUsesNeutralNamesAndAlignedPaletteDotsInBothThemesAndDirections() {
        val metrics = SessionChartGroup.entries.flatMap(::savedAcquisitionMetrics)
        alertViolations.value = metrics.map {
            ThresholdAlertViolation(ThresholdAlertRule(it, true, ThresholdAlertDirection.ABOVE, .5f), 1.0)
        }
        page.value = "alert_summary"
        fixture()
        for (night in listOf(false, true)) for (layout in listOf(LayoutDirection.Ltr, LayoutDirection.Rtl)) {
            compose.runOnIdle { dark.value = night; direction.value = layout }
            for (metric in metrics) {
                val name = compose.onNodeWithTag("alert_summary_name_${metric.name}", useUnmergedTree = true)
                    .performScrollTo()
                name.assertUvirActionTextColor(currentPrimaryText)
                val dot = compose.onNodeWithTag("alert_summary_dot_${metric.name}", useUnmergedTree = true)
                val nameBounds = name.getUnclippedBoundsInRoot()
                val dotBounds = dot.getUnclippedBoundsInRoot()
                assertEquals(10f, (dotBounds.right - dotBounds.left).value, .1f)
                assertEquals(10f, (dotBounds.bottom - dotBounds.top).value, .1f)
                val gap = if (layout == LayoutDirection.Ltr) nameBounds.left - dotBounds.right else dotBounds.left - nameBounds.right
                assertEquals(8f, gap.value, .1f)
                assertEquals((nameBounds.top.value + nameBounds.bottom.value) / 2f,
                    (dotBounds.top.value + dotBounds.bottom.value) / 2f, .1f)
                val pixels = dot.captureToImage().toPixelMap()
                val actual = pixels[pixels.width / 2, pixels.height / 2]
                val expected = uvirSpectralColor(metric)
                assertEquals(expected.red, actual.red, .01f)
                assertEquals(expected.green, actual.green, .01f)
                assertEquals(expected.blue, actual.blue, .01f)
            }
            if (layout == LayoutDirection.Ltr) screenshot("alert-summary-neutral-${if (night) "night" else "day"}.png")
        }
    }

    @Test fun singleSessionAndListAlertsKeepPlainChannelNamesInBothThemes() {
        val metrics = listOf(ThresholdAlertMetric.UVC, ThresholdAlertMetric.UVB, ThresholdAlertMetric.UVA,
            ThresholdAlertMetric.YELLOW, ThresholdAlertMetric.NIR)
        alertViolations.value = metrics.map {
            ThresholdAlertViolation(ThresholdAlertRule(it, true, ThresholdAlertDirection.ABOVE, .5f), 1.0)
        }
        fixture()
        for (night in listOf(false, true)) for (currentPage in listOf("alert", "session_alert", "alert_summary")) {
            compose.runOnIdle { dark.value = night; page.value = currentPage }
            for (metric in metrics) {
                compose.onNodeWithText(context.getString(thresholdAlertMetricLabelResource(metric)))
                    .performScrollTo().assertIsDisplayed()
            }
            compose.onAllNodes(hasText("nm", substring = true)).assertCountEquals(0)
            screenshot("saved-$currentPage-plain-${if (night) "night" else "day"}.png")
        }
    }

    @Test fun liveAndSavedChannelsUseTheSameGrandTotalInBothThemesAndDirections() {
        fixture()
        for (night in listOf(false, true)) for (layout in listOf(LayoutDirection.Ltr, LayoutDirection.Rtl)) {
            compose.runOnIdle { dark.value = night; direction.value = layout; group.value = SessionChartGroup.UV }
            for (currentPage in listOf("live", "saved")) {
                compose.runOnIdle { page.value = currentPage }
                compose.onAllNodesWithText("5,6%").onFirst().performScrollTo().assertIsDisplayed()
                compose.onNodeWithText("22,2%").performScrollTo().assertIsDisplayed()
                compose.onNodeWithText("25,0%").assertDoesNotExist()
            }
            if (layout == LayoutDirection.Ltr) screenshot("saved-global-share-${if (night) "night" else "day"}.png")
            compose.runOnIdle { page.value = "live" }
            val bars = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo),
                useUnmergedTree = true).fetchSemanticsNodes().map { it.config[SemanticsProperties.ProgressBarRangeInfo].current }
            assertEquals(listOf(.222f, .056f, .056f, .111f), bars)
        }
    }

    @Test fun savedAcquisitionAndSessionFollowDigitsAndUnitsWithoutMutatingTheirSample() {
        fixture()
        for (showHeader in listOf(false, true)) {
            compose.runOnIdle { session.value = showHeader; digits.value = 3; unit.value = UvirIrradianceUnit.W_M2 }
            compose.onAllNodesWithText("1,000").onFirst().performScrollTo().assertIsDisplayed()
            compose.runOnIdle { digits.value = 1 }
            compose.onAllNodesWithText("1,0").onFirst().performScrollTo().assertIsDisplayed()
            compose.runOnIdle { unit.value = UvirIrradianceUnit.MW_CM2; digits.value = 3 }
            compose.onAllNodesWithText("0,100").onFirst().performScrollTo().assertIsDisplayed()
            compose.runOnIdle { unit.value = UvirIrradianceUnit.UW_CM2; digits.value = 0 }
            compose.onAllNodesWithText("100").onFirst().performScrollTo().assertIsDisplayed()
        }
        assertEquals(1.0, records.single().sample.uvc, 0.0)
    }

    @Test fun visibleHevAndInfraredPercentagesAreNotRelativeToTheirOwnIslands() {
        fixture()
        compose.runOnIdle { group.value = SessionChartGroup.VISIBLE }
        compose.onNodeWithText("22,2%").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("55,6%").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("40,0%").assertDoesNotExist()
        compose.runOnIdle { group.value = SessionChartGroup.FAR_RED_NIR }
        compose.onNodeWithText("16,7%").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("5,6%").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("75,0%").assertDoesNotExist()
    }

    @Test fun savedAlertsFollowDigitsButKeepThresholdRelativePercentages() {
        fixture()
        for (currentPage in listOf("alert", "session_alert")) {
            compose.runOnIdle { page.value = currentPage; digits.value = 2 }
            compose.onNodeWithText("1,00").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("≥ 0,50").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("+100,0%").performScrollTo().assertIsDisplayed()
            compose.runOnIdle { digits.value = 3 }
            compose.onNodeWithText("1,000").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("≥ 0,500").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("+100,0%").performScrollTo().assertIsDisplayed()
        }
    }

    @Test fun savedChartValueLabelsRespectTheSelectedDecimals() {
        fixture()
        compose.runOnIdle { page.value = "chart"; digits.value = 3 }
        compose.onAllNodesWithText("1,000").onFirst().performScrollTo().assertIsDisplayed()
        compose.runOnIdle { digits.value = 1 }
        compose.onAllNodesWithText("1,0").onFirst().performScrollTo().assertIsDisplayed()
        assertEquals(1.0, records.single().sample.uvc, 0.0)
    }

    private fun screenshot(name: String) {
        File(context.getExternalFilesDir(null), name).outputStream().use {
            assertTrue(compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it))
        }
    }
}
