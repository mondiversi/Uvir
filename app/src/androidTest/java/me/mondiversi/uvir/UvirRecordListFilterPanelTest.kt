package me.mondiversi.uvir

import android.content.res.Configuration
import androidx.test.espresso.Espresso.pressBack
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Isolated composables only; no app database or sensor commands. */
class UvirRecordListFilterPanelTest {
    @get:Rule val compose = createComposeRule()
    private fun label(id: Int) = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    @Test fun filterButtonOpensAndClosesWithoutResettingTheQuery() {
        var filters by mutableStateOf(UvirRecordListFilters())
        var open by mutableStateOf(false)
        compose.setContent {
            MaterialTheme {
                Column(Modifier.fillMaxSize()) {
                    UvirRecordListFilterButton(filters.isActive, true) { open = !open }
                    if (open) UvirRecordListFilterPanel(filters, mapOf("1" to "Sensor A"),
                        setOf(true, false), { filters = it }, notes = listOf("Garden"))
                }
            }
        }
        compose.onNodeWithTag("list-filter-toggle").performClick()
        compose.onNodeWithTag("filter-note").performTouchInput { click(center) }
        compose.onNodeWithTag("filter-choice-option-Garden").performClick()
        pressBack()
        compose.onNodeWithTag("list-filter-toggle").performClick()
        compose.onNodeWithTag("filter-note").assertDoesNotExist()
        compose.onNodeWithTag("list-filter-toggle").performClick()
        compose.onNodeWithTag("filter-note")
            .assertContentDescriptionContains("Garden", substring = true)
    }

    @Test fun onlyModesPresentInTheSourceAreOffered() {
        compose.setContent {
            MaterialTheme { UvirRecordListFilterPanel(UvirRecordListFilters(),
                mapOf("1" to "Sensor A"), setOf(true), {}) }
        }
        compose.onNodeWithTag("filter-mode").performTouchInput { click(center) }
        compose.onNodeWithText(label(R.string.share_automatic)).assertExists()
        compose.onNodeWithText(label(R.string.share_manual)).assertDoesNotExist()
    }

