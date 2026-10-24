package me.mondiversi.uvir

import android.graphics.Bitmap
import android.os.LocaleList
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.Locale
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Synthetic view fixtures only: no database, settings, sensor commands or archive writes. */
class UvirSavedValueControlsUiTest {
    @get:Rule val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val page = mutableStateOf("acquisition")
    private val detailKey = mutableStateOf("fixture")
    private val group = mutableStateOf(SessionChartGroup.UV)
    private val night = mutableStateOf(false)
    private val direction = mutableStateOf(LayoutDirection.Ltr)
    private val chart = mutableStateOf(false)
    private val visitOpen = mutableStateOf(true)
    private var headerClicks = 0
    private var titlePrimary = Color.Unspecified
    private var titleSecondary = Color.Unspecified
    private var titleCard = Color.Unspecified
    private val sample = SensorSample(uvc = 1.0, uvb = 1.0, uva = 2.0,
        violetto = 2.0, blu = 2.0, verde = 2.0, giallo = 2.0, arancione = 2.0, rosso = 2.0, f8 = 2.0, nir = 2.0)
    private val records = listOf(SavedRecordDetail(1, 1_000, "Fixture", false, sample),
        SavedRecordDetail(2, 2_000, "Fixture", false, sample.copy(uvb = 4.0, uva = 5.0)))

    private fun violations(index: Int): List<ThresholdAlertViolation> = if (group.value.biological) {
        listOf(ThresholdAlertViolation(ThresholdAlertRule(ThresholdAlertMetric.BIO_DNA_UV,
            true, ThresholdAlertDirection.ABOVE, .5f), 1.0))
    } else listOf(
        ThresholdAlertViolation(ThresholdAlertRule(ThresholdAlertMetric.UVC, true, ThresholdAlertDirection.ABOVE, .5f), if (index == 0) 1.0 else 3.0),
        ThresholdAlertViolation(ThresholdAlertRule(ThresholdAlertMetric.UVA, true, ThresholdAlertDirection.ABOVE, 1f), 2.0),
        ThresholdAlertViolation(ThresholdAlertRule(ThresholdAlertMetric.UV_TOTAL, true, ThresholdAlertDirection.ABOVE, 3f), 4.0)
    )

    private fun fixture(restorer: StateRestorationTester? = null) {
        val content: @Composable () -> Unit = {
            KeepUvirVerificationScreenOn()
            val configuration = uvirThemeConfiguration(LocalConfiguration.current,
                if (night.value) UvirAppTheme.DARK else UvirAppTheme.LIGHT).apply { setLocales(LocaleList(Locale.ITALY)) }
            CompositionLocalProvider(LocalConfiguration provides configuration,
                LocalLayoutDirection provides direction.value,
                LocalUvirNumericFormat provides UvirNumericFormat.EUROPEAN,
                LocalUvirIrradianceUnit provides UvirIrradianceUnit.W_M2,
                LocalUvirLiveIrradianceDecimals provides 2) {
                MaterialTheme(colorScheme = if (night.value) darkColorScheme() else lightColorScheme()) {
                    val colors = MaterialTheme.colorScheme
                    titlePrimary = colors.onSurface
                    titleSecondary = colors.onSurfaceVariant
                    titleCard = colors.surfaceVariant
                    if (visitOpen.value) {
                    var display by rememberUvirSavedValueDisplay("${page.value}:${detailKey.value}")
                    Surface {
                        Column(Modifier.width(340.dp).height(640.dp).padding(12.dp)
                            .verticalScroll(rememberScrollState()).testTag("saved_fixture")) {
                            when (page.value) {
                                "acquisition", "session" -> if (chart.value) {
                                    AcquisitionVerticalBarChart(savedAcquisitionChartBars(sample,
                                        if (group.value.biological) AcquisitionChartGroup.BIOLOGICAL else AcquisitionChartGroup.IRRADIANCE, display),
                                        if (group.value.biological) AcquisitionChartGroup.BIOLOGICAL else AcquisitionChartGroup.IRRADIANCE,
                                        colors.surfaceVariant, colors.onSurface, colors.onSurfaceVariant,
                                        mutedGroups = UvirLiveSpectrumDisplay.irradianceGroups.filterNot { display.spectrum.groupVisible(it) }.toSet())
                                } else {
                                    SessionAcquisitionValuesCard(if (page.value == "session") records else records.take(1), group.value,
                                        showRecordHeader = page.value == "session", showRelativeBreakdown = true,
                                        expanded = true, onToggle = { headerClicks++ }, cardColor = colors.surfaceVariant,
                                        primaryText = colors.onSurface, secondaryText = colors.onSurfaceVariant,
                                        display = display, onDisplayChange = { display = it })
                                }
                                "alert" -> AlertViolationDataCard(group.value.biological, violations(0), expanded = true,
                                    onToggle = { headerClicks++ }, cardColor = colors.surfaceVariant,
                                    primaryText = colors.onSurface, secondaryText = colors.onSurfaceVariant)
                                "alert_session" -> AlertSessionValuesCard(records.mapIndexed { index, record ->
                                    AlertSessionValueEvent(ThresholdAlertLogEntry(record.id, record.timestamp, "",
                                        sessionId = 1, sessionSequence = index + 1), violations(index))
                                }, group.value.biological, true, { headerClicks++ }, colors.surfaceVariant,
                                    colors.onSurface, colors.onSurfaceVariant)
                            }
                        }
                    }
                    }
                }
            }
        }
        if (restorer == null) compose.setContent(content) else restorer.setContent(content)
    }

