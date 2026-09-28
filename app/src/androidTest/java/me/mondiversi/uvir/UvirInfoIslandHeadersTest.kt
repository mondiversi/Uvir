package me.mondiversi.uvir

import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** The actual Info page, without launching links or touching sensor data. */
class UvirInfoIslandHeadersTest {
    @get:Rule val compose = createComposeRule()

    @Test fun infoHeadersKeepNormalTextColorsAndAlwaysOpenContentInBothThemesAndDirections() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val night = mutableStateOf(false)
        val rtl = mutableStateOf(false)
        var titleColor = Color.Transparent
        compose.setContent {
            MaterialTheme(colorScheme = if (night.value) darkColorScheme() else lightColorScheme()) {
                val colors = MaterialTheme.colorScheme
                titleColor = colors.onSurface
                CompositionLocalProvider(LocalLayoutDirection provides
                    if (rtl.value) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                    UvirVersionInfoScreen(
                        scrollState = rememberScrollState(),
                        backgroundColor = colors.background,
                        cardColor = colors.surface,
                        primaryText = colors.onSurface,
                        secondaryText = colors.onSurfaceVariant,
                        onDismissRequest = {}
                    )
                }
            }
        }
        val headers = listOf(
            context.getString(R.string.about_whats_new_title, BuildConfig.VERSION_NAME),
            context.getString(R.string.github_repository_title),
            context.getString(R.string.project_numbers_title)
        )
        for (dark in listOf(false, true)) for (rightToLeft in listOf(false, true)) {
            compose.runOnIdle { night.value = dark; rtl.value = rightToLeft }
            for (title in headers) {
                val layouts = mutableListOf<TextLayoutResult>()
                val header = compose.onNodeWithText(title)
                header.performScrollTo().assertIsDisplayed().assertHasNoClickAction()
                    .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
                assertEquals(titleColor, layouts.single().layoutInput.style.color)
                assertEquals(FontWeight.Bold, layouts.single().layoutInput.style.fontWeight)
                // A touch must not collapse these informational sections.
                header.performTouchInput { click() }
                header.assertIsDisplayed()
            }
            compose.onNodeWithText(context.getString(R.string.project_numbers_description))
                .performScrollTo().assertIsDisplayed()
            compose.onNodeWithText(context.getString(R.string.open_github_repository))
                .performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("• ${context.getString(R.string.about_whats_new_connectivity)}")
                .performScrollTo().assertIsDisplayed()
        }
    }
}
