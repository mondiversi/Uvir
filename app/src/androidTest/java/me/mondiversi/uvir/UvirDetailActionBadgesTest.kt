package me.mondiversi.uvir

import android.content.res.Configuration
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Render the real detail and list views using a private database fixture only. */
class UvirDetailActionBadgesTest : SessionNoteEditingFixture() {
    @get:Rule val compose = createComposeRule()

    private fun verifyScreen(screen: Int) {
        database.startAcquisitionSession(12L, "original", 900L, a)
        database.startAlertSession(13L, "original", 900L, a)
        val records = listOf(database.readRecord(acquisition())!!, database.readRecord(acquisition())!!)
        assertTrue(database.updateAcquisitionSessionVariantsPerPosition(12L, 2))
        val entry = alert()
        val night = mutableStateOf(false)
        val rtl = mutableStateOf(false)
        var purple = Color.Unspecified
        var red = Color.Unspecified
        var background = Color.Unspecified
        compose.setContent {
            val configuration = Configuration(LocalConfiguration.current).apply {
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                    if (night.value) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            }
            CompositionLocalProvider(LocalContext provides context, LocalConfiguration provides configuration,
                LocalLayoutDirection provides if (rtl.value) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                MaterialTheme(colorScheme = if (night.value) darkColorScheme() else lightColorScheme()) {
                    UvirActionTheme {
                        val colors = MaterialTheme.colorScheme
                        purple = colors.primary
                        red = UvirDestructiveActionColor
                        background = colors.background
                        when (screen) {
                            0 -> RecordDetailScreen(records[0], database, rememberLazyListState(),
                                background, colors.surface, colors.onSurface, colors.onSurfaceVariant, colors.outline, {}, {})
                            1 -> SessionChartScreen(12L, records, database,
                                background, colors.surface, colors.onSurface, colors.onSurfaceVariant,
                                onDeleteSession = {}, onOpenRecord = { _, _ -> }, onBack = {})
                            2 -> AlertChartScreen(entry, database, background, colors.surface,
                                colors.onSurface, colors.onSurfaceVariant, {}, {})
                            3 -> AlertSessionChartScreen(13L, listOf(entry), database, background, colors.surface,
                                colors.onSurface, colors.onSurfaceVariant, {}, {}, {})
                            4 -> HistoryScreen(database, rememberLazyListState(), ListEdgeAnchor.START, {},
                                background, colors.surface, colors.onSurface, colors.onSurfaceVariant, {}, {}, {})
                            5 -> ThresholdAlertLogScreen(database, rememberLazyListState(),
                                background, colors.surface, colors.onSurface, colors.onSurfaceVariant, {})
                            6 -> AcquisitionChartScreen(records[0], background, colors.surface,
                                colors.onSurface, colors.onSurfaceVariant, {})
                            7 -> UvirSensorInfoScreen(SensorConnectionMode.USB, UvirSensorRuntimeInfo(deviceId = a),
                                background, colors.onSurface, colors.onSurfaceVariant, colors.surface, {})
                        }
                    }
                }
            }
        }
        val shareRes = when (screen) {
            0 -> R.string.share_measurements
            1 -> R.string.session_chart_share
            2 -> R.string.alert_chart_share
            3 -> R.string.alert_session_chart_share
            4 -> R.string.share_measurements
            5 -> R.string.threshold_alert_log_share
            6 -> R.string.acquisition_chart_share
            else -> R.string.export
        }
        for (dark in listOf(false, true)) for (rightToLeft in listOf(false, true)) {
            compose.runOnIdle { night.value = dark; rtl.value = rightToLeft }
            compose.waitForIdle()
            assertCompactBack(purple, background)
            assertBadge(context.getString(shareRes), purple, background)
            if (screen < 6) {
                assertBadge(context.getString(if (screen >= 4) R.string.delete_all else R.string.delete), red, background)
            }
            if (screen == 1) {
                assertBadge(context.getString(R.string.session_sequence_filter_title), purple, background)
                compose.onNodeWithTag("session-cycle-filter-toggle").assertIsSelected()
            }
            if (screen == 4 || screen == 5) {
                assertBadge(context.getString(R.string.list_filters), purple, background)
                compose.onNodeWithTag("list-filter-toggle").performSemanticsAction(
                    androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }
                compose.onNodeWithContentDescription(context.getString(R.string.navigate_back)).performSemanticsAction(
                    androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }
                // Compare the resting palette after the pressed ripple has faded.
                compose.mainClock.advanceTimeBy(600L)
                compose.waitForIdle()
                assertBadge(context.getString(R.string.list_filters), purple, background)
            }
            if (screen == 7) {
                // Activate without a pointer ripple: this test compares the resting palette.
                compose.onNodeWithContentDescription(context.getString(R.string.export)).performSemanticsAction(
                    androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }
                compose.onNodeWithText(context.getString(R.string.export_sensor_info_dialog_title)).assertIsDisplayed()
                compose.onNodeWithContentDescription(context.getString(R.string.close)).performSemanticsAction(
                    androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }
                compose.mainClock.advanceTimeBy(600L)
                compose.waitForIdle()
                assertBadge(context.getString(R.string.export), purple, background)
            }
        }
    }

    private fun assertBadge(label: String, color: Color, background: Color) {
        val node = compose.onNodeWithContentDescription(label).assertIsDisplayed().assertIsEnabled().assertHasClickAction()
        val bounds = node.fetchSemanticsNode().boundsInRoot
        val target = with(compose.density) { 40.dp.toPx() }
        assertEquals(target, bounds.width, 1f)
        assertEquals(target, bounds.height, 1f)
        val pixels = node.captureToImage().toPixelMap()
        val inset = with(compose.density) { 5.dp.toPx().toInt() }
        assertEquals(background, pixels[inset, pixels.height / 2])
        assertTrue("The bare action must retain its semantic color",
            (0 until pixels.height).any { y -> (0 until pixels.width).any { x -> pixels[x, y] == color } })
        assertEquals(background, pixels[0, 0])
    }

    private fun assertCompactBack(color: Color, background: Color) {
        val node = compose.onNodeWithContentDescription(context.getString(R.string.navigate_back))
            .assertIsDisplayed().assertIsEnabled().assertHasClickAction()
        val pixels = node.captureToImage().toPixelMap()
        val inset = with(compose.density) { 5.dp.toPx().toInt() }
        assertEquals(background, pixels[inset, pixels.height / 2])
        val badgeInset = with(compose.density) { 11.dp.toPx().toInt() }
        assertEquals(background, pixels[badgeInset, pixels.height / 2])
        assertTrue((0 until pixels.height).any { y -> (0 until pixels.width).any { x -> pixels[x, y] == color } })
    }

    @Test fun acquisitionUsesPurpleActionsAndRedDelete() = verifyScreen(0)
    @Test fun acquisitionSessionIncludesTheVariantsBadge() = verifyScreen(1)
    @Test fun alertUsesPurpleActionsAndRedDelete() = verifyScreen(2)
    @Test fun alertSessionUsesPurpleActionsAndRedDelete() = verifyScreen(3)
    @Test fun acquisitionListUsesBadgesAndCompactBack() = verifyScreen(4)
    @Test fun alertListUsesBadgesAndCompactBack() = verifyScreen(5)
    @Test fun standaloneChartExportUsesBadgeAndCompactBack() = verifyScreen(6)
    @Test fun sensorInformationExportUsesBadgeAndKeepsItsConfirmation() = verifyScreen(7)
}
