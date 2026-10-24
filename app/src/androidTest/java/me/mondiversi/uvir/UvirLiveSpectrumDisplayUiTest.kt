package me.mondiversi.uvir

import android.graphics.Bitmap
import android.content.Context
import android.content.ContextWrapper
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.Locale
import java.util.UUID
import kotlin.math.abs
import kotlin.math.roundToInt
import org.junit.Assert.*
import org.junit.After
import org.junit.Rule
import org.junit.Test

/** Presentation fixtures only: no sensor commands, preferences, recordings or archive writes. */
class UvirLiveSpectrumDisplayUiTest {
    @get:Rule val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val preferenceName = "main-spectrum-${UUID.randomUUID()}-"
    private val isolatedContext = object : ContextWrapper(context) {
        override fun getApplicationContext(): Context = this
        override fun getSharedPreferences(name: String, mode: Int) = context.getSharedPreferences(preferenceName + name, mode)
    }
    @After fun clearPreferences() { context.deleteSharedPreferences(preferenceName + PREFS_NAME) }
    private val visitOpen = mutableStateOf(true)
    private val group = mutableStateOf(SensorGroup.UV)
    private val night = mutableStateOf(false)
    private val direction = mutableStateOf(LayoutDirection.Ltr)
    private val unit = mutableStateOf(UvirIrradianceUnit.W_M2)
    private val sensorKey = mutableStateOf("synthetic-sensor")
    private val chart = mutableStateOf(false)
    private var toggles = 0
    private var primaryText = Color.Unspecified
    private var secondaryText = Color.Unspecified
    private var cardColor = Color.Unspecified
    private var trackColor = Color.Unspecified
    private var lastState = UvirLiveSpectrumDisplay()
    private val sample = SensorSample(uvc = 1.0, uvb = 1.0, uva = 2.0,
        violetto = 2.0, blu = 2.0, verde = 2.0, giallo = 2.0, arancione = 2.0, rosso = 2.0,
        f8 = 2.0, nir = 2.0)

    private fun fixture(restorer: StateRestorationTester? = null) {
        val content: @Composable () -> Unit = {
            KeepUvirVerificationScreenOn()
            val configuration = uvirThemeConfiguration(LocalConfiguration.current,
                if (night.value) UvirAppTheme.DARK else UvirAppTheme.LIGHT).apply {
                setLocales(LocaleList(Locale.ITALY))
            }
            CompositionLocalProvider(LocalContext provides isolatedContext, LocalConfiguration provides configuration,
                LocalLayoutDirection provides direction.value,
                LocalUvirNumericFormat provides UvirNumericFormat.EUROPEAN,
                LocalUvirIrradianceUnit provides unit.value) {
                MaterialTheme(colorScheme = if (night.value) darkColorScheme() else lightColorScheme()) {
                    val colors = MaterialTheme.colorScheme
                    primaryText = colors.onSurface
                    secondaryText = colors.onSurfaceVariant
                    cardColor = colors.surfaceVariant
                    trackColor = colors.outlineVariant
                    if (visitOpen.value) {
                    var state by rememberUvirLiveSpectrumDisplay(sensorKey.value)
                    lastState = state
                    Surface {
                        Column(Modifier.width(340.dp).height(620.dp).padding(12.dp)
                            .verticalScroll(rememberScrollState())) {
                            SensorGroupContent(group.value, true, { toggles++ }, showChart = chart.value,
                                liveHistory = listOf(LiveSamplePoint(1_000, sample), LiveSamplePoint(2_000, sample)),
                                sample = sample, uvTotal = 4.0, visibleTotal = 12.0, nirTotal = 4.0, hev = 4.0,
                                cardColor = colors.surfaceVariant, primaryText = colors.onSurface,
                                secondaryText = colors.onSurfaceVariant, trackColor = colors.outlineVariant,
                                display = state, onDisplayChange = { state = it })
                        }
                    }
                    }
                }
            }
        }
        if (restorer == null) compose.setContent(content) else restorer.setContent(content)
    }