    private fun node(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true)
    private fun acquisitionKey(metric: String, id: Int = 1) = "acquisition_${id}_$metric"
    private fun alertKey(metric: String, id: Int = 1) = if (page.value == "alert") "alert_single_$metric" else "alert_${id}_$metric"
    private fun value(key: String) = node("saved_value_$key")
    private fun percent(key: String) = node("saved_percentage_$key")

    private fun assertPaletteDot(key: String, expected: Color, layout: LayoutDirection) {
        val dot = node("saved_dot_$key")
        dot.assert(SemanticsMatcher.keyNotDefined(androidx.compose.ui.semantics.SemanticsActions.OnClick))
        val dotBounds = dot.getUnclippedBoundsInRoot()
        val nameBounds = node("saved_name_$key").getUnclippedBoundsInRoot()
        assertEquals(10f, (dotBounds.right - dotBounds.left).value, .1f)
        assertEquals(10f, (dotBounds.bottom - dotBounds.top).value, .1f)
        val gap = if (layout == LayoutDirection.Ltr) nameBounds.left - dotBounds.right else dotBounds.left - nameBounds.right
        assertEquals(8f, gap.value, .1f)
        assertEquals((nameBounds.top.value + nameBounds.bottom.value) / 2f,
            (dotBounds.top.value + dotBounds.bottom.value) / 2f, .1f)
        val pixels = dot.captureToImage().toPixelMap()
        val actual = pixels[pixels.width / 2, pixels.height / 2]
        val opaque = expected.compositeOver(titleCard)
        assertEquals(opaque.red, actual.red, .01f)
        assertEquals(opaque.green, actual.green, .01f)
        assertEquals(opaque.blue, actual.blue, .01f)
    }

