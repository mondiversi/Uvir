package me.mondiversi.uvir

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Popup-only fixtures: never touch the real sensor, archive or preferences. */
class UvirAdaptiveDialogTest {
    @get:Rule val compose = createComposeRule()

    @Test fun dialogWindowRetainsHostScaleAndFontsWithAndWithoutActions() {
        val shortestWidth = mutableIntStateOf(400)
        val night = mutableStateOf(false)
        val rtl = mutableStateOf(false)
        val actions = mutableStateOf(false)
        var hostDensity = 0f
        var dialogDensity = 0f
        var hostWidth = 0
        var dialogWidth = 0
        var expectedGlyphSize = 0f
        compose.setContent {
            val view = LocalView.current
            DisposableEffect(view) {
                val previous = view.keepScreenOn
                view.keepScreenOn = true
                onDispose { view.keepScreenOn = previous }
            }
            val configuration = Configuration(LocalConfiguration.current).apply {
                smallestScreenWidthDp = shortestWidth.intValue
            }
            CompositionLocalProvider(LocalConfiguration provides configuration,
                LocalLayoutDirection provides if (rtl.value) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                UvirAdaptiveDisplay {
                    val density = LocalDensity.current
                    val logicalWidth = LocalConfiguration.current.screenWidthDp
                    SideEffect {
                        hostDensity = density.density
                        hostWidth = logicalWidth
                        expectedGlyphSize = with(density) { 32.dp.toPx() }
                    }
                    MaterialTheme(colorScheme = if (night.value) darkColorScheme() else lightColorScheme()) {
                        Text("B", style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.testTag("reference_text"))
                        UvirAlertDialog(
                            onDismissRequest = {},
                            title = { Text("T") },
                            modifier = Modifier.testTag("adaptive_dialog"),
                            confirmButton = if (actions.value) {
                                { TextButton({}) { Text("OK") } }
                            } else null,
                            text = {
                                val popupDensity = LocalDensity.current
                                val popupWidth = LocalConfiguration.current.screenWidthDp
                                SideEffect {
                                    dialogDensity = popupDensity.density
                                    dialogWidth = popupWidth
                                }
                                Column {
                                    Text("B", modifier = Modifier.testTag("popup_text"))
                                    Box(Modifier.size(32.dp).testTag("popup_glyph"))
                                }
                            }
                        )
                    }
                }
            }
        }
        for (width in listOf(400, 800)) for (dark in listOf(false, true))
            for (rightToLeft in listOf(false, true)) for (hasActions in listOf(false, true)) {
                compose.runOnIdle {
                    shortestWidth.intValue = width; night.value = dark
                    rtl.value = rightToLeft; actions.value = hasActions
                }
                val glyph = compose.onNodeWithTag("popup_glyph").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
                val reference = compose.onNodeWithTag("reference_text").fetchSemanticsNode().boundsInRoot
                val text = compose.onNodeWithTag("popup_text").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
                assertEquals("Dialog must not fall back to native density", hostDensity, dialogDensity, .001f)
                assertEquals("Popup sizing uses the host's logical viewport", hostWidth, dialogWidth)
                assertEquals(expectedGlyphSize, glyph.width, 1f)
                assertEquals("Typography grows with the screen, not independently", reference.height, text.height, 1f)
            }
    }

    @Test fun enlargedDialogKeepsFooterAndActionsVisibleWhileOnlyTheBodyScrolls() {
        compose.setContent {
            val view = LocalView.current
            DisposableEffect(view) {
                val previous = view.keepScreenOn
                view.keepScreenOn = true
                onDispose { view.keepScreenOn = previous }
            }
            UvirAdaptiveDisplay {
                MaterialTheme {
                    UvirAlertDialog(
                        onDismissRequest = {}, title = { Text("Popup") },
                        text = { repeat(40) { Text("Record $it", Modifier.testTag("record_$it")) } },
                        fixedBottomContent = { Text("Footer", Modifier.testTag("fixed_footer")) },
                        confirmButton = { TextButton({}, Modifier.testTag("confirm")) { Text("OK") } },
                        dismissButton = { TextButton({}, Modifier.testTag("cancel")) { Text("Cancel") } }
                    )
                }
            }
        }
        val footer = compose.onNodeWithTag("fixed_footer").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val confirm = compose.onNodeWithTag("confirm").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val cancel = compose.onNodeWithTag("cancel").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val scroll = compose.onNodeWithTag("uvir_dialog_scroll")
        assertTrue(scroll.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].maxValue() > 0f)
        compose.onNodeWithTag("record_39").performScrollTo().assertIsDisplayed()
        assertEquals(footer, compose.onNodeWithTag("fixed_footer").fetchSemanticsNode().boundsInRoot)
        assertEquals(confirm, compose.onNodeWithTag("confirm").fetchSemanticsNode().boundsInRoot)
        assertEquals(cancel, compose.onNodeWithTag("cancel").fetchSemanticsNode().boundsInRoot)
    }
}