    @Test fun excludedIslandTitleTurnsGrayAndReturnsToNormalWithAnyVisibleChildInBothThemes() {
        fixture()
        for (dark in listOf(false, true)) for (current in UvirLiveSpectrumDisplay.irradianceGroups) {
            compose.runOnIdle { night.value = dark; group.value = current }
            compose.waitForIdle()
            val title = compose.onNodeWithTag("live_group_title_${current.name}", useUnmergedTree = true)
            val header = compose.onNodeWithTag("live_island_header_${current.name}", useUnmergedTree = true)
            val total = compose.onNodeWithTag("live_group_total_${current.name}", useUnmergedTree = true)
            val percentage = compose.onNodeWithTag("live_group_percentage_${current.name}", useUnmergedTree = true)
            val originalTotal = total.fetchSemanticsNode().config[SemanticsProperties.Text].single().text
            val originalPercentage = percentage.fetchSemanticsNode().config[SemanticsProperties.Text].single().text
            title.assertUvirActionTextColor(primaryText)
            header.performClick().assertIsOff()
            title.assertUvirActionTextColor(secondaryText.copy(alpha = .55f))
            total.assertTextEquals("—").assertUvirActionTextColor(secondaryText.copy(alpha = .55f))
            percentage.assertTextEquals("—").assertUvirActionTextColor(secondaryText.copy(alpha = .55f))
            compose.onNodeWithTag("live_group_bar_${current.name}", useUnmergedTree = true)
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.ProgressBarRangeInfo,
                    androidx.compose.ui.semantics.ProgressBarRangeInfo(0f, 0f..1f)))
            compose.mainClock.advanceTimeBy(300)
            compose.waitForIdle()
            Thread.sleep(400)
            val icon = compose.onNodeWithTag("live_group_icon_${current.name}", useUnmergedTree = true)
                .captureToImage().toPixelMap()
            val expectedIcon = secondaryText.copy(alpha = .55f).compositeOver(cardColor)
            val matching = (0 until icon.height).sumOf { y -> (0 until icon.width).count { x ->
                val pixel = icon[x, y]
                abs(pixel.red - expectedIcon.red) + abs(pixel.green - expectedIcon.green) + abs(pixel.blue - expectedIcon.blue) < .04f
            } }
            assertTrue("Excluded icon is gray in $current / dark=$dark", matching > 4)
            compose.onNodeWithTag("live_row_${UvirLiveSpectrumDisplay.channels(current).first().name}", useUnmergedTree = true)
                .performClick().assertIsOn()
            title.assertUvirActionTextColor(primaryText)
            header.performClick().assertIsOff()
            title.assertUvirActionTextColor(secondaryText.copy(alpha = .55f))
            header.performClick().assertIsOn()
            title.assertUvirActionTextColor(primaryText)
            total.assertTextEquals(originalTotal)
            percentage.assertTextEquals(originalPercentage)
        }
    }

    @Test fun groupMarkHidesAndRestoresEveryChildWithoutCollapsingOrChangingOtherGroups() {
        fixture()
        for (current in UvirLiveSpectrumDisplay.irradianceGroups) {
            compose.runOnIdle { group.value = current }
            val control = compose.onNodeWithTag("live_island_header_${current.name}", useUnmergedTree = true)
            control.assertIsOn().performTouchInput { click(center) }.assertIsOff()
            compose.onNodeWithTag("live_group_${current.name}_cross", useUnmergedTree = true).assertIsDisplayed()
            for (metric in UvirLiveSpectrumDisplay.channels(current)) {
                compose.onNodeWithTag("live_row_${metric.name}", useUnmergedTree = true).assertIsOff()
                compose.onNodeWithTag("live_value_${metric.name}", useUnmergedTree = true).assertTextEquals("—")
            }
            compose.runOnIdle {
                assertEquals(0, toggles)
                UvirLiveSpectrumDisplay.irradianceGroups.filter { it != current }.forEach { assertTrue(lastState.groupVisible(it)) }
            }
            control.performClick().assertIsOn()
            for (metric in UvirLiveSpectrumDisplay.channels(current))
                compose.onNodeWithTag("live_row_${metric.name}", useUnmergedTree = true).assertIsOn()
        }
        assertEquals(1.0, sample.uvc, 0.0)
    }

    @Test fun groupExclusionUsesTheSameDurableMainBandFlagsAndCanBeReactivatedIndividually() {
        fixture()
        val control = compose.onNodeWithTag("live_island_header_UV", useUnmergedTree = true)
        control.performClick().assertIsOff()
        compose.runOnIdle { visitOpen.value = false }
        compose.runOnIdle { visitOpen.value = true; sensorKey.value = "mock-source" }
        control.assertIsOff()
        compose.onNodeWithTag("live_row_UVC", useUnmergedTree = true).performClick().assertIsOn()
        control.assertIsOn()
        compose.onNodeWithTag("live_row_UVB", useUnmergedTree = true).assertIsOff()
        control.performClick().assertIsOff()
        control.performClick().assertIsOn()
        compose.runOnIdle { assertTrue(lastState.excludedChannels.isEmpty()); assertEquals(0, toggles) }
    }

    @Test fun groupMarkFollowsOnlyTheNameInBothThemesAndDirections() {
        fixture()
        for (dark in listOf(false, true)) for (layout in listOf(LayoutDirection.Ltr, LayoutDirection.Rtl)) {
            compose.runOnIdle { night.value = dark; direction.value = layout }
            val title = compose.onNodeWithText(context.getString(R.string.uv_radiation), useUnmergedTree = true).getUnclippedBoundsInRoot()
            val mark = compose.onNodeWithTag("live_group_UV_check", useUnmergedTree = true).getUnclippedBoundsInRoot()
            val reading = compose.onNodeWithTag("live_group_total_UV", useUnmergedTree = true).getUnclippedBoundsInRoot()
            val childTitle = compose.onNodeWithTag("live_name_UVC", useUnmergedTree = true).getUnclippedBoundsInRoot()
            val childMark = compose.onNodeWithTag("live_check_UVC", useUnmergedTree = true).getUnclippedBoundsInRoot()
            assertEquals((title.top.value + title.bottom.value) / 2f, (mark.top.value + mark.bottom.value) / 2f, .1f)
            if (layout == LayoutDirection.Ltr) assertTrue(title.right < mark.left) else assertTrue(mark.right < title.left)
            val gap = if (layout == LayoutDirection.Ltr) mark.left - title.right else title.left - mark.right
            val childGap = if (layout == LayoutDirection.Ltr) childMark.left - childTitle.right else childTitle.left - childMark.right
            assertEquals(gap.value, childGap.value, .01f)
            assertTrue(mark.bottom < reading.top)
            compose.onNodeWithTag("live_group_UV_check", useUnmergedTree = true)
                .assert(SemanticsMatcher.keyNotDefined(SemanticsActions.OnClick))
        }
    }

    @Test fun percentageLinkChangesOnlyItsOwnIslandWithoutCollapsingIt() {
        fixture()
        compose.onNodeWithTag("live_percentage_UVC", useUnmergedTree = true).assertTextEquals("5,0%")
        compose.onNodeWithTag("live_percentage_base_UV", useUnmergedTree = true).assertTextEquals("% totale").performClick()
        compose.onNodeWithTag("live_percentage_base_UV", useUnmergedTree = true).assertTextEquals("% gruppo")
        compose.onNodeWithTag("live_percentage_UVC", useUnmergedTree = true).assertTextEquals("25,0%")
        compose.onNodeWithTag("live_group_percentage_UV", useUnmergedTree = true).assertTextEquals("20,0%")
        compose.runOnIdle { assertEquals(0, toggles); group.value = SensorGroup.NIR }
        compose.onNodeWithTag("live_percentage_base_NIR", useUnmergedTree = true).assertTextEquals("% totale")
        compose.runOnIdle { group.value = SensorGroup.UV }
        compose.onNodeWithTag("live_percentage_base_UV", useUnmergedTree = true).assertTextEquals("% gruppo")
    }

    @Test fun everyMainIslandShowsItsBasisLinkAboveThePercentageInBothThemesAndDirections() {
        fixture()
        for (current in listOf(SensorGroup.UV, SensorGroup.VISIBLE, SensorGroup.NIR)) {
            for (dark in listOf(false, true)) for (layout in listOf(LayoutDirection.Ltr, LayoutDirection.Rtl)) {
                compose.runOnIdle { group.value = current; night.value = dark; direction.value = layout }
                val percent = compose.onNodeWithTag("live_group_percentage_${current.name}", useUnmergedTree = true)
                val basis = compose.onNodeWithTag("live_percentage_base_${current.name}", useUnmergedTree = true)
                val originalPercentage = percent.fetchSemanticsNode().config[SemanticsProperties.Text].single().text
                for (label in listOf("% totale", "% gruppo")) {
                    basis.assertIsDisplayed().assertTextEquals(label)
                    percent.assertTextEquals(originalPercentage)
                    val valueBounds = percent.fetchSemanticsNode().boundsInRoot
                    val linkBounds = basis.fetchSemanticsNode().boundsInRoot
                    assertTrue("Basis above percentage: $current/$dark/$layout", linkBounds.bottom < valueBounds.top)
                    if (layout == LayoutDirection.Ltr) assertEquals(valueBounds.right, linkBounds.right, 1f)
                    else assertEquals(valueBounds.left, linkBounds.left, 1f)
                    basis.performTouchInput { click(center) }
                }
                compose.runOnIdle { assertEquals(0, toggles) }
                if (layout == LayoutDirection.Ltr) {
                    File(context.getExternalFilesDir(null), "main-percent-order-restored-${current.name}-${if (dark) "night" else "day"}.png")
                        .outputStream().use { assertTrue(compose.onRoot().captureToImage().asAndroidBitmap()
                            .compress(Bitmap.CompressFormat.PNG, 100, it)) }
                }
            }
        }
    }

    @Test fun everyHeaderKeepsItsGlobalPercentageWhileItsChildrenSwitchToGroupShares() {
        fixture()
        for ((current, channel, shares) in listOf(
            Triple(SensorGroup.UV, "UVC", Triple("20,0%", "5,0%", "25,0%")),
            Triple(SensorGroup.VISIBLE, "BLUE", Triple("60,0%", "10,0%", "16,7%")),
            Triple(SensorGroup.NIR, "NIR", Triple("20,0%", "10,0%", "50,0%"))
        )) {
            val (header, globalShare, groupShare) = shares
            compose.runOnIdle { group.value = current }
            val headerNode = compose.onNodeWithTag("live_group_percentage_$current", useUnmergedTree = true)
            val headerBar = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo),
                useUnmergedTree = true).onFirst()
            val initialBar = headerBar.fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo]
            headerNode.assertTextEquals(header)
            compose.onNodeWithTag("live_percentage_$channel", useUnmergedTree = true).assertTextEquals(globalShare)
            compose.onNodeWithTag("live_percentage_base_$current", useUnmergedTree = true).performClick()
            compose.onNodeWithTag("live_percentage_$channel", useUnmergedTree = true).assertTextEquals(groupShare)
            headerNode.assertTextEquals(header)
            headerBar.assert(SemanticsMatcher.expectValue(SemanticsProperties.ProgressBarRangeInfo, initialBar))
            compose.onNodeWithTag("live_percentage_base_$current", useUnmergedTree = true).performClick()
            headerNode.assertTextEquals(header)
        }
    }

    @Test fun rowHidesValuesAndBarsAndRecalculatesTheVisibleTotal() {
        fixture()
        compose.onNodeWithTag("live_row_UVC", useUnmergedTree = true).performClick().assertIsOff()
        compose.onNodeWithTag("live_value_UVC", useUnmergedTree = true).assertTextEquals("—")
        compose.onNodeWithTag("live_percentage_UVC", useUnmergedTree = true).assertTextEquals("—")
        compose.onNodeWithTag("live_group_total_UV", useUnmergedTree = true).assertTextEquals("3,00 W/m²")
        compose.onNodeWithTag("live_value_UVA", useUnmergedTree = true).assertTextEquals("2,00 W/m²")
        compose.onNodeWithTag("live_percentage_UVA", useUnmergedTree = true).assertTextEquals("10,5%")
        compose.onNodeWithTag("live_row_UVC", useUnmergedTree = true).performClick().assertIsOn()
        compose.onNodeWithTag("live_value_UVC", useUnmergedTree = true).assertTextEquals("1,00 W/m²")
        compose.onNodeWithTag("live_group_total_UV", useUnmergedTree = true).assertTextEquals("4,00 W/m²")
    }

    @Test fun rowsHaveNoEyeAndNamesReadingsAndBarsKeepTheFullRowWidth() {
        fixture()
        for (dark in listOf(false, true)) for (layout in listOf(LayoutDirection.Ltr, LayoutDirection.Rtl)) {
            for ((current, metric) in listOf(SensorGroup.UV to "UVC",
                SensorGroup.VISIBLE to "HEV", SensorGroup.NIR to "FAR_RED")) {
                compose.runOnIdle { night.value = dark; direction.value = layout; group.value = current }
                val row = compose.onNodeWithTag("live_row_$metric", useUnmergedTree = true)
                row.performScrollTo()
                val rowBounds = row.fetchSemanticsNode().boundsInRoot
                val bar = compose.onNodeWithTag("live_bar_$metric", useUnmergedTree = true)
                    .fetchSemanticsNode().boundsInRoot
                val value = compose.onNodeWithTag("live_value_$metric", useUnmergedTree = true)
                    .fetchSemanticsNode().boundsInRoot
                val name = compose.onNodeWithTag("live_name_$metric", useUnmergedTree = true)
                    .fetchSemanticsNode().boundsInRoot
                val indicator = compose.onNodeWithTag("live_selection_slot_$metric", useUnmergedTree = true)
                    .fetchSemanticsNode().boundsInRoot
                compose.onNodeWithTag("live_visibility_$metric", useUnmergedTree = true).assertDoesNotExist()
                compose.onNodeWithTag("live_checkbox_$metric", useUnmergedTree = true).assertDoesNotExist()
                row.assertHasClickAction()
                val cardBounds = compose.onNodeWithTag("live_spectrum_card_$current", useUnmergedTree = true)
                    .fetchSemanticsNode().boundsInRoot
                val sideInset = with(compose.density) { UvirIslandContentPadding.toPx() }
                assertEquals(cardBounds.left, rowBounds.left, 1f)
                assertEquals(cardBounds.right, rowBounds.right, 1f)
                assertEquals(rowBounds.left + sideInset, bar.left, 1f)
                assertEquals(rowBounds.right - sideInset, bar.right, 1f)
                assertTrue("Name stays above the reading", value.top >= name.bottom)
                if (layout == LayoutDirection.Ltr) {
                    assertEquals(bar.left, value.left, 1f)
                    assertEquals(bar.left, name.left, 1f)
                    assertTrue("Tick follows only the name", name.right < indicator.left)
                } else {
                    assertEquals(bar.right, value.right, 1f)
                    assertEquals(bar.right, name.right, 1f)
                    assertTrue("Tick follows only the name in RTL", indicator.right < name.left)
                }
                assertEquals(name.center.y, indicator.center.y, 1f)
            }
        }
    }

    @Test fun tickSharesTheRowsSingleAccessibleToggle() {
        fixture()
        val row = compose.onNodeWithTag("live_row_UVC", useUnmergedTree = true)
        val tick = compose.onNodeWithTag("live_check_UVC", useUnmergedTree = true)
        row.assertIsOn().assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox))
        tick.assert(SemanticsMatcher.keyNotDefined(SemanticsActions.OnClick))
        compose.onAllNodes(isToggleable()).assertCountEquals(4) // Three bands plus their independent group control.
        tick.performTouchInput { click(center) }
        row.assertIsOff()
        tick.assertDoesNotExist()
        val cross = compose.onNodeWithTag("live_cross_UVC", useUnmergedTree = true)
        cross.assertIsDisplayed().assert(SemanticsMatcher.keyNotDefined(SemanticsActions.OnClick))
        compose.onNodeWithTag("live_value_UVC", useUnmergedTree = true).assertTextEquals("—")
        cross.performTouchInput { click(center) }
        row.assertIsOn()
        tick.assertExists()
        cross.assertDoesNotExist()
        compose.onNodeWithTag("live_value_UVC", useUnmergedTree = true).assertTextEquals("1,00 W/m²")
        compose.runOnIdle { assertEquals(0, toggles) }
    }

    @Test fun tickUsesTheSameTextColorInBothThemesAndChangesToCrossWithoutMovingTheName() {
        fixture()
        val row = compose.onNodeWithTag("live_row_UVC", useUnmergedTree = true)
        val tick = compose.onNodeWithTag("live_check_UVC", useUnmergedTree = true)
        val name = compose.onNodeWithTag("live_name_UVC", useUnmergedTree = true)
        for (dark in listOf(false, true)) {
            compose.runOnIdle { night.value = dark }
            Thread.sleep(400)
            val pixels = tick.captureToImage().toPixelMap()
            val expected = primaryText
            val matching = (0 until pixels.height).sumOf { y ->
                (0 until pixels.width).count { x ->
                    val actual = pixels[x, y]
                    abs(actual.red - expected.red) + abs(actual.green - expected.green) + abs(actual.blue - expected.blue) < .03f
                }
            }
            assertTrue("Tick follows the text color, not the accent: dark=$dark", matching > 4)
            val before = name.fetchSemanticsNode().boundsInRoot
            row.performClick().assertIsOff()
            tick.assertDoesNotExist()
            // Inspect the resting color, not the transient native ripple,
            // whose exit animation runs on wall time rather than test time.
            compose.mainClock.advanceTimeBy(300)
            compose.waitForIdle()
            Thread.sleep(400)
            val cross = compose.onNodeWithTag("live_cross_UVC", useUnmergedTree = true).assertIsDisplayed()
            val crossPixels = cross.captureToImage().toPixelMap()
            val excludedText = secondaryText.copy(alpha = .55f).compositeOver(cardColor)
            val crossMatching = (0 until crossPixels.height).sumOf { y ->
                (0 until crossPixels.width).count { x ->
                    val actual = crossPixels[x, y]
                    abs(actual.red - excludedText.red) + abs(actual.green - excludedText.green) +
                        abs(actual.blue - excludedText.blue) < .03f
                }
            }
            assertTrue("Cross follows the excluded text color: dark=$dark", crossMatching > 4)
            assertEquals(before, name.fetchSemanticsNode().boundsInRoot)
            row.performClick().assertIsOn()
        }
    }

    @Test fun paddingAtBothSidesAndAboveAndBelowTheBandIsPartOfTheSameTapTarget() {
        fixture()
        val row = compose.onNodeWithTag("live_row_UVB", useUnmergedTree = true)
        for (edge in 0..3) {
            row.performTouchInput {
                click(when (edge) {
                    0 -> Offset(1f, height / 2f)
                    1 -> Offset(width - 1f, height / 2f)
                    2 -> Offset(width / 2f, 1f)
                    else -> Offset(width / 2f, height - 1f)
                })
            }
            compose.onNodeWithTag("live_value_UVB", useUnmergedTree = true).assertTextEquals("—")
            compose.onNodeWithTag("live_value_UVC", useUnmergedTree = true).assertTextEquals("1,00 W/m²")
            compose.onNodeWithTag("live_value_UVA", useUnmergedTree = true).assertTextEquals("2,00 W/m²")
            row.performClick()
        }
        compose.runOnIdle { assertEquals(0, toggles) }
    }

    @Test fun pressedFeedbackReachesSideAndVerticalPaddingInBothThemes() {
        fixture()
        val root = compose.onRoot()
        val row = compose.onNodeWithTag("live_row_UVB", useUnmergedTree = true)
        for (dark in listOf(false, true)) {
            compose.runOnIdle { night.value = dark }
            // Android's native ripple uses wall time rather than the Compose test clock.
            Thread.sleep(400)
            val resting = root.captureToImage().toPixelMap()
            val bounds = row.fetchSemanticsNode().boundsInRoot
            val origin = root.fetchSemanticsNode().boundsInRoot.topLeft
            val inset = with(compose.density) { 2.dp.toPx() }
            row.performTouchInput { down(center) }
            // Scroll containers delay PressInteraction on the Compose clock before
            // Android starts drawing the native ripple on wall time.
            compose.mainClock.advanceTimeBy(200)
            compose.waitForIdle()
            Thread.sleep(350)
            val pressedImage = root.captureToImage()
            val pressed = pressedImage.toPixelMap()
            File(context.getExternalFilesDir(null), "live-row-pressed-${if (dark) "night" else "day"}.png")
                .outputStream().use { assertTrue(pressedImage.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)) }
            for (point in listOf(Offset(bounds.left + inset, bounds.center.y),
                Offset(bounds.right - inset, bounds.center.y), Offset(bounds.center.x, bounds.top + inset),
                Offset(bounds.center.x, bounds.bottom - inset))) {
                val x = (point.x - origin.x).roundToInt().coerceIn(0, pressed.width - 1)
                val y = (point.y - origin.y).roundToInt().coerceIn(0, pressed.height - 1)
                val before = resting[x, y]
                val after = pressed[x, y]
                assertTrue("Feedback covers padding: dark=$dark, point=$point",
                    abs(before.red - after.red) + abs(before.green - after.green) + abs(before.blue - after.blue) > .01f)
            }
            row.performTouchInput { up() }
            row.performClick()
        }
    }

    @Test fun tappingTheNameValuePercentageBarOrEmptyRowAreaTogglesTheSameBand() {
        fixture()
        for (part in listOf("live_name_UVC", "live_value_UVC", "live_percentage_UVC", "live_bar_UVC")) {
            compose.onNodeWithTag(part, useUnmergedTree = true).performTouchInput { click(center) }
            compose.onNodeWithTag("live_value_UVC", useUnmergedTree = true).assertTextEquals("—")
            compose.onNodeWithTag("live_row_UVC", useUnmergedTree = true).assertIsOff()
                .performTouchInput { click(Offset(width * .5f, height * .25f)) }
            compose.onNodeWithTag("live_value_UVC", useUnmergedTree = true).assertTextEquals("1,00 W/m²")
        }
        compose.runOnIdle { assertEquals(0, toggles) }
    }

    @Test fun hevAndItsComponentsStayConsistentInTheLiveUi() {
        group.value = SensorGroup.VISIBLE
        fixture()
        compose.onNodeWithTag("live_row_HEV", useUnmergedTree = true).performClick()
        compose.onNodeWithTag("live_value_HEV", useUnmergedTree = true).assertTextEquals("—")
        compose.onNodeWithTag("live_value_VIOLET", useUnmergedTree = true).assertTextEquals("—")
        compose.onNodeWithTag("live_value_BLUE", useUnmergedTree = true).assertTextEquals("—")
        compose.onNodeWithTag("live_group_total_VISIBLE", useUnmergedTree = true).assertTextEquals("8,00 W/m²")
        compose.onNodeWithTag("live_row_VIOLET", useUnmergedTree = true).performClick()
        compose.onNodeWithTag("live_value_HEV", useUnmergedTree = true).assertTextEquals("2,00 W/m²")
        compose.onNodeWithTag("live_value_BLUE", useUnmergedTree = true).assertTextEquals("—")
        compose.onNodeWithTag("live_group_total_VISIBLE", useUnmergedTree = true).assertTextEquals("10,00 W/m²")
    }

    @Test fun onlyMainBandVisibilitySurvivesRestorationAndSourceChanges() {
        val restorer = StateRestorationTester(compose)
        fixture(restorer)
        compose.onNodeWithTag("live_row_UVC", useUnmergedTree = true).performClick()
        compose.onNodeWithTag("live_percentage_base_UV", useUnmergedTree = true).performClick()
        restorer.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("live_value_UVC", useUnmergedTree = true).assertTextEquals("—")
        compose.onNodeWithTag("live_percentage_base_UV", useUnmergedTree = true).assertTextEquals("% totale")
        compose.runOnIdle { sensorKey.value = "another-synthetic-sensor" }
        compose.onNodeWithTag("live_value_UVC", useUnmergedTree = true).assertTextEquals("—")
        compose.onNodeWithTag("live_percentage_base_UV", useUnmergedTree = true).assertTextEquals("% totale")
    }

    @Test fun reopeningMainKeepsExcludedBandsUntilReenabledButNeverPersistsPercentageBases() {
        fixture()
        val preferences = isolatedContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        compose.onNodeWithTag("live_percentage_base_UV", useUnmergedTree = true).performClick()
        compose.runOnIdle { assertFalse(preferences.contains(KEY_MAIN_HIDDEN_SPECTRUM_CHANNELS)) }
        compose.onNodeWithTag("live_row_UVC", useUnmergedTree = true).performClick()
        compose.runOnIdle { group.value = SensorGroup.VISIBLE }
        compose.onNodeWithTag("live_row_HEV", useUnmergedTree = true).performClick()
        compose.runOnIdle {
            assertEquals(setOf(ThresholdAlertMetric.UVC, ThresholdAlertMetric.VIOLET, ThresholdAlertMetric.BLUE),
                loadUvirMainHiddenChannels(preferences))
            visitOpen.value = false
        }
        compose.runOnIdle { visitOpen.value = true; group.value = SensorGroup.UV }
        compose.onNodeWithTag("live_value_UVC", useUnmergedTree = true).assertTextEquals("—")
        compose.onNodeWithTag("live_percentage_base_UV", useUnmergedTree = true).assertTextEquals("% totale")
        compose.onNodeWithTag("live_row_UVC", useUnmergedTree = true).performClick()
        compose.runOnIdle { group.value = SensorGroup.VISIBLE }
        compose.onNodeWithTag("live_value_HEV", useUnmergedTree = true).assertTextEquals("—")
        compose.onNodeWithTag("live_row_HEV", useUnmergedTree = true).performClick()
        compose.runOnIdle { assertTrue(loadUvirMainHiddenChannels(preferences).isEmpty()); visitOpen.value = false }
        compose.runOnIdle { visitOpen.value = true; group.value = SensorGroup.UV }
        compose.onNodeWithTag("live_value_UVC", useUnmergedTree = true).assertTextEquals("1,00 W/m²")
        compose.runOnIdle { assertTrue(lastState.excludedChannels.isEmpty()) }
    }

    @Test fun resettingOnlyTheMainVisibilityPreferenceImmediatelyRestoresAllBands() {
        fixture()
        compose.onNodeWithTag("live_row_UVC", useUnmergedTree = true).performClick()
        compose.onNodeWithTag("live_percentage_base_UV", useUnmergedTree = true).performClick()
        compose.runOnIdle { isolatedContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putStringSet(KEY_MAIN_HIDDEN_SPECTRUM_CHANNELS, emptySet()).commit() }
        compose.onNodeWithTag("live_value_UVC", useUnmergedTree = true).assertTextEquals("1,00 W/m²")
        compose.onNodeWithTag("live_percentage_base_UV", useUnmergedTree = true).assertTextEquals("% gruppo")
    }

    @Test fun hiddenValuesRemainHiddenWhenUnitsChange() {
        fixture()
        compose.onNodeWithTag("live_row_UVC", useUnmergedTree = true).performClick()
        for (current in UvirIrradianceUnit.entries) {
            compose.runOnIdle { unit.value = current }
            compose.onNodeWithTag("live_value_UVC", useUnmergedTree = true).assertTextEquals("—")
            compose.onNodeWithTag("live_percentage_UVA", useUnmergedTree = true).assertTextEquals("10,5%")
        }
        compose.runOnIdle { assertEquals(setOf(ThresholdAlertMetric.UVC), lastState.excludedChannels) }
    }

    @Test fun excludedChannelsKeepTheirFullWidthEmptyGrayTrackAndBothValueDashes() {
        fixture()
        for (dark in listOf(false, true)) for ((current, metric) in listOf(
            SensorGroup.UV to "UVC", SensorGroup.VISIBLE to "HEV", SensorGroup.NIR to "FAR_RED")) {
            compose.runOnIdle { night.value = dark; group.value = current }
            val row = compose.onNodeWithTag("live_row_$metric", useUnmergedTree = true)
            val bar = compose.onNodeWithTag("live_bar_$metric", useUnmergedTree = true)
            val before = bar.assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            row.performClick().assertIsOff()
            compose.onNodeWithTag("live_value_$metric", useUnmergedTree = true).assertTextEquals("—")
            compose.onNodeWithTag("live_percentage_$metric", useUnmergedTree = true).assertTextEquals("—")
            bar.assertIsDisplayed().assert(SemanticsMatcher.expectValue(SemanticsProperties.ProgressBarRangeInfo,
                androidx.compose.ui.semantics.ProgressBarRangeInfo(0f, 0f..1f)))
            val after = bar.fetchSemanticsNode().boundsInRoot
            assertEquals(before.width, after.width, 0f)
            assertEquals(before.height, after.height, 0f)
            // Inspect the resting track after the native touch ripple has faded.
            compose.mainClock.advanceTimeBy(300)
            compose.waitForIdle()
            Thread.sleep(400)
            val pixels = bar.captureToImage().toPixelMap()
            for (fraction in listOf(.1f, .5f, .9f)) {
                val color = pixels[(pixels.width * fraction).toInt(), pixels.height / 2]
                assertEquals(trackColor.red, color.red, .01f)
                assertEquals(trackColor.green, color.green, .01f)
                assertEquals(trackColor.blue, color.blue, .01f)
            }
            row.performClick().assertIsOn()
            assertTrue(bar.fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo].current > 0f)
        }
    }

    @Test fun liveChartsOmitExcludedChannelsWithoutLosingTheSelection() {
        fixture()
        compose.onNodeWithTag("live_row_UVC", useUnmergedTree = true).performClick()
        compose.runOnIdle { chart.value = true }
        compose.onNodeWithText("UVC", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithText("UVA", useUnmergedTree = true).assertExists()
        compose.runOnIdle { chart.value = false }
        compose.onNodeWithTag("live_value_UVC", useUnmergedTree = true).assertTextEquals("—")
    }

    @Test fun controlsAndSuppressedRowsRenderInBothThemesAndDirections() {
        fixture()
        compose.onNodeWithTag("live_row_UVC", useUnmergedTree = true).performClick()
        for (dark in listOf(false, true)) for (layout in listOf(LayoutDirection.Ltr, LayoutDirection.Rtl)) {
            compose.runOnIdle { night.value = dark; direction.value = layout }
            compose.onNodeWithTag("live_row_UVC", useUnmergedTree = true).assertIsDisplayed().assertIsOff()
            compose.onNodeWithTag("live_percentage_base_UV", useUnmergedTree = true).assertIsDisplayed()
            compose.onNodeWithTag("live_value_UVA", useUnmergedTree = true).assertTextEquals("2,00 W/m²")
            if (layout == LayoutDirection.Ltr) {
                File(context.getExternalFilesDir(null), "live-spectrum-${if (dark) "night" else "day"}.png")
                    .outputStream().use { stream ->
                        assertTrue(compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, stream))
                    }
            }
        }
    }
}