    @Test fun savedNamesUseNeutralTextWithPaletteDotsInAllFourViewsAndBothThemesAndDirections() {
        fixture()
        for (dark in listOf(false, true)) for (layout in listOf(LayoutDirection.Ltr, LayoutDirection.Rtl)) {
            for (current in listOf("acquisition", "session")) for (currentGroup in SessionChartGroup.entries) {
                compose.runOnIdle { night.value = dark; direction.value = layout; page.value = current; group.value = currentGroup }
                for (metric in savedAcquisitionMetrics(currentGroup)) {
                    val key = acquisitionKey(metric.name)
                    node("saved_name_$key").performScrollTo().assertUvirActionTextColor(titlePrimary)
                    assertPaletteDot(key, uvirSpectralColor(metric), layout)
                }
                if (currentGroup == SessionChartGroup.VISIBLE && layout == LayoutDirection.Ltr) {
                    node("saved_name_${acquisitionKey("VISIBLE_TOTAL")}").performScrollTo()
                    File(context.getExternalFilesDir(null), "spectral-labels-$current-${if (dark) "night" else "day"}.png")
                        .outputStream().use { compose.onRoot().captureToImage().asAndroidBitmap()
                            .compress(Bitmap.CompressFormat.PNG, 100, it) }
                }
            }
            for (current in listOf("alert", "alert_session")) for (bio in listOf(false, true)) {
                compose.runOnIdle {
                    page.value = current
                    group.value = if (bio) SessionChartGroup.BIOLOGICAL else SessionChartGroup.UV
                }
                for (violation in violations(0)) {
                    val metric = violation.rule.metric
                    val key = alertKey(metric.name)
                    node("saved_name_$key").performScrollTo().assertUvirActionTextColor(titlePrimary)
                    assertPaletteDot(key, uvirSpectralColor(metric), layout)
                }
            }
        }
    }

    @Test fun excludedRowsGrayTheNameAndDotAndTheDotStillTogglesTheWholeRow() {
        fixture()
        for (dark in listOf(false, true)) {
            compose.runOnIdle { night.value = dark }
            val key = acquisitionKey("UVC")
            val row = node("saved_row_$key")
            row.performClick().assertIsOff()
            node("saved_name_$key").assertUvirActionTextColor(titleSecondary.copy(alpha = .55f))
            compose.mainClock.advanceTimeBy(300)
            compose.waitForIdle()
            Thread.sleep(350)
            assertPaletteDot(key, titleSecondary.copy(alpha = .55f), LayoutDirection.Ltr)
            node("saved_dot_$key").performTouchInput { click(center) }
            row.assertIsOn()
            node("saved_name_$key").assertUvirActionTextColor(titlePrimary)
        }
    }

    @Test fun mutedSavedIslandTitlesAreGrayInTablesAndChartsAndRestoreWithOneBandInBothThemes() {
        fixture()
        for (current in listOf("acquisition", "session")) for (dark in listOf(false, true)) {
            for (currentGroup in listOf(SessionChartGroup.UV, SessionChartGroup.VISIBLE, SessionChartGroup.FAR_RED_NIR)) {
                compose.runOnIdle { page.value = current; night.value = dark; group.value = currentGroup; chart.value = false }
                compose.waitForIdle()
                val sensorGroup = currentGroup.spectrumIconGroup()
                val title = node("chart_group_title_${sensorGroup.name}")
                val total = node("saved_row_${acquisitionKey(savedAcquisitionMetrics(currentGroup).first().name)}")
                title.assertUvirActionTextColor(titlePrimary)
                total.performClick().assertIsOff()
                title.assertUvirActionTextColor(titleSecondary.copy(alpha = .55f))
                compose.runOnIdle { chart.value = true }
                title.assertUvirActionTextColor(titleSecondary.copy(alpha = .55f))
                compose.runOnIdle { chart.value = false }
                node("saved_row_${acquisitionKey(UvirLiveSpectrumDisplay.channels(sensorGroup).first().name)}").performClick()
                title.assertUvirActionTextColor(titlePrimary)
                total.performClick().performClick().assertIsOn()
            }
        }
    }

