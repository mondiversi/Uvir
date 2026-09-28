package me.mondiversi.uvir

import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import kotlin.math.abs

/** Isolated UI fixtures: no real exports, sensor commands or data changes. */
class UvirSettingsActionButtonStyleTest {
    @get:Rule val compose = createComposeRule()

    @Test fun activeButtonsAreTransparentWhileDisabledButtonsKeepTheirNeutralFill() {
        val night = mutableStateOf(false)
        val enabled = mutableStateOf(true)
        val rtl = mutableStateOf(false)
        var palettes = emptyList<ButtonColors>()
        var borders = emptyList<BorderStroke?>()
        var card = Color.Unspecified
        compose.setContent {
            KeepScreenOn()
            val configuration = Configuration(LocalConfiguration.current).apply {
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                    if (night.value) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            }
            CompositionLocalProvider(LocalConfiguration provides configuration,
                LocalLayoutDirection provides if (rtl.value) LayoutDirection.Rtl else LayoutDirection.Ltr,
                LocalUvirSettingsActionButtons provides true) {
                MaterialTheme(colorScheme = if (night.value) darkColorScheme() else lightColorScheme()) {
                    UvirActionTheme {
                        val foreground = MaterialTheme.colorScheme.onSurface
                        card = MaterialTheme.colorScheme.surface
                        palettes = listOf(uvirOutlinedActionColors(foreground),
                            uvirPrimaryOutlinedButtonColors(), uvirDestructiveOutlinedButtonColors())
                        borders = listOf(uvirOutlinedActionBorder(enabled.value, foreground),
                            uvirPrimaryOutlinedButtonBorder(enabled.value, foreground),
                            uvirDestructiveOutlinedButtonBorder(enabled.value, foreground))
                        Column(Modifier.width(280.dp).background(card)) {
                            palettes.forEachIndexed { index, colors ->
                                OutlinedButton({}, enabled = enabled.value, colors = colors,
                                    border = borders[index], modifier = Modifier.fillMaxWidth().testTag("action-$index")) {
                                    UvirLabeledButtonContent("Action $index",
                                        fontWeight = if (index == 2) FontWeight.Bold else null) {
                                        assertEquals(1.25f, LocalUvirActionGlyphStrokeScale.current)
                                        UvirButtonGlyphIcon(UvirButtonGlyph.REFRESH)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        for (dark in listOf(false, true)) for (rightToLeft in listOf(false, true)) for (active in listOf(false, true)) {
            compose.runOnIdle { night.value = dark; rtl.value = rightToLeft; enabled.value = active }
            compose.waitForIdle()
            assertNotEquals(palettes[0].contentColor, palettes[1].contentColor)
            assertNotEquals(palettes[1].contentColor, palettes[2].contentColor)
            for (index in 0..2) {
                assertEquals(Color.Transparent, palettes[index].containerColor)
                assertEquals(if (dark) Color(0xFF33343C) else Color(0xFFECEDEF), palettes[index].disabledContainerColor)
                assertEquals(if (dark) Color(0xFFA1A5AE) else Color(0xFF808790), palettes[index].disabledContentColor)
                assertEquals(1.dp, borders[index]!!.width)
                if (!active) assertEquals(borders[0], borders[index])
                val button = compose.onNodeWithTag("action-$index")
                if (active) button.assertIsEnabled() else button.assertIsNotEnabled()
                assertWeight("Action $index", FontWeight.SemiBold, fits = true)
                val pixels = button.captureToImage().toPixelMap()
                val inset = with(compose.density) { 8.dp.toPx().toInt() }
                val actual = pixels[pixels.width / 2, pixels.height - inset]
                val expectedSurface = (if (active) palettes[index].containerColor else palettes[index].disabledContainerColor).compositeOver(card)
                assertTrue("Correct resting fill: $dark/$active/$index",
                    abs(expectedSurface.red - actual.red) < .02f && abs(expectedSurface.green - actual.green) < .02f && abs(expectedSurface.blue - actual.blue) < .02f)
                if (active) assertEquals(card, expectedSurface) else assertNotEquals(card, expectedSurface)
            }
        }
    }

    @Test fun transparentButtonsStillHighlightOnlyWhilePressed() {
        compose.mainClock.autoAdvance = false
        val night = mutableStateOf(false)
        lateinit var pressed: State<Boolean>
        var clicks = 0
        val interactions = MutableInteractionSource()
        compose.setContent {
            KeepScreenOn()
            MaterialTheme(colorScheme = if (night.value) darkColorScheme() else lightColorScheme()) {
                CompositionLocalProvider(LocalUvirSettingsActionButtons provides true) {
                    pressed = interactions.collectIsPressedAsState()
                    Box(Modifier.background(MaterialTheme.colorScheme.surface).padding(48.dp)) {
                        OutlinedButton({ clicks++ }, interactionSource = interactions,
                            modifier = Modifier.width(220.dp).testTag("press-fixture"),
                            colors = uvirPrimaryOutlinedButtonColors(),
                            border = uvirPrimaryOutlinedButtonBorder(true, MaterialTheme.colorScheme.onSurface)) {
                            UvirLabeledButtonContent("Action") { UvirButtonGlyphIcon(UvirButtonGlyph.REFRESH) }
                        }
                    }
                }
            }
        }
        compose.mainClock.advanceTimeByFrame()
        for (dark in listOf(false, true)) {
            compose.runOnIdle { night.value = dark }
            compose.mainClock.advanceTimeBy(300)
            val button = compose.onNodeWithTag("press-fixture")
            val resting = button.captureToImage().toPixelMap()
            // Drive the interaction directly so screenshots do not depend on
            // the device's touch-injection timing; verify clicks separately.
            val press = PressInteraction.Press(Offset(resting.width / 2f, resting.height / 2f))
            compose.runOnIdle { assertTrue(interactions.tryEmit(press)) }
            compose.waitUntil(3_000) { pressed.value }
            compose.mainClock.advanceTimeBy(250)
            compose.waitForIdle()
            assertTrue(pressed.value)
            val held = button.captureToImage().toPixelMap()
            val changed = (0 until resting.height).sumOf { y ->
                (0 until resting.width).count { x ->
                    val before = resting[x, y]
                    val after = held[x, y]
                    abs(before.red - after.red) + abs(before.green - after.green) + abs(before.blue - after.blue) > .01f
                }
            }
            compose.runOnIdle { assertTrue(interactions.tryEmit(PressInteraction.Release(press))) }
            compose.waitUntil(3_000) { !pressed.value }
            compose.mainClock.advanceTimeBy(300)
            compose.waitForIdle()
            assertFalse(pressed.value)
            assertTrue("Pressed feedback must remain visible: $dark", changed > resting.width)
            button.performClick()
        }
        compose.runOnIdle { assertEquals(2, clicks) }
    }

    @Test fun dialogDoesNotInheritTheSettingsTrial() {
        var palette: ButtonColors? = null
        compose.setContent {
            KeepScreenOn()
            MaterialTheme {
                CompositionLocalProvider(LocalUvirSettingsActionButtons provides true,
                    LocalUvirActionGlyphStrokeScale provides 1.25f) {
                    UvirAlertDialog(onDismissRequest = {}, title = { Text("Fixture") },
                        text = {
                            assertFalse(LocalUvirSettingsActionButtons.current)
                            assertEquals(1f, LocalUvirActionGlyphStrokeScale.current)
                            palette = uvirPrimaryOutlinedButtonColors()
                            OutlinedButton({}, colors = palette!!) {
                                UvirLabeledButtonContent("Dialog button") { UvirButtonGlyphIcon(UvirButtonGlyph.REFRESH) }
                            }
                        }, confirmButton = { TextButton({}) { Text("Dialog action") } })
                }
            }
        }
        assertWeight("Dialog button", FontWeight.Medium)
        assertWeight("Dialog action", FontWeight.Medium)
        assertNotEquals(Color.Transparent, palette!!.containerColor)
        assertNotEquals(Color.Transparent, palette!!.disabledContainerColor)
    }

    @Test fun actualInfoPageUsesSemiboldActionLabels() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        compose.setContent {
            KeepScreenOn()
            MaterialTheme {
                UvirVersionInfoScreen(rememberScrollState(), Color(0xFFF0F0F0), Color.White,
                    Color.Black, Color.DarkGray, onDismissRequest = {})
            }
        }
        for (id in listOf(R.string.open_github_repository, R.string.check_for_updates)) {
            val label = context.getString(id)
            compose.onNodeWithText(label, useUnmergedTree = true).performScrollTo()
            assertWeight(label, FontWeight.SemiBold, fits = true)
        }
    }

    private fun assertWeight(label: String, weight: FontWeight, fits: Boolean = false) {
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(label, useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertEquals(label, weight, layouts.single().layoutInput.style.fontWeight)
        if (fits) assertFalse("Fits: $label", layouts.single().hasVisualOverflow)
    }

    @Composable private fun KeepScreenOn() {
        val view = LocalView.current
        DisposableEffect(view) {
            val previous = view.keepScreenOn
            view.keepScreenOn = true
            onDispose { view.keepScreenOn = previous }
        }
    }
}
