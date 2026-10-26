package me.mondiversi.uvir

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.UUID
import kotlin.math.abs
import kotlin.math.roundToInt
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Synthetic Main View only: private test preferences, no sensor, database or recording. */
class UvirMainIslandOrderUiTest {
    @get:Rule val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val preferenceName = "island-order-${UUID.randomUUID()}-"
    private val isolatedContext = object : ContextWrapper(context) {
        override fun getSharedPreferences(name: String, mode: Int) = context.getSharedPreferences(preferenceName + name, mode)
    }
    private val preferences get() = isolatedContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    @After fun cleanUp() { context.deleteSharedPreferences(preferenceName + PREFS_NAME) }
    private val open = mutableStateOf(true)
    private val source = mutableStateOf("real")
    private val night = mutableStateOf(false)
    private val rtl = mutableStateOf(false)
    private val landscape = mutableStateOf(false)
    private var currentOrder = UvirDefaultMainIslandOrder
    private var collapseCalls = 0
    private var lastDisplay = UvirLiveSpectrumDisplay()
    private val sample = SensorSample(uvc = 1.0, uvb = 1.0, uva = 2.0,
        violetto = 2.0, blu = 2.0, verde = 2.0, giallo = 2.0, arancione = 2.0, rosso = 2.0,
        f8 = 2.0, nir = 2.0)

    private fun fixture(restorer: StateRestorationTester? = null) {
        val content: @Composable () -> Unit = {
            KeepUvirVerificationScreenOn()
            val configuration = uvirThemeConfiguration(LocalConfiguration.current,
                if (night.value) UvirAppTheme.DARK else UvirAppTheme.LIGHT)
            CompositionLocalProvider(LocalContext provides isolatedContext, LocalConfiguration provides configuration,
                LocalLayoutDirection provides if (rtl.value) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                MaterialTheme(colorScheme = if (night.value) darkColorScheme() else lightColorScheme()) {
                    val primary = if (night.value) Color.White else Color(0xFF101418)
                    val secondary = if (night.value) Color(0xFF90A4AE) else Color(0xFF546E7A)
                    val card = if (night.value) Color(0xFF1C242B) else Color.White
                    val track = if (night.value) Color(0xFF37474F) else Color(0xFFDCE3E7)
                    if (open.value) key(source.value) {
                        val order = rememberUvirMainIslandOrder()
                        var display by rememberUvirLiveSpectrumDisplay(source.value)
                        lastDisplay = display
                        currentOrder = order.value
                        val draw: @Composable (SensorGroup) -> Unit = { group ->
                            SensorGroupContent(group, expanded = false, onToggle = { collapseCalls++ },
                                sample = sample, uvTotal = 4.0, visibleTotal = 12.0, nirTotal = 4.0, hev = 4.0,
                                cardColor = card, primaryText = primary, secondaryText = secondary, trackColor = track,
                                display = display, onDisplayChange = { display = it },
                                collapsible = false, reorderControls = { title ->
                                    val index = order.value.indexOf(group)
                                    UvirMainIslandMoveControls(group, title, secondary,
                                        onMoveUp = if (index > 0) ({ order.value = moveUvirMainIsland(order.value, group, -1) }) else null,
                                        onMoveDown = if (index < order.value.lastIndex) ({ order.value = moveUvirMainIsland(order.value, group, 1) }) else null)
                                })
                        }
                        Surface(color = if (night.value) Color(0xFF101418) else Color(0xFFF4F7F9)) {
                            Column(Modifier.requiredWidth(if (landscape.value) 640.dp else 340.dp).height(580.dp)
                                .padding(12.dp).verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(UvirIslandSpacing)) {
                                if (landscape.value) UvirLiveLandscapeIslands({ _, _ -> }, order.value, draw)
                                else order.value.forEach { group -> key(group) { draw(group) } }
                            }
                        }
                    }
                }
            }
        }
        if (restorer == null) compose.setContent(content) else restorer.setContent(content)
    }

    private fun move(group: SensorGroup, up: Boolean) = compose.onNodeWithTag(
        "main_island_move_${if (up) "up" else "down"}_${group.name}", useUnmergedTree = true)
    private fun card(group: SensorGroup) = compose.onNodeWithTag("live_spectrum_card_${group.name}", useUnmergedTree = true)

