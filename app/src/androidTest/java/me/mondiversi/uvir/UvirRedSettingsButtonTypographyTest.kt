package me.mondiversi.uvir

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
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

/** Render actual reset buttons, but never invoke a reset or touch app data. */
class UvirRedSettingsButtonTypographyTest {
    @get:Rule val compose = createComposeRule()

    @Test fun onlyRedSettingsLabelsAreBoldInBothThemesDirectionsAndStates() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val locale = context.resources.configuration.locales[0]
        val night = mutableStateOf(false)
        val rtl = mutableStateOf(false)
        val enabled = mutableStateOf(true)
        compose.setContent {
            MaterialTheme(colorScheme = if (night.value) darkColorScheme() else lightColorScheme()) {
                CompositionLocalProvider(LocalLayoutDirection provides if (rtl.value) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                    Column(Modifier.width(280.dp)) {
                        UvirDataAndRestoreContent(
                            autoEnabled = !enabled.value,
                            sensorRestoreEnabled = enabled.value,
                            secondaryText = MaterialTheme.colorScheme.onSurfaceVariant,
                            onResetRequested = { error("No reset") },
                            onRestoreDefaultsRequested = { error("No app reset") },
                            onSensorRestoreRequested = { error("No sensor reset") }
                        )
                        OutlinedButton({}, colors = uvirOutlinedActionColors(MaterialTheme.colorScheme.onSurface)) {
                            UvirLabeledButtonContent("Normal") { UvirButtonGlyphIcon(UvirButtonGlyph.REFRESH) }
                        }
                        OutlinedButton({}, colors = uvirPrimaryOutlinedButtonColors()) {
                            UvirLabeledButtonContent("Purple") { UvirButtonGlyphIcon(UvirButtonGlyph.PLAY) }
                        }
                        TextButton({}) { Text("Dialog action") }
                    }
                }
            }
        }
        val redLabels = listOf(R.string.data_management_counters_button,
            R.string.settings_category_sensor, R.string.data_management_app_button)
            .map { context.getString(it).uppercase(locale) }
        for (dark in listOf(false, true)) for (rightToLeft in listOf(false, true)) for (active in listOf(false, true)) {
            compose.runOnIdle { night.value = dark; rtl.value = rightToLeft; enabled.value = active }
            for (label in redLabels + listOf("Normal", "Purple", "Dialog action")) {
                val layouts = mutableListOf<TextLayoutResult>()
                compose.onNodeWithText(label, useUnmergedTree = true)
                    .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
                val layout = layouts.single()
                assertEquals(label, if (label in redLabels) FontWeight.Bold else FontWeight.Medium,
                    layout.layoutInput.style.fontWeight)
                // Unchanged Material actions are weight controls only; verify
                // the fitting behavior of the reset labels changed by this trial.
                if (label in redLabels) assertFalse("Label must fit: $label", layout.hasVisualOverflow)
            }
        }
    }
}
