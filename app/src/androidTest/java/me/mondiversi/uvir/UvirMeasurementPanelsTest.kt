package me.mondiversi.uvir

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Disposable layout only: never opens the production database or transports. */
class UvirMeasurementPanelsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun detailSelectorsAndVariantsStayOnOneRowInBothOrientationsAndRtl() {
        val split = mutableStateOf(true)
        val rtl = mutableStateOf(false)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val descriptions = listOf(R.string.irradiance_view, R.string.biological_effects_view,
            R.string.show_live_values, R.string.show_live_chart).map(context::getString)
        compose.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalUvirMeasurementPanels provides split.value,
                    LocalLayoutDirection provides if (rtl.value) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                    Box(Modifier.requiredWidth(300.dp)) {
                        MeasurementDetailViewSelector(ViewMode.IRRADIANCE, {}, false, {},
                            Color.LightGray, Color.Black, Color.DarkGray,
                            middleContent = {
                                Box(Modifier.testTag("variant-slot")) {
                                    UvirSessionCyclePositionSelector(3, 1, Color.LightGray,
                                        Color.Black, Color.DarkGray, {})
                                }
                            })
                    }
                }
            }
        }
        for (isSplit in listOf(true, false)) {
            for (isRtl in listOf(false, true)) {
                compose.runOnIdle { split.value = isSplit; rtl.value = isRtl }
                val variant = compose.onNodeWithTag("variant-slot").getUnclippedBoundsInRoot()
                val centerY = (variant.top.value + variant.bottom.value) / 2f
                descriptions.forEach { description ->
                    val bounds = compose.onNodeWithContentDescription(description).getUnclippedBoundsInRoot()
                    assertEquals(description, centerY, (bounds.top.value + bounds.bottom.value) / 2f, 1f)
                    assertTrue(description, bounds.right - bounds.left >= UvirMeasurementSelectorIconSize)
                }
            }
        }
    }

    @Test fun homeIslandsPutInfraredImmediatelyBelowUvAndVisibleInTheOtherColumn() {
        compose.setContent {
            Box(Modifier.requiredWidth(640.dp)) {
                UvirLiveLandscapeIslands(onGroupBounds = { _, _ -> }) { group ->
                    val height = when (group) {
                        SensorGroup.UV -> 100.dp
                        SensorGroup.NIR -> 60.dp
                        else -> 300.dp
                    }
                    Text(group.name, Modifier.height(height).testTag(group.name))
                }
            }
        }
        val uv = compose.onNodeWithTag("UV").getUnclippedBoundsInRoot()
        val ir = compose.onNodeWithTag("NIR").getUnclippedBoundsInRoot()
        val visible = compose.onNodeWithTag("VISIBLE").getUnclippedBoundsInRoot()
        assertEquals(uv.left.value, ir.left.value, 0.1f)
        assertEquals(uv.top.value, visible.top.value, 0.1f)
        val expectedGap = with(compose.density) { UvirIslandSpacing.roundToPx().toDp().value }
        assertEquals(expectedGap, ir.top.value - uv.bottom.value, 0.01f)
        assertTrue(visible.left > uv.right)
    }

    @Test fun landscapeKeepsTitleOnLeftAndFixedSelectorAboveRightValues() {
        val rtl = mutableStateOf(false)
        val landscape = mutableStateOf(true)
        val chart = mutableStateOf(false)
        compose.setContent {
            val configuration = Configuration(LocalConfiguration.current).apply {
                orientation = if (landscape.value) Configuration.ORIENTATION_LANDSCAPE
                    else Configuration.ORIENTATION_PORTRAIT
            }
            CompositionLocalProvider(LocalConfiguration provides configuration,
                LocalLayoutDirection provides if (rtl.value) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                Box(Modifier.requiredWidth(640.dp).fillMaxSize()) {
                    val state = rememberLazyListState()
                    UvirMeasurementScaffold(state, androidx.compose.ui.graphics.Color.White,
                        androidx.compose.ui.graphics.Color.Gray, PaddingValues(8.dp),
                        topBar = { UvirCollapsingDetailTitleBar(state) { Text("Title", Modifier.testTag("title")) } }) {
                        item { Text("Identity", Modifier.testTag("identity")) }
                        item { Text("Note", Modifier.testTag("note")) }
                        stickyHeader("selector") {
                            Button(onClick = { chart.value = !chart.value }, Modifier.testTag("selector")) {
                                Text(if (chart.value) "Chart" else "Data")
                            }
                        }
                        items(30, key = { "value_$it" }) {
                            Text("Value $it", Modifier.height(100.dp).testTag("value_$it"))
                        }
                    }
                }
            }
        }
        val header = compose.onNodeWithTag("identity").getUnclippedBoundsInRoot()
        val selector = compose.onNodeWithTag("selector").getUnclippedBoundsInRoot()
        val value = compose.onNodeWithTag("value_0").getUnclippedBoundsInRoot()
        assertTrue(header.right < value.left)
        assertTrue(header.right < selector.left)
        assertEquals(header.top.value, value.top.value, 0.1f)
        val viewport = compose.onNodeWithTag("measurement-values-pane").getUnclippedBoundsInRoot()
        assertTrue(viewport.top - selector.bottom >= UvirMeasurementHeaderGap)
        compose.onNodeWithTag("selector").performClick()
        compose.onNodeWithText("Chart").assertExists()
        compose.onNodeWithTag("value_0").performTouchInput { swipeUp() }
        compose.onNodeWithTag("title").assertIsDisplayed()
        compose.onNodeWithTag("selector").assertIsDisplayed()
        assertEquals(selector, compose.onNodeWithTag("selector").getUnclippedBoundsInRoot())
        assertEquals(viewport, compose.onNodeWithTag("measurement-values-pane").getUnclippedBoundsInRoot())
        compose.runOnIdle { rtl.value = true }
        val mirroredHeader = compose.onNodeWithTag("identity").getUnclippedBoundsInRoot()
        val mirroredSelector = compose.onNodeWithTag("selector").getUnclippedBoundsInRoot()
        compose.onNodeWithTag("title").assertIsDisplayed()
        assertTrue(mirroredHeader.left > 320.dp)
        assertTrue(mirroredSelector.right < mirroredHeader.left)
        compose.runOnIdle { landscape.value = false }
        compose.onNodeWithText("Chart").assertExists()
    }
}