    @Test fun percentAndEnabledOrDisabledChevronsNeverToggleTheGroupButTheLeftAreaDoes() {
        fixture()
        move(SensorGroup.UV, true).performTouchInput { click(center) }
        compose.runOnIdle { assertTrue(lastDisplay.excludedChannels.isEmpty()) }
        compose.onNodeWithTag("live_percentage_base_UV", useUnmergedTree = true).performTouchInput { click(center) }
        compose.runOnIdle { assertTrue(lastDisplay.excludedChannels.isEmpty()) }
        move(SensorGroup.UV, false).performTouchInput { click(center) }
        compose.runOnIdle { assertTrue(lastDisplay.excludedChannels.isEmpty()) }
        val header = compose.onNodeWithTag("live_island_header_UV", useUnmergedTree = true).performScrollTo()
        compose.onNodeWithTag("live_group_UV_check", useUnmergedTree = true).performTouchInput { click(center) }
        header.assertIsOff()
        compose.runOnIdle { assertEquals(0, collapseCalls) }
        header.performTouchInput { click(Offset(2f, height / 2f)) }
        header.assertIsOn()
        move(SensorGroup.UV, false).performScrollTo().performClick()
        move(SensorGroup.UV, false).performScrollTo().performTouchInput { click(center) }
        compose.runOnIdle { assertTrue(lastDisplay.excludedChannels.isEmpty()); assertEquals(0, collapseCalls) }
    }

