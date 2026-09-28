package me.mondiversi.uvir

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Isolated header trial: callbacks only count taps; no database or sensor commands. */
class UvirHomeHeaderTrialTest {
    @get:Rule val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val dark = mutableStateOf(false)
    private val direction = mutableStateOf(LayoutDirection.Ltr)
    private val usbStatus = mutableStateOf(UsbSensorConnectionStatus.CONNECTED)
    private val wirelessStatus = mutableStateOf(WirelessSensorConnectionStatus.CONNECTED)
    private val confirmed = mutableStateOf(true)
    private val activity = mutableStateOf(false)
    private val fakeData = mutableStateOf(false)
    private val connectionMode = mutableStateOf(SensorConnectionMode.WIFI)
    private val infoClicks = AtomicInteger()
    private val sourceClicks = AtomicInteger()
    private val settingsClicks = AtomicInteger()
    private val sensorSelections = AtomicInteger()
    private val sensorInfoClicks = AtomicInteger()
    private val sensorInfoVisible = mutableStateOf(false)
    private val sensorSelectionExpanded = mutableStateOf(false)
    private val selectedSensor = mutableStateOf("A")
    private val sensorNameOverride = mutableStateOf<String?>(null)
    private val headerWidth = mutableStateOf(280.dp)
    private val includeMenu = mutableStateOf(false)
    private val infoDescription = "Version info trial"

    private fun showHeader() {
        compose.setContent {
            val view = LocalView.current
            DisposableEffect(view) {
                // Keep only this isolated test window awake, without changing
                // the phone's timeout or developer settings.
                val previous = view.keepScreenOn
                view.keepScreenOn = true
                onDispose { view.keepScreenOn = previous }
            }
            CompositionLocalProvider(LocalLayoutDirection provides direction.value) {
                MaterialTheme(colorScheme = if (dark.value) darkColorScheme() else lightColorScheme()) {
                    val colors = MaterialTheme.colorScheme
                    if (!sensorInfoVisible.value) Column(Modifier.width(headerWidth.value).background(colors.background).testTag("trial_header")) {
                        UvirHomeHeader(
                            sensorConnectionMode = connectionMode.value,
                            useFakeSensorData = fakeData.value,
                            usbSensorStatus = if (connectionMode.value == SensorConnectionMode.USB)
                                usbStatus.value else UsbSensorConnectionStatus.DISCONNECTED,
                            usbAppConnectionConfirmed = connectionMode.value == SensorConnectionMode.USB && confirmed.value,
                            wirelessSensorStatus = wirelessStatus.value,
                            wirelessSensorMode = connectionMode.value,
                            wirelessAppConnectionConfirmed = confirmed.value,
                            sensorActivityInProgress = activity.value,
                            versionInfoDescription = infoDescription,
                            primaryText = colors.onSurface,
                            secondaryText = colors.onSurfaceVariant,
                            onOpenVersionInfo = { infoClicks.incrementAndGet() },
                            onSensorConnectionModeChanged = { sourceClicks.incrementAndGet(); connectionMode.value = it },
                            onOpenSettings = { settingsClicks.incrementAndGet() },
                            sensorSelectionExpanded = sensorSelectionExpanded.value,
                            onSensorSelectionExpandedChange = { sensorSelectionExpanded.value = it },
                            sensorDisplayName = sensorNameOverride.value ?: "Header sensor ${selectedSensor.value}",
                            sensorSelectionEnabled = true,
                            sensorProfiles = listOf(
                                UvirSensorProfile(1, "A", "Header sensor A", 0, 0),
                                UvirSensorProfile(2, "B", "Header sensor B", 0, 0)),
                            selectedSensorDeviceId = selectedSensor.value,
                            onSensorSelected = { selectedSensor.value = it; sensorSelections.incrementAndGet() },
                            onOpenSensorInfo = { sensorInfoClicks.incrementAndGet(); sensorInfoVisible.value = true }
                        )
                        if (includeMenu.value) UvirHomeMenuBar(
                            pinned = false, viewMode = ViewMode.IRRADIANCE, showChart = false,
                            monitoringAlertMetrics = emptyList(), backgroundColor = colors.background,
                            cardColor = colors.surfaceContainer, primaryText = colors.onSurface,
                            secondaryText = colors.onSurfaceVariant, unreadAcquisitionCount = 0, unreadAlertCount = 0,
                            acquisitionActivityInProgress = false, alertActivityInProgress = false,
                            acquisitionSyncInProgress = false, alertSyncInProgress = false,
                            onViewModeChanged = {}, onShowChartChanged = {}, onOpenHistory = {}, onOpenAlertLog = {})
                    }
                    if (sensorInfoVisible.value) TextButton(onClick = {
                        sensorInfoVisible.value = false
                    }) { Text("Close info trial") }
                }
            }
        }
    }

