package me.mondiversi.uvir

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Synthetic session content; opening callbacks never navigate to the user's archive. */
class UvirSessionRecordHeaderUiTest {
    @get:Rule val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val alerts = mutableStateOf(false)
    private val night = mutableStateOf(false)
    private val rtl = mutableStateOf(false)
    private val single = mutableStateOf(false)
    private val opened = mutableListOf<Long>()
    private var collapses = 0
    private var display by mutableStateOf(UvirSavedValueDisplay())
    private val sample = SensorSample(uvc = 1.0, uvb = 2.0, uva = 3.0)
    private val records = listOf(SavedRecordDetail(1, 1_000, "", false, sample), SavedRecordDetail(2, 2_000, "", false, sample))
    private val events = records.map { record -> AlertSessionValueEvent(
        ThresholdAlertLogEntry(record.id, record.timestamp, "", sessionId = 1, sessionSequence = record.id.toInt()),
        listOf(ThresholdAlertViolation(ThresholdAlertRule(ThresholdAlertMetric.UVC, true, ThresholdAlertDirection.ABOVE, .5f), 1.0))) }

    private fun fixture() = compose.setContent {
        KeepUvirVerificationScreenOn()
        CompositionLocalProvider(LocalLayoutDirection provides if (rtl.value) LayoutDirection.Rtl else LayoutDirection.Ltr) {
            MaterialTheme(colorScheme = if (night.value) darkColorScheme() else lightColorScheme()) {
                val colors = MaterialTheme.colorScheme
                Surface {
                    Column(Modifier.width(340.dp).height(620.dp).padding(12.dp).verticalScroll(rememberScrollState())) {
                        if (alerts.value) {
                            if (single.value) AlertViolationDataCard(false, events.first().violations,
                                expanded = true, onToggle = { collapses++ }, cardColor = colors.surfaceVariant,
                                primaryText = colors.onSurface, secondaryText = colors.onSurfaceVariant)
                            else AlertSessionValuesCard(events, false, true, { collapses++ }, colors.surfaceVariant,
                                colors.onSurface, colors.onSurfaceVariant, { opened += it.id })
                        } else SessionAcquisitionValuesCard(if (single.value) records.take(1) else records, SessionChartGroup.UV,
                            showRecordHeader = !single.value, showRelativeBreakdown = true, expanded = true,
                            onToggle = { collapses++ }, cardColor = colors.surfaceVariant, primaryText = colors.onSurface,
                            secondaryText = colors.onSurfaceVariant, display = display,
                            onDisplayChange = { display = it }, onOpenRecord = { opened += it.id })
                    }
                }
            }
        }
    }

    private fun key(id: Int) = if (alerts.value) "alert_false_$id" else "acquisition_UV_$id"
    private fun row(id: Int) = compose.onNodeWithTag("session_record_header_${key(id)}", useUnmergedTree = true)
    private fun arrow(id: Int) = compose.onNodeWithTag("session_record_chevron_${key(id)}", useUnmergedTree = true)

    @Test fun eachWholeRowOpensOnlyItsOwnRecordAndTheChevronHasNoSeparateButtonInBothThemesAndRtl() {
        fixture()
        for (isAlert in listOf(false, true)) for (dark in listOf(false, true)) for (rightToLeft in listOf(false, true)) {
            compose.runOnIdle { alerts.value = isAlert; night.value = dark; rtl.value = rightToLeft }
            val recordRow = row(1).performScrollTo().assertHasClickAction()
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            val arrow = arrow(1).assertHasNoClickAction()
            val bounds = recordRow.getUnclippedBoundsInRoot()
            val glyph = arrow.getUnclippedBoundsInRoot()
            val onePixel = with(compose.density) { 1.toDp().value }
            assertEquals(10f, (glyph.right - glyph.left).value, onePixel + .001f)
            if (rightToLeft) assertEquals(16f, (glyph.left - bounds.left).value, onePixel + .001f)
            else assertEquals(16f, (bounds.right - glyph.right).value, onePixel + .001f)
            val before = opened.size
            recordRow.performTouchInput { click(Offset(2f, height / 2f)) }
            arrow.performTouchInput { click(center) }
            recordRow.performTouchInput { click(center) }
            row(2).performScrollTo().performClick()
            compose.runOnIdle {
                assertEquals(listOf(1L, 1L, 1L, 2L), opened.drop(before))
                assertEquals(0, collapses)
                assertTrue(display.spectrum.excludedChannels.isEmpty())
            }
        }
    }

    @Test fun recordOpeningAndBandSelectionAreSeparateAndSingleViewsHaveNoRecordHeader() {
        fixture()
        compose.onNodeWithTag("saved_row_acquisition_1_UVC", useUnmergedTree = true).performClick().assertIsOff()
        compose.runOnIdle { assertTrue(opened.isEmpty()) }
        row(1).performClick()
        compose.runOnIdle { assertEquals(listOf(1L), opened); single.value = true }
        row(1).assertDoesNotExist()
        compose.runOnIdle { alerts.value = true }
        row(1).assertDoesNotExist()
        compose.runOnIdle { assertEquals(0, collapses) }
    }

    @Test fun pressedFeedbackSpansTheWholeRecordRowForBothSessionKindsAndThemes() {
        fixture()
        for (isAlert in listOf(false, true)) for (dark in listOf(false, true)) {
            compose.runOnIdle { alerts.value = isAlert; night.value = dark }
            val row = row(1).performScrollTo()
            val root = compose.onRoot()
            Thread.sleep(400)
            val before = root.captureToImage().toPixelMap()
            val bounds = row.fetchSemanticsNode().boundsInRoot
            val origin = root.fetchSemanticsNode().boundsInRoot.topLeft
            val inset = with(compose.density) { 2.dp.toPx() }
            row.performTouchInput { down(center) }
            compose.mainClock.advanceTimeBy(200)
            compose.waitForIdle()
            Thread.sleep(350)
            val image = root.captureToImage()
            val after = image.toPixelMap()
            for (point in listOf(Offset(bounds.left + inset, bounds.center.y), Offset(bounds.right - inset, bounds.center.y))) {
                val x = (point.x - origin.x).roundToInt().coerceIn(0, after.width - 1)
                val y = (point.y - origin.y).roundToInt().coerceIn(0, after.height - 1)
                val a = before[x, y]; val b = after[x, y]
                assertTrue(abs(a.red - b.red) + abs(a.green - b.green) + abs(a.blue - b.blue) > .01f)
            }
            File(context.getExternalFilesDir(null), "session-record-row-${if (isAlert) "alert" else "acquisition"}-${if (dark) "night" else "day"}.png")
                .outputStream().use { assertTrue(image.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)) }
            row.performTouchInput { up() }
        }
    }
}
