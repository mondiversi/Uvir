package me.mondiversi.uvir

import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Isolated layout fixtures only: no archive, preferences, links or sensor commands. */
class UvirInfoBrandHeaderTest {
    @get:Rule val compose = createComposeRule()

    @Test fun logoAndTitleStayCenteredInBothThemesDirectionsAndFontScales() {
        val night = mutableStateOf(false)
        val rtl = mutableStateOf(false)
        val largeText = mutableStateOf(false)
        var expectedColor = Color.Transparent
        compose.setContent {
            val density = LocalDensity.current
            MaterialTheme(colorScheme = if (night.value) darkColorScheme() else lightColorScheme()) {
                expectedColor = MaterialTheme.colorScheme.onSurface
                CompositionLocalProvider(
                    LocalLayoutDirection provides if (rtl.value) LayoutDirection.Rtl else LayoutDirection.Ltr,
                    LocalDensity provides Density(density.density, if (largeText.value) 2f else 1f)
                ) {
                    Column(Modifier.requiredWidth(280.dp)) {
                        UvirInfoBrandHeader("Uvir", BuildConfig.VERSION_NAME, expectedColor)
                    }
                }
            }
        }
        for (dark in listOf(false, true)) for (rightToLeft in listOf(false, true))
            for (enlarged in listOf(false, true)) {
                compose.runOnIdle { night.value = dark; rtl.value = rightToLeft; largeText.value = enlarged }
                val logo = compose.onNodeWithTag("info_app_logo", useUnmergedTree = true)
                    .assertIsDisplayed().assertHasNoClickAction().getUnclippedBoundsInRoot()
                val title = compose.onNodeWithTag("info_app_title").assertIsDisplayed().assertHasNoClickAction()
                val text = title.getUnclippedBoundsInRoot()
                val layouts = mutableListOf<TextLayoutResult>()
                title.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
                assertEquals(80f, (logo.right - logo.left).value, .1f)
                assertEquals(logo.right - logo.left, logo.bottom - logo.top)
                assertEquals((logo.top.value + logo.bottom.value) / 2f,
                    (text.top.value + text.bottom.value) / 2f, 1f)
                assertEquals(rightToLeft, logo.left > text.left)
                assertEquals(14f, if (rightToLeft) (logo.left - text.right).value else (text.left - logo.right).value, .1f)
                assertEquals(expectedColor, layouts.single().layoutInput.style.color)
                assertEquals(FontWeight.Bold, layouts.single().layoutInput.style.fontWeight)
                assertEquals("Uvir\nV. ${BuildConfig.VERSION_NAME}", layouts.single().layoutInput.text.text)
                assertTrue(layouts.single().lineCount >= 2)
                assertFalse(layouts.single().hasVisualOverflow)
            }
    }

    @Test fun introductionKeepsTheBrandAboveDescriptionInPortraitAndLandscape() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val landscape = mutableStateOf(false)
        compose.setContent {
            val configuration = Configuration(LocalConfiguration.current).apply {
                orientation = if (landscape.value) Configuration.ORIENTATION_LANDSCAPE
                    else Configuration.ORIENTATION_PORTRAIT
            }
            CompositionLocalProvider(LocalConfiguration provides configuration) {
                MaterialTheme {
                    val colors = MaterialTheme.colorScheme
                    Column(Modifier.requiredWidth(if (landscape.value) 600.dp else 320.dp)) {
                        UvirVersionInfoScreen(rememberScrollState(), colors.background, colors.surface,
                            colors.onSurface, colors.onSurfaceVariant, {})
                    }
                }
            }
        }
        for (wide in listOf(false, true)) {
            compose.runOnIdle { landscape.value = wide }
            val card = compose.onNodeWithTag("info_introduction").assertIsDisplayed().getUnclippedBoundsInRoot()
            val brand = compose.onNodeWithTag("info_brand_header", useUnmergedTree = true)
                .assertIsDisplayed().getUnclippedBoundsInRoot()
            val title = compose.onNodeWithTag("info_app_title").assertIsDisplayed()
            val description = compose.onNodeWithText(context.getString(R.string.about_description))
                .assertIsDisplayed().getUnclippedBoundsInRoot()
            title.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { action ->
                val results = mutableListOf<TextLayoutResult>()
                action(results)
                assertEquals("${context.getString(R.string.app_name)}\nV. ${BuildConfig.VERSION_NAME}",
                    results.single().layoutInput.text.text)
            }
            assertTrue(brand.top >= card.top)
            assertTrue(brand.bottom <= card.bottom)
            assertTrue(brand.right <= card.right)
            // Layout is rasterized to whole pixels; fractional dp densities can
            // differ from the requested gap by less than one physical pixel.
            assertEquals(UvirSettingsGroupGap.value, (description.top - brand.bottom).value,
                1f / context.resources.displayMetrics.density)
        }
    }
}