    @Test fun logoInfoAndUnifiedMenuKeepSensorAndConnectionActionsIndependent() {
        showHeader()
        for (night in listOf(false, true)) for (layout in listOf(LayoutDirection.Ltr, LayoutDirection.Rtl)) {
            compose.runOnIdle { dark.value = night; direction.value = layout; selectedSensor.value = "A"; connectionMode.value = SensorConnectionMode.WIFI }
            compose.onNodeWithContentDescription(infoDescription).performClick()
            compose.onNodeWithTag("home_sensor_name").performClick()
            compose.onNodeWithTag("sensor_profile_info_A").performClick()
            compose.onNodeWithText("Close info trial").performClick()
            compose.onNodeWithTag("home_sensor_selection_menu").assertIsDisplayed()
            compose.onNodeWithTag("home_sensor_option_B").performClick()
            compose.onNodeWithTag("home_sensor_status").performClick()
            compose.onNodeWithTag("home_source_option_USB").performClick()
            compose.onNodeWithTag("home_sensor_selection_menu").assertIsDisplayed()
            compose.onNodeWithContentDescription(context.getString(R.string.close)).performClick()
            compose.onNodeWithContentDescription(context.getString(R.string.acquisition_parameters)).performClick()
        }
        assertEquals(4, infoClicks.get())
        assertEquals(4, sensorInfoClicks.get())
        assertEquals(4, sensorSelections.get())
        assertEquals(4, sourceClicks.get())
        assertEquals(4, settingsClicks.get())
    }

