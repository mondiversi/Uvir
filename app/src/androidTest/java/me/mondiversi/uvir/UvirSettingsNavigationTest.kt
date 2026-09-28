package me.mondiversi.uvir

import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UvirSettingsNavigationTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun settingsListShowsOnlyPageRowsAndOpensTheSelectedContent() {
        compose.setContent {
            var selected by remember { mutableStateOf<UvirSettingsPage?>(null) }
            MaterialTheme {
                CompositionLocalProvider(
                    LocalUvirSettingsNavigation provides UvirSettingsNavigation(
                        selectedPage = selected,
                        onOpenPage = { selected = it }
                    )
                ) {
                    Column {
                        TestSettingsPage(
                            page = UvirSettingsPage.SENSOR_MEASUREMENT,
                            title = "Measurement",
                            content = "Measurement content"
                        )
                        TestSettingsPage(
                            page = UvirSettingsPage.LANGUAGE_AND_FORMATS,
                            title = "Language and formats",
                            content = "Language and formats content"
                        )
                    }
                }
            }
        }

        compose.onNodeWithContentDescription("Measurement").assertIsDisplayed()
        compose.onNodeWithContentDescription("Language and formats").assertIsDisplayed()
        compose.onNodeWithText("Measurement content").assertDoesNotExist()

        compose.onNodeWithContentDescription("Measurement").performClick()
        compose.waitForIdle()

        compose.onNodeWithText("Measurement content").assertIsDisplayed()
        compose.onNodeWithContentDescription("Language and formats").assertDoesNotExist()
        compose.onNodeWithText("Language and formats content").assertDoesNotExist()
    }

    @Test
    fun ordinarySettingsSectionsKeepTheirExpandableBehaviorOutsideTheSettingsNavigator() {
        compose.setContent {
            var expanded by remember { mutableStateOf(false) }
            MaterialTheme {
                SettingsSection(
                    title = "Expandable",
                    containerColor = Color.DarkGray,
                    titleColor = Color.White,
                    dividerColor = Color.Gray,
                    expanded = expanded,
                    onExpandedChange = { expanded = it }
                ) {
                    androidx.compose.material3.Text("Expandable content")
                }
            }
        }

        compose.onNodeWithText("Expandable content").assertDoesNotExist()
        compose.onNodeWithContentDescription("Expandable").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Expandable content").assertIsDisplayed()
    }

    @Test
    fun diagnosticsEntryIsTheLastSensorRowAndOpensTheSamePage() {
        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        val diagnostics = context.getString(R.string.settings_section_debug)
        compose.setContent {
            var selected by remember { mutableStateOf<UvirSettingsPage?>(null) }
            MaterialTheme {
                CompositionLocalProvider(LocalUvirSettingsNavigation provides UvirSettingsNavigation(
                    selectedPage = selected, onOpenPage = { selected = it })) {
                    Column {
                        if (selected == null) {
                            androidx.compose.material3.Text("Sensor section")
                            TestSettingsPage(UvirSettingsPage.SENSOR_MEASUREMENT, "Measurement", "Measurement content")
                            UvirSettingsDiagnosticsListEntry(Color.DarkGray, Color.White, Color.Gray)
                            androidx.compose.material3.Text("Phone section")
                            TestSettingsPage(UvirSettingsPage.LANGUAGE, "Language", "Language content")
                        } else if (selected == UvirSettingsPage.DEBUG) {
                            androidx.compose.material3.Text("Diagnostics content")
                        }
                    }
                }
            }
        }
        val measurement = compose.onNodeWithContentDescription("Measurement").fetchSemanticsNode().boundsInRoot
        val entry = compose.onNodeWithContentDescription(diagnostics).fetchSemanticsNode().boundsInRoot
        val phone = compose.onNodeWithText("Phone section").fetchSemanticsNode().boundsInRoot
        org.junit.Assert.assertTrue(measurement.bottom <= entry.top && entry.bottom <= phone.top)
        compose.onNodeWithContentDescription(diagnostics).assertHasClickAction().performClick()
        compose.onNodeWithText("Diagnostics content").assertIsDisplayed()
        compose.onNodeWithText("Phone section").assertDoesNotExist()
    }

    @androidx.compose.runtime.Composable
    private fun TestSettingsPage(
        page: UvirSettingsPage,
        title: String,
        content: String
    ) {
        SettingsSection(
            title = title,
            containerColor = Color.DarkGray,
            titleColor = Color.White,
            dividerColor = Color.Gray,
            settingsPage = page
        ) {
            androidx.compose.material3.Text(content)
        }
    }

    @Test
    fun mainSettingsListAndDetailIconsUseExactlyTheirTitleColor() {
        val night = mutableStateOf(false)
        val rtl = mutableStateOf(false)
        val selected = mutableStateOf<UvirSettingsPage?>(null)
        var text = Color.Transparent
        var card = Color.Transparent
        var listIcon = Color.Transparent
        var detailIcon = Color.Transparent
        compose.setContent {
            MaterialTheme(colorScheme = if (night.value)
                androidx.compose.material3.darkColorScheme() else androidx.compose.material3.lightColorScheme()) {
                text = MaterialTheme.colorScheme.onSurface
                card = MaterialTheme.colorScheme.surface
                CompositionLocalProvider(
                    LocalLayoutDirection provides if (rtl.value) LayoutDirection.Rtl else LayoutDirection.Ltr,
                    LocalUvirSettingsNavigation provides
                        UvirSettingsNavigation(selected.value, { selected.value = it })) {
                    SettingsSection("Page", MaterialTheme.colorScheme.surface, text, Color.Gray,
                        titleIconContent = { tint ->
                            listIcon = tint
                            Box(Modifier.size(20.dp).background(tint).testTag("list-glyph"))
                        },
                        settingsPage = UvirSettingsPage.DEBUG) {
                        SettingsSection("Detail", MaterialTheme.colorScheme.surface, text, Color.Gray,
                            highlightExpandedHeader = false,
                            titleIconContent = { tint -> detailIcon = tint }) {
                            androidx.compose.material3.Text("Body")
                        }
                    }
                }
            }
        }
        for (dark in listOf(false, true)) for (rightToLeft in listOf(false, true)) {
            compose.runOnIdle { night.value = dark; rtl.value = rightToLeft; selected.value = null }
            compose.onNodeWithContentDescription("Page").assertIsDisplayed()
            compose.runOnIdle { org.junit.Assert.assertEquals(text, listIcon) }
            val row = compose.onNodeWithContentDescription("Page")
            val root = row.fetchSemanticsNode().boundsInRoot
            val glyph = compose.onNodeWithTag("list-glyph", useUnmergedTree = true)
                .fetchSemanticsNode().boundsInRoot
            val pixels = row.captureToImage().toPixelMap()
            val padding = with(compose.density) { 4.dp.toPx() }
            val x = (glyph.left - root.left - padding + 1).toInt()
            val y = (glyph.center.y - root.top).toInt()
            org.junit.Assert.assertEquals("No circle behind the icon", card, pixels[x, y])
            org.junit.Assert.assertEquals("The icon matches the title exactly", text,
                pixels[(glyph.center.x - root.left).toInt(), y])
            org.junit.Assert.assertEquals("The icon container is transparent", card,
                pixels[x, (glyph.top - root.top - padding + 1).toInt()])
            compose.onNodeWithContentDescription("Page").performClick()
            compose.onNodeWithText("Body").assertIsDisplayed()
            compose.runOnIdle { org.junit.Assert.assertEquals(text, detailIcon) }
        }
    }

    @Test
    fun allDetailHeaderIconsMatchTheTitleAndHaveNoBackground() {
        val night = mutableStateOf(false)
        val rtl = mutableStateOf(false)
        val neutralIcons = mutableStateOf(true)
        var text = Color.Transparent
        var card = Color.Transparent
        var staticTint = Color.Transparent
        var sectionTint = Color.Transparent
        var outsideTint = Color.Transparent
        compose.setContent {
            MaterialTheme(colorScheme = if (night.value)
                androidx.compose.material3.darkColorScheme() else androidx.compose.material3.lightColorScheme()) {
                text = MaterialTheme.colorScheme.onSurface
                card = MaterialTheme.colorScheme.surface
                CompositionLocalProvider(LocalLayoutDirection provides
                    if (rtl.value) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                    Column {
                        CompositionLocalProvider(LocalUvirSettingsNeutralIcons provides neutralIcons.value) {
                            Box(Modifier.testTag("static-island")) {
                                SettingsIsland(card, text) {
                                    SettingsIslandHeader("Static", text, titleIconContent = { tint ->
                                        staticTint = tint
                                        Box(Modifier.size(20.dp).background(tint).testTag("static-glyph"))
                                    })
                                    androidx.compose.material3.Text("Static body")
                                }
                            }
                            SettingsSection("Section", card, text, Color.Gray,
                                highlightExpandedHeader = false, showExpandedDivider = true,
                                titleIconContent = { tint -> sectionTint = tint }) {
                                androidx.compose.material3.Text("Section body")
                            }
                        }
                        SettingsSection("Other screen", card, text, Color.Gray,
                            highlightExpandedHeader = false,
                            titleIconContent = { tint -> outsideTint = tint }) {
                            androidx.compose.material3.Text("Other body")
                        }
                    }
                }
            }
        }
        for (dark in listOf(false, true)) for (rightToLeft in listOf(false, true))
            for (neutral in listOf(false, true)) {
                compose.runOnIdle { night.value = dark; rtl.value = rightToLeft; neutralIcons.value = neutral }
                compose.onNodeWithText("Static body").assertIsDisplayed()
                compose.runOnIdle {
                    org.junit.Assert.assertEquals(text, staticTint)
                    org.junit.Assert.assertEquals(text, sectionTint)
                    org.junit.Assert.assertEquals(text, outsideTint)
                }
                val island = compose.onNodeWithTag("static-island")
                val root = island.fetchSemanticsNode().boundsInRoot
                val glyph = compose.onNodeWithTag("static-glyph").fetchSemanticsNode().boundsInRoot
                val pixels = island.captureToImage().toPixelMap()
                val inset = with(compose.density) { 4.dp.toPx() }
                val x = (glyph.left - root.left - inset + 1).toInt()
                val y = (glyph.center.y - root.top).toInt()
                org.junit.Assert.assertEquals("No circle in either style", card, pixels[x, y])
                org.junit.Assert.assertEquals(text,
                    pixels[(glyph.center.x - root.left).toInt(), y])
            }
    }

    @Test
    fun bareIconsFollowActualAppIslandsEvenWhenMaterialSurfacesStayLight() {
        val night = mutableStateOf(false)
        val rtl = mutableStateOf(false)
        compose.setContent {
            val configuration = Configuration(LocalConfiguration.current).apply {
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                    if (night.value) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            }
            CompositionLocalProvider(
                LocalConfiguration provides configuration,
                LocalLayoutDirection provides if (rtl.value) LayoutDirection.Rtl else LayoutDirection.Ltr,
                LocalUvirSettingsNeutralIcons provides true
            ) {
                // Reproduce MainActivity's action theme plus UvirApp's own island palette.
                MaterialTheme(colorScheme = androidx.compose.material3.lightColorScheme()) {
                    UvirActionTheme {
                        val card = if (night.value) Color(0xFF1C242B) else Color.White
                        val text = if (night.value) Color.White else Color(0xFF101418)
                        Column {
                            Box(Modifier.testTag("app-list")) {
                                CompositionLocalProvider(LocalUvirSettingsNavigation provides
                                    UvirSettingsNavigation(null, {})) {
                                    SettingsSection("Page", card, text, Color.Gray,
                                        settingsPage = UvirSettingsPage.DEBUG,
                                        titleIconContent = { tint ->
                                            Box(Modifier.size(20.dp).background(tint).testTag("app-list-glyph"))
                                        }) {}
                                }
                            }
                            Box(Modifier.testTag("app-static")) {
                                SettingsIsland(card, text) {
                                    SettingsIslandHeader("Static", text, titleIconContent = { tint ->
                                        Box(Modifier.size(20.dp).background(tint).testTag("app-static-glyph"))
                                    })
                                }
                            }
                            Box(Modifier.testTag("app-section")) {
                                SettingsSection("Section", card, text, Color.Gray,
                                    highlightExpandedHeader = false,
                                    titleIconContent = { tint ->
                                        Box(Modifier.size(20.dp).background(tint).testTag("app-section-glyph"))
                                    }) {}
                            }
                        }
                    }
                }
            }
        }
        for (dark in listOf(false, true)) for (rightToLeft in listOf(false, true)) {
            compose.runOnIdle { night.value = dark; rtl.value = rightToLeft }
            val expected = if (dark) Color.White else Color(0xFF101418)
            val islandColor = if (dark) Color(0xFF1C242B) else Color.White
            for (tag in listOf("app-list", "app-static", "app-section")) {
                val island = compose.onNodeWithTag(tag)
                val root = island.fetchSemanticsNode().boundsInRoot
                val glyph = compose.onNodeWithTag("$tag-glyph", useUnmergedTree = true)
                    .fetchSemanticsNode().boundsInRoot
                val pixels = island.captureToImage().toPixelMap()
                val inset = with(compose.density) { 4.dp.toPx() }
                val x = (glyph.left - root.left - inset + 1).toInt()
                val y = (glyph.center.y - root.top).toInt()
                org.junit.Assert.assertEquals("$tag has no circle, dark=$dark", islandColor, pixels[x, y])
                org.junit.Assert.assertEquals("$tag icon matches the actual app text, dark=$dark", expected,
                    pixels[(glyph.center.x - root.left).toInt(), y])
            }
        }
    }

    @Test
    fun appTextColorsRemainReadableAgainstTheIslands() {
        val daySurface = Color.White
        val nightSurface = Color(0xFF1C242B)
        val day = Color(0xFF101418)
        val night = Color.White
        org.junit.Assert.assertTrue(night.luminance() > day.luminance())
        for ((icon, surface) in listOf(day to daySurface, night to nightSurface)) {
            val contrast = (maxOf(icon.luminance(), surface.luminance()) + 0.05f) /
                (minOf(icon.luminance(), surface.luminance()) + 0.05f)
            org.junit.Assert.assertTrue("The icon must contrast with its island", contrast >= 4.5f)
        }
    }
}