    @Test fun resetClearsAllCriteriaAndWorksWithNightRtlAndLargeText() {
        var filters by mutableStateOf(UvirRecordListFilters(1000, 2000, setOf("לילה 🌙"), setOf("1"),
            setOf(UvirRecordFilterMode.AUTOMATIC), setOf("2"), setOf("10")))
        compose.setContent {
            val density = LocalDensity.current
            val nightConfiguration = Configuration(LocalConfiguration.current).apply {
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or Configuration.UI_MODE_NIGHT_YES
            }
            CompositionLocalProvider(LocalConfiguration provides nightConfiguration,
                LocalLayoutDirection provides LayoutDirection.Rtl,
                LocalDensity provides Density(density.density, 1.3f)) {
                MaterialTheme(colorScheme = darkColorScheme()) {
                    UvirRecordListFilterPanel(filters, mapOf("1" to "Sensor A"),
                        setOf(true), { filters = it })
                }
            }
        }
        compose.onNodeWithTag("filter-reset").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(UvirRecordListFilters(), filters) }
    }

    @Test fun filterStateSurvivesActivityStateRestoration() {
        val restoration = StateRestorationTester(compose)
        var state: MutableState<UvirRecordListFilters>? = null
        val expected = UvirRecordListFilters(1000, 2000, setOf("مرحبا 🌙", "Garden"), setOf("1", "2"),
            setOf(UvirRecordFilterMode.MANUAL, UvirRecordFilterMode.EXTERNAL), setOf("7", "8"),
            setOf("10", "20"), setOf(2, 5))
        restoration.setContent { state = rememberRecordListFilters() }
        compose.runOnIdle { state!!.value = expected }
        restoration.emulateSavedInstanceStateRestore()
        compose.runOnIdle { assertEquals(expected, state!!.value) }
    }

    @Test fun idChoiceUsesAnExistingIdentifierWithoutModifyingOtherCriteria() {
        var filters by mutableStateOf(UvirRecordListFilters(notes = setOf("Garden")))
        compose.setContent {
            MaterialTheme { UvirRecordListFilterPanel(filters, mapOf("1" to "Sensor A"),
                setOf(true, false), { filters = it }, recordIds = listOf(123)) }
        }
        compose.onNodeWithTag("filter-id").performScrollTo().performTouchInput { click(center) }
        compose.onNodeWithTag("filter-choice-option-123").performClick()
        compose.runOnIdle {
            assertEquals(setOf("123"), filters.recordIds)
            assertEquals(setOf("Garden"), filters.notes)
        }
    }
    @Test fun controlsFollowIdDateModeSensorNoteOrder() {
        compose.setContent {
            MaterialTheme { UvirRecordListFilterPanel(UvirRecordListFilters(),
                mapOf("1" to "Sensor A"), setOf(true, false), {},
                recordIds = listOf(1), sessionIds = listOf(10), notes = listOf("Garden"),
                dates = listOf(0L)) }
        }
        fun top(tag: String) = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot.top
        assertTrue(top("filter-id") < top("filter-from"))
        assertTrue(top("filter-from") < top("filter-mode"))
        assertEquals(top("filter-mode"), top("filter-sensor"), 0.5f)
        assertTrue(top("filter-mode") < top("filter-note"))
        assertEquals(top("filter-note"), top("filter-variant"), 0.5f)
    }
    @Test fun variantChoicesComeFromSavedSessionsAndCanBeDisabledForAlerts() {
        var filters by mutableStateOf(UvirRecordListFilters())
        var enabled by mutableStateOf(true)
        compose.setContent {
            MaterialTheme {
                UvirRecordListFilterPanel(
                    filters, mapOf("1" to "Sensor A"), setOf(true), { filters = it },
                    variantCounts = listOf(2, 5, 4), variantFilterEnabled = enabled
                )
            }
        }
        compose.onNodeWithTag("filter-variant").performScrollTo().performTouchInput { click(center) }
        compose.onNodeWithTag("filter-choice-option-2").assertExists()
        compose.onNodeWithTag("filter-choice-option-4").assertExists()
        compose.onNodeWithTag("filter-choice-option-5").performClick()
        compose.runOnIdle { assertEquals(setOf(5), filters.variantCounts); enabled = false }
        compose.onNodeWithTag("filter-variant").assertIsNotEnabled()
    }
    @Test fun panelMatchesTheListBackgroundAndFieldsStayReadableInBothThemes() {
        val night = mutableStateOf(false)
        compose.setContent {
            val configuration = Configuration(LocalConfiguration.current).apply {
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                    if (night.value) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            }
            CompositionLocalProvider(LocalConfiguration provides configuration) {
                MaterialTheme(colorScheme = if (night.value) darkColorScheme() else lightColorScheme()) {
                    UvirRecordListFilterPanel(UvirRecordListFilters(notes = setOf("Garden")),
                        mapOf("1" to "Sensor A"), setOf(true, false), {},
                        backgroundColor = if (night.value) Color(0xFF101418) else Color(0xFFF4F7F9))
                }
            }
        }
        for (dark in listOf(false, true)) {
            compose.runOnIdle { night.value = dark }
            val panel = compose.onNodeWithTag("filter-panel").captureToImage().toPixelMap()
            val expected = if (dark) Color(0xFF101418) else Color(0xFFF4F7F9)
            // Sample the empty lateral inset away from the system bar/edge shadow.
            val actual = panel[with(compose.density) { 8.dp.toPx() }.toInt(), panel.height / 2]
            assertEquals(expected.red, actual.red, 0.01f)
            assertEquals(expected.green, actual.green, 0.01f)
            assertEquals(expected.blue, actual.blue, 0.01f)
            if (dark) for (tag in listOf("filter-from", "filter-note", "filter-sensor", "filter-mode")) {
                val pixels = compose.onNodeWithTag(tag).performScrollTo().captureToImage().toPixelMap()
                var readable = 0
                for (y in 0 until pixels.height) for (x in 0 until pixels.width)
                    if (pixels[x, y].luminance() > 0.55f) readable++
                assertTrue("Night text/borders must remain readable: " + tag, readable > 25)
            }
            compose.onNodeWithTag("filter-sensor").performScrollTo().performTouchInput { click(center) }
            val menu = compose.onNodeWithTag("filter-choice-menu").captureToImage().toPixelMap()
            if (dark) {
                val menuColor = menu[menu.width - 16, menu.height / 2]
                assertEquals(Color(0xFF27323B).red, menuColor.red, 0.01f)
                assertEquals(Color(0xFF27323B).green, menuColor.green, 0.01f)
                assertEquals(Color(0xFF27323B).blue, menuColor.blue, 0.01f)
            }
            compose.onNodeWithText("Sensor A").performClick()
            pressBack()
        }
    }

    @Test fun filterToggleUsesTheStandardDisabledStateAndDoesNotClick() {
        var clicks = 0
        compose.setContent { MaterialTheme { UvirRecordListFilterButton(false, false) { clicks++ } } }
        compose.onNodeWithTag("list-filter-toggle").assertIsNotEnabled().performTouchInput { click(center) }
        compose.runOnIdle { assertEquals(0, clicks) }
    }

    @Test fun decorativeIconsPrecedeEveryLabelAndFollowThemeDirectionAndDisabledState() {
        val night = mutableStateOf(false)
        val direction = mutableStateOf(LayoutDirection.Ltr)
        val variantsEnabled = mutableStateOf(true)
        var enabledTint = Color.Unspecified
        var disabledTint = Color.Unspecified
        var panelBackground = Color.Unspecified
        compose.setContent {
            val view = LocalView.current
            DisposableEffect(view) {
                val previous = view.keepScreenOn
                view.keepScreenOn = true
                onDispose { view.keepScreenOn = previous }
            }
            val configuration = Configuration(LocalConfiguration.current).apply {
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                    if (night.value) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            }
            val density = LocalDensity.current
            CompositionLocalProvider(LocalConfiguration provides configuration,
                LocalLayoutDirection provides direction.value,
                LocalDensity provides Density(density.density, 1.3f)) {
                MaterialTheme(colorScheme = if (night.value) darkColorScheme() else lightColorScheme()) {
                    val colors = UvirOutlinedTextFieldColors()
                    val background = MaterialTheme.colorScheme.background
                    SideEffect {
                        enabledTint = colors.unfocusedLabelColor
                        disabledTint = colors.disabledTextColor
                        panelBackground = background
                    }
                    UvirRecordListFilterPanel(UvirRecordListFilters(), mapOf("1" to "Sensor A"),
                        setOf(true, false), {}, variantFilterEnabled = variantsEnabled.value)
                }
            }
        }
        val fields = listOf(
            Triple("id", "filter-id", R.string.list_filter_id),
            Triple("session", "filter-session-id", R.string.share_session_id_label),
            Triple("from", "filter-from", R.string.list_filter_from),
            Triple("until", "filter-until", R.string.list_filter_until),
            Triple("mode", "filter-mode", R.string.acquisition_mode_label),
            Triple("sensor", "filter-sensor", R.string.sensor_selector_label),
            Triple("note", "filter-note", R.string.share_note_label),
            Triple("variant", "filter-variant", R.string.list_filter_variant)
        )
        for (dark in listOf(false, true)) for (layout in listOf(LayoutDirection.Ltr, LayoutDirection.Rtl)) {
            compose.runOnIdle { night.value = dark; direction.value = layout; variantsEnabled.value = true }
            for ((icon, field, textId) in fields) {
                compose.onNodeWithTag(field).performScrollTo()
                val image = compose.onNodeWithTag("filter-label-icon-$icon", useUnmergedTree = true)
                    .assertIsDisplayed()
                val bounds = image.fetchSemanticsNode().boundsInRoot
                val text = compose.onNodeWithText(label(textId), useUnmergedTree = true)
                    .fetchSemanticsNode().boundsInRoot
                if (layout == LayoutDirection.Ltr) assertTrue(bounds.right < text.left)
                else assertTrue(bounds.left > text.right)
                assertEquals("Label and icon stay vertically centered: $icon", text.center.y, bounds.center.y, 1f)
                assertIconTint(image.captureToImage().toPixelMap(), enabledTint)
            }
            compose.runOnIdle { variantsEnabled.value = false }
            compose.onNodeWithTag("filter-variant").performScrollTo().assertIsNotEnabled()
            val disabled = compose.onNodeWithTag("filter-label-icon-variant", useUnmergedTree = true)
                .assertHasNoClickAction().captureToImage().toPixelMap()
            // Disabled alpha is applied once to the whole drawing, not once per overlapping path.
            assertIconTint(disabled, disabledTint.compositeOver(panelBackground))
            assertTrue("Disabled icon is less prominent than enabled labels", disabledTint.alpha < enabledTint.alpha)
        }
    }

    private fun assertIconTint(pixels: androidx.compose.ui.graphics.PixelMap, expected: Color) {
        var matching = 0
        for (y in 0 until pixels.height) for (x in 0 until pixels.width) {
            val pixel = pixels[x, y]
            if (kotlin.math.abs(pixel.red - expected.red) < 0.03f &&
                kotlin.math.abs(pixel.green - expected.green) < 0.03f &&
                kotlin.math.abs(pixel.blue - expected.blue) < 0.03f) matching++
        }
        assertTrue("The decorative icon uses the label color", matching > 3)
    }

    @Test fun severalSensorsCanBeCheckedWithoutReopeningAndAllClearsTheSelection() {
        var filters by mutableStateOf(UvirRecordListFilters())
        compose.setContent {
            val view = LocalView.current
            DisposableEffect(view) {
                val previous = view.keepScreenOn
                view.keepScreenOn = true
                onDispose { view.keepScreenOn = previous }
            }
            UvirAdaptiveDisplay { MaterialTheme(colorScheme =
                if (androidx.compose.foundation.isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
                UvirRecordListFilterPanel(filters, mapOf("1" to "Sensor A", "2" to "Sensor B", "3" to "Sensor C"),
                    setOf(true, false), { filters = it })
            } }
        }
        compose.onNodeWithTag("filter-sensor").performScrollTo().performTouchInput { click(center) }
        compose.onNodeWithTag("filter-choice-option-all").assertIsOn()
        compose.onNodeWithTag("filter-choice-option-1").assertIsOff().performClick().assertIsOn()
        compose.onNodeWithTag("filter-choice-option-2").performClick().assertIsOn()
        compose.onNodeWithTag("filter-choice-option-1").assertIsOn()
        compose.onNodeWithTag("filter-choice-option-3").assertIsOff()
        compose.onNodeWithTag("filter-choice-option-all").assertIsOff()
        compose.runOnIdle { assertEquals(setOf("1", "2"), filters.sensorKeys) }
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        java.io.File(instrumentation.targetContext.cacheDir, "multi-filter-audit.png").outputStream().use {
            instrumentation.uiAutomation.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        pressBack()
        compose.onNodeWithTag("filter-sensor").assertContentDescriptionContains("Sensor A, Sensor B", substring = true)
        compose.onNodeWithTag("filter-sensor").performTouchInput { click(center) }
        compose.onNodeWithTag("filter-choice-option-1").assertIsOn().performClick().assertIsOff()
        compose.runOnIdle { assertEquals(setOf("2"), filters.sensorKeys) }
        compose.onNodeWithTag("filter-choice-option-all").performClick().assertIsOn()
        compose.runOnIdle { assertFalse(filters.isActive) }
    }

    @Test fun preferencesReloadAllChoicesAndKeepAcquisitionsSeparateFromAlerts() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "isolated-list-filter-test-" + java.util.UUID.randomUUID()
        val preferences = context.getSharedPreferences(name, android.content.Context.MODE_PRIVATE)
        try {
            val expected = UvirRecordListFilters(sensorKeys = setOf("1", "2"),
                notes = setOf("مرحبا 🌙,\nGarden"), variantCounts = setOf(2, 5))
            saveRecordListFilters(preferences, KEY_ACQUISITION_LIST_FILTERS, expected)
            assertEquals(expected, readRecordListFilters(preferences, KEY_ACQUISITION_LIST_FILTERS))
            assertEquals(UvirRecordListFilters(), readRecordListFilters(preferences, KEY_ALERT_LIST_FILTERS))
            var state: MutableState<UvirRecordListFilters>? = null
            val epoch = mutableIntStateOf(0)
            compose.setContent { key(epoch.intValue) {
                state = rememberRecordListFilters(preferences, KEY_ACQUISITION_LIST_FILTERS)
            } }
            compose.runOnIdle { assertEquals(expected, state!!.value); epoch.intValue++ }
            compose.runOnIdle { assertEquals(expected, state!!.value); state!!.value = UvirRecordListFilters() }
            compose.waitForIdle()
            assertFalse(preferences.contains(KEY_ACQUISITION_LIST_FILTERS))
        } finally {
            // Only this test's uniquely named preference file is removed.
            context.deleteSharedPreferences(name)
        }
    }

    @Test fun longSensorMenusScrollWithoutLosingPreviouslyCheckedChoices() {
        var filters by mutableStateOf(UvirRecordListFilters())
        compose.setContent {
            UvirAdaptiveDisplay {
                MaterialTheme(colorScheme = if (androidx.compose.foundation.isSystemInDarkTheme())
                    darkColorScheme() else lightColorScheme()) {
                    UvirRecordListFilterPanel(filters,
                        (1..40).associate { it.toString() to "Sensor $it" },
                        setOf(true, false), { filters = it })
                }
            }
        }
        compose.onNodeWithTag("filter-sensor").performScrollTo().performTouchInput { click(center) }
        val range = compose.onNodeWithTag("filter-choice-menu").fetchSemanticsNode()
            .config[androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange]
        assertTrue(range.maxValue() > 0f)
        compose.onNodeWithTag("filter-choice-option-1").performScrollTo().performClick()
        compose.onNodeWithTag("filter-choice-option-40").performScrollTo().performClick().assertIsOn()
        compose.onNodeWithTag("filter-choice-option-1").performScrollTo().assertIsOn()
        compose.runOnIdle { assertEquals(setOf("1", "40"), filters.sensorKeys) }
    }
}
