package me.mondiversi.uvir

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.util.Locale

/** Real option rows, isolated callbacks: does not change the app language or preferences. */
class UvirLanguageCodeAlignmentTest {
    @get:Rule val compose = createComposeRule()

    @Test fun allLanguageCodesShareOneCenteredColumnInBothThemesDirectionsAndFontSizes() {
        val night = mutableStateOf(false)
        val rtl = mutableStateOf(false)
        val fontScale = mutableStateOf(1f)
        val languages = AppLanguage.entries.filter { it != AppLanguage.SYSTEM }
        compose.setContent {
            val view = LocalView.current
            DisposableEffect(view) {
                val previous = view.keepScreenOn
                view.keepScreenOn = true
                onDispose { view.keepScreenOn = previous }
            }
            CompositionLocalProvider(
                LocalDensity provides Density(LocalDensity.current.density, fontScale.value),
                LocalLayoutDirection provides if (rtl.value) LayoutDirection.Rtl else LayoutDirection.Ltr
            ) {
                MaterialTheme(colorScheme = if (night.value) darkColorScheme() else lightColorScheme()) {
                    val colors = MaterialTheme.colorScheme
                    Column(Modifier.width(280.dp).verticalScroll(rememberScrollState())) {
                        languages.forEach { language ->
                            UvirSettingsRadioOption(
                                selected = language == AppLanguage.ITALIAN,
                                title = "Language ${language.name.lowercase(Locale.ROOT)}",
                                example = language.storedValue.uppercase(Locale.ROOT),
                                primaryText = colors.onSurface, secondaryText = colors.onSurfaceVariant,
                                centerExample = true, onClick = {}
                            )
                        }
                    }
                }
            }
        }
        for (dark in listOf(false, true)) for (rightToLeft in listOf(false, true)) {
            for (scale in listOf(1f, 1.8f)) {
                compose.runOnIdle { night.value = dark; rtl.value = rightToLeft; fontScale.value = scale }
                var columnCenter: Float? = null
                var columnWidth: Float? = null
                for (language in languages) {
                    val code = language.storedValue.uppercase(Locale.ROOT)
                    val node = compose.onNodeWithText(code, useUnmergedTree = true)
                        .performScrollTo().assertIsDisplayed()
                    val bounds = node.fetchSemanticsNode().boundsInRoot
                    if (columnCenter == null) { columnCenter = bounds.center.x; columnWidth = bounds.width }
                    assertEquals("Column center: $code, dark=$dark, rtl=$rightToLeft, scale=$scale",
                        columnCenter!!, bounds.center.x, 1f)
                    assertEquals("Column width: $code", columnWidth!!, bounds.width, 1f)
                    val layouts = mutableListOf<TextLayoutResult>()
                    node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
                    val layout = layouts.single()
                    assertEquals(TextAlign.Center, layout.layoutInput.style.textAlign)
                    assertEquals(1, layout.lineCount)
                    assertFalse("Clipped code: $code", layout.hasVisualOverflow)
                    assertEquals("Text center: $code", layout.size.width / 2f,
                        (layout.getLineLeft(0) + layout.getLineRight(0)) / 2f, 1f)
                    assertTrue(bounds.width > 0f)
                }
            }
        }
    }

    @Test fun ordinaryFormatExamplesAlignAtTheTrailingEdgeWithoutUsingTheCodeColumn() {
        compose.setContent {
            MaterialTheme {
                val colors = MaterialTheme.colorScheme
                Column(Modifier.width(280.dp)) {
                    UvirSettingsRadioOption(false, "Format", "123456789.00", colors.onSurface,
                        colors.onSurfaceVariant, onClick = {})
                }
            }
        }
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText("123456789.00", useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertEquals(TextAlign.End, layouts.single().layoutInput.style.textAlign)
        // Numeric/date samples must not inherit the fixed language-code column.
        assertEquals(0, layouts.single().layoutInput.constraints.minWidth)
    }
}
