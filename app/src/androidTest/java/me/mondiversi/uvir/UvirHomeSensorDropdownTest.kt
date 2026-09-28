package me.mondiversi.uvir

import android.content.res.Configuration
import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import kotlin.math.roundToInt
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.After
import java.util.UUID

/** Isolated menus: callbacks only, never real transports, settings or records. */
class UvirHomeSensorDropdownTest {
    @get:Rule val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val preferencePrefix = "sensor-menu-test-${UUID.randomUUID()}-"
    private val preferenceNames = mutableSetOf<String>()
    private val menuContext = object : ContextWrapper(context) {
        override fun getApplicationContext(): Context = this
        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
            preferenceNames.add(preferencePrefix + name)
            return context.getSharedPreferences(preferencePrefix + name, mode)
        }
    }
    @After fun cleanUp() { preferenceNames.forEach { context.deleteSharedPreferences(it) } }
    private val night = mutableStateOf(false)
    private val direction = mutableStateOf(LayoutDirection.Ltr)
    private val confirmed = mutableStateOf(true)
    private val fakeData = mutableStateOf(false)
    private val activity = mutableStateOf(false)
    private val wirelessStatus = mutableStateOf(WirelessSensorConnectionStatus.CONNECTED)
    private val usbStatus = mutableStateOf(UsbSensorConnectionStatus.CONNECTED)
    private val connectionMode = mutableStateOf(SensorConnectionMode.WIFI)
    private val otherStatuses = mutableStateOf(emptyMap<String, UvirStatusIndicator>())
    private val selectedId = mutableStateOf("A")
    private val selectedDisplayName = mutableStateOf("Portatile")
    private val wirelessEnabled = mutableStateOf(true)
    private val selectionEnabled = mutableStateOf(true)
    private val selectionExpanded = mutableStateOf(false)
    private val infoVisible = mutableStateOf(false)
    private val visibleHeaderSelectionStates = mutableListOf<Boolean>()
    private val fontScale = mutableStateOf(1f)
    private val compactDialog = mutableStateOf(false)
    private val profileModes = mutableStateOf(mapOf("a" to SensorConnectionMode.WIFI,
        "b" to SensorConnectionMode.BLUETOOTH))
    private var infoRequests = 0
    private val sensorRequests = mutableListOf<String>()
    private val modes = mutableListOf<SensorConnectionMode>()
    private val profiles = mutableStateOf(listOf(
        UvirSensorProfile(1, "A", "Sensor A", 0, 1_700_000_000_000L),
        UvirSensorProfile(2, "B", "Sensor B", 0, 1_700_000_100_000L)))

    private fun inkColor(pixels: androidx.compose.ui.graphics.PixelMap): Color {
        val background = pixels[0, 0]
        return (0 until pixels.height).flatMap { y -> (0 until pixels.width).map { x -> pixels[x, y] } }
            .maxBy { kotlin.math.abs(it.red - background.red) + kotlin.math.abs(it.green - background.green) + kotlin.math.abs(it.blue - background.blue) }
    }

    private fun menuDotColor(id: String): Color =
        inkColor(compose.onNodeWithTag("home_sensor_status_$id", true).captureToImage().toPixelMap())

    private fun showHeader() {
        compose.setContent {
            val view = LocalView.current
            DisposableEffect(view) {
                val previous = view.keepScreenOn
                view.keepScreenOn = true
                onDispose { view.keepScreenOn = previous }
            }
            val configuration = Configuration(LocalConfiguration.current).apply {
                if (compactDialog.value) screenHeightDp = 400
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                    if (night.value) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            }
            CompositionLocalProvider(LocalConfiguration provides configuration, LocalLayoutDirection provides direction.value,
                LocalDensity provides Density(LocalDensity.current.density, fontScale.value),
                LocalContext provides menuContext, LocalUvirDateFormat provides UvirDateFormat.INTERNATIONAL,
                LocalUvirTimeFormat provides UvirTimeFormat.H24) {
                MaterialTheme(colorScheme = if (night.value) darkColorScheme() else lightColorScheme()) {
                    val colors = MaterialTheme.colorScheme
                    if (infoVisible.value) {
                        UvirSensorInfoScreen(
                            connectionMode = connectionMode.value,
                            sensorInfo = UvirSensorRuntimeInfo(deviceId = selectedId.value),
                            backgroundColor = colors.background, primaryText = colors.onSurface,
                            secondaryText = colors.onSurfaceVariant, cardColor = colors.surface,
                            onDismissRequest = { infoVisible.value = false }
                        )
                    } else Box(Modifier.width(304.dp).background(colors.background)) {
                        SideEffect { visibleHeaderSelectionStates.add(selectionExpanded.value) }
                        UvirHomeHeader(sensorConnectionMode = connectionMode.value, useFakeSensorData = fakeData.value,
                            usbSensorStatus = if (connectionMode.value == SensorConnectionMode.USB)
                                usbStatus.value else UsbSensorConnectionStatus.DISCONNECTED,
                            usbAppConnectionConfirmed = connectionMode.value == SensorConnectionMode.USB && confirmed.value,
                            wirelessSensorStatus = wirelessStatus.value,
                            wirelessSensorMode = connectionMode.value, wirelessAppConnectionConfirmed = confirmed.value,
                            sensorActivityInProgress = activity.value, versionInfoDescription = "Trial info",
                            primaryText = if (night.value) Color.White else Color.Black,
                            secondaryText = colors.onSurfaceVariant,
                            onOpenVersionInfo = {}, onOpenSettings = {},
                            sensorSelectionExpanded = selectionExpanded.value,
                            onSensorSelectionExpandedChange = { selectionExpanded.value = it },
                            sensorDisplayName = if (selectedId.value.isBlank()) "" else selectedDisplayName.value,
                            sensorSelectionEnabled = selectionEnabled.value,
                            sensorProfiles = profiles.value,
                            sensorConnectionModes = profileModes.value,
                            sensorStatusIndicators = otherStatuses.value,
                            selectedSensorDeviceId = selectedId.value,
                            onSensorSelected = { sensorRequests.add(it); selectedId.value =
                                if (it == NO_SENSOR_SELECTED_REQUEST || it == ASSOCIATE_NEW_SENSOR_REQUEST) "" else it },
                            onOpenSensorInfo = { infoRequests++; infoVisible.value = true },
                            onSensorConnectionModeChanged = { modes.add(it); connectionMode.value = it },
                            wifiEnabled = wirelessEnabled.value, bluetoothEnabled = wirelessEnabled.value,
                            internetEnabled = wirelessEnabled.value)
                    }
                }
            }
        }
    }

    @Test fun nameOpensRadioDialogAndInfoDoesNotSelectOrReconnectSensor() {
        showHeader()
        compose.onNodeWithTag("home_sensor_glyph", true).assertIsDisplayed()
        compose.onNodeWithTag("home_sensor_name").performClick()
        assertEquals(0, infoRequests)
        compose.onNodeWithTag("home_sensor_option_none").assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.sensor_associate_action)).assertDoesNotExist()
        compose.onNodeWithContentDescription(context.getString(R.string.close)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.sensor_selection_title)).assertIsDisplayed()
        compose.onNodeWithTag("sensor_selector_arrow").assertDoesNotExist()
        compose.onNodeWithTag("home_sensor_logo").assertDoesNotExist()
        compose.onNodeWithTag("home_sensor_option_A").assertIsSelected()
        compose.onNodeWithTag("home_sensor_option_B").assertIsNotSelected()
        compose.onNodeWithTag("home_sensor_option_none").assertIsNotSelected()
        for (id in listOf("none", "A", "B")) {
            compose.onNodeWithTag("home_sensor_radio_$id", true).assertIsDisplayed()
        }
        compose.onNodeWithTag("home_sensor_info").assertDoesNotExist()
        compose.onNodeWithTag("sensor_profile_info_B").assertIsNotEnabled().performTouchInput { click() }
        assertEquals(0, sensorRequests.size)
        compose.onNodeWithTag("sensor_profile_info_A").assertIsEnabled().performTouchInput { click() }
        assertEquals(1, infoRequests)
        assertEquals(0, sensorRequests.size)
        compose.onNodeWithTag("home_sensor_selection_menu").assertDoesNotExist()
        compose.onNodeWithContentDescription(context.getString(R.string.navigate_back)).performClick()
        compose.onNodeWithTag("home_sensor_selection_menu").assertIsDisplayed()
        compose.onNodeWithTag("home_sensor_option_B").performClick()
        assertEquals(listOf("B"), sensorRequests)
        assertEquals(1, infoRequests)
        compose.onNodeWithTag("home_sensor_selection_menu").assertDoesNotExist()
        compose.runOnIdle { confirmed.value = false }
        compose.onNodeWithTag("home_sensor_name").assertIsEnabled().performTouchInput { click() }
        compose.onNodeWithTag("sensor_profile_info_B").assertIsNotEnabled().performTouchInput { click() }
        assertEquals(1, infoRequests)
        assertEquals(listOf("B"), sensorRequests)
        compose.onNodeWithTag("home_sensor_option_none").performClick()
        assertEquals(listOf("B", NO_SENSOR_SELECTED_REQUEST), sensorRequests)
        compose.onNodeWithTag("home_sensor_name").assertIsEnabled()
        compose.onNodeWithTag("home_sensor_selection_menu").assertDoesNotExist()
    }

    @Test fun selectingCurrentSensorClosesMenuWithoutAnotherRequest() {
        showHeader()
        compose.onNodeWithTag("home_sensor_name").performClick()
        compose.onNodeWithTag("home_sensor_option_A").performClick()
        assertEquals(0, sensorRequests.size)
        compose.onNodeWithTag("home_sensor_selection_menu").assertDoesNotExist()
    }

    @Test fun statusAndDotOpenSameSelectionDialogInEveryConnectionStateWithoutSideEffects() {
        showHeader()
        for (state in listOf("connected", "offline", "connecting", "activity", "none", "debug")) {
            compose.runOnIdle {
                confirmed.value = state == "connected"
                wirelessStatus.value = when (state) {
                    "connected", "connecting" -> WirelessSensorConnectionStatus.CONNECTED
                    else -> WirelessSensorConnectionStatus.DISCONNECTED
                }
                activity.value = state == "activity"
                selectedId.value = if (state == "none") "" else "A"
                fakeData.value = state == "debug"
            }
            compose.onNodeWithTag("home_sensor_status").performClick()
            compose.onNodeWithTag("home_sensor_selection_menu").assertIsDisplayed()
            compose.onNodeWithTag("home_sensor_source_menu").assertDoesNotExist()
            compose.onNodeWithContentDescription(context.getString(R.string.close)).performClick()
            compose.onNodeWithTag("home_sensor_glyph", true).performTouchInput { click() }
            compose.onNodeWithTag("home_sensor_selection_menu").assertIsDisplayed()
            compose.onNodeWithContentDescription(context.getString(R.string.close)).performClick()
        }
        compose.runOnIdle { selectionEnabled.value = false }
        compose.onNodeWithTag("home_sensor_status").assertIsNotEnabled().performTouchInput { click() }
        compose.onNodeWithTag("home_sensor_selection_menu").assertDoesNotExist()
        assertTrue(sensorRequests.isEmpty())
        assertTrue(modes.isEmpty())
        assertEquals(0, infoRequests)
    }

    @Test fun pressingSensorNameOrStatusDoesNotDrawBackgroundInEitherThemeOrDirection() {
        showHeader()
        compose.mainClock.autoAdvance = false
        for (dark in listOf(false, true)) for (layout in listOf(LayoutDirection.Ltr, LayoutDirection.Rtl)) {
            compose.runOnIdle { night.value = dark; direction.value = layout }
            compose.mainClock.advanceTimeBy(300)
            for (tag in listOf("home_sensor_name", "home_sensor_status")) {
                // Let the dialog close before comparing pressed pixels.
                compose.mainClock.advanceTimeBy(600)
                val control = compose.onNodeWithTag(tag)
                val before = control.captureToImage().toPixelMap()
                control.performTouchInput { down(center) }
                compose.mainClock.advanceTimeBy(300)
                val during = control.captureToImage().toPixelMap()
                var changed = 0
                for (y in 0 until before.height) for (x in 0 until before.width) {
                    if (before[x, y] != during[x, y]) changed++
                }
                assertEquals("$tag must remain visually unchanged while pressed", 0, changed)
                control.performTouchInput { up() }
                compose.mainClock.advanceTimeBy(300)
                compose.onNodeWithTag("home_sensor_option_A").performClick()
                compose.mainClock.advanceTimeBy(300)
            }
        }
        assertEquals(0, infoRequests)
        assertEquals(0, sensorRequests.size)
    }

    @Test fun nameKeepsCompactHeaderWithDisclosureChevronButWithoutLogoOrDropdownArrow() {
        showHeader()
        val density = context.resources.displayMetrics.density
        for (layout in listOf(LayoutDirection.Ltr, LayoutDirection.Rtl)) {
            compose.runOnIdle { direction.value = layout }
            val name = compose.onNodeWithText("Portatile", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            val selector = compose.onNodeWithTag("home_sensor_selector").fetchSemanticsNode().boundsInRoot
            assertEquals("Name has no leading indentation",
                if (layout == LayoutDirection.Ltr) selector.left else selector.right,
                if (layout == LayoutDirection.Ltr) name.left else name.right, 1f)
            assertEquals(36f * density, selector.height, 1f)
            compose.onNodeWithTag("home_sensor_logo").assertDoesNotExist()
            compose.onNodeWithTag("sensor_selector_arrow").assertDoesNotExist()
            val chevron = compose.onNodeWithTag("home_sensor_name_chevron", true).assertIsDisplayed()
                .fetchSemanticsNode().boundsInRoot
            assertEquals(name.center.y, chevron.center.y, 1f)
            assertTrue("Chevron immediately follows the name",
                if (layout == LayoutDirection.Ltr) chevron.left > name.right else chevron.right < name.left)
            compose.onNodeWithTag("home_sensor_name_chevron", true).performTouchInput { click() }
            compose.onNodeWithTag("home_sensor_selection_menu").assertIsDisplayed()
            compose.onNodeWithContentDescription(context.getString(R.string.close)).performClick()
        }
    }

    @Test fun menuInfoPrecedesDateAndKeepsIndependentNormalAndDisabledAppearance() {
        showHeader()
        for (dark in listOf(false, true)) for (layout in listOf(LayoutDirection.Ltr, LayoutDirection.Rtl)) {
            compose.runOnIdle { night.value = dark; direction.value = layout }
            compose.onNodeWithTag("home_sensor_info").assertDoesNotExist()
            compose.onNodeWithTag("home_sensor_name").performClick()
            val info = compose.onNodeWithTag("sensor_profile_info_A").assertIsDisplayed().assertIsEnabled()
            val infoBounds = info.fetchSemanticsNode().boundsInRoot
            val date = compose.onNodeWithTag("home_sensor_last_connection_A", true)
                .fetchSemanticsNode().boundsInRoot
            assertEquals(date.center.y, infoBounds.center.y, 1f)
            assertTrue("Info is before the connection date",
                if (layout == LayoutDirection.Ltr) infoBounds.right <= date.left else infoBounds.left >= date.right)
            val mark = compose.onNodeWithTag("home_sensor_radio_A", true).fetchSemanticsNode().boundsInRoot
            assertEquals(infoBounds.center.y, mark.center.y, 1f)
            assertTrue("The radio remains after the connection date",
                if (layout == LayoutDirection.Ltr) mark.left > date.right else mark.right < date.left)
            val enabledPixels = info.captureToImage().toPixelMap()
            compose.runOnIdle { confirmed.value = false }
            val disabledPixels = info.assertIsNotEnabled().captureToImage().toPixelMap()
            var different = 0
            for (y in 0 until enabledPixels.height) for (x in 0 until enabledPixels.width) {
                if (enabledPixels[x, y] != disabledPixels[x, y]) different++
            }
            assertTrue("Disconnected info must be visibly muted", different > 0)
            compose.runOnIdle { confirmed.value = true }
            for (id in listOf("none", "B")) {
                val other = compose.onNodeWithTag("home_sensor_radio_$id", true)
                    .fetchSemanticsNode().boundsInRoot
                assertEquals("Every selection radio uses the same trailing alignment", mark.center.x, other.center.x, 1f)
            }
            info.performClick()
            compose.onNodeWithTag("home_sensor_selection_menu").assertDoesNotExist()
            compose.onNodeWithContentDescription(context.getString(R.string.navigate_back)).performClick()
            compose.onNodeWithTag("home_sensor_selection_menu").assertIsDisplayed()
            compose.onNodeWithContentDescription(context.getString(R.string.close)).performClick()
        }
        assertEquals(4, infoRequests)
        assertEquals(0, sensorRequests.size)
    }

    @Test fun returningFromSensorInfoNeverComposesAClosedSelectionDialog() {
        showHeader()
        for (dark in listOf(false, true)) for (systemBack in listOf(false, true)) {
            compose.runOnIdle { night.value = dark; confirmed.value = true }
            compose.onNodeWithTag("home_sensor_name").performClick()
            compose.onNodeWithTag("sensor_profile_info_A").performClick()
            compose.onNodeWithTag("home_sensor_selection_menu").assertDoesNotExist()
            compose.onNodeWithTag("home_sensor_name").assertDoesNotExist()
            compose.runOnIdle {
                assertTrue("Info must preserve the open selection", selectionExpanded.value)
                visibleHeaderSelectionStates.clear()
                // A disconnect while reading Info must not close the selection on return.
                confirmed.value = false
            }
            if (systemBack) androidx.test.espresso.Espresso.pressBack()
            else compose.onNodeWithContentDescription(context.getString(R.string.navigate_back)).performClick()
            compose.onNodeWithTag("home_sensor_selection_menu").assertIsDisplayed()
            compose.onNodeWithTag("home_sensor_option_A").assertIsSelected()
            compose.onNodeWithTag("sensor_profile_info_A").assertIsNotEnabled()
            compose.runOnIdle {
                assertTrue("There must be no intermediate home frame with the dialog closed",
                    visibleHeaderSelectionStates.isNotEmpty() && visibleHeaderSelectionStates.all { it })
            }
            compose.onNodeWithContentDescription(context.getString(R.string.close)).performClick()
            compose.onNodeWithTag("home_sensor_selection_menu").assertDoesNotExist()
            compose.runOnIdle { assertTrue(!selectionExpanded.value) }
        }
        assertTrue(sensorRequests.isEmpty())
        assertTrue(modes.isEmpty())
    }

    @Test fun sensorRowsAndFourTransportTilesKeepTheirSelectedFrameInBothThemes() {
        showHeader()
        for (dark in listOf(false, true)) {
            compose.runOnIdle { night.value = dark }
            compose.onNodeWithTag("home_sensor_name").performClick()
            compose.onNodeWithTag("home_sensor_option_A").assertIsSelected()
            compose.onNodeWithTag("home_source_option_WIFI").assertIsSelected()
            compose.onNodeWithTag("home_source_option_USB").assertIsNotSelected()
            val grid = compose.onNodeWithTag("home_sensor_connection_grid").fetchSemanticsNode().boundsInRoot
            val widths = SensorConnectionMode.entries.map {
                compose.onNodeWithTag("home_source_option_${it.name}").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            }
            for (tile in widths) {
                assertEquals(widths.first().width, tile.width, 1f)
                assertEquals(widths.first().center.y, tile.center.y, 1f)
                assertTrue(tile.top >= grid.top && tile.bottom <= grid.bottom)
            }
            compose.onNodeWithContentDescription(context.getString(R.string.close)).performClick()
        }
    }

    @Test fun connectionSelectorHasOneSharedSurfaceAndMeasurementStyleSelectedFrame() {
        showHeader()
        val density = context.resources.displayMetrics.density
        for (dark in listOf(false, true)) for (layout in listOf(LayoutDirection.Ltr, LayoutDirection.Rtl)) {
            compose.runOnIdle { night.value = dark; direction.value = layout; connectionMode.value = SensorConnectionMode.WIFI }
            compose.onNodeWithTag("home_sensor_name").performClick()
            val primary = if (dark) Color.White else Color.Black
            val surface = if (dark) darkColorScheme().surface else lightColorScheme().surface
            val barColor = primary.copy(alpha = .04f).compositeOver(surface)
            val bar = compose.onNodeWithTag("home_sensor_connection_grid")
            val pixels = bar.captureToImage().toPixelMap()
            fun assertColor(message: String, expected: Color, actual: Color) {
                assertEquals(message, expected.red, actual.red, .01f)
                assertEquals(message, expected.green, actual.green, .01f)
                assertEquals(message, expected.blue, actual.blue, .01f)
            }
            assertColor("One continuous background above every segment", barColor,
                pixels[pixels.width / 2, (density * 1.5f).toInt()])
            val bounds = bar.fetchSemanticsNode().boundsInRoot
            val tiles = SensorConnectionMode.entries.map {
                compose.onNodeWithTag("home_source_option_${it.name}").fetchSemanticsNode().boundsInRoot
            }.sortedBy { it.left }
            assertEquals(3f * density, tiles.first().left - bounds.left, 1f)
            assertEquals(3f * density, bounds.right - tiles.last().right, 1f)
            tiles.zipWithNext().forEach { (left, right) ->
                assertEquals("Same inset and gap as the measurement selector", 3f * density, right.left - left.right, 1f)
            }
            val selected = compose.onNodeWithTag("home_source_option_WIFI").captureToImage().toPixelMap()
            val expectedFill = uvirSegmentedSelectedContainerColor(primary).compositeOver(barColor)
            assertColor("Selected frame uses the shared measurement palette", expectedFill,
                selected[(density * 5f).toInt(), selected.height / 2])
            compose.onNodeWithContentDescription(context.getString(R.string.close)).performClick()
        }
        assertTrue(modes.isEmpty())
    }

    @Test fun pressingEitherRadioDialogClipsFeedbackToRoundedCorners() {
        showHeader()
        compose.mainClock.autoAdvance = false
        for (dark in listOf(false, true)) {
            compose.runOnIdle { night.value = dark }
            compose.mainClock.advanceTimeBy(600)
            for ((trigger, option) in listOf("home_sensor_name" to "home_sensor_option_B",
                "home_sensor_name" to "home_source_option_USB")) {
                compose.onNodeWithTag(trigger).performClick()
                compose.mainClock.advanceTimeBy(600)
                val row = compose.onNodeWithTag(option)
                val before = row.captureToImage().toPixelMap()
                row.performTouchInput { down(center) }
                compose.mainClock.advanceTimeBy(600)
                android.os.SystemClock.sleep(350L)
                val pressed = row.captureToImage().toPixelMap()
                assertEquals("Press never paints square outer corners", before[0, 0], pressed[0, 0])
                assertEquals(before[before.width - 1, 0], pressed[pressed.width - 1, 0])
                assertTrue("Feedback is still visible inside the row",
                    before[before.width / 2, before.height / 2] != pressed[pressed.width / 2, pressed.height / 2])
                row.performTouchInput { cancel() }
                compose.mainClock.advanceTimeBy(600)
                compose.onNodeWithContentDescription(context.getString(R.string.close)).performClick()
                compose.mainClock.advanceTimeBy(600)
            }
        }
        assertTrue(sensorRequests.isEmpty())
        assertTrue(modes.isEmpty())
    }

    @Test fun selectionPopupOmitsAssociationActionInBothThemesAndDirections() {
        showHeader()
        for (dark in listOf(false, true)) for (layout in listOf(LayoutDirection.Ltr, LayoutDirection.Rtl)) {
            compose.runOnIdle { night.value = dark; direction.value = layout }
            compose.onNodeWithTag("home_sensor_name").performClick()
            compose.onNodeWithTag("home_sensor_associate").assertDoesNotExist()
            compose.onNodeWithText(context.getString(R.string.sensor_associate_action)).assertDoesNotExist()
            compose.onNodeWithTag("home_sensor_connection_grid").assertIsDisplayed()
            compose.onNodeWithContentDescription(context.getString(R.string.close)).performClick()
            compose.onNodeWithTag("home_sensor_selection_menu").assertDoesNotExist()
            assertEquals(0, infoRequests)
        }
        assertTrue(sensorRequests.isEmpty())
    }

    @Test fun transportTilesRespectDisabledMethodsAndStayOpenAfterSelection() {
        wirelessEnabled.value = false
        showHeader()
        compose.onNodeWithTag("home_sensor_name").performClick()
        compose.onNodeWithTag("home_source_option_WIFI").assertIsSelected()
        for (mode in listOf(SensorConnectionMode.WIFI, SensorConnectionMode.BLUETOOTH, SensorConnectionMode.INTERNET)) {
            compose.onNodeWithTag("home_source_option_${mode.name}").assertIsNotEnabled().performTouchInput { click() }
        }
        assertTrue(modes.isEmpty())
        compose.onNodeWithTag("home_source_option_USB").assertIsEnabled().performClick()
        assertEquals(listOf(SensorConnectionMode.USB), modes)
        compose.onNodeWithTag("home_sensor_selection_menu").assertIsDisplayed()
        compose.onNodeWithTag("home_source_option_USB").assertIsSelected().performClick()
        assertEquals(1, modes.size)
        compose.runOnIdle { selectionEnabled.value = false }
        compose.onNodeWithTag("home_sensor_selection_menu").assertIsDisplayed()
        compose.onNodeWithTag("home_source_option_USB").assertIsNotEnabled()
        compose.onNodeWithContentDescription(context.getString(R.string.close)).performClick()
    }

    @Test fun selectionDialogCanCloseWithoutChangingSensor() {
        showHeader()
        compose.onNodeWithTag("home_sensor_name").performClick()
        compose.onNodeWithText(context.getString(R.string.sensor_selection_title)).assertIsDisplayed()
        compose.onNodeWithContentDescription(context.getString(R.string.close)).performClick()
        compose.onNodeWithTag("home_sensor_selection_menu").assertDoesNotExist()
        assertTrue(sensorRequests.isEmpty())
        assertEquals(0, infoRequests)
    }

    @Test fun sensorRadioAndTransportTileSelectOnceAndNoSensorDisablesTheGrid() {
        showHeader()
        compose.onNodeWithTag("home_sensor_name").performClick()
        compose.onNodeWithText(context.getString(R.string.sensor_no_selection)).assertIsDisplayed()
        compose.onNodeWithTag("home_sensor_radio_B", true).performTouchInput { click() }
        assertEquals(listOf("B"), sensorRequests)
        compose.onNodeWithTag("home_sensor_name").performClick()
        compose.onNodeWithTag("home_sensor_radio_none", true).performTouchInput { click() }
        assertEquals(listOf("B", NO_SENSOR_SELECTED_REQUEST), sensorRequests)
        // The name is exposed as one accessible button, not as a separate text node.
        compose.onNodeWithContentDescription(context.getString(R.string.sensor_selection_title) +
            ": " + context.getString(R.string.sensor_no_selection)).assertIsDisplayed()
        compose.onNodeWithTag("home_sensor_name").performClick()
        SensorConnectionMode.entries.forEach { compose.onNodeWithTag("home_source_option_${it.name}").assertIsNotEnabled() }
        compose.onNodeWithTag("home_sensor_option_A").performClick()
        compose.onNodeWithTag("home_sensor_name").performClick()
        compose.onNodeWithTag("home_source_option_USB").performTouchInput { click() }
        assertEquals(listOf(SensorConnectionMode.USB), modes)
        compose.onNodeWithContentDescription(context.getString(R.string.close)).performClick()
        assertEquals(0, infoRequests)
    }

    @Test fun headerActionPressedCircleUsesFullFortyDpTargetInBothThemes() {
        compose.setContent {
            val view = LocalView.current
            DisposableEffect(view) {
                val previous = view.keepScreenOn
                view.keepScreenOn = true
                onDispose { view.keepScreenOn = previous }
            }
            MaterialTheme(colorScheme = if (night.value) darkColorScheme() else lightColorScheme()) {
                Box(Modifier.background(MaterialTheme.colorScheme.background)) {
                    UvirHomeHeaderActionButton("Trial header action", {}, Modifier.testTag("trial_action")) {}
                }
            }
        }
        compose.mainClock.autoAdvance = false
        for (dark in listOf(false, true)) {
            compose.runOnIdle { night.value = dark }
            compose.mainClock.advanceTimeBy(600)
            val action = compose.onNodeWithTag("trial_action")
            val before = action.captureToImage().toPixelMap()
            assertEquals(40f * context.resources.displayMetrics.density, before.width.toFloat(), 1f)
            action.performTouchInput { down(center) }
            compose.mainClock.advanceTimeBy(600)
            // Android's native ripple uses the render thread, not the Compose test clock.
            android.os.SystemClock.sleep(350L)
            val pressed = action.captureToImage().toPixelMap()
            val paddingPixel = (before.width * 0.95f).toInt().coerceAtMost(before.width - 1)
            assertTrue("Ripple includes the outer padding, not just the old 32dp circle",
                before[paddingPixel, before.height / 2] != pressed[paddingPixel, pressed.height / 2])
            assertEquals("Pressed background stays circular", before[0, 0], pressed[0, 0])
            action.performTouchInput { cancel() }
            compose.mainClock.advanceTimeBy(600)
            android.os.SystemClock.sleep(350L)
        }
    }

    @Test fun sensorAndTransportAreIndependentGroupsInOnePopupInBothThemesAndDirections() {
        showHeader()
        for (dark in listOf(false, true)) for (layout in listOf(LayoutDirection.Ltr, LayoutDirection.Rtl))
            for (scale in listOf(1f, 1.5f)) {
                compose.runOnIdle { night.value = dark; direction.value = layout; fontScale.value = scale; connectionMode.value = SensorConnectionMode.WIFI }
                compose.onNodeWithTag("home_sensor_name").performClick()
                compose.onNodeWithTag("home_sensor_associate").assertDoesNotExist()
                SensorConnectionMode.entries.forEach { compose.onNodeWithTag("home_source_option_${it.name}").assertIsDisplayed().assertIsEnabled() }
                compose.onNodeWithTag("home_source_option_BLUETOOTH").performClick()
                compose.onNodeWithTag("home_source_option_BLUETOOTH").assertIsSelected()
                compose.onNodeWithTag("home_sensor_option_A").assertIsSelected()
                compose.onNodeWithTag("home_sensor_option_B").assertIsNotSelected()
                compose.onNodeWithTag("home_sensor_selection_menu").assertIsDisplayed()
                compose.onNodeWithContentDescription(context.getString(R.string.close)).performClick()
            }
        assertTrue(sensorRequests.isEmpty())
        assertEquals(List(8) { SensorConnectionMode.BLUETOOTH }, modes)
    }

    @Test fun unifiedMenuUsesTheSharedDayAndNightSurfaceInBothLayoutDirections() {
        showHeader()
        for (dark in listOf(false, true)) for (layout in listOf(LayoutDirection.Ltr, LayoutDirection.Rtl)) {
            compose.runOnIdle { night.value = dark; direction.value = layout }
            compose.onNodeWithTag("home_sensor_name").performClick()
            val expected = if (dark) darkColorScheme().surface else lightColorScheme().surface
            val pixels = compose.onNodeWithTag("home_sensor_selection_menu").captureToImage().toPixelMap()
            val color = pixels[pixels.width / 2, 4]
            assertEquals(expected.red, color.red, .02f)
            assertEquals(expected.green, color.green, .02f)
            assertEquals(expected.blue, color.blue, .02f)
            compose.onNodeWithTag("home_sensor_connection_grid").assertIsDisplayed()
            compose.onNodeWithContentDescription(context.getString(R.string.close)).performClick()
        }
    }

    @Test fun sensorRowsRemainCompactWithConnectionIconsInEitherTheme() {
        confirmed.value = false
        showHeader()
        for (dark in listOf(false, true)) for (layout in listOf(LayoutDirection.Ltr, LayoutDirection.Rtl))
            for (scale in listOf(1f, 1.5f)) {
                compose.runOnIdle {
                    night.value = dark; direction.value = layout; fontScale.value = scale
                }
                compose.onNodeWithTag("home_sensor_name").performClick()
                compose.mainClock.advanceTimeBy(600)
                for (id in listOf("A", "B")) {
                    compose.onNodeWithText("Sensor $id", useUnmergedTree = true).assertIsDisplayed()
                    compose.onNodeWithTag("home_sensor_status_$id", true).assertIsDisplayed()
                    SensorConnectionMode.entries.forEach { mode ->
                        compose.onNodeWithTag("home_sensor_connection_${id}_${mode.name}", true).assertDoesNotExist()
                    }
                }
                val firstRow = compose.onNodeWithTag("home_sensor_row_A", true).fetchSemanticsNode().boundsInRoot
                val nextRow = compose.onNodeWithTag("home_sensor_row_B", true).fetchSemanticsNode().boundsInRoot
                assertEquals("Same gap as the source choices", SensorRadioOptionGap.value * context.resources.displayMetrics.density,
                    nextRow.top - firstRow.bottom, 1f)
                if (scale == 1f) {
                    assertEquals("Rows share the source's minimum height", SensorRadioOptionMinHeight.value * context.resources.displayMetrics.density,
                        firstRow.height, 1.5f)
                }
                compose.onNodeWithTag("sensor_profile_info_A").assertIsNotEnabled()
                compose.onNodeWithTag("home_sensor_option_A").performClick()
            }
        assertEquals(0, infoRequests)
        assertEquals(0, sensorRequests.size)
        assertEquals(0, modes.size)
    }

    @Test fun noSensorRowHasSameHeightAndNoStatusDot() {
        showHeader()
        for (dark in listOf(false, true)) for (layout in listOf(LayoutDirection.Ltr, LayoutDirection.Rtl)) {
            compose.runOnIdle { night.value = dark; direction.value = layout }
            compose.onNodeWithTag("home_sensor_name").performClick()
            val none = compose.onNodeWithTag("home_sensor_option_none").fetchSemanticsNode().boundsInRoot
            val sensor = compose.onNodeWithTag("home_sensor_row_A", true).fetchSemanticsNode().boundsInRoot
            assertEquals(sensor.height, none.height, 1f)
            assertEquals(SensorRadioOptionGap.value * context.resources.displayMetrics.density, sensor.top - none.bottom, 1f)
            compose.onNodeWithTag("home_sensor_status_none", true).assertDoesNotExist()
            compose.onNodeWithTag("home_sensor_option_A").performClick()
        }
    }

    @Test fun connectionStartUsesTwoLinesAndRemainsAvailableOfflineInsteadOfLastTraffic() {
        val wifiStart = 1_700_000_000_000L
        val usbStart = wifiStart + 60_000L
        val otherStart = wifiStart + 120_000L
        UvirSensorConnectionHistory.recordIfStarted(menuContext, "A", SensorConnectionMode.WIFI, false, true, wifiStart)
        UvirSensorConnectionHistory.recordIfStarted(menuContext, "A", SensorConnectionMode.USB, false, true, usbStart)
        UvirSensorConnectionHistory.recordIfStarted(menuContext, "B", SensorConnectionMode.BLUETOOTH, false, true, otherStart)
        fun stamp(time: Long) = formatUvirDateTime(time, UvirDateFormat.INTERNATIONAL,
            separator = "\n", timeFormat = UvirTimeFormat.H24)
        showHeader()
        for (dark in listOf(false, true)) {
            compose.runOnIdle { night.value = dark; confirmed.value = true }
            compose.onNodeWithTag("home_sensor_name").performClick()
            compose.onNodeWithTag("home_sensor_last_connection_A", true).assertTextEquals(stamp(wifiStart))
            compose.onNodeWithTag("home_sensor_last_connection_B", true).assertTextEquals(stamp(otherStart))
            val layouts = mutableListOf<TextLayoutResult>()
            compose.onNodeWithTag("home_sensor_last_connection_A", true)
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertEquals(2, layouts.single().lineCount)
            val sensorHeight = compose.onNodeWithTag("home_sensor_option_A").fetchSemanticsNode().boundsInRoot.height
            compose.runOnIdle { confirmed.value = false }
            compose.onNodeWithTag("home_sensor_last_connection_A", true).assertTextEquals(stamp(usbStart))
            assertEquals(SensorRadioOptionMinHeight.value * context.resources.displayMetrics.density, sensorHeight, 1.5f)
            assertEquals(SensorConnectionMode.USB, UvirSensorConnectionHistory.lastConnectedMode(menuContext, "A"))
            compose.onNodeWithTag("home_sensor_connection_grid").assertIsDisplayed()
            compose.onNodeWithContentDescription(context.getString(R.string.close)).performClick()
        }
        assertTrue(sensorRequests.isEmpty())
        assertTrue(modes.isEmpty())
    }

    @Test fun connectionIconsAppearInEachSensorRowAndTransportChoicesAreBelowTheList() {
        showHeader()
        for (dark in listOf(false, true)) for (layout in listOf(LayoutDirection.Ltr, LayoutDirection.Rtl))
            for (mode in SensorConnectionMode.entries) {
                compose.runOnIdle { night.value = dark; direction.value = layout; connectionMode.value = mode; profileModes.value = mapOf("a" to mode, "b" to mode) }
                compose.onNodeWithTag("home_sensor_name").performClick()
                for (id in listOf("A", "B")) compose.onNodeWithTag("home_sensor_status_$id", true).assertIsDisplayed()
                val lastRow = compose.onNodeWithTag("home_sensor_row_B", true).fetchSemanticsNode().boundsInRoot
                val grid = compose.onNodeWithTag("home_sensor_connection_grid").fetchSemanticsNode().boundsInRoot
                assertTrue(grid.top > lastRow.bottom)
                compose.onNodeWithTag("home_sensor_associate").assertDoesNotExist()
                compose.onNodeWithTag("home_source_option_${mode.name}").assertIsSelected().performClick()
                compose.onNodeWithTag("home_sensor_selection_menu").assertIsDisplayed()
                compose.onNodeWithContentDescription(context.getString(R.string.close)).performClick()
            }
        assertTrue(sensorRequests.isEmpty())
        assertTrue(modes.isEmpty())
        assertEquals(0, infoRequests)
    }

    @Test fun offlineSelectedIconsFollowEachChoiceInsteadOfTheLastSuccessfulWifiConnection() {
        UvirSensorConnectionHistory.recordIfStarted(menuContext, "A", SensorConnectionMode.WIFI, false, true, 100L)
        UvirSensorConnectionHistory.recordIfStarted(menuContext, "B", SensorConnectionMode.INTERNET, false, true, 200L)
        confirmed.value = false
        usbStatus.value = UsbSensorConnectionStatus.DISCONNECTED
        wirelessStatus.value = WirelessSensorConnectionStatus.DISCONNECTED
        showHeader()
        for (dark in listOf(false, true)) for (layout in listOf(LayoutDirection.Ltr, LayoutDirection.Rtl)) {
            compose.runOnIdle { night.value = dark; direction.value = layout; connectionMode.value = SensorConnectionMode.WIFI }
            compose.onNodeWithTag("home_sensor_name").performClick()
            for (mode in SensorConnectionMode.entries) {
                compose.onNodeWithTag("home_source_option_${mode.name}").performClick().assertIsSelected()
                val label = context.getString(uvirSensorConnectionLabelRes(mode))
                for (tag in listOf("home_sensor_status", "home_sensor_option_A")) {
                    assertTrue("$tag follows $mode even offline",
                        compose.onNodeWithTag(tag).fetchSemanticsNode().config[SemanticsProperties.ContentDescription]
                            .any { label in it })
                }
                val expected = Color(UvirStatusDot.RED.colorArgb)
                val pixels = compose.onNodeWithTag("home_sensor_status_A", true).captureToImage().toPixelMap()
                val distance = (0 until pixels.height).flatMap { y -> (0 until pixels.width).map { x -> pixels[x, y] } }
                    .minOf { kotlin.math.abs(it.red - expected.red) + kotlin.math.abs(it.green - expected.green) + kotlin.math.abs(it.blue - expected.blue) }
                assertTrue("$mode keeps the offline red tint: night=$dark, layout=$layout, distance=$distance", distance < .075f)
                assertTrue(compose.onNodeWithTag("home_sensor_option_B").fetchSemanticsNode()
                    .config[SemanticsProperties.ContentDescription].any { context.getString(R.string.sensor_connection_internet) in it })
            }
            compose.onNodeWithContentDescription(context.getString(R.string.close)).performClick()
            compose.onNodeWithTag("home_sensor_name").performClick()
            compose.onNodeWithTag("home_source_option_INTERNET").assertIsSelected()
            compose.onNodeWithContentDescription(context.getString(R.string.close)).performClick()
        }
        assertEquals(16, modes.size)
        assertTrue(sensorRequests.isEmpty())
    }

    @Test fun connectionFooterHasOnlyTheNormalBottomInsetInBothThemesAndDirections() {
        showHeader()
        for (dark in listOf(false, true)) for (layout in listOf(LayoutDirection.Ltr, LayoutDirection.Rtl))
            for (scale in listOf(1f, 1.5f)) {
                compose.runOnIdle { night.value = dark; direction.value = layout; fontScale.value = scale }
                compose.onNodeWithTag("home_sensor_name").performClick()
                val dialog = compose.onNodeWithTag("home_sensor_dialog").fetchSemanticsNode().boundsInRoot
                val footer = compose.onNodeWithTag("home_sensor_connection_footer").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
                assertEquals("No unused confirmation gap below the connections",
                    24f * context.resources.displayMetrics.density, dialog.bottom - footer.bottom, 1f)
                compose.onNodeWithContentDescription(context.getString(R.string.close)).performClick()
            }
        assertTrue(sensorRequests.isEmpty())
        assertTrue(modes.isEmpty())
    }

    @Test fun onlyLongSensorListScrollsWhileConnectionFooterAndTitleStayFixed() {
        profiles.value = profiles.value + (1..30).map {
            UvirSensorProfile(100L + it, "extra_$it", "ZZ sensor ${it.toString().padStart(2, '0')}", 0, 0)
        }
        showHeader()
        for (dark in listOf(false, true)) for (layout in listOf(LayoutDirection.Ltr, LayoutDirection.Rtl))
            for (short in listOf(false, true))
            for (scale in listOf(1f, 1.5f)) {
                compose.runOnIdle { night.value = dark; direction.value = layout; fontScale.value = scale; compactDialog.value = short }
                compose.onNodeWithTag("home_sensor_name").performClick()
                val scroll = compose.onNodeWithTag("uvir_dialog_scroll")
                assertTrue(scroll.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].maxValue() > 0f)
                val grid = compose.onNodeWithTag("home_sensor_connection_grid").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
                val footer = compose.onNodeWithTag("home_sensor_connection_footer").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
                val dialog = compose.onNodeWithTag("home_sensor_dialog").fetchSemanticsNode().boundsInRoot
                assertEquals("Normal bottom inset also with an overflowing list",
                    24f * context.resources.displayMetrics.density, dialog.bottom - footer.bottom, 1f)
                val title = compose.onNodeWithText(context.getString(R.string.sensor_selection_title)).fetchSemanticsNode().boundsInRoot
                val viewport = scroll.fetchSemanticsNode().boundsInRoot
                assertTrue("Connections are outside the scroll viewport", footer.top >= viewport.bottom)
                compose.onNodeWithTag("home_sensor_associate").assertDoesNotExist()
                compose.onNodeWithText(context.getString(R.string.sensor_associate_action)).assertDoesNotExist()
                scroll.performTouchInput { swipeUp() }
                compose.onNodeWithTag("home_sensor_option_extra_30").performScrollTo().assertIsDisplayed()
                assertTrue("The sensor list actually moved", scroll.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value() > 0f)
                assertEquals(grid, compose.onNodeWithTag("home_sensor_connection_grid").fetchSemanticsNode().boundsInRoot)
                assertEquals(footer, compose.onNodeWithTag("home_sensor_connection_footer").fetchSemanticsNode().boundsInRoot)
                assertEquals(title, compose.onNodeWithText(context.getString(R.string.sensor_selection_title)).fetchSemanticsNode().boundsInRoot)
                SensorConnectionMode.entries.forEach { compose.onNodeWithTag("home_source_option_${it.name}").assertIsDisplayed() }
                compose.onNodeWithContentDescription(context.getString(R.string.close)).performClick()
            }
        assertTrue(sensorRequests.isEmpty())
        assertTrue(modes.isEmpty())
    }

    @Test fun eachRowUsesItsOwnLiveStatusBeforeNameAndOfflineSensorsRemainSelectable() {
        showHeader()
        for (dark in listOf(false, true)) for (layout in listOf(LayoutDirection.Ltr, LayoutDirection.Rtl)) {
            compose.runOnIdle { night.value = dark; direction.value = layout }
            compose.onNodeWithTag("home_sensor_name").performClick()
            compose.mainClock.advanceTimeBy(600)
            for ((id, expected) in listOf("A" to UvirStatusDot.GREEN, "B" to UvirStatusDot.RED)) {
                val dot = compose.onNodeWithTag("home_sensor_status_$id", true).assertIsDisplayed()
                val bounds = dot.fetchSemanticsNode().boundsInRoot
                val name = compose.onNodeWithText("Sensor $id", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                assertEquals(name.center.y, bounds.center.y, 1f)
                assertTrue("Status precedes the sensor name",
                    if (layout == LayoutDirection.Ltr) bounds.right < name.left else bounds.left > name.right)
                val actual = menuDotColor(id)
                val color = Color(expected.colorArgb)
                assertEquals(color.red, actual.red, 0.02f)
                assertEquals(color.green, actual.green, 0.02f)
                assertEquals(color.blue, actual.blue, 0.02f)
                compose.onNodeWithTag("home_sensor_option_$id").assertIsEnabled()
            }
            compose.onNodeWithTag("sensor_profile_info_B").assertIsNotEnabled()
            compose.onNodeWithTag("home_sensor_option_A").performClick()
        }
        compose.onNodeWithTag("home_sensor_name").performClick()
        compose.onNodeWithTag("home_sensor_option_B").assertIsEnabled().performClick()
        assertEquals(listOf("B"), sensorRequests)
    }

    @Test fun selectedStatusAndPulseMatchHomeAndOtherStatesAreIndependent() {
        showHeader()
        compose.runOnIdle {
            otherStatuses.value = mapOf("b" to UvirStatusIndicator(UvirStatusDot.GREEN, false))
        }
        compose.onNodeWithTag("home_sensor_name").performClick()
        // Finish the popup's entrance before freezing time to compare pulse phases.
        compose.onNodeWithTag("home_sensor_selection_menu").captureToImage()
        compose.mainClock.autoAdvance = false
        for (state in listOf("handshake", "offline", "activity", "debug")) {
            compose.runOnIdle {
                confirmed.value = false
                wirelessStatus.value = if (state == "handshake") WirelessSensorConnectionStatus.CONNECTED
                    else WirelessSensorConnectionStatus.DISCONNECTED
                activity.value = state == "activity"
                fakeData.value = state == "debug"
            }
            compose.mainClock.advanceTimeBy(600)
            val homeDot = compose.onNodeWithTag("home_sensor_glyph", true).captureToImage().toPixelMap()
            val rowColor = menuDotColor("A")
            val homeColor = inkColor(homeDot)
            assertEquals("Same colour and animation: $state", homeColor.red, rowColor.red, 0.02f)
            assertEquals(homeColor.green, rowColor.green, 0.02f)
            assertEquals(homeColor.blue, rowColor.blue, 0.02f)
            val otherColor = menuDotColor("B")
            assertEquals(Color(UvirStatusDot.GREEN.colorArgb).green, otherColor.green, 0.02f)
        }
        assertEquals(0, sensorRequests.size)
        assertEquals(0, infoRequests)
        assertEquals(0, modes.size)
    }

    @Test fun longHomeNameScrollsWithoutMovingChevronOrSettingsAndStillOpensSelection() {
        val longName = "Sensore portatile con un nome molto lungo per il controllo dello scorrimento"
        selectedDisplayName.value = longName
        showHeader()
        compose.mainClock.autoAdvance = false
        val viewport = compose.onNodeWithTag("home_sensor_name_text", true).fetchSemanticsNode().boundsInRoot
        val chevron = compose.onNodeWithTag("home_sensor_name_chevron", true).fetchSemanticsNode().boundsInRoot
        val settings = compose.onNodeWithContentDescription(context.getString(R.string.acquisition_parameters))
            .fetchSemanticsNode().boundsInRoot
        val text = compose.onNodeWithText(longName, useUnmergedTree = true)
        assertTrue(text.fetchSemanticsNode().config[SemanticsProperties.HorizontalScrollAxisRange].maxValue() > 0f)
        val layouts = mutableListOf<TextLayoutResult>()
        text.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertEquals(1, layouts.single().lineCount)
        text.performSemanticsAction(SemanticsActions.ScrollBy) { it(50f, 0f) }
        compose.mainClock.advanceTimeBy(500)
        assertEquals(viewport, compose.onNodeWithTag("home_sensor_name_text", true).fetchSemanticsNode().boundsInRoot)
        assertEquals(chevron, compose.onNodeWithTag("home_sensor_name_chevron", true).fetchSemanticsNode().boundsInRoot)
        assertEquals(settings, compose.onNodeWithContentDescription(context.getString(R.string.acquisition_parameters))
            .fetchSemanticsNode().boundsInRoot)
        text.performTouchInput { click() }
        compose.mainClock.advanceTimeBy(100)
        compose.onNodeWithTag("home_sensor_selection_menu").assertIsDisplayed()
        compose.onNodeWithContentDescription(context.getString(R.string.close)).performClick()
        compose.mainClock.advanceTimeBy(100)
        compose.runOnIdle { selectedDisplayName.value = "Uvir" }
        compose.mainClock.advanceTimeBy(100)
        assertEquals(0f, compose.onNodeWithText("Uvir", useUnmergedTree = true)
            .fetchSemanticsNode().config[SemanticsProperties.HorizontalScrollAxisRange].maxValue(), 0f)
        assertTrue(sensorRequests.isEmpty())
        assertTrue(modes.isEmpty())
    }

    @Test fun longListNameIsSingleLineCappedAtFiftyFivePercentWithFixedDetails() {
        val longName = "Sensore con un nome lungo per verificare che data informazioni e selezione rimangano visibili"
        profiles.value = profiles.value.map { if (it.hardwareUid == "A") it.copy(displayName = longName) else it }
        showHeader()
        for (dark in listOf(false, true)) for (layout in listOf(LayoutDirection.Ltr, LayoutDirection.Rtl))
            for (scale in listOf(1f, 1.5f)) {
                compose.runOnIdle { night.value = dark; direction.value = layout; fontScale.value = scale }
                compose.onNodeWithTag("home_sensor_name").performClick()
                val name = compose.onNodeWithTag("home_sensor_label_A", true).assertIsDisplayed()
                val nameBounds = name.fetchSemanticsNode().boundsInRoot
                val row = compose.onNodeWithTag("home_sensor_option_A").fetchSemanticsNode().boundsInRoot
                val contentWidth = row.width - 2f * SensorRadioOptionHorizontalPadding.value * context.resources.displayMetrics.density
                assertTrue("Names use at most 55% of the usable row", nameBounds.width <= contentWidth * .55f + 1f)
                val text = compose.onNodeWithText(longName, useUnmergedTree = true)
                assertTrue(text.fetchSemanticsNode().config[SemanticsProperties.HorizontalScrollAxisRange].maxValue() > 0f)
                val layouts = mutableListOf<TextLayoutResult>()
                text.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
                assertEquals(1, layouts.single().lineCount)
                val info = compose.onNodeWithTag("sensor_profile_info_A").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
                val date = compose.onNodeWithTag("home_sensor_last_connection_A", true).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
                val radio = compose.onNodeWithTag("home_sensor_radio_A", true).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
                assertTrue("Name never invades info", if (layout == LayoutDirection.Ltr) nameBounds.right <= info.left else nameBounds.left >= info.right)
                assertTrue("Info precedes the date", if (layout == LayoutDirection.Ltr) info.right <= date.left else info.left >= date.right)
                assertTrue("Radio remains after the date", if (layout == LayoutDirection.Ltr) date.right < radio.left else date.left > radio.right)
                assertEquals(date.center.y, info.center.y, 1f)
                compose.onNodeWithContentDescription(context.getString(R.string.close)).performClick()
            }
        assertTrue(sensorRequests.isEmpty())
        assertTrue(modes.isEmpty())
    }
}
