package me.mondiversi.uvir

import android.content.res.Configuration
import android.os.LocaleList
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Only isolated composables: no real records, sensor commands or app preferences. */
class UvirAutomaticAcquisitionSectionsTest {
    @get:Rule val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun expansionScrollsToHeader(useCheckbox: Boolean) {
        val expanded = mutableStateOf(false)
        var changes = 0
        compose.setContent {
            MaterialTheme {
                LazyColumn(Modifier.width(320.dp).height(400.dp).testTag("list"),
                    state = rememberLazyListState()) {
                    item { Spacer(Modifier.height(260.dp)) }
                    item {
                        UvirAutomaticAcquisitionSection("Option", AutomaticSettingIconType.DURATION,
                            expanded.value, { expanded.value = it; changes++ },
                            MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.onSurface,
                            MaterialTheme.colorScheme.onSurfaceVariant) {
                            Box(Modifier.height(600.dp)) { Text("Details") }
                        }
                    }
                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
        }
        val header = compose.onNodeWithText("Option", useUnmergedTree = true)
        val before = header.fetchSemanticsNode().boundsInRoot.top
        if (useCheckbox) compose.onNode(isToggleable()).performClick()
        else compose.onNodeWithText("Option").performClick()
        compose.waitForIdle()
        val after = header.fetchSemanticsNode().boundsInRoot.top
        val listTop = compose.onNodeWithTag("list").fetchSemanticsNode().boundsInRoot.top
        assertTrue("Opening must reveal the card on the first tap: $before -> $after", after < before)
        assertTrue("Header must stay at the top, not below the viewport", after - listTop < 60f)
        assertEquals(1, changes)
        compose.onNodeWithText("Details").assertIsDisplayed()
    }

    @Test fun headerTapBringsFirstExpansionIntoView() = expansionScrollsToHeader(false)
    @Test fun checkboxTapUsesTheSameFirstExpansionScroll() = expansionScrollsToHeader(true)

    @Test fun expandedHeaderKeepsNeutralBackgroundAndTextInBothThemes() {
        val dark = mutableStateOf(false)
        val expanded = mutableStateOf(false)
        var density = 1f
        var expected = Color.Unspecified
        var expectedText = Color.Unspecified
        compose.setContent {
            val configuration = Configuration(LocalConfiguration.current).apply {
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                    if (dark.value) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            }
            CompositionLocalProvider(LocalConfiguration provides configuration) {
                MaterialTheme(colorScheme = if (dark.value) darkColorScheme() else lightColorScheme()) {
                    density = LocalDensity.current.density
                    val surface = MaterialTheme.colorScheme.surface
                    expected = surface
                    expectedText = MaterialTheme.colorScheme.onSurface
                    Box(Modifier.width(320.dp).testTag("card")) {
                        UvirAutomaticAcquisitionSection("Option", AutomaticSettingIconType.DURATION,
                            expanded.value, { expanded.value = it }, surface,
                            MaterialTheme.colorScheme.onSurface, MaterialTheme.colorScheme.onSurfaceVariant) {
                            Text("Details")
                        }
                    }
                }
            }
        }
        for (night in listOf(false, true)) {
            for (open in listOf(false, true)) {
                compose.runOnIdle { dark.value = night; expanded.value = open }
                compose.waitForIdle()
                val header = compose.onNodeWithText("Option")
                val card = compose.onNodeWithTag("card")
                assertEquals("Whole header remains the tap target", card.fetchSemanticsNode().boundsInRoot.width,
                    header.fetchSemanticsNode().boundsInRoot.width, 1f)
                val layouts = mutableListOf<TextLayoutResult>()
                compose.onNodeWithText("Option", useUnmergedTree = true)
                    .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
                assertEquals("No accent text on expanded automatic cards", expectedText,
                    layouts.single().layoutInput.style.color)
                val pixels = card.captureToImage().toPixelMap()
                val y = (24 * density).toInt()
                val points = listOf((2 * density).toInt(), pixels.width - 1 - (2 * density).toInt())
                points.forEach { x ->
                    val actual = pixels[x, y]
                    assertEquals("Red channel at header edge", expected.red, actual.red, 0.015f)
                    assertEquals("Green channel at header edge", expected.green, actual.green, 0.015f)
                    assertEquals("Blue channel at header edge", expected.blue, actual.blue, 0.015f)
                }
            }
        }
    }

    @Test fun collapsedTitleKeepsItsExistingSizeAndWeight() {
        val expanded = mutableStateOf(false)
        compose.setContent {
            MaterialTheme {
                UvirAutomaticAcquisitionSection("Option", AutomaticSettingIconType.CONDITIONAL,
                    expanded.value, { expanded.value = it }, MaterialTheme.colorScheme.surface,
                    MaterialTheme.colorScheme.onSurface, MaterialTheme.colorScheme.onSurfaceVariant) {
                    Text("Details")
                }
            }
        }
        fun layout(): TextLayoutResult {
            val results = mutableListOf<TextLayoutResult>()
            compose.onNodeWithText("Option", useUnmergedTree = true)
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
            return results.single()
        }
        assertEquals(14.sp, layout().layoutInput.style.fontSize)
        assertEquals(FontWeight.Normal, layout().layoutInput.style.fontWeight)
        compose.onNodeWithText("Option").performClick()
        assertEquals(14.sp, layout().layoutInput.style.fontSize)
        assertEquals(FontWeight.Bold, layout().layoutInput.style.fontWeight)
    }

    @Test fun translatedHeaderDoesNotClipWithLargeText() {
        val language = mutableStateOf("en")
        var title = ""
        compose.setContent {
            val config = Configuration(context.resources.configuration).apply {
                setLocales(LocaleList.forLanguageTags(language.value))
            }
            val localized = context.createConfigurationContext(config)
            val density = LocalDensity.current
            CompositionLocalProvider(LocalContext provides localized, LocalConfiguration provides config,
                LocalDensity provides Density(density.density, 1.5f)) {
                MaterialTheme {
                    title = localized.getString(R.string.conditional_acquisition)
                    Column(Modifier.width(320.dp).height(400.dp).verticalScroll(rememberScrollState())) {
                        UvirConditionalAcquisitionCard(true, AcquisitionConditionMatch.ANY,
                            AcquisitionConditionAction.ACQUIRE, emptyList(), false, false,
                            MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.onSurface,
                            MaterialTheme.colorScheme.onSurfaceVariant, UvirNumericFormat.SYSTEM, {}, {}, {})
                    }
                }
            }
        }
        for (tag in listOf("en","it","es","fr","pt","de","ar","he","fa","ru","tr","el","hi","ja","zh-CN","ko-KR","sw")) {
            compose.runOnIdle { language.value = tag }
            compose.waitForIdle()
            val results = mutableListOf<TextLayoutResult>()
            compose.onNodeWithText(title, useUnmergedTree = true).performScrollTo().assertIsDisplayed()
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
            assertFalse("$tag header overflow", results.single().hasVisualOverflow)
        }
    }
}