    @Test fun groupControlHidesAllBandsOfSingleAcquisitionsAndEveryRecordInSessions() {
        fixture()
        for (current in listOf("acquisition", "session")) for (currentGroup in listOf(
            SessionChartGroup.UV, SessionChartGroup.VISIBLE, SessionChartGroup.FAR_RED_NIR)) {
            compose.runOnIdle { page.value = current; group.value = currentGroup }
            val sensorGroup = currentGroup.spectrumIconGroup()
            val totalMetric = savedAcquisitionMetrics(currentGroup).first()
            val control = node("saved_row_${acquisitionKey(totalMetric.name)}")
            control.performScrollTo().assertIsOn().performTouchInput { click(center) }.assertIsOff()
            for (id in 1..if (current == "session") 2 else 1) {
                for (metric in UvirLiveSpectrumDisplay.channels(sensorGroup)) {
                    node("saved_row_${acquisitionKey(metric.name, id)}").assertIsOff()
                    value(acquisitionKey(metric.name, id)).assertTextEquals("—")
                }
                value(acquisitionKey(totalMetric.name, id)).assertTextEquals("—")
            }
            node("saved_cross_${acquisitionKey(totalMetric.name)}").assertIsDisplayed()
            compose.runOnIdle { assertEquals(0, headerClicks) }
            control.performClick().assertIsOn()
        }
        assertEquals(1.0, records[0].sample.uvc, 0.0)
        assertEquals(1.0, records[1].sample.uvc, 0.0)
    }

    @Test fun groupChoicesAreTemporaryAndChartsFollowTheTotalRowWithoutHeaderControls() {
        fixture()
        for (current in listOf("acquisition", "session")) {
            compose.runOnIdle { page.value = current; chart.value = false }
            node("saved_row_${acquisitionKey("UV_TOTAL")}").performClick().assertIsOff()
            compose.runOnIdle { chart.value = true }
            node("saved_group_UV_visibility").assertDoesNotExist()
            compose.onNodeWithText("UVC").assertDoesNotExist()
            compose.runOnIdle { chart.value = false }
            node("saved_row_${acquisitionKey("UV_TOTAL")}").assertIsOff().performClick().assertIsOn()
            compose.runOnIdle { chart.value = true }
            compose.onAllNodesWithText("UVC").onFirst().assertExists()
            compose.runOnIdle { chart.value = false }
            node("saved_row_${acquisitionKey("UV_TOTAL")}").performClick().assertIsOff()
            compose.runOnIdle { visitOpen.value = false }
            compose.runOnIdle { visitOpen.value = true; chart.value = false }
            node("saved_row_${acquisitionKey("UV_TOTAL")}").assertIsOn()
            value(acquisitionKey("UVC")).assertTextEquals("1,00")
        }
        compose.runOnIdle { assertEquals(0, headerClicks) }
    }