    @Test fun groupToggleFeedbackCoversWholeHeadingWhileTouchTargetStaysOnLeftInBothThemes() {
        fixture()
        val root = compose.onRoot()
        val header = compose.onNodeWithTag("live_island_header_UV", useUnmergedTree = true)
        val heading = compose.onNodeWithTag("live_island_heading_UV", useUnmergedTree = true)
        for (dark in listOf(false, true)) {
            compose.runOnIdle { night.value = dark }
            Thread.sleep(400)
            val resting = root.captureToImage().toPixelMap()
            val bounds = heading.fetchSemanticsNode().boundsInRoot
            val origin = root.fetchSemanticsNode().boundsInRoot.topLeft
            val inset = with(compose.density) { 2.dp.toPx() }
            header.performTouchInput { down(Offset(50f, 10f)) }
            compose.mainClock.advanceTimeBy(200)
            compose.waitForIdle()
            Thread.sleep(350)
            val image = root.captureToImage()
            val pressed = image.toPixelMap()
            File(context.getExternalFilesDir(null), "main-group-header-pressed-${if (dark) "night" else "day"}.png")
                .outputStream().use { assertTrue(image.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)) }
            for (point in listOf(Offset(bounds.left + inset, bounds.center.y), Offset(bounds.right - inset, bounds.center.y),
                Offset(bounds.center.x, bounds.top + inset), Offset(bounds.center.x, bounds.bottom - inset))) {
                val radius = with(compose.density) { 2.dp.toPx() }.roundToInt()
                val centerX = (point.x - origin.x).roundToInt()
                val centerY = (point.y - origin.y).roundToInt()
                // Sample a patch: a sun ray or text stroke can hide the feedback at one pixel.
                val changed = (centerY - radius..centerY + radius).sumOf { y ->
                    (centerX - radius..centerX + radius).count { x ->
                        if (x !in 0 until pressed.width || y !in 0 until pressed.height) false
                        else {
                            val before = resting[x, y]
                            val after = pressed[x, y]
                            abs(before.red - after.red) + abs(before.green - after.green) + abs(before.blue - after.blue) > .01f
                        }
                    }
                }
                assertTrue("Header feedback covers $point in dark=$dark",
                    changed > 2)
            }
            header.performTouchInput { up() }
            header.performClick()
        }
    }

    @Test fun onlyIconNameMarkAndReadingToggleWhileRightSideAndBarNeverMuteTheIsland() {
        fixture()
        for (dark in listOf(false, true)) for (rightToLeft in listOf(false, true)) {
            compose.runOnIdle { night.value = dark; rtl.value = rightToLeft }
            for (group in UvirDefaultMainIslandOrder) {
                val suffix = group.name
                val heading = compose.onNodeWithTag("live_island_heading_$suffix", useUnmergedTree = true).performScrollTo()
                val header = compose.onNodeWithTag("live_island_header_$suffix", useUnmergedTree = true)
                heading.assert(SemanticsMatcher.keyNotDefined(SemanticsActions.OnClick))
                val toggleBounds = header.fetchSemanticsNode().boundsInRoot
                val headingBounds = heading.fetchSemanticsNode().boundsInRoot
                val moveBounds = compose.onNodeWithTag("main_island_moves_$suffix", useUnmergedTree = true)
                    .fetchSemanticsNode().boundsInRoot
                val basis = compose.onNodeWithTag("live_percentage_base_$suffix", useUnmergedTree = true)
                val basisBounds = basis.fetchSemanticsNode().boundsInRoot
                if (rightToLeft) assertTrue(basisBounds.right <= moveBounds.left && moveBounds.right <= toggleBounds.left)
                else assertTrue(toggleBounds.right <= moveBounds.left && moveBounds.right <= basisBounds.left)

                compose.onNodeWithTag("live_group_percentage_$suffix", useUnmergedTree = true)
                    .performTouchInput { click(center) }
                // Empty space below the percentage, including the progress bar, is not a visibility target.
                val percentX = basisBounds.center.x - headingBounds.left
                heading.performTouchInput { click(Offset(percentX, height - 2f)) }
                val controlsX = if (rightToLeft) (basisBounds.right + toggleBounds.left) / 2f
                    else (toggleBounds.right + basisBounds.left) / 2f
                heading.performTouchInput { click(Offset(controlsX - headingBounds.left, toggleBounds.center.y - headingBounds.top)) }
                compose.runOnIdle {
                    assertTrue("Right-hand controls never mute $group/$dark/$rightToLeft: ${lastDisplay.excludedChannels}",
                        lastDisplay.excludedChannels.isEmpty())
                    // The chevrons' expanded touch targets may reorder the island from this
                    // gutter. Restore the fixture's order before testing the remaining targets.
                    preferences.edit().putString(KEY_MAIN_ISLAND_ORDER, "UV,VISIBLE,NIR").apply()
                }
                heading.performScrollTo()
                val wasGroupPercentage = compose.runOnIdle { group in lastDisplay.groupPercentages }
                basis.performTouchInput { click(center) }
                compose.runOnIdle {
                    assertTrue(lastDisplay.excludedChannels.isEmpty())
                    assertEquals("Percentage basis toggles independently: $group/$dark/$rightToLeft",
                        !wasGroupPercentage, group in lastDisplay.groupPercentages)
                }
                basis.performTouchInput { click(center) }
                for (part in listOf("icon", "title", "total")) {
                    compose.onNodeWithTag("live_group_${part}_$suffix", useUnmergedTree = true)
                        .performScrollTo().performTouchInput { click(center) }
                    header.assertIsOff()
                    compose.onNodeWithTag("live_group_${suffix}_cross", useUnmergedTree = true)
                        .performTouchInput { click(center) }
                    header.assertIsOn()
                }
                compose.runOnIdle { assertTrue(lastDisplay.excludedChannels.isEmpty()); assertEquals(0, collapseCalls) }
            }
        }
    }

    @Test fun chevronsMoveUvToTheBottomAndBoundariesAreDisabledWithoutCollapsing() {
        fixture()
        move(SensorGroup.UV, true).assertIsNotEnabled()
        move(SensorGroup.NIR, false).assertIsNotEnabled()
        repeat(2) { move(SensorGroup.UV, false).performScrollTo().performTouchInput { click(center) } }
        compose.runOnIdle {
            assertEquals(listOf(SensorGroup.VISIBLE, SensorGroup.NIR, SensorGroup.UV), currentOrder)
            assertEquals(currentOrder, loadUvirMainIslandOrder(preferences))
            assertEquals(0, collapseCalls)
        }
        move(SensorGroup.UV, false).assertIsNotEnabled()
        val visible = card(SensorGroup.VISIBLE).getUnclippedBoundsInRoot()
        val infrared = card(SensorGroup.NIR).getUnclippedBoundsInRoot()
        val uv = card(SensorGroup.UV).getUnclippedBoundsInRoot()
        assertTrue(visible.top < infrared.top && infrared.top < uv.top)
        repeat(2) { move(SensorGroup.UV, true).performScrollTo().performClick() }
        compose.runOnIdle {
            assertEquals(UvirDefaultMainIslandOrder, currentOrder)
            assertEquals(setOf(KEY_MAIN_ISLAND_ORDER), preferences.all.keys)
            assertTrue(lastDisplay.excludedChannels.isEmpty())
        }
    }

    @Test fun reopeningSwitchingSourceAndRecreationRetainTheOrderAndResetPreferenceUpdatesItLive() {
        val restorer = StateRestorationTester(compose)
        fixture(restorer)
        move(SensorGroup.UV, false).performClick()
        val expected = listOf(SensorGroup.VISIBLE, SensorGroup.UV, SensorGroup.NIR)
        compose.runOnIdle { open.value = false }
        compose.runOnIdle { open.value = true; source.value = "mock" }
        compose.runOnIdle { assertEquals(expected, currentOrder) }
        restorer.emulateSavedInstanceStateRestore()
        compose.runOnIdle { assertEquals(expected, currentOrder); source.value = "another-real-sensor" }
        compose.runOnIdle {
            assertEquals(expected, currentOrder)
            preferences.edit().putString(KEY_MAIN_ISLAND_ORDER, "UV,VISIBLE,NIR").apply()
        }
        compose.runOnIdle { assertEquals(UvirDefaultMainIslandOrder, currentOrder) }
    }

    @Test fun headersKeepTheirMoveColumnAndCompactWithVisibilityInBothThemesAndRtl() {
        fixture()
        for (dark in listOf(false, true)) for (rightToLeft in listOf(false, true)) {
            compose.runOnIdle { night.value = dark; rtl.value = rightToLeft }
            for (group in UvirDefaultMainIslandOrder) {
                val header = compose.onNodeWithTag("live_island_header_${group.name}", useUnmergedTree = true)
                header.assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox))
                val total = compose.onNodeWithTag("live_group_total_${group.name}", useUnmergedTree = true).getUnclippedBoundsInRoot()
                val basis = compose.onNodeWithTag("live_percentage_base_${group.name}", useUnmergedTree = true).getUnclippedBoundsInRoot()
                val up = move(group, true).getUnclippedBoundsInRoot()
                val down = move(group, false).getUnclippedBoundsInRoot()
                assertEquals(up.left.value, down.left.value, .1f)
                assertEquals(up.bottom.value, down.top.value, .1f)
                if (rightToLeft) assertTrue(basis.right <= up.left && up.right <= total.left)
                else assertTrue(total.right <= up.left && up.right <= basis.left)
                val percentage = compose.onNodeWithTag("live_percentage_base_${group.name}", useUnmergedTree = true)
                percentage.performScrollTo().performTouchInput { click(center) }
                val switchedUp = move(group, true).getUnclippedBoundsInRoot()
                val switchedDown = move(group, false).getUnclippedBoundsInRoot()
                assertEquals("Percentage captions must not shift the move column", up.left.value, switchedUp.left.value, .1f)
                assertEquals(up.right.value, switchedUp.right.value, .1f)
                assertEquals(down.left.value, switchedDown.left.value, .1f)
                percentage.performTouchInput { click(center) }
            }
            val header = compose.onNodeWithTag("live_island_header_UV", useUnmergedTree = true).performScrollTo()
            header.performTouchInput { click(Offset(2f, 2f)) }
            header.assertIsOff()
            compose.runOnIdle { assertEquals(0, collapseCalls) }
            compose.onNodeWithTag("live_row_UVC", useUnmergedTree = true).assertDoesNotExist()
            compose.onNodeWithTag("live_group_bar_UV", useUnmergedTree = true).assertDoesNotExist()
            if (!rightToLeft) File(context.getExternalFilesDir(null), "main-island-order-${if (dark) "night" else "day"}.png")
                .outputStream().use { assertTrue(header.captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)) }
            header.performClick().assertIsOn()
            compose.onNodeWithTag("live_row_UVC", useUnmergedTree = true).assertExists()
            compose.onNodeWithTag("live_group_bar_UV", useUnmergedTree = true).assertExists()
        }
    }

    @Test fun landscapeReflowsTheSameSavedOrderWithoutChangingDefaultColumns() {
        landscape.value = true
        fixture()
        val uv = card(SensorGroup.UV).getUnclippedBoundsInRoot()
        val visible = card(SensorGroup.VISIBLE).getUnclippedBoundsInRoot()
        val ir = card(SensorGroup.NIR).getUnclippedBoundsInRoot()
        assertEquals(uv.left.value, ir.left.value, .1f)
        assertEquals(uv.top.value, visible.top.value, .1f)
        assertTrue(uv.right < visible.left)
        compose.runOnIdle { preferences.edit().putString(KEY_MAIN_ISLAND_ORDER, "VISIBLE,NIR,UV").apply() }
        val movedUv = card(SensorGroup.UV).getUnclippedBoundsInRoot()
        val movedVisible = card(SensorGroup.VISIBLE).getUnclippedBoundsInRoot()
        val movedIr = card(SensorGroup.NIR).getUnclippedBoundsInRoot()
        assertEquals(movedVisible.left.value, movedUv.left.value, .1f)
        assertTrue(movedUv.top > movedVisible.bottom)
        assertEquals(movedVisible.top.value, movedIr.top.value, .1f)
        assertTrue(movedIr.left > movedVisible.right)
        compose.runOnIdle { landscape.value = false }
        compose.runOnIdle { assertEquals(listOf(SensorGroup.VISIBLE, SensorGroup.NIR, SensorGroup.UV), currentOrder) }
    }

    @Test fun biologicalIslandAlsoStaysOpenWithoutAHeaderClickOrReorderButtons() {
        compose.setContent {
            MaterialTheme {
                Column(Modifier.width(340.dp).height(580.dp).verticalScroll(rememberScrollState())) {
                    BiologicalEffectsContent(sample, expanded = false, onToggle = { collapseCalls++ },
                        cardColor = Color.White, primaryText = Color.Black, secondaryText = Color.Gray, collapsible = false)
                }
            }
        }
        val header = compose.onNodeWithTag("live_biological_header", useUnmergedTree = true)
        header.assert(SemanticsMatcher.keyNotDefined(SemanticsActions.OnClick))
        header.performTouchInput { click(center) }
        compose.onNodeWithText(context.getString(R.string.dna_uv_proxy)).assertExists()
        compose.runOnIdle { assertEquals(0, collapseCalls) }
    }
}
