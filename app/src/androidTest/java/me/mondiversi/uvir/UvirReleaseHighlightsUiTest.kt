package me.mondiversi.uvir

import android.content.res.Configuration
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.platform.app.InstrumentationRegistry
import java.util.Locale
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Read-only release-page fixtures in the disposable verification package. */
class UvirReleaseHighlightsUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun allTwentyLanguagesRenderAndScrollThroughEveryReleaseHighlight() {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val language = mutableStateOf("en")
        val dark = mutableStateOf(false)
        fun localized(tag: String) = base.createConfigurationContext(
            Configuration(base.resources.configuration).apply {
                setLocale(Locale.forLanguageTag(tag))
                fontScale = 1.3f
            }
        )
        compose.setContent {
            KeepUvirVerificationScreenOn()
            val context = localized(language.value)
            CompositionLocalProvider(
                LocalContext provides context,
                LocalConfiguration provides context.resources.configuration,
                LocalResources provides context.resources,
                LocalLayoutDirection provides if (language.value in listOf("ar", "fa", "he"))
                    LayoutDirection.Rtl else LayoutDirection.Ltr
            ) {
                key(language.value) {
                    MaterialTheme(colorScheme = if (dark.value) darkColorScheme() else lightColorScheme()) {
                        val colors = MaterialTheme.colorScheme
                        UvirVersionInfoScreen(rememberScrollState(), colors.background,
                            colors.surface, colors.onSurface, colors.onSurfaceVariant, {})
                    }
                }
            }
        }
        val keys = listOf(R.string.about_whats_new_connectivity, R.string.about_whats_new_connections,
            R.string.about_whats_new_measurements, R.string.about_whats_new_interface,
            R.string.update_whats_new, R.string.about_whats_new_github)
        for ((index, tag) in listOf("en", "it", "de", "fr", "es", "pt", "nl", "pl", "el",
            "ru", "tr", "sw", "id", "hi", "ar", "fa", "he", "ja", "ko", "zh-CN").withIndex()) {
            compose.runOnIdle { language.value = tag; dark.value = index % 2 == 1 }
            val context = localized(tag)
            compose.onNodeWithText(context.getString(R.string.about_whats_new_title, BuildConfig.VERSION_NAME))
                .performScrollTo().assertIsDisplayed()
            for (resource in keys) {
                val node = compose.onNodeWithText("• " + context.getString(resource))
                    .performScrollTo().assertIsDisplayed()
                val layouts = mutableListOf<TextLayoutResult>()
                node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
                assertFalse("Clipped release text: $tag/$resource", layouts.single().hasVisualOverflow)
            }
        }
        assertEquals("1.4.2", BuildConfig.VERSION_NAME)
    }
}
