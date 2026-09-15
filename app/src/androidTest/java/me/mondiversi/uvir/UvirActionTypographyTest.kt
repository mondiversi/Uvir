package me.mondiversi.uvir

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** No app data, preferences or sensor commands. Deliberately tests an unthemed
 * root, as used by Uvir before adding the shared action-color theme. */
class UvirActionTypographyTest {
    @get:Rule val compose = createComposeRule()

    @Composable private fun Environment(night: Boolean, rtl: Boolean = false,
        fontScale: Float = 1f, content: @Composable () -> Unit) {
        val config = Configuration(LocalConfiguration.current).apply {
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                if (night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
        }
        val density = LocalDensity.current
        CompositionLocalProvider(LocalConfiguration provides config,
            LocalDensity provides Density(density.density, fontScale),
            LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
            content = content)
    }

    @Test fun defaultRootTextStyleTypographyAndShapesArePreservedInBothThemes() {
        val night = mutableStateOf(false)
        var before = TextStyle.Default
        var after = TextStyle.Default
        var beforeTypography: Typography? = null
        var afterTypography: Typography? = null
        var beforeShapes: Shapes? = null
        var afterShapes: Shapes? = null
        compose.setContent {
            Environment(night.value) {
                before = LocalTextStyle.current
                beforeTypography = MaterialTheme.typography
                beforeShapes = MaterialTheme.shapes
                UvirActionTheme {
                    after = LocalTextStyle.current
                    afterTypography = MaterialTheme.typography
                    afterShapes = MaterialTheme.shapes
                    Text("Root")
                }
            }
        }
        for (dark in listOf(false, true)) {
            compose.runOnIdle { night.value = dark }
            compose.waitForIdle()
            assertEquals("Action colors must not impose bodyLarge on the root", before, after)
            assertSame(beforeTypography, afterTypography)
            assertSame(beforeShapes, afterShapes)
            assertEquals(TextUnit.Unspecified, before.lineHeight)
            assertEquals(TextUnit.Unspecified, after.lineHeight)
        }
    }

    @Test fun explicitlyInheritedTextStyleIsNotResetByTheActionTheme() {
        val custom = TextStyle(fontSize = 12.sp, lineHeight = 15.sp,
            fontWeight = FontWeight.Medium, letterSpacing = 0.1.sp, color = Color.Cyan)
        var inherited = TextStyle.Default
        compose.setContent {
            Environment(true) {
                CompositionLocalProvider(LocalTextStyle provides custom) {
                    UvirActionTheme { inherited = LocalTextStyle.current; Text("Custom") }
                }
            }
        }
        assertEquals(custom, inherited)
        assertEquals(custom, textLayout("Custom").layoutInput.style)
    }

    @Test fun plainMaterialThemeReproducesTheSpacingRegressionButActionThemeDoesNot() {
        compose.setContent {
            Environment(true) {
                Column(Modifier.width(280.dp)) {
                    Text("First line\nSecond line", Modifier.testTag("before"), fontSize = 11.sp)
                    MaterialTheme {
                        Text("First line\nSecond line", Modifier.testTag("regression"), fontSize = 11.sp)
                    }
                    UvirActionTheme {
                        Text("First line\nSecond line", Modifier.testTag("fixed"), fontSize = 11.sp)
                    }
                }
            }
        }
        fun height(tag: String) = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot.height
        assertTrue("Ordinary MaterialTheme must reproduce the enlarged line boxes",
            height("regression") > height("before") + 1f)
        assertEquals("Only the palette may change", height("before"), height("fixed"), 1f)
    }

    @Test fun realHomeSettingsAndDetailCardsKeepTheirPreThemeTextAndLayoutMetrics() {
        val themed = mutableStateOf(false)
        val night = mutableStateOf(false)
        val rtl = mutableStateOf(false)
        val scale = mutableFloatStateOf(1f)
        compose.setContent {
            Environment(night.value, rtl.value, scale.floatValue) {
                val content: @Composable () -> Unit = {
                    Column(Modifier.width(300.dp).background(Color.Black)) {
                        Box(Modifier.testTag("header")) {
                            UvirHomeHeader(SensorConnectionMode.USB, true,
                                UsbSensorConnectionStatus.DISCONNECTED, false,
                                WirelessSensorConnectionStatus.DISCONNECTED, null, false, false,
                                "Info", Color.White, Color.Gray, {}, {}, {})
                        }
                        Box(Modifier.testTag("closed")) {
                            SettingsSection("Closed settings", Color.Black, Color.White, Color.Gray,
                                expanded = false, onExpandedChange = {}) { Text("Hidden") }
                        }
                        Box(Modifier.testTag("open")) {
                            SettingsSection("Open settings", Color.Black, Color.White, Color.Gray,
                                expanded = true, onExpandedChange = {}) {
                                Text("Settings description", fontSize = 12.sp)
                            }
                        }
                        Box(Modifier.testTag("context")) {
                            UvirDetailContextCard(true, "Demo sensor", "Sample note",
                                Color.Black, Color.White, Color.Gray)
                        }
                        Box(Modifier.testTag("identity")) {
                            UvirDetailIdentityCard(null, 1L, "ID / session", "Date and time",
                                "14/09/2026 20:00:00", "14/09/2026 20:01:00", "1m",
                                durationCount = 2, durationCountKind = UvirDetailDurationCountKind.ACQUISITION,
                                cardColor = Color.Black, primaryText = Color.White, secondaryText = Color.Gray)
                        }
                    }
                }
                if (themed.value) UvirActionTheme(content) else content()
            }
        }
        val tags = listOf("header", "closed", "open", "context", "identity")
        val labels = listOf("Uvir", BuildConfig.VERSION_NAME, "Closed settings", "Open settings",
            "Settings description", "Sample note", "Demo sensor")
        for (dark in listOf(false, true)) for (rightToLeft in listOf(false, true)) for (font in listOf(1f, 1.5f)) {
            compose.runOnIdle { night.value = dark; rtl.value = rightToLeft; scale.floatValue = font; themed.value = false }
            val heights = tags.associateWith { compose.onNodeWithTag(it).fetchSemanticsNode().boundsInRoot.height }
            val styles = labels.associateWith { metrics(textLayout(it).layoutInput.style) }
            compose.runOnIdle { themed.value = true }
            for (tag in tags) assertEquals("$tag: night=$dark rtl=$rightToLeft font=$font",
                heights.getValue(tag), compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot.height, 1f)
            for (label in labels) assertEquals("Text metrics for $label", styles.getValue(label),
                metrics(textLayout(label).layoutInput.style))
        }
    }

    @Test fun materialButtonsAndFieldsStillUseTheirOwnStandardTypography() {
        val themed = mutableStateOf(false)
        compose.setContent {
            Environment(true) {
                val content: @Composable () -> Unit = {
                    Column(Modifier.width(280.dp)) {
                        TextButton({}, Modifier.testTag("button")) { Text("Action") }
                        OutlinedTextField("Value", {}, Modifier.testTag("field"), label = { Text("Label") })
                    }
                }
                if (themed.value) UvirActionTheme(content) else content()
            }
        }
        val styles = listOf("Action", "Value", "Label").associateWith { metrics(textLayout(it).layoutInput.style) }
        val heights = listOf("button", "field").associateWith {
            compose.onNodeWithTag(it).fetchSemanticsNode().boundsInRoot.height }
        compose.runOnIdle { themed.value = true }
        for ((label, original) in styles) assertEquals(original, metrics(textLayout(label).layoutInput.style))
        for ((tag, original) in heights) assertEquals(original,
            compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot.height, 1f)
    }

    private fun textLayout(text: String): TextLayoutResult {
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(text, useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        return layouts.single()
    }

    private fun metrics(style: TextStyle) = listOf(style.fontSize, style.lineHeight,
        style.fontWeight, style.fontFamily, style.fontStyle, style.letterSpacing)
}
