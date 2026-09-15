package me.mondiversi.uvir

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
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
    private val colorTitleForAlignment = mutableStateOf(false)
    private val infoClicks = AtomicInteger()
    private val sourceClicks = AtomicInteger()
    private val settingsClicks = AtomicInteger()
    private val infoDescription = "Version info trial"
    private val sourceDescription = context.getString(
        R.string.sensor_source_accessibility,
        context.getString(R.string.sensor_connection_wifi)
    )

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
                    Box(Modifier.width(280.dp).background(colors.background).testTag("trial_header")) {
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
                            primaryText = if (colorTitleForAlignment.value) Color.Blue else colors.onSurface,
                            secondaryText = colors.onSurfaceVariant,
                            onOpenVersionInfo = { infoClicks.incrementAndGet() },
                            onOpenSensorSource = { sourceClicks.incrementAndGet() },
                            onOpenSettings = { settingsClicks.incrementAndGet() }
                        )
                    }
                }
            }
        }
    }

    @Test fun logoTitleAndVersionOpenInfoWhileOnlySensorButtonOpensSource() {
        showHeader()
        var combinations = 0
        for (darkTheme in listOf(false, true)) {
            for (layoutDirection in listOf(LayoutDirection.Ltr, LayoutDirection.Rtl)) {
                compose.runOnIdle { dark.value = darkTheme; direction.value = layoutDirection }
                compose.onNodeWithContentDescription(infoDescription).assertHasClickAction().performClick()
                compose.onNodeWithText(BuildConfig.VERSION_NAME, useUnmergedTree = true)
                    .performTouchInput { click(center) }
                compose.onNodeWithText("Uvir").assertHasClickAction().performClick()
                compose.onNodeWithText(context.getString(R.string.sensor_status_connected))
                    .assertHasNoClickAction()
                compose.onNodeWithContentDescription(sourceDescription).assertHasClickAction().performClick()
                compose.onNodeWithContentDescription(context.getString(R.string.acquisition_parameters))
                    .performClick()
                combinations++
                assertEquals(combinations * 3, infoClicks.get())
                assertEquals(combinations, sourceClicks.get())
                assertEquals(combinations, settingsClicks.get())
            }
        }
    }

    @Test fun versionFollowsTitleAndSensorButtonImmediatelyPrecedesSettings() {
        showHeader()
        for (darkTheme in listOf(false, true)) {
            for (layoutDirection in listOf(LayoutDirection.Ltr, LayoutDirection.Rtl)) {
                compose.runOnIdle { dark.value = darkTheme; direction.value = layoutDirection }
                assertEquals(1, compose.onAllNodesWithText(BuildConfig.VERSION_NAME,
                    useUnmergedTree = true).fetchSemanticsNodes().size)
                val logo = compose.onNodeWithContentDescription(infoDescription).fetchSemanticsNode().boundsInRoot
                val version = compose.onNodeWithText(BuildConfig.VERSION_NAME,
                    useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                val source = compose.onNodeWithContentDescription(sourceDescription).fetchSemanticsNode().boundsInRoot
                val title = compose.onNodeWithText("Uvir").fetchSemanticsNode().boundsInRoot
                val settings = compose.onNodeWithContentDescription(context.getString(R.string.acquisition_parameters))
                    .fetchSemanticsNode().boundsInRoot
                assertEquals(title.center.y, version.center.y, 2f)
                assertEquals(settings.center.y, source.center.y, 1f)
                val logoGap = if (layoutDirection == LayoutDirection.Ltr) title.left - logo.right
                    else logo.left - title.right
                assertEquals("More breathing room between logo and title/status", logo.width * 12f / 46f, logoGap, 1f)
                assertTrue("The source column must remain compact", source.width < logo.width * 0.7f)
                val buttonGap = if (layoutDirection == LayoutDirection.Ltr) settings.left - source.right
                    else source.left - settings.right
                assertEquals("Compact gap between sensor and settings", source.width * 3f / 28f, buttonGap, 1f)
                if (layoutDirection == LayoutDirection.Ltr) {
                    assertTrue(logo.right <= title.left)
                    assertTrue(title.right <= version.left)
                    assertTrue(version.right <= source.left)
                    assertTrue(source.right <= settings.left)
                } else {
                    assertTrue(logo.left >= title.right)
                    assertTrue(title.left >= version.right)
                    assertTrue(version.left >= source.right)
                    assertTrue(source.left >= settings.right)
                }
                compose.onNodeWithTag("home_status_dot", useUnmergedTree = true).assertIsDisplayed()
                compose.onNodeWithText(BuildConfig.VERSION_NAME, useUnmergedTree = true).assertIsDisplayed()
            }
        }
    }

    @Test fun pressingSourceDoesNotDrawABackgroundInEitherTheme() {
        compose.mainClock.autoAdvance = false
        showHeader()
        compose.mainClock.advanceTimeByFrame()
        for (darkTheme in listOf(false, true)) {
            compose.runOnIdle { dark.value = darkTheme }
            compose.mainClock.advanceTimeByFrame()
            val before = compose.onNodeWithTag("trial_header").captureToImage().toPixelMap()
            compose.onNodeWithContentDescription(sourceDescription).performTouchInput { down(center) }
            compose.mainClock.advanceTimeBy(300L)
            val during = compose.onNodeWithTag("trial_header").captureToImage().toPixelMap()
            assertEquals(before.width, during.width)
            assertEquals(before.height, during.height)
            for (y in 0 until before.height) {
                for (x in 0 until before.width) {
                    assertEquals("No pressed background, dark=$darkTheme at $x,$y", before[x, y], during[x, y])
                }
            }
            compose.onNodeWithContentDescription(sourceDescription).performTouchInput { up() }
            compose.mainClock.advanceTimeByFrame()
        }
    }

    @Test fun connectionDrawingsAlignWithVisibleSettingsIconInBothThemesAndDirections() {
        colorTitleForAlignment.value = true
        showHeader()
        for (darkTheme in listOf(false, true)) {
            for (layoutDirection in listOf(LayoutDirection.Ltr, LayoutDirection.Rtl)) {
                for (mode in SensorConnectionMode.entries) {
                    compose.runOnIdle {
                        dark.value = darkTheme
                        direction.value = layoutDirection
                        connectionMode.value = mode
                    }
                    val label = context.getString(when (mode) {
                        SensorConnectionMode.USB -> R.string.sensor_connection_usb
                        SensorConnectionMode.WIFI -> R.string.sensor_connection_wifi
                        SensorConnectionMode.BLUETOOTH -> R.string.sensor_connection_bluetooth
                        SensorConnectionMode.INTERNET -> R.string.sensor_connection_internet
                    })
                    val source = compose.onNodeWithContentDescription(context.getString(
                        R.string.sensor_source_accessibility, label
                    )).fetchSemanticsNode().boundsInRoot
                    val title = compose.onNodeWithContentDescription(context.getString(R.string.acquisition_parameters))
                        .fetchSemanticsNode().boundsInRoot
                    val header = compose.onNodeWithTag("trial_header")
                    val bounds = header.fetchSemanticsNode().boundsInRoot
                    val pixels = header.captureToImage().toPixelMap()
                    val sourceLeft = (source.left - bounds.left).toInt().coerceAtLeast(0)
                    val sourceRight = (source.right - bounds.left).toInt().coerceAtMost(pixels.width)
                    val titleLeft = (title.left - bounds.left).toInt().coerceAtLeast(0)
                    val titleRight = (title.right - bounds.left).toInt().coerceAtMost(pixels.width)
                    val rowBottom = (source.bottom - bounds.top).toInt().coerceAtMost(pixels.height)
                    var sourceTop = pixels.height
                    var sourceBottom = -1
                    var titleTop = pixels.height
                    var titleBottom = -1
                    for (y in 0 until rowBottom) {
                        for (x in sourceLeft until sourceRight) {
                            val color = pixels[x, y]
                            if (color.green > 0.45f && color.green > color.red * 1.3f &&
                                color.green > color.blue * 1.3f) {
                                sourceTop = minOf(sourceTop, y)
                                sourceBottom = maxOf(sourceBottom, y)
                            }
                        }
                        for (x in titleLeft until titleRight) {
                            val color = pixels[x, y]
                            if (color.blue > 0.6f && color.blue > color.red * 2f &&
                                color.blue > color.green * 2f) {
                                titleTop = minOf(titleTop, y)
                                titleBottom = maxOf(titleBottom, y)
                            }
                        }
                    }
                    assertTrue("Source $mode must be visible", sourceBottom >= sourceTop)
                    assertTrue("Settings icon must be visible", titleBottom >= titleTop)
                    assertEquals("Visible source/settings centers: $mode, dark=$darkTheme, $layoutDirection",
                        (titleTop + titleBottom) / 2f, (sourceTop + sourceBottom) / 2f, 1f)
                }
            }
        }
    }

    private fun assertDotAndSourceColor(expected: Color) {
        val dot = compose.onNodeWithTag("home_status_dot", useUnmergedTree = true).captureToImage().toPixelMap()
        val actual = dot[dot.width / 2, dot.height / 2]
        assertEquals(expected.red, actual.red, 0.01f)
        assertEquals(expected.green, actual.green, 0.01f)
        assertEquals(expected.blue, actual.blue, 0.01f)
        val description = if (fakeData.value) {
            context.getString(R.string.sensor_source_debug_accessibility)
        } else {
            val label = context.getString(when (connectionMode.value) {
                SensorConnectionMode.USB -> R.string.sensor_connection_usb
                SensorConnectionMode.WIFI -> R.string.sensor_connection_wifi
                SensorConnectionMode.BLUETOOTH -> R.string.sensor_connection_bluetooth
                SensorConnectionMode.INTERNET -> R.string.sensor_connection_internet
            })
            context.getString(R.string.sensor_source_accessibility, label)
        }
        val icon = compose.onNodeWithContentDescription(description).captureToImage().toPixelMap()
        var matchingPixels = 0
        for (y in 0 until icon.height) for (x in 0 until icon.width) {
            val pixel = icon[x, y]
            if (kotlin.math.abs(pixel.red - expected.red) < 0.02f &&
                kotlin.math.abs(pixel.green - expected.green) < 0.02f &&
                kotlin.math.abs(pixel.blue - expected.blue) < 0.02f) matchingPixels++
        }
        assertTrue("Connection icon must match the dot: $description, color=$expected", matchingPixels > 4)
    }

    @Test fun activityDotAndSourceAreBlueOnlyForAConfirmedRealConnection() {
        showHeader()
        for (darkTheme in listOf(false, true)) {
            for (layout in listOf(LayoutDirection.Ltr, LayoutDirection.Rtl)) {
                for (mode in SensorConnectionMode.entries) {
                    val cases = listOf(
                        Triple(false, true, Color(0xFF43A047)),
                        Triple(true, true, Color(0xFF2979FF)),
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
        compose.onNodeWithText(context.getString(R.string.sensor_info_connecting)).assertIsDisplayed()
        compose.runOnIdle { wirelessStatus.value = WirelessSensorConnectionStatus.CONNECTING }
        compose.onNodeWithText(context.getString(R.string.sensor_info_searching)).assertIsDisplayed()
        compose.runOnIdle { wirelessStatus.value = WirelessSensorConnectionStatus.DISCONNECTED }
        compose.onNodeWithText(context.getString(R.string.sensor_status_no_sensor)).assertIsDisplayed()
    }
}