    @Test fun compactHeaderPlacesBareConnectionGlyphInStatusRowAndOnlySettingsAtEnd() {
        showHeader()
        val density = context.resources.displayMetrics.density
        for (night in listOf(false, true)) for (layout in listOf(LayoutDirection.Ltr, LayoutDirection.Rtl)) {
            compose.runOnIdle { dark.value = night; direction.value = layout }
            val logo = compose.onNodeWithContentDescription(infoDescription).fetchSemanticsNode().boundsInRoot
            val version = compose.onNodeWithText(BuildConfig.VERSION_NAME, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            assertEquals(logo.center.x, version.center.x, 1f)
            assertTrue(version.top >= logo.center.y && version.bottom <= logo.bottom)
            val selector = compose.onNodeWithTag("home_sensor_selector").fetchSemanticsNode().boundsInRoot
            val settings = compose.onNodeWithContentDescription(context.getString(R.string.acquisition_parameters)).fetchSemanticsNode().boundsInRoot
            val glyph = compose.onNodeWithTag("home_sensor_glyph", true).fetchSemanticsNode().boundsInRoot
            val status = compose.onNodeWithTag("home_sensor_status").fetchSemanticsNode().boundsInRoot
            assertEquals(36f * density, selector.height, 1f)
            assertEquals(40f * density, settings.width, 1f)
            assertEquals(19.2f * .6f * density, glyph.width, 1f)
            // Optical alignment follows the visible letters, not the line's empty leading.
            assertTrue(glyph.center.y in status.top..status.bottom)
            assertEquals(8f * density, if (layout == LayoutDirection.Ltr) settings.left - selector.right else selector.left - settings.right, 1f)
            assertTrue(glyph.top >= status.top && glyph.bottom <= status.bottom)
            compose.onNodeWithTag("home_sensor_source").assertDoesNotExist()
            compose.onNodeWithTag("home_status_dot", true).assertDoesNotExist()
        }
    }

    @Test fun shortAndLongNamesLeaveSettingsVisibleAndStatusBelowTheTitle() {
        showHeader()
        for (night in listOf(false, true)) for (layout in listOf(LayoutDirection.Ltr, LayoutDirection.Rtl))
            for (name in listOf("P", "Portatile", "A very long sensor name which must leave Settings visible")) {
                compose.runOnIdle { dark.value = night; direction.value = layout; sensorNameOverride.value = name }
                val title = compose.onNodeWithTag("home_sensor_name").assertHasClickAction().fetchSemanticsNode().boundsInRoot
                val status = compose.onNodeWithTag("home_sensor_status").fetchSemanticsNode().boundsInRoot
                val settings = compose.onNodeWithContentDescription(context.getString(R.string.acquisition_parameters)).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
                assertTrue(title.bottom <= status.top)
                assertTrue(if (layout == LayoutDirection.Ltr) title.right < settings.left else title.left > settings.right)
                compose.onNodeWithTag("home_sensor_name_chevron", true).assertIsDisplayed()
                compose.onNodeWithTag("home_sensor_source").assertDoesNotExist()
            }
    }

    @Test fun portableNameRemainsFullyVisibleBesideSensorGlyphOnPhoneWidth() {
        headerWidth.value = 304.dp
        sensorNameOverride.value = "Portatile"
        showHeader()
        for (night in listOf(false, true)) for (layout in listOf(LayoutDirection.Ltr, LayoutDirection.Rtl)) {
            compose.runOnIdle { dark.value = night; direction.value = layout }
            val results = mutableListOf<TextLayoutResult>()
            compose.onNodeWithText("Portatile", useUnmergedTree = true)
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { action -> action(results) }
            assertEquals(1, results.size)
            assertTrue("Portatile must not be ellipsized: night=$night, layout=$layout", !results.single().isLineEllipsized(0))
        }
    }

    @Test fun settingsUsesTheTrailingEdgeIndependentlyOfTheMenuBelow() {
        includeMenu.value = true
        showHeader()
        for (width in listOf(280.dp, 304.dp)) for (night in listOf(false, true))
            for (layout in listOf(LayoutDirection.Ltr, LayoutDirection.Rtl)) {
                compose.runOnIdle { headerWidth.value = width; dark.value = night; direction.value = layout }
                val settings = compose.onNodeWithContentDescription(context.getString(R.string.acquisition_parameters))
                    .fetchSemanticsNode().boundsInRoot
                val header = compose.onNodeWithTag("trial_header").fetchSemanticsNode().boundsInRoot
                val endInset = if (layout == LayoutDirection.Ltr) header.right - settings.right
                    else settings.left - header.left
                assertEquals("The menu below does not add a trailing inset", 0f, endInset, 1f)
            }
    }

    @Test fun unifiedDialogShowsFourConnectionChoicesAndDoesNotCloseOnTransportChange() {
        showHeader()
        compose.onNodeWithTag("home_sensor_name").performClick()
        for (mode in SensorConnectionMode.entries) {
            compose.onNodeWithTag("home_source_option_${mode.name}").assertIsDisplayed().performClick()
            compose.onNodeWithTag("home_sensor_selection_menu").assertIsDisplayed()
        }
        assertEquals(4, sourceClicks.get())
        compose.onNodeWithContentDescription(context.getString(R.string.close)).performClick()
        compose.onNodeWithTag("home_sensor_selection_menu").assertDoesNotExist()
    }

    @Test fun bareStatusIconOpensTheSameSensorMenuWithoutAnExtraSourceButton() {
        showHeader()
        for (night in listOf(false, true)) {
            compose.runOnIdle { dark.value = night }
            compose.onNodeWithTag("home_sensor_glyph", true).performTouchInput { click() }
            compose.onNodeWithTag("home_sensor_connection_grid").assertIsDisplayed()
            compose.onNodeWithContentDescription(context.getString(R.string.close)).performClick()
            compose.onNodeWithTag("home_sensor_source").assertDoesNotExist()
        }
        assertEquals(0, sourceClicks.get())
    }

    @Test fun connectionAndSettingsUseBareThemedGlyphsWithoutBadges() {
        showHeader()
        for (night in listOf(false, true)) for (layout in listOf(LayoutDirection.Ltr, LayoutDirection.Rtl))
            for (mode in SensorConnectionMode.entries) {
                compose.runOnIdle { dark.value = night; direction.value = layout; connectionMode.value = mode }
                val background = if (night) darkColorScheme().background else lightColorScheme().background
                val glyph = compose.onNodeWithTag("home_sensor_glyph", true).captureToImage().toPixelMap()
                assertEquals(background, glyph[0, 0])
                assertDotAndSourceColor(Color(0xFF43A047))
                val settings = compose.onNodeWithContentDescription(context.getString(R.string.acquisition_parameters)).captureToImage().toPixelMap()
                val accent = if (night) darkColorScheme().primary else lightColorScheme().primary
                val inset = with(compose.density) { 6.dp.toPx().toInt() }
                assertEquals("Settings must have no resting circle",
                    background, settings[inset, settings.height / 2])
                assertTrue((0 until settings.height).any { y -> (0 until settings.width).any { x -> settings[x, y] == accent } })
            }
    }

    private fun assertDotAndSourceColor(expected: Color) {
        val pixels = compose.onNodeWithTag("home_sensor_glyph", true).captureToImage().toPixelMap()
        val background = if (dark.value) darkColorScheme().background else lightColorScheme().background
        // The icon can pulse: compare the hue after compositing, not a hollow center pixel.
        val ink = (0 until pixels.height).flatMap { y -> (0 until pixels.width).map { x -> pixels[x, y] } }
            .maxBy { kotlin.math.abs(it.red - background.red) + kotlin.math.abs(it.green - background.green) + kotlin.math.abs(it.blue - background.blue) }
        val components = listOf(expected.red - background.red, expected.green - background.green, expected.blue - background.blue)
        val actual = listOf(ink.red - background.red, ink.green - background.green, ink.blue - background.blue)
        val index = components.indices.maxBy { kotlin.math.abs(components[it]) }
        val alpha = actual[index] / components[index]
        assertTrue("Bare connection glyph retains its status tint", alpha > .35f && alpha <= 1.03f)
        for (i in components.indices) assertEquals(components[i] * alpha, actual[i], .025f)
    }

    @Test fun activityDotAndSourceKeepTheConnectionStateColor() {
        showHeader()
        for (darkTheme in listOf(false, true)) {
            for (layout in listOf(LayoutDirection.Ltr, LayoutDirection.Rtl)) {
                for (mode in SensorConnectionMode.entries) {
                    val cases = listOf(
                        Triple(false, true, Color(0xFF43A047)),
                        Triple(true, true, Color(0xFF43A047)),
                        Triple(true, false, Color(0xFFFFC107))
                    )
                    for ((busy, connected, expected) in cases) {
                        compose.runOnIdle {
                            dark.value = darkTheme
                            direction.value = layout
                            connectionMode.value = mode
                            activity.value = busy
                            confirmed.value = connected
                            fakeData.value = false
                            usbStatus.value = UsbSensorConnectionStatus.CONNECTED
                            wirelessStatus.value = WirelessSensorConnectionStatus.CONNECTED
                        }
                        assertDotAndSourceColor(expected)
                    }
                    compose.runOnIdle {
                        usbStatus.value = UsbSensorConnectionStatus.DISCONNECTED
                        wirelessStatus.value = WirelessSensorConnectionStatus.DISCONNECTED
                        confirmed.value = false
                        activity.value = true
                    }
                    assertDotAndSourceColor(Color(0xFFE53935))
                    compose.onNodeWithText(
                        context.getString(R.string.sensor_info_activity)
                    ).assertIsDisplayed()
                }
                compose.runOnIdle { fakeData.value = true; activity.value = true }
                assertDotAndSourceColor(Color(0xFFF57C00))
            }
        }
    }

    @Test fun fakeDataShowsConnectedLabelWithoutClaimingRealActivity() {
        fakeData.value = true
        showHeader()
        for (darkTheme in listOf(false, true)) {
            for (layoutDirection in listOf(LayoutDirection.Ltr, LayoutDirection.Rtl)) {
                for (busy in listOf(false, true)) {
                    compose.runOnIdle {
                        dark.value = darkTheme
                        direction.value = layoutDirection
                        activity.value = busy
                        confirmed.value = false
                    }
                    compose.onNodeWithText(context.getString(R.string.sensor_status_connected)).assertIsDisplayed()
                    compose.onNodeWithText(context.getString(R.string.debug_fake_data)).assertDoesNotExist()
                    compose.onNodeWithText(context.getString(R.string.sensor_info_activity)).assertDoesNotExist()
                }
            }
        }
    }

    @Test fun existingConnectionAndActivityLabelsRemainUnchanged() {
        showHeader()
        compose.onNodeWithText(context.getString(R.string.sensor_status_connected)).assertIsDisplayed()
        compose.runOnIdle { activity.value = true }
        compose.onNodeWithText(context.getString(R.string.sensor_info_activity)).assertIsDisplayed()
        compose.runOnIdle { confirmed.value = false }
        compose.onNodeWithText(context.getString(R.string.sensor_info_activity)).assertIsDisplayed()
        compose.runOnIdle { activity.value = false }
        compose.onNodeWithText(context.getString(R.string.sensor_info_connecting)).assertIsDisplayed()
        compose.runOnIdle { wirelessStatus.value = WirelessSensorConnectionStatus.CONNECTING }
        compose.onNodeWithText(context.getString(R.string.sensor_info_searching)).assertIsDisplayed()
        compose.runOnIdle { wirelessStatus.value = WirelessSensorConnectionStatus.DISCONNECTED }
        compose.onNodeWithText(context.getString(R.string.sensor_status_no_sensor)).assertIsDisplayed()
    }
}