    @Test fun totalRowMarkMatchesItsChildrenAndTheHeaderHasNoMarkInBothThemesAndDirections() {
        fixture()
        for (dark in listOf(false, true)) for (layout in listOf(LayoutDirection.Ltr, LayoutDirection.Rtl)) {
            compose.runOnIdle { night.value = dark; direction.value = layout }
            val title = compose.onAllNodesWithText(context.getString(R.string.uv_radiation), useUnmergedTree = true).onLast().getUnclippedBoundsInRoot()
            val mark = node("saved_check_${acquisitionKey("UV_TOTAL")}").getUnclippedBoundsInRoot()
            val childTitle = compose.onAllNodesWithText("UVC", useUnmergedTree = true).onFirst().getUnclippedBoundsInRoot()
            val childMark = node("saved_check_${acquisitionKey("UVC")}").getUnclippedBoundsInRoot()
            // Odd text/glyph pixel heights can round their centers one physical pixel apart.
            val onePixel = with(compose.density) { 1.toDp().value }
            assertEquals((title.top.value + title.bottom.value) / 2f, (mark.top.value + mark.bottom.value) / 2f, onePixel + .001f)
            if (layout == LayoutDirection.Ltr) assertTrue(title.right < mark.left) else assertTrue(mark.right < title.left)
            assertEquals((mark.right - mark.left).value, (childMark.right - childMark.left).value, .01f)
            val gap = if (layout == LayoutDirection.Ltr) mark.left - title.right else title.left - mark.right
            val childGap = if (layout == LayoutDirection.Ltr) childMark.left - childTitle.right else childTitle.left - childMark.right
            assertEquals(gap.value, childGap.value, onePixel + .001f)
            node("saved_group_UV_check").assertDoesNotExist()
            node("saved_group_UV_visibility").assertDoesNotExist()
            node("saved_row_${acquisitionKey("UV_TOTAL")}").performTouchInput { click(center) }.assertIsOff()
            if (layout == LayoutDirection.Ltr) File(context.getExternalFilesDir(null), "group-toggle-saved-${if (dark) "night" else "day"}.png")
                .outputStream().use { assertTrue(compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)) }
            node("saved_row_${acquisitionKey("UV_TOTAL")}").performClick().assertIsOn()
            compose.runOnIdle { assertEquals(0, headerClicks) }
        }
    }

    @Test fun percentageLinkChangesChildrenButNotTheTotalAndNeverCollapsesTheIsland() {
        fixture()
        val link = node("saved_percentage_base_UV")
        percent(acquisitionKey("UVC")).assertTextEquals("5,0%")
        percent(acquisitionKey("UV_TOTAL")).assertTextEquals("20,0%")
        link.assertHasClickAction().performTouchInput { click(center) }
        link.assertTextEquals("% gruppo")
        percent(acquisitionKey("UVC")).assertTextEquals("25,0%")
        percent(acquisitionKey("UV_TOTAL")).assertTextEquals("20,0%")
        compose.runOnIdle { assertEquals(0, headerClicks); group.value = SessionChartGroup.VISIBLE }
        node("saved_percentage_base_VISIBLE").assertTextEquals("% totale")
        compose.runOnIdle { group.value = SessionChartGroup.UV }
        link.assertTextEquals("% gruppo")
    }

    @Test fun sessionBandExclusionAppliesToAllRecordsButEveryShareUsesItsOwnRecord() {
        page.value = "session"
        fixture()
        node("saved_row_${acquisitionKey("UVC")}").performClick().assertIsOff()
        for (id in 1..2) {
            node("saved_row_${acquisitionKey("UVC", id)}").assertIsOff()
            value(acquisitionKey("UVC", id)).assertTextEquals("—")
        }
        value(acquisitionKey("UV_TOTAL")).assertTextEquals("3,00")
        value(acquisitionKey("UV_TOTAL", 2)).assertTextEquals("9,00")
        percent(acquisitionKey("UV_TOTAL")).assertTextEquals("15,8%")
        percent(acquisitionKey("UV_TOTAL", 2)).assertTextEquals("36,0%")
        node("saved_percentage_base_UV").performClick()
        percent(acquisitionKey("UVA")).assertTextEquals("66,7%")
        percent(acquisitionKey("UVA", 2)).assertTextEquals("55,6%")
        percent(acquisitionKey("UV_TOTAL")).assertTextEquals("15,8%")
        node("saved_cross_${acquisitionKey("UVC", 2)}").performTouchInput { click(center) }
        for (id in 1..2) node("saved_row_${acquisitionKey("UVC", id)}").assertIsOn()
        assertEquals(1.0, records[0].sample.uvc, 0.0)
        assertEquals(1.0, records[1].sample.uvc, 0.0)
    }

    @Test fun hevExclusionRecalculatesOtherIslandsWithoutDoubleCountingOrChangingTheRawSample() {
        fixture()
        compose.runOnIdle { group.value = SessionChartGroup.VISIBLE }
        node("saved_row_${acquisitionKey("HEV")}").performClick()
        for (metric in listOf("HEV", "VIOLET", "BLUE")) value(acquisitionKey(metric)).assertTextEquals("—")
        value(acquisitionKey("VISIBLE_TOTAL")).assertTextEquals("8,00")
        percent(acquisitionKey("VISIBLE_TOTAL")).assertTextEquals("50,0%")
        compose.runOnIdle { group.value = SessionChartGroup.UV }
        percent(acquisitionKey("UV_TOTAL")).assertTextEquals("25,0%")
        assertEquals(2.0, sample.blu, 0.0)
    }

    @Test fun alertsAlwaysShowEverySelectiveValueWithoutVisibilityControlsOrPercentageBasis() {
        fixture()
        for (current in listOf("alert", "alert_session")) {
            compose.runOnIdle { page.value = current }
            for (sensorGroup in UvirLiveSpectrumDisplay.irradianceGroups)
                node("saved_group_${sensorGroup.name}_visibility").assertDoesNotExist()
            percent(alertKey("UVC")).assertTextEquals("+100,0%")
            value(alertKey("UVC")).assertTextEquals("1,00")
            node("saved_row_${alertKey("UVC")}").assertHasNoClickAction()
                .performTouchInput { click(center) }
            value(alertKey("UVC")).assertTextEquals("1,00")
            node("saved_threshold_${alertKey("UVC")}").assertTextEquals("≥ 0,50")
            percent(alertKey("UVC")).assertTextEquals("+100,0%")
            percent(alertKey("UVA")).assertTextEquals("+100,0%")
            percent(alertKey("UV_TOTAL")).assertTextEquals("+33,3%")
            for (metric in listOf("UVC", "UVA", "UV_TOTAL")) {
                node("saved_row_${alertKey(metric)}").assertHasNoClickAction()
                node("saved_check_${alertKey(metric)}").assertDoesNotExist()
                node("saved_cross_${alertKey(metric)}").assertDoesNotExist()
            }
            node("saved_percentage_base_UV").assertDoesNotExist()
            if (current == "alert_session") {
                value(alertKey("UVC", 2)).assertTextEquals("3,00")
                percent(alertKey("UVC", 2)).assertTextEquals("+500,0%")
            }
            compose.runOnIdle { group.value = SessionChartGroup.BIOLOGICAL }
            val bioKey = alertKey("BIO_DNA_UV")
            node("saved_row_$bioKey").assertHasNoClickAction().performTouchInput { click(center) }
            value(bioKey).assertTextEquals("1,00")
            percent(bioKey).assertTextEquals("+100,0%")
            node("saved_check_$bioKey").assertDoesNotExist()
            node("saved_cross_$bioKey").assertDoesNotExist()
            compose.runOnIdle { group.value = SessionChartGroup.UV }
        }
        compose.runOnIdle { assertEquals(0, headerClicks) }
    }

    @Test fun biologicalRowsCanBeHiddenWithoutCreatingAPercentageBasisOrHidingSpectralChannels() {
        group.value = SessionChartGroup.BIOLOGICAL
        fixture()
        val key = acquisitionKey("BIO_DNA_UV")
        node("saved_row_$key").performClick().assertIsOff()
        value(key).assertTextEquals("—")
        node("saved_percentage_base_BIOLOGICAL").assertDoesNotExist()
        compose.runOnIdle { group.value = SessionChartGroup.UV }
        node("saved_row_${acquisitionKey("UVC")}").assertIsOn()
        compose.runOnIdle { group.value = SessionChartGroup.BIOLOGICAL }
        node("saved_row_$key").assertIsOff()
    }

    @Test fun detailChoicesResetAfterRestorationAndCannotLeakIntoAnotherRecordOrTheMainView() {
        val restorer = StateRestorationTester(compose)
        fixture(restorer)
        node("saved_row_${acquisitionKey("UVC")}").performClick()
        node("saved_percentage_base_UV").performClick()
        restorer.emulateSavedInstanceStateRestore()
        node("saved_row_${acquisitionKey("UVC")}").assertIsOn()
        node("saved_percentage_base_UV").assertTextEquals("% totale")
        node("saved_row_${acquisitionKey("UVC")}").performClick()
        node("saved_percentage_base_UV").performClick()
        compose.runOnIdle { detailKey.value = "another-detail" }
        node("saved_row_${acquisitionKey("UVC")}").assertIsOn()
        node("saved_percentage_base_UV").assertTextEquals("% totale")
        assertTrue(UvirLiveSpectrumDisplay().excludedChannels.isEmpty())
    }

    @Test fun reopeningTheSameSavedAcquisitionOrSessionAlwaysRestoresAllValuesAndTotalPercentages() {
        fixture()
        for (current in listOf("acquisition", "session")) {
            compose.runOnIdle { page.value = current }
            node("saved_row_${acquisitionKey("UVC")}").performClick().assertIsOff()
            node("saved_percentage_base_UV").performClick().assertTextEquals("% gruppo")
            compose.runOnIdle { visitOpen.value = false }
            compose.runOnIdle { visitOpen.value = true }
            node("saved_row_${acquisitionKey("UVC")}").assertIsOn()
            value(acquisitionKey("UVC")).assertTextEquals("1,00")
            node("saved_percentage_base_UV").assertTextEquals("% totale")
            if (current == "session") node("saved_row_${acquisitionKey("UVC", 2)}").assertIsOn()
        }
        assertEquals(1.0, records[0].sample.uvc, 0.0)
    }

    @Test fun bothSideEdgesAndVerticalPaddingToggleOnlyTheTargetRow() {
        fixture()
        val row = node("saved_row_${acquisitionKey("UVB")}")
        for (edge in 0..3) {
            row.performTouchInput { click(when (edge) {
                0 -> Offset(1f, height / 2f)
                1 -> Offset(width - 1f, height / 2f)
                2 -> Offset(width / 2f, 1f)
                else -> Offset(width / 2f, height - 1f)
            }) }
            row.assertIsOff()
            node("saved_row_${acquisitionKey("UVC")}").assertIsOn()
            node("saved_row_${acquisitionKey("UVA")}").assertIsOn()
            row.performClick().assertIsOn()
        }
        compose.runOnIdle { assertEquals(0, headerClicks) }
    }

    @Test fun controlsKeepTheirAlignmentAndRenderInAllFourViewsInBothThemesAndDirections() {
        fixture()
        for (current in listOf("acquisition", "session", "alert", "alert_session")) {
            for (dark in listOf(false, true)) for (layout in listOf(LayoutDirection.Ltr, LayoutDirection.Rtl)) {
                compose.runOnIdle { page.value = current; night.value = dark; direction.value = layout }
                val key = if (current.startsWith("alert")) alertKey("UVC") else acquisitionKey("UVC")
                val row = node("saved_row_$key").performScrollTo()
                if (current.startsWith("alert")) {
                    row.assertHasNoClickAction().performTouchInput { click(center) }
                    value(key).assertTextEquals("1,00")
                    node("saved_check_$key").assertDoesNotExist()
                    node("saved_cross_$key").assertDoesNotExist()
                    if (layout == LayoutDirection.Ltr) {
                        File(context.getExternalFilesDir(null), "saved-controls-$current-${if (dark) "night" else "day"}.png")
                            .outputStream().use { assertTrue(compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)) }
                    }
                    continue
                }
                if (row.fetchSemanticsNode().config[SemanticsProperties.ToggleableState] == androidx.compose.ui.state.ToggleableState.On) row.performClick()
                val cross = node("saved_cross_$key").assertIsDisplayed()
                val bounds = row.fetchSemanticsNode().boundsInRoot
                val mark = cross.fetchSemanticsNode().boundsInRoot
                val label = compose.onAllNodesWithText("UVC", useUnmergedTree = true).onFirst().fetchSemanticsNode().boundsInRoot
                assertEquals(label.center.y, mark.center.y, 1f)
                assertTrue(mark.height <= label.height)
                if (layout == LayoutDirection.Ltr) assertTrue(label.right < mark.left) else assertTrue(mark.right < label.left)
                assertTrue(bounds.left < mark.left && bounds.right > mark.right)
                if (layout == LayoutDirection.Ltr) {
                    File(context.getExternalFilesDir(null), "saved-controls-$current-${if (dark) "night" else "day"}.png")
                        .outputStream().use { assertTrue(compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)) }
                }
            }
        }
    }

    @Test fun switchingToChartKeepsTheSelectionAndSwitchingBackCanRestoreTheBand() {
        fixture()
        node("saved_row_${acquisitionKey("UVC")}").performClick()
        compose.runOnIdle { chart.value = true }
        compose.onNodeWithText("UVC").assertDoesNotExist()
        compose.runOnIdle { chart.value = false }
        node("saved_cross_${acquisitionKey("UVC")}").performTouchInput { click(center) }
        compose.runOnIdle { chart.value = true }
        compose.onAllNodesWithText("UVC").onFirst().performScrollTo().assertIsDisplayed()
    }
}
